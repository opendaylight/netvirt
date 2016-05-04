/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbPortInterfaceAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.Trunks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.TrunksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VlanTrunkSouthboundUtils {
    private static final Logger LOG = LoggerFactory.getLogger(VlanTrunkSouthboundUtils.class);
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));

    public static void addVlanPortToBridge(InstanceIdentifier<?> bridgeIid, IfL2vlan ifL2vlan,
                                            OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                            String parentInterface, DataBroker dataBroker, WriteTransaction t) {
        LOG.info("Vlan Interface creation not supported yet. please visit later.");
        int vlanId = ifL2vlan.getVlanId().getValue();
        addTrunkTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, parentInterface, vlanId, dataBroker, t);
    }

    public static void updateVlanMemberInTrunk(InstanceIdentifier<?> bridgeIid, IfL2vlan ifL2vlan,
                                           OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                           String parentInterface, DataBroker dataBroker, WriteTransaction t) {
        LOG.info("Vlan Interface creation not supported yet. please visit later.");
        int vlanId = ifL2vlan.getVlanId().getValue();
        updateTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, parentInterface, vlanId, dataBroker, t);
    }

    private static void addTrunkTerminationPoint(InstanceIdentifier<?> bridgeIid, OvsdbBridgeAugmentation bridgeNode,
                                                 String bridgeName, String parentInterface, int vlanId,
                                                 DataBroker dataBroker, WriteTransaction t) {
        if (vlanId == 0) {
            LOG.error("Found vlanid 0 for bridge: {}, interface: {}", bridgeName, parentInterface);
            return;
        }

        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), parentInterface);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(parentInterface);
        tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Trunk);
        OvsdbTerminationPointAugmentation terminationPointAugmentation = null;
        Optional<TerminationPoint> terminationPointOptional =
                IfmUtil.read(LogicalDatastoreType.OPERATIONAL, tpIid, dataBroker);
        if (terminationPointOptional.isPresent()) {
            TerminationPoint terminationPoint = terminationPointOptional.get();
            terminationPointAugmentation = terminationPoint.getAugmentation(OvsdbTerminationPointAugmentation.class);
            if (terminationPointAugmentation != null) {
                List<Trunks> trunks = terminationPointAugmentation.getTrunks();
                if (trunks == null) {
                    trunks = new ArrayList<>();
                }

                trunks.add(new TrunksBuilder().setTrunk(new VlanId(vlanId)).build());
                tpAugmentationBuilder.setTrunks(trunks);
            }
        } else {
            List<Trunks> trunks = new ArrayList<>();
            trunks.add(new TrunksBuilder().setTrunk(new VlanId(vlanId)).build());
            tpAugmentationBuilder.setTrunks(trunks);
        }

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

        t.put(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);
    }

    public static void addTerminationPointWithTrunks(InstanceIdentifier<?> bridgeIid, List<Trunks> trunks,
                                                     String parentInterface, WriteTransaction t) {
        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), parentInterface);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(parentInterface);
        tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Trunk);
        tpAugmentationBuilder.setTrunks(trunks);

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

        t.put(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);
    }

    private static void updateTerminationPoint(InstanceIdentifier<?> bridgeIid, OvsdbBridgeAugmentation bridgeNode,
                                           String bridgeName, String parentInterface, int vlanId,
                                           DataBroker dataBroker, WriteTransaction t) {
        if (vlanId == 0) {
            LOG.error("Found vlanid 0 for bridge: {}, interface: {}", bridgeName, parentInterface);
            return;
        }

        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), parentInterface);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        tpAugmentationBuilder.setName(parentInterface);
        tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Trunk);
        OvsdbTerminationPointAugmentation terminationPointAugmentation = null;
        Optional<TerminationPoint> terminationPointOptional =
                IfmUtil.read(LogicalDatastoreType.OPERATIONAL, tpIid, dataBroker);
        if (terminationPointOptional.isPresent()) {
            TerminationPoint terminationPoint = terminationPointOptional.get();
            terminationPointAugmentation = terminationPoint.getAugmentation(OvsdbTerminationPointAugmentation.class);
            if (terminationPointAugmentation != null) {
                List<Trunks> trunks = terminationPointAugmentation.getTrunks();
                if (trunks != null) {
                    trunks.remove(new TrunksBuilder().setTrunk(new VlanId(vlanId)).build());
                }

                tpAugmentationBuilder.setTrunks(trunks);
                TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
                tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
                tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

                t.put(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);
            }
        }
    }

    private static InstanceIdentifier<TerminationPoint> createTerminationPointInstanceIdentifier(NodeKey nodekey,
                                                                                                String portName){
        InstanceIdentifier<TerminationPoint> terminationPointPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class,nodekey)
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(portName)));

        LOG.debug("Termination point InstanceIdentifier generated : {}", terminationPointPath);
        return terminationPointPath;
    }
}