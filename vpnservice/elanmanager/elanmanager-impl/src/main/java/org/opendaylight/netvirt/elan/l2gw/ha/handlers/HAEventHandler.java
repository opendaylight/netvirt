/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import com.google.common.base.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAEventHandler implements IHAEventHandler {

    private final NodeConnectedHandler nodeConnectedHandler;
    private final ConfigNodeUpdatedHandler configNodeUpdatedHandler = new ConfigNodeUpdatedHandler();
    private final OpNodeUpdatedHandler opNodeUpdatedHandler = new OpNodeUpdatedHandler();
    private final DataBroker db;

    @Inject
    public HAEventHandler(DataBroker db) {
        this.db = db;
        this.nodeConnectedHandler = new NodeConnectedHandler(db);
    }

    @Override
    public void handleChildNodeConnected(Node connectedNode,
                                         InstanceIdentifier<Node> connectedNodePath,
                                         InstanceIdentifier<Node> haNodePath,
                                         ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (haNodePath == null) {
            return;
        }
        nodeConnectedHandler.handleNodeConnected(connectedNode, connectedNodePath, haNodePath,
                Optional.absent(), Optional.absent(), tx);
    }

    @Override
    public void handleChildNodeReConnected(Node connectedNode,
                                           InstanceIdentifier<Node> connectedNodePath,
                                           InstanceIdentifier<Node> haNodePath,
                                           Optional<Node> haGlobalCfg,
                                           Optional<Node> haPSCfg,
                                           ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (haNodePath == null) {
            return;
        }
        nodeConnectedHandler.handleNodeConnected(connectedNode, connectedNodePath, haNodePath,
                haGlobalCfg, haPSCfg, tx);
    }

    @Override
    public void copyChildGlobalOpUpdateToHAParent(Node updatedSrcNode,
                                                  Node origSrcNode,
                                                  InstanceIdentifier<Node> haPath,
                                                  ReadWriteTransaction tx) throws ReadFailedException {
        if (haPath == null) {
            return;
        }
        opNodeUpdatedHandler.copyChildGlobalOpUpdateToHAParent(updatedSrcNode, origSrcNode, haPath, tx);
    }

    @Override
    public void copyChildPsOpUpdateToHAParent(Node updatedSrcPSNode,
                                              Node origSrcPSNode,
                                              InstanceIdentifier<Node> haPath,
                                              ReadWriteTransaction tx) throws ReadFailedException {
        if (haPath == null) {
            return;
        }
        opNodeUpdatedHandler.copyChildPsOpUpdateToHAParent(updatedSrcPSNode, origSrcPSNode, haPath, tx);
    }

    @Override
    public void copyHAPSUpdateToChild(Node haUpdated,
                                      Node haOriginal,
                                      InstanceIdentifier<Node> haChildNodeId,
                                      ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        if (haChildNodeId == null) {
            return;
        }
        configNodeUpdatedHandler.copyHAPSUpdateToChild(haUpdated, haOriginal, haChildNodeId, tx);
    }

    @Override
    public void copyHAGlobalUpdateToChild(Node haUpdated,
                                          Node haOriginal,
                                          InstanceIdentifier<Node> haChildNodeId,
                                          ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        if (haChildNodeId == null) {
            return;
        }
        configNodeUpdatedHandler.copyHAGlobalUpdateToChild(haUpdated, haOriginal, haChildNodeId, tx);
    }

}
