/*
 * Copyright (c) 2016 ,2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.MdsalEvent;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAOpClusteredListener extends HwvtepNodeBaseListener implements
        ClusteredDataTreeChangeListener<Node>, RecoverableListener {

    private final Set<InstanceIdentifier<Node>> connectedNodes = ConcurrentHashMap.newKeySet();
    private final Map<InstanceIdentifier<Node>, Set<Consumer<Optional<Node>>>> waitingJobs = new ConcurrentHashMap<>();
    private final IdManagerService idManager;

    @Inject
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    public HAOpClusteredListener(DataBroker db,
                                 final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                 final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                 final IdManagerService idManager) throws Exception {
        super(db);
        this.idManager = idManager;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(db);
    }

    @PostConstruct
    public void init() throws Exception {
        registerListener();
    }


    public Set<InstanceIdentifier<Node>> getConnectedNodes() {
        return connectedNodes;
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAOpClusteredListener");
            registerListener(LogicalDatastoreType.OPERATIONAL, getDataBroker(), HAOpClusteredListener.this);
        } catch (Exception e) {
            LOG.error("HA OP Clustered register listener error.", e);
        }

    }

    public void deregisterListener() {
        LOG.info("Deregistering HAOpClusteredListener");
        super.close();
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
        connectedNodes.add(key);
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
                    hwvtepHACache.addDebugEvent(new MdsalEvent("Waiting jobs of node are run ", getNodeId(key)));
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
                            DataObjectModification<Node> mod,
                            ReadWriteTransaction tx) {
        boolean wasHAChild = hwvtepHACache.isHAEnabledDevice(childPath);
        addToHACacheIfBecameHAChild(childPath, updatedChildNode, beforeChildNode);
        boolean isHAChild = hwvtepHACache.isHAEnabledDevice(childPath);


        if (!wasHAChild && isHAChild) {
            hwvtepHACache.addDebugEvent(new MdsalEvent(getNodeId(childPath), "became ha child"));
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
        HwvtepGlobalAugmentation updatedAugmentaion = updatedChildNode.augmentation(HwvtepGlobalAugmentation.class);
        HwvtepGlobalAugmentation beforeAugmentaion = null;
        if (beforeChildNode != null) {
            beforeAugmentaion = beforeChildNode.augmentation(HwvtepGlobalAugmentation.class);
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
            if (!Objects.equals(up, be)) {
                LOG.info("Manager entry updated for node {} ", updatedChildNode.getNodeId().getValue());
                addToCacheIfHAChildNode(childPath, updatedChildNode);
            }
            //TODO handle unhaed case
        }
    }

    public Set<InstanceIdentifier<Node>> getConnected(Set<InstanceIdentifier<Node>> candidateds) {
        if (candidateds == null) {
            return Collections.emptySet();
        }
        return candidateds.stream()
                .filter(connectedNodes::contains)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized void runAfterNodeIsConnected(InstanceIdentifier<Node> iid, Consumer<Optional<Node>> consumer) {
        if (connectedNodes.contains(iid)) {
            HAJobScheduler.getInstance().submitJob(() -> {
                try (ReadOnlyTransaction tx = getDataBroker().newReadOnlyTransaction()) {
                    consumer.accept(tx.read(LogicalDatastoreType.OPERATIONAL, iid).checkedGet());
                } catch (Exception e) {
                    LOG.error("Failed job run after node {}", iid);
                }
            });
        } else {
            waitingJobs.computeIfAbsent(iid, key -> Sets.newConcurrentHashSet()).add(consumer);
        }
        hwvtepHACache.addDebugEvent(new MdsalEvent("Job waiting for ", getNodeId(iid)));
    }
}

