/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.nodehandlertest;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIpsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by eaksahu on 10/14/2016.
 */
public class NodeConnectedHandlerUtils {

    void addNode(InstanceIdentifier<Node> path,
            InstanceIdentifier<Node> psPath, String logicalSwitchData, String localUcasMacData, String localMcastData,
            String remoteMcastData, String remoteUcasteMacData, String globalTerminationPointIp,
            TypedWriteTransaction<Operational> tx) {
        NodeBuilder nodeBuilder = prepareOperationalNode(path);
        HwvtepGlobalAugmentationBuilder augmentationBuilder = prepareAugmentationBuilder();

        GlobalAugmentationHelper.addLogicalSwitches(augmentationBuilder, getData(logicalSwitchData));

        GlobalAugmentationHelper.addLocalUcastMacs(path, augmentationBuilder, getData(localUcasMacData));

        GlobalAugmentationHelper.addLocalMcastMacs(path, augmentationBuilder, getData(localMcastData));

        GlobalAugmentationHelper.addRemoteMcastMacs(path, augmentationBuilder, getData(remoteMcastData));

        GlobalAugmentationHelper.addRemoteUcastMacs(path, augmentationBuilder, getData(remoteUcasteMacData));

        GlobalAugmentationHelper.addGlobalTerminationPoints(nodeBuilder, path, getData(globalTerminationPointIp));

        GlobalAugmentationHelper.addSwitches(augmentationBuilder, psPath);

        nodeBuilder.addAugmentation(augmentationBuilder.build());

        tx.put(path, nodeBuilder.build());
    }

    void addPsNode(InstanceIdentifier<Node> path, InstanceIdentifier<Node> parentPath, List<String> portNameList,
            TypedWriteTransaction<Operational> tx) {
        PhysicalSwitchAugmentationBuilder physicalSwitchAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();
        physicalSwitchAugmentationBuilder.setManagedBy(new HwvtepGlobalRef(parentPath));
        physicalSwitchAugmentationBuilder.setPhysicalSwitchUuid(getUUid("d1s3"));
        physicalSwitchAugmentationBuilder.setHwvtepNodeName(new HwvtepNodeName("s3"));
        physicalSwitchAugmentationBuilder.setHwvtepNodeDescription("description");

        List<TunnelIps> tunnelIps = new ArrayList<>();
        IpAddress ip = new IpAddress(new Ipv4Address("192.168.122.30"));
        tunnelIps.add(new TunnelIpsBuilder().withKey(new TunnelIpsKey(ip)).setTunnelIpsKey(ip).build());
        physicalSwitchAugmentationBuilder.setTunnelIps(tunnelIps);

        NodeBuilder nodeBuilder = prepareOperationalNode(path);
        nodeBuilder.addAugmentation(physicalSwitchAugmentationBuilder.build());
        PhysicalSwitchHelper.dId = parentPath;
        nodeBuilder.setTerminationPoint(PhysicalSwitchHelper
                .addPhysicalSwitchTerminationPoints(path, portNameList));

        tx.mergeParentStructurePut(path, nodeBuilder.build());
    }

    private static NodeBuilder prepareOperationalNode(InstanceIdentifier<Node> iid) {
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(iid.firstKeyOf(Node.class).getNodeId());
        return nodeBuilder;
    }

    private static HwvtepGlobalAugmentationBuilder prepareAugmentationBuilder() {
        HwvtepGlobalAugmentationBuilder builder = new HwvtepGlobalAugmentationBuilder();
        builder.setManagers(TestBuilders.buildManagers());
        return builder;
    }

    private static List<String> getData(String data) {
        return Arrays.asList(data.split(","));
    }

    public static Uuid getUUid(String key) {
        return new Uuid(UUID.nameUUIDFromBytes(key.getBytes()).toString());
    }
}
