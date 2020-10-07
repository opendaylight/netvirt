/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.nodehandlertest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by eaksahu on 8/8/2016.
 */
public final class PhysicalSwitchHelper {
    static InstanceIdentifier<Node> dId;

    private PhysicalSwitchHelper() {

    }

    public static InstanceIdentifier<Node> getPhysicalSwitchInstanceIdentifier(InstanceIdentifier<Node> iid,
                                                                               String switchName) {
        NodeId id = iid.firstKeyOf(Node.class).getNodeId();
        String nodeString = id.getValue() + "/physicalswitch/" + switchName;
        NodeId nodeId = new NodeId(new Uri(nodeString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID);
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    static List<TerminationPoint> addPhysicalSwitchTerminationPoints(InstanceIdentifier<Node> switchIid,
        List<String> portNames) {
        List<TerminationPoint> tps = new ArrayList<>();
        for (String portName : portNames) {
            tps.add(buildTerminationPointForPhysicalSwitch(switchIid, portName, getVlanBindingData(1)));
        }
        return tps;
    }

    private static TerminationPoint buildTerminationPointForPhysicalSwitch(InstanceIdentifier<Node> switchIid,
        String portName, Map<Long, String> vlanBindingData) {
        TerminationPointKey tpKey = new TerminationPointKey(new TpId(portName));
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.withKey(tpKey);
        tpBuilder.setTpId(tpKey.getTpId());
        switchIid.firstKeyOf(Node.class);
        HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder =
                new HwvtepPhysicalPortAugmentationBuilder();
        buildTerminationPoint(tpAugmentationBuilder, portName, vlanBindingData);
        tpBuilder.addAugmentation(tpAugmentationBuilder.build());
        return tpBuilder.build();
    }

    private static void buildTerminationPoint(HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder,
            String portName, Map<Long, String> vlanBindingData) {
        updatePhysicalPortId(portName, tpAugmentationBuilder);
        updatePort(tpAugmentationBuilder, vlanBindingData);
    }

    private static void updatePhysicalPortId(String portName,
            HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder) {
        tpAugmentationBuilder.setHwvtepNodeName(new HwvtepNodeName(portName));
        tpAugmentationBuilder.setHwvtepNodeDescription("");
    }

    private static void updatePort(HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder,
            Map<Long, String> vlanBindings) {
        updateVlanBindings(vlanBindings, tpAugmentationBuilder);
        tpAugmentationBuilder.setPhysicalPortUuid(new Uuid(UUID.randomUUID().toString()));
    }

    private static void updateVlanBindings(Map<Long, String> vlanBindings,
            HwvtepPhysicalPortAugmentationBuilder tpAugmentationBuilder) {
        List<VlanBindings> vlanBindingsList = new ArrayList<>();
        for (Map.Entry<Long, String> vlanBindingEntry : vlanBindings.entrySet()) {
            Long vlanBindingKey = vlanBindingEntry.getKey();
            String logicalSwitch = vlanBindingEntry.getValue();
            if (logicalSwitch != null && vlanBindingKey != null) {
                vlanBindingsList.add(createVlanBinding(vlanBindingKey, logicalSwitch));
            }
        }
        tpAugmentationBuilder.setVlanBindings(vlanBindingsList);
    }

    private static VlanBindings createVlanBinding(Long key, String logicalSwitch) {
        VlanBindingsBuilder vbBuilder = new VlanBindingsBuilder();
        VlanBindingsKey vbKey = new VlanBindingsKey(new VlanId(key.intValue()));
        vbBuilder.withKey(vbKey);
        vbBuilder.setVlanIdKey(vbKey.getVlanIdKey());
        HwvtepLogicalSwitchRef hwvtepLogicalSwitchRef =
                new HwvtepLogicalSwitchRef(createInstanceIdentifier(logicalSwitch));
        vbBuilder.setLogicalSwitchRef(hwvtepLogicalSwitchRef);
        return vbBuilder.build();
    }

    private static InstanceIdentifier<LogicalSwitches> createInstanceIdentifier(String logicalSwitch) {
        NodeId id = dId.firstKeyOf(Node.class).getNodeId();
        NodeKey nodeKey = new NodeKey(id);
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class, nodeKey).augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(new HwvtepNodeName(logicalSwitch)))
                .build();
    }

    private static Map<Long, String> getVlanBindingData(int mapSize) {
        Map<Long, String> vlanBindings = new HashMap<>();
        for (long i = 0; i < mapSize; i++) {
            i = i * 100;
            vlanBindings.put(i, "9227c228-6bba-4bbe-bdb8-6942768ff0f1");
        }
        return vlanBindings;
    }

}
