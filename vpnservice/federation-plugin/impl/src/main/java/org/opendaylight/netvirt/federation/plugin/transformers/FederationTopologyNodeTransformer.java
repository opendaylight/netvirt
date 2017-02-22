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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationTopologyNodeTransformer implements FederationPluginTransformer<Node, NetworkTopology> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationTopologyNodeTransformer.class);

    @Inject
    public FederationTopologyNodeTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                this);
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY, this);
    }

    @Override
    public NetworkTopology applyEgressTransformation(Node node, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        TopologyBuilder topologyBuilder = new TopologyBuilder();
        topologyBuilder.setKey(new TopologyKey(new TopologyId(new Uri("ovsdb:1"))));
        String nodeName = node.getNodeId().getValue();
        NodeBuilder nodeBuilder;
        if (nodeName.contains(FederationPluginConstants.INTEGRATION_BRIDGE_PREFIX)) {
            nodeBuilder = getNodeBuilderForEgressTransformationOnBrIntNode(node);
        } else {
            nodeBuilder = getNodeBuilderForEgressTransformationOnBrIntParentNode(node);
        }

        nodeBuilder.addAugmentation(TopologyNodeShadowProperties.class,
                new TopologyNodeShadowPropertiesBuilder().setShadow(true).build());
        topologyBuilder.setNode(Collections.singletonList(nodeBuilder.build()));
        return new NetworkTopologyBuilder().setTopology(Collections.singletonList(topologyBuilder.build())).build();
    }

    @Override
    public Pair<InstanceIdentifier<Node>, Node> applyIngressTransformation(NetworkTopology networkTopology,
            ModificationType modificationType, int generationNumber, String remoteIp) {
        List<Topology> topologyList = networkTopology.getTopology();
        if (topologyList == null || topologyList.isEmpty()) {
            LOG.error("Topology network is empty");
            return null;
        }

        Topology topology = topologyList.get(0);
        List<Node> nodeList = topology.getNode();
        if (nodeList == null || nodeList.isEmpty()) {
            LOG.error("Topology node is empty");
            return null;
        }

        Node node = nodeList.get(0);
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        nodeBuilder
                .addAugmentation(TopologyNodeShadowProperties.class,
                        new TopologyNodeShadowPropertiesBuilder(
                                nodeBuilder.getAugmentation(TopologyNodeShadowProperties.class)).setShadow(true)
                                        .setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        return Pair.of(InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(new Uri("ovsdb:1"))))
                .child(Node.class, node.getKey()), nodeBuilder.build());
    }

    private NodeBuilder getNodeBuilderForEgressTransformationOnBrIntParentNode(Node node) {
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setKey(node.getKey());
        nodeBuilder.setNodeId(node.getNodeId());
        OvsdbNodeAugmentation ovsdbNodeAugmentation = node.getAugmentation(OvsdbNodeAugmentation.class);
        if (ovsdbNodeAugmentation != null) {
            nodeBuilder.addAugmentation(OvsdbNodeAugmentation.class, new OvsdbNodeAugmentationBuilder()
                    .setOpenvswitchOtherConfigs(ovsdbNodeAugmentation.getOpenvswitchOtherConfigs()).build());
        } else {
            LOG.warn("OvsdbNodeAugmentation for node ID {} is null", node.getNodeId());
        }

        return nodeBuilder;
    }

    private NodeBuilder getNodeBuilderForEgressTransformationOnBrIntNode(Node node) {
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        List<TerminationPoint> tps = node.getTerminationPoint();
        List<TerminationPoint> newTps = new ArrayList<>();

        if (tps != null) {
            for (TerminationPoint tp : tps) {
                String portName = tp.getTpId().getValue();
                if (!FederationPluginUtils.isPortNameFiltered(portName)) {
                    newTps.add(tp);
                } else {
                    LOG.trace("Not copying because it is not vm port: " + tp.getTpId());
                }
            }
            nodeBuilder.setTerminationPoint(newTps);
        }

        return nodeBuilder;
    }
}
