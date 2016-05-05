/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.*;

import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipState;
import org.opendaylight.netvirt.fcapsapp.alarm.AlarmAgent;
import org.opendaylight.netvirt.fcapsapp.performancecounter.NodeUpdateCounter;
import org.opendaylight.netvirt.fcapsapp.performancecounter.PacketInCounterHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetAddress;
import java.util.Collection;

public class NodeEventListener<D extends DataObject> implements ClusteredDataTreeChangeListener<D>,AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NodeEventListener.class);
    public static final AlarmAgent alarmAgent = new AlarmAgent();
    public static final NodeUpdateCounter nodeUpdateCounter = new NodeUpdateCounter();
    public static final PacketInCounterHandler packetInCounter = new PacketInCounterHandler();
    private final EntityOwnershipService entityOwnershipService;

    /**
     * Construcor set EntityOwnershipService
     * @param eos
     */
    public NodeEventListener(final EntityOwnershipService eos) {
        this.entityOwnershipService = eos;
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<D>> changes) {
        for (DataTreeModification<D> change : changes) {
            final InstanceIdentifier<D> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<D> mod = change.getRootNode();
            final InstanceIdentifier<FlowCapableNode> nodeConnIdent =
                    key.firstIdentifierOf(FlowCapableNode.class);
            String nodeId = null, hostName = null;
            try {
                nodeId = getDpnId(String.valueOf(nodeConnIdent.firstKeyOf(Node.class).getId()));
            } catch (Exception ex) {
                LOG.error("Dpn retrieval failed");
                return;
            }

            hostName = System.getenv().get("HOSTNAME");
            if (hostName == null) {
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (Exception e) {
                    LOG.error("Retrieving hostName failed {}", e);
                }
            }
            LOG.debug("retrieved hostname {}", hostName);
            if (nodeId != null) {
                switch (mod.getModificationType()) {
                    case DELETE:
                        LOG.debug("NodeRemoved {} notification is received on host {}", nodeId, hostName);
                        if (nodeUpdateCounter.isDpnConnectedLocal(nodeId)) {
                            alarmAgent.raiseControlPathAlarm(nodeId, hostName);
                            nodeUpdateCounter.nodeRemovedNotification(nodeId, hostName);
                        }
                        packetInCounter.nodeRemovedNotification(nodeId);
                        break;
                    case SUBTREE_MODIFIED:
                        if (isNodeOwner(nodeId)) {
                            LOG.debug("NodeUpdated {} notification is received", nodeId);
                        } else {
                            LOG.debug("UPDATE: Node {} is not connected to host {}", nodeId, hostName);
                        }
                        break;
                    case WRITE:
                        if (mod.getDataBefore() == null) {
                            if (isNodeOwner(nodeId)) {
                                LOG.debug("NodeAdded {} notification is received on host {}", nodeId, hostName);
                                alarmAgent.clearControlPathAlarm(nodeId);
                                nodeUpdateCounter.nodeAddedNotification(nodeId, hostName);
                            } else {
                                LOG.debug("ADD: Node {} is not connected to host {}", nodeId, hostName);
                            }
                        }
                        break;
                    default:
                        LOG.debug("Unhandled Modification type {}", mod.getModificationType());
                        throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());

                }
            } else {
                LOG.error("DpnID is null");
            }
        }
    }

    private String getDpnId(String node) {
        //Uri [_value=openflow:1]
        String temp[] = node.split("=");
        String dpnId = temp[1].substring(0,temp[1].length() - 1);
        return dpnId;

    }

    /**
     * Method checks if *this* instance of controller is owner of
     * the given openflow node.
     * @param nodeId DpnId
     * @return True if owner, else false
     */
    public boolean isNodeOwner(String nodeId) {
        Entity entity = new Entity("openflow", nodeId);
        Optional<EntityOwnershipState> entityState = this.entityOwnershipService.getOwnershipState(entity);
        if (entityState.isPresent()) {
            return entityState.get().isOwner();
        }
        return false;
    }

    @Override
    public void close() throws Exception {
    }
}