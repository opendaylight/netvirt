/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import static org.opendaylight.mdsal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
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
public class NodeCopier {

    private static final Logger LOG = LoggerFactory.getLogger(NodeCopier.class);

    private final GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    private final PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    private final GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    private final PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public NodeCopier(DataBroker db) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
    }

    public <D extends Datastore> void copyGlobalNode(Optional<Node> srcGlobalNodeOptional,
                               InstanceIdentifier<Node> srcPath,
                               InstanceIdentifier<Node> dstPath,
                               Class<D> datastoreType,
                               TypedReadWriteTransaction<D> tx)
            throws ExecutionException, InterruptedException {
        if (!srcGlobalNodeOptional.isPresent() && Configuration.class.equals(datastoreType)) {
            Futures.addCallback(tx.read(srcPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> LoggingFutures.addErrorLogging(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(datastoreType, tx -> {
                            if (nodeOptional.isPresent()) {
                                copyGlobalNode(nodeOptional, srcPath, dstPath, datastoreType, tx);
                            } else {
                                /*
                                 * In case the Parent HA Global Node is not present and Child HA node is present
                                 * It means that both the child are disconnected/removed hence the parent is
                                 * deleted.
                                 * @see org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpNodeListener
                                 * OnGLobalNode() delete function
                                 * So we should delete the existing config child node as cleanup
                                 */
                                HwvtepHAUtil.deleteNodeIfPresent(tx, dstPath);
                            }
                        }), LOG, "Failed to read source node {}", srcPath));
                }

                @Override
                public void onFailure(Throwable throwable) {
                }
            }, MoreExecutors.directExecutor());
            return;
        }
        HwvtepGlobalAugmentation srcGlobalAugmentation =
                srcGlobalNodeOptional.get().augmentation(HwvtepGlobalAugmentation.class);
        if (srcGlobalAugmentation == null) {
            /*
             * If Source HA Global Node is not present
             * It means that both the child are disconnected/removed hence the parent is deleted.
             * @see org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpNodeListener OnGLobalNode() delete function
             * So we should delete the existing config child node as cleanup
             */
            HwvtepHAUtil.deleteNodeIfPresent(tx, dstPath);
            return;
        }
        NodeBuilder haNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPath);
        HwvtepGlobalAugmentationBuilder haBuilder = new HwvtepGlobalAugmentationBuilder();

        Optional<Node> existingDstGlobalNodeOptional = tx.read(dstPath).get();
        Node existingDstGlobalNode =
                existingDstGlobalNodeOptional.isPresent() ? existingDstGlobalNodeOptional.get() : null;
        HwvtepGlobalAugmentation existingHAGlobalData = HwvtepHAUtil.getGlobalAugmentationOfNode(existingDstGlobalNode);


        if (Operational.class.equals(datastoreType)) {
            globalAugmentationMerger.mergeOperationalData(haBuilder, existingHAGlobalData, srcGlobalAugmentation,
                    dstPath);
            globalNodeMerger.mergeOperationalData(haNodeBuilder,
                    existingDstGlobalNode, srcGlobalNodeOptional.get(), dstPath);
            haBuilder.setManagers(HwvtepHAUtil.buildManagersForHANode(srcGlobalNodeOptional.get(),
                    existingDstGlobalNodeOptional));
            //Also update the manager section in config which helps in cluster reboot scenarios
            LoggingFutures.addErrorLogging(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    confTx -> haBuilder.getManagers().forEach(manager -> {
                        InstanceIdentifier<Managers> managerIid =
                            dstPath.augmentation(HwvtepGlobalAugmentation.class).child(Managers.class, manager.key());
                        confTx.put(managerIid, manager, CREATE_MISSING_PARENTS);
                    })), LOG, "Error updating the manager section in config");

        } else {
            globalAugmentationMerger.mergeConfigData(haBuilder, srcGlobalAugmentation, dstPath);
            globalNodeMerger.mergeConfigData(haNodeBuilder, srcGlobalNodeOptional.get(), dstPath);
        }

        haBuilder.setDbVersion(srcGlobalAugmentation.getDbVersion());
        haNodeBuilder.addAugmentation(HwvtepGlobalAugmentation.class, haBuilder.build());
        Node haNode = haNodeBuilder.build();
        if (Operational.class.equals(datastoreType)) {
            tx.merge(dstPath, haNode, CREATE_MISSING_PARENTS);
        } else {
            tx.put(dstPath, haNode, CREATE_MISSING_PARENTS);
        }
    }

    public <D extends Datastore> void copyPSNode(Optional<Node> srcPsNodeOptional,
                           InstanceIdentifier<Node> srcPsPath,
                           InstanceIdentifier<Node> dstPsPath,
                           InstanceIdentifier<Node> dstGlobalPath,
                           Class<D> datastoreType,
                           TypedReadWriteTransaction<D> tx)
            throws ExecutionException, InterruptedException {
        if (!srcPsNodeOptional.isPresent() && Configuration.class.equals(datastoreType)) {
            Futures.addCallback(tx.read(srcPsPath), new FutureCallback<Optional<Node>>() {
                @Override
                public void onSuccess(Optional<Node> nodeOptional) {
                    HAJobScheduler.getInstance().submitJob(() -> {
                        LoggingFutures.addErrorLogging(
                            txRunner.callWithNewReadWriteTransactionAndSubmit(datastoreType, tx -> {
                                if (nodeOptional.isPresent()) {
                                    copyPSNode(nodeOptional,
                                        srcPsPath, dstPsPath, dstGlobalPath, datastoreType, tx);
                                } else {
                                    /*
                                     * Deleting node please refer @see #copyGlobalNode for explanation
                                     */
                                    HwvtepHAUtil.deleteNodeIfPresent(tx, dstPsPath);
                                }
                            }), LOG, "Failed to read source node {}", srcPsPath);
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                }
            }, MoreExecutors.directExecutor());
            return;
        }
        NodeBuilder dstPsNodeBuilder = HwvtepHAUtil.getNodeBuilderForPath(dstPsPath);
        PhysicalSwitchAugmentationBuilder dstPsAugmentationBuilder = new PhysicalSwitchAugmentationBuilder();

        PhysicalSwitchAugmentation srcPsAugmenatation =
                srcPsNodeOptional.get().augmentation(PhysicalSwitchAugmentation.class);

        Node existingDstPsNode = tx.read(dstPsPath).get().orNull();
        PhysicalSwitchAugmentation existingDstPsAugmentation =
                HwvtepHAUtil.getPhysicalSwitchAugmentationOfNode(existingDstPsNode);
        if (Operational.class.equals(datastoreType)) {
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
        tx.merge(dstPsPath, dstPsNode, CREATE_MISSING_PARENTS);
        LOG.debug("Copied {} physical switch node from {} to {}", datastoreType, srcPsPath, dstPsPath);
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
