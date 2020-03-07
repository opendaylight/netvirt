/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.controller.md.sal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.groups.attributes.SecurityGroups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.groups.attributes.security.groups.SecurityGroup;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSecurityGroupListener
        extends AsyncDataTreeChangeListenerBase<SecurityGroup, NeutronSecurityGroupListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSecurityGroupListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;

    @Inject
    public NeutronSecurityGroupListener(DataBroker dataBroker, JobCoordinator jobCoordinator) {
        super(SecurityGroup.class, NeutronSecurityGroupListener.class);
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<SecurityGroup> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(SecurityGroups.class).child(SecurityGroup.class);
    }

    @Override
    protected void remove(InstanceIdentifier<SecurityGroup> key, SecurityGroup securityGroup) {
        LOG.trace("Removing securityGroup: {}", securityGroup);
        InstanceIdentifier<Acl> identifier = getAclInstanceIdentifier(securityGroup);
        String jobKey = securityGroup.key().getUuid().getValue();
        jobCoordinator.enqueueJob(jobKey,
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> tx.delete(identifier))),
            NeutronSecurityRuleConstants.DJC_MAX_RETRIES);
    }

    @Override
    protected void update(InstanceIdentifier<SecurityGroup> key, SecurityGroup dataObjectModificationBefore,
        SecurityGroup dataObjectModificationAfter) {
        LOG.debug("Do nothing");
    }

    @Override
    protected void add(InstanceIdentifier<SecurityGroup> instanceIdentifier, SecurityGroup securityGroup) {
        LOG.trace("Adding securityGroup: {}", securityGroup);
        Acl acl = toAclBuilder(securityGroup).build();
        InstanceIdentifier<Acl> identifier = getAclInstanceIdentifier(securityGroup);
        String jobKey = securityGroup.key().getUuid().getValue();
        jobCoordinator.enqueueJob(jobKey,
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> tx.put(identifier, acl, CREATE_MISSING_PARENTS))),
            NeutronSecurityRuleConstants.DJC_MAX_RETRIES);
    }

    @Override
    protected NeutronSecurityGroupListener getDataTreeChangeListener() {
        return this;
    }

    private InstanceIdentifier<Acl> getAclInstanceIdentifier(SecurityGroup securityGroup) {
        return InstanceIdentifier
            .builder(AccessLists.class).child(Acl.class,
                new AclKey(securityGroup.key().getUuid().getValue(), NeutronSecurityRuleConstants.ACLTYPE))
            .build();
    }

    private AclBuilder toAclBuilder(SecurityGroup securityGroup) {
        AclBuilder aclBuilder = new AclBuilder();
        aclBuilder.setAclName(securityGroup.key().getUuid().getValue());
        aclBuilder.setAclType(NeutronSecurityRuleConstants.ACLTYPE);
        aclBuilder.setAccessListEntries(new AccessListEntriesBuilder().setAce(new ArrayList<>()).build());

        return aclBuilder;
    }
}
