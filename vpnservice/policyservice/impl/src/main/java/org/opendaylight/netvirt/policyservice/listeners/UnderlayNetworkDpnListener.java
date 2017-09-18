/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.PolicyAceFlowProgrammer;
import org.opendaylight.netvirt.policyservice.PolicyRouteFlowProgrammer;
import org.opendaylight.netvirt.policyservice.PolicyRouteGroupProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen on {@link DpnToInterface} operational updates for each underlay
 * network. When new DPN is added/removed, all policy pipeline associated flows
 * and groups will be populated to the new DPN.<br>
 * When new tunnel interfaces are added/removed from underlay network, the
 * corresponding policy classifier group buckets would be updated accordingly.
 */
@SuppressWarnings("deprecation")
@Singleton
public class UnderlayNetworkDpnListener
        extends AsyncDataTreeChangeListenerBase<DpnToInterface, UnderlayNetworkDpnListener> {
    private static final Logger LOG = LoggerFactory.getLogger(UnderlayNetworkDpnListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;
    private final PolicyRouteFlowProgrammer routeFlowProgrammer;
    private final PolicyRouteGroupProgrammer routeGroupProgramer;

    @Inject
    public UnderlayNetworkDpnListener(final DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
            final PolicyAceFlowProgrammer aceFlowProgrammer, final PolicyRouteFlowProgrammer routeFlowProgrammer,
            final PolicyRouteGroupProgrammer routeGroupProgramer) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
        this.aceFlowProgrammer = aceFlowProgrammer;
        this.routeFlowProgrammer = routeFlowProgrammer;
        this.routeGroupProgramer = routeGroupProgramer;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<DpnToInterface> getWildCardPath() {
        return InstanceIdentifier.create(UnderlayNetworks.class).child(UnderlayNetwork.class)
                .child(DpnToInterface.class);
    }

    @Override
    protected UnderlayNetworkDpnListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<DpnToInterface> key, DpnToInterface dpnToInterface) {
        String underlayNetwork = key.firstKeyOf(UnderlayNetwork.class).getNetworkName();
        BigInteger dpId = dpnToInterface.getDpId();
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        LOG.info("DPN {} removed from underlay network {} with tunnels {}", dpId, underlayNetwork, tunnelInterfaces);
        List<PolicyProfile> profiles = policyServiceUtil.getUnderlayNetworkPolicyProfiles(underlayNetwork);
        if (profiles == null || profiles.isEmpty()) {
            LOG.debug("No policy profiles found for underlay network {}", underlayNetwork);
            return;
        }

        populatePolicyGroupBucketsToDpn(underlayNetwork, profiles, tunnelInterfaces, dpId, NwConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<DpnToInterface> key, DpnToInterface origDpnToInterface,
            DpnToInterface updatedDpnToInterface) {
        String underlayNetwork = key.firstKeyOf(UnderlayNetwork.class).getNetworkName();
        BigInteger dpId = updatedDpnToInterface.getDpId();
        LOG.info("DPN {} updated to underlay network {} with tunnels {}", dpId, underlayNetwork,
                updatedDpnToInterface.getTunnelInterface());
        List<PolicyProfile> profiles = policyServiceUtil.getUnderlayNetworkPolicyProfiles(underlayNetwork);
        if (profiles == null || profiles.isEmpty()) {
            LOG.debug("No policy profiles found for underlay network {}", underlayNetwork);
            return;
        }

        List<TunnelInterface> origTunnelInterfaces = origDpnToInterface.getTunnelInterface();
        if (origTunnelInterfaces == null) {
            origTunnelInterfaces = Collections.emptyList();
        }
        List<TunnelInterface> updatedTunnelInterfaces = updatedDpnToInterface.getTunnelInterface();
        if (updatedTunnelInterfaces == null) {
            updatedTunnelInterfaces = Collections.emptyList();
        }
        List<TunnelInterface> removedTunnelInterfaces = new ArrayList<>(origTunnelInterfaces);
        removedTunnelInterfaces.removeAll(updatedTunnelInterfaces);
        List<TunnelInterface> addedTunnelInterfaces = new ArrayList<>(updatedTunnelInterfaces);
        addedTunnelInterfaces.removeAll(origTunnelInterfaces);

        populatePolicyGroupBucketsToDpn(underlayNetwork, profiles, removedTunnelInterfaces, dpId, NwConstants.DEL_FLOW);
        populatePolicyGroupsToDpn(profiles, addedTunnelInterfaces, dpId, NwConstants.ADD_FLOW);
        populatePolicyGroupBucketsToDpn(underlayNetwork, profiles, addedTunnelInterfaces, dpId, NwConstants.ADD_FLOW);
    }

    @Override
    protected void add(InstanceIdentifier<DpnToInterface> key, DpnToInterface dpnToInterface) {
        String underlayNetwork = key.firstKeyOf(UnderlayNetwork.class).getNetworkName();
        BigInteger dpId = dpnToInterface.getDpId();
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        LOG.info("DPN {} added to underlay network {} with tunnels {}", dpId, underlayNetwork, tunnelInterfaces);
        List<PolicyProfile> profiles = policyServiceUtil.getUnderlayNetworkPolicyProfiles(underlayNetwork);
        if (profiles == null || profiles.isEmpty()) {
            LOG.debug("No policy profiles found for underlay network {}", underlayNetwork);
            return;
        }

        populatePolicyGroupsToDpn(profiles, tunnelInterfaces, dpId, NwConstants.ADD_FLOW);
        populatePolicyGroupBucketsToDpn(underlayNetwork, profiles, tunnelInterfaces, dpId, NwConstants.ADD_FLOW);
        populatePolicyAclRulesToDpn(dpId, profiles, NwConstants.ADD_FLOW);
        populatePolicyRoutesToDpn(profiles, tunnelInterfaces, dpId, NwConstants.ADD_FLOW);
    }

    private void populatePolicyGroupsToDpn(List<PolicyProfile> profiles,
            List<TunnelInterface> tunnelInterfaces, BigInteger dpId, int addOrRemove) {
        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();
            routeGroupProgramer.programPolicyClassifierGroups(policyClassifier, dpId, tunnelInterfaces, addOrRemove);
        });
    }

    private void populatePolicyGroupBucketsToDpn(String underlayNetwork, List<PolicyProfile> profiles,
            List<TunnelInterface> tunnelInterfaces, BigInteger dpId, int addOrRemove) {
        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();
            List<String> underlayNetworks =
                    policyServiceUtil.getUnderlayNetworksForClassifier(policyClassifier);
            if (underlayNetworks != null) {
                int bucketId = underlayNetworks.indexOf(underlayNetwork);
                if (bucketId != -1) {
                    routeGroupProgramer.programPolicyClassifierGroupBuckets(policyClassifier, tunnelInterfaces,
                            dpId, bucketId, addOrRemove);
                } else {
                    LOG.warn("Policy classifier {} routes do not contain {}", policyClassifier,
                            underlayNetwork);
                }
            } else {
                LOG.warn("No underlay networks found for classifier {} while populating {} flows",
                        policyClassifier, underlayNetwork);
            }
        });
    }

    private void populatePolicyAclRulesToDpn(BigInteger dpId, List<PolicyProfile> profiles,
            int addOrRemove) {
        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();

            List<PolicyAclRule> policyClassifierAclRules =
                    policyServiceUtil.getPolicyClassifierAclRules(policyClassifier);
            if (policyClassifierAclRules != null) {
                policyClassifierAclRules.forEach(aclRule -> {
                    List<AceRule> aceRules = aclRule.getAceRule();
                    if (aceRules != null) {
                        aceRules.forEach(aceRule -> {
                            com.google.common.base.Optional<Ace> policyAceOpt = policyServiceUtil
                                    .getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                            if (policyAceOpt.isPresent()) {
                                aceFlowProgrammer.programAceFlows(policyAceOpt.get(), dpId, addOrRemove);
                            } else {
                                LOG.warn("Failed to get ACL {} rule {}", aclRule.getAclName(), aceRule.getRuleName());
                            }
                        });
                    } else {
                        LOG.debug("No ACE rule found for policy ACL {}", aclRule.getAclName());
                    }
                });
            }
        });
    }

    private void populatePolicyRoutesToDpn(List<PolicyProfile> profiles, List<TunnelInterface> tunnelInterfaces,
            BigInteger dpId, int addOrRemove) {
        if (tunnelInterfaces == null) {
            LOG.debug("No tunnel interfaces found for DPN {}", dpId);
            return;
        }

        profiles.forEach(policyProfile -> {
            String policyClassifier = policyProfile.getPolicyClassifier();
            tunnelInterfaces.forEach(tunnelInterface -> {
                BigInteger remoteDpId = tunnelInterface.getRemoteDpId();
                routeFlowProgrammer.programPolicyClassifierFlow(policyClassifier, dpId, remoteDpId, addOrRemove);
            });
        });
    }
}
