/*
 * Copyright (c) 2016 ,2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HAOpClusteredListener extends HwvtepNodeBaseListener implements ClusteredDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(HAOpClusteredListener.class);

    private final Set<InstanceIdentifier<Node>> connectedNodes = ConcurrentHashMap.newKeySet();
    private final Map<InstanceIdentifier<Node>, Set<Consumer<Optional<Node>>>> waitingJobs = new ConcurrentHashMap<>();

    @Inject
    public HAOpClusteredListener(DataBroker db, HwvtepNodeHACache hwvtepNodeHACache,
                                 MetricProvider metricProvider) throws Exception {
        super(LogicalDatastoreType.OPERATIONAL, db, hwvtepNodeHACache, metricProvider, false);
        LOG.info("Registering HAOpClusteredListener");
    }

    public Set<InstanceIdentifier<Node>> getConnectedNodes() {
        return connectedNodes;
    }

    @Override
    synchronized  void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx)  {
        connectedNodes.remove(key);
        getHwvtepNodeHACache().updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)  {
        connectedNodes.remove(key);
        getHwvtepNodeHACache().updateDisconnectedNodeStatus(key);
    }

    @Override
    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)    {
        connectedNodes.add(key);
        getHwvtepNodeHACache().updateConnectedNodeStatus(key);
    }

    @Override
    public synchronized void onGlobalNodeAdd(InstanceIdentifier<Node> key, Node updated, ReadWriteTransaction tx) {
        connectedNodes. add(key);
        HwvtepHAUtil.addToCacheIfHAChildNode(key, updated, getHwvtepNodeHACache());
        getHwvtepNodeHACache().updateConnectedNodeStatus(key);
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

    @Override
    void onGlobalNodeUpdate(InstanceIdentifier<Node> childPath,
                            Node updatedChildNode,
                            Node beforeChildNode,
                            DataObjectModification<Node> mod,
                            ReadWriteTransaction tx) {
        boolean wasHAChild = getHwvtepNodeHACache().isHAEnabledDevice(childPath);
        addToHACacheIfBecameHAChild(childPath, updatedChildNode, beforeChildNode);
        boolean isHAChild = getHwvtepNodeHACache().isHAEnabledDevice(childPath);


        if (!wasHAChild && isHAChild) {
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
                try (ReadOnlyTransaction tx = getDataBroker().newReadOnlyTransaction()) {
                    consumer.accept(tx.read(LogicalDatastoreType.OPERATIONAL, iid).checkedGet());
                } catch (ReadFailedException e) {
                    LOG.error("Failed to read oper ds {}", iid);
                }
            });
        } else {
            waitingJobs.computeIfAbsent(iid, key -> Sets.newConcurrentHashSet()).add(consumer);
        }
    }
}

