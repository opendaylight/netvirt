/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator.portchain;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairGroups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * OpenDaylight Neutron Port Pair Group yang models data change listener.
 */
public class NeutronPortPairGroupListener extends DelegatingDataTreeListener<PortPairGroup> {
    private static final InstanceIdentifier<PortPairGroup> PORT_PAIR_GROUP_IID =
            InstanceIdentifier.create(Neutron.class).child(PortPairGroups.class).child(PortPairGroup.class);

    public NeutronPortPairGroupListener(DataBroker db) {
        super(db,new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, PORT_PAIR_GROUP_IID));
    }

    /**
     * Method removes PortPairGroup which is identified by InstanceIdentifier.
     *
     * @param deletedPortPairGroup        - PortPairGroup for removing
     */
    @Override
    public void remove(PortPairGroup deletedPortPairGroup) {
        //NO-OP
    }

    /**
     * Method updates the original PortPairGroup to the update PortPairGroup.
     * Both are identified by same InstanceIdentifier.
     *
     * @param origPortPairGroup     - original PortPairGroup
     * @param updatePortPairGroup     - changed PortPairGroup (contain updates)
     */
    @Override
    public void update(PortPairGroup origPortPairGroup, PortPairGroup updatePortPairGroup) {
        //NO-OP
    }

    /**
     * Method adds the PortPairGroup which is identified by InstanceIdentifier
     * to device.
     *
     * @param newPortPairGroup        - new PortPairGroup
     */
    @Override
    public void add(PortPairGroup newPortPairGroup) {
        //NO-OP
    }
}
