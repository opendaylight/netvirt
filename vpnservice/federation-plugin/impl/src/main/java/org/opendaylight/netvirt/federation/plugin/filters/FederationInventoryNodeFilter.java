/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import com.google.common.base.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginCounters;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationInventoryNodeTransformer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class FederationInventoryNodeFilter implements FederationPluginFilter<Node, Nodes> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationInventoryNodeFilter.class);

    private final DataBroker dataBroker;
    private final FederationInventoryNodeTransformer transformer;

    @Inject
    public FederationInventoryNodeFilter(final DataBroker dataBroker,
            final FederationInventoryNodeTransformer transformer) {
        this.dataBroker = dataBroker;
        this.transformer = transformer;
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, this);
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.INVENTORY_NODE_OPER_KEY, this);
    }

    @Override
    public FilterResult applyEgressFilter(Node newNode, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<Node> dataTreeModification) {
        String nodeName = newNode.getKey().getId().getValue();
        if (isShadow(newNode)) {
            LOG.trace("Node {} filtered out. Reason: shadow node", nodeName);
            return FilterResult.DENY;
        }
        if (dataTreeModification != null
                && dataTreeModification.getRootNode().getModificationType() == ModificationType.SUBTREE_MODIFIED) {
            Node oldNode = dataTreeModification.getRootNode().getDataBefore();
            Nodes oldNodes = transformer.applyEgressTransformation(oldNode, federatedMappings, pendingModifications);
            Nodes newNodes = transformer.applyEgressTransformation(newNode, federatedMappings, pendingModifications);
            if (oldNodes.equals(newNodes)) {
                FederationPluginCounters.egress_node_filtered_after_transform.inc();
                return FilterResult.DENY;
            }
        }
        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, Nodes node) {
        KeyedInstanceIdentifier<Node, NodeKey> nodeId = InstanceIdentifier.create(Nodes.class).child(Node.class,
                node.getNode().get(0).getKey());
        ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction();
        try {
            LogicalDatastoreType dsType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
            Optional<Node> checkedGet = readTx.read(dsType, nodeId).checkedGet();
            if (checkedGet.isPresent()) {
                Node persistedNode = checkedGet.get();
                if (!isShadow(persistedNode)) {
                    LOG.error("trying to update my own node - SOMETHING IS WRONG. nodeId: {}", nodeId);
                    LOG.error("original node id {}", node.getNode().get(0).getId());
                    LOG.error("persistedNode id {}", persistedNode.getId());
                    return FilterResult.DENY;
                }
            }
        } catch (ReadFailedException e) {
            LOG.error("can't read node {}", nodeId);
        }
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(Node node) {
        InventoryNodeShadowProperties nodeShadowProperties = node.getAugmentation(InventoryNodeShadowProperties.class);
        return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow());
    }
}
