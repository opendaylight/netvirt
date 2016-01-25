/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SouthboundUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SouthboundUtils.class);
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));

    public static void addPortToBridge(InstanceIdentifier<?> bridgeIid, Interface iface,
                                       OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                       String portName, DataBroker dataBroker, List<ListenableFuture<Void>> futures) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
        if (ifTunnel != null) {
            addTunnelPortToBridge(ifTunnel, bridgeIid, iface, bridgeAugmentation, bridgeName, portName, dataBroker, tx);
        }

        IfL2vlan ifL2vlan = iface.getAugmentation(IfL2vlan.class);
        if (ifL2vlan != null) {
            addVlanPortToBridge(bridgeIid, ifL2vlan, bridgeAugmentation, bridgeName, portName, dataBroker, tx);
        }
        futures.add(tx.submit());
    }

    public static void addBridge(InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                 OvsdbBridgeAugmentation bridgeAugmentation,
                                 DataBroker dataBroker, List<ListenableFuture<Void>> futures){
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        NodeId nodeId = InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)).getNodeId();
        NodeBuilder bridgeNodeBuilder = new NodeBuilder();
        bridgeNodeBuilder.setNodeId(nodeId);
        bridgeNodeBuilder.addAugmentation(OvsdbBridgeAugmentation.class, bridgeAugmentation);
        tx.put(LogicalDatastoreType.CONFIGURATION, createNodeInstanceIdentifier(nodeId), bridgeNodeBuilder.build(), true);
        futures.add(tx.submit());
    }

    public static void deleteBridge(InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                 DataBroker dataBroker, List<ListenableFuture<Void>> futures){
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        NodeId nodeId = InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)).getNodeId();
        tx.delete(LogicalDatastoreType.CONFIGURATION, createNodeInstanceIdentifier(nodeId));
        futures.add(tx.submit());
    }

    private static void addVlanPortToBridge(InstanceIdentifier<?> bridgeIid, IfL2vlan ifL2vlan,
                                              OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                              String portName, DataBroker dataBroker, WriteTransaction t) {
        int vlanId = ifL2vlan.getVlanId().getValue();
        addTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, portName, vlanId, null, null, dataBroker, t);
    }

    private static void addTunnelPortToBridge(IfTunnel ifTunnel, InstanceIdentifier<?> bridgeIid, Interface iface,
                                             OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                             String portName, DataBroker dataBroker, WriteTransaction t) {
        Class type = null;
        LOG.debug("adding tunnel port {} to bridge {}",portName, bridgeName);

        if (ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeGre.class) ||
                ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeMplsOverGre.class)) {
            type = InterfaceTypeGre.class;
        } else if (ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            type = InterfaceTypeVxlan.class;
        }

        if (type == null) {
            LOG.warn("Unknown Tunnel Type obtained while creating interface: {}", iface);
            return;
        }

        int vlanId = 0;
        IfL2vlan ifL2vlan = iface.getAugmentation(IfL2vlan.class);
        if (ifL2vlan != null) {
            vlanId = ifL2vlan.getVlanId().getValue();
        }

        Map<String, String> options = Maps.newHashMap();
        options.put("key", "flow");

        IpAddress localIp = ifTunnel.getTunnelSource();
        options.put("local_ip", localIp.getIpv4Address().getValue());

        IpAddress remoteIp = ifTunnel.getTunnelDestination();
        options.put("remote_ip", remoteIp.getIpv4Address().getValue());

        addTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, portName, vlanId, type, options, dataBroker, t);
    }

    private static void addTerminationPoint(InstanceIdentifier<?> bridgeIid, OvsdbBridgeAugmentation bridgeNode,
                                            String bridgeName, String portName, int vlanId, Class type,
                                            Map<String, String> options, DataBroker dataBroker, WriteTransaction t) {
        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), portName);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();

        tpAugmentationBuilder.setName(portName);

        if (type != null) {
            tpAugmentationBuilder.setInterfaceType(type);
        }

        if (options != null) {
            List<Options> optionsList = new ArrayList<Options>();
            for (Map.Entry<String, String> entry : options.entrySet()) {
                OptionsBuilder optionsBuilder = new OptionsBuilder();
                optionsBuilder.setKey(new OptionsKey(entry.getKey()));
                optionsBuilder.setOption(entry.getKey());
                optionsBuilder.setValue(entry.getValue());
                optionsList.add(optionsBuilder.build());
            }
            tpAugmentationBuilder.setOptions(optionsList);
        }

        if (vlanId != 0) {
                tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Access);
                tpAugmentationBuilder.setVlanTag(new VlanId(vlanId));
        }

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

        t.put(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);
    }

    private static InstanceIdentifier<TerminationPoint> createTerminationPointInstanceIdentifier(Node node,
                                                                                                 String portName){
        InstanceIdentifier<TerminationPoint> terminationPointPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class,node.getKey())
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(portName)));

        LOG.debug("Termination point InstanceIdentifier generated : {}", terminationPointPath);
        return terminationPointPath;
    }

    public static InstanceIdentifier<TerminationPoint> createTerminationPointInstanceIdentifier(NodeKey nodekey,
                                                                                                String portName){
        InstanceIdentifier<TerminationPoint> terminationPointPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class,nodekey)
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(portName)));

        LOG.debug("Termination point InstanceIdentifier generated : {}",terminationPointPath);
        return terminationPointPath;
    }

    public static InstanceIdentifier<Node> createNodeInstanceIdentifier(NodeId nodeId) {
        return InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class,new NodeKey(nodeId));
    }
}