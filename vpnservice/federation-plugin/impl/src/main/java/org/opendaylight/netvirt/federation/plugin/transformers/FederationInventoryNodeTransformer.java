/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.transformers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableStatisticsGatheringStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class FederationInventoryNodeTransformer implements FederationPluginTransformer<Node, Nodes> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationInventoryNodeTransformer.class);

    @Inject
    public FederationInventoryNodeTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                this);
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.INVENTORY_NODE_OPER_KEY,
                this);
    }

    @Override
    public Nodes applyEgressTransformation(Node node, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        nodeBuilder.addAugmentation(FlowCapableNode.class, null);
        nodeBuilder.addAugmentation(FlowCapableStatisticsGatheringStatus.class, null);
        List<NodeConnector> ncList = node.getNodeConnector();
        List<NodeConnector> newNcList = new ArrayList<>();
        if (ncList != null) {
            for (NodeConnector nc : ncList) {
                FlowCapableNodeConnector flowCapableAugmentation = nc.getAugmentation(FlowCapableNodeConnector.class);
                if (flowCapableAugmentation != null) {
                    String portName = flowCapableAugmentation.getName();
                    if (FederationPluginUtils.isPortNameFiltered(portName)) {
                        continue;
                    }
                }
                NodeConnectorBuilder ncBuilder = new NodeConnectorBuilder(nc);
                ncBuilder.addAugmentation(FlowCapableNodeConnectorStatisticsData.class, null);
                newNcList.add(ncBuilder.build());
            }
        }
        nodeBuilder.setNodeConnector(newNcList);
        nodeBuilder.addAugmentation(InventoryNodeShadowProperties.class,
                new InventoryNodeShadowPropertiesBuilder().setShadow(true).build());
        return new NodesBuilder().setNode(Collections.singletonList(nodeBuilder.build())).build();
    }

    @Override
    public Pair<InstanceIdentifier<Node>, Node> applyIngressTransformation(Nodes nodes,
            ModificationType modificationType, int generationNumber, String remoteIp) {
        List<Node> nodeList = nodes.getNode();
        if (nodeList == null || nodeList.isEmpty()) {
            LOG.error("Inventory nodes is empty");
            return null;
        }

        Node node = nodeList.get(0);
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        nodeBuilder.addAugmentation(InventoryNodeShadowProperties.class,
                new InventoryNodeShadowPropertiesBuilder(
                        nodeBuilder.getAugmentation(InventoryNodeShadowProperties.class)).setShadow(true)
                                .setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        return Pair.of(InstanceIdentifier.create(Nodes.class).child(Node.class, node.getKey()), nodeBuilder.build());
    }

}
