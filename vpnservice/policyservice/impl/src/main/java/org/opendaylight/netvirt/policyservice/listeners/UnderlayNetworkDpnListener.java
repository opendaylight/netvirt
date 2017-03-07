/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.PolicyAceFlowProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.PolicyProfile;
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
@Singleton
public class UnderlayNetworkDpnListener
        extends AsyncDataTreeChangeListenerBase<DpnToInterface, UnderlayNetworkDpnListener> {
    private static final Logger LOG = LoggerFactory.getLogger(UnderlayNetworkDpnListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;

    @Inject
    public UnderlayNetworkDpnListener(final DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
            final PolicyAceFlowProgrammer aceFlowProgrammer) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
        this.aceFlowProgrammer = aceFlowProgrammer;
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
    }

    @Override
    protected void update(InstanceIdentifier<DpnToInterface> key, DpnToInterface origDpnToInterface,
            DpnToInterface updatedDpnToInterface) {
    }

    @Override
    protected void add(InstanceIdentifier<DpnToInterface> key, DpnToInterface dpnToInterface) {
        String underlayNetwork = key.firstKeyOf(UnderlayNetwork.class).getNetworkName();
        BigInteger dpId = dpnToInterface.getDpId();
        LOG.info("DPN {} added to underlay network {}", dpId, underlayNetwork);
        populatePolicyRulesToDpn(underlayNetwork, dpId);

    }

    private void populatePolicyRulesToDpn(String underlayNetwork, BigInteger dpId) {
        List<PolicyProfile> profiles = policyServiceUtil.getUnderlayNetworkPolicyProfiles(underlayNetwork);
        if (profiles == null || profiles.isEmpty()) {
            LOG.debug("No policy profiles found for underlay network {}", underlayNetwork);
            return;
        }

        profiles.forEach(profile -> {
            String policyClassifier = profile.getPolicyClassifier();

            Optional.ofNullable(policyServiceUtil.getPolicyClassifierAclRules(policyClassifier)).ifPresent(aclRules -> {
                aclRules.forEach(aclRule -> {
                    Optional.ofNullable(aclRule.getAceRule()).ifPresent(aceRules -> {
                        aceRules.forEach(aceRule -> {
                            com.google.common.base.Optional<Ace> policyAceOpt = policyServiceUtil
                                    .getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                            if (policyAceOpt.isPresent()) {
                                aceFlowProgrammer.programAceFlows(policyAceOpt.get(), dpId, NwConstants.ADD_FLOW);
                            } else {
                                LOG.warn("Failed to get ACL {} rule {}", aclRule.getAclName(), aceRule.getRuleName());
                            }
                        });
                    });
                });
            });
        });
    }

}
