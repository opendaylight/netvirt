/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HAOpClusteredListener extends HwvtepNodeBaseListener implements ClusteredDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(HAOpClusteredListener.class);

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();
    private static DataBroker dataBroker;
    private ListenerRegistration<HAOpClusteredListener> registration;
    private final Set<InstanceIdentifier<Node>> connectedNodes = ConcurrentHashMap.newKeySet();
    private final Map<InstanceIdentifier<Node>, Set<Consumer<Optional<Node>>>> waitingJobs = new ConcurrentHashMap<>();

    public HAOpClusteredListener(DataBroker db) throws Exception {
        super(LogicalDatastoreType.OPERATIONAL, db);
        LOG.info("Registering HAOpClusteredListener");
    }

    public Set<InstanceIdentifier<Node>> getConnectedNodes() {
        return connectedNodes;
    }

    @Override
    synchronized  void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx)  {
        connectedNodes.remove(key);
        hwvtepHACache.updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)  {
        connectedNodes.remove(key);
        hwvtepHACache.updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)    {
        connectedNodes.add(key);
        hwvtepHACache.updateConnectedNodeStatus(key);
    }

    @Override
    public synchronized void onGlobalNodeAdd(InstanceIdentifier<Node> key, Node updated, ReadWriteTransaction tx) {
        connectedNodes. add(key);
        addToCacheIfHAChildNode(key, updated);
        hwvtepHACache.updateConnectedNodeStatus(key);
        if (waitingJobs.containsKey(key) && !waitingJobs.get(key).isEmpty()) {
            try {
                HAJobScheduler jobScheduler = HAJobScheduler.getInstance();
                Optional<Node> nodeOptional = tx.read(LogicalDatastoreType.OPERATIONAL, key).checkedGet();
                if (nodeOptional.isPresent()) {
                    waitingJobs.get(key).forEach(
                        (waitingJob) -> jobScheduler.submitJob(() -> waitingJob.accept(nodeOptional)));
                    waitingJobs.get(key).clear();
                } else {
                    LOG.error("Failed to read oper node {}", key);
                }
            } catch (ReadFailedException e) {
                LOG.error("Failed to read oper node {}", key);
            }
        }
    }

    public static void addToCacheIfHAChildNode(InstanceIdentifier<Node> childPath, Node childNode) {
        String haId = HwvtepHAUtil.getHAIdFromManagerOtherConfig(childNode);
        if (!Strings.isNullOrEmpty(haId)) {
            InstanceIdentifier<Node> parentId = HwvtepHAUtil.createInstanceIdentifierFromHAId(haId);
            //HwvtepHAUtil.updateL2GwCacheNodeId(childNode, parentId);
            hwvtepHACache.addChild(parentId, childPath/*child*/);
        }
    }

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childPath,
                            Node updatedChildNode,
                            Node beforeChildNode,
                            ReadWriteTransaction tx) {
        boolean wasHAChild = hwvtepHACache.isHAEnabledDevice(childPath);
        addToHACacheIfBecameHAChild(childPath, updatedChildNode, beforeChildNode);
        boolean isHAChild = hwvtepHACache.isHAEnabledDevice(childPath);


        if (!wasHAChild && isHAChild) {
            LOG.debug(getNodeId(childPath) + " " + "became ha_child");
        } else if (wasHAChild && !isHAChild) {
            LOG.debug(getNodeId(childPath) + " " + "unbecome ha_child");
        }
    }

    static String getNodeId(InstanceIdentifier<Node> key) {
        String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
        int idx = nodeId.indexOf("uuid/");
        if (idx > 0) {
            nodeId = nodeId.substring(idx + "uuid/".length());
        }
        return nodeId;
    }

    /**
     * If Normal non-ha node changes to HA node , its added to HA cache.
     *
     * @param childPath HA child path which got converted to HA node
     * @param updatedChildNode updated Child node
     * @param beforeChildNode non-ha node before updated to HA node
     */
    public static void addToHACacheIfBecameHAChild(InstanceIdentifier<Node> childPath,
                                                   Node updatedChildNode,
                                                   Node beforeChildNode) {
        HwvtepGlobalAugmentation updatedAugmentaion = updatedChildNode.getAugmentation(HwvtepGlobalAugmentation.class);
        HwvtepGlobalAugmentation beforeAugmentaion = null;
        if (beforeChildNode != null) {
            beforeAugmentaion = beforeChildNode.getAugmentation(HwvtepGlobalAugmentation.class);
        }
        List<Managers> up = null;
        List<Managers> be = null;
        if (updatedAugmentaion != null) {
            up = updatedAugmentaion.getManagers();
        }
        if (beforeAugmentaion != null) {
            be = beforeAugmentaion.getManagers();
        }
        if (up != null) {
            if (be != null) {
                if (up.size() > 0) {
                    if (be.size() > 0) {
                        Managers m1 = up.get(0);
                        Managers m2 = be.get(0);
                        if (!m1.equals(m2)) {
                            LOG.trace("Manager entry updated for node {} ", updatedChildNode.getNodeId().getValue());
                            addToCacheIfHAChildNode(childPath, updatedChildNode);
                        }
                    }
                }
            }
            //TODO handle unhaed case
        }
    }

    public Set<InstanceIdentifier<Node>> getConnected(Set<InstanceIdentifier<Node>> candidateds) {
        if (candidateds == null) {
            return Collections.emptySet();
        }
        return candidateds.stream()
                .filter((iid) -> connectedNodes.contains(iid))
                .collect(Collectors.toSet());
    }

    public synchronized void runAfterNodeIsConnected(InstanceIdentifier<Node> iid, Consumer<Optional<Node>> consumer) {
        if (connectedNodes.contains(iid)) {
            ReadWriteTransaction tx = db.newReadWriteTransaction();
            HAJobScheduler.getInstance().submitJob(() -> {
                try {
                    consumer.accept(tx.read(LogicalDatastoreType.OPERATIONAL, iid).checkedGet());
                } catch (ReadFailedException e) {
                    LOG.error("Failed to read oper ds {}", iid);
                }
            });
            return;
        }
        waitingJobs.putIfAbsent(iid, ConcurrentHashMap.newKeySet());
        waitingJobs.get(iid).add(consumer);
    }
}

