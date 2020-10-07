/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.netvirt.elan.l2gw.nodehandlertest.NodeConnectedHandlerUtils.getUUid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.GlobalAugmentationHelper;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.TestBuilders;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.DevicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class L2gwBuilders {

    private final SingleTransactionDataBroker singleTransactionDataBroker;

    public L2gwBuilders(SingleTransactionDataBroker singleTransactionDataBroker) {
        this.singleTransactionDataBroker = singleTransactionDataBroker;
    }

    public L2gatewayConnection buildConnection(String connectionName, String l2gwName, String elan,
                                                      Integer segmentationId) {

        final L2gatewayConnectionBuilder l2gatewayConnectionBuilder = new L2gatewayConnectionBuilder();

        String uuidConnectionName = UUID.nameUUIDFromBytes(connectionName.getBytes()).toString();
        l2gatewayConnectionBuilder.setUuid(new Uuid(uuidConnectionName));

        String uuidL2gwName = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        l2gatewayConnectionBuilder.setL2gatewayId(new Uuid(uuidL2gwName));
        l2gatewayConnectionBuilder.setNetworkId(new Uuid(elan));
        l2gatewayConnectionBuilder.setSegmentId(segmentationId);
        l2gatewayConnectionBuilder.setTenantId(new Uuid(ExpectedObjects.ELAN1));

        String portName = "port";
        String uuidPort = UUID.nameUUIDFromBytes(portName.getBytes()).toString();
        l2gatewayConnectionBuilder.setPortId(new Uuid(uuidPort));
        return l2gatewayConnectionBuilder.build();
    }

    public L2gateway buildL2gw(String l2gwName, String deviceName, List<String> intfNames) {
        final L2gatewayBuilder l2gatewayBuilder = new L2gatewayBuilder();
        String uuid = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        //String tenantUuid = UUID.fromString(ELAN1).toString();
        l2gatewayBuilder.setUuid(new Uuid(uuid));
        l2gatewayBuilder.setTenantId(new Uuid(ExpectedObjects.ELAN1));

        final List<Devices> devices = new ArrayList<>();
        final DevicesBuilder deviceBuilder = new DevicesBuilder();
        final List<Interfaces> interfaces = new ArrayList<>();
        for (String intfName : intfNames) {
            final InterfacesBuilder interfacesBuilder = new InterfacesBuilder();
            interfacesBuilder.setInterfaceName(intfName);
            interfacesBuilder.setSegmentationIds(new ArrayList<>());
            interfaces.add(interfacesBuilder.build());
        }
        deviceBuilder.setDeviceName(deviceName);
        deviceBuilder.setUuid(new Uuid(uuid));
        deviceBuilder.setInterfaces(interfaces);

        devices.add(deviceBuilder.build());
        l2gatewayBuilder.setDevices(devices);
        return l2gatewayBuilder.build();
    }

    public InstanceIdentifier<L2gateway> buildL2gwIid(String l2gwName) {
        String l2gwNameUuid = UUID.nameUUIDFromBytes(l2gwName.getBytes()).toString();
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class)
                .child(L2gateway.class, new L2gatewayKey(toUuid(l2gwNameUuid)));
    }

    public InstanceIdentifier<L2gatewayConnection> buildConnectionIid(String connectionName) {
        String l2gwConnectionNameUuid = UUID.nameUUIDFromBytes(connectionName.getBytes()).toString();
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class, new L2gatewayConnectionKey(toUuid(l2gwConnectionNameUuid)));
    }

    public Uuid toUuid(String name) {
        return new Uuid(UUID.fromString(name).toString());
    }

    static InstanceIdentifier<Node> createInstanceIdentifier(String nodeIdString) {
        org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId nodeId
                = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021
                .NodeId(new Uri(nodeIdString));
        org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology
                .topology.NodeKey nodeKey = new org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network
                .topology.rev131021.network.topology.topology.NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("hwvtep:1")));
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology
                        .rev131021.network.topology.topology.Node.class, nodeKey)
                .build();
    }

    public void buildTorNode(String torNodeId, String deviceName, String tepIp)
            throws Exception {

        InstanceIdentifier<Node> nodePath =
                createInstanceIdentifier(torNodeId);
        InstanceIdentifier<Node> psNodePath =
                createInstanceIdentifier(torNodeId + "/physicalswitch/" + deviceName);

        // Create PS node
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(psNodePath.firstKeyOf(Node.class).getNodeId());
        PhysicalSwitchAugmentationBuilder physicalSwitchAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();
        physicalSwitchAugmentationBuilder.setManagedBy(new HwvtepGlobalRef(nodePath));
        physicalSwitchAugmentationBuilder.setPhysicalSwitchUuid(new Uuid(UUID.nameUUIDFromBytes(deviceName.getBytes())
                .toString()));
        physicalSwitchAugmentationBuilder.setHwvtepNodeName(new HwvtepNodeName(deviceName));
        physicalSwitchAugmentationBuilder.setHwvtepNodeDescription("torNode");
        List<TunnelIps> tunnelIps = new ArrayList<>();
        IpAddress ip = IpAddressBuilder.getDefaultInstance(tepIp);
        tunnelIps.add(new TunnelIpsBuilder().withKey(new TunnelIpsKey(ip)).setTunnelIpsKey(ip).build());
        physicalSwitchAugmentationBuilder.setTunnelIps(tunnelIps);
        nodeBuilder.addAugmentation(physicalSwitchAugmentationBuilder.build());
        singleTransactionDataBroker.syncWrite(LogicalDatastoreType.OPERATIONAL, psNodePath, nodeBuilder.build());

        nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(nodePath.firstKeyOf(Node.class).getNodeId());
        HwvtepGlobalAugmentationBuilder builder = new HwvtepGlobalAugmentationBuilder();
        builder.setDbVersion("1.6.0");
        builder.setManagers(TestBuilders.buildManagers1());
        GlobalAugmentationHelper.addSwitches(builder, psNodePath);
        nodeBuilder.addAugmentation(builder.build());
        singleTransactionDataBroker.syncWrite(LogicalDatastoreType.OPERATIONAL, nodePath, nodeBuilder.build());
    }

    public LocalUcastMacs createLocalUcastMac(InstanceIdentifier<Node> nodeId, String mac, String ipAddr,
                                                     String tepIp) throws TransactionCommitFailedException {

        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(nodeId.firstKeyOf(Node.class).getNodeId());
        final List<LocalUcastMacs> localUcastMacses = new ArrayList<>();
        LocalUcastMacsBuilder localUcastMacsBuilder = new LocalUcastMacsBuilder();
        localUcastMacsBuilder.setIpaddr(IpAddressBuilder.getDefaultInstance(ipAddr));
        localUcastMacsBuilder.setMacEntryKey(new MacAddress(mac));
        localUcastMacsBuilder.setMacEntryUuid(getUUid(mac));
        localUcastMacsBuilder.setLocatorRef(TestBuilders.buildLocatorRef(nodeId, tepIp));
        localUcastMacsBuilder.setLogicalSwitchRef(TestBuilders.buildLogicalSwitchesRef(nodeId, ExpectedObjects.ELAN1));
        LocalUcastMacs localUcastMacs = localUcastMacsBuilder.build();
        localUcastMacses.add(localUcastMacs);
        HwvtepGlobalAugmentationBuilder builder1 =
                new HwvtepGlobalAugmentationBuilder().setLocalUcastMacs(localUcastMacses) ;
        nodeBuilder.addAugmentation(builder1.build());
        singleTransactionDataBroker.syncUpdate(LogicalDatastoreType.OPERATIONAL, nodeId, nodeBuilder.build());
        return localUcastMacs;
    }

    public InstanceIdentifier<LocalUcastMacs> buildMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class).child(LocalUcastMacs.class, mac.key());
    }

    public void deletel2GWConnection(String connectionName) throws TransactionCommitFailedException {
        singleTransactionDataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION, buildConnectionIid(connectionName));
    }

}
