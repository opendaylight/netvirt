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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairGroups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * OpenDaylight Neutron Port Group yang models data change listener
 */
public class NeutronPortPairGroupListener extends DelegatingDataTreeListener<PortPairGroup> {
    private static final InstanceIdentifier<PortPairGroup> portPairGroupIid =
            InstanceIdentifier.create(Neutron.class).child(PortPairGroups.class).child(PortPairGroup.class);

    public NeutronPortPairGroupListener(DataBroker db, PortPairGroupTranslator portPairGroupTranslator) {
        super(portPairGroupTranslator, db,
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, portPairGroupIid));
    }
}
