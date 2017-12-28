/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.HAEventHandler;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.IHAEventHandler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAConfigNodeListener extends HwvtepNodeBaseListener {
    private final IHAEventHandler haEventHandler;

    @Inject
    public HAConfigNodeListener(DataBroker db, HAEventHandler haEventHandler,
            HwvtepNodeHACache hwvtepNodeHACache) throws Exception {
        super(LogicalDatastoreType.CONFIGURATION, db, hwvtepNodeHACache);
        this.haEventHandler = haEventHandler;
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> key,
                     Node haPSNode,
                     ReadWriteTransaction tx) throws InterruptedException, ExecutionException, ReadFailedException {
        //copy the ps node data to children
        String psId = haPSNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childSwitchIds = getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childSwitchId : childSwitchIds) {
            haEventHandler.copyHAPSUpdateToChild(haPSNode, null/*haOriginal*/, childSwitchId, tx);
        }
        LOG.trace("Handle config ps node add {}", psId);
    }

    @Override
    void onPsNodeUpdate(InstanceIdentifier<Node> key,
                        Node haPSUpdated,
                        Node haPSOriginal,
                        ReadWriteTransaction tx) throws InterruptedException, ExecutionException, ReadFailedException {
        //copy the ps node data to children
        String psId = haPSUpdated.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childSwitchIds = getPSChildrenIdsForHAPSNode(psId);
        for (InstanceIdentifier<Node> childSwitchId : childSwitchIds) {
            haEventHandler.copyHAPSUpdateToChild(haPSUpdated, haPSOriginal, childSwitchId, tx);
        }
    }

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> key,
                            Node haUpdated,
                            Node haOriginal,
                            ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        //copy the ha node data to children taken care of the HAListeners
        /*
        Set<InstanceIdentifier<Node>> childNodeIds = hwvtepHACache.getChildrenForHANode(key);
        for (InstanceIdentifier<Node> haChildNodeId : childNodeIds) {
            haEventHandler.copyHAGlobalUpdateToChild(haUpdated, haOriginal, haChildNodeId, tx);
        }
        */
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key,
                        Node deletedPsNode,
                        ReadWriteTransaction tx) throws ReadFailedException {
        //delete ps children nodes
        String psId = deletedPsNode.getNodeId().getValue();
        Set<InstanceIdentifier<Node>> childPsIds = getPSChildrenIdsForHAPSNode(psId);
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
        Set<InstanceIdentifier<Node>> children = getHwvtepNodeHACache().getChildrenForHANode(key);
        for (InstanceIdentifier<Node> childId : children) {
            HwvtepHAUtil.deleteNodeIfPresent(tx, CONFIGURATION, childId);
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
        return Collections.EMPTY_SET;
    }
}
