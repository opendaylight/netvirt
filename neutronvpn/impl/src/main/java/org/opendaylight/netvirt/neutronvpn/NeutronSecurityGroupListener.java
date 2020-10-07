/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.AclserviceAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.groups.attributes.SecurityGroups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.groups.attributes.security.groups.SecurityGroup;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSecurityGroupListener extends AbstractAsyncDataTreeChangeListener<SecurityGroup> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSecurityGroupListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;
    private final NeutronSecurityGroupUtils neutronSecurityGroupUtils;

    @Inject
    public NeutronSecurityGroupListener(DataBroker dataBroker, JobCoordinator jobCoordinator,
            final NeutronSecurityGroupUtils neutronSecurityGroupUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(SecurityGroups.class).child(SecurityGroup.class),
                Executors.newSingleThreadExecutor("NeutronSecurityGroupListener", LOG));
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.neutronSecurityGroupUtils = neutronSecurityGroupUtils;
        init();
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        neutronSecurityGroupUtils.createAclIdPool();
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<SecurityGroup> key, SecurityGroup securityGroup) {
        LOG.trace("Removing securityGroup: {}", securityGroup);
        InstanceIdentifier<Acl> identifier = getAclInstanceIdentifier(securityGroup);
        String securityGroupId = securityGroup.key().getUuid().getValue();
        jobCoordinator.enqueueJob(securityGroupId, () -> {
            neutronSecurityGroupUtils.releaseAclTag(securityGroupId);
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> tx.delete(identifier)));
        });
    }

    @Override
    public void update(InstanceIdentifier<SecurityGroup> key, SecurityGroup dataObjectModificationBefore,
        SecurityGroup dataObjectModificationAfter) {
        if (Objects.equals(dataObjectModificationBefore, dataObjectModificationAfter)) {
            return;
        }
        LOG.debug("Do nothing");
    }

    @Override
    public void add(InstanceIdentifier<SecurityGroup> instanceIdentifier, SecurityGroup securityGroup) {
        LOG.trace("Adding securityGroup: {}", securityGroup);
        String securityGroupId = securityGroup.key().getUuid().getValue();
        InstanceIdentifier<Acl> identifier = getAclInstanceIdentifier(securityGroup);
        jobCoordinator.enqueueJob(securityGroupId, () -> {
            Integer aclTag = neutronSecurityGroupUtils.allocateAclTag(securityGroupId);
            Acl acl = toAclBuilder(securityGroup, aclTag).build();
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> tx.mergeParentStructurePut(identifier, acl)));
        });
    }

    private InstanceIdentifier<Acl> getAclInstanceIdentifier(SecurityGroup securityGroup) {
        return InstanceIdentifier
            .builder(AccessLists.class).child(Acl.class,
                new AclKey(securityGroup.key().getUuid().getValue(), NeutronSecurityGroupConstants.ACLTYPE))
            .build();
    }

    private AclBuilder toAclBuilder(SecurityGroup securityGroup, Integer aclTag) {
        AclBuilder aclBuilder = new AclBuilder();
        aclBuilder.setAclName(securityGroup.key().getUuid().getValue());
        aclBuilder.setAclType(NeutronSecurityGroupConstants.ACLTYPE);
        aclBuilder.setAccessListEntries(new AccessListEntriesBuilder().setAce(new ArrayList<>()).build());
        if (aclTag != NeutronSecurityGroupConstants.INVALID_ACL_TAG) {
            AclserviceAugmentationBuilder aclserviceAugmentationBuilder = new AclserviceAugmentationBuilder();
            aclserviceAugmentationBuilder.setAclTag(aclTag);
            aclBuilder.addAugmentation(aclserviceAugmentationBuilder.build());
        }

        return aclBuilder;
    }
}
