/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.simulator.ovs;

import java.util.HashMap;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ConnectionInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ConnectionInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagerEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagerEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class OvsdbSwitch {
    Uuid uuid;
    HashMap<String, OvsBridge> bridges;
    private IpAddress switchIp;
    private InstanceIdentifier<Node> nodeIid;
    private ManagerEntry manager;
    private ConnectionInfo connectionInfo;
    private NodeId nodeId;

    public OvsdbSwitch(IpAddress ipaddr) {
        uuid = RandomUtils.createUuid();
        bridges = new HashMap<String, OvsBridge>();
        switchIp = ipaddr;
    }

    public Uuid getUuid() {
        return uuid;
    }

    public IpAddress getKey() {
        return switchIp;
    }

    public void addBridge(OvsBridge bridge) {
        bridge.setSwitchKey(this.switchIp);
        bridges.put(bridge.getName(), bridge);
    }

    public HashMap<String, OvsBridge> getBridges() {
        return bridges;
    }

    public OvsBridge getBridge(String bridgeName) {
        return bridges.get(bridgeName);
    }

    public ManagerEntry getManager() {
        return manager;
    }

    public void setManager(ManagerEntry manager) {
        this.manager = manager;
    }

    public void setManager(boolean isPassive) {
        String target = null;
        if (isPassive) {
            target = OvsConstants.PASSIVE_TARGET;
        } else {
            target = OvsConstants.ACTIVE_TARGET;
            setConnectionInfo(RandomUtils.createPortNumber(), OvsConstants.ODL_PORT);
            setNodeIid();
        }
        ManagerEntryBuilder meBuilder = new ManagerEntryBuilder();
        manager = meBuilder.setTarget(new Uri(target)).setConnected(!isPassive).setNumberOfConnections(1L).build();
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public void setConnectionInfo(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
    }

    public void setConnectionInfo(Integer localPort, Integer remotePort) {
        ConnectionInfoBuilder cibb = new ConnectionInfoBuilder();
        cibb.setLocalIp(switchIp).setLocalPort(new PortNumber(localPort));
        cibb.setRemoteIp(OvsConstants.ODL_IP_ADDR).setRemotePort(new PortNumber(remotePort));
        this.connectionInfo = cibb.build();
    }

    public InstanceIdentifier<Node> getNodeIid() {
        return nodeIid;
    }

    private void setNodeIid() {
        //TODO currently only doing Active, add support for passive.
        String nodeString = OvsConstants.OVS_ACTIVE_URI_PREFIX + uuid.getValue();
        NodeId nodeId = new NodeId(new Uri(nodeString));
        this.nodeId = nodeId;
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(OvsConstants.OVS_TOPOLOGY_ID);
        nodeIid = InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, topoKey)
            .child(Node.class, nodeKey)
            .build();
    }

    public NodeId getNodeId() {
        return nodeId;
    }

}
