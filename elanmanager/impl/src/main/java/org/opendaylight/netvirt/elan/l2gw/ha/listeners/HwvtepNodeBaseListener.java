/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HwvtepNodeBaseListener<D extends Datastore> implements
    DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeBaseListener.class);
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    private ListenerRegistration<HwvtepNodeBaseListener> registration;
    private final DataBroker dataBroker;
    private final Class<D> datastoreType;
    protected final ManagedNewTransactionRunner txRunner;


    public HwvtepNodeBaseListener(Class<D> datastoreType, DataBroker dataBroker) throws Exception {
        this.dataBroker = dataBroker;
        this.datastoreType = datastoreType;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
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

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        // Batch Transaction used to internally submit to ResourceBatching Manager here
        HAJobScheduler.getInstance().submitJob(() -> {
            TypedReadWriteTransaction tx = getTx();
            try {
                processConnectedNodes(changes, tx);
                processUpdatedNodes(changes, tx);
                processDisconnectedNodes(changes, tx);
                //tx.submit().get();
            } catch (InterruptedException | ExecutionException | ReadFailedException e) {
                LOG.error("Error processing data-tree changes", e);
            }
        });
    }

    @SuppressWarnings("illegalcatch")
    private void processUpdatedNodes(Collection<DataTreeModification<Node>> changes,
                        TypedReadWriteTransaction<D> tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            Node updated = HwvtepHAUtil.getUpdated(mod);
            Node original = HwvtepHAUtil.getOriginal(mod);
            try {
                if (updated != null && original != null) {
                    if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                        onGlobalNodeUpdate(key, updated, original, mod, tx);
                    } else {
                        onPsNodeUpdate(updated, mod, tx);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception during Processing Updated Node {}", nodeId, e);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void processDisconnectedNodes(Collection<DataTreeModification<Node>> changes,
                                            TypedReadWriteTransaction<D> tx)
            throws InterruptedException, ExecutionException, ReadFailedException {

        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            Node deleted = HwvtepHAUtil.getRemoved(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (deleted != null) {
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

    @SuppressWarnings("checkstyle:IllegalCatch")
    void processConnectedNodes(Collection<DataTreeModification<Node>> changes,
                                TypedReadWriteTransaction<D> tx) {
        for (DataTreeModification<Node> change : changes) {

            InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            DataObjectModification<Node> mod = change.getRootNode();
            Node node = HwvtepHAUtil.getCreated(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            try {
                if (node != null) {
                    if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                        LOG.trace("Handle global node add {}", node.getNodeId().getValue());
                        onGlobalNodeAdd(key, node, tx);
                    } else {
                        LOG.trace("Handle ps node add {}", node.getNodeId().getValue());
                        onPsNodeAdd(key, node, tx);
                    }
                }
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("Exception during Processing Connected Node {}", nodeId, e);
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

    TypedReadWriteTransaction<D> getTx() {
        return new BatchedTransaction(datastoreType);
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
