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

    public NeutronPortPairListener(DataBroker db, PortPairTranslator portPairTranslator) {
        super(portPairTranslator, db,
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, portPairIid));
    }
}
