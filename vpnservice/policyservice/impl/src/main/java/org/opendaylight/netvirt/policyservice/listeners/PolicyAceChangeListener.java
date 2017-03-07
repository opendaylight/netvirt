/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LOG.trace("ACE {} removed", ace);
        processPolicyAce(identifier.firstKeyOf(Acl.class), ace, NwConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<Ace> identifier, Ace origAce, Ace updatedAce) {
        LOG.trace("ACE {} updated to {}", origAce, updatedAce);
        AclKey aclKey = identifier.firstKeyOf(Acl.class);
        processPolicyAce(aclKey, origAce, NwConstants.DEL_FLOW);
        processPolicyAce(aclKey, updatedAce, NwConstants.ADD_FLOW);
    }

    @Override
    protected void add(InstanceIdentifier<Ace> identifier, Ace ace) {
        LOG.trace("ACE {} added", ace);
        processPolicyAce(identifier.firstKeyOf(Acl.class), ace, NwConstants.ADD_FLOW);
    }

    private void processPolicyAce(AclKey aclKey, Ace ace, int addOrRemove) {
        if (PolicyServiceUtil.isPolicyAcl(aclKey.getAclType())) {
            LOG.trace("Ignoring non policy ACE rule {}", ace.getRuleName());
            return;
        }

        String ruleName = ace.getRuleName();
        String policyClassifierName = policyServiceUtil.getAcePolicyClassifier(ace);
        if (policyClassifierName == null) {
            LOG.debug("No egress policy classifier found for ACE rule {}", ruleName);
            return;
        }

        List<String> underlayNetworks = policyServiceUtil.getUnderlayNetworksForClassifier(policyClassifierName);
        if (underlayNetworks == null || underlayNetworks.isEmpty()) {
            LOG.debug("No underlay networks found for ACE rule {} classifier {}", ruleName, policyClassifierName);
        }

        policyServiceUtil.updateUnderlayNetworksAceRule(underlayNetworks, aclKey.getAclName(), ruleName);
        aceFlowProgrammer.programAceFlows(ace, policyClassifierName, underlayNetworks, NwConstants.ADD_FLOW);
    }

}
