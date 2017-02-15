/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;


import com.google.common.base.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class QosNeutronUtils {
    public static ConcurrentHashMap<Uuid, QosPolicy> qosPolicyMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Port>> qosPortsMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, HashMap<Uuid, Network>> qosNetworksMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, Network> networkMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Uuid, Port> portMap = new ConcurrentHashMap<>();

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

    public static void addToPortCache(Port port) {
        portMap.put(port.getUuid(), port);
    }

    public static void removeFromPortCache(Port port) {
        portMap.remove(port.getUuid());
    }

    public static void addToNetworkCache(Network network) {
        networkMap.put(network.getUuid(), network);
    }

    public static void removeFromNetworkCache(Network network) {
        networkMap.remove(network.getUuid());
    }

    public static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<NetworkMap> networkMapId = InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class, new NetworkMapKey(networkId)).build();
        Optional<NetworkMap> optionalNetworkMap = read(broker, LogicalDatastoreType.CONFIGURATION, networkMapId);
        return optionalNetworkMap.isPresent() ? optionalNetworkMap.get().getSubnetIdList() : null;
    }

    protected static List<Uuid> getPortIdsFromSubnetId(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetMapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        Optional<Subnetmap> optionalSubnetmap = read(broker, LogicalDatastoreType.CONFIGURATION, subnetMapId);
        return optionalSubnetmap.isPresent() ? optionalSubnetmap.get().getPortList() : null;
    }

    protected static Network getNeutronNetwork(DataBroker broker, Uuid networkId) {
        Network network = null;
        network = networkMap.get(networkId);
        if (network != null) {
            return network;
        }
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(Networks.class)
                .child(Network.class, new NetworkKey(networkId));
        Optional<Network> net = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (net.isPresent()) {
            network = net.get();
        }
        return network;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        try {
            return tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
