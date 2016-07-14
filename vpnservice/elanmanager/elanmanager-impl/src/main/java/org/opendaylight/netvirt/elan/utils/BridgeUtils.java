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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.ovsdb.utils.config.ConfigProperties;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathTypeNetdev;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeDpdk;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeDpdkr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeDpdkvhost;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeDpdkvhostuser;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGeneve;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGre64;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeIpsecGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeIpsecGre64;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeLisp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypePatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeSystem;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeTap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeSecure;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbFailModeStandalone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
    private static final String INTEGRATION_BRIDGE = "br-int";
    private static final String EXTERNAL_BRIDGE = "br-ex";

    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southboundUtils;
    private final OvsdbSouthboundUtils ovsdbSouthboundUtils;
    private Random random;


    /**
     * Construct a new BridgeUtils
     * @param dataBroker
     */
    public BridgeUtils(DataBroker dataBroker) {
        //TODO: ClusterAware!!!??
        this.mdsalUtils = new MdsalUtils(dataBroker);
        this.ovsdbSouthboundUtils = new OvsdbSouthboundUtils(dataBroker);
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
        if ((!ovsdbSouthboundUtils.isBridgeOnOvsdbNode(ovsdbNode, bridgeName)) ||
                (ovsdbSouthboundUtils.getBridgeFromConfig(ovsdbNode, bridgeName) == null)) {
            Class<? extends DatapathTypeBase> dpType = null;
            if (isUserSpaceEnabled()) {
                dpType = DatapathTypeNetdev.class;
            }
            rv = ovsdbSouthboundUtils.addBridge(ovsdbNode, bridgeName, southboundUtils.getControllersFromOvsdbNode(ovsdbNode), dpType, mac);
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


    public String getBridgeDatapathId(Node node) {
        OvsdbBridgeAugmentation bridgeAugmentation = node.getAugmentation(OvsdbBridgeAugmentation.class);
        if (bridgeAugmentation == null || bridgeAugmentation.getDatapathId() == null) {
            return null;
        }

        return bridgeAugmentation.getDatapathId().getValue();
    }

    /*
    public boolean addPhysicalInterface(Node ovsdbNode, String externalInterfaceName) {
        boolean res = true;
        String bridgeName = "br-" + externalInterfaceName;


        if(!addBridge(ovsdbNode, bridgeName, null)) {
            return false;
        }

        addPatchPort()

    }
    */

    public String getIntBridgeSidePatchName(String physicalInterfaceName) {
        return "patch-br-" + physicalInterfaceName;
    }

    public boolean addPortToBridge (Node node, String bridgeName, String portName) {
        boolean rv = true;

        if (ovsdbSouthboundUtils.extractTerminationPointAugmentation(node, portName) == null) {
            rv = ovsdbSouthboundUtils.addTerminationPoint(node, bridgeName, portName, null);

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

    public boolean addPatchPort (Node node, String bridgeName, String portName, String peerPortName) {
        boolean rv = true;

        if (ovsdbSouthboundUtils.extractTerminationPointAugmentation(node, portName) == null) {
            rv = ovsdbSouthboundUtils.addPatchTerminationPoint(node, bridgeName, portName, peerPortName);

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
            LOG.debug("readOvsdbNode: Provided node is not a bridge node : {}",bridgeNode);
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
