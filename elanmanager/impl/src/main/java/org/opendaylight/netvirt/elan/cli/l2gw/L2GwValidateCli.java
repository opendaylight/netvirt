/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.cli.l2gw;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Function;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LogicalSwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TerminationPointCmd;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.DevicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindingsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "l2gw", name = "validate", description = "Validates the hwvtep nodes data")
public class L2GwValidateCli extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(L2GwValidateCli.class);

    private final MergeCommand[] globalCommands = new MergeCommand[]{new LogicalSwitchesCmd(), new RemoteUcastCmd(),
        new RemoteMcastCmd()};
    private final MergeCommand[] physicalSwitchCommands = new MergeCommand[]{new TerminationPointCmd()};

    private final Map<InstanceIdentifier<Node>, Node> operationalNodes = new HashMap<>();
    private final Map<InstanceIdentifier<Node>, Node> configNodes = new HashMap<>();
    private final Map<String, ElanInstance> elanInstanceMap = new HashMap<>();
    private final Map<Uuid, L2gateway> uuidToL2Gateway = new HashMap<>();
    private final InstanceIdentifier<Topology> topoIid = HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier();
    private final Map<InstanceIdentifier<Node>, Map<InstanceIdentifier, DataObject>> operationalNodesData =
            new HashMap<>();
    private final Map<InstanceIdentifier<Node>, Map<InstanceIdentifier, DataObject>> configNodesData =
            new HashMap<>();

    private final DataBroker dataBroker;
    private final L2GatewayCache l2GatewayCache;
    private final ManagedNewTransactionRunner txRunner;

    private List<L2gateway> l2gateways;
    private List<L2gatewayConnection> l2gatewayConnections;

    private PrintWriter pw;

    public L2GwValidateCli(DataBroker dataBroker, L2GatewayCache l2GatewayCache) {
        this.dataBroker = dataBroker;
        this.l2GatewayCache = l2GatewayCache;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public Object doExecute() throws Exception {
        pw = new PrintWriter(new FileOutputStream(new File("l2gw.validation.txt")));
        readNodes();
        verifyHANodes();
        verifyConfigVsOperationalDiff();
        verifyL2GatewayConnections();
        pw.close();
        return null;
    }

    @SuppressWarnings("illegalcatch")
    private void readNodes() {
        try {
            InstanceIdentifier<Topology> topoId = HwvtepSouthboundUtils
                .createHwvtepTopologyInstanceIdentifier();
            txRunner.callWithNewReadOnlyTransactionAndClose(OPERATIONAL, operTx -> {
                Optional<Topology> operationalTopoOptional = operTx.read(topoId).get();
                if (operationalTopoOptional.isPresent()) {
                    for (Node node : operationalTopoOptional.get().nonnullNode().values()) {
                        InstanceIdentifier<Node> nodeIid = topoId.child(Node.class, node.key());
                        operationalNodes.put(nodeIid, node);
                    }
                }
            });

            txRunner.callWithNewReadOnlyTransactionAndClose(CONFIGURATION, configTx -> {
                Optional<Topology> configTopoOptional = configTx.read(topoId).get();
                if (configTopoOptional.isPresent()) {
                    for (Node node : configTopoOptional.get().nonnullNode().values()) {
                        InstanceIdentifier<Node> nodeIid = topoId.child(Node.class, node.key());
                        configNodes.put(nodeIid, node);
                    }
                }
                fillNodesData(operationalNodes, operationalNodesData);
                fillNodesData(configNodes, configNodesData);

                Optional<ElanInstances> elanInstancesOptional = configTx.read(
                    InstanceIdentifier.builder(ElanInstances.class).build()).get();

                if (elanInstancesOptional.isPresent()
                    && elanInstancesOptional.get().getElanInstance() != null) {
                    for (ElanInstance elanInstance : elanInstancesOptional.get().nonnullElanInstance().values()) {
                        elanInstanceMap.put(elanInstance.getElanInstanceName(), elanInstance);
                    }
                }
            });
            l2gatewayConnections = L2GatewayConnectionUtils.getAllL2gatewayConnections(dataBroker);
            l2gateways = L2GatewayConnectionUtils.getL2gatewayList(dataBroker);
            for (L2gateway l2gateway : l2gateways) {
                uuidToL2Gateway.put(l2gateway.getUuid(), l2gateway);
            }
        } catch (Exception e) {
            LOG.error("Exception : ", e);
        }
    }

    private boolean isPresent(Map<InstanceIdentifier<Node>, Map<InstanceIdentifier, DataObject>> dataMap,
        InstanceIdentifier<Node> nodeIid, InstanceIdentifier dataIid) {
        if (dataMap.containsKey(nodeIid)) {
            return dataMap.get(nodeIid).containsKey(dataIid);
        }
        return false;
    }

    @Nullable
    private DataObject getData(Map<InstanceIdentifier<Node>, Map<InstanceIdentifier, DataObject>> dataMap,
        InstanceIdentifier<Node> nodeIid, InstanceIdentifier dataIid) {
        if (dataMap.containsKey(nodeIid)) {
            return dataMap.get(nodeIid).get(dataIid);
        }
        return null;
    }

    private void fillNodesData(Map<InstanceIdentifier<Node>, Node> nodes,
        Map<InstanceIdentifier<Node>, Map<InstanceIdentifier, DataObject>> dataMap) {

        for (Map.Entry<InstanceIdentifier<Node>, Node> entry : nodes.entrySet()) {
            InstanceIdentifier<Node> nodeId = entry.getKey();
            Node node = entry.getValue();
            Map<InstanceIdentifier, DataObject> map = new HashMap<>();
            dataMap.put(nodeId, map);
            if (node.augmentation(HwvtepGlobalAugmentation.class) != null) {
                for (MergeCommand command : globalCommands) {
                    List<DataObject> data = command.getData(node.augmentation(HwvtepGlobalAugmentation.class));
                    if (data != null) {
                        for (DataObject dataObject : data) {
                            map.put(command.generateId(nodeId, dataObject), dataObject);
                        }
                    }
                }
            } else {
                for (MergeCommand command : physicalSwitchCommands) {
                    List<DataObject> data = command.getData(node);
                    if (data != null) {
                        for (DataObject dataObject : data) {
                            map.put(command.generateId(nodeId, dataObject), dataObject);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks the diff between config and operational topology nodes and prints it to the file if any.
     * This will tell what is present in the controller config and not in the device
     */
    private void verifyConfigVsOperationalDiff() {
        for (Node cfgNode : configNodes.values()) {
            InstanceIdentifier<Node> nodeId = topoIid.child(Node.class, cfgNode.key());
            compareNodes(cfgNode, operationalNodes.get(nodeId), false, CONFIGURATION);
        }
    }

    /**
     * Checks the diff between HA parent and child nodes.
     * Whatever config data in parent should be present in child nodes
     * Whatever operational data in child should be present in parent node
     */
    private void verifyHANodes() {
        pw.println("Verifying HA nodes");
        boolean parentChildComparison = true;
        HwvtepHACache haCache = HwvtepHACache.getInstance();
        Set<InstanceIdentifier<Node>> parentNodes = haCache.getHAParentNodes();
        if (HwvtepHAUtil.isEmpty(parentNodes)) {
            return;
        }
        for (InstanceIdentifier<Node> parentNodeIid : parentNodes) {
            String parentNodeId = parentNodeIid.firstKeyOf(Node.class).getNodeId().getValue();
            Node parentOpNode = operationalNodes.get(parentNodeIid);
            Node parentCfgNode = configNodes.get(parentNodeIid);
            Set<InstanceIdentifier<Node>> childNodeIids = haCache.getChildrenForHANode(parentNodeIid);
            if (HwvtepHAUtil.isEmpty(childNodeIids)) {
                pw.println("No child nodes could be found for parent node " + parentNodeId);
                continue;
            }
            for (InstanceIdentifier<Node> childNodeIid : childNodeIids) {
                String childNodeId = childNodeIid.firstKeyOf(Node.class).getNodeId().getValue();
                if (parentOpNode != null) {
                    compareNodes(parentOpNode, operationalNodes.get(childNodeIid), parentChildComparison,
                        OPERATIONAL);
                } else {
                    pw.println("Missing parent operational node for id " + parentNodeId);
                }
                if (parentCfgNode != null) {
                    if (configNodes.get(childNodeIid) == null) {
                        if (containsLogicalSwitch(parentCfgNode)) {
                            pw.println("Missing child config data " + childNodeId);
                        }
                    } else {
                        compareNodes(parentCfgNode, configNodes.get(childNodeIid), parentChildComparison,
                            CONFIGURATION);
                    }
                } else {
                    pw.println("Missing parent config node for id " + parentNodeId);
                }
            }
        }
    }

    private static boolean containsLogicalSwitch(Node node) {
        if (node == null || node.augmentation(HwvtepGlobalAugmentation.class) == null
            || node.augmentation(HwvtepGlobalAugmentation.class).nonnullSwitches().isEmpty()) {
            return false;
        }
        return true;
    }

    private <D extends Datastore> boolean compareNodes(Node node1, Node node2, boolean parentChildComparison,
        Class<D> datastoreType) {

        if (node1 == null || node2 == null) {
            return false;
        }
        InstanceIdentifier<Node> nodeIid1 = HwvtepSouthboundUtils.createInstanceIdentifier(node1.getNodeId());
        InstanceIdentifier<Node> nodeIid2 = HwvtepSouthboundUtils.createInstanceIdentifier(node2.getNodeId());

        NodeId nodeId1 = nodeIid1.firstKeyOf(Node.class).getNodeId();
        NodeId nodeId2 = nodeIid2.firstKeyOf(Node.class).getNodeId();

        PhysicalSwitchAugmentation psAug1 = node1.augmentation(PhysicalSwitchAugmentation.class);
        PhysicalSwitchAugmentation psAug2 = node2.augmentation(PhysicalSwitchAugmentation.class);

        HwvtepGlobalAugmentation aug1 = node1.augmentation(HwvtepGlobalAugmentation.class);
        HwvtepGlobalAugmentation aug2 = node2.augmentation(HwvtepGlobalAugmentation.class);

        boolean globalNodes = psAug1 == null && psAug2 == null ? true : false;
        MergeCommand[] commands = globalNodes ? globalCommands : physicalSwitchCommands;

        for (MergeCommand cmd : commands) {

            List<DataObject> data1 = null;
            List<DataObject> data2 = null;

            if (globalNodes) {
                data1 = cmd.getData(aug1);
                data2 = cmd.getData(aug2);
            } else {
                data1 = cmd.getData(node1);
                data2 = cmd.getData(node2);
            }
            data1 = data1 == null ? Collections.emptyList() : data1;
            data2 = data2 == null ? Collections.emptyList() : data2;

            if (parentChildComparison) {
                data2 = cmd.transform(nodeIid1, data2);
            }
            Function<DataObject, DataObject> withoutUuidTransformer = cmd::withoutUuid;
            data1 = data1.stream().map(withoutUuidTransformer).collect(Collectors.toList());
            data2 = data2.stream().map(withoutUuidTransformer).collect(Collectors.toList());

            Map<Identifier<?>, DataObject> map1 = new HashMap<>();
            Map<Identifier<?>, DataObject> map2 = new HashMap<>();
            for (DataObject dataObject : data1) {
                map1.put(cmd.getKey(dataObject), dataObject);
            }
            for (DataObject dataObject : data2) {
                map2.put(cmd.getKey(dataObject), dataObject);
            }
            Set<DataObject> diff = new HashSet<>();

            for (Entry<Identifier<?>, DataObject> entry : map1.entrySet()) {
                DataObject obj1 = entry.getValue();
                DataObject obj2 = map2.get(entry.getKey());
                if (obj2 == null || !cmd.areEqual(obj1, obj2)) {
                    diff.add(obj1);
                }
            }

            if (!diff.isEmpty()) {
                if (parentChildComparison) {
                    pw.println("Missing " + cmd.getDescription() + " child entries in " + datastoreType
                        + " parent node " + nodeId1 + " contain " + " more entries than child "
                        + nodeId2 + " " + diff.size());
                } else {
                    pw.println("Missing " + cmd.getDescription() + " op entries config "
                        + nodeId1 + " contain " + " more entries than operational node " + diff.size());
                }
                if (diff.size() < 10) {
                    for (Object obj : diff) {
                        pw.println(cmd.getKey((DataObject) obj));
                    }
                }
            }

            diff = new HashSet<>();
            for (Entry<Identifier<?>, DataObject> entry : map2.entrySet()) {
                DataObject obj1 = entry.getValue();
                DataObject obj2 = map1.get(entry.getKey());
                if (globalNodes || parentChildComparison) {
                    if (obj2 == null || !cmd.areEqual(obj1, obj2)) {
                        diff.add(obj1);
                    }
                }
            }
            if (!diff.isEmpty()) {
                if (parentChildComparison) {
                    pw.println("Extra " + cmd.getDescription() + " child entries in " + datastoreType + " node "
                        + nodeId2 + " contain " + " more entries than parent node " + nodeId1 + " " + diff.size());
                } else {
                    pw.println("Extra " + cmd.getDescription() + " operational node "
                        + nodeId2 + " contain " + " more entries than config node " + diff.size());
                }
                if (diff.size() < 10) {
                    for (Object obj : diff) {
                        pw.println(cmd.getKey((DataObject) obj));
                    }
                }
            }
        }
        return true;
    }

    private void verifyL2GatewayConnections() {
        boolean isValid = true;
        for (L2gatewayConnection l2gatewayConnection : l2gatewayConnections) {

            Uuid l2GatewayDeiceUuid = l2gatewayConnection.getL2gatewayId();
            if (l2GatewayDeiceUuid != null) {
                L2gateway l2gateway = uuidToL2Gateway.get(l2GatewayDeiceUuid);
                String logicalSwitchName = l2gatewayConnection.getNetworkId().getValue();
                Map<DevicesKey, Devices> devices = l2gateway.nonnullDevices();

                for (Devices device : devices.values()) {
                    L2GatewayDevice l2GatewayDevice = l2GatewayCache.get(device.getDeviceName());
                    isValid = verifyL2GatewayDevice(l2gateway, device, l2GatewayDevice);
                    if (!isValid) {
                        continue;
                    }
                    NodeId nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
                    InstanceIdentifier<Node> nodeIid = topoIid.child(Node.class, new NodeKey(nodeId));

                    isValid = verfiyLogicalSwitch(logicalSwitchName, nodeIid);
                    if (isValid) {
                        isValid = verifyMcastMac(logicalSwitchName, nodeIid);
                        verifyVlanBindings(nodeIid, logicalSwitchName, device, l2gatewayConnection.getSegmentId());
                        L2GatewayDevice elanL2gatewayDevice = ElanL2GwCacheUtils
                            .getL2GatewayDeviceFromCache(logicalSwitchName, nodeId.getValue());
                        if (elanL2gatewayDevice == null) {
                            pw.println("Failed elan l2gateway device not found for network " + logicalSwitchName
                                + " and device " + device.getDeviceName() + " " + l2GatewayDevice.getHwvtepNodeId()
                                + " l2gw connection id " + l2gatewayConnection.getUuid());
                        }
                    }
                }
            }

        }
    }

    private boolean verifyL2GatewayDevice(L2gateway l2gateway, Devices device, L2GatewayDevice l2GatewayDevice) {
        if (l2GatewayDevice == null) {
            pw.println("Failed l2gateway not found in cache for device " + device.getDeviceName());
            return false;
        }
        if (l2GatewayDevice.getHwvtepNodeId() == null) {
            pw.println("L2gateway cache is not updated with node id for device " + device.getDeviceName());
            return false;
        }
        if (l2GatewayDevice.getTunnelIp() == null) {
            pw.println("L2gateway cache is not updated with tunnel ip for device " + device.getDeviceName());
            return false;
        }
        if (!l2GatewayDevice.getL2GatewayIds().contains(l2gateway.getUuid())) {
            pw.println("L2gateway cache is not updated with l2gw id for device " + device.getDeviceName());
            return false;
        }
        return true;
    }

    private static InstanceIdentifier<TerminationPoint> getPhysicalPortTerminationPointIid(NodeId nodeId,
            String portName) {
        TerminationPointKey tpKey = new TerminationPointKey(new TpId(portName));
        InstanceIdentifier<TerminationPoint> iid = HwvtepSouthboundUtils.createTerminationPointId(nodeId, tpKey);
        return iid;
    }

    private boolean verfiyLogicalSwitch(String logicalSwitchName, InstanceIdentifier<Node> nodeIid) {
        NodeId nodeId = nodeIid.firstKeyOf(Node.class).getNodeId();
        InstanceIdentifier<LogicalSwitches> logicalSwitchPath = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName));

        if (!isPresent(configNodesData, nodeIid, logicalSwitchPath)) {
            pw.println("Failed to find config logical switch " + logicalSwitchName + " for node "
                    + nodeId.getValue());
            return false;
        }

        if (!isPresent(operationalNodesData, nodeIid, logicalSwitchPath)) {
            pw.println("Failed to find operational logical switch " + logicalSwitchName + " for node "
                + nodeId.getValue());
            return false;
        }
        return true;
    }

    private boolean verifyMcastMac(String logicalSwitchName,
                                    InstanceIdentifier<Node> nodeIid) {
        NodeId nodeId = nodeIid.firstKeyOf(Node.class).getNodeId();

        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(
                        new NodeId(new Uri(nodeId)), new HwvtepNodeName(logicalSwitchName)));

        RemoteMcastMacs remoteMcastMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef).build();
        InstanceIdentifier<RemoteMcastMacs> mcastMacIid = HwvtepSouthboundUtils
                .createRemoteMcastMacsInstanceIdentifier(new NodeId(new Uri(nodeId)), remoteMcastMac.key());


        if (!isPresent(configNodesData, nodeIid, mcastMacIid)) {
            pw.println("Failed to find config mcast mac for logical switch " + logicalSwitchName
                    + " node id " + nodeId.getValue());
            return false;
        }

        if (!isPresent(operationalNodesData, nodeIid, mcastMacIid)) {
            pw.println("Failed to find operational mcast mac for logical switch " + logicalSwitchName
                + " node id " + nodeId.getValue());
            return false;
        }
        return true;
    }

    private boolean verifyVlanBindings(InstanceIdentifier<Node> nodeIid,
                                        String logicalSwitchName,
                                        Devices hwVtepDevice,
                                        Integer defaultVlanId) {
        boolean valid = true;
        NodeId nodeId = nodeIid.firstKeyOf(Node.class).getNodeId();
        if (hwVtepDevice.getInterfaces() == null) {
            return false;
        }
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712
                .l2gateway.attributes.devices.Interfaces deviceInterface : hwVtepDevice.nonnullInterfaces().values()) {

            NodeId switchNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, hwVtepDevice.getDeviceName());
            InstanceIdentifier<Node> physicalSwitchNodeIid = topoIid.child(Node.class, new NodeKey(switchNodeId));
            InstanceIdentifier<TerminationPoint> terminationPointIid =
                    getPhysicalPortTerminationPointIid(switchNodeId, deviceInterface.getInterfaceName());

            TerminationPoint operationalTerminationPoint = (TerminationPoint) getData(operationalNodesData,
                    physicalSwitchNodeIid, terminationPointIid);
            if (operationalTerminationPoint == null) {
                valid = false;
                pw.println("Failed to find the operational port " + deviceInterface.getInterfaceName()
                    + " for node " + hwVtepDevice.getDeviceName() + " nodeid " + nodeId.getValue());
                continue;
            }
            TerminationPoint configTerminationPoint = (TerminationPoint) getData(configNodesData,
                physicalSwitchNodeIid, terminationPointIid);
            if (configTerminationPoint == null) {
                valid = false;
                pw.println("Failed to find the configurational port " + deviceInterface.getInterfaceName()
                        + " for node " + hwVtepDevice.getDeviceName() +  " for logical switch " + logicalSwitchName
                        + " nodeid " + nodeId.getValue());
                continue;
            }

            List<VlanBindings> expectedVlans = new ArrayList<>();
            if (deviceInterface.getSegmentationIds() != null && !deviceInterface.getSegmentationIds().isEmpty()) {
                for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                    expectedVlans.add(HwvtepSouthboundUtils.createVlanBinding(nodeId, vlanId, logicalSwitchName));
                }
            } else {
                expectedVlans.add(HwvtepSouthboundUtils.createVlanBinding(nodeId, defaultVlanId, logicalSwitchName));
            }

            HwvtepPhysicalPortAugmentation portAugmentation = configTerminationPoint.augmentation(
                HwvtepPhysicalPortAugmentation.class);
            if (portAugmentation == null || portAugmentation.nonnullVlanBindings().values().isEmpty()) {
                pw.println("Failed to find the config vlan bindings for port " + deviceInterface.getInterfaceName()
                    + " for node " + hwVtepDevice.getDeviceName() +  " for logical switch " + logicalSwitchName
                    + " nodeid " + nodeId.getValue());
                valid = false;
                continue;
            }
            portAugmentation = operationalTerminationPoint.augmentation(HwvtepPhysicalPortAugmentation.class);
            if (portAugmentation == null || portAugmentation.nonnullVlanBindings().values().isEmpty()) {
                pw.println("Failed to find the operational vlan bindings for port " + deviceInterface.getInterfaceName()
                    + " for node " + hwVtepDevice.getDeviceName() +  " for logical switch " + logicalSwitchName
                    + " nodeid " + nodeId.getValue());
                valid = false;
                continue;
            }
            VlanBindings expectedBindings = !expectedVlans.isEmpty() ? expectedVlans.get(0) : null;
            boolean foundBindings = false;
            Map<VlanBindingsKey, VlanBindings> vlanBindingses = configTerminationPoint.augmentation(
                HwvtepPhysicalPortAugmentation.class).nonnullVlanBindings();
            for (VlanBindings actual : vlanBindingses.values()) {
                if (actual.equals(expectedBindings)) {
                    foundBindings = true;
                    break;
                }
            }
            if (!foundBindings) {
                pw.println("Mismatch in vlan bindings for port " + deviceInterface.getInterfaceName()
                    + " for node " + hwVtepDevice.getDeviceName() +  " for logical switch " + logicalSwitchName
                    + " nodeid " + nodeId.getValue());
                pw.println("Failed to find the vlan bindings " + expectedBindings);
                pw.println("Actual bindings present in config are ");
                for (VlanBindings actual : vlanBindingses.values()) {
                    pw.println(actual.toString());
                }
                valid = false;
            }
        }
        return true;
    }
}
