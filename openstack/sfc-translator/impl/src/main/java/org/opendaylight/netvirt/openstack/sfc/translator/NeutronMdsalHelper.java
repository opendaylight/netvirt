/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions to read Neutron models (e.g network, subnet, port, sfc flow classifier
 * port pair, port group, port chain) from md-sal data store.
 */
public class NeutronMdsalHelper {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronMdsalHelper.class);
    private static final InstanceIdentifier<Ports> portsPairIid =
            InstanceIdentifier.create(Neutron.class).child(Ports.class);


    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;

    public NeutronMdsalHelper(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        mdsalUtils = new MdsalUtils(this.dataBroker);
    }

    public Port getNeutronPort(Uuid portId) {
        Port neutronPort = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION , getNeutronPortPath(portId));
        return neutronPort;
    }

    private InstanceIdentifier<Port> getNeutronPortPath(Uuid portId) {
        return portsPairIid.builder().child(Port.class, new PortKey(portId)).build();
    }
}
