/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class QosNeutronMappings {
    public static ConcurrentHashMap<Uuid, QosPolicy> qosPolicyMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Port>> qosPortsMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Network>> qosNetworksMap = new ConcurrentHashMap<>();

    public static void addToQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.put(qosPolicy.getUuid(),qosPolicy);
    }

    public static void removeFromQosPolicyCache(QosPolicy qosPolicy) {
        qosPolicyMap.remove(qosPolicy.getUuid());
    }

    public static void addToQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid)) {
            if (!qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
                qosPortsMap.get(qosUuid).put(port.getUuid(), port);
            }
        } else {
            HashMap<Uuid, Port> portMap = new HashMap<>();
            portMap.put(port.getUuid(), port);
            qosPortsMap.put(qosUuid, portMap);
        }
    }

    public static void removeFromQosPortsCache(Uuid qosUuid, Port port) {
        if (qosPortsMap.containsKey(qosUuid) && qosPortsMap.get(qosUuid).containsKey(port.getUuid())) {
            qosPortsMap.get(qosUuid).remove(port.getUuid(), port);
        }
    }

    public static void addToQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid)) {
            if (!qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
                qosNetworksMap.get(qosUuid).put(network.getUuid(), network);
            }
        } else {

            HashMap<Uuid, Network> networkMap = new HashMap<>();
            networkMap.put(network.getUuid(), network);
            qosNetworksMap.put(qosUuid, networkMap);
        }
    }

    public static void removeFromQosNetworksCache(Uuid qosUuid, Network network) {
        if (qosNetworksMap.containsKey(qosUuid) && qosNetworksMap.get(qosUuid).containsKey(network.getUuid())) {
            qosNetworksMap.get(qosUuid).remove(network.getUuid(), network);
        }
    }
}
