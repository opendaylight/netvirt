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
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenDaylight Neutron Port Pair yang models data change listener.
 */
public class NeutronPortPairListener extends DelegatingDataTreeListener<PortPair> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortPairListener.class);

    private static final InstanceIdentifier<PortPair> PORT_PAIR_IID =
            InstanceIdentifier.create(Neutron.class).child(PortPairs.class).child(PortPair.class);

    private final SfcMdsalHelper sfcMdsalHelper;

    public NeutronPortPairListener(DataBroker db) {
        super(db,new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, PORT_PAIR_IID));
        sfcMdsalHelper = new SfcMdsalHelper(db);
    }

    /**
     * Method removes PortPair which is identified by InstanceIdentifier.
     *
     * @param deletedPortPair        - PortPair for removing
     */
    @Override
    public void remove(PortPair deletedPortPair) {
        LOG.info("Received remove port pair event {}", deletedPortPair);

        ServiceFunctionKey sfKey = PortPairTranslator.getSFKey(deletedPortPair);
        LOG.info("Removing service function {}", sfKey);
        sfcMdsalHelper.removeServiceFunction(sfKey);

        ServiceFunctionForwarder sff;
        ServiceFunctionForwarder updatedSff;
        SffName sffName = new SffName(SfcMdsalHelper.NETVIRT_LOGICAL_SFF_NAME);
        sff = sfcMdsalHelper.readServiceFunctionForwarder(new ServiceFunctionForwarderKey(sffName));
        updatedSff = PortPairGroupTranslator.removePortPairFromServiceFunctionForwarder(sff, deletedPortPair);
        LOG.info("Updating service function forwarder as {}", updatedSff);
        sfcMdsalHelper.addServiceFunctionForwarder(updatedSff);
    }

    /**
     * Method updates the original PortPair to the update PortPair.
     * Both are identified by same InstanceIdentifier.
     *
     * @param origPortPair     - original PortPair
     * @param updatePortPair     - changed PortPair (contain updates)
     */
    @Override
    public void update(PortPair origPortPair, PortPair updatePortPair) {
        //NO-OP
    }

    /**
     * Method adds the PortPair which is identified by InstanceIdentifier
     * to device.
     *
     * @param newPortPair        - new PortPair
     */
    @Override
    public void add(PortPair newPortPair) {
        //NO-OP
        // Port Pair data written in neutron data store will be used
        // When user will create port chain.
    }
}
