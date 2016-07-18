/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.ovsdb.utils.config.ConfigProperties;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeNetdev;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides functions for creating bridges via OVSDB, specifically the br-int bridge.
 * Note and TODO: br-ex is temporary. vpnservice does not require it but for the time being it is
 * left here because devstack expects it.
 */
public class ElanBridgeManager {
    private static final Logger LOG = LoggerFactory.getLogger(ElanBridgeManager.class);

    public static final String PROVIDER_MAPPINGS_KEY = "provider_mappings";
    private static final String INTEGRATION_BRIDGE = "br-int";
    private static final String EXTERNAL_BRIDGE = "br-ex";
    private static final String EXT_BR_PFX = "br-ex-";
    private static final String INT_SIDE_PATCH_PORT_SUFFIX = "-ex-patch";
    private final String EX_SIDE_PATCH_PORT_SUFFIX = "-int-patch";

    private ElanServiceProvider elanServiceProvider = null;
    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southboundUtils;
    private final DataBroker dataBroker;
    private Random random;


    /**
     * Construct a new ElanBridgeManager
     * @param dataBroker
     */
    public ElanBridgeManager(ElanServiceProvider elanServiceProvider, DataBroker dataBroker) {
        this.elanServiceProvider = elanServiceProvider;
        this.dataBroker = dataBroker;
        //TODO: ClusterAware!!!??
        this.mdsalUtils = new MdsalUtils(dataBroker);
        this.southboundUtils = new SouthboundUtils(mdsalUtils);
        this.random = new Random(System.currentTimeMillis());
    }

    /**
     * Is OVS running in userspace mode?
     * @return true if the ovsdb.userspace.enabled variable is set to true
     */
    public boolean isUserSpaceEnabled() {
        final String enabledPropertyStr = ConfigProperties.getProperty(this.getClass(), "ovsdb.userspace.enabled");
        return enabledPropertyStr != null && enabledPropertyStr.equalsIgnoreCase("yes");
    }

    /**
     * Is vpnservice handling L3 as well? This function is deprocated because br-ex is temporary as mentioned above
     * @return true if the ovsdb.l3.fwd.enable is set to true
     */
    @Deprecated
    public boolean isL3ForwardingEnabled() {
        final String enabledPropertyStr = ConfigProperties.getProperty(this.getClass(), "ovsdb.l3.fwd.enabled");
        return enabledPropertyStr != null && enabledPropertyStr.equalsIgnoreCase("yes");
    }

    /**
     * Is the Node object an OVSDB node?
     * @param node unidentified node object
     * @return true if the Node is an OVSDB node
     */
    public boolean isOvsdbNode(Node node) {
        return southboundUtils.extractNodeAugmentation(node) != null;
    }

    /**
     * Is this Node the integration bridge (br-int)
     * @param node unidentified noe object
     * @return true if the Node is a bridge and it is the integration bridge
     */
    public boolean isIntegrationBridge(Node node) {
        if (!isBridgeNode(node)) {
            return false;
        }

        String bridgeName = southboundUtils.extractBridgeName(node);
        if (bridgeName == null) {
            return false;
        }

        return bridgeName.equals(INTEGRATION_BRIDGE);
    }

    /**
     * Is this a bridge created for an external physical network. Physical network interfaces are specified
     * in the Open_vSwitch table's other_config. All bridges created for these interfaces begin with "br-ex-"
     * followed by the name of the interface
     * @param node unidentified node object
     * @return true if this node is an external physical network bridge
     */
    public boolean isExternalPhysnetBridge(Node node) {
        if (!isBridgeNode(node)) {
            return false;
        }

        String bridgeName = southboundUtils.extractBridgeName(node);
        if (bridgeName == null) {
            return false;
        }

        return bridgeName.startsWith(EXT_BR_PFX);
    }

    /**
     * Is this node a bridge?
     * @param node unidentified node object
     * @return true if this node is a bridge
     */
    public boolean isBridgeNode(Node node) {
        return southboundUtils.extractBridgeAugmentation(node) != null;
    }

    /**
     * Advance the "preperation" of the OVSDB node. This re-entrant method advances the state of an OVSDB
     * node towards the prepared state where all bridges and patch ports are created and active. This method
     * should be invoked for the OVSDB node, the integration bridge node and any external physical network
     * nodes BUT IT IS SAFE TO INVOKE IT ON ANY NODE.
     * @param node A node
     * @param generateIntBridgeMac whether or not the int bridge's mac should be set to a random value
     */
    public void processNodePrep(Node node, boolean generateIntBridgeMac) {

        if (isOvsdbNode(node)) {
            createBridges(node, generateIntBridgeMac);
            return;
        }

        Node ovsdbNode = southboundUtils.readOvsdbNode(node);
        if (ovsdbNode == null) {
            LOG.error("Node is neither bridge nor ovsdb {}", node);
            return;
        }

        if (isIntegrationBridge(node)) {
            createExternalBridgesForPhysnets(ovsdbNode);
            return;
        }

        if (isExternalPhysnetBridge(node)) {
            patchExternalPhysnetBridgeToBrInt(ovsdbNode, node);
            writeNormalFlow(node);
            return;
        }
    }

    private void writeNormalFlow(Node physnetBridge) {
        List<InstructionInfo> instructionInfos = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionInfos = new ArrayList<ActionInfo>();
        actionInfos.add(new ActionInfo(ActionType.output,
                new String[] {physnetBridge.getNodeId().getValue() + ":" + "NORMAL"}));
        instructionInfos.add(new InstructionInfo(InstructionType.apply_actions, actionInfos));

        FlowEntity flowEntity =  MDSALUtil.buildFlowEntity(
                                                BigInteger.valueOf(southboundUtils.getDataPathId(physnetBridge)),
                                                (short)0,
                                                "NORMAL",
                                                0,
                                                "NORMAL",
                                                0,
                                                0,
                                                BigInteger.ZERO,
                                                null,
                                                instructionInfos);
        elanServiceProvider.getMdsalManager().installFlow(flowEntity);
    }

    private void patchExternalPhysnetBridgeToBrInt(Node ovsdbNode, Node physnetBridge) {
        String physnetBridgeName = southboundUtils.extractBridgeName(physnetBridge);
        String portName = physnetBridgeName.substring(EXT_BR_PFX.length());

        Node intBridgeNode = southboundUtils.readBridgeNode(ovsdbNode, INTEGRATION_BRIDGE);
        if (intBridgeNode == null) {
            LOG.error("Could not read br-int from {}", ovsdbNode);
        }

        if (!addPortToBridge(physnetBridge, physnetBridgeName, portName)) {
            LOG.error("Failed to add {} port to {}", portName, physnetBridge);
            return;
        }

        String portNameInt = getIntSidePatchPortName(portName);
        String portNameExt = getExSidePatchPortName(portName);
        if (!addPatchPort(intBridgeNode, INTEGRATION_BRIDGE, portNameInt, portNameExt)) {
            LOG.error("Failed to add patch port {} to {}", portNameInt, intBridgeNode);
            return;
        }

        if (!addPatchPort(physnetBridge, physnetBridgeName, portNameExt, portNameInt)) {
            LOG.error("Failed to add patch port {} to {}", portNameExt, physnetBridge);
            return;
        }
    }

    private void createExternalBridgesForPhysnets(Node ovsdbNode) {
        Optional<Map<String, String>> providerMappings = getOpenvswitchOtherConfigMap(ovsdbNode, PROVIDER_MAPPINGS_KEY);

        for (String portName : providerMappings.or(Collections.emptyMap()).values()) {
            String bridgeName = EXT_BR_PFX + portName;
            if (!addBridge(ovsdbNode, bridgeName, null)) {
                LOG.error("Failed to create bridge {}  on {}", bridgeName, ovsdbNode);
            }
        }
    }

    private void createBridges(Node ovsdbNode, boolean generateIntBridgeMac) {

        try {
            createIntegrationBridge(ovsdbNode, generateIntBridgeMac);
        } catch (Exception e) {
            LOG.error("Error creating Integration Bridge on " + ovsdbNode, e);
            return;
        }

        try {
            if (isL3ForwardingEnabled()) {
                createExternalBridge(ovsdbNode);
            }
        } catch (Exception e) {
            LOG.error("Error creating External Bridge on " + ovsdbNode, e);
            return;
        }
    }

    private boolean createIntegrationBridge(Node ovsdbNode, boolean generateIntBridgeMac) {
        LOG.debug("ElanBridgeManager.createIntegrationBridge, skipping if exists");
        if (!addBridge(ovsdbNode, INTEGRATION_BRIDGE,
                generateIntBridgeMac ? generateRandomMac() : null)) {
            LOG.warn("Integration Bridge Creation failed");
            return false;
        }
        return true;
    }

    private boolean createExternalBridge(Node ovsdbNode) {
        LOG.debug("ElanBridgeManager.createExternalBridge, skipping if exists");
        if (!addBridge(ovsdbNode, EXTERNAL_BRIDGE, null)) {
            LOG.warn("External Bridge Creation failed");
            return false;
        }
        return true;
    }

    /**
     * Add a bridge to the OVSDB node but check that it does not exist in the CONFIGURATION or OPERATIONAL md-sals first
     * @param ovsdbNode Which OVSDB node
     * @param bridgeName Name of the bridge
     * @param mac mac address to set on the bridge or null
     * @return true if no errors occurred
     */
    public boolean addBridge(Node ovsdbNode, String bridgeName, String mac) {
        boolean rv = true;
        if ((!southboundUtils.isBridgeOnOvsdbNode(ovsdbNode, bridgeName)) ||
                (southboundUtils.getBridgeFromConfig(ovsdbNode, bridgeName) == null)) {
            Class<? extends DatapathTypeBase> dpType = null;
            if (isUserSpaceEnabled()) {
                dpType = DatapathTypeNetdev.class;
            }
            rv = southboundUtils.addBridge(ovsdbNode, bridgeName, southboundUtils.getControllersFromOvsdbNode(ovsdbNode), dpType, mac);
        }
        return rv;
    }

    /**
     * Get OpenvSwitch other-config by key.
     * @param node OVSDB node
     * @param key key to extract from other-config
     * @return the value for key or null if key not found
     */
    public String getOpenvswitchOtherConfig(Node node, String key) {
        OvsdbNodeAugmentation ovsdbNode = node.getAugmentation(OvsdbNodeAugmentation.class);
        if (ovsdbNode == null) {
            Node nodeFromReadOvsdbNode = getOvsdbNodeFromOperational(node);
            if (nodeFromReadOvsdbNode != null) {
                ovsdbNode = nodeFromReadOvsdbNode.getAugmentation(OvsdbNodeAugmentation.class);
            }
        }

        if (ovsdbNode != null && ovsdbNode.getOpenvswitchOtherConfigs() != null) {
            for (OpenvswitchOtherConfigs openvswitchOtherConfigs : ovsdbNode.getOpenvswitchOtherConfigs()) {
                if (openvswitchOtherConfigs.getOtherConfigKey().equals(key)) {
                    return openvswitchOtherConfigs.getOtherConfigValue();
                }
            }
        }

        return null;
    }

    /**
     * Extract OpenvSwitch other-config to key value map.
     * @param node OVSDB node
     * @param key key to extract from other-config
     * @return Optional of key-value Map
     */
    public Optional<Map<String, String>> getOpenvswitchOtherConfigMap(Node node, String key) {
        String providerMappings = getOpenvswitchOtherConfig(node, key);
        return extractMultiKeyValueToMap(providerMappings);
    }

    /**
     * Get all OVS nodes from topology.
     * @return a list of all nodes or null if not found
     */
    public List<Node> getOvsNodes() {
        InstanceIdentifier<Topology> inst = InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID));
        Topology topology = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, inst);
        return topology != null ? topology.getNode() : null;
    }

    /**
     * Get the OVS node physical interface name from provider mappings.
     * @param node OVSDB node
     * @param physicalNetworkName name of physical network
     */
    public String getPhysicalInterfaceName(Node node, String physicalNetworkName) {
        Optional<Map<String, String>> providerMappings = getOpenvswitchOtherConfigMap(node, PROVIDER_MAPPINGS_KEY);
        if (!providerMappings.isPresent()) {
            LOG.trace("Physical network {} not found in {}", physicalNetworkName, PROVIDER_MAPPINGS_KEY);
            return null;
        }

        return providerMappings.get().get(physicalNetworkName);
    }

    /**
     * Get the name of the patch-port which is patched to the bridge containing interfaceName
     * @param interfaceName The external interface
     * @return
     */
    public static String getIntSidePatchPortName(String interfaceName) {
        return interfaceName + INT_SIDE_PATCH_PORT_SUFFIX;
    }

    private String getExSidePatchPortName(String physicalInterfaceName) {
        return physicalInterfaceName + EX_SIDE_PATCH_PORT_SUFFIX;
    }

    /**
     * Add a port to a bridge
     * @param node the bridge node
     * @param bridgeName name of the bridge
     * @param portName name of port to add
     * @return true if successful in writing to mdsal
     */
    public boolean addPortToBridge (Node node, String bridgeName, String portName) {
        boolean rv = true;

        if (southboundUtils.extractTerminationPointAugmentation(node, portName) == null) {
            rv = southboundUtils.addTerminationPoint(node, bridgeName, portName, null);

            if (rv) {
                LOG.debug("addPortToBridge: node: {}, bridge: {}, portname: {} status: success",
                        node.getNodeId().getValue(), bridgeName, portName);
            } else {
                LOG.error("addPortToBridge: node: {}, bridge: {}, portname: {} status: FAILED",
                        node.getNodeId().getValue(), bridgeName, portName);
            }
        } else {
            LOG.trace("addPortToBridge: node: {}, bridge: {}, portname: {} status: not_needed",
                    node.getNodeId().getValue(), bridgeName, portName);
        }

        return rv;
    }

    /**
     * Add a patch port to a bridge
     * @param node the bridge node
     * @param bridgeName name of the bridge
     * @param portName name of the port
     * @param peerPortName name of the port's peer (the other side)
     * @return true if successful
     */
    public boolean addPatchPort (Node node, String bridgeName, String portName, String peerPortName) {
        boolean rv = true;

        if (southboundUtils.extractTerminationPointAugmentation(node, portName) == null) {
            rv = southboundUtils.addPatchTerminationPoint(node, bridgeName, portName, peerPortName);

            if (rv) {
                LOG.info("addPatchPort: node: {}, bridge: {}, portname: {} peer: {} status: success",
                        node.getNodeId().getValue(), bridgeName, portName, peerPortName);
            } else {
                LOG.error("addPatchPort: node: {}, bridge: {}, portname: {} peer: {} status: FAILED",
                        node.getNodeId().getValue(), bridgeName, portName, peerPortName);
            }
        } else {
            LOG.trace("addPatchPort: node: {}, bridge: {}, portname: {} peer: {} status: not_needed",
                    node.getNodeId().getValue(), bridgeName, portName, peerPortName);
        }

        return rv;
    }

    @SuppressWarnings("unchecked")
    private Node getOvsdbNodeFromOperational(Node bridgeNode) {
        Node ovsdbNode = null;
        OvsdbBridgeAugmentation bridgeAugmentation = bridgeNode.getAugmentation(OvsdbBridgeAugmentation.class);
        if (bridgeAugmentation != null) {
            InstanceIdentifier<Node> ovsdbNodeIid =
                    (InstanceIdentifier<Node>) bridgeAugmentation.getManagedBy().getValue();
            ovsdbNode = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, ovsdbNodeIid);
        }else{
            LOG.debug("Provided node is not a bridge node : {}",bridgeNode);
        }
        return ovsdbNode;
    }

    public long getDatapathId(Node node) {
        return southboundUtils.getDataPathId(node);
    }

    private String generateRandomMac() {
        byte[] macBytes = new byte[6];
        random.nextBytes(macBytes);
        macBytes[0] &= 0xfc; //the two low bits of the first byte need to be zero

        StringBuilder stringBuilder = new StringBuilder();

        int i = 0;
        while(true) {
            stringBuilder.append(String.format("%02x", macBytes[i++]));
            if (i >= 6) {
                break;
            }
            stringBuilder.append(':');
        }

        return stringBuilder.toString();
    }

    private static Optional<Map<String, String>> extractMultiKeyValueToMap(String multiKeyValueStr) {
        if (Strings.isNullOrEmpty(multiKeyValueStr)) {
            return Optional.absent();
        }

        Map<String, String> valueMap = new HashMap<>();
        Splitter splitter = Splitter.on(",");
        for (String keyValue : splitter.split(multiKeyValueStr)) {
            String[] split = keyValue.split(":", 2);
            if (split != null && split.length == 2) {
                valueMap.put(split[0], split[1]);
            }
        }

        return Optional.of(valueMap);
    }
}
