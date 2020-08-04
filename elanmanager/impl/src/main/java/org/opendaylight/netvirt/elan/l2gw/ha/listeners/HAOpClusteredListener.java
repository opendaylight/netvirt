/*
 * Copyright (c) 2016 ,2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HAOpClusteredListener extends HwvtepNodeBaseListener<Operational> implements
        ClusteredDataTreeChangeListener<Node>, RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(HAOpClusteredListener.class);
    private final Set<InstanceIdentifier<Node>> connectedNodes = ConcurrentHashMap.newKeySet();
    private final Map<InstanceIdentifier<Node>, Set<Consumer<Optional<Node>>>> waitingJobs = new ConcurrentHashMap<>();
    private final IdManagerService idManager;

    @Inject
    public HAOpClusteredListener(DataBroker db,
                                 final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                 final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                 final IdManagerService idManager) throws Exception {
        super(OPERATIONAL, db);
        this.idManager = idManager;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(db);
    }

    public Set<InstanceIdentifier<Node>> getConnectedNodes() {
        return connectedNodes;
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering HAOpClusteredListener");
            registerListener(OPERATIONAL, getDataBroker());
        } catch (Exception e) {
            LOG.error("HA OP Clustered register listener error.", e);
        }

    }

    public void deregisterListener() {
        LOG.info("Deregistering HAOpClusteredListener");
        super.close();
    }

    @Override
    synchronized  void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added,
            TypedReadWriteTransaction<Operational> tx)  {
        connectedNodes.remove(key);
        hwvtepHACache.updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, TypedReadWriteTransaction<Operational> tx)  {
        connectedNodes.remove(key);
        hwvtepHACache.updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, TypedReadWriteTransaction<Operational> tx)    {
        connectedNodes.add(key);
        hwvtepHACache.updateConnectedNodeStatus(key);
    }

    @Override
    public synchronized void onGlobalNodeAdd(InstanceIdentifier<Node> key,
        Node updated, TypedReadWriteTransaction<Operational> tx) {
        connectedNodes.add(key);
        addToCacheIfHAChildNode(key, updated);
        hwvtepHACache.updateConnectedNodeStatus(key);
        if (waitingJobs.containsKey(key) && !waitingJobs.get(key).isEmpty()) {
            try {
                HAJobScheduler jobScheduler = HAJobScheduler.getInstance();
                Optional<Node> nodeOptional = tx.read(key).get();
                if (nodeOptional.isPresent()) {
                    waitingJobs.get(key).forEach(
                        (waitingJob) -> jobScheduler.submitJob(() -> waitingJob.accept(nodeOptional)));
                    waitingJobs.get(key).clear();
                    hwvtepHACache.addDebugEvent(new MdsalEvent("Waiting jobs of node are run ", getNodeId(key)));
                } else {
                    LOG.error("Failed to read oper node {}", key);
                }
            } catch (InterruptedException | ExecutionException e) {
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
                            TypedReadWriteTransaction<Operational> tx) {
        boolean wasHAChild = hwvtepHACache.isHAEnabledDevice(childPath);
        addToHACacheIfBecameHAChild(childPath, updatedChildNode, beforeChildNode);
        boolean isHAChild = hwvtepHACache.isHAEnabledDevice(childPath);


        if (!wasHAChild && isHAChild) {
            hwvtepHACache.addDebugEvent(new MdsalEvent(getNodeId(childPath), "became ha child"));
            LOG.debug("{} became ha_child", getNodeId(childPath));
        } else if (wasHAChild && !isHAChild) {
            LOG.debug("{} unbecome ha_child", getNodeId(childPath));
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
        Collection<Managers> up = null;
        Collection<Managers> be = null;
        if (updatedAugmentaion != null) {
            up = updatedAugmentaion.nonnullManagers().values();
        }
        if (beforeAugmentaion != null) {
            be = beforeAugmentaion.nonnullManagers().values();
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

    public synchronized void runAfterNodeIsConnected(InstanceIdentifier<Node> iid, Consumer<Optional<Node>> consumer) {
        if (connectedNodes.contains(iid)) {
            HAJobScheduler.getInstance().submitJob(() -> {
                txRunner.callWithNewReadOnlyTransactionAndClose(OPERATIONAL, tx -> {
                    try {
                        consumer.accept(tx.read(iid).get());
                    } catch (ExecutionException | InterruptedException e) {
                        LOG.error("Failed job run after node {}", iid);
                    }
                });
            });
        } else {
            waitingJobs.computeIfAbsent(iid, key -> Sets.newConcurrentHashSet()).add(consumer);
        }
        hwvtepHACache.addDebugEvent(new MdsalEvent("Job waiting for ", getNodeId(iid)));
    }
}

