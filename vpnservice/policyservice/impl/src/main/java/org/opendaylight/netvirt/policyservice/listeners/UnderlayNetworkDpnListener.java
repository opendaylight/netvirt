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
                    }
                });
            }
        });
    }

}
