/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.metrics.Counter;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HwvtepNodeBaseListener implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeBaseListener.class);
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    private final ListenerRegistration<HwvtepNodeBaseListener> registration;
    private final DataBroker dataBroker;
    private final HwvtepNodeHACache hwvtepNodeHACache;
    private final MetricProvider metricProvider;
    private final LogicalDatastoreType datastoreType;

    Labeled<Labeled<Labeled<Labeled<Counter>>>> childModCounter;//config.add.remoteUcast.nodeid
    Labeled<Labeled<Labeled<Counter>>> nodeModCounter;//config.add.nodeid
    private final boolean updateCounter;

    public HwvtepNodeBaseListener(LogicalDatastoreType datastoreType, DataBroker dataBroker,
                                  HwvtepNodeHACache hwvtepNodeHACache, MetricProvider metricProvider,
                                  boolean updateCounter) throws Exception {
        this.dataBroker = dataBroker;
        this.datastoreType = datastoreType;
        this.hwvtepNodeHACache = hwvtepNodeHACache;
        this.metricProvider = metricProvider;
        this.updateCounter = updateCounter;
        this.childModCounter = metricProvider.newCounter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("l2gw").id("child").build(),
                "datastore", "modification",  "class", "nodeid");
        this.nodeModCounter = metricProvider.newCounter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("l2gw").id("node").build(),
                "datastore", "modification", "nodeid");
        final DataTreeIdentifier<Node> treeId = new DataTreeIdentifier<>(datastoreType, getWildcardPath());
        TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
        registration = looper.loopUntilNoException(() ->
            dataBroker.registerDataTreeChangeListener(treeId, HwvtepNodeBaseListener.this));
    }

    protected DataBroker getDataBroker() {
        return dataBroker;
    }

    protected HwvtepNodeHACache getHwvtepNodeHACache() {
        return hwvtepNodeHACache;
    }

    /**
     * If Normal non-ha node changes to HA node , its added to HA cache.
     *
     * @param childPath HA child path which got converted to HA node
     * @param updatedChildNode updated Child node
     * @param beforeChildNode non-ha node before updated to HA node
     */
    protected void addToHACacheIfBecameHAChild(InstanceIdentifier<Node> childPath, Node updatedChildNode,
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

        if (up != null && be != null && up.size() > 0 && be.size() > 0) {
            Managers m1 = up.get(0);
            Managers m2 = be.get(0);
            if (!m1.equals(m2)) {
                LOG.trace("Manager entry updated for node {} ", updatedChildNode.getNodeId().getValue());
                HwvtepHAUtil.addToCacheIfHAChildNode(childPath, updatedChildNode, hwvtepNodeHACache);
            }
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        HAJobScheduler.getInstance().submitJob(() -> {
            ReadWriteTransaction tx = getTx();
            try {
                processConnectedNodes(changes, tx);
                processUpdatedNodes(changes, tx);
                processDisconnectedNodes(changes, tx);
                tx.submit().get();
            } catch (InterruptedException | ExecutionException | ReadFailedException e) {
                LOG.error("Error processing data-tree changes", e);
            }
        });
    }

    private void processUpdatedNodes(Collection<DataTreeModification<Node>> changes,
                                     ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            Node updated = HwvtepHAUtil.getUpdated(mod);
            Node original = HwvtepHAUtil.getOriginal(mod);
            Collection<DataObjectModification<? extends DataObject>> childModCollection = null;
            DataObjectModification subMod = null;
            if (updated != null && original != null) {
                if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                    onGlobalNodeUpdate(key, updated, original, mod, tx);
                    subMod = change.getRootNode().getModifiedAugmentation(HwvtepGlobalAugmentation.class);
                } else {
                    onPsNodeUpdate(updated, original, mod, tx);
                    subMod = change.getRootNode().getModifiedAugmentation(PhysicalSwitchAugmentation.class);
                }
                if (subMod != null) {
                    childModCollection = subMod.getModifiedChildren();
                }
                if (childModCollection != null) {
                    childModCollection.forEach(childMod -> {
                        String childClsName = childMod.getDataType().getClass().getSimpleName();
                        String modificationType = childMod.getModificationType().toString();
                        if (updateCounter) {
                            childModCounter.label(datastoreType.name())
                                    .label(modificationType)
                                    .label(childClsName)
                                    .label(nodeId);
                        }
                    });
                }
            }
        }
    }

    private void processDisconnectedNodes(Collection<DataTreeModification<Node>> changes,
                                          ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {

        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            Node deleted = HwvtepHAUtil.getRemoved(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (deleted != null) {
                if (updateCounter) {
                    nodeModCounter.label(datastoreType.name()).label("delete").label(nodeId).increment();
                }
                if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                    LOG.trace("Handle global node delete {}", deleted.getNodeId().getValue());
                    onGlobalNodeDelete(key, deleted, tx);
                } else {
                    LOG.trace("Handle ps node node delete {}", deleted.getNodeId().getValue());
                    onPsNodeDelete(key, deleted, tx);
                }
            }
        }
    }

    void processConnectedNodes(Collection<DataTreeModification<Node>> changes,
                               ReadWriteTransaction tx)
            throws ReadFailedException {
        for (DataTreeModification<Node> change : changes) {
            InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            DataObjectModification<Node> mod = change.getRootNode();
            Node node = HwvtepHAUtil.getCreated(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (node != null) {
                if (updateCounter) {
                    nodeModCounter.label(datastoreType.name()).label("add").label(nodeId).increment();
                }
                if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                    LOG.trace("Handle global node add {}", node.getNodeId().getValue());
                    onGlobalNodeAdd(key, node, tx);
                } else {
                    LOG.trace("Handle ps node add {}", node.getNodeId().getValue());
                    onPsNodeAdd(key, node, tx);
                }
            }
        }
    }

    private InstanceIdentifier<Node> getWildcardPath() {
        InstanceIdentifier<Node> path = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class);
        return path;
    }

    @Override
    @PreDestroy
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }

    ReadWriteTransaction getTx() {
        return new BatchedTransaction();
    }

    //default methods
    void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx)
            throws ReadFailedException {
    }

    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)
            throws ReadFailedException {

    }

    void onGlobalNodeAdd(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx) {

    }

    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)
            throws ReadFailedException {

    }

    void onGlobalNodeUpdate(InstanceIdentifier<Node> key, Node updated, Node original,
                            DataObjectModification<Node> mod, ReadWriteTransaction tx)
            throws ReadFailedException, InterruptedException, ExecutionException {

    }

    void onPsNodeUpdate(Node updated, Node original,
                        DataObjectModification<Node> mod, ReadWriteTransaction tx)
            throws ReadFailedException, InterruptedException, ExecutionException {

    }

}
