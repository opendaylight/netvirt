/*
 * Copyright (c) 2015 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netvirt.openstack.netvirt.AbstractEvent;
import org.opendaylight.netvirt.openstack.netvirt.AbstractHandler;
import org.opendaylight.netvirt.openstack.netvirt.NodeCacheManagerEvent;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.netvirt.openstack.netvirt.api.NodeCacheListener;
import org.opendaylight.netvirt.openstack.netvirt.api.NodeCacheManager;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Flavio Fernandes (ffernand@redhat.com)
 * @author Sam Hague (shague@redhat.com)
 */
public class NodeCacheManagerImpl extends AbstractHandler implements NodeCacheManager {
    private static final Logger LOG = LoggerFactory.getLogger(NodeCacheManagerImpl.class);

    private Map<NodeId, Node> nodeCache = new ConcurrentHashMap<>();
    private Map<Long, NodeCacheListener> handlers = Maps.newHashMap();

    private final Southbound southbound;

    public NodeCacheManagerImpl(final Southbound southbound, final EventDispatcher eventDispatcher) {
        this.southbound = southbound;
        this.eventDispatcher = eventDispatcher;

        this.eventDispatcher.eventHandlerAdded(AbstractEvent.HandlerType.NODE, this);
    }

    @Override
    public void nodeAdded(Node node) {
        LOG.debug("nodeAdded: {}", node);
        enqueueEvent(new NodeCacheManagerEvent(node, Action.UPDATE));
    }

    @Override
    public void nodeRemoved(Node node) {
        LOG.debug("nodeRemoved: {}", node);
        enqueueEvent(new NodeCacheManagerEvent(node, Action.DELETE));
    }

    // TODO SB_MIGRATION
    // might need to break this into two different events
    // notifyOvsdbNode, notifyBridgeNode or just make sure the
    // classes implementing the interface check for ovsdbNode or bridgeNode
    private void processNodeUpdate(Node node) {
        Action action = Action.UPDATE;

        NodeId nodeId = node.getNodeId();
        if (nodeCache.get(nodeId) == null) {
            action = Action.ADD;
        }
        nodeCache.put(nodeId, node);

        LOG.debug("processNodeUpdate: size= {}, Node type= {}, action= {}, node= {}",
                nodeCache.size(),
                southbound.getBridge(node) != null ? "BridgeNode" : "OvsdbNode",
                action == Action.ADD ? "ADD" : "UPDATE",
                node);

        for (NodeCacheListener handler : handlers.values()) {
            try {
                handler.notifyNode(node, action);
            } catch (Exception e) {
                LOG.error("Failed notifying node add event", e);
            }
        }
        LOG.debug("processNodeUpdate returns");
    }

    private void processNodeRemoved(Node node) {
        nodeCache.remove(node.getNodeId());
        for (NodeCacheListener handler : handlers.values()) {
            try {
                handler.notifyNode(node, Action.DELETE);
            } catch (Exception e) {
                LOG.error("Failed notifying node remove event", e);
            }
        }
        LOG.warn("processNodeRemoved returns");
    }

    /**
     * Process the event.
     *
     * @param abstractEvent the {@link AbstractEvent} event to be handled.
     * @see EventDispatcher
     */
    @Override
    public void processEvent(AbstractEvent abstractEvent) {
        if (!(abstractEvent instanceof NodeCacheManagerEvent)) {
            LOG.error("Unable to process abstract event {}", abstractEvent);
            return;
        }
        NodeCacheManagerEvent ev = (NodeCacheManagerEvent) abstractEvent;
        LOG.debug("NodeCacheManagerImpl: dequeue: {}", ev);
        switch (ev.getAction()) {
            case DELETE:
                processNodeRemoved(ev.getNode());
                break;
            case UPDATE:
                processNodeUpdate(ev.getNode());
                break;
            default:
                LOG.warn("Unable to process event action {}", ev.getAction());
                break;
        }
    }

    @Override
    public Map<NodeId,Node> getOvsdbNodes() {
        Map<NodeId,Node> ovsdbNodesMap = new ConcurrentHashMap<>();
        for (Map.Entry<NodeId, Node> ovsdbNodeEntry : nodeCache.entrySet()) {
            if (southbound.extractOvsdbNode(ovsdbNodeEntry.getValue()) != null) {
                ovsdbNodesMap.put(ovsdbNodeEntry.getKey(), ovsdbNodeEntry.getValue());
            }
        }
        return ovsdbNodesMap;
    }

    @Override
    public List<Node> getBridgeNodes() {
        List<Node> nodes = Lists.newArrayList();
        for (Node node : nodeCache.values()) {
            if (southbound.getBridge(node) != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public List <Long> getBridgeDpids(final String bridgeName) {
        List<Long> dpids = Lists.newArrayList();
        for (Node node : nodeCache.values()) {
            if (bridgeName == null || southbound.getBridge(node, bridgeName) != null) {
                long dpid = southbound.getDataPathId(node);
                if (dpid != 0L) {
                    dpids.add(Long.valueOf(dpid));
                }
            }
        }
        return dpids;
    }

    @Override
    public List<Node> getNodes() {
        List<Node> nodes = Lists.newArrayList();
        for (Node node : nodeCache.values()) {
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Method called by blueprint
     */
    public void init() {
        LOG.debug("populateNodeCache : Populating the node cache");
        List<Node> nodes = southbound.readOvsdbTopologyNodes();
        for(Node ovsdbNode : nodes) {
            this.nodeCache.put(ovsdbNode.getNodeId(), ovsdbNode);
        }
        nodes = southbound.readOvsdbTopologyBridgeNodes();
        for(Node bridgeNode : nodes) {
            this.nodeCache.put(bridgeNode.getNodeId(), bridgeNode);
        }
        LOG.debug("populateNodeCache : Node cache population is done. Total nodes : {}",this.nodeCache.size());
    }
}
