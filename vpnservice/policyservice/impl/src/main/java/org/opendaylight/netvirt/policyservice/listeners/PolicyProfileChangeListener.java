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
import org.opendaylight.netvirt.policyservice.PolicyIdManager;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRule;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen on {@link PolicyProfile} config changes and update the policy pipeline
 * whenever the association between policy classifier and underlay networks
 * changes.<br>
 * This listener will update the following flows/groups:<br>
 * * POLICY_CLASSIFER_TABLE policy ACL flows when policy classifier
 * added/removed<br>
 * * POLICY_ROUTING_TABLE flows when policy classifier added/removed<br>
 * * Policy classifier groups for each remote DPN is updated when policy
 * classifier added/removed or when the list of associated underlay networks is
 * updated
 */
@Singleton
public class PolicyProfileChangeListener
        extends AsyncDataTreeChangeListenerBase<PolicyProfile, PolicyProfileChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyProfileChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;

    @Inject
    public PolicyProfileChangeListener(final DataBroker dataBroker, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyAceFlowProgrammer aceFlowProgrammer) {
        this.dataBroker = dataBroker;
        this.policyIdManager = policyIdManager;
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
        String policyClassifier = policyProfile.getPolicyClassifier();
        LOG.info("Policy profile {} removed", policyClassifier);
        List<String> underlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
        handlePolicyProfileUpdate(policyClassifier, underlayNetworks, false);
        policyIdManager.releasePolicyClassifierId(policyClassifier);

    }

    @Override
    protected void update(InstanceIdentifier<PolicyProfile> key, PolicyProfile origPolicyProfile,
            PolicyProfile updatedPolicyProfile) {
        LOG.info("Policy profile {} updated", updatedPolicyProfile.getPolicyClassifier());
        List<String> origUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(origPolicyProfile.getPolicyRoute());
        List<String> updatedUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(updatedPolicyProfile.getPolicyRoute());

        List<String> removedUnderlayNetworks = new ArrayList<>(origUnderlayNetworks);
        removedUnderlayNetworks.removeAll(updatedUnderlayNetworks);
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(removedUnderlayNetworks,
                origPolicyProfile.getPolicyClassifier(), false);

        updatedUnderlayNetworks.removeAll(origUnderlayNetworks);
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(updatedUnderlayNetworks,
                updatedPolicyProfile.getPolicyClassifier(), true);
    }

    @Override
    protected void add(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        LOG.info("Policy profile {} added", policyProfile.getPolicyClassifier());
        List<String> underlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
        handlePolicyProfileUpdate(policyProfile.getPolicyClassifier(), underlayNetworks, true);
    }

    private void handlePolicyProfileUpdate(String policyClassifier, List<String> underlayNetworks, boolean isAdded) {
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(underlayNetworks, policyClassifier, isAdded);
        List<PolicyAclRule> aclRules = policyServiceUtil.getPolicyClassifierAclRules(policyClassifier);
        if (aclRules == null || aclRules.isEmpty()) {
            LOG.debug("No policy ACE rules found for policy classifier {}", policyClassifier);
            return;
        }

        List<BigInteger> dpIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        if (dpIds == null || dpIds.isEmpty()) {
            LOG.debug("No DPNs found for installation of underlay networks {}", underlayNetworks);
            return;
        }

        aclRules.forEach(aclRule -> {
            Optional.ofNullable(aclRule.getAceRule()).map(aceRules -> {
                aceRules.forEach(aceRule -> {
                    com.google.common.base.Optional<Ace> policyAceOpt = policyServiceUtil
                            .getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                    if (policyAceOpt.isPresent()) {
                        aceFlowProgrammer.programAceFlows(policyAceOpt.get(), policyClassifier, dpIds,
                                isAdded ? NwConstants.ADD_FLOW : NwConstants.DEL_FLOW);
                    }
                });
                return aceRules;
            }).orElseGet(() -> {
                LOG.debug("No ACE rules found for ACL {}", aclRule.getAclName());
                return null;
            });
        });
    }

}
