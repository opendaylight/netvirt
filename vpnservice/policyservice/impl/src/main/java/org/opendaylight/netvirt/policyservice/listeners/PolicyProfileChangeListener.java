/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.PolicyAceFlowProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRule;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyProfileChangeListener
        extends AsyncDataTreeChangeListenerBase<PolicyProfile, PolicyProfileChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyProfileChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;

    public PolicyProfileChangeListener(final DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
            final PolicyAceFlowProgrammer aceFlowProgrammer) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
        this.aceFlowProgrammer = aceFlowProgrammer;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<PolicyProfile> getWildCardPath() {
        return InstanceIdentifier.create(PolicyProfiles.class).child(PolicyProfile.class);
    }

    @Override
    protected PolicyProfileChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        LOG.trace("Policy profile {} removed", policyProfile);
        List<String> underlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
        policyServiceUtil.updateUnderlayNetworksPolicyClassifier(underlayNetworks, policyProfile.getPolicyClassifier(),
                false);
    }

    @Override
    protected void update(InstanceIdentifier<PolicyProfile> key, PolicyProfile origPolicyProfile,
            PolicyProfile updatedPolicyProfile) {
        LOG.trace("Policy profile {} updated to {}", origPolicyProfile, updatedPolicyProfile);
        // TODO Auto-generated method stub
        List<String> origUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(origPolicyProfile.getPolicyRoute());
        List<String> updatedUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(updatedPolicyProfile.getPolicyRoute());

        List<String> removedUnderlayNetworks = new ArrayList<>(origUnderlayNetworks);
        removedUnderlayNetworks.removeAll(updatedUnderlayNetworks);
        policyServiceUtil.updateUnderlayNetworksPolicyClassifier(removedUnderlayNetworks,
                origPolicyProfile.getPolicyClassifier(), false);

        updatedUnderlayNetworks.removeAll(origUnderlayNetworks);
        handlePolicyProfileUpdate(updatedPolicyProfile.getPolicyClassifier(), updatedUnderlayNetworks);
    }

    @Override
    protected void add(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        LOG.trace("Policy profile {} added", policyProfile);
        List<String> underlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
        handlePolicyProfileUpdate(policyProfile.getPolicyClassifier(), underlayNetworks);
    }

    private void handlePolicyProfileUpdate(String policyClassifierName, List<String> underlayNetworks) {
        if (underlayNetworks == null || underlayNetworks.isEmpty()) {
            LOG.debug("No underlay networks found for policy classifier {}", policyClassifierName);
            return;
        }

        policyServiceUtil.updateUnderlayNetworksPolicyClassifier(underlayNetworks, policyClassifierName, true);
        List<PolicyAclRule> aclRules = policyServiceUtil.getPolicyClassifierAclRules(policyClassifierName);
        if (aclRules == null || aclRules.isEmpty()) {
            LOG.debug("No policy ACE rules found for policy classifier {}", policyClassifierName);
            return;
        }

        aclRules.forEach(aclRule -> {
            List<AceRule> aceRules = aclRule.getAceRule();
            if (aceRules != null) {
                aceRules.forEach(aceRule -> {
                    Ace policyAce = policyServiceUtil.getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                    aceFlowProgrammer.programAceFlows(policyAce, policyClassifierName, underlayNetworks,
                            NwConstants.ADD_FLOW);
                });
            }
        });
    }

}
