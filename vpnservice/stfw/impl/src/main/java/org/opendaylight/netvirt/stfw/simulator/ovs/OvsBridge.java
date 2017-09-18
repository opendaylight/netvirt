/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.simulator.ovs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ControllerEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ControllerEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ProtocolEntry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class OvsBridge {
    private Uuid uuid;

    private String name;
    private IpAddress switchKey;
    private Class<? extends DatapathTypeBase> datapathType;
    private DatapathId datapathId;
    private OvsdbNodeRef managedBy;
    private InstanceIdentifier<Node> bridgeIid;
    private NodeId bridgeNodeId;
    private BigInteger openflowNodeId;
    private org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId openflowNodeName;
    private HashMap<String, OvsPort> ports;
    private ControllerEntry controller;
    /*Set<Uuid> controller;*/
    private List<ProtocolEntry> protocols;
    private List<BridgeOtherConfigs> otherConfigs;
    private List<BridgeExternalIds> externalIds;

    public OvsBridge() {
        this(OvsConstants.DEFAULT_BRIDGE);
    }

    public OvsBridge(String name) {
        uuid = RandomUtils.createUuid();
        this.name = name;
        setDatapathId(new DatapathId(RandomUtils.createDatapathId()));
        openflowNodeId = RandomUtils.createOpenFlowDpnId(datapathId);
        openflowNodeName =
            new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("openflow:" + openflowNodeId);
        ports = new HashMap<String, OvsPort>();
        protocols = new ArrayList<ProtocolEntry>();
        otherConfigs = new ArrayList<BridgeOtherConfigs>();
        externalIds = new ArrayList<BridgeExternalIds>();
        this.setControllerEntry(OvsConstants.CONTROLLER_TARGET);
    }

    public Uuid getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public DatapathId getDatapathId() {
        return datapathId;
    }

    public BigInteger getOpenflowNodeId() {
        return openflowNodeId;
    }

    public org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId getOpenflowNodeName() {
        return openflowNodeName;
    }

    public void setDatapathId(DatapathId datapathId) {
        this.datapathId = datapathId;
    }

    public int addPort(OvsPort port) {
        ports.put(port.getName(), port);
        return ports.size();
    }

    public OvsdbNodeRef getManagedBy() {
        return managedBy;
    }

    public void setManagedBy(OvsdbNodeRef managedBy) {
        this.managedBy = managedBy;
    }

    public InstanceIdentifier<Node> getBridgeIid() {
        return bridgeIid;
    }

    public void setBridgeIid(InstanceIdentifier<Node> bridgeIid) {
        this.bridgeIid = bridgeIid;
    }

    public void setBridgeIid(OvsdbSwitch ovsSwitch) {
        InstanceIdentifier<Node> iid;
        String nodeString = ovsSwitch.getNodeId().getValue() + "/bridge/" + this.getName();
        NodeId nodeId = new NodeId(new Uri(nodeString));
        this.bridgeNodeId = nodeId;
        NodeKey nodeKey = new NodeKey(nodeId);
        iid = InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(OvsConstants.OVS_TOPOLOGY_ID))
            .child(Node.class, nodeKey).build();
        setBridgeIid(iid);
    }

    public NodeId getNodeId() {
        return this.bridgeNodeId;
    }

    public IpAddress getSwitchKey() {
        return switchKey;
    }

    public void setSwitchKey(IpAddress switchKey) {
        this.switchKey = switchKey;
    }

    public ControllerEntry getControllerEntry() {
        return controller;
    }

    public void setControllerEntry(String target) {
        ControllerEntryBuilder ceBuilder = new ControllerEntryBuilder();
        controller = ceBuilder.setTarget(new Uri(target)).setIsConnected(true).build();
    }

    public OvsPort getPort(String name) {
        return ports.get(name);
    }

    public HashMap<String, OvsPort> getPorts() {
        return ports;
    }
}
