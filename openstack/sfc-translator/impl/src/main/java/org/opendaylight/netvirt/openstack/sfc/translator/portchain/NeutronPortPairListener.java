/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator.portchain;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.netvirt.openstack.sfc.translator.NeutronMdsalHelper;
import org.opendaylight.netvirt.openstack.sfc.translator.OvsdbMdsalHelper;
import org.opendaylight.netvirt.openstack.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * OpenDaylight Neutron Port Pair yang models data change listener
 */
public class NeutronPortPairListener extends DelegatingDataTreeListener<PortPair> {
    private static final InstanceIdentifier<PortPair> portPairIid =
            InstanceIdentifier.create(Neutron.class).child(PortPairs.class).child(PortPair.class);

    private final DataBroker db;
    private final SfcMdsalHelper sfcMdsalHelper;
    private final NeutronMdsalHelper neutronMdsalHelper;
    private final OvsdbMdsalHelper ovsdbMdsalHelper;

    public NeutronPortPairListener(DataBroker db) {
        super(db,new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, portPairIid));
        this.db = db;
        sfcMdsalHelper = new SfcMdsalHelper(db);
        neutronMdsalHelper = new NeutronMdsalHelper(db);
        ovsdbMdsalHelper = new OvsdbMdsalHelper(db);
    }

    /**
     * Method removes PortPair which is identified by InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param deletedPortPair        - PortPair for removing
     */
    @Override
    public void remove(InstanceIdentifier<PortPair> path, PortPair deletedPortPair) {
        sfcMdsalHelper.removeServiceFunction(PortPairTranslator.getSFKey(deletedPortPair));
    }

    /**
     * Method updates the original PortPair to the update PortPair.
     * Both are identified by same InstanceIdentifier.
     *
     * @param path - the whole path to PortPair
     * @param originalPortPair   - original PortPair (for update)
     * @param updatePortPair     - changed PortPair (contain updates)
     */
    @Override
    public void update(InstanceIdentifier<PortPair> path, PortPair originalPortPair, PortPair updatePortPair) {
        //NO-OP
    }

    /**
     * Method adds the PortPair which is identified by InstanceIdentifier
     * to device.
     *
     * @param path - the whole path to new PortPair
     * @param newPortPair        - new PortPair
     */
    @Override
    public void add(InstanceIdentifier<PortPair> path, PortPair newPortPair) {
        //NO-OP
        // Port Pair data written in neutron data store will be used
        // When user will create port chain.
    }
}
