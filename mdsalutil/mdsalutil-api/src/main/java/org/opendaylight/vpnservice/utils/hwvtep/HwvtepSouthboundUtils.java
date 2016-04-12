/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.utils.hwvtep;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindingsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindingsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;

/**
 * TODO: Move these API's to ovsdb's utils.hwvtepsouthbound-utils module.
 */
public class HwvtepSouthboundUtils {

    /**
     * Creates the hwvtep topology instance identifier.
     *
     * @return the instance identifier
     */
    public static InstanceIdentifier<Topology> createHwvtepTopologyInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID));
    }

    /**
     * Creates the instance identifier.
     *
     * @param nodeId
     *            the node id
     * @return the instance identifier
     */
    public static InstanceIdentifier<Node> createInstanceIdentifier(NodeId nodeId) {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(nodeId));
    }

    /**
     * Creates the logical switches instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param hwvtepNodeName
     *            the hwvtep node name
     * @return the instance identifier
     */
    public static InstanceIdentifier<LogicalSwitches> createLogicalSwitchesInstanceIdentifier(NodeId nodeId,
            HwvtepNodeName hwvtepNodeName) {
        return createInstanceIdentifier(nodeId).augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(hwvtepNodeName));
    }

    /**
     * Creates the remote ucast macs instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param mac
     *            the mac
     * @return the instance identifier
     */
    public static InstanceIdentifier<RemoteUcastMacs> createRemoteUcastMacsInstanceIdentifier(NodeId nodeId,
            String logicalSwitchName,
            MacAddress mac) {
        InstanceIdentifier<LogicalSwitches> logicalSwitch = createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
        return createInstanceIdentifier(nodeId).augmentation(HwvtepGlobalAugmentation.class)
                .child(RemoteUcastMacs.class, new RemoteUcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch), mac));
    }

    /**
     * Creates the local ucast macs instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param mac
     *            the mac
     * @return the instance identifier
     */
    public static InstanceIdentifier<LocalUcastMacs> createLocalUcastMacsInstanceIdentifier(NodeId nodeId,
            String logicalSwitchName,
            MacAddress mac) {
        InstanceIdentifier<LogicalSwitches> logicalSwitch = createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
        return createInstanceIdentifier(nodeId).augmentation(HwvtepGlobalAugmentation.class).child(LocalUcastMacs.class,
                new LocalUcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch), mac));
    }

    /**
     * Creates the remote mcast macs instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param mac
     *            the mac
     * @return the instance identifier
     */
    public static InstanceIdentifier<RemoteMcastMacs> createRemoteMcastMacsInstanceIdentifier(NodeId nodeId,
            String logicalSwitchName, MacAddress mac) {
        InstanceIdentifier<LogicalSwitches> logicalSwitch = createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
        return createInstanceIdentifier(nodeId).augmentation(HwvtepGlobalAugmentation.class)
                .child(RemoteMcastMacs.class, new RemoteMcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch), mac));
    }

    /**
     * Creates the remote mcast macs instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param remoteMcastMacsKey
     *            the remote mcast macs key
     * @return the instance identifier
     */
    public static InstanceIdentifier<RemoteMcastMacs> createRemoteMcastMacsInstanceIdentifier(NodeId nodeId,
            RemoteMcastMacsKey remoteMcastMacsKey) {
        return createInstanceIdentifier(nodeId).augmentation(HwvtepGlobalAugmentation.class)
                .child(RemoteMcastMacs.class, remoteMcastMacsKey);
    }

    /**
     * Creates the physical locator instance identifier.
     *
     * @param nodeId
     *            the node id
     * @param physicalLocatorAug
     *            the physical locator aug
     * @return the instance identifier
     */
    public static InstanceIdentifier<TerminationPoint> createPhysicalLocatorInstanceIdentifier(NodeId nodeId,
            HwvtepPhysicalLocatorAugmentation physicalLocatorAug) {
        return createInstanceIdentifier(nodeId).child(TerminationPoint.class,
                getTerminationPointKey(physicalLocatorAug));
    }

    /**
     * Creates the physical port instance identifier.
     *
     * @param physicalSwitchNodeId
     *            the physical switch node id
     * @param phyPortName
     *            the phy port name
     * @return the instance identifier
     */
    public static InstanceIdentifier<HwvtepPhysicalPortAugmentation> createPhysicalPortInstanceIdentifier(
            NodeId physicalSwitchNodeId, String phyPortName) {
        return createInstanceIdentifier(physicalSwitchNodeId)
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(phyPortName)))
                .augmentation(HwvtepPhysicalPortAugmentation.class);
    }

    /**
     * Creates the vlan binding instance identifier.
     *
     * @param physicalSwitchNodeId
     *            the physical switch node id
     * @param phyPortName
     *            the phy port name
     * @param vlanId
     *            the vlan id
     * @return the instance identifier
     */
    public static InstanceIdentifier<VlanBindings> createVlanBindingInstanceIdentifier(NodeId physicalSwitchNodeId,
            String phyPortName, Integer vlanId) {
        return createPhysicalPortInstanceIdentifier(physicalSwitchNodeId, phyPortName).child(VlanBindings.class,
                new VlanBindingsKey(new VlanId(vlanId)));
    }

    /**
     * Gets the termination point key.
     *
     * @param phyLocator
     *            the phy locator
     * @return the termination point key
     */
    public static TerminationPointKey getTerminationPointKey(HwvtepPhysicalLocatorAugmentation phyLocator) {
        TerminationPointKey tpKey = null;
        if (phyLocator.getEncapsulationType() != null && phyLocator.getDstIp() != null) {
            String encapType = HwvtepSouthboundConstants.ENCAPS_TYPE_MAP.get(phyLocator.getEncapsulationType());
            String tpKeyStr = encapType + ":" + String.valueOf(phyLocator.getDstIp().getValue());
            tpKey = new TerminationPointKey(new TpId(tpKeyStr));
        }
        return tpKey;
    }

    /**
     * Creates the managed node id.
     *
     * @param nodeId
     *            the node id
     * @param physicalSwitchName
     *            the physical switch name
     * @return the node id
     */
    public static NodeId createManagedNodeId(NodeId nodeId, String physicalSwitchName) {
        String phySwitchNodeId = nodeId.getValue() + "/" + HwvtepSouthboundConstants.PSWITCH_URI_PREFIX + "/"
                + physicalSwitchName;
        return new NodeId(phySwitchNodeId);
    }

    /**
     * Create logical switch.
     *
     * @param name
     *            the name
     * @param desc
     *            the desc
     * @param tunnelKey
     *            the tunnel key
     * @return the logical switches
     */
    public static LogicalSwitches createLogicalSwitch(String name, String desc, String tunnelKey) {
        HwvtepNodeName hwvtepName = new HwvtepNodeName(name);
        LogicalSwitchesBuilder lsBuilder = new LogicalSwitchesBuilder().setHwvtepNodeDescription(desc)
                .setHwvtepNodeName(hwvtepName).setKey(new LogicalSwitchesKey(hwvtepName)).setTunnelKey(tunnelKey);
        return lsBuilder.build();
    }

    /**
     * Create hwvtep physical locator augmentation.
     *
     * @param ipAddress
     *            the ip address
     * @return the hwvtep physical locator augmentation
     */
    public static HwvtepPhysicalLocatorAugmentation createHwvtepPhysicalLocatorAugmentation(String ipAddress) {
        // FIXME: Get encapsulation type dynamically
        Class<? extends EncapsulationTypeBase> encapTypeClass = createEncapsulationType(StringUtils.EMPTY);
        HwvtepPhysicalLocatorAugmentationBuilder phyLocBuilder = new HwvtepPhysicalLocatorAugmentationBuilder()
                .setEncapsulationType(encapTypeClass).setDstIp(new IpAddress(ipAddress.toCharArray()));
        return phyLocBuilder.build();
    }

    public static Class<? extends EncapsulationTypeBase> createEncapsulationType(String type) {
        Preconditions.checkNotNull(type);
        if (type.isEmpty()) {
            return EncapsulationTypeVxlanOverIpv4.class;
        } else {
            ImmutableBiMap<String, Class<? extends EncapsulationTypeBase>> mapper = HwvtepSouthboundConstants.ENCAPS_TYPE_MAP
                    .inverse();
            return mapper.get(type);
        }
    }

    /**
     * Create remote ucast mac.
     *
     * @param nodeId
     *            the node id
     * @param mac
     *            the mac
     * @param ipAddress
     *            the ip address
     * @param logicalSwitchName
     *            the logical switch name
     * @param physicalLocatorAug
     *            the physical locator aug
     * @return the remote ucast macs
     */
    public static RemoteUcastMacs createRemoteUcastMac(NodeId nodeId, String mac, IpAddress ipAddress,
            String logicalSwitchName, HwvtepPhysicalLocatorAugmentation physicalLocatorAug) {
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(
                createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));
        HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                createPhysicalLocatorInstanceIdentifier(nodeId, physicalLocatorAug));

        RemoteUcastMacs remoteUcastMacs = new RemoteUcastMacsBuilder().setMacEntryKey(new MacAddress(mac))
                .setIpaddr(ipAddress).setLogicalSwitchRef(lsRef).setLocatorRef(phyLocRef).build();
        return remoteUcastMacs;
    }

    /**
     * Creates the remote mcast mac.
     *
     * @param nodeId
     *            the node id
     * @param mac
     *            the mac
     * @param ipAddress
     *            the ip address
     * @param logicalSwitchName
     *            the logical switch name
     * @param lstPhysicalLocatorAug
     *            the lst physical locator aug
     * @return the remote mcast macs
     */
    public static RemoteMcastMacs createRemoteMcastMac(NodeId nodeId, String mac, IpAddress ipAddress,
            String logicalSwitchName, List<HwvtepPhysicalLocatorAugmentation> lstPhysicalLocatorAug) {
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(
                createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));

        List<LocatorSet> lstLocatorSet = new ArrayList<>();
        for (HwvtepPhysicalLocatorAugmentation phyLocatorAug : lstPhysicalLocatorAug) {
            HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                    createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug));
            lstLocatorSet.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());
        }

        RemoteMcastMacs remoteMcastMacs = new RemoteMcastMacsBuilder().setMacEntryKey(new MacAddress(mac))
                .setIpaddr(ipAddress).setLogicalSwitchRef(lsRef).setLocatorSet(lstLocatorSet).build();
        return remoteMcastMacs;
    }

    /**
     * Create vlan binding.
     *
     * @param nodeId
     *            the node id
     * @param vlanId
     *            the vlan id
     * @param logicalSwitchName
     *            the logical switch name
     * @return the vlan bindings
     */
    public static VlanBindings createVlanBinding(NodeId nodeId, int vlanId, String logicalSwitchName) {
        VlanBindingsBuilder vbBuilder = new VlanBindingsBuilder();
        VlanBindingsKey vbKey = new VlanBindingsKey(new VlanId(vlanId));
        vbBuilder.setKey(vbKey);
        vbBuilder.setVlanIdKey(vbKey.getVlanIdKey());

        final InstanceIdentifier<LogicalSwitches> lSwitchIid = createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(lSwitchIid);
        vbBuilder.setLogicalSwitchRef(lsRef);
        return vbBuilder.build();
    }

}
