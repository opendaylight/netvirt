/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.simulator.ovs;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.northbound.NeutronPort;
import org.opendaylight.netvirt.stfw.simulator.openflow.InventoryState;
import org.opendaylight.netvirt.stfw.utils.MdsalUtils;
import org.opendaylight.netvirt.stfw.utils.NeutronUtils;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagedNodeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagedNodeEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagerEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OvsSimulator {

    private static final Logger LOG = LoggerFactory.getLogger(OvsSimulator.class);
    private DataBroker dataBroker;
    private InventoryState inventoryState = InventoryState.getInstance();
    private HashMap<IpAddress, OvsdbSwitch> ovsdbSwitches;
    private HashMap<NodeId, IpAddress> nodeToSwitchMap;
    private Map<String, Integer> dpnToFlowsCount;
    private ConcurrentHashMap<String, Integer> interfacesCount;

    @Inject
    public OvsSimulator(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        ovsdbSwitches = new HashMap<IpAddress, OvsdbSwitch>();
        nodeToSwitchMap = new HashMap<NodeId, IpAddress>();
        dpnToFlowsCount = new HashMap<String, Integer>();
        interfacesCount = new ConcurrentHashMap<>();
        LOG.info("OvsSimulator Started");
    }

    public boolean addSwitches(int count) {
        Preconditions.checkArgument(count > 0);
        LOG.debug("creating {} OVS Switches", count);
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txBridges = dataBroker.newWriteOnlyTransaction();
        int currentSize = ovsdbSwitches.size();
        for (int i = currentSize + 1; i <= currentSize + count; i++) {
            OvsdbSwitch ovsSwitch = addSwitch(tx, RandomUtils.createIp(i));
            addBridge(txBridges, ovsSwitch, "br-int");
        }
        return (MdsalUtils.submitTransaction(tx) && MdsalUtils.submitTransaction(txBridges));
    }

    public boolean deleteSwitches() {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        WriteTransaction bridgeTx = dataBroker.newWriteOnlyTransaction();
        for (IpAddress ipAddress : ovsdbSwitches.keySet()) {
            OvsdbSwitch ovsSwitch = ovsdbSwitches.get(ipAddress);
            HashMap<String, OvsBridge> ovsdbBridges = ovsSwitch.getBridges();
            if (ovsdbBridges != null) {
                for (OvsBridge ovsdbBridge : ovsdbBridges.values()) {
                    deleteBridge(bridgeTx, ovsdbBridge);
                }
            }
            deleteSwitch(tx, ovsSwitch);
        }
        ovsdbSwitches.clear();
        return (MdsalUtils.submitTransaction(tx) && MdsalUtils.submitTransaction(bridgeTx));
    }

    public OvsdbSwitch addSwitch(WriteTransaction tx, String ipaddr) {
        OvsdbSwitch ovsSwitch = new OvsdbSwitch(new IpAddress(ipaddr.toCharArray()));
        ovsSwitch.setManager(false);
        List<ManagerEntry> mgrList = new ArrayList<ManagerEntry>();
        mgrList.add(ovsSwitch.getManager());

        OvsdbNodeAugmentationBuilder ovsdbNodeBuilder = new OvsdbNodeAugmentationBuilder();
        ovsdbNodeBuilder.setConnectionInfo(ovsSwitch.getConnectionInfo());
        ovsdbNodeBuilder.setManagerEntry(mgrList);
        OpenvswitchOtherConfigs otherConfig = new OpenvswitchOtherConfigsBuilder()
            .setOtherConfigKey(OvsConstants.LOCAL_IP).setOtherConfigValue(ipaddr).build();
        ovsdbNodeBuilder.setOpenvswitchOtherConfigs(Arrays.asList(otherConfig));
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(ovsSwitch.getNodeIid().firstKeyOf(Node.class).getNodeId());
        nodeBuilder.addAugmentation(OvsdbNodeAugmentation.class, ovsdbNodeBuilder.build());
        tx.merge(LogicalDatastoreType.OPERATIONAL, ovsSwitch.getNodeIid(), nodeBuilder.build());
        ovsdbSwitches.put(ovsSwitch.getKey(), ovsSwitch);
        nodeToSwitchMap.put(ovsSwitch.getNodeId(), ovsSwitch.getKey());
        return ovsSwitch;
    }


    public void addBridge(WriteTransaction tx, OvsdbSwitch ovsSwitch, String bridgeName) {
        OvsBridge bridge = new OvsBridge(bridgeName);
        bridge.setManagedBy(new OvsdbNodeRef(ovsSwitch.getNodeIid()));
        bridge.setBridgeIid(ovsSwitch);

        // Update the connection node to let it know it manages this bridge
        Node connectionNode = buildConnectionNode(ovsSwitch, bridge);
        tx.merge(LogicalDatastoreType.OPERATIONAL, ovsSwitch.getNodeIid(), connectionNode);

        // Update the bridge node with whatever data we are getting
        Node bridgeNode = buildBridgeNode(bridge);
        tx.merge(LogicalDatastoreType.OPERATIONAL, bridge.getBridgeIid(), bridgeNode);
        ovsSwitch.addBridge(bridge);
        nodeToSwitchMap.put(bridge.getNodeId(), ovsSwitch.getKey());
        inventoryState.addNode(tx, bridge);
    }

    public void deleteSwitch(WriteTransaction tx, OvsdbSwitch ovsdbSwitch) {
        tx.delete(LogicalDatastoreType.OPERATIONAL, ovsdbSwitch.getNodeIid());
    }

    public void deleteBridge(WriteTransaction tx, OvsBridge ovsBridge) {
        tx.delete(LogicalDatastoreType.OPERATIONAL, ovsBridge.getBridgeIid());
        inventoryState.deleteNode(tx, ovsBridge);
    }

    private Node buildConnectionNode(OvsdbSwitch ovsSwitch, OvsBridge bridge) {
        // Update node with managed node reference
        NodeBuilder connectionNode = new NodeBuilder();
        connectionNode.setNodeId(ovsSwitch.getNodeId());

        OvsdbNodeAugmentationBuilder ovsdbConnectionAugmentationBuilder = new OvsdbNodeAugmentationBuilder();
        List<ManagedNodeEntry> managedBridges = new ArrayList<>();
        ManagedNodeEntry managedBridge =
            new ManagedNodeEntryBuilder().setBridgeRef(new OvsdbBridgeRef(bridge.getBridgeIid())).build();
        managedBridges.add(managedBridge);
        ovsdbConnectionAugmentationBuilder.setManagedNodeEntry(managedBridges);

        connectionNode.addAugmentation(OvsdbNodeAugmentation.class, ovsdbConnectionAugmentationBuilder.build());

        LOG.debug("Update node with bridge node ref {}", ovsdbConnectionAugmentationBuilder.getManagedNodeEntry()
            .iterator().next());
        return connectionNode.build();
    }

    private Node buildBridgeNode(OvsBridge bridge) {
        NodeBuilder bridgeNodeBuilder = new NodeBuilder();
        NodeId bridgeNodeId = bridge.getNodeId();
        bridgeNodeBuilder.setNodeId(bridgeNodeId);
        OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentationBuilder = new OvsdbBridgeAugmentationBuilder();
        ovsdbBridgeAugmentationBuilder.setBridgeName(new OvsdbBridgeName(bridge.getName()));
        ovsdbBridgeAugmentationBuilder.setBridgeUuid(bridge.getUuid());
        ovsdbBridgeAugmentationBuilder.setDatapathId(bridge.getDatapathId());
        ovsdbBridgeAugmentationBuilder.setControllerEntry(Arrays.asList(bridge.getControllerEntry()));
        //TODO: Fill in any other information that is needed by us
        /*setDataPathType(ovsdbBridgeAugmentationBuilder, bridge);
        setProtocol(ovsdbBridgeAugmentationBuilder, bridge);
        setExternalIds(ovsdbBridgeAugmentationBuilder, bridge);
        setOtherConfig(ovsdbBridgeAugmentationBuilder, bridge);
        setFailMode(ovsdbBridgeAugmentationBuilder, bridge);
        setOpenFlowNodeRef(ovsdbBridgeAugmentationBuilder, bridge);*/
        ovsdbBridgeAugmentationBuilder.setManagedBy(bridge.getManagedBy());
        bridgeNodeBuilder.addAugmentation(OvsdbBridgeAugmentation.class, ovsdbBridgeAugmentationBuilder.build());

        return bridgeNodeBuilder.build();
    }

    private InstanceIdentifier<TerminationPoint> getInstanceIdentifier(InstanceIdentifier<Node> bridgeIid,
                                                                       OvsPort port) {
        return bridgeIid.child(TerminationPoint.class, new TerminationPointKey(new TpId(port.getName())));
    }

    public OvsdbSwitch getSwitchFromNodeId(NodeId nodeId) {
        IpAddress ipAddr = nodeToSwitchMap.get(nodeId);
        return ovsdbSwitches.get(ipAddr);
    }

    public OvsdbSwitch getOvsSwitch(IpAddress ipaddr) {
        return ovsdbSwitches.get(ipaddr);
    }

    public HashMap<IpAddress, OvsdbSwitch> getOvsSwitches() {
        return ovsdbSwitches;
    }

    public DataBroker getBroker() {
        return dataBroker;
    }

    public void incrementDpnToFlowCount(String dpnId) {
        dpnToFlowsCount.putIfAbsent(dpnId, 0);
        dpnToFlowsCount.put(dpnId, dpnToFlowsCount.get(dpnId) + 1);
    }

    public void decrementDpnToFlowCount(String dpnId) {
        if (dpnToFlowsCount.get(dpnId) == null) {
            return;
        }
        dpnToFlowsCount.put(dpnId, dpnToFlowsCount.get(dpnId) - 1);
    }

    public Map<String, Integer> getDpnToFlowCount() {
        return dpnToFlowsCount;
    }

    public void incrementInterfacesCount(String interfaceType) {
        interfacesCount.putIfAbsent(interfaceType, 0);
        interfacesCount.put(interfaceType, interfacesCount.get(interfaceType) + 1);
    }

    public void decrementInterfacesCount(String interfaceType) {
        if (interfacesCount.get(interfaceType) == null) {
            return;
        }
        interfacesCount.put(interfaceType, interfacesCount.get(interfaceType) - 1);
    }

    public ConcurrentHashMap<String, Integer> getInterfacesCount() {
        return interfacesCount;
    }

    public void addPort(WriteTransaction txTopo, WriteTransaction txInv, OvsBridge ovsBridge, NeutronPort port) {
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        String vifPortName = NeutronUtils.getVifPortName(port);
        if (vifPortName == null) {
            LOG.warn("VifPortName for {} returned null", port.getPortId());
            return;
        }
        tpBuilder.setTpId(new TpId(vifPortName));
        OvsdbTerminationPointAugmentationBuilder ovsTpAugBuilder = new OvsdbTerminationPointAugmentationBuilder();
        List<InterfaceExternalIds> exIds = new ArrayList<>();
        InterfaceExternalIds exId = new InterfaceExternalIdsBuilder().setExternalIdKey("iface-id")
            .setExternalIdValue(port.getPortId().getValue()).build();
        exIds.add(exId);
        ovsTpAugBuilder.setName(vifPortName).setInterfaceExternalIds(exIds);
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, ovsTpAugBuilder.build());
        addPort(txTopo, txInv, ovsBridge, tpBuilder.build());
    }

    public void addPort(WriteTransaction topoTx, WriteTransaction invTx, OvsBridge bridge, TerminationPoint tp) {
        OvsPort port = new OvsPort(tp.getTpId().getValue());
        port.setOfPortId(bridge.getPorts().size() + 1);
        bridge.addPort(port);
        topoTx.merge(LogicalDatastoreType.OPERATIONAL, getInstanceIdentifier(bridge.getBridgeIid(), port), tp);
        inventoryState.addNodeConnector(invTx, bridge, port);
    }

    public void delPort(WriteTransaction topoTx, WriteTransaction invTx, OvsBridge bridge, TerminationPoint tp) {
        OvsPort port = bridge.getPort(tp.getTpId().getValue());
        if (port == null) {
            return;
        }
        topoTx.delete(LogicalDatastoreType.OPERATIONAL, getInstanceIdentifier(bridge.getBridgeIid(), port));
        inventoryState.deleteNodeConnector(invTx, bridge, port);
    }

    public void delPort(WriteTransaction txTopo, WriteTransaction txInv, OvsBridge ovsBridge, NeutronPort port) {
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        String vifPortName = NeutronUtils.getVifPortName(port);
        if (vifPortName == null) {
            return;
        }
        tpBuilder.setTpId(new TpId(vifPortName));
        delPort(txTopo, txInv, ovsBridge, tpBuilder.build());
    }
}

