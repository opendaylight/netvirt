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
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.PolicyAceFlowProgrammer;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen on policy ACL {@link Ace} config changes and update the
 * POLICY_CLASSIFER_TABLE accordingly.<br>
 * Each valid policy ACE contains a set of {@link Matches} associated to
 * policy-classifier. The policy classifier is coded using write_metadata
 * instruction with METADATA_MASK_POLICY_CLASSIFER_ID mask
 *
 */
@Singleton
public class PolicyAceChangeListener extends AsyncDataTreeChangeListenerBase<Ace, PolicyAceChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAceChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyAceFlowProgrammer aceFlowProgrammer;

    @Inject
    public PolicyAceChangeListener(final DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
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
    protected InstanceIdentifier<Ace> getWildCardPath() {
        return InstanceIdentifier.create(AccessLists.class).child(Acl.class).child(AccessListEntries.class)
                .child(Ace.class);
    }

    @Override
    protected PolicyAceChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<Ace> identifier, Ace ace) {
        handlePolicyAceUpdate(identifier.firstKeyOf(Acl.class), ace, false);
    }

    @Override
    protected void update(InstanceIdentifier<Ace> identifier, Ace origAce, Ace updatedAce) {
        AclKey aclKey = identifier.firstKeyOf(Acl.class);
        handlePolicyAceUpdate(aclKey, origAce, false);
        handlePolicyAceUpdate(aclKey, updatedAce, true);
    }

    @Override
    protected void add(InstanceIdentifier<Ace> identifier, Ace ace) {
        handlePolicyAceUpdate(identifier.firstKeyOf(Acl.class), ace, true);
    }

    private void handlePolicyAceUpdate(AclKey aclKey, Ace ace, boolean isAdded) {
        if (!PolicyServiceUtil.isPolicyAcl(aclKey.getAclType())) {
            return;
        }

        LOG.trace("Policy ACE {} {}", ace, isAdded ? "updated" : "removed");
        String ruleName = ace.getRuleName();
        Optional<String> policyClassifierOpt = policyServiceUtil.getAcePolicyClassifier(ace);
        if (!policyClassifierOpt.isPresent()) {
            LOG.warn("No egress policy classifier found for ACE rule {}", ruleName);
            return;
        }


        String policyClassifier = policyClassifierOpt.get();
        List<String> underlayNetworks = policyServiceUtil.getUnderlayNetworksForClassifier(policyClassifier);
        if (underlayNetworks == null || underlayNetworks.isEmpty()) {
            LOG.debug("No underlay networks found for ACE rule {} classifier {}", ruleName, policyClassifier);
        }

        policyServiceUtil.updateAclRuleForPolicyClassifier(policyClassifier, aclKey.getAclName(), ruleName, isAdded);
        List<BigInteger> dpIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        if (dpIds == null || dpIds.isEmpty()) {
            LOG.debug("No DPNs found for installation of ACE rule {} networks {}", ace.getRuleName(), underlayNetworks);
            return;
        }

        aceFlowProgrammer.programAceFlows(ace, policyClassifier, dpIds,
                isAdded ? NwConstants.ADD_FLOW : NwConstants.DEL_FLOW);
    }

}
