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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeCopier implements INodeCopier {

    private static final Logger LOG = LoggerFactory.getLogger(NodeCopier.class);

    private final GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    private final PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    private final GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    private final PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();
    private final DataBroker db;

    @Inject
    public NodeCopier(DataBroker db) {
        this.db = db;
    }

    @Override
    public void copyGlobalNode(Optional<Node> srcGlobalNodeOptional,
                               InstanceIdentifier<Node> srcPath,
                               InstanceIdentifier<Node> dstPath,
                               LogicalDatastoreType logicalDatastoreType,
                               ReadWriteTransaction tx) throws ReadFailedException {
        if (!srcGlobalNodeOptional.isPresent() && logicalDatastoreType == CONFIGURATION) {
            Futures.addCallback(tx.read(logicalDatastoreType, srcPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> {
                        try {
                            ReadWriteTransaction tx1 = new BatchedTransaction();
                            if (nodeOptional.isPresent()) {
                                copyGlobalNode(nodeOptional, srcPath, dstPath, logicalDatastoreType, tx1);
                            } else {
                                /**
                                 * In case the Parent HA Global Node is not present and Child HA node is present
                                 * It means that both the child are disconnected/removed hence the parent is deleted.
                                 * @see org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpNodeListener
                                 * OnGLobalNode() delete function
                                 * So we should delete the existing config child node as cleanup
                                 */
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
        HwvtepGlobalAugmentation srcGlobalAugmentation =
                srcGlobalNodeOptional.get().getAugmentation(HwvtepGlobalAugmentation.class);
        if (srcGlobalAugmentation == null) {
            /**
             * If Source HA Global Node is not present
             * It means that both the child are disconnected/removed hence the parent is deleted.
             * @see org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpNodeListener OnGLobalNode() delete function
             * So we should delete the existing config child node as cleanup
             */
            HwvtepHAUtil.deleteNodeIfPresent(tx, logicalDatastoreType, dstPath);
            return;
        }
        NodeBuilder haNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPath);
        HwvtepGlobalAugmentationBuilder haBuilder = new HwvtepGlobalAugmentationBuilder();

        Optional<Node> existingDstGlobalNodeOptional = tx.read(logicalDatastoreType, dstPath).checkedGet();
        Node existingDstGlobalNode =
                existingDstGlobalNodeOptional.isPresent() ? existingDstGlobalNodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAGlobalData = HwvtepHAUtil.getGlobalAugmentationOfNode(existingDstGlobalNode);

        globalAugmentationMerger.mergeOperationalData(haBuilder, existingHAGlobalData, srcGlobalAugmentation, dstPath);
        globalNodeMerger.mergeOperationalData(haNodeBuilder,
                existingDstGlobalNode, srcGlobalNodeOptional.get(), dstPath);


        if (OPERATIONAL == logicalDatastoreType) {
            haBuilder.setManagers(HwvtepHAUtil.buildManagersForHANode(srcGlobalNodeOptional.get(),
                    existingDstGlobalNodeOptional));
            //Also update the manager section in config which helps in cluster reboot scenarios
            haBuilder.getManagers().stream().forEach((manager) -> {
                InstanceIdentifier<Managers> managerIid = dstPath.augmentation(HwvtepGlobalAugmentation.class)
                        .child(Managers.class, manager.getKey());
                tx.put(CONFIGURATION, managerIid, manager, true);
            });

        }
        haBuilder.setDbVersion(srcGlobalAugmentation.getDbVersion());
        haNodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, haBuilder.build());
        Node haNode = haNodeBuilder.build();
        if (OPERATIONAL == logicalDatastoreType) {
            tx.merge(logicalDatastoreType, dstPath, haNode, true);
        } else {
            tx.put(logicalDatastoreType, dstPath, haNode, true);
        }
    }

    @Override
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
                                /**
                                 * Deleting node please refer @see #copyGlobalNode for explanation
                                 */
                                HwvtepHAUtil.deleteNodeIfPresent(tx1, logicalDatastoreType, dstPsPath);
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
        if (OPERATIONAL == logicalDatastoreType) {
            psAugmentationMerger.mergeOperationalData(dstPsAugmentationBuilder, existingDstPsAugmentation,
                    srcPsAugmenatation, dstPsPath);
            psNodeMerger.mergeOperationalData(dstPsNodeBuilder, existingDstPsNode, srcPsNodeOptional.get(), dstPsPath);
        } else {
            psAugmentationMerger.mergeConfigData(dstPsAugmentationBuilder, srcPsAugmenatation, dstPsPath);
            psNodeMerger.mergeConfigData(dstPsNodeBuilder, srcPsNodeOptional.get(), dstPsPath);
        }
        mergeOpManagedByAttributes(srcPsAugmenatation, dstPsAugmentationBuilder, dstGlobalPath);

        dstPsNodeBuilder.addAugmentation(PhysicalSwitchAugmentation.class, dstPsAugmentationBuilder.build());
        Node dstPsNode = dstPsNodeBuilder.build();
        tx.merge(logicalDatastoreType, dstPsPath, dstPsNode, true);
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
