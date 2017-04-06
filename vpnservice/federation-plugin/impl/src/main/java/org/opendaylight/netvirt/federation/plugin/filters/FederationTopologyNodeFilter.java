/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationTopologyNodeFilter implements FederationPluginFilter<Node, NetworkTopology> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationTopologyNodeFilter.class);

    @Inject
    public FederationTopologyNodeFilter() {
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, this);
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY, this);
    }

    @Override
    public FilterResult applyEgressFilter(Node node, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<Node> dataTreeModification) {
        String nodeName = node.getNodeId().getValue();
        if (isShadow(node)) {
            LOG.trace("Node {} filtered out. Reason: shadow node", nodeName);
            return FilterResult.DENY;
        }

        if (nodeName.contains(ITMConstants.BRIDGE_URI_PREFIX)
                && !nodeName.contains(FederationPluginConstants.INTEGRATION_BRIDGE_PREFIX)) {
            LOG.trace("Node {} filtered out. Reason: bridge that is not integration bridge", nodeName);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, NetworkTopology topology) {
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(Node node) {
        TopologyNodeShadowProperties nodeShadowProperties = node.getAugmentation(TopologyNodeShadowProperties.class);
        return nodeShadowProperties != null && Boolean.TRUE.equals(nodeShadowProperties.isShadow());
    }
}
