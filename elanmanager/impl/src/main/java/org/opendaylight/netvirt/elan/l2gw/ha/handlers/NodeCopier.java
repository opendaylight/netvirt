/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeCopier implements INodeCopier {

    static Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();

    public void copyGlobalNode(Optional<Node> srcGlobalNodeOptional,
                        InstanceIdentifier<Node> srcPath,
                        InstanceIdentifier<Node> dstPath,
                        LogicalDatastoreType logicalDatastoreType,
                        ReadWriteTransaction tx) throws ReadFailedException {

        HwvtepGlobalAugmentation srcGlobalAugmentation =
                srcGlobalNodeOptional.get().getAugmentation(HwvtepGlobalAugmentation.class);
        if (srcGlobalAugmentation == null) {
            if (logicalDatastoreType == CONFIGURATION) {
                tx.put(logicalDatastoreType, srcPath, new NodeBuilder().setNodeId(srcPath
                        .firstKeyOf(Node.class).getNodeId()).build());
                return;
            }
            else {
                LOG.error("Operational child node information is not present");
                return;
            }
        }
        NodeBuilder haNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPath);
        HwvtepGlobalAugmentationBuilder haBuilder = new HwvtepGlobalAugmentationBuilder();

        Optional<Node> existingDstGlobalNodeOptional = tx.read(logicalDatastoreType, dstPath).checkedGet();
        Node existingDstGlobalNode =
                existingDstGlobalNodeOptional.isPresent() ? existingDstGlobalNodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAGlobalData = HwvtepHAUtil.getGlobalAugmentationOfNode(existingDstGlobalNode);

        if (OPERATIONAL == logicalDatastoreType) {
            globalAugmentationMerger.mergeOperationalData(
                    haBuilder, existingHAGlobalData, srcGlobalAugmentation, dstPath);
            globalNodeMerger.mergeOperationalData(haNodeBuilder,
                    existingDstGlobalNode, srcGlobalNodeOptional.get(), dstPath);

            haBuilder.setManagers(HwvtepHAUtil.buildManagersForHANode(srcGlobalNodeOptional.get(),
                    existingDstGlobalNodeOptional));

        } else {
            globalAugmentationMerger.mergeConfigData(haBuilder, srcGlobalAugmentation, dstPath);
            globalNodeMerger.mergeConfigData(haNodeBuilder, srcGlobalNodeOptional.get(), dstPath);
        }

        haBuilder.setDbVersion(srcGlobalAugmentation.getDbVersion());
        haNodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, haBuilder.build());
        Node haNode = haNodeBuilder.build();
        if (OPERATIONAL == logicalDatastoreType) {
            tx.merge(logicalDatastoreType, dstPath, haNode);
        } else {
            tx.put(logicalDatastoreType, dstPath, haNode);
        }
    }

    public void copyPSNode(Optional<Node> srcPsNodeOptional,
                           InstanceIdentifier<Node> srcPsPath,
                           InstanceIdentifier<Node> dstPsPath,
                           InstanceIdentifier<Node> dstGlobalPath,
                           LogicalDatastoreType logicalDatastoreType,
                           ReadWriteTransaction tx) throws ReadFailedException {
        if (!srcPsNodeOptional.isPresent() && logicalDatastoreType == CONFIGURATION) {
            Futures.addCallback(tx.read(logicalDatastoreType, srcPsPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> {
                        try {
                            ReadWriteTransaction tx1 = new BatchedTransaction();
                            if (nodeOptional.isPresent()) {
                                copyPSNode(nodeOptional,
                                        srcPsPath, dstPsPath, dstGlobalPath, logicalDatastoreType, tx1);
                            } else {
                                tx1.put(logicalDatastoreType, dstPsPath, new NodeBuilder().setNodeId(dstPsPath
                                        .firstKeyOf(Node.class).getNodeId()).build());
                            }
                        } catch (ReadFailedException e) {
                            LOG.error("Failed to read src node {}", srcPsNodeOptional.get());
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                }
            });
            return;
        }
        NodeBuilder dstPsNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPsPath);
        PhysicalSwitchAugmentationBuilder dstPsAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();

        PhysicalSwitchAugmentation srcPsAugmenatation =
                srcPsNodeOptional.get().getAugmentation(PhysicalSwitchAugmentation.class);

        Node existingDstPsNode = HwvtepHAUtil.readNode(tx, logicalDatastoreType, dstPsPath);
        PhysicalSwitchAugmentation existingDstPsAugmentation =
                HwvtepHAUtil.getPhysicalSwitchAugmentationOfNode(existingDstPsNode);
        mergeOpManagedByAttributes(srcPsAugmenatation, dstPsAugmentationBuilder, dstGlobalPath);
        if (OPERATIONAL == logicalDatastoreType) {
            psAugmentationMerger.mergeOperationalData(dstPsAugmentationBuilder, existingDstPsAugmentation,
                    srcPsAugmenatation, dstPsPath);
            psNodeMerger.mergeOperationalData(dstPsNodeBuilder, existingDstPsNode, srcPsNodeOptional.get(), dstPsPath);
            dstPsNodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, dstPsAugmentationBuilder.build());
            Node dstPsNode = dstPsNodeBuilder.build();
            tx.merge(logicalDatastoreType, dstPsPath, dstPsNode);
        } else {
            /* Below Change done to rerduce the side of tx.put() generated here.
            1. Check if child node already exists in config-topo.
            2. If not present, then construct Child ps-node with augmentation data only and do tx.put(node).
            Followed by, then tx.put(termination-points) for each of termination-points present in parent ps-node.
            3. If present, then construct augmentation data and do tx.put(augmentation) then followed by
            tx.put(termination-points) for each of termination-points present in parent ps-node.
             */
            String dstNodeName = dstPsNodeBuilder.getNodeId().getValue();
            psAugmentationMerger.mergeConfigData(dstPsAugmentationBuilder, srcPsAugmenatation, dstPsPath);
            boolean isEntryExists = tx.exists(CONFIGURATION, dstPsPath).checkedGet();
            if (isEntryExists) {
                LOG.info("Destination PS Node: {} already exists in config-topo.", dstNodeName);
                InstanceIdentifier<PhysicalSwitchAugmentation> dstPsAugPath =
                    dstPsPath.augmentation(PhysicalSwitchAugmentation.class);
                tx.put(logicalDatastoreType, dstPsAugPath, dstPsAugmentationBuilder.build());
            } else {
                LOG.info("Destination PS Node: {} doesn't still exist in config-topo.", dstNodeName);
                dstPsNodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class,
                    dstPsAugmentationBuilder.build());
                Node dstPsNode = dstPsNodeBuilder.build();
                tx.put(logicalDatastoreType, dstPsPath, dstPsNode);
            }
            psNodeMerger.mergeConfigData(dstPsNodeBuilder, srcPsNodeOptional.get(), dstPsPath);

            if (dstPsNodeBuilder.getTerminationPoint() != null) {
                dstPsNodeBuilder.getTerminationPoint().forEach(terminationPoint -> {
                    InstanceIdentifier<TerminationPoint> terminationPointPath =
                        dstPsPath.child(TerminationPoint.class, terminationPoint.getKey());
                    tx.put(logicalDatastoreType, terminationPointPath, terminationPoint);
                    LOG.trace("Destination PS Node: {} updated with termination-point : {}",
                        dstNodeName, terminationPoint.getKey());
                });
            }
        }
        LOG.debug("Copied {} physical switch node from {} to {}", logicalDatastoreType, srcPsPath, dstPsPath);
    }

    public void mergeOpManagedByAttributes(PhysicalSwitchAugmentation psAugmentation,
                                           PhysicalSwitchAugmentationBuilder builder,
                                           InstanceIdentifier<Node> haNodePath) {
        builder.setManagedBy(new HwvtepGlobalRef(haNodePath));
        if (psAugmentation != null) {
            builder.setHwvtepNodeName(psAugmentation.getHwvtepNodeName());
            builder.setHwvtepNodeDescription(psAugmentation.getHwvtepNodeDescription());
            builder.setTunnelIps(psAugmentation.getTunnelIps());
            if (psAugmentation.getHwvtepNodeName() != null) {
                builder.setPhysicalSwitchUuid(HwvtepHAUtil.getUUid(psAugmentation.getHwvtepNodeName().getValue()));
            }
        }
    }
}
