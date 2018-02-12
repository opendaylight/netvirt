/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.infrautils.utils.concurrent.Executors;
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
public class NeutronSecurityGroupListener extends AbstractAsyncDataTreeChangeListener<SecurityGroup> {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronSecurityGroupListener.class);
    private final DataBroker dataBroker;

    @Inject
    public NeutronSecurityGroupListener(DataBroker dataBroker) {
        super(dataBroker,
                LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(Neutron.class)
                        .child(SecurityGroups.class)
                        .child(SecurityGroup.class)
                        .build(),
                Executors.newSingleThreadExecutor("NeutronSecurityGroupListener", LOG));

        this.dataBroker = dataBroker;
    }

    @Override
    public void add(@Nonnull SecurityGroup newSecurityGroup) {
        // nop: ACLs will be added through security rule listener
    }

    @Override
    public void remove(@Nonnull SecurityGroup removedSecurityGroup) {
        LOG.trace("removed securityGroup: {}", removedSecurityGroup);
        AclKey aclKey = new AclKey(removedSecurityGroup.getUuid().getValue(), NeutronSecurityRuleConstants.ACLTYPE);
        InstanceIdentifier<Acl> aclInstanceIdentifier;
        aclInstanceIdentifier = InstanceIdentifier.builder(AccessLists.class).child(Acl.class, aclKey).build();
        SingleTransactionDataBroker singleTransactionDataBroker = new SingleTransactionDataBroker(dataBroker);
        try {
            singleTransactionDataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION, aclInstanceIdentifier);
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Exception occurred while removing acl for security group: {}", removedSecurityGroup, e);
        }
    }

    @Override
    public void update(@Nonnull SecurityGroup originalSecurityGroup, @Nonnull SecurityGroup updatedSecurityGroup) {
        // nop: ACLs will be updated through security rule listener
    }
}
