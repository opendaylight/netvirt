/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.ovsdb.utils.config.ConfigProperties;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeNetdev;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeDpdk;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeProtocolBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeSecure;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeStandalone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeOtherConfigsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ControllerEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ControllerEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ProtocolEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.ProtocolEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ConnectionInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.InterfaceTypeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.ManagedNodeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides functions for creating bridges via OVSDB, specifically the br-int bridge.
 * Note and TODO: br-ex is temporary. vpnservice does not require it but for the time being it is
 * left here because devstack expects it.
 */
public class BridgeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeUtils.class);

    public static final String PROVIDER_MAPPINGS_KEY = "provider_mappings";
    private static final ImmutableBiMap<Class<? extends OvsdbFailModeBase>,String> OVSDB_FAIL_MODE_MAP
            = new ImmutableBiMap.Builder<Class<? extends OvsdbFailModeBase>,String>()
            .put(OvsdbFailModeStandalone.class,"standalone")
            .put(OvsdbFailModeSecure.class,"secure")
            .build();

    private static final String DISABLE_IN_BAND = "disable-in-band";
    private static final String INTEGRATION_BRIDGE = "br-int";
    private static final String EXTERNAL_BRIDGE = "br-ex";
    private static final String PATCH_PORT_PREFIX = "patch-";

    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southboundUtils;
    private Random random;


    /**
     * Construct a new BridgeUtils
     * @param dataBroker
     */
    public BridgeUtils(DataBroker dataBroker) {
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
     * Prepare the OVSDB node for netvirt, create br-int
     * @param ovsdbNode The OVSDB node
     * @param generateIntBridgeMac should BrigeUtil set br-int's mac address to a random address. If this vlaue is false, the dpid may change as ports are added to the bridge
     */
    public void prepareNode(Node ovsdbNode, boolean generateIntBridgeMac) {
        try {
            createIntegrationBridge(ovsdbNode, generateIntBridgeMac);
        } catch (Exception e) {
            LOG.error("Error creating Integration Bridge on {}", ovsdbNode, e);
            return;
        }

        try {
            if (isL3ForwardingEnabled()) {
                createExternalBridge(ovsdbNode);
            }
        } catch (Exception e) {
            LOG.error("Error creating External Bridge on {}", ovsdbNode, e);
            return;
        }
        // this node is an ovsdb node so it doesn't have a bridge
        // so either look up the bridges or just wait for the bridge update to come in
        // and add the flows there.
        //networkingProviderManager.getProvider(node).initializeFlowRules(node);
    }

    private boolean createIntegrationBridge(Node ovsdbNode, boolean generateIntBridgeMac) {
        LOG.debug("BridgeUtils.createIntegrationBridge, skipping if exists");
        if (!addBridge(ovsdbNode, INTEGRATION_BRIDGE,
                generateIntBridgeMac ? generateRandomMac() : null)) {
            LOG.warn("Integration Bridge Creation failed");
            return false;
        }
        return true;
    }

    private boolean createExternalBridge(Node ovsdbNode) {
        LOG.debug("BridgeUtils.createExternalBridge, skipping if exists");
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
        if ((!isBridgeOnOvsdbNode(ovsdbNode, bridgeName)) ||
                (getBridgeFromConfig(ovsdbNode, bridgeName) == null)) {
            Class<? extends DatapathTypeBase> dpType = null;
            if (isUserSpaceEnabled()) {
                dpType = DatapathTypeNetdev.class;
            }
            rv = addBridge(ovsdbNode, bridgeName, southboundUtils.getControllersFromOvsdbNode(ovsdbNode), dpType, mac);
        }
        return rv;
    }

    /**
     * Add a bridge to the OVSDB node
     * @param ovsdbNode Which OVSDB node
     * @param bridgeName Name of the bridge
     * @param controllersStr OVSDB's controller
     * @param dpType datapath type
     * @param mac mac address to set on the bridge or null
     * @return true if the write to md-sal was successful
     */
    public boolean addBridge(Node ovsdbNode, String bridgeName, List<String> controllersStr,
                             final Class<? extends DatapathTypeBase> dpType, String mac) {
        boolean result = false;

        LOG.debug("addBridge: node: {}, bridgeName: {}, controller(s): {}", ovsdbNode, bridgeName, controllersStr);
        ConnectionInfo connectionInfo = southboundUtils.getConnectionInfo(ovsdbNode);
        if (connectionInfo == null) {
            throw new InvalidParameterException("Could not find ConnectionInfo");
        }

        NodeBuilder bridgeNodeBuilder = new NodeBuilder();
        InstanceIdentifier<Node> bridgeIid = southboundUtils.createInstanceIdentifier(ovsdbNode.getKey(), bridgeName);
        NodeId bridgeNodeId = southboundUtils.createManagedNodeId(bridgeIid);
        bridgeNodeBuilder.setNodeId(bridgeNodeId);

        OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentationBuilder = new OvsdbBridgeAugmentationBuilder();
        ovsdbBridgeAugmentationBuilder.setControllerEntry(createControllerEntries(controllersStr));
        ovsdbBridgeAugmentationBuilder.setBridgeName(new OvsdbBridgeName(bridgeName));
        ovsdbBridgeAugmentationBuilder.setProtocolEntry(createMdsalProtocols());
        ovsdbBridgeAugmentationBuilder.setFailMode( OVSDB_FAIL_MODE_MAP.inverse().get("secure"));

        BridgeOtherConfigsBuilder bridgeOtherConfigsBuilder = new BridgeOtherConfigsBuilder();
        bridgeOtherConfigsBuilder.setBridgeOtherConfigKey(DISABLE_IN_BAND);
        bridgeOtherConfigsBuilder.setBridgeOtherConfigValue("true");

        List<BridgeOtherConfigs> bridgeOtherConfigsList = new ArrayList<>();
        bridgeOtherConfigsList.add(bridgeOtherConfigsBuilder.build());
        if (mac != null) {
            BridgeOtherConfigsBuilder macOtherConfigBuilder = new BridgeOtherConfigsBuilder();
            macOtherConfigBuilder.setBridgeOtherConfigKey("hwaddr");
            macOtherConfigBuilder.setBridgeOtherConfigValue(mac);
            bridgeOtherConfigsList.add(macOtherConfigBuilder.build());
        }

        ovsdbBridgeAugmentationBuilder.setBridgeOtherConfigs(bridgeOtherConfigsList);
        setManagedByForBridge(ovsdbBridgeAugmentationBuilder, ovsdbNode.getKey());
        if (dpType != null) {
            ovsdbBridgeAugmentationBuilder.setDatapathType(dpType);
        }

        if (isOvsdbNodeDpdk(ovsdbNode)) {
            ovsdbBridgeAugmentationBuilder.setDatapathType(DatapathTypeNetdev.class);
        }

        bridgeNodeBuilder.addAugmentation(OvsdbBridgeAugmentation.class, ovsdbBridgeAugmentationBuilder.build());

        Node node = bridgeNodeBuilder.build();
        result = mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, bridgeIid, node);
        LOG.debug("addBridge: result: {}", result);

        return result;
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

    public static String getPatchPortName(String interfaceName) {
        return interfaceName != null ? PATCH_PORT_PREFIX + interfaceName : null;
    }

    private List<ControllerEntry> createControllerEntries(List<String> controllersStr) {
        List<ControllerEntry> controllerEntries = new ArrayList<>();
        if (controllersStr != null) {
            for (String controllerStr : controllersStr) {
                ControllerEntryBuilder controllerEntryBuilder = new ControllerEntryBuilder();
                controllerEntryBuilder.setTarget(new Uri(controllerStr));
                controllerEntries.add(controllerEntryBuilder.build());
            }
        }
        return controllerEntries;
    }

    private List<ProtocolEntry> createMdsalProtocols() {
        List<ProtocolEntry> protocolList = new ArrayList<>();
        ImmutableBiMap<String, Class<? extends OvsdbBridgeProtocolBase>> mapper =
                southboundUtils.OVSDB_PROTOCOL_MAP.inverse();
        protocolList.add(new ProtocolEntryBuilder().
                setProtocol(mapper.get("OpenFlow13")).build());
        return protocolList;
    }

    private void setManagedByForBridge(OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentationBuilder,
                                       NodeKey ovsdbNodeKey) {
        InstanceIdentifier<Node> connectionNodePath = southboundUtils.createInstanceIdentifier(ovsdbNodeKey.getNodeId());
        ovsdbBridgeAugmentationBuilder.setManagedBy(new OvsdbNodeRef(connectionNodePath));
    }

    private boolean isOvsdbNodeDpdk(Node ovsdbNode) {
        boolean found = false;
        OvsdbNodeAugmentation ovsdbNodeAugmentation = southboundUtils.extractNodeAugmentation(ovsdbNode);
        if (ovsdbNodeAugmentation != null) {
            List<InterfaceTypeEntry> ifTypes = ovsdbNodeAugmentation.getInterfaceTypeEntry();
            if (ifTypes != null) {
                for (InterfaceTypeEntry ifType : ifTypes) {
                    if (ifType.getInterfaceType().equals(InterfaceTypeDpdk.class)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }

    private boolean isBridgeOnOvsdbNode(Node ovsdbNode, String bridgeName) {
        boolean found = false;
        //TODO: MAKE SURE extract function is right
        OvsdbNodeAugmentation ovsdbNodeAugmentation = southboundUtils.extractNodeAugmentation(ovsdbNode);
        if (ovsdbNodeAugmentation != null) {
            List<ManagedNodeEntry> managedNodes = ovsdbNodeAugmentation.getManagedNodeEntry();
            if (managedNodes != null) {
                for (ManagedNodeEntry managedNode : managedNodes) {
                    InstanceIdentifier<?> bridgeIid = managedNode.getBridgeRef().getValue();
                    if (bridgeIid.toString().contains(bridgeName)) {
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }

    private OvsdbBridgeAugmentation getBridgeFromConfig(Node node, String bridge) {
        OvsdbBridgeAugmentation ovsdbBridgeAugmentation = null;
        InstanceIdentifier<Node> bridgeIid =
                southboundUtils.createInstanceIdentifier(node.getKey(), bridge);
        Node bridgeNode = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, bridgeIid);
        if (bridgeNode != null) {
            ovsdbBridgeAugmentation = bridgeNode.getAugmentation(OvsdbBridgeAugmentation.class);
        }
        return ovsdbBridgeAugmentation;
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
