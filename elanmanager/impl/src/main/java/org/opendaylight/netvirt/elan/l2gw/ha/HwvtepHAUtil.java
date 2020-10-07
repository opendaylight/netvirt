/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.SwitchesCmd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.ManagersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.ManagersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HwvtepHAUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepHAUtil.class);

    //TODO reuse HWvtepSouthboundConstants
    public static final String HA_ENABLED = "ha_enabled";
    public static final String HWVTEP_ENTITY_TYPE = "hwvtep";
    public static final String TEP_PREFIX = "vxlan_over_ipv4:";
    public static final String HA_ID = "ha_id";
    public static final String HA_CHILDREN = "ha_children";
    public static final String PHYSICALSWITCH = "/physicalswitch/";
    public static final TopologyId HWVTEP_TOPOLOGY_ID = new TopologyId(new Uri("hwvtep:1"));
    public static final String UUID = "uuid";
    public static final String HWVTEP_URI_PREFIX = "hwvtep";
    public static final String MANAGER_KEY = "managerKey";
    public static final String L2GW_JOB_KEY = ":l2gw";

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    private HwvtepHAUtil() {
    }

    public static HwvtepPhysicalLocatorRef buildLocatorRef(InstanceIdentifier<Node> nodeIid, String tepIp) {
        InstanceIdentifier<TerminationPoint> tepId = buildTpId(nodeIid, tepIp);
        return new HwvtepPhysicalLocatorRef(tepId);
    }

    public static String getNodeIdVal(InstanceIdentifier<?> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    public static Uuid getUUid(String key) {
        return new Uuid(java.util.UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
    }

    public static InstanceIdentifier<TerminationPoint> buildTpId(InstanceIdentifier<Node> nodeIid,String tepIp) {
        String tpKeyStr = TEP_PREFIX + tepIp;
        TerminationPointKey tpKey = new TerminationPointKey(new TpId(tpKeyStr));
        InstanceIdentifier<TerminationPoint> plIid = nodeIid.child(TerminationPoint.class, tpKey);
        return plIid;
    }

    public static String getTepIpVal(HwvtepPhysicalLocatorRef locatorRef) {
        InstanceIdentifier<TerminationPoint> tpId = (InstanceIdentifier<TerminationPoint>) locatorRef.getValue();
        return tpId.firstKeyOf(TerminationPoint.class).getTpId().getValue().substring("vxlan_over_ipv4:".length());
    }

    public static String getLogicalSwitchSwitchName(HwvtepLogicalSwitchRef logicalSwitchRef) {
        InstanceIdentifier<LogicalSwitches> id = (InstanceIdentifier<LogicalSwitches>) logicalSwitchRef.getValue();
        return id.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    public static String getNodeIdFromLocatorRef(HwvtepPhysicalLocatorRef locatorRef) {
        InstanceIdentifier<TerminationPoint> tpId = (InstanceIdentifier<TerminationPoint>) locatorRef.getValue();
        return tpId.firstKeyOf(Node.class).getNodeId().getValue();
    }

    public static String getNodeIdFromLogicalSwitches(HwvtepLogicalSwitchRef logicalSwitchRef) {
        InstanceIdentifier<LogicalSwitches> id = (InstanceIdentifier<LogicalSwitches>) logicalSwitchRef.getValue();
        return id.firstKeyOf(Node.class).getNodeId().getValue();
    }

    public static InstanceIdentifier<Node> createInstanceIdentifierFromHAId(String haUUidVal) {
        String nodeString = HWVTEP_URI_PREFIX + "://"
            + UUID + "/" + java.util.UUID.nameUUIDFromBytes(haUUidVal.getBytes(StandardCharsets.UTF_8)).toString();
        NodeId nodeId = new NodeId(new Uri(nodeString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(HWVTEP_TOPOLOGY_ID);
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    public static InstanceIdentifier<Node> convertToInstanceIdentifier(String nodeIdString) {
        NodeId nodeId = new NodeId(new Uri(nodeIdString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(HWVTEP_TOPOLOGY_ID);
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    /**
     * Build other config data for HA node .
     *
     * @param key The key as in HA child device other config
     * @param val The value as in HA child device other config
     * @return return other config object
     */
    public static ManagerOtherConfigsBuilder getOtherConfigBuilder(String key, String val) {
        ManagerOtherConfigsBuilder otherConfigsBuilder = new ManagerOtherConfigsBuilder();
        ManagerOtherConfigsKey otherConfigsKey = new ManagerOtherConfigsKey(key);
        otherConfigsBuilder.withKey(otherConfigsKey);
        otherConfigsBuilder.setOtherConfigKey(key);
        otherConfigsBuilder.setOtherConfigValue(val);
        return otherConfigsBuilder;
    }

    public static <D extends Datastore> Node readNode(TypedReadWriteTransaction<D> tx, InstanceIdentifier<Node> nodeId)
            throws ExecutionException, InterruptedException {
        Optional<Node> optional = tx.read(nodeId).get();
        if (optional.isPresent()) {
            return optional.get();
        }
        return null;
    }

    public static String convertToGlobalNodeId(String psNodeId) {
        int idx = psNodeId.indexOf(PHYSICALSWITCH);
        if (idx > 0) {
            return psNodeId.substring(0, idx);
        }
        return psNodeId;
    }

    /**
     * Trnaform logical switch to nodepath passed .
     *
     * @param src {@link HwvtepLogicalSwitchRef} Logical Switch Ref which needs to be transformed
     * @param nodePath {@link InstanceIdentifier} src needs to be transformed to this path
     * @return ref {@link HwvtepLogicalSwitchRef} the transforrmed result
     */
    public static HwvtepLogicalSwitchRef convertLogicalSwitchRef(HwvtepLogicalSwitchRef src,
                                                                 InstanceIdentifier<Node> nodePath) {
        InstanceIdentifier<LogicalSwitches> srcId = (InstanceIdentifier<LogicalSwitches>)src.getValue();
        HwvtepNodeName switchName = srcId.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName();
        InstanceIdentifier<LogicalSwitches> iid = nodePath.augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class, new LogicalSwitchesKey(switchName));
        HwvtepLogicalSwitchRef ref = new HwvtepLogicalSwitchRef(iid);
        return ref;
    }

    /**
     * Trnaform locator reference to nodepath passed .
     *
     * @param src {@link HwvtepPhysicalLocatorRef} Logical Switch Ref which needs to be transformed
     * @param nodePath {@link InstanceIdentifier} src needs to be transformed to this path
     * @return physicalLocatorRef {@link HwvtepPhysicalLocatorRef} the transforrmed result
     */
    public static HwvtepPhysicalLocatorRef convertLocatorRef(HwvtepPhysicalLocatorRef src,
                                                             InstanceIdentifier<Node> nodePath) {
        InstanceIdentifier<TerminationPoint> srcTepPath = (InstanceIdentifier<TerminationPoint>)src.getValue();
        TpId tpId = srcTepPath.firstKeyOf(TerminationPoint.class).getTpId();
        InstanceIdentifier<TerminationPoint> tpPath =
                nodePath.child(TerminationPoint.class, new TerminationPointKey(tpId));
        HwvtepPhysicalLocatorRef physicalLocatorRef = new HwvtepPhysicalLocatorRef(tpPath);
        return physicalLocatorRef;
    }

    public static boolean isEmptyList(@Nullable List list) {
        return list == null || list.isEmpty();
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    @Nullable
    public static Node getOriginal(DataObjectModification<Node> mod) {
        Node node = null;
        switch (mod.getModificationType()) {
            case SUBTREE_MODIFIED:
            case DELETE:
                node = mod.getDataBefore();
                break;
            case WRITE:
                if (mod.getDataBefore() !=  null) {
                    node = mod.getDataBefore();
                }
                break;
            default:
                break;
        }
        return node;
    }

    @Nullable
    public static Node getUpdated(DataObjectModification<Node> mod) {
        switch (mod.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                return mod.getDataAfter();
            default:
                return null;
        }
    }

    @Nullable
    public static Node getCreated(DataObjectModification<Node> mod) {
        if (mod.getModificationType() == DataObjectModification.ModificationType.WRITE
                && mod.getDataBefore() == null) {
            return mod.getDataAfter();
        }
        return null;
    }

    @Nullable
    public static Node getRemoved(DataObjectModification<Node> mod) {
        if (mod.getModificationType() == DataObjectModification.ModificationType.DELETE) {
            return mod.getDataBefore();
        }
        return null;
    }

    @Nullable
    public static String getPsName(Node psNode) {
        String psNodeId = psNode.getNodeId().getValue();
        if (psNodeId.contains(PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(PHYSICALSWITCH) + PHYSICALSWITCH.length());
        }
        return null;
    }

    @Nullable
    public static String getPsName(InstanceIdentifier<Node> psNodeIid) {
        String psNodeId = psNodeIid.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(PHYSICALSWITCH) +  PHYSICALSWITCH.length());
        }
        return null;
    }

    @Nullable
    public static String getPsName(String psNodeId) {
        if (psNodeId.contains(PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(PHYSICALSWITCH) + PHYSICALSWITCH.length());
        }
        return null;
    }

    public static InstanceIdentifier<Node> getGlobalNodePathFromPSNode(Node psNode) {
        String psNodeId = psNode.getNodeId().getValue();
        if (psNodeId.contains(PHYSICALSWITCH)) {
            return convertToInstanceIdentifier(psNodeId.substring(0, psNodeId.indexOf(PHYSICALSWITCH)));
        }
        return convertToInstanceIdentifier(psNodeId);
    }

    @Nullable
    public static InstanceIdentifier<Node> convertPsPath(Node psNode, InstanceIdentifier<Node> nodePath) {
        String psNodeId = psNode.getNodeId().getValue();
        if (psNodeId.contains(PHYSICALSWITCH)) {
            String psName = psNodeId.substring(psNodeId.indexOf(PHYSICALSWITCH) + PHYSICALSWITCH.length());
            String haPsNodeIdVal = nodePath.firstKeyOf(Node.class).getNodeId().getValue() + PHYSICALSWITCH + psName;
            InstanceIdentifier<Node> haPsPath = convertToInstanceIdentifier(haPsNodeIdVal);
            return haPsPath;
        } else {
            LOG.error("Failed to find ps path from node {}", psNode);
            return null;
        }
    }

    public static NodeBuilder getNodeBuilderForPath(InstanceIdentifier<Node> haPath) {
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(haPath.firstKeyOf(Node.class).getNodeId());
        return nodeBuilder;
    }

    @Nullable
    public static String getHAIdFromManagerOtherConfig(Node node) {
        if (node.augmentation(HwvtepGlobalAugmentation.class) == null) {
            return null;
        }
        HwvtepGlobalAugmentation globalAugmentation = node.augmentation(HwvtepGlobalAugmentation.class);
        if (globalAugmentation != null) {
            List<Managers> managers = new ArrayList<Managers>(globalAugmentation.nonnullManagers().values());
            if (managers != null && !managers.isEmpty() && managers.get(0).nonnullManagerOtherConfigs() != null) {
                for (ManagerOtherConfigs configs : managers.get(0).nonnullManagerOtherConfigs().values()) {
                    if (HA_ID.equals(configs.getOtherConfigKey())) {
                        return configs.getOtherConfigValue();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns ha child node path from ha node of config data tree.
     *
     * @param haGlobalConfigNodeOptional HA global node
     * @return ha Child ids
     */
    public static  List<NodeId> getChildNodeIdsFromManagerOtherConfig(Optional<Node> haGlobalConfigNodeOptional) {
        List<NodeId> childNodeIds = new ArrayList<>();
        if (!haGlobalConfigNodeOptional.isPresent()) {
            return childNodeIds;
        }
        HwvtepGlobalAugmentation augmentation =
                haGlobalConfigNodeOptional.get().augmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null && augmentation.getManagers() != null
                && augmentation.getManagers().size() > 0) {
            Managers managers = new ArrayList<>(augmentation.nonnullManagers().values()).get(0);
            if (null == managers.getManagerOtherConfigs()) {
                return childNodeIds;
            }
            for (ManagerOtherConfigs otherConfigs : managers.nonnullManagerOtherConfigs().values()) {
                if (HA_CHILDREN.equals(otherConfigs.getOtherConfigKey())) {
                    String nodeIdsVal = otherConfigs.getOtherConfigValue();
                    if (nodeIdsVal != null) {
                        String[] parts = nodeIdsVal.split(",");
                        for (String part : parts) {
                            childNodeIds.add(new NodeId(part));
                        }
                    }

                }
            }
        }
        return childNodeIds;
    }

    /**
     * Return PS children for passed PS node .
     *
     * @param psNodId PS node path
     * @return child Switches
     */
    public static Set<InstanceIdentifier<Node>> getPSChildrenIdsForHAPSNode(String psNodId) {
        if (!psNodId.contains(PHYSICALSWITCH)) {
            return Collections.emptySet();
        }
        String nodeId = convertToGlobalNodeId(psNodId);
        InstanceIdentifier<Node> iid = convertToInstanceIdentifier(nodeId);
        if (hwvtepHACache.isHAParentNode(iid)) {
            Set<InstanceIdentifier<Node>> childSwitchIds = new HashSet<>();
            Set<InstanceIdentifier<Node>> childGlobalIds = hwvtepHACache.getChildrenForHANode(iid);
            final String append = psNodId.substring(psNodId.indexOf(PHYSICALSWITCH));
            for (InstanceIdentifier<Node> childId : childGlobalIds) {
                String childIdVal = childId.firstKeyOf(Node.class).getNodeId().getValue();
                childSwitchIds.add(convertToInstanceIdentifier(childIdVal + append));
            }
            return childSwitchIds;
        }
        return Collections.emptySet();
    }

    public static HwvtepGlobalAugmentation getGlobalAugmentationOfNode(Node node) {
        HwvtepGlobalAugmentation result = null;
        if (node != null) {
            result = node.augmentation(HwvtepGlobalAugmentation.class);
        }
        if (result == null) {
            result = new HwvtepGlobalAugmentationBuilder().build();
        }
        return result;
    }

    public static PhysicalSwitchAugmentation getPhysicalSwitchAugmentationOfNode(Node psNode) {
        PhysicalSwitchAugmentation result = null;
        if (psNode != null) {
            result = psNode.augmentation(PhysicalSwitchAugmentation.class);
        }
        if (result == null) {
            result = new PhysicalSwitchAugmentationBuilder().build();
        }
        return result;
    }

    /**
     * Transform child managers (Source) to HA managers using HA node path.
     *
     * @param childNode Child Node
     * @param haGlobalCfg HA global config node
     * @return Transformed managers
     */
    public static List<Managers> buildManagersForHANode(Node childNode, Optional<Node> haGlobalCfg) {

        Set<NodeId> nodeIds = new HashSet<>();
        nodeIds.add(childNode.getNodeId());
        List<NodeId> childNodeIds = getChildNodeIdsFromManagerOtherConfig(haGlobalCfg);
        nodeIds.addAll(childNodeIds);

        InstanceIdentifier<Node> parentIid = HwvtepHACache.getInstance().getParent(
                convertToInstanceIdentifier(childNode.getNodeId().getValue()));
        HwvtepHACache.getInstance().getChildrenForHANode(parentIid).stream()
                .forEach(iid -> nodeIds.add(iid.firstKeyOf(Node.class).getNodeId()));

        ManagersBuilder builder1 = new ManagersBuilder();

        builder1.withKey(new ManagersKey(new Uri(MANAGER_KEY)));
        List<ManagerOtherConfigs> otherConfigses = new ArrayList<>();
        String children = nodeIds.stream().map(NodeId::getValue).collect(Collectors.joining(","));
        otherConfigses.add(getOtherConfigBuilder(HA_CHILDREN, children).build());
        builder1.setManagerOtherConfigs(otherConfigses);
        List<Managers> managers = new ArrayList<>();
        managers.add(builder1.build());
        return managers;
    }

    /**
     * Transform child switch (Source) to HA swicthes using HA node path.
     *
     * @param childNode  HA child node
     * @param haNodePath  HA node path
     * @param haNode Ha node object
     * @return Transformed switches
     */
    public static List<Switches> buildSwitchesForHANode(Node childNode,
                                                        InstanceIdentifier<Node> haNodePath,
                                                        Optional<Node> haNode) {
        List<Switches> psList = new ArrayList<>();
        boolean switchesAlreadyPresent = false;
        if (haNode.isPresent()) {
            Node node = haNode.get();
            HwvtepGlobalAugmentation augmentation = node.augmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null) {
                if (augmentation.getSwitches() != null) {
                    if (augmentation.getSwitches().size() > 0) {
                        switchesAlreadyPresent = true;
                    }
                }
            }
        }
        if (!switchesAlreadyPresent) {
            HwvtepGlobalAugmentation augmentation = childNode.augmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null && augmentation.getSwitches() != null) {
                List<Switches> src = new ArrayList<>(augmentation.nonnullSwitches().values());
                if (src != null && src.size() > 0) {
                    psList.add(new SwitchesCmd().transform(haNodePath, src.get(0)));
                }
            }
        }
        return psList;
    }

    /**
     * Build HA Global node from child nodes in config data tress.
     *
     * @param tx Transaction
     * @param childNode Child Node object
     * @param haNodePath Ha node path
     * @param haGlobalCfg HA global node object
     */
    public static void buildGlobalConfigForHANode(TypedReadWriteTransaction<Configuration> tx,
                                                  Node childNode,
                                                  InstanceIdentifier<Node> haNodePath,
                                                  Optional<Node> haGlobalCfg) {

        NodeBuilder nodeBuilder = new NodeBuilder();
        HwvtepGlobalAugmentationBuilder hwvtepGlobalBuilder = new HwvtepGlobalAugmentationBuilder();
        hwvtepGlobalBuilder.setSwitches(buildSwitchesForHANode(childNode, haNodePath, haGlobalCfg));
        hwvtepGlobalBuilder.setManagers(buildManagersForHANode(childNode, haGlobalCfg));

        nodeBuilder.setNodeId(haNodePath.firstKeyOf(Node.class).getNodeId());
        nodeBuilder.addAugmentation(hwvtepGlobalBuilder.build());
        Node configHANode = nodeBuilder.build();
        tx.mergeParentStructureMerge(haNodePath, configHANode);
    }

    public static <D extends Datastore> void deleteNodeIfPresent(TypedReadWriteTransaction<D> tx,
        InstanceIdentifier<?> iid) throws ExecutionException, InterruptedException {
        if (tx.read(iid).get().isPresent()) {
            LOG.info("Deleting child node {}", getNodeIdVal(iid));
            tx.delete(iid);
        }
    }

    /**
     * Delete PS data of HA node of Config Data tree.
     *
     * @param key Node object
     * @param haNode Ha Node from which to be deleted
     * @param tx Transaction
     */
    public static void deletePSNodesOfNode(InstanceIdentifier<Node> key, Node haNode,
        TypedReadWriteTransaction<Configuration> tx) throws ExecutionException, InterruptedException {
        //read from switches attribute and clean up them
        HwvtepGlobalAugmentation globalAugmentation = haNode.augmentation(HwvtepGlobalAugmentation.class);
        if (globalAugmentation == null) {
            return;
        }
        HashMap<InstanceIdentifier<Node>,Boolean> deleted = new HashMap<>();
        List<Switches> switches = new ArrayList<>(globalAugmentation.nonnullSwitches().values());
        if (switches != null) {
            for (Switches switche : switches) {
                InstanceIdentifier<Node> psId = (InstanceIdentifier<Node>)switche.getSwitchRef().getValue();
                deleteNodeIfPresent(tx, psId);
                deleted.put(psId, Boolean.TRUE);
            }
        }
        //also read from managed by attribute of switches and cleanup them as a back up if the above cleanup fails
        Optional<Topology> topologyOptional = tx.read(key.firstIdentifierOf(Topology.class)).get();
        String deletedNodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
        if (topologyOptional.isPresent()) {
            Topology topology = topologyOptional.get();
            if (topology.getNode() != null) {
                for (Node psNode : topology.nonnullNode().values()) {
                    PhysicalSwitchAugmentation ps = psNode.augmentation(PhysicalSwitchAugmentation.class);
                    if (ps != null) {
                        InstanceIdentifier<Node> iid = (InstanceIdentifier<Node>)ps.getManagedBy().getValue();
                        String nodeIdVal = iid.firstKeyOf(Node.class).getNodeId().getValue();
                        if (deletedNodeId.equals(nodeIdVal)) {
                            InstanceIdentifier<Node> psNodeId =
                                    convertToInstanceIdentifier(psNode.getNodeId().getValue());
                            if (deleted.containsKey(psNodeId)) {
                                deleteNodeIfPresent(tx, psNodeId);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Delete switches from Node in Operational Data Tree .
     *
     * @param haPath HA node path from whih switches will be deleted
     * @param tx  Transaction object
     * @throws ReadFailedException  Exception thrown if read fails
     */
    /*public static void deleteSwitchesManagedByNode(InstanceIdentifier<Node> haPath,
                                                   ReadWriteTransaction tx)
            throws ReadFailedException {

        Optional<Node> nodeOptional = tx.read(OPERATIONAL, haPath).checkedGet();
        if (!nodeOptional.isPresent()) {
            return;
        }
        Node node = nodeOptional.get();
        HwvtepGlobalAugmentation globalAugmentation = node.augmentation(HwvtepGlobalAugmentation.class);
        if (globalAugmentation == null) {
            return;
        }
        List<Switches> switches = globalAugmentation.getSwitches();
        if (switches != null) {
            for (Switches switche : switches) {
                InstanceIdentifier<Node> id = (InstanceIdentifier<Node>)switche.getSwitchRef().getValue();
                deleteNodeIfPresent(tx, OPERATIONAL, id);
            }
        }
    }*/

    /**
     * Returns true/false if all the childrens are deleted from Operational Data store.
     *
     * @param children IID for the child node to read from OP data tree
     * @param tx Transaction
     * @return true/false boolean
     * @throws ReadFailedException Exception thrown if read fails
     */
    /*public static boolean areAllChildDeleted(Set<InstanceIdentifier<Node>> children,
                                             ReadWriteTransaction tx) throws ReadFailedException {
        for (InstanceIdentifier<Node> childId : children) {
            if (tx.read(OPERATIONAL, childId).checkedGet().isPresent()) {
                return false;
            }
        }
        return true;
    }*/
}
