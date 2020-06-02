/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeCopier;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HAOpNodeListener extends HwvtepNodeBaseListener<Operational> implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(HAOpNodeListener.class);

    private static final BiPredicate<String, InstanceIdentifier<Node>> IS_PS_CHILD_TO_GLOBAL_NODE =
        (globalNodeId, iid) -> {
            String psNodeId = iid.firstKeyOf(Node.class).getNodeId().getValue();
            return psNodeId.startsWith(globalNodeId) && psNodeId.contains("physicalswitch");
        };

    private final IHAEventHandler haEventHandler;
    private final HAOpClusteredListener haOpClusteredListener;
    private final NodeCopier nodeCopier;

    @Inject
    public HAOpNodeListener(DataBroker db, HAEventHandler haEventHandler,
                            HAOpClusteredListener haOpClusteredListener,
                            NodeCopier nodeCopier, HwvtepNodeHACache hwvtepNodeHACache,
                            MetricProvider metricProvider,
                            final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                            final ServiceRecoveryRegistry serviceRecoveryRegistry) throws Exception {
        super(OPERATIONAL, db, hwvtepNodeHACache, metricProvider, true);
        this.haEventHandler = haEventHandler;
        this.haOpClusteredListener = haOpClusteredListener;
        this.nodeCopier = nodeCopier;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAOpNodeListener");
            registerListener(OPERATIONAL, getDataBroker());
        } catch (Exception e) {
            LOG.error("HA OP Node register listener error.");
        }
    }

    public void deregisterListener() {
        LOG.info("Deregistering HAOpNodeListener");
        super.close();
    }

    String getNodeId(InstanceIdentifier<Node> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    @Override
    public void onGlobalNodeAdd(InstanceIdentifier<Node> childGlobalPath,
                                Node childNode,
                                TypedReadWriteTransaction<Operational> tx) {
        //copy child global node to ha global node
        //create ha global config node if not present
        //copy ha global config node to child global config node
        LOG.trace("Node connected {} - Checking if Ha or Non-Ha enabled ", childNode.getNodeId().getValue());
        haOpClusteredListener.onGlobalNodeAdd(childGlobalPath, childNode, tx);
        if (isNotHAChild(childGlobalPath)) {
            return;
        }
        InstanceIdentifier<Node> haNodePath = getHwvtepNodeHACache().getParent(childGlobalPath);
        LOG.trace("Ha enabled child node connected {}", childNode.getNodeId().getValue());
        try {
            nodeCopier.copyGlobalNode(Optional.ofNullable(childNode), childGlobalPath, haNodePath, OPERATIONAL, tx);
            LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> nodeCopier.copyGlobalNode(Optional.ofNullable(null), haNodePath, childGlobalPath,
                    CONFIGURATION, confTx)), LOG, "Error copying to configuration");
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read nodes {} , {} ", childGlobalPath, haNodePath);
        }
        readAndCopyChildPsOpToParent(childNode, tx);
    }

    //Update on global node has been taken care by HAListeners as per perf improvement
    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childGlobalPath,
                            Node updatedChildNode,
                            Node originalChildNode,
                            DataObjectModification<Node> mod,
                            TypedReadWriteTransaction<Operational> tx)  {

        String oldHAId = HwvtepHAUtil.getHAIdFromManagerOtherConfig(originalChildNode);
        if (!Strings.isNullOrEmpty(oldHAId)) { //was already ha child
            InstanceIdentifier<Node> haPath = getHwvtepNodeHACache().getParent(childGlobalPath);
            LOG.debug("Copy oper update from child {} to parent {}", childGlobalPath, haPath);
            haEventHandler.copyChildGlobalOpUpdateToHAParent(haPath, mod, tx);
            return;//TODO handle unha case
        }

        addToHACacheIfBecameHAChild(childGlobalPath, updatedChildNode, originalChildNode);
        if (isNotHAChild(childGlobalPath)) {
            return;
        }
        LOG.info("{} became ha child ", updatedChildNode.getNodeId().getValue());
        onGlobalNodeAdd(childGlobalPath, updatedChildNode, tx);
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> childGlobalPath,
                            Node childNode,
                            TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        haOpClusteredListener.onGlobalNodeDelete(childGlobalPath, childNode, tx);
        if (isNotHAChild(childGlobalPath)) {
            LOG.info("non ha child global delete {} ", getNodeId(childGlobalPath));
            return;
        }
        LOG.info("ha child global delete {} ", getNodeId(childGlobalPath));
        InstanceIdentifier<Node> haNodePath = getHwvtepNodeHACache().getParent(childGlobalPath);
        Set<InstanceIdentifier<Node>> children = getHwvtepNodeHACache().getChildrenForHANode(haNodePath);
        if (haOpClusteredListener.getConnected(children).isEmpty()) {
            LOG.info("All child deleted for ha node {} ", HwvtepHAUtil.getNodeIdVal(haNodePath));
            //ha ps delete is taken care by ps node delete
            //HwvtepHAUtil.deleteSwitchesManagedBy-Node(haNodePath, tx);
            HwvtepHAUtil.deleteNodeIfPresent(tx, haNodePath);
        } else {
            LOG.info("not all child deleted {} connected {}", getNodeId(childGlobalPath),
                    haOpClusteredListener.getConnected(children));
        }
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> childPsPath,
                     Node childPsNode,
                     TypedReadWriteTransaction<Operational> tx) {
        //copy child ps oper node to ha ps oper node
        //copy ha ps config node to child ps config
        haOpClusteredListener.onPsNodeAdd(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
        if (!haOpClusteredListener.getConnectedNodes().contains(childGlobalPath)) {
            return;
        }
        if (isNotHAChild(childGlobalPath)) {
            return;
        }
        LOG.info("ha ps child connected {} ", getNodeId(childPsPath));
        InstanceIdentifier<Node> haGlobalPath = getHwvtepNodeHACache().getParent(childGlobalPath);
        InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
        try {
            nodeCopier.copyPSNode(Optional.ofNullable(childPsNode), childPsPath, haPsPath, haGlobalPath,
                    OPERATIONAL, tx);
            LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> nodeCopier.copyPSNode(Optional.ofNullable(null), haPsPath, childPsPath, childGlobalPath,
                    CONFIGURATION, confTx)), LOG, "Error copying to configuration");
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read nodes {} , {} ", childPsPath, haGlobalPath);
        }
    }

    @Override
    void onPsNodeUpdate(Node updatedChildPSNode,
        DataObjectModification<Node> mod,
        TypedReadWriteTransaction<Operational> tx) {
        InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(updatedChildPSNode);
        if (isNotHAChild(childGlobalPath)) {
            return;
        }
        InstanceIdentifier<Node> haGlobalPath = getHwvtepNodeHACache().getParent(childGlobalPath);
        haEventHandler.copyChildPsOpUpdateToHAParent(updatedChildPSNode, haGlobalPath, mod, tx);
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> childPsPath,
                        Node childPsNode,
                        TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        //one child ps node disconnected
        //find if all child ps nodes disconnected then delete parent ps node
        haOpClusteredListener.onPsNodeDelete(childPsPath, childPsNode, tx);
        InstanceIdentifier<Node> disconnectedChildGlobalPath = HwvtepHAUtil.getGlobalNodePathFromPSNode(childPsNode);
        if (isNotHAChild(disconnectedChildGlobalPath)) {
            LOG.info("on non ha ps child delete {} ", getNodeId(childPsPath));
            return;
        }
        InstanceIdentifier<Node> haGlobalPath = getHwvtepNodeHACache().getParent(disconnectedChildGlobalPath);
        Set<InstanceIdentifier<Node>> childPsPaths = getHwvtepNodeHACache().getChildrenForHANode(haGlobalPath).stream()
                .map((childGlobalPath) -> HwvtepHAUtil.convertPsPath(childPsNode, childGlobalPath))
                .collect(Collectors.toSet());
        //TODO validate what if this is null
        if (haOpClusteredListener.getConnected(childPsPaths).isEmpty()) {
            InstanceIdentifier<Node> haPsPath = HwvtepHAUtil.convertPsPath(childPsNode, haGlobalPath);
            LOG.info("All child deleted for ha ps node {} ", HwvtepHAUtil.getNodeIdVal(haPsPath));
            HwvtepHAUtil.deleteNodeIfPresent(tx, haPsPath);
            //HwvtepHAUtil.deleteGlobalNodeSwitches(haGlobalPath, haPsPath, LogicalDatastoreType.OPERATIONAL, tx);
        } else {
            LOG.info("not all ha ps child deleted {} connected {}", getNodeId(childPsPath),
                    haOpClusteredListener.getConnected(childPsPaths));
        }
    }

    private void readAndCopyChildPsOpToParent(Node childNode, TypedReadWriteTransaction<Operational> tx) {
        String childGlobalNodeId = childNode.getNodeId().getValue();
        List<InstanceIdentifier> childPsIids = new ArrayList<>();
        HwvtepGlobalAugmentation hwvtepGlobalAugmentation = childNode.augmentation(HwvtepGlobalAugmentation.class);
        if (hwvtepGlobalAugmentation == null ||
            HwvtepHAUtil.isEmpty(hwvtepGlobalAugmentation.nonnullSwitches().values())) {
            haOpClusteredListener.getConnectedNodes()
                    .stream()
                    .filter((connectedIid) -> IS_PS_CHILD_TO_GLOBAL_NODE.test(childGlobalNodeId, connectedIid))
                    .forEach(childPsIids::add);
        } else {
            hwvtepGlobalAugmentation.nonnullSwitches().values().forEach(
                (switches) -> childPsIids.add(switches.getSwitchRef().getValue()));
        }
        if (childPsIids.isEmpty()) {
            LOG.info("No child ps found for global {}", childGlobalNodeId);
        }
        childPsIids.forEach((psIid) -> {
            try {
                InstanceIdentifier<Node> childPsIid = psIid;
                Optional<Node> childPsNode = tx.read(childPsIid).get();
                if (childPsNode.isPresent()) {
                    LOG.debug("Child oper PS node found");
                    onPsNodeAdd(childPsIid, childPsNode.get(), tx);
                } else {
                    LOG.debug("Child oper ps node not found {}", childPsIid);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Failed to read child ps node {}", psIid);
            }
        });
    }

    private boolean isNotHAChild(InstanceIdentifier<Node> nodeId) {
        return  getHwvtepNodeHACache().getParent(nodeId) == null;
    }
}
