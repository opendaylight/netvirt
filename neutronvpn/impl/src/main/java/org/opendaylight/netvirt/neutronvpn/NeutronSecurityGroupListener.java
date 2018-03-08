/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
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

    @Inject
    public NeutronSecurityGroupListener(final DataBroker dataBroker) {
        super(SecurityGroup.class, NeutronSecurityGroupListener.class);
        this.dataBroker = dataBroker;
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
    protected void add(InstanceIdentifier<SecurityGroup> instanceIdentifier, SecurityGroup securityGroup) {
        LOG.trace("Received add event for securityGroup: {}", securityGroup);
        // ACLs will be added through security rule listener
    }

    private InstanceIdentifier<Acl> getAclInstanceIdentifier(SecurityGroup securityGroup) {
        return InstanceIdentifier.builder(AccessLists.class)
                .child(Acl.class, new AclKey(securityGroup.getUuid().getValue(), NeutronSecurityRuleConstants.ACLTYPE))
                .build();
    }

    @Override
    protected void remove(InstanceIdentifier<SecurityGroup> instanceIdentifier, SecurityGroup securityGroup) {
        LOG.trace("Received remove event for securityGroup: {}", securityGroup);
        try {
            InstanceIdentifier<Acl> identifier = getAclInstanceIdentifier(securityGroup);
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, identifier);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Exception occurred while removing acl for security group: {}", securityGroup, e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<SecurityGroup> instanceIdentifier, SecurityGroup oldSecurityGroup,
            SecurityGroup updatedSecurityGroup) {
        LOG.trace("Received update event for securityGroup: {}", updatedSecurityGroup);
        // ACLs will be updated through security rule listener
    }

    @Override
    protected NeutronSecurityGroupListener getDataTreeChangeListener() {
        return this;
    }
}
