/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import com.google.common.base.Optional;

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
import org.opendaylight.netvirt.policyservice.PolicyRouteGroupProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UnderlayNetworkDpnListener
        extends AsyncDataTreeChangeListenerBase<DpnToInterface, UnderlayNetworkDpnListener> {
    private static final Logger LOG = LoggerFactory.getLogger(UnderlayNetworkDpnListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;
    private final PolicyRouteGroupProgrammer routeGroupProgramer;

    @Inject
    public UnderlayNetworkDpnListener(final DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
            final PolicyAceFlowProgrammer aceFlowProgrammer, final PolicyRouteGroupProgrammer routeGroupProgramer) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
        this.aceFlowProgrammer = aceFlowProgrammer;
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

        populatePolicyRoutesToDpn(underlayNetwork, profiles, tunnelInterfaces, dpId, NwConstants.DEL_FLOW);
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

        List<TunnelInterface> origTunnelInterfaces = Optional.fromNullable(origDpnToInterface.getTunnelInterface())
                .or(Collections.emptyList());
        List<TunnelInterface> upfdatedTunnelInterfaces = Optional
                .fromNullable(updatedDpnToInterface.getTunnelInterface()).or(Collections.emptyList());
        List<TunnelInterface> removedTunnelInterfaces = new ArrayList<>(origTunnelInterfaces);
        removedTunnelInterfaces.removeAll(upfdatedTunnelInterfaces);
        List<TunnelInterface> addedTunnelInterfaces = new ArrayList<>(upfdatedTunnelInterfaces);
        addedTunnelInterfaces.removeAll(origTunnelInterfaces);

        populatePolicyRoutesToDpn(underlayNetwork, profiles, removedTunnelInterfaces, dpId, NwConstants.DEL_FLOW);
        populatePolicyRoutesToDpn(underlayNetwork, profiles, addedTunnelInterfaces, dpId, NwConstants.ADD_FLOW);
    }

    @Override
    protected void add(InstanceIdentifier<DpnToInterface> key, DpnToInterface dpnToInterface) {
        String underlayNetwork = key.firstKeyOf(UnderlayNetwork.class).getNetworkName();
        BigInteger dpId = dpnToInterface.getDpId();
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        LOG.info("DPN {} added to underlay network {} with tunnels {}", dpId, underlayNetwork, tunnelInterfaces);
        populatePolicyRulesToDpn(underlayNetwork, tunnelInterfaces, dpId, NwConstants.ADD_FLOW);
    }

    private void populatePolicyRulesToDpn(String underlayNetwork, List<TunnelInterface> tunnelInterfaces,
            BigInteger dpId, int addOrRemove) {
        List<PolicyProfile> profiles = policyServiceUtil.getUnderlayNetworkPolicyProfiles(underlayNetwork);
        if (profiles == null || profiles.isEmpty()) {
            LOG.debug("No policy profiles found for underlay network {}", underlayNetwork);
            return;
        }

        populatePolicyRoutesToDpn(underlayNetwork, profiles, tunnelInterfaces, dpId, addOrRemove);
        populatePolicyAclRulesToDpn(underlayNetwork, dpId, profiles);
    }

    private void populatePolicyRoutesToDpn(String underlayNetwork, List<PolicyProfile> profiles,
            List<TunnelInterface> tunnelInterfaces, BigInteger dpId, int addOrRemove) {
        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();
            List<String> underlayNetworks = policyServiceUtil.getUnderlayNetworksForClassifier(policyClassifier);
            if (underlayNetworks != null) {
                int bucketId = underlayNetworks.indexOf(underlayNetwork);
                if (bucketId != -1) {
                    routeGroupProgramer.programPolicyClassifierBuckets(policyClassifier, tunnelInterfaces, dpId,
                            bucketId, addOrRemove);
                } else {
                    LOG.warn("Policy classifier {} routes do not contain {}", policyClassifier, underlayNetwork);
                }
            } else {
                LOG.warn("No underlay networks found for classifier {} while populating {} flows", policyClassifier,
                        underlayNetwork);
            }
        });
    }

    private void populatePolicyAclRulesToDpn(String underlayNetwork, BigInteger dpId, List<PolicyProfile> profiles) {
        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile
                .PolicyAclRule> aclRules = policyServiceUtil.getPolicyClassifierAclRules(policyClassifier);
            if (aclRules != null) {
                aclRules.forEach(aclRule -> {
                    List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy
                        .profile.policy.acl.rule.AceRule> aceRules = aclRule.getAceRule();
                    if (aceRules != null) {
                        aceRules.forEach(aceRule -> {
                            Ace policyAce = policyServiceUtil.getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                            if (policyAce != null) {
                                aceFlowProgrammer.programAceFlows(policyAce, underlayNetwork, dpId,
                                        NwConstants.ADD_FLOW);
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
}
