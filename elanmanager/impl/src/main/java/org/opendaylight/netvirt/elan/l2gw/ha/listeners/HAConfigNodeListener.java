/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeCopier;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAConfigNodeListener extends HwvtepNodeBaseListener {
    private final IHAEventHandler haEventHandler;
    private final NodeCopier nodeCopier;

    @Inject
    public HAConfigNodeListener(DataBroker db, HAEventHandler haEventHandler,
                                NodeCopier nodeCopier) throws Exception {
        super(LogicalDatastoreType.CONFIGURATION, db);
        this.haEventHandler = haEventHandler;
        this.nodeCopier = nodeCopier;
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> haPsPath,
                     Node haPSNode,
                     ReadWriteTransaction tx) throws InterruptedException, ExecutionException, ReadFailedException {
        //copy the ps node data to children
        String psId = haPSNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childSwitchIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        if (childSwitchIds.isEmpty()) {
            LOG.error("Failed to find any ha children {}", haPsPath);
            return;
        }
        for (InstanceIdentifier<Node> childPsPath : childSwitchIds) {
            String nodeId =
                    HwvtepHAUtil.convertToGlobalNodeId(childPsPath.firstKeyOf(Node.class).getNodeId().getValue());
            InstanceIdentifier<Node> childGlobalPath = HwvtepHAUtil.convertToInstanceIdentifier(nodeId);
            nodeCopier.copyPSNode(Optional.fromNullable(haPSNode), haPsPath, childPsPath, childGlobalPath,
                    LogicalDatastoreType.CONFIGURATION, tx);
        }
        LOG.trace("Handle config ps node add {}", psId);
    }

    @Override
    void onPsNodeUpdate(Node haPSUpdated,
            Node haPSOriginal,
            DataObjectModification<Node> mod,
            ReadWriteTransaction tx) throws InterruptedException, ExecutionException, ReadFailedException {
        //copy the ps node data to children
        String psId = haPSUpdated.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childSwitchIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childSwitchId : childSwitchIds) {
            haEventHandler.copyHAPSUpdateToChild(childSwitchId, mod, tx);
        }
    }

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> key,
                            Node haUpdated,
                            Node haOriginal,
                            DataObjectModification<Node> mod,
                            ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        Set<InstanceIdentifier<Node>> childNodeIds = hwvtepHACache.getChildrenForHANode(key);
        for (InstanceIdentifier<Node> haChildNodeId : childNodeIds) {
            haEventHandler.copyHAGlobalUpdateToChild(haChildNodeId, mod, tx);
        }
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key,
                        Node deletedPsNode,
                        ReadWriteTransaction tx) throws ReadFailedException {
        //delete ps children nodes
        String psId = deletedPsNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childPsIds = HwvtepHAUtil.getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childPsId : childPsIds) {
            HwvtepHAUtil.deleteNodeIfPresent(tx, CONFIGURATION, childPsId);
        }
    }

    @Override
    void onGlobalNodeDelete(InstanceIdentifier<Node> key,
                            Node haNode,
                            ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        //delete child nodes
        Set<InstanceIdentifier<Node>> children = hwvtepHACache.getChildrenForHANode(key);
        for (InstanceIdentifier<Node> childId : children) {
            HwvtepHAUtil.deleteNodeIfPresent(tx, CONFIGURATION, childId);
        }
        HwvtepHAUtil.deletePSNodesOfNode(key, haNode, tx);
    }
}
