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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeCopier implements INodeCopier {

    static Logger LOG = LoggerFactory.getLogger(NodeCopier.class);

    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();
    DataBroker db;
    HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    public NodeCopier(DataBroker db) {
        this.db = db;
    }

    public void copyGlobalNode(Node srcGlobalNode,
                        InstanceIdentifier<Node> srcPath,
                        InstanceIdentifier<Node> dstPath,
                        LogicalDatastoreType logicalDatastoreType,
                        ReadWriteTransaction tx) throws ReadFailedException {
        if (srcGlobalNode == null && logicalDatastoreType == CONFIGURATION) {
            Futures.addCallback(tx.read(logicalDatastoreType, srcPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(@Nullable Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> {
                        try {
                            ReadWriteTransaction tx1 = new BatchedTransaction(db);
                            if (nodeOptional.isPresent()) {
                                copyGlobalNode(nodeOptional.get(), srcPath, dstPath, logicalDatastoreType, tx1);
                            } else {
                                HwvtepHAUtil.deleteNodeIfPresent(tx1, logicalDatastoreType, dstPath);
                            }
                        } catch (ReadFailedException e) {
                            LOG.error("Failed to read source node {}",srcPath);
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                }
            });
            return;
        }
        HwvtepGlobalAugmentation srcGlobalAugmentation = srcGlobalNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (srcGlobalAugmentation == null) {
            return;
        }
        NodeBuilder haNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPath);
        HwvtepGlobalAugmentationBuilder haBuilder = new HwvtepGlobalAugmentationBuilder();

        Optional<Node> existingDstGlobalNodeOptional = tx.read(logicalDatastoreType, dstPath).checkedGet();
        Node existingDstGlobalNode =
                existingDstGlobalNodeOptional.isPresent() ? existingDstGlobalNodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAGlobalData = HwvtepHAUtil.getGlobalAugmentationOfNode(existingDstGlobalNode);

        globalAugmentationMerger.mergeOperationalData(haBuilder, existingHAGlobalData, srcGlobalAugmentation, dstPath);
        globalNodeMerger.mergeOperationalData(haNodeBuilder, existingDstGlobalNode, srcGlobalNode, dstPath);

        if (OPERATIONAL == logicalDatastoreType) {
            haBuilder.setManagers(HwvtepHAUtil.buildManagersForHANode(srcGlobalNode, existingDstGlobalNodeOptional));
        }
        haNodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, haBuilder.build());
        Node haNode = haNodeBuilder.build();
        tx.merge(logicalDatastoreType, dstPath, haNode, true);
    }

    public void copyPSNode(Node srcPsNode,
                           InstanceIdentifier<Node> srcPsPath,
                           InstanceIdentifier<Node> dstPsPath,
                           InstanceIdentifier<Node> dstGlobalPath,
                           LogicalDatastoreType logicalDatastoreType,
                           ReadWriteTransaction tx) throws ReadFailedException {
        if (srcPsNode == null && logicalDatastoreType == CONFIGURATION) {
            Futures.addCallback(tx.read(logicalDatastoreType, srcPsPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(@Nullable Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> {
                        try {
                            ReadWriteTransaction tx1 = new BatchedTransaction(db);
                            if (nodeOptional.isPresent()) {
                                copyPSNode(nodeOptional.get(),
                                        srcPsPath, dstPsPath, dstGlobalPath, logicalDatastoreType, tx1);
                            } else {
                                HwvtepHAUtil.deleteNodeIfPresent(tx1, logicalDatastoreType, dstPsPath);
                            }
                        } catch (ReadFailedException e) {
                            LOG.error("Failed to read src node {}", srcPsNode);
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

        PhysicalSwitchAugmentation srcPsAugmenatation = srcPsNode.getAugmentation(PhysicalSwitchAugmentation.class);

        Node existingDstPsNode = HwvtepHAUtil.readNode(tx, logicalDatastoreType, dstPsPath);
        PhysicalSwitchAugmentation existingDstPsAugmentation =
                HwvtepHAUtil.getPhysicalSwitchAugmentationOfNode(existingDstPsNode);

        psAugmentationMerger.mergeOperationalData(dstPsAugmentationBuilder, existingDstPsAugmentation,
                srcPsAugmenatation, dstPsPath);
        psNodeMerger.mergeOperationalData(dstPsNodeBuilder, existingDstPsNode, srcPsNode, dstPsPath);
        mergeOpManagedByAttributes(srcPsAugmenatation, dstPsAugmentationBuilder, dstGlobalPath);

        dstPsNodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, dstPsAugmentationBuilder.build());
        Node dstPsNode = dstPsNodeBuilder.build();
        tx.merge(logicalDatastoreType, dstPsPath, dstPsNode, true);
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
}
