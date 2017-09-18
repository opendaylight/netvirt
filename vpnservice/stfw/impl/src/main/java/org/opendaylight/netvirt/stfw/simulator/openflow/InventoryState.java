/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.simulator.openflow;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsBridge;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsPort;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.port.rev130925.PortNumberUni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.port.rev130925.flow.capable.port.StateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryState {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryState.class);
    private static final InventoryState INVENTORY_STATE = new InventoryState();

    private InventoryState() {
    }

    public static InventoryState getInstance() {
        return INVENTORY_STATE;
    }

    public void addNode(WriteTransaction transaction, OvsBridge ovsBridge) {
        NodeId nodeId = ovsBridge.getOpenflowNodeName();
        InstanceIdentifier<Node> id =
            InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(nodeId))
                .build();
        List<NodeConnector> ncList = new ArrayList<>();
        NodeBuilder inventoryBuilder = new NodeBuilder().setKey(new NodeKey(nodeId))
            .setId(nodeId).setNodeConnector(ncList);
        transaction.put(LogicalDatastoreType.OPERATIONAL, id, inventoryBuilder.build(), true);
    }

    public void addNodeConnector(WriteTransaction transaction, OvsBridge ovsBridge, OvsPort ovsPort) {
        NodeId nodeId = ovsBridge.getOpenflowNodeName();
        Uuid portUuid = ovsPort.getUuid();
        String macAddress = RandomUtils.createMac(portUuid);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(nodeId.getValue() + ":" + (ovsPort.getOfPortId()));
        InstanceIdentifier<NodeConnector> id = InstanceIdentifier
            .builder(Nodes.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(
                    nodeId))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey(
                    nodeConnectorId)).build();
        NodeConnectorBuilder nodeConnectorBuilder = new NodeConnectorBuilder().setId(nodeConnectorId)
            .setKey(new NodeConnectorKey(nodeConnectorId));
        FlowCapableNodeConnectorBuilder flowCapableNc = new FlowCapableNodeConnectorBuilder()
            .setHardwareAddress(new MacAddress(macAddress));
        flowCapableNc.setPortNumber(new PortNumberUni(new Long(ovsPort.getOfPortId()))).setName(ovsPort.getName())
            .setState(new StateBuilder().setLive(true).setBlocked(false).setLinkDown(false).build());
        nodeConnectorBuilder.addAugmentation(FlowCapableNodeConnector.class, flowCapableNc.build());
        transaction.put(LogicalDatastoreType.OPERATIONAL, id, nodeConnectorBuilder.build(), true);
    }

    public void deleteNode(WriteTransaction transaction, OvsBridge ovsBridge) {
        NodeId nodeId = ovsBridge.getOpenflowNodeName();
        InstanceIdentifier<Node> id =
            InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(nodeId))
                .build();
        transaction.delete(LogicalDatastoreType.OPERATIONAL, id);
    }

    public void deleteNodeConnector(WriteTransaction transaction, OvsBridge ovsBridge, OvsPort ovsPort) {
        NodeId nodeId = ovsBridge.getOpenflowNodeName();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(nodeId.getValue() + ":" + (ovsPort.getOfPortId()));
        InstanceIdentifier<NodeConnector> id = InstanceIdentifier
            .builder(Nodes.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(
                    nodeId))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey(
                    nodeConnectorId)).build();
        transaction.delete(LogicalDatastoreType.OPERATIONAL, id);
    }
}

