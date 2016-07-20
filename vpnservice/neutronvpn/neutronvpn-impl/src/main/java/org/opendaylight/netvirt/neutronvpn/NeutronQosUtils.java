/*
 * Copyright (c) 2016 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronQosUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronQosPolicyChangeListener.class);

    public static void handleNeutronPortQosUpdate(Port port, Uuid qosUuid) {
        LOG.info("Handling Port QoS update: {}", port, qosUuid);
    }

    public static void handleNeutronPortQosRemove(Port port, Uuid qosUuid) {
        LOG.info("Handling Port QoS removal: {}", port, qosUuid);
    }

    public static void handleNeutronNetworkQosUpdate(Network network, Uuid qosUuid) {
        LOG.info("Handling Port QoS update: {}", network, qosUuid);
    }

    public static void handleNeutronNetworkQosRemove(Network network, Uuid qosUuid) {
        LOG.info("Handling Port QoS removal: {}", network, qosUuid);
    }

}
