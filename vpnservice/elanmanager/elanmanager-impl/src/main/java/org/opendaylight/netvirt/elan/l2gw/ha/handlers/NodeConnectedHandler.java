/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAJobScheduler;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalNodeMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSNodeMerger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeConnectedHandler {

    static Logger LOG = LoggerFactory.getLogger(NodeConnectedHandler.class);

    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger     = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger         = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger             = PSNodeMerger.getInstance();
    DataBroker db;
    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    public NodeConnectedHandler(DataBroker db) {
        this.db = db;
    }

    public void handleNodeConnected(Node childNode,
                                    InstanceIdentifier<Node> childNodePath,
                                    InstanceIdentifier<Node> haNodePath,
                                    Optional<Node> haGlobalCfg,
                                    Optional<Node> haPSCfg,
                                    ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {

        //build ha config node
        HwvtepHAUtil.buildGlobalConfigForHANode(tx, childNode, haNodePath, haGlobalCfg);

        //cop child op to ha node
        copyChildOpToHA(childNode, haNodePath, tx);
        readAndCopyChildPSOpToHAPS(childNode, haNodePath, tx);

        if (haGlobalCfg.isPresent()) {
            //copy ha config to newly connected child case of reconnected child
            if (haPSCfg.isPresent()) {
                /*
                 copy task of physical switch node is done in the next transaction
                 The reason being if it is done in the same transaction,
                 hwvtep plugin is not able to proess this update and send vlanbindings to device
                 as it is expecting the logical switch to be already present in operational ds
                 (created in the device )
                 */
                HAJobScheduler.getInstance().submitJob(new Callable<Void>() {
                    @Override
                    public Void call() throws InterruptedException, ExecutionException, ReadFailedException,
                            TransactionCommitFailedException {
                        hwvtepHACache.updateConnectedNodeStatus(childNodePath);
                        LOG.info("HA child reconnected handleNodeReConnected {}",
                                childNode.getNodeId().getValue());
                        ReadWriteTransaction tx = db.newReadWriteTransaction();
                        copyHAPSConfigToChildPS(haPSCfg.get(), childNodePath, tx);
                        tx.submit().checkedGet();
                        return null;
                    }
                });

            }
            copyHANodeConfigToChild(haGlobalCfg.get(), childNodePath, tx);
        }

        //delete child config if ha config is missing
        deleteChildPSConfigIfHAPSConfigIsMissing(haGlobalCfg, childNode, tx);
        //deleteChildConfigIfHAConfigIsMissing(haGlobalCfg, childNodePath, tx);
    }

    private void deleteChildConfigIfHAConfigIsMissing(Optional<Node> haGlobalCfg,
                                                      InstanceIdentifier<Node> childNodePath,
                                                      ReadWriteTransaction tx) throws ReadFailedException {
        if (!haGlobalCfg.isPresent()) {
            LOG.info("HA global node not present cleaning up child");
            HwvtepHAUtil.deleteNodeIfPresent(tx, CONFIGURATION, childNodePath);
        }
    }

    private void deleteChildPSConfigIfHAPSConfigIsMissing(Optional<Node> haPSCfg,
                                                          Node childNode,
                                                          ReadWriteTransaction tx) throws ReadFailedException {
        if (haPSCfg.isPresent()) {
            return;
        }
        LOG.info("HA ps node not present cleanup child ");
        HwvtepGlobalAugmentation augmentation = childNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null) {
            List<Switches> switches = augmentation.getSwitches();
            if (switches != null) {
                for (Switches ps : switches) {
                    HwvtepHAUtil.deleteNodeIfPresent(tx, CONFIGURATION, ps.getSwitchRef().getValue());
                }
            }
        } else {
            LOG.info("Global augumentation not present for connected node ");
        }
    }

    void readAndCopyChildPSOpToHAPS(Node childGlobalNode,
                                    InstanceIdentifier<Node> haNodePath,
                                    ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {

        if (childGlobalNode == null || childGlobalNode.getAugmentation(HwvtepGlobalAugmentation.class) == null) {
            return;
        }
        List<Switches> switches = childGlobalNode.getAugmentation(HwvtepGlobalAugmentation.class).getSwitches();
        if (switches == null) {
            return;
        }
        for (Switches ps : switches) {
            Node childPsNode = HwvtepHAUtil.readNode(tx, OPERATIONAL,
                    (InstanceIdentifier<Node>)ps.getSwitchRef().getValue());
            if (childPsNode != null) {
                InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haNodePath);
                copyChildPSOpToHAPS(childPsNode, haNodePath, haPsPath, tx);
            }
        }
    }

    private void copyHANodeConfigToChild(Node srcNode,
                                         InstanceIdentifier<Node> childPath,
                                         ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (srcNode == null) {
            return;
        }
        HwvtepGlobalAugmentation src = srcNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (src == null) {
            return;
        }
        NodeBuilder nodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(childPath);
        HwvtepGlobalAugmentationBuilder dstBuilder = new HwvtepGlobalAugmentationBuilder();

        NodeId nodeId = srcNode.getNodeId();
        Set<NodeId> nodeIds = Sets.newHashSet();
        nodeIds.add(nodeId);

        globalAugmentationMerger.mergeConfigData(dstBuilder, src, childPath);
        globalNodeMerger.mergeConfigData(nodeBuilder, srcNode, childPath);
        nodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, dstBuilder.build());
        Node dstNode = nodeBuilder.build();
        tx.put(CONFIGURATION, childPath, dstNode, true);
    }

    private void copyChildOpToHA(Node childNode,
                                 InstanceIdentifier<Node> haNodePath,
                                 ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (childNode == null) {
            return;
        }
        HwvtepGlobalAugmentation childData = childNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (childData == null) {
            return;
        }
        NodeBuilder haNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(haNodePath);
        HwvtepGlobalAugmentationBuilder haBuilder = new HwvtepGlobalAugmentationBuilder();

        Optional<Node> existingHANodeOptional = tx.read(OPERATIONAL, haNodePath).checkedGet();
        Node existingHANode = existingHANodeOptional.isPresent() ? existingHANodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAData = HwvtepHAUtil.getGlobalAugmentationOfNode(existingHANode);

        globalAugmentationMerger.mergeOperationalData(haBuilder, existingHAData, childData, haNodePath);
        globalNodeMerger.mergeOperationalData(haNodeBuilder, existingHANode, childNode, haNodePath);

        haBuilder.setManagers(HwvtepHAUtil.buildManagersForHANode(childNode, existingHANodeOptional));
        haBuilder.setSwitches(HwvtepHAUtil.buildSwitchesForHANode(childNode, haNodePath, existingHANodeOptional));
        haNodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, haBuilder.build());
        Node haNode = haNodeBuilder.build();
        tx.merge(OPERATIONAL, haNodePath, haNode, true);
    }

    public void mergeOpManagedByAttributes(PhysicalSwitchAugmentation psAugmentation,
                                           PhysicalSwitchAugmentationBuilder builder,
                                           InstanceIdentifier<Node> haNodePath) {
        builder.setManagedBy(new HwvtepGlobalRef(haNodePath));
        builder.setHwvtepNodeName(psAugmentation.getHwvtepNodeName());
        builder.setHwvtepNodeDescription(psAugmentation.getHwvtepNodeDescription());
        builder.setTunnelIps(psAugmentation.getTunnelIps());
        builder.setPhysicalSwitchUuid(HwvtepHAUtil.getUUid(psAugmentation.getHwvtepNodeName().getValue()));
    }

    public void copyHAPSConfigToChildPS(Node haPsNode,
                                        InstanceIdentifier<Node> childPath,
                                        ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        InstanceIdentifier<Node> childPsPath = HwvtepHAUtil.convertPsPath(haPsNode, childPath);

        NodeBuilder childPsBuilder = HwvtepHAUtil.getNodeBuilderForPath(childPsPath);
        PhysicalSwitchAugmentationBuilder dstBuilder = new PhysicalSwitchAugmentationBuilder();
        PhysicalSwitchAugmentation src = haPsNode.getAugmentation(PhysicalSwitchAugmentation.class);

        psAugmentationMerger.mergeConfigData(dstBuilder, src, childPath);
        psNodeMerger.mergeConfigData(childPsBuilder, haPsNode, childPath);

        childPsBuilder.addAugmentation(PhysicalSwitchAugmentation.class, dstBuilder.build());
        Node childPSNode = childPsBuilder.build();
        tx.put(CONFIGURATION, childPsPath, childPSNode, true);
    }


    public void copyChildPSOpToHAPS(Node childPsNode,
                                    InstanceIdentifier<Node> haPath,
                                    InstanceIdentifier<Node> haPspath,
                                    ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {

        NodeBuilder haPSNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(haPspath);
        PhysicalSwitchAugmentationBuilder dstBuilder = new PhysicalSwitchAugmentationBuilder();

        PhysicalSwitchAugmentation src = childPsNode.getAugmentation(PhysicalSwitchAugmentation.class);

        Node existingHAPSNode = HwvtepHAUtil.readNode(tx, OPERATIONAL, haPspath);
        PhysicalSwitchAugmentation existingHAPSAugumentation =
                HwvtepHAUtil.getPhysicalSwitchAugmentationOfNode(existingHAPSNode);

        psAugmentationMerger.mergeOperationalData(dstBuilder, existingHAPSAugumentation, src, haPath);
        psNodeMerger.mergeOperationalData(haPSNodeBuilder,existingHAPSNode, childPsNode, haPath);
        mergeOpManagedByAttributes(src, dstBuilder, haPath);

        haPSNodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, dstBuilder.build());
        Node haPsNode = haPSNodeBuilder.build();
        tx.merge(OPERATIONAL, haPspath, haPsNode, true);
    }

}
