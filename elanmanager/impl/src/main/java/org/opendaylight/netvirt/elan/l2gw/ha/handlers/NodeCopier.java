/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;


import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.ReadFailedException;
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
public class NodeCopier<D extends Datastore> implements INodeCopier<D> {

    static Logger LOG = LoggerFactory.getLogger(NodeCopier.class);

    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();

    @Override
    public <D extends Datastore> void copyGlobalNode(Optional<Node> srcGlobalNodeOptional,
                        InstanceIdentifier<Node> srcPath,
                        InstanceIdentifier<Node> dstPath,
                        Class<D> logicalDatastoreType,
                        TypedReadWriteTransaction<D> tx) {

        HwvtepGlobalAugmentation srcGlobalAugmentation =
                srcGlobalNodeOptional.get().augmentation(HwvtepGlobalAugmentation.class);
        if (srcGlobalAugmentation == null) {
            if (Configuration.class.equals(logicalDatastoreType)) {
                tx.put(srcPath, new NodeBuilder().setNodeId(srcPath
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
        Optional<Node> existingDstGlobalNodeOptional = Optional.empty();
        try {
            existingDstGlobalNodeOptional = tx.read(dstPath).get();

        } catch (ExecutionException | InterruptedException e) {

        }
        Node existingDstGlobalNode = existingDstGlobalNodeOptional.isPresent()
            ? existingDstGlobalNodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAGlobalData = HwvtepHAUtil
            .getGlobalAugmentationOfNode(existingDstGlobalNode);
        if (Operational.class.equals(logicalDatastoreType)) {
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
        haNodeBuilder.addAugmentation(haBuilder.build());
        Node haNode = haNodeBuilder.build();
        if (Operational.class.equals(logicalDatastoreType)) {
            tx.mergeParentStructureMerge(dstPath, haNode);
        } else {
            tx.mergeParentStructurePut(dstPath, haNode);
        }
    }


    public <D extends Datastore> void copyPSNode(Optional<Node> srcPsNodeOptional,
                           InstanceIdentifier<Node> srcPsPath,
                           InstanceIdentifier<Node> dstPsPath,
                           InstanceIdentifier<Node> dstGlobalPath,
                           Class<D> logicalDatastoreType,
                           TypedReadWriteTransaction<D> tx) {
        if (!srcPsNodeOptional.isPresent() && Configuration.class.equals(logicalDatastoreType)) {
            try {
                Futures.addCallback(tx.read(srcPsPath), new FutureCallback<Optional<Node>>() {
                    @Override
                    public void onSuccess(Optional<Node> nodeOptional) {
                        HAJobScheduler.getInstance().submitJob(() -> {
                            TypedReadWriteTransaction<D> tx1 = new BatchedTransaction(
                                logicalDatastoreType);
                            if (nodeOptional.isPresent()) {
                                copyPSNode(nodeOptional,
                                    srcPsPath, dstPsPath, dstGlobalPath, logicalDatastoreType, tx1);
                            } else {
                                tx1.put(dstPsPath, new NodeBuilder().setNodeId(dstPsPath
                                    .firstKeyOf(Node.class).getNodeId()).build());
                            }

                        });
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                });
            } catch (ExecutionException | InterruptedException e) {

            }
            return;
        }
        NodeBuilder dstPsNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPsPath);
        PhysicalSwitchAugmentationBuilder dstPsAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();

        PhysicalSwitchAugmentation srcPsAugmenatation =
                srcPsNodeOptional.get().augmentation(PhysicalSwitchAugmentation.class);
        Node existingDstPsNode = null;
        try {
            existingDstPsNode = HwvtepHAUtil.readNode(tx, dstPsPath);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("NodeCopier Read Failed for Node:{}", dstPsPath);
        }
        PhysicalSwitchAugmentation existingDstPsAugmentation =
                HwvtepHAUtil.getPhysicalSwitchAugmentationOfNode(existingDstPsNode);
        mergeOpManagedByAttributes(srcPsAugmenatation, dstPsAugmentationBuilder, dstGlobalPath);
        if (Operational.class.equals(logicalDatastoreType)) {
            psAugmentationMerger.mergeOperationalData(dstPsAugmentationBuilder, existingDstPsAugmentation,
                    srcPsAugmenatation, dstPsPath);
            psNodeMerger.mergeOperationalData(dstPsNodeBuilder, existingDstPsNode, srcPsNodeOptional.get(), dstPsPath);
            dstPsNodeBuilder.addAugmentation(dstPsAugmentationBuilder.build());
            Node dstPsNode = dstPsNodeBuilder.build();
            tx.mergeParentStructureMerge(dstPsPath, dstPsNode);
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
            try {
                boolean isEntryExists = tx.exists(dstPsPath).get();
                if (isEntryExists) {
                    LOG.info("Destination PS Node: {} already exists in config-topo.", dstNodeName);
                    InstanceIdentifier<PhysicalSwitchAugmentation> dstPsAugPath =
                        dstPsPath.augmentation(PhysicalSwitchAugmentation.class);
                    tx.put(dstPsAugPath, dstPsAugmentationBuilder.build());
                } else {
                    LOG.info("Destination PS Node: {} doesn't still exist in config-topo.",
                        dstNodeName);
                    dstPsNodeBuilder.addAugmentation(dstPsAugmentationBuilder.build());
                    Node dstPsNode = dstPsNodeBuilder.build();
                    tx.put(dstPsPath, dstPsNode);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error While checking Existing on Node {} in config-topo");
            }
            psNodeMerger.mergeConfigData(dstPsNodeBuilder, srcPsNodeOptional.get(), dstPsPath);

            if (dstPsNodeBuilder.getTerminationPoint() != null) {
                dstPsNodeBuilder.getTerminationPoint().values().forEach(terminationPoint -> {
                    InstanceIdentifier<TerminationPoint> terminationPointPath =
                        dstPsPath.child(TerminationPoint.class, terminationPoint.key());
                    tx.put(terminationPointPath, terminationPoint);
                    LOG.trace("Destination PS Node: {} updated with termination-point : {}",
                        dstNodeName, terminationPoint.key());
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
