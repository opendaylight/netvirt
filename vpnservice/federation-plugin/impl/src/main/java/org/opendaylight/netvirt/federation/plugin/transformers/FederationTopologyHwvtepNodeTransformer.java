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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginCounters;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitchesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindingsBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationTopologyHwvtepNodeTransformer implements FederationPluginTransformer<Node, NetworkTopology> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationTopologyHwvtepNodeTransformer.class);

    @Inject
    public FederationTopologyHwvtepNodeTransformer() {
        FederationPluginTransformerRegistry
                .registerTransformer(FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_CONFIG_KEY, this);
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_OPER_KEY,
                this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public NetworkTopology applyEgressTransformation(Node node, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        TopologyBuilder topologyBuilder = new TopologyBuilder();
        topologyBuilder.setKey(FederationPluginConstants.HWVTEP_TOPOLOGY_KEY);
        String nodeName = node.getNodeId().getValue();
        NodeBuilder nodeBuilder;
        FederationPluginCounters.hwvtep_egress_apply_transformation.inc();
        LOG.debug("applying egress transformation to node {}", nodeName);

        if (nodeName.contains(FederationPluginConstants.HWVTEP_PHYSICAL_SWITCH)) {
            nodeBuilder = getNodeBuilderForEgressTransformationOnPhysicalSwitchNode(node, federatedMappings);
        } else {
            nodeBuilder = getNodeBuilderForEgressTransformationOnPhysicalSwitchParentNode(node, federatedMappings);
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
        FederationPluginCounters.hwvtep_ingress_apply_transformation.inc();
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
        LOG.debug("applying ingress transformation to node {}", node);
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        nodeBuilder.addAugmentation(TopologyNodeShadowProperties.class,
                new TopologyNodeShadowPropertiesBuilder(nodeBuilder.getAugmentation(TopologyNodeShadowProperties.class))
                        .setShadow(true).setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        return Pair.of(InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, FederationPluginConstants.HWVTEP_TOPOLOGY_KEY).child(Node.class, node.getKey()),
                nodeBuilder.build());
    }

    private NodeBuilder getNodeBuilderForEgressTransformationOnPhysicalSwitchParentNode(Node node,
            FederatedMappings federatedMappings) {
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setKey(node.getKey());
        nodeBuilder.setNodeId(node.getNodeId());
        // TODO - replace logical switch id in:
        // hwvtep:logical-switches - done
        // hwvtep:remote-mcast-macs/logical-switch-ref  - done
        // hwvtep:local-mcast-macs/logical-switch-ref  - done
        // hwvtep:remote-ucast-macs  - done
        HwvtepGlobalAugmentation originalGlobalAug = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (originalGlobalAug != null) {
            List<LogicalSwitches> federatedLogicalSwitches = copyFederatedLogicalSwitches(originalGlobalAug,
                    federatedMappings);
            HwvtepGlobalAugmentationBuilder hgab = new HwvtepGlobalAugmentationBuilder();
            List<RemoteUcastMacs> federatedRemoteUcastMacs = copyRemoteUcastMacs(originalGlobalAug, federatedMappings);
            hgab.setRemoteUcastMacs(federatedRemoteUcastMacs);
            List<RemoteMcastMacs> federatedRemoteMcastMacs = copyRemoteMcastMacs(originalGlobalAug, federatedMappings);
            hgab.setRemoteMcastMacs(federatedRemoteMcastMacs);
            List<LocalUcastMacs> federatedLocalUcastMacs = copyLocalUcastMacs(originalGlobalAug, federatedMappings);
            hgab.setLocalUcastMacs(federatedLocalUcastMacs);
            List<LocalMcastMacs> federatedLocalMcastMacs = copyLocalMcastMacs(originalGlobalAug, federatedMappings);
            hgab.setLocalMcastMacs(federatedLocalMcastMacs);

            hgab.setConnectionInfo(originalGlobalAug.getConnectionInfo());
            hgab.setLogicalSwitches(federatedLogicalSwitches);
            nodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, hgab.build());
        }

        PhysicalSwitchAugmentation originalPhysicalAug = node.getAugmentation(PhysicalSwitchAugmentation.class);
        if (originalPhysicalAug != null) {
            PhysicalSwitchAugmentationBuilder psa = new PhysicalSwitchAugmentationBuilder(originalPhysicalAug);
            nodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, psa.build());
        }

        nodeBuilder.setTerminationPoint(node.getTerminationPoint());
        return nodeBuilder;
    }

    private List<LocalUcastMacs> copyLocalUcastMacs(HwvtepGlobalAugmentation originalGlobalAug,
            FederatedMappings federatedMappings) {
        List<LocalUcastMacs> ret = new ArrayList<>();
        if (originalGlobalAug.getLocalUcastMacs() != null) {
            for (LocalUcastMacs localMac : originalGlobalAug.getLocalUcastMacs()) {
                LogicalSwitchesKey lsKey = localMac.getLogicalSwitchRef().getValue().firstKeyOf(LogicalSwitches.class);
                String consumerNetworkId = federatedMappings.getConsumerNetworkId(lsKey.getHwvtepNodeName().getValue());
                if (consumerNetworkId != null) {
                    LocalUcastMacsBuilder lumb = new LocalUcastMacsBuilder(localMac);
                    InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                            federateLogicalSwitchRef(localMac.getLogicalSwitchRef(), consumerNetworkId);
                    lumb.setLogicalSwitchRef(new HwvtepLogicalSwitchRef(federatedLogicalSwitchRef));
                    ret.add(lumb.build());
                }
            }
        }
        return ret;
    }

    private List<LocalMcastMacs> copyLocalMcastMacs(HwvtepGlobalAugmentation originalGlobalAug,
            FederatedMappings federatedMappings) {
        List<LocalMcastMacs> ret = new ArrayList<>();
        if (originalGlobalAug.getLocalUcastMacs() != null) {
            for (LocalMcastMacs localMac : originalGlobalAug.getLocalMcastMacs()) {
                LogicalSwitchesKey lsKey = localMac.getLogicalSwitchRef().getValue().firstKeyOf(LogicalSwitches.class);
                String consumerNetworkId = federatedMappings.getConsumerNetworkId(lsKey.getHwvtepNodeName().getValue());
                if (consumerNetworkId != null) {
                    LocalMcastMacsBuilder lumb = new LocalMcastMacsBuilder(localMac);
                    InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                            federateLogicalSwitchRef(localMac.getLogicalSwitchRef(), consumerNetworkId);
                    lumb.setLogicalSwitchRef(new HwvtepLogicalSwitchRef(federatedLogicalSwitchRef));
                    ret.add(lumb.build());
                }
            }
        }
        return ret;
    }

    private List<RemoteUcastMacs> copyRemoteUcastMacs(HwvtepGlobalAugmentation originalGlobalAug,
            FederatedMappings federatedMappings) {
        List<RemoteUcastMacs> ret = new ArrayList<>();
        if (originalGlobalAug.getRemoteUcastMacs() != null) {
            for (RemoteUcastMacs localMac : originalGlobalAug.getRemoteUcastMacs()) {
                LogicalSwitchesKey lsKey = localMac.getLogicalSwitchRef().getValue().firstKeyOf(LogicalSwitches.class);
                String consumerNetworkId = federatedMappings.getConsumerNetworkId(lsKey.getHwvtepNodeName().getValue());
                if (consumerNetworkId != null) {
                    RemoteUcastMacsBuilder rumb = new RemoteUcastMacsBuilder(localMac);
                    InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                            federateLogicalSwitchRef(localMac.getLogicalSwitchRef(), consumerNetworkId);
                    rumb.setLogicalSwitchRef(new HwvtepLogicalSwitchRef(federatedLogicalSwitchRef));
                    ret.add(rumb.build());
                }
            }
        }
        return ret;
    }

    private List<RemoteMcastMacs> copyRemoteMcastMacs(HwvtepGlobalAugmentation originalGlobalAug,
            FederatedMappings federatedMappings) {
        List<RemoteMcastMacs> ret = new ArrayList<>();
        if (originalGlobalAug.getRemoteMcastMacs() != null) {
            for (RemoteMcastMacs remoteMac : originalGlobalAug.getRemoteMcastMacs()) {
                LogicalSwitchesKey lsKey = remoteMac.getLogicalSwitchRef().getValue().firstKeyOf(LogicalSwitches.class);
                String consumerNetworkId = federatedMappings.getConsumerNetworkId(lsKey.getHwvtepNodeName().getValue());
                if (consumerNetworkId != null) {
                    RemoteMcastMacsBuilder rumb = new RemoteMcastMacsBuilder(remoteMac);
                    InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                            federateLogicalSwitchRef(remoteMac.getLogicalSwitchRef(), consumerNetworkId);
                    rumb.setLogicalSwitchRef(new HwvtepLogicalSwitchRef(federatedLogicalSwitchRef));
                    ret.add(rumb.build());
                }
            }
        }
        return ret;
    }

    private List<LogicalSwitches> copyFederatedLogicalSwitches(HwvtepGlobalAugmentation hwvtepNodeAugmentation,
            FederatedMappings federatedMappings) {
        List<LogicalSwitches> logicalSwitches = hwvtepNodeAugmentation.getLogicalSwitches();
        List<LogicalSwitches> federatedLogicalSwitches = new ArrayList<>();
        if (logicalSwitches != null) {
            for (LogicalSwitches ls : logicalSwitches) {
                HwvtepNodeName hwvtepNodeName = ls.getKey().getHwvtepNodeName();
                String consumerNetworkId = federatedMappings.getConsumerNetworkId(hwvtepNodeName.getValue());
                if (consumerNetworkId != null) {
                    LogicalSwitchesBuilder lsb = new LogicalSwitchesBuilder(ls);
                    lsb.setKey(new LogicalSwitchesKey(new HwvtepNodeName(consumerNetworkId)));
                    federatedLogicalSwitches.add(lsb.build());
                }
            }
        }
        return federatedLogicalSwitches;
    }

    private NodeBuilder getNodeBuilderForEgressTransformationOnPhysicalSwitchNode(Node node,
            FederatedMappings federatedMappings) {
        NodeBuilder nodeBuilder = new NodeBuilder(node);
        List<TerminationPoint> tps = node.getTerminationPoint();
        List<TerminationPoint> federatedTps = new ArrayList<>();

        // TODO - need to replace logical-switch-ref to use the name of the
        // network of the remote site
        // termination-point/hwvtep:vlan-bindings/logical-switch-ref
        if (tps != null) {
            for (TerminationPoint tp : tps) {
                HwvtepPhysicalPortAugmentation portAug = tp.getAugmentation(HwvtepPhysicalPortAugmentation.class);
                if (portAug != null) {
                    List<VlanBindings> origVlanBindings = portAug.getVlanBindings();
                    List<VlanBindings> federatedVlanBindings = new ArrayList<>();
                    if (origVlanBindings != null) {
                        for (VlanBindings vb : origVlanBindings) {
                            LogicalSwitchesKey lsKey = vb.getLogicalSwitchRef().getValue()
                                    .firstKeyOf(LogicalSwitches.class);
                            String consumerNetworkId = federatedMappings
                                    .getConsumerNetworkId(lsKey.getHwvtepNodeName().getValue());
                            if (consumerNetworkId != null) {
                                VlanBindingsBuilder vbb = new VlanBindingsBuilder(vb);
                                InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                                        federateLogicalSwitchRef(vb.getLogicalSwitchRef(), consumerNetworkId);
                                vbb.setLogicalSwitchRef(new HwvtepLogicalSwitchRef(federatedLogicalSwitchRef));
                                federatedVlanBindings.add(vbb.build());
                            }
                        }
                        if (!federatedVlanBindings.isEmpty()) {
                            TerminationPointBuilder tpb = new TerminationPointBuilder(tp);
                            HwvtepPhysicalPortAugmentationBuilder hppab = new HwvtepPhysicalPortAugmentationBuilder(
                                    portAug);
                            hppab.setVlanBindings(federatedVlanBindings);
                            tpb.addAugmentation(HwvtepPhysicalPortAugmentation.class, hppab.build());
                            federatedTps.add(tpb.build());
                        }
                    }
                }
            }
            nodeBuilder.setTerminationPoint(federatedTps);
        }

        return nodeBuilder;
    }

    private InstanceIdentifier<LogicalSwitches> federateLogicalSwitchRef(HwvtepLogicalSwitchRef logicalSwitchRef,
            String consumerNetworkId) {
        NodeKey nodeKey = logicalSwitchRef.getValue().firstKeyOf(Node.class);
        InstanceIdentifier<LogicalSwitches> federatedLogicalSwitchRef =
                InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, FederationPluginConstants.HWVTEP_TOPOLOGY_KEY)
                .child(Node.class, nodeKey).augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class,
                        new LogicalSwitchesKey(new HwvtepNodeName(consumerNetworkId)));

        return federatedLogicalSwitchRef;

    }

}
