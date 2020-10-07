/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeNetdev;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeOtherConfigsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagedNodeEntry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides functions for creating bridges via OVSDB, specifically the br-int bridge.
 */
@Singleton
// Checkstyle expects the first sentence to end with a period, question marks don’t count
@SuppressWarnings("checkstyle:SummaryJavadoc")
public class ElanBridgeManager {
    private static final Logger LOG = LoggerFactory.getLogger(ElanBridgeManager.class);

    public static final String PROVIDER_MAPPINGS_KEY = "provider_mappings";
    private static final String INTEGRATION_BRIDGE = "br-int";
    private static final String INT_SIDE_PATCH_PORT_SUFFIX = "-patch";
    private static final String EX_SIDE_PATCH_PORT_SUFFIX = "-int-patch";
    private static final String OTHER_CONFIG_PARAMETERS_DELIMITER = ",";
    private static final String OTHER_CONFIG_KEY_VALUE_DELIMITER = ":";
    private static final int MAX_LINUX_INTERFACE_NAME_LENGTH = 15;
    private static final String OTHER_CONFIG_DATAPATH_ID = "datapath-id";
    private static final String OTHER_CONFIG_HWADDR = "hwaddr";
    private static final String OTHER_CONFIG_DISABLE_IN_BAND = "disable-in-band";

    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;
    private final SouthboundUtils southboundUtils;
    private final Random random;
    private final Uint32 maxBackoff;
    private final Uint32 inactivityProbe;


    /**
     * Construct a new ElanBridgeManager.
     * @param elanConfig the elan configuration
     * @param interfaceManager InterfaceManager
     * @param southboundUtils southboutUtils
     * @param dataBroker DataBroker
     */
    @Inject
    public ElanBridgeManager(ElanConfig elanConfig, IInterfaceManager interfaceManager,
            SouthboundUtils southboundUtils, DataBroker dataBroker) {
        //TODO: ClusterAware!!!??
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.southboundUtils = southboundUtils;
        this.random = new Random(System.currentTimeMillis());
        this.maxBackoff = elanConfig.getControllerMaxBackoff() != null
                ? elanConfig.getControllerMaxBackoff() : Uint32.valueOf(1000);
        this.inactivityProbe = elanConfig.getControllerInactivityProbe() != null
                ? elanConfig.getControllerInactivityProbe() : Uint32.ZERO;
    }

    /**
     * Is OVS running in userspace mode?
     * @return true if the ovsdb.userspace.enabled variable is set to true
     */
    public boolean isUserSpaceEnabled() {
        final String enabledPropertyStr = System.getProperty("ovsdb.userspace.enabled", "no");
        return enabledPropertyStr != null && enabledPropertyStr.equalsIgnoreCase("yes");
    }

    /**
     * Is the Node object an OVSDB node.
     * @param node unidentified node object
     * @return true if the Node is an OVSDB node
     */
    public boolean isOvsdbNode(Node node) {
        return southboundUtils.extractNodeAugmentation(node) != null;
    }

    /**
     * Is this Node the integration bridge (br-int).
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
     * Is this node a bridge.
     * @param node unidentified node object
     * @return true if this node is a bridge
     */
    public boolean isBridgeNode(Node node) {
        return southboundUtils.extractBridgeAugmentation(node) != null;
    }

    /**
     * Advance the "preperation" of the OVSDB node. This re-entrant method advances the state of an OVSDB
     * node towards the prepared state where all bridges and patch ports are created and active. This method
     * should be invoked for the OVSDB node and the integration bridge node BUT IT IS SAFE TO INVOKE IT ON ANY NODE.
     * @param node A node
     * @param generateIntBridgeMac whether or not the int bridge's mac should be set to a random value
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void processNodePrep(Node node, boolean generateIntBridgeMac) {
        if (isOvsdbNode(node)) {
            if (southboundUtils.readBridgeNode(node, INTEGRATION_BRIDGE) == null) {
                LOG.debug("OVSDB node in operational does not have br-int, create one {}", node.getNodeId().getValue());
                try {
                    createIntegrationBridgeConfig(node, generateIntBridgeMac);
                } catch (RuntimeException e) {
                    LOG.error("Error creating bridge on {}", node, e);
                }
            }
            return;
        }

        //Assume "node" is a bridge node, extract the OVSDB node from operational
        Node ovsdbNode = southboundUtils.readOvsdbNode(node);
        if (ovsdbNode == null) {
            LOG.error("Node is neither bridge nor ovsdb {}", node);
            return;
        }

        if (isIntegrationBridge(node)) {
            prepareIntegrationBridge(ovsdbNode, node);
        }

    }

    public void handleNewProviderNetBridges(Node originalNode, Node updatedNode) {
        if (!isOvsdbNode(updatedNode)) {
            return;
        }

        List<ManagedNodeEntry> originalManagedNodes = new ArrayList<>(getManagedNodeEntries(originalNode));
        List<ManagedNodeEntry> updatedManagedNodes = new ArrayList<>(getManagedNodeEntries(updatedNode));

        updatedManagedNodes.removeAll(originalManagedNodes);
        if (updatedManagedNodes.isEmpty()) {
            return;
        }
        LOG.debug("handleNewProviderNetBridges checking if any of these are provider nets {}", updatedManagedNodes);

        Node brInt = southboundUtils.readBridgeNode(updatedNode, INTEGRATION_BRIDGE);
        if (brInt == null) {
            LOG.info("handleNewProviderNetBridges, br-int not found");
            return;
        }

        Collection<String> providerVals = getOpenvswitchOtherConfigMap(updatedNode, PROVIDER_MAPPINGS_KEY).values();
        for (ManagedNodeEntry nodeEntry : updatedManagedNodes) {
            String bridgeName = nodeEntry.getBridgeRef().getValue().firstKeyOf(Node.class).getNodeId().getValue();
            bridgeName = bridgeName.substring(bridgeName.lastIndexOf('/') + 1);
            if (bridgeName.equals(INTEGRATION_BRIDGE)) {
                continue;
            }
            if (providerVals.contains(bridgeName)) {
                patchBridgeToBrInt(brInt, southboundUtils.readBridgeNode(updatedNode, bridgeName), bridgeName);
            }
        }


    }

    @Nullable
    private List<ManagedNodeEntry> getManagedNodeEntries(Node node) {
        OvsdbNodeAugmentation ovsdbNode = southboundUtils.extractNodeAugmentation(node);
        if (ovsdbNode == null) {
            return null;
        }

        return new ArrayList<>(ovsdbNode.nonnullManagedNodeEntry().values());
    }

    private void prepareIntegrationBridge(Node ovsdbNode, Node brIntNode) {
        if (southboundUtils.getBridgeFromConfig(ovsdbNode, INTEGRATION_BRIDGE) == null) {
            LOG.debug("br-int in operational but not config, copying into config");
            copyBridgeToConfig(brIntNode);
        }

        Map<String, String> providerMappings = getOpenvswitchOtherConfigMap(ovsdbNode, PROVIDER_MAPPINGS_KEY);

        for (String value : providerMappings.values()) {
            if (southboundUtils.extractTerminationPointAugmentation(brIntNode, value) != null) {
                LOG.debug("prepareIntegrationBridge: port {} already exists on {}", value, INTEGRATION_BRIDGE);
                continue;
            }

            Node exBridgeNode = southboundUtils.readBridgeNode(ovsdbNode, value);
            if (exBridgeNode != null) {
                LOG.debug("prepareIntegrationBridge: bridge {} found. Patching to {}", value, INTEGRATION_BRIDGE);
                patchBridgeToBrInt(brIntNode, exBridgeNode, value);
            } else {
                LOG.debug("prepareIntegrationBridge: adding interface {} to {}", value, INTEGRATION_BRIDGE);
                if (!addPortToBridge(brIntNode, INTEGRATION_BRIDGE, value)) {
                    LOG.error("Failed to add {} port to {}", value, brIntNode);
                }
            }

        }

        if (!addControllerToBridge(ovsdbNode, INTEGRATION_BRIDGE)) {
            LOG.error("Failed to set controller to existing integration bridge {}", brIntNode);
        }

    }

    private void copyBridgeToConfig(Node brIntNode) {
        NodeBuilder bridgeNodeBuilder = new NodeBuilder(brIntNode);
        bridgeNodeBuilder.setTerminationPoint(Collections.emptyMap());
        InstanceIdentifier<Node> brNodeIid = SouthboundUtils.createInstanceIdentifier(brIntNode.getNodeId());
        try {
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION,
                brNodeIid, bridgeNodeBuilder.build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to copy Bridge {} to config", brNodeIid, e);
        }
    }

    private void patchBridgeToBrInt(Node intBridgeNode, Node exBridgeNode, String physnetBridgeName) {

        String portNameInt = getIntSidePatchPortName(physnetBridgeName);
        String portNameExt = getExSidePatchPortName(physnetBridgeName);
        if (!addPatchPort(intBridgeNode, INTEGRATION_BRIDGE, portNameInt, portNameExt)) {
            LOG.error("Failed to add patch port {} to {}", portNameInt, intBridgeNode);
            return;
        }

        if (!addPatchPort(exBridgeNode, physnetBridgeName, portNameExt, portNameInt)) {
            LOG.error("Failed to add patch port {} to {}", portNameExt, exBridgeNode);
            return;
        }
    }

    private boolean createIntegrationBridgeConfig(Node ovsdbNode, boolean generateIntBridgeMac) {
        // Make sure iface-type exist in Open_vSwitch table prior to br-int creation
        // in order to allow mixed topology of both DPDK and non-DPDK OVS nodes
        if (!ifaceTypesExist(ovsdbNode)) {
            LOG.debug("Skipping integration bridge creation as if-types has not been initialized");
            return false;
        }

        LOG.debug("ElanBridgeManager.createIntegrationBridgeConfig, skipping if exists");
        if (!addBridge(ovsdbNode, INTEGRATION_BRIDGE,
                generateIntBridgeMac ? generateRandomMac() : null)) {
            LOG.warn("Integration Bridge Creation failed");
            return false;
        }
        return true;
    }

    private boolean ifaceTypesExist(Node ovsdbNode) {
        OvsdbNodeAugmentation ovsdbNodeAugmentation = southboundUtils.extractNodeAugmentation(ovsdbNode);
        return ovsdbNodeAugmentation != null && ovsdbNodeAugmentation.getInterfaceTypeEntry() != null
                && !ovsdbNodeAugmentation.getInterfaceTypeEntry().isEmpty();
    }

    /**
     * Add a bridge to the OVSDB node but check that it does not exist in the
     * CONFIGURATION. If it already exists in OPERATIONAL, update it with all
     * configurable parameters but make sure to maintain the same datapath-id.
     *
     * @param ovsdbNode Which OVSDB node
     * @param bridgeName Name of the bridge
     * @param mac mac address to set on the bridge or null
     * @return true if no errors occurred
     */
    public boolean addBridge(Node ovsdbNode, String bridgeName, @Nullable String mac) {
        boolean rv = true;
        if (southboundUtils.getBridgeFromConfig(ovsdbNode, bridgeName) == null) {
            Class<? extends DatapathTypeBase> dpType = null;
            if (isUserSpaceEnabled()) {
                dpType = DatapathTypeNetdev.class;
            }

            List<BridgeOtherConfigs> otherConfigs = buildBridgeOtherConfigs(ovsdbNode, bridgeName, mac);

            rv = southboundUtils.addBridge(ovsdbNode, bridgeName,
                    southboundUtils.getControllersFromOvsdbNode(ovsdbNode), dpType, otherConfigs,
                    maxBackoff, inactivityProbe);
        }
        return rv;
    }

    private List<BridgeOtherConfigs> buildBridgeOtherConfigs(Node ovsdbNode, String bridgeName, String mac) {
        // First attempt to extract the bridge augmentation from operational...
        Node bridgeNode = southboundUtils.getBridgeNode(ovsdbNode, bridgeName);
        OvsdbBridgeAugmentation bridgeAug = null;
        if (bridgeNode != null) {
            bridgeAug = southboundUtils.extractBridgeAugmentation(bridgeNode);
        }

        // ...if present, it means this bridge already exists and we need to take
        // care not to change the datapath id. We do this by explicitly setting
        // other_config:datapath-id to the value reported in the augmentation.
        List<BridgeOtherConfigs> otherConfigs;
        if (bridgeAug != null) {
            DatapathId dpId = bridgeAug.getDatapathId();
            if (dpId != null) {
                otherConfigs = new ArrayList<>(bridgeAug.nonnullBridgeOtherConfigs().values());
                if (otherConfigs == null) {
                    otherConfigs = new ArrayList<>();
                }

                if (otherConfigs.stream().noneMatch(otherConfig ->
                            otherConfig.getBridgeOtherConfigKey().equals(OTHER_CONFIG_DATAPATH_ID))) {
                    String dpIdVal = dpId.getValue().replace(":", "");
                    otherConfigs.add(new BridgeOtherConfigsBuilder()
                                    .setBridgeOtherConfigKey(OTHER_CONFIG_DATAPATH_ID)
                                    .setBridgeOtherConfigValue(dpIdVal).build());
                }
            } else {
                otherConfigs = new ArrayList<>();
            }
        } else  {
            otherConfigs = new ArrayList<>();
            if (mac != null) {
                otherConfigs.add(new BridgeOtherConfigsBuilder()
                                .setBridgeOtherConfigKey(OTHER_CONFIG_HWADDR)
                                .setBridgeOtherConfigValue(mac).build());
            }
        }
        //ovsdb always adds disableInBand=true, so no need to add this default value here.
        /*if (otherConfigs.stream().noneMatch(otherConfig ->
                otherConfig.getBridgeOtherConfigKey().equals(OTHER_CONFIG_DISABLE_IN_BAND))) {
            otherConfigs.add(new BridgeOtherConfigsBuilder()
                            .setBridgeOtherConfigKey(OTHER_CONFIG_DISABLE_IN_BAND)
                            .setBridgeOtherConfigValue("true").build());
        }*/

        return otherConfigs;
    }

    private boolean addControllerToBridge(Node ovsdbNode,String bridgeName) {
        return southboundUtils.setBridgeController(ovsdbNode,
                bridgeName, southboundUtils.getControllersFromOvsdbNode(ovsdbNode),
                maxBackoff, inactivityProbe);
    }

    /**
     * {@inheritDoc}.
     */
    public Map<String, String> getOpenvswitchOtherConfigMap(Node node, String key) {
        String otherConfigVal = southboundUtils.getOpenvswitchOtherConfig(node, key);
        return getMultiValueMap(otherConfigVal);
    }

    /**
     * Get the OVS node physical interface name from provider mappings.
     * @param node OVSDB node
     * @param physicalNetworkName name of physical network
     * @return physical network name
     */
    @Nullable
    public String getProviderMappingValue(Node node, String physicalNetworkName) {
        Map<String, String> providerMappings = getOpenvswitchOtherConfigMap(node, PROVIDER_MAPPINGS_KEY);
        String providerMappingValue = providerMappings.get(physicalNetworkName);
        if (providerMappingValue == null) {
            LOG.trace("Physical network {} not found in {}", physicalNetworkName, PROVIDER_MAPPINGS_KEY);
        }

        return providerMappingValue;
    }

    /**
     * Get the name of the port in br-int for the given provider-mapping value. This is either a patch port to a bridge
     * with providerMappingValue - patch-&lt;providerMappingValue&gt; or simply a port with the same name as
     * providerMappingValue
     * @param bridgeNode br-int Node
     * @param providerMappingValue this is the last part of provider_mappings=net_name:THIS
     * @return the name of the port on br-int
     */
    public String getIntBridgePortNameFor(Node bridgeNode, String providerMappingValue) {
        String res = providerMappingValue;
        Node managingNode = southboundUtils.readOvsdbNode(bridgeNode);
        if (managingNode != null && southboundUtils.isBridgeOnOvsdbNode(managingNode, providerMappingValue)) {
            res = getIntSidePatchPortName(providerMappingValue);
        }

        return res;
    }

    /**
     * Get the name of the patch-port which is patched to the bridge containing
     * interfaceName. Patch port name is truncated to the maximum allowed characters
     *
     * @param interfaceName The external interface
     * @return interface name
     */
    public String getIntSidePatchPortName(String interfaceName) {
        String patchPortName = interfaceName + INT_SIDE_PATCH_PORT_SUFFIX;
        if (patchPortName.length() <= MAX_LINUX_INTERFACE_NAME_LENGTH) {
            return patchPortName;
        }

        LOG.debug("Patch port {} exceeds maximum allowed length. Truncating to {} characters",
                patchPortName, MAX_LINUX_INTERFACE_NAME_LENGTH);
        return patchPortName.substring(0, MAX_LINUX_INTERFACE_NAME_LENGTH - 1);
    }

    private static String getExSidePatchPortName(String physicalInterfaceName) {
        return physicalInterfaceName + EX_SIDE_PATCH_PORT_SUFFIX;
    }

    /**
     * Add a port to a bridge.
     * @param node the bridge node
     * @param bridgeName name of the bridge
     * @param portName name of port to add
     * @return true if successful in writing to mdsal
     */
    public boolean addPortToBridge(Node node, String bridgeName, String portName) {
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
     * Add a patch port to a bridge.
     * @param node the bridge node
     * @param bridgeName name of the bridge
     * @param portName name of the port
     * @param peerPortName name of the port's peer (the other side)
     * @return true if successful
     */
    public boolean addPatchPort(Node node, String bridgeName, String portName, String peerPortName) {
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

    public Optional<Uint64> getDpIdFromManagerNodeId(String managerNodeId) {
        InstanceIdentifier<Node> identifier = getIntegrationBridgeIdentifier(managerNodeId);
        OvsdbBridgeAugmentation integrationBridgeAugmentation = interfaceManager.getOvsdbBridgeForNodeIid(identifier);
        if (integrationBridgeAugmentation == null) {
            LOG.debug("Failed to get OvsdbBridgeAugmentation for node {}", managerNodeId);
            return Optional.empty();
        }

        return Optional.ofNullable(integrationBridgeAugmentation.getDatapathId())
                .map(datapathId -> MDSALUtil.getDpnId(datapathId.getValue()));
    }

    private static InstanceIdentifier<Node> getIntegrationBridgeIdentifier(String managerNodeId) {
        NodeId brNodeId = new NodeId(
                managerNodeId + "/" + ITMConstants.BRIDGE_URI_PREFIX + "/" + ITMConstants.DEFAULT_BRIDGE_NAME);
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(IfmConstants.OVSDB_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(brNodeId));
    }

    private String generateRandomMac() {
        byte[] macBytes = new byte[6];
        random.nextBytes(macBytes);
        macBytes[0] &= 0xfc; //the two low bits of the first byte need to be zero

        StringBuilder stringBuilder = new StringBuilder();

        int index = 0;
        while (true) {
            stringBuilder.append(String.format("%02x", macBytes[index++]));
            if (index >= 6) {
                break;
            }
            stringBuilder.append(':');
        }

        return stringBuilder.toString();
    }

    private static Map<String, String> getMultiValueMap(String multiKeyValueStr) {
        if (Strings.isNullOrEmpty(multiKeyValueStr)) {
            return Collections.emptyMap();
        }

        Map<String, String> valueMap = new HashMap<>();
        Splitter splitter = Splitter.on(OTHER_CONFIG_PARAMETERS_DELIMITER);
        for (String keyValue : splitter.split(multiKeyValueStr)) {
            String[] split = keyValue.split(OTHER_CONFIG_KEY_VALUE_DELIMITER, 2);
            if (split.length == 2) {
                valueMap.put(split[0], split[1]);
            }
        }

        return valueMap;
    }

    /**
     * {@inheritDoc}.
     */
    @Nullable
    private Node getBridgeNode(Uint64 dpId) {
        Map<NodeKey, Node> ovsdbNodes = southboundUtils.getOvsdbNodes();
        if (null == ovsdbNodes) {
            LOG.debug("Could not find any (?) ovsdb nodes");
            return null;
        }

        for (Node node : ovsdbNodes.values()) {
            if (!isIntegrationBridge(node)) {
                continue;
            }

            long nodeDpid = southboundUtils.getDataPathId(node);
            if (dpId.equals(Uint64.valueOf(nodeDpid))) {
                return node;
            }
        }

        return null;
    }

    @Nullable
    public String getProviderInterfaceName(Uint64 dpId, String physicalNetworkName) {
        Node brNode;

        brNode = getBridgeNode(dpId);
        if (brNode == null) {
            LOG.debug("Could not find bridge node for {}", dpId);
            return null;
        }

        return getProviderInterfaceName(brNode, physicalNetworkName);
    }

    @Nullable
    public String getProviderInterfaceName(Node bridgeNode, String physicalNetworkName) {
        if (physicalNetworkName == null) {
            return null;
        }

        String providerMappingValue = getProviderMappingValue(bridgeNode, physicalNetworkName);
        if (providerMappingValue == null) {
            LOG.trace("No provider mapping found for physicalNetworkName {} node {}", physicalNetworkName,
                    bridgeNode.getNodeId().getValue());
            return null;
        }

        long dataPathId = southboundUtils.getDataPathId(bridgeNode);
        if (dataPathId < 1) {
            LOG.info("No DatapathID for node {} with physicalNetworkName {}",
                    bridgeNode.getNodeId().getValue(), physicalNetworkName);
            return null;
        }

        String portName = getIntBridgePortNameFor(bridgeNode, providerMappingValue);
        String dpIdStr = String.valueOf(dataPathId);
        return interfaceManager.getPortNameForInterface(dpIdStr, portName);
    }

    public boolean hasDatapathID(Node node) {
        return southboundUtils.getDataPathId(node) > 0;
    }

    public boolean isBridgeOnOvsdbNode(Node ovsdbNode, String bridgename) {
        return southboundUtils.isBridgeOnOvsdbNode(ovsdbNode, bridgename);
    }

    public String getIntegrationBridgeName() {
        return INTEGRATION_BRIDGE;
    }

    public Uint64 getDatapathId(Node node) {
        return Uint64.valueOf(southboundUtils.getDataPathId(node));
    }
}
