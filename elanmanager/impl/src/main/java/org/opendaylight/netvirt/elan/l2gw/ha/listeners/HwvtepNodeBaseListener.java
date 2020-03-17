/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.PreDestroy;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HwvtepNodeBaseListener<D extends Datastore>
    implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeBaseListener.class);
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    private ListenerRegistration<HwvtepNodeBaseListener> registration;
    private final DataBroker dataBroker;
    final ManagedNewTransactionRunner txRunner;
    private final HwvtepNodeHACache hwvtepNodeHACache;
    private final Class<D> datastoreType;
    private final Function<DataObject, String> noLogicalSwitch = (data) -> "No_Ls";

    private final Labeled<Labeled<Labeled<Labeled<Labeled<Meter>>>>> childModCounter;
    private final Labeled<Labeled<Labeled<Meter>>> nodeModCounter;
    private final boolean updateMetrics;

    private static final ImmutableMap<Class, Function<DataObject, String>> LOGICAL_SWITCH_EXTRACTOR =
        new ImmutableMap.Builder<Class, Function<DataObject, String>>()
            .put(LogicalSwitches.class, data -> ((LogicalSwitches) data).getHwvtepNodeName().getValue())
            .put(RemoteMcastMacs.class,
                data -> logicalSwitchNameFromIid(((RemoteMcastMacs) data).key().getLogicalSwitchRef().getValue()))
            .put(RemoteUcastMacs.class, data -> logicalSwitchNameFromIid(
                ((RemoteUcastMacs) data).key().getLogicalSwitchRef().getValue())).build();


    public HwvtepNodeBaseListener(Class<D> datastoreType, DataBroker dataBroker,
                                  HwvtepNodeHACache hwvtepNodeHACache, MetricProvider metricProvider,
                                  boolean updateMetrics) throws Exception {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.datastoreType = datastoreType;
        this.hwvtepNodeHACache = hwvtepNodeHACache;
        this.updateMetrics = updateMetrics;
        this.childModCounter = metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("l2gw").id("child").build(),
                "datastore", "modification", "class", "nodeid", "logicalswitch");
        this.nodeModCounter = metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("l2gw").id("node").build(),
                "datastore", "modification", "nodeid");
        registerListener(datastoreType, dataBroker);
    }

    protected void registerListener(Class<D> dsType, DataBroker broker) throws Exception {
        final DataTreeIdentifier<Node> treeId = DataTreeIdentifier.create(Datastore.toType(dsType),
                getWildcardPath());
        TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
        registration = looper.loopUntilNoException(() ->
                broker.registerDataTreeChangeListener(treeId, HwvtepNodeBaseListener.this));
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
            Managers m1 = up.get(0);
            Managers m2 = be.get(0);
            if (!Objects.equals(m1, m2)) {
                LOG.trace("Manager entry updated for node {} ", updatedChildNode.getNodeId().getValue());
                HwvtepHAUtil.addToCacheIfHAChildNode(childPath, updatedChildNode, hwvtepNodeHACache);
            }
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        HAJobScheduler.getInstance().submitJob(() -> LoggingFutures.addErrorLogging(
            txRunner.callWithNewReadWriteTransactionAndSubmit(datastoreType, tx -> {
                processConnectedNodes(changes, tx);
                processUpdatedNodes(changes, tx);
                processDisconnectedNodes(changes, tx);
            }), LOG, "Error processing data-tree changes"));
    }

    private void processUpdatedNodes(Collection<DataTreeModification<Node>> changes,
                                     TypedReadWriteTransaction<D> tx)
            throws ExecutionException, InterruptedException {
        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            Node updated = HwvtepHAUtil.getUpdated(mod);
            Node original = HwvtepHAUtil.getOriginal(mod);
            updateCounters(nodeId, mod.getModifiedChildren());
            if (updated != null && original != null) {
                DataObjectModification subMod;
                if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                    onGlobalNodeUpdate(key, updated, original, mod, tx);
                    subMod = change.getRootNode().getModifiedAugmentation(HwvtepGlobalAugmentation.class);
                } else {
                    onPsNodeUpdate(updated, mod, tx);
                    subMod = change.getRootNode().getModifiedAugmentation(PhysicalSwitchAugmentation.class);
                }
                if (subMod != null) {
                    updateCounters(nodeId, subMod.getModifiedChildren());
                }
            }
        }
    }

    private String logicalSwitchNameFromChildMod(DataObjectModification<? extends DataObject> childMod) {
        DataObject dataAfter = childMod.getDataAfter();
        DataObject data = dataAfter != null ? dataAfter : childMod.getDataBefore();
        return LOGICAL_SWITCH_EXTRACTOR.getOrDefault(childMod.getModificationType().getClass(), noLogicalSwitch)
                .apply(data);
    }

    private static String logicalSwitchNameFromIid(InstanceIdentifier<?> input) {
        InstanceIdentifier<LogicalSwitches> iid = (InstanceIdentifier<LogicalSwitches>)input;
        return iid.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    private void updateCounters(String nodeId,
                                Collection<? extends DataObjectModification<? extends DataObject>> childModCollection) {
        if (childModCollection == null || !updateMetrics) {
            return;
        }
        childModCollection.forEach(childMod -> {
            String childClsName = childMod.getDataType().getClass().getSimpleName();
            String modificationType = childMod.getModificationType().toString();
            String logicalSwitchName = logicalSwitchNameFromChildMod(childMod);
            childModCounter.label(Datastore.toType(datastoreType).name())
                    .label(modificationType)
                    .label(childClsName)
                    .label(nodeId)
                    .label(logicalSwitchName).mark();
        });
    }

    private void processDisconnectedNodes(Collection<DataTreeModification<Node>> changes,
                                          TypedReadWriteTransaction<D> tx)
            throws InterruptedException, ExecutionException {
        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            Node deleted = HwvtepHAUtil.getRemoved(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (deleted != null) {
                if (updateMetrics) {
                    nodeModCounter.label(Datastore.toType(datastoreType).name())
                            .label(DataObjectModification.ModificationType.DELETE.name()).label(nodeId).mark();
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
                               TypedReadWriteTransaction<D> tx)
            throws ExecutionException, InterruptedException {
        for (DataTreeModification<Node> change : changes) {
            InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            DataObjectModification<Node> mod = change.getRootNode();
            Node node = HwvtepHAUtil.getCreated(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (node != null) {
                if (updateMetrics) {
                    nodeModCounter.label(Datastore.toType(datastoreType).name())
                            .label(DataObjectModification.ModificationType.WRITE.name()).label(nodeId).mark();
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

    private static InstanceIdentifier<Node> getWildcardPath() {
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

    //default methods
    void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added, TypedReadWriteTransaction<D> tx)
        throws ExecutionException, InterruptedException {
    }

    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, TypedReadWriteTransaction<D> tx)
        throws ExecutionException, InterruptedException {

    }

    void onGlobalNodeAdd(InstanceIdentifier<Node> key, Node added, TypedReadWriteTransaction<D> tx) {

    }

    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, TypedReadWriteTransaction<D> tx)
            throws InterruptedException, ExecutionException {

    }

    void onGlobalNodeUpdate(InstanceIdentifier<Node> key, Node updated, Node original,
                            DataObjectModification<Node> mod, TypedReadWriteTransaction<D> tx) {

    }

    void onPsNodeUpdate(Node updated,
                        DataObjectModification<Node> mod, TypedReadWriteTransaction<D> tx) {

    }

}
