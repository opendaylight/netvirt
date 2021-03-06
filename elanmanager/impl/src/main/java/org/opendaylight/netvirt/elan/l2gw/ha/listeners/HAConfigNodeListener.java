/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeCopier;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HAConfigNodeListener extends HwvtepNodeBaseListener<Configuration> implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(HAConfigNodeListener.class);
    private final IHAEventHandler haEventHandler;
    private final NodeCopier nodeCopier;

    @Inject
    public HAConfigNodeListener(DataBroker db, HAEventHandler haEventHandler,
                                NodeCopier nodeCopier,
                                final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                final ServiceRecoveryRegistry serviceRecoveryRegistry) throws Exception {
        super(CONFIGURATION, db);
        this.haEventHandler = haEventHandler;
        this.nodeCopier = nodeCopier;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(db);
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAConfigNodeListener");
            registerListener(CONFIGURATION, getDataBroker());
        } catch (Exception e) {
            LOG.error("HA Config Node register listener error.", e);
        }
    }

    public void deregisterListener() {
        LOG.info("Deregistering HAConfigNodeListener");
        super.close();
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> haPsPath,
                    Node haPSNode,
                    TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        //copy the ps node data to children
        String psId = haPSNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childSwitchIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        if (childSwitchIds.isEmpty()) {
            if (!hwvtepHACache.isHAEnabledDevice(haPsPath)) {
                LOG.error("HAConfigNodeListener Failed to find any ha children {}", haPsPath);
            }
            return;
        }

        for (InstanceIdentifier<Node> childPsPath : childSwitchIds) {
            String nodeId =
                    HwvtepHAUtil.convertToGlobalNodeId(childPsPath.firstKeyOf(Node.class).getNodeId().getValue());
            InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.convertToInstanceIdentifier(nodeId);
            nodeCopier.copyPSNode(Optional.ofNullable(haPSNode), haPsPath, childPsPath, childGlobalPath,
                    CONFIGURATION, tx);
        }
        LOG.trace("Handle config ps node add {}", psId);
    }

    @Override
    void onPsNodeUpdate(Node haPSUpdated,
        DataObjectModification<Node> mod,
        TypedReadWriteTransaction<Configuration> tx) {
        //copy the ps node data to children
        String psId = haPSUpdated.getNodeId().getValue();
        ((BatchedTransaction)tx).setSrcNodeId(haPSUpdated.getNodeId());
        ((BatchedTransaction)tx).updateMetric(true);
        Set<InstanceIdentifier<Node>> childSwitchIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childSwitchId : childSwitchIds) {
            haEventHandler.copyHAPSUpdateToChild(childSwitchId, mod, tx);
            ((BatchedTransaction)tx).updateMetric(false);
        }
    }

    @Override
    void onGlobalNodeAdd(InstanceIdentifier<Node> haGlobalPath, Node haGlobalNode,
        TypedReadWriteTransaction<Configuration> tx) {
        //copy the parent global node data to children
        String haParentId = haGlobalNode.getNodeId().getValue();
        List<NodeId> childGlobalIds = HwvtepHAUtil
                .getChildNodeIdsFromManagerOtherConfig(Optional.ofNullable(haGlobalNode));
        if (childGlobalIds.isEmpty()) {
            if (!hwvtepHACache.isHAEnabledDevice(haGlobalPath)) {
                LOG.error("HAConfigNodeListener Failed to find any ha children {}", haGlobalPath);
            }
            return;
        }
        for (NodeId nodeId : childGlobalIds) {
            InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.convertToInstanceIdentifier(nodeId.getValue());
            nodeCopier.copyGlobalNode(Optional.ofNullable(haGlobalNode), haGlobalPath, childGlobalPath,
                    CONFIGURATION, tx);
        }
        LOG.trace("Handle config global node add {}", haParentId);
    }

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> key,
                            Node haUpdated,
                            Node haOriginal,
                            DataObjectModification<Node> mod,
                            TypedReadWriteTransaction<Configuration> tx) {
        Set<InstanceIdentifier<Node>> childNodeIds = hwvtepHACache.getChildrenForHANode(key);
        ((BatchedTransaction)tx).setSrcNodeId(haUpdated.getNodeId());
        ((BatchedTransaction)tx).updateMetric(true);
        for (InstanceIdentifier<Node> haChildNodeId : childNodeIds) {
            haEventHandler.copyHAGlobalUpdateToChild(haChildNodeId, mod, tx);
            ((BatchedTransaction)tx).updateMetric(false);
        }
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key,
                        Node deletedPsNode,
                        TypedReadWriteTransaction<Configuration> tx) {
        //delete ps children nodes
        String psId = deletedPsNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childPsIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childPsId : childPsIds) {
            try {
                HwvtepHAUtil.deleteNodeIfPresent(tx, childPsId);
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("Exception while deleting PS node {} from config topo", childPsId);
            }
        }
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> key,
                            Node haNode,
                            TypedReadWriteTransaction<Configuration> tx) {
        //delete child nodes
        Set<InstanceIdentifier<Node>> children = hwvtepHACache.getChildrenForHANode(key);
        for (InstanceIdentifier<Node> childId : children) {
            try {
                HwvtepHAUtil.deleteNodeIfPresent(tx, childId);
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("Exception while deleting Global node {} from config topo ", childId);
            }
        }
        try {
            HwvtepHAUtil.deletePSNodesOfNode(key, haNode, tx);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while deleting PS nodes for HA Node {} from config topo", haNode.getNodeId());
        }
    }
}
