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
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.PolicyAceFlowProgrammer;
import org.opendaylight.netvirt.policyservice.PolicyIdManager;
import org.opendaylight.netvirt.policyservice.PolicyRouteFlowProgrammer;
import org.opendaylight.netvirt.policyservice.PolicyRouteGroupProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRule;
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
@SuppressWarnings("deprecation")
@Singleton
public class PolicyProfileChangeListener
        extends AsyncDataTreeChangeListenerBase<PolicyProfile, PolicyProfileChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyProfileChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;
    private final PolicyRouteFlowProgrammer routeFlowProgrammer;
    private final PolicyRouteGroupProgrammer routeGroupProgramer;

    @Inject
    public PolicyProfileChangeListener(final DataBroker dataBroker, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyAceFlowProgrammer aceFlowProgrammer,
            final PolicyRouteFlowProgrammer routeFlowProgrammer, final PolicyRouteGroupProgrammer routeGroupProgramer) {
        this.dataBroker = dataBroker;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.aceFlowProgrammer = aceFlowProgrammer;
        this.routeFlowProgrammer = routeFlowProgrammer;
        this.routeGroupProgramer = routeGroupProgramer;
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
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(underlayNetworks, policyClassifier, false);
        List<BigInteger> dpnIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        List<BigInteger> remoteDpIds = policyServiceUtil.getUnderlayNetworksRemoteDpns(underlayNetworks);
        routeGroupProgramer.programPolicyClassifierGroups(policyClassifier, dpnIds, remoteDpIds, NwConstants.DEL_FLOW);
        updatePolicyAclRules(policyClassifier, underlayNetworks, NwConstants.DEL_FLOW);
        routeFlowProgrammer.programPolicyClassifierFlows(policyClassifier, dpnIds, remoteDpIds, NwConstants.DEL_FLOW);
        policyIdManager.releasePolicyClassifierId(policyClassifier);
        releasePolicyClassifierGroupIds(policyClassifier, dpnIds);
    }

    @Override
    protected void update(InstanceIdentifier<PolicyProfile> key, PolicyProfile origPolicyProfile,
            PolicyProfile updatedPolicyProfile) {
        List<String> origUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(origPolicyProfile.getPolicyRoute());
        List<String> updatedUnderlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(updatedPolicyProfile.getPolicyRoute());
        List<String> removedUnderlayNetworks = new ArrayList<>(origUnderlayNetworks);
        removedUnderlayNetworks.removeAll(updatedUnderlayNetworks);
        List<String> addedUnderlayNetworks = new ArrayList<>(updatedUnderlayNetworks);
        addedUnderlayNetworks.removeAll(origUnderlayNetworks);

        String policyClassifier = updatedPolicyProfile.getPolicyClassifier();
        LOG.info("Policy profile {} updated", policyClassifier);
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(removedUnderlayNetworks, policyClassifier, false);
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(addedUnderlayNetworks, policyClassifier, true);

        // rewrite all group buckets
        routeGroupProgramer.programPolicyClassifierGroupBuckets(policyClassifier, origUnderlayNetworks,
                NwConstants.DEL_FLOW);
        routeGroupProgramer.programPolicyClassifierGroupBuckets(policyClassifier, updatedUnderlayNetworks,
                NwConstants.ADD_FLOW);

        updatedUnderlayNetworks.removeAll(origUnderlayNetworks);
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(updatedUnderlayNetworks,
                updatedPolicyProfile.getPolicyClassifier(), true);
        updatePolicyAclRules(policyClassifier, updatedUnderlayNetworks, NwConstants.ADD_FLOW);
    }

    @Override
    protected void add(InstanceIdentifier<PolicyProfile> key, PolicyProfile policyProfile) {
        String policyClassifier = policyProfile.getPolicyClassifier();
        LOG.info("Policy profile {} added", policyClassifier);
        List<String> underlayNetworks = PolicyServiceUtil
                .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
        policyServiceUtil.updatePolicyClassifierForUnderlayNetworks(underlayNetworks, policyClassifier, true);
        List<BigInteger> dpnIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        List<BigInteger> remoteDpIds = policyServiceUtil.getUnderlayNetworksRemoteDpns(underlayNetworks);
        routeGroupProgramer.programPolicyClassifierGroups(policyClassifier, dpnIds, remoteDpIds, NwConstants.ADD_FLOW);
        routeGroupProgramer.programPolicyClassifierGroupBuckets(policyClassifier, underlayNetworks,
                NwConstants.ADD_FLOW);
        updatePolicyAclRules(policyClassifier, underlayNetworks, NwConstants.ADD_FLOW);
        routeFlowProgrammer.programPolicyClassifierFlows(policyClassifier, dpnIds, remoteDpIds, NwConstants.ADD_FLOW);
    }

    private void updatePolicyAclRules(String policyClassifier, List<String> underlayNetworks, int addOrRemove) {
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
            List<AceRule> aceRules = aclRule.getAceRule();
            if (aceRules != null) {
                aceRules.forEach(aceRule -> {
                    Optional<Ace> policyAceOpt =
                            policyServiceUtil.getPolicyAce(aclRule.getAclName(), aceRule.getRuleName());
                    if (policyAceOpt.isPresent()) {
                        aceFlowProgrammer.programAceFlows(policyAceOpt.get(), policyClassifier, dpIds,
                                addOrRemove);
                    }
                });
            } else {
                LOG.debug("No ACE rules found for ACL {}", aclRule.getAclName());
            }
        });
    }

    private void releasePolicyClassifierGroupIds(String policyClassifier, List<BigInteger> dpnIds) {
        dpnIds.forEach(dpnId -> policyIdManager.releasePolicyClassifierGroupId(policyClassifier, dpnId));
    }
}
