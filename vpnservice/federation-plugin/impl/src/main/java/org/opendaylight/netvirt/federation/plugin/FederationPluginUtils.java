/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.federation.service.api.message.BindingAwareJsonConverter;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.netvirt.federation.plugin.creators.FederationPluginCreatorRegistry;
import org.opendaylight.netvirt.federation.plugin.creators.FederationPluginModificationCreator;
import org.opendaylight.netvirt.federation.plugin.filters.FederationPluginFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationPluginFilterRegistry;
import org.opendaylight.netvirt.federation.plugin.filters.FilterResult;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationPluginIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationPluginIdentifierRegistry;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationPluginTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationPluginTransformerRegistry;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableStatisticsGatheringStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableStatisticsGatheringStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfStackedVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfStackedVlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.statistics.rev131111.NodeGroupFeatures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.statistics.rev131111.NodeGroupFeaturesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.meter.statistics.rev131111.NodeMeterFeatures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.meter.statistics.rev131111.NodeMeterFeaturesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTag;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanTagNameMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanTagNameMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.FederationGenerations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwConnectionShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OpState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OpStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsDataBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederationPluginUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FederationPluginUtils.class);

    private static final List<String> SORTED_LISTENER_KEYS =

            ImmutableList.of(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, //
                    FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY, //
                    FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_CONFIG_KEY, //
                    FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_OPER_KEY, //
                    FederationPluginConstants.L2_GATEWAY_KEY, //
                    FederationPluginConstants.L2_GATEWAY_CONNECTION_KEY, //
                    FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, //
                    FederationPluginConstants.INVENTORY_NODE_OPER_KEY, //
                    FederationPluginConstants.IETF_INTERFACE_KEY, //
                    FederationPluginConstants.ELAN_INTERFACE_KEY, //
                    FederationPluginConstants.VPN_INTERFACE_KEY);


    private static volatile boolean yangModulesInitialized = false;

    private FederationPluginUtils() {

    }

    public static String uuidToCleanStr(Uuid uuid) {
        String uuidStr = uuid.toString();
        String str = "";
        if (uuidStr.indexOf('=') != -1 && uuidStr.indexOf(']') != -1) {
            str = uuidStr.substring(uuidStr.indexOf('=') + 1, uuidStr.indexOf(']'));
        }
        return str;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static synchronized void initYangModules() {
        if (yangModulesInitialized) {
            return;
        }

        ArrayList<YangModuleInfo> moduleInfos = new ArrayList<>();
        try {
            moduleInfos.add(BindingReflections.getModuleInfo(Topology.class));
            moduleInfos.add(BindingReflections.getModuleInfo(OvsdbNodeAugmentation.class));
            moduleInfos.add(BindingReflections.getModuleInfo(Nodes.class));
            moduleInfos.add(BindingReflections.getModuleInfo(FlowCapableNodeConnector.class));
            moduleInfos.add(BindingReflections.getModuleInfo(FlowCapableNodeConnectorStatisticsData.class));
            moduleInfos.add(BindingReflections.getModuleInfo(NodeMeterFeatures.class));
            moduleInfos.add(BindingReflections.getModuleInfo(NodeGroupFeatures.class));
            moduleInfos.add(BindingReflections.getModuleInfo(Interfaces.class));
            moduleInfos.add(BindingReflections.getModuleInfo(IfExternal.class));
            moduleInfos.add(BindingReflections.getModuleInfo(InterfaceAcl.class));
            moduleInfos.add(BindingReflections.getModuleInfo(ParentRefs.class));
            moduleInfos.add(BindingReflections.getModuleInfo(IfL2vlan.class));
            moduleInfos.add(BindingReflections.getModuleInfo(IfStackedVlan.class));
            moduleInfos.add(BindingReflections.getModuleInfo(ElanInterfaces.class));
            moduleInfos.add(BindingReflections.getModuleInfo(EtreeInterface.class));
            moduleInfos.add(BindingReflections.getModuleInfo(EtreeInstance.class));
            moduleInfos.add(BindingReflections.getModuleInfo(EtreeLeafTagName.class));
            moduleInfos.add(BindingReflections.getModuleInfo(VpnInterfaces.class));
            moduleInfos.add(BindingReflections.getModuleInfo(Adjacencies.class));
            moduleInfos.add(BindingReflections.getModuleInfo(SubnetRoute.class));
            moduleInfos.add(BindingReflections.getModuleInfo(OpState.class));
            moduleInfos.add(BindingReflections.getModuleInfo(TopologyNodeShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(ElanShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(IfShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(InventoryNodeShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(VpnShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(Neutron.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gateways.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gateway.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gatewayConnections.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gatewayConnection.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gwConnectionShadowProperties.class));
            moduleInfos.add(BindingReflections.getModuleInfo(L2gwShadowProperties.class));

            BindingAwareJsonConverter.init(moduleInfos);
            bug7420Workaround(5);
            yangModulesInitialized = true;
            LOG.info("Finished initializing BindingReflections modules");
        } catch (Exception e) {
            LOG.error("Failed to initialized MessageSeralizationUtils", e);
        }
    }

    public static List<String> getOrderedListenerKeys() {
        return SORTED_LISTENER_KEYS;
    }

    public static LogicalDatastoreType getListenerDatastoreType(String listenerKey) {
        return FederationPluginIdentifierRegistry.getDatastoreType(listenerKey);
    }

    public static InstanceIdentifier<? extends DataObject> getInstanceIdentifier(String listenerKey) {
        FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject>
            identifierHandler = FederationPluginIdentifierRegistry.getIdentifier(listenerKey);
        if (identifierHandler == null) {
            LOG.error("Failed to get identifier for {}", listenerKey);
            return null;
        }

        return identifierHandler.getInstanceIdentifier();
    }

    public static InstanceIdentifier<? extends DataObject> getParentInstanceIdentifier(String listenerKey) {
        FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject>
            identifierHandler = FederationPluginIdentifierRegistry.getIdentifier(listenerKey);
        if (identifierHandler == null) {
            LOG.error("Failed to get identifier for {}", listenerKey);
            return null;
        }

        return identifierHandler.getParentInstanceIdentifier();
    }

    public static InstanceIdentifier<? extends DataObject> getSubtreeInstanceIdentifier(String listenerKey) {
        FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject>
            identifierHandler = FederationPluginIdentifierRegistry.getIdentifier(listenerKey);
        if (identifierHandler == null) {
            LOG.error("Failed to get identifier for {}", listenerKey);
            return null;
        }

        return identifierHandler.getSubtreeInstanceIdentifier();
    }

    public static <T extends DataObject> String getClassListener(Class<T> clazz, LogicalDatastoreType datastoreType) {
        return FederationPluginIdentifierRegistry.getListenerKey(datastoreType, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, S extends DataObject> FilterResult applyEgressFilter(String listenerKey,
            T dataObject, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<T> dataTreeModification) {
        FederationPluginFilter<T, S> filter = (FederationPluginFilter<T, S>) FederationPluginFilterRegistry
                .getFilter(listenerKey);
        if (filter == null) {
            LOG.error("Filter not found for key {}", listenerKey);
            return FilterResult.DENY;
        }

        return filter.applyEgressFilter(dataObject, federatedMappings, pendingModifications, dataTreeModification);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, R extends DataObject> FilterResult applyIngressFilter(String listenerKey,
            R dataObject) {
        FederationPluginFilter<T, R> filter = (FederationPluginFilter<T, R>) FederationPluginFilterRegistry
                .getFilter(listenerKey);
        if (filter == null) {
            LOG.error("Filter not found for key {}", listenerKey);
            return FilterResult.DENY;
        }

        return filter.applyIngressFilter(listenerKey, dataObject);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, S extends DataObject> Pair<InstanceIdentifier<T>, T>
            applyIngressTransformation(String listenerKey, S dataObject, ModificationType modificationType,
                    int generationNumber, String remoteIp) {
        FederationPluginTransformer<T, S> transformer =
                (FederationPluginTransformer<T, S>) FederationPluginTransformerRegistry.getTransformer(listenerKey);
        if (transformer == null) {
            LOG.error("Transformer not found for key {}", listenerKey);
            return null;
        }

        return transformer.applyIngressTransformation(dataObject, modificationType, generationNumber, remoteIp);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, S extends DataObject> S applyEgressTransformation(String listenerKey,
            T dataObject, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        FederationPluginTransformer<T, S> transformer =
                (FederationPluginTransformer<T, S>) FederationPluginTransformerRegistry.getTransformer(listenerKey);
        if (transformer == null) {
            LOG.error("Transformer not found for key {} ", listenerKey);
            return null;
        }

        return transformer.applyEgressTransformation(dataObject, federatedMappings, pendingModifications);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, S extends DataObject> Collection<DataTreeModification<T>> createModifications(
            String listenerKey, S parentDataObject) {
        FederationPluginModificationCreator<T, S> creator =
                (FederationPluginModificationCreator<T, S>) FederationPluginCreatorRegistry.getCreator(listenerKey);
        if (creator == null) {
            LOG.error("Modification creator not found for key {} ", listenerKey);
            return null;
        }

        return creator.createDataTreeModifications(parentDataObject);
    }

    public static <T extends DataObject> T getDataObjectFromModification(DataTreeModification<T> dataTreeModification) {
        DataObjectModification<T> dataObjectModification = dataTreeModification.getRootNode();
        switch (dataObjectModification.getModificationType()) {
            case WRITE:
            case SUBTREE_MODIFIED:
                return dataObjectModification.getDataAfter();
            case DELETE:
                return dataObjectModification.getDataBefore();
            default:
                break;
        }

        return null;
    }

    /**
     * Discover if interface is a DHCP port. Should be replaced with type
     * definition in ietf-model
     *
     * @param dataBroker
     *            - the databroker.
     * @param interfaceName
     *            - the interface name .
     * @return if true
     */
    public static boolean isDhcpInterface(DataBroker dataBroker, String interfaceName) {
        Uuid portId;

        try {
            portId = new Uuid(interfaceName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
                new PortKey(portId));
        Port port;
        try {
            port = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.CONFIGURATION, inst);
            return port != null && NeutronConstants.DEVICE_OWNER_DHCP.equals(port.getDeviceOwner());
        } catch (ReadFailedException e) {
            LOG.debug("Interface {} is not associated with any neutron port", interfaceName);
            return false;
        }
    }

    public static RemoteSiteGenerationInfo getGenerationInfoForRemoteSite(DataBroker broker, String remoteIp) {
        InstanceIdentifier<RemoteSiteGenerationInfo> remoteSiteGenerationNumber = InstanceIdentifier
                .create(FederationGenerations.class)
                .child(RemoteSiteGenerationInfo.class, new RemoteSiteGenerationInfoKey(remoteIp));
        try {
            return SingleTransactionDataBroker.syncRead(broker,
                    LogicalDatastoreType.CONFIGURATION, remoteSiteGenerationNumber);
        } catch (ReadFailedException e) {
            LOG.debug("No generation info found for remote site {}", remoteIp);
            return null;
        }
    }

    public static String getSubnetIdFromVpnInterface(VpnInterface vpnInterface) {
        Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
        if (adjacencies == null) {
            return null;
        }

        for (Adjacency adjacency : adjacencies.getAdjacency()) {
            if (adjacency.getAdjacencyType() != null
                    && adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                Uuid subnetId = adjacency.getSubnetId();
                return subnetId != null ? subnetId.getValue() : null;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataObject, S extends DataObject> S getAssociatedDataObjectFromPending(String listenerKey,
            T dataObject, PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        S associatedObject = null;
        Map<String, Collection<DataTreeModification<?>>> modifications = pendingModifications.get(dataObject);

        if (modifications != null) {
            Collection<DataTreeModification<?>> listenerModifications = modifications.get(listenerKey);
            if (listenerModifications != null && !listenerModifications.isEmpty()) {
                DataTreeModification<S> modification = (DataTreeModification<S>) listenerModifications.iterator()
                        .next();
                associatedObject = modification.getRootNode().getDataAfter();
                if (associatedObject == null) {
                    associatedObject = modification.getRootNode().getDataBefore();
                }
            }
        }

        return associatedObject;
    }

    public static boolean isPortNameFiltered(String portName) {
        return portName.startsWith(FederationPluginConstants.TUNNEL_PREFIX)
                || portName.startsWith(ITMConstants.DEFAULT_BRIDGE_NAME);
    }

    public static boolean updateGenerationInfo(DataBroker broker, String remoteIp, int generationNumber) {
        if (remoteIp == null) {
            LOG.error("Cannot write generation number - remote IP is null");
            return false;
        }

        LOG.info("Writing generation number {} for remote site {}", remoteIp, generationNumber);
        WriteTransaction putTx = broker.newWriteOnlyTransaction();
        RemoteSiteGenerationInfoBuilder builder = new RemoteSiteGenerationInfoBuilder();
        builder.setRemoteIp(remoteIp);
        builder.setGenerationNumber(generationNumber);
        InstanceIdentifier<RemoteSiteGenerationInfo> path = InstanceIdentifier.create(FederationGenerations.class)
                .child(RemoteSiteGenerationInfo.class, new RemoteSiteGenerationInfoKey(remoteIp));
        putTx.put(LogicalDatastoreType.CONFIGURATION, path, builder.build());
        CheckedFuture<Void, TransactionCommitFailedException> future1 = putTx.submit();

        try {
            future1.checkedGet();
        } catch (org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException e) {
            return false;
        }

        return true;
    }

    public static void deleteGenerationInfo(DataBroker broker, String remoteIp) {
        if (remoteIp == null) {
            LOG.error("Cannot remove generation number - remote IP is null");
            return;
        }

        LOG.info("Deleting generation number for remote site {}", remoteIp);
        WriteTransaction deleteTx = broker.newWriteOnlyTransaction();
        InstanceIdentifier<RemoteSiteGenerationInfo> path = InstanceIdentifier.create(FederationGenerations.class)
                .child(RemoteSiteGenerationInfo.class, new RemoteSiteGenerationInfoKey(remoteIp));
        deleteTx.delete(LogicalDatastoreType.CONFIGURATION, path);
        deleteTx.submit();
    }

    // https://bugs.opendaylight.org/show_bug.cgi?id=7420
    // SubnetRoute.class; allegedly not federated
    @SuppressWarnings({ "checkstyle:emptyblock", "deprecation" })
    private static void bug7420Workaround(int moreRetries) {

        if (moreRetries == 0) {
            return;
        }
        try {

            TopologyBuilder topologyBuilder = new TopologyBuilder();
            topologyBuilder.setKey(FederationPluginConstants.OVSDB_TOPOLOGY_KEY);
            String nodeName = "aaa";
            NodeBuilder nodeBuilder = new NodeBuilder();
            nodeBuilder.setNodeId(new NodeId(nodeName));
            nodeBuilder.addAugmentation(TopologyNodeShadowProperties.class,
                    new TopologyNodeShadowPropertiesBuilder().setShadow(true).build());
            nodeBuilder.addAugmentation(OvsdbNodeAugmentation.class, new OvsdbNodeAugmentationBuilder().build());
            topologyBuilder.setNode(Collections.singletonList(nodeBuilder.build()));
            NetworkTopology networkTopology = new NetworkTopologyBuilder()
                    .setTopology(Collections.singletonList(topologyBuilder.build())).build();
            InstanceIdentifier<NetworkTopology> iid = InstanceIdentifier.create(NetworkTopology.class);
            BindingAwareJsonConverter.jsonStringFromDataObject(iid, networkTopology);

            ElanInterfaceBuilder eeib = new ElanInterfaceBuilder();
            eeib.setElanInstanceName("aaa");
            eeib.setKey(new ElanInterfaceKey("vvv"));
            eeib.addAugmentation(ElanShadowProperties.class, new ElanShadowPropertiesBuilder().setShadow(true).build());
            ElanInterfacesBuilder eib = new ElanInterfacesBuilder();
            eib.setElanInterface(Collections.singletonList(eeib.build()));
            InstanceIdentifier<ElanInterfaces> iid2 = InstanceIdentifier.create(ElanInterfaces.class);
            BindingAwareJsonConverter.jsonStringFromDataObject(iid2, eib.build());

            InterfaceBuilder interfaceBuilder = new InterfaceBuilder().setKey(new InterfaceKey("sad"));
            interfaceBuilder.addAugmentation(IfShadowProperties.class,
                    new IfShadowPropertiesBuilder().setShadow(true).build());
            interfaceBuilder.addAugmentation(IfL2vlan.class, new IfL2vlanBuilder().build());
            interfaceBuilder.addAugmentation(IfExternal.class, new IfExternalBuilder().build());
            interfaceBuilder.addAugmentation(InterfaceAcl.class, new InterfaceAclBuilder().build());
            interfaceBuilder.addAugmentation(IfStackedVlan.class, new IfStackedVlanBuilder().build());
            interfaceBuilder.addAugmentation(ParentRefs.class, new ParentRefsBuilder().build());

            Interfaces interfaces = new InterfacesBuilder()
                    .setInterface(Collections.singletonList(interfaceBuilder.build())).build();
            InstanceIdentifier<Interfaces> iid4 = InstanceIdentifier.create(Interfaces.class);
            BindingAwareJsonConverter.jsonStringFromDataObject(iid4, interfaces);

            org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder inventoryNodeBuilder
                    = new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder().setKey(
                            new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(
                                    "asd")));
            inventoryNodeBuilder.addAugmentation(FlowCapableNode.class, new FlowCapableNodeBuilder().build());
            inventoryNodeBuilder.addAugmentation(NodeMeterFeatures.class, new NodeMeterFeaturesBuilder().build());
            inventoryNodeBuilder.addAugmentation(FlowCapableStatisticsGatheringStatus.class,
                    new FlowCapableStatisticsGatheringStatusBuilder().build());
            inventoryNodeBuilder.addAugmentation(NodeGroupFeatures.class, new NodeGroupFeaturesBuilder().build());

            List<NodeConnector> newNcList = new ArrayList<>();
            NodeConnectorBuilder ncBuilder = new NodeConnectorBuilder()
                    .setKey(new NodeConnectorKey(new NodeConnectorId("asd")));
            ncBuilder.addAugmentation(FlowCapableNodeConnectorStatisticsData.class,
                    new FlowCapableNodeConnectorStatisticsDataBuilder().build());
            ncBuilder.addAugmentation(FlowCapableNodeConnector.class, new FlowCapableNodeConnectorBuilder().build());
            newNcList.add(ncBuilder.build());
            inventoryNodeBuilder.setNodeConnector(newNcList);
            inventoryNodeBuilder.addAugmentation(InventoryNodeShadowProperties.class,
                    new InventoryNodeShadowPropertiesBuilder().setShadow(true).build());
            Nodes nodes = new NodesBuilder().setNode(Collections.singletonList(inventoryNodeBuilder.build())).build();
            InstanceIdentifier<Nodes> iid5 = InstanceIdentifier.create(Nodes.class);
            BindingAwareJsonConverter.jsonStringFromDataObject(iid5, nodes);

            List<Adjacency> federatedAdjacencies = Collections
                    .singletonList(new AdjacencyBuilder().setKey(new AdjacencyKey("asd")).build());
            VpnInterfaceBuilder vpnInterfaceBuilder = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey("asd"));
            vpnInterfaceBuilder.addAugmentation(Adjacencies.class,
                    new AdjacenciesBuilder().setAdjacency(federatedAdjacencies).build());
            vpnInterfaceBuilder.addAugmentation(VpnShadowProperties.class,
                    new VpnShadowPropertiesBuilder().setShadow(true).build());
            vpnInterfaceBuilder.addAugmentation(OpState.class, new OpStateBuilder().build());
            VpnInterfaces vpnInterfaces = new VpnInterfacesBuilder()
                    .setVpnInterface(Collections.singletonList(vpnInterfaceBuilder.build())).build();
            InstanceIdentifier<VpnInterfaces> iid6 = InstanceIdentifier.create(VpnInterfaces.class);
            BindingAwareJsonConverter.jsonStringFromDataObject(iid6, vpnInterfaces);

            InstanceIdentifier<ElanInstances> iid7 = InstanceIdentifier.create(ElanInstances.class);
            ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().addAugmentation(EtreeInstance.class,
                    new EtreeInstanceBuilder().build());
            ElanInstancesBuilder instancesBuilder = new ElanInstancesBuilder().setElanInstance(
                    Collections.singletonList(elanInstanceBuilder.setKey(new ElanInstanceKey("asd")).build()));
            BindingAwareJsonConverter.jsonStringFromDataObject(iid7, instancesBuilder.build());

            InstanceIdentifier<ElanTagNameMap> iid8 = InstanceIdentifier.create(ElanTagNameMap.class);
            ElanTagName tagName = new ElanTagNameBuilder().setKey(new ElanTagNameKey(123L))
                    .addAugmentation(EtreeLeafTagName.class,
                            new EtreeLeafTagNameBuilder().setEtreeLeafTag(new EtreeLeafTag(23L)).build())
                    .build();
            ElanTagNameMapBuilder mapBuilder = new ElanTagNameMapBuilder()
                    .setElanTagName(Collections.singletonList(tagName));
            BindingAwareJsonConverter.jsonStringFromDataObject(iid8, mapBuilder.build());
        } catch (UncheckedExecutionException t) {
            LOG.info("Frozen issue occured in workaround - this is acceptable, retrying it", t);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            bug7420Workaround(--moreRetries);
        }
    }

}
