/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeCopier;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
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
                                NodeCopier nodeCopier, HwvtepNodeHACache hwvtepNodeHACache,
                                MetricProvider metricProvider,
                                final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                final ServiceRecoveryRegistry serviceRecoveryRegistry) throws Exception {
        super(CONFIGURATION, db, hwvtepNodeHACache, metricProvider, true);
        this.haEventHandler = haEventHandler;
        this.nodeCopier = nodeCopier;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAConfigNodeListener");
            registerListener(CONFIGURATION, getDataBroker());
        } catch (Exception e) {
            LOG.error("HA Config Node register listener error.");
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
        Set<InstanceIdentifier<Node>> childSwitchIds = getPSChildrenIdsForHAPSNode(psId);
        if (childSwitchIds.isEmpty()) {
            LOG.error("Failed to find any ha children {}", haPsPath);
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
        Set<InstanceIdentifier<Node>> childSwitchIds = getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childSwitchId : childSwitchIds) {
            haEventHandler.copyHAPSUpdateToChild(childSwitchId, mod, tx);
        }
    }

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> key,
                            Node haUpdated,
                            Node haOriginal,
                            DataObjectModification<Node> mod,
                            TypedReadWriteTransaction<Configuration> tx) {
        Set<InstanceIdentifier<Node>> childNodeIds = getHwvtepNodeHACache().getChildrenForHANode(key);
        for (InstanceIdentifier<Node> haChildNodeId : childNodeIds) {
            haEventHandler.copyHAGlobalUpdateToChild(haChildNodeId, mod, tx);
        }
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key,
                        Node deletedPsNode,
                        TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        //delete ps children nodes
        String psId = deletedPsNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childPsIds = getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childPsId : childPsIds) {
            HwvtepHAUtil.deleteNodeIfPresent(tx, childPsId);
        }
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> key,
                            Node haNode,
                            TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        //delete child nodes
        Set<InstanceIdentifier<Node>> children = getHwvtepNodeHACache().getChildrenForHANode(key);
        for (InstanceIdentifier<Node> childId : children) {
            HwvtepHAUtil.deleteNodeIfPresent(tx, childId);
        }
        HwvtepHAUtil.deletePSNodesOfNode(key, haNode, tx);
    }

    private Set<InstanceIdentifier<Node>> getPSChildrenIdsForHAPSNode(String psNodId) {
        if (!psNodId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return Collections.emptySet();
        }
        String nodeId = HwvtepHAUtil.convertToGlobalNodeId(psNodId);
        InstanceIdentifier<Node> iid = HwvtepHAUtil.convertToInstanceIdentifier(nodeId);
        if (getHwvtepNodeHACache().isHAParentNode(iid)) {
            Set<InstanceIdentifier<Node>> childSwitchIds = new HashSet<>();
            Set<InstanceIdentifier<Node>> childGlobalIds = getHwvtepNodeHACache().getChildrenForHANode(iid);
            final String append = psNodId.substring(psNodId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
            for (InstanceIdentifier<Node> childId : childGlobalIds) {
                String childIdVal = childId.firstKeyOf(Node.class).getNodeId().getValue();
                childSwitchIds.add(HwvtepHAUtil.convertToInstanceIdentifier(childIdVal + append));
            }
            return childSwitchIds;
        }
        return Collections.emptySet();
    }
}
