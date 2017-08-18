/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HwvtepNodeBaseListener implements DataTreeChangeListener<Node>, AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeBaseListener.class);
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    static HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    private ListenerRegistration<HwvtepNodeBaseListener> registration;
    protected final DataBroker db;
    private final LogicalDatastoreType logicalDatastoreType;

    public HwvtepNodeBaseListener(LogicalDatastoreType datastoreType, DataBroker dataBroker) {
        db = dataBroker;
        this.logicalDatastoreType = datastoreType;
    }

    public void init() throws Exception {
        registerListener(logicalDatastoreType, db);
    }

    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) throws Exception {
        final DataTreeIdentifier<Node> treeId = new DataTreeIdentifier<>(dsType, getWildcardPath());
        TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
        registration = looper.loopUntilNoException(() ->
                db.registerDataTreeChangeListener(treeId, HwvtepNodeBaseListener.this));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        HAJobScheduler.getInstance().submitJob(() -> {
            ReadWriteTransaction tx = getTx();
            try {
                extractUpdatedData(changes, (BatchedTransaction) tx);
                processConnectedNodes(changes, tx);
                processUpdatedNodes(changes, tx);
                processDisconnectedNodes(changes, tx);
                tx.submit().get();
            } catch (InterruptedException e1) {
                LOG.error("InterruptedException " + e1.getMessage());
            } catch (ExecutionException e2) {
                LOG.error("ExecutionException" + e2.getMessage());
            } catch (ReadFailedException e3) {
                LOG.error("ReadFailedException" + e3.getMessage());
            }
        });
    }

    private void extractDataChanged(final InstanceIdentifier<Node> key,
                                    final DataObjectModification<Node> mod,
                                    final Map<Class<? extends Identifiable>, List<Identifiable>> updatedData,
                                    final Map<Class<? extends Identifiable>, List<Identifiable>> deletedData) {

        extractDataChanged(key, mod.getModifiedChildren(), updatedData, deletedData);
        DataObjectModification<HwvtepGlobalAugmentation> aug = mod.getModifiedAugmentation(
                HwvtepGlobalAugmentation.class);
        if (aug != null && getModificationType(aug) != null) {
            extractDataChanged(key, aug.getModifiedChildren(), updatedData, deletedData);
        }
        DataObjectModification<PhysicalSwitchAugmentation> psAug = mod.getModifiedAugmentation(
                PhysicalSwitchAugmentation.class);
        if (psAug != null && getModificationType(psAug) != null) {
            extractDataChanged(key, psAug.getModifiedChildren(), updatedData, deletedData);
        }
    }

    private void extractDataChanged(final InstanceIdentifier<Node> key,
                                    final Collection<DataObjectModification<? extends DataObject>> children,
                                    final Map<Class<? extends Identifiable>, List<Identifiable>> updatedData,
                                    final Map<Class<? extends Identifiable>, List<Identifiable>> deletedData) {
        if (children == null) {
            return;
        }
        String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
        for (DataObjectModification<? extends DataObject> child : children) {
            DataObjectModification.ModificationType type = getModificationType(child);
            if (type == null) {
                continue;
            }
            InstanceIdentifier instanceIdentifier = null;
            Class<? extends Identifiable> childClass = (Class<? extends Identifiable>) child.getDataType();
            InstanceIdentifier.PathArgument pathArgument = child.getIdentifier();
            switch (type) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    DataObject dataAfter = child.getDataAfter();
                    if (!(dataAfter instanceof Identifiable)) {
                        continue;
                    }
                    DataObject before = child.getDataBefore();
                    //if (Objects.equals(dataAfter, before)) {
                        /*
                        in cluster reboot scenarios,
                        application rewrites the data tx.put( logicalswitchiid, logicalswitch )
                        that time it fires the update again ignoring such updates here
                         */
                        //continue;
                    //}
                    Identifiable identifiable = (Identifiable) dataAfter;
                    addToUpdatedData(updatedData, childClass, identifiable);
                    break;
                case DELETE:
                    DataObject dataBefore = child.getDataBefore();
                    if (!(dataBefore instanceof Identifiable)) {
                        continue;
                    }
                    addToUpdatedData(deletedData, childClass, (Identifiable)dataBefore);
                    break;
                default:
                    break;
            }
        }
    }

    private void addToUpdatedData(Map<Class<? extends Identifiable>, List<Identifiable>> updatedData,
                                  Class<? extends Identifiable> childClass, Identifiable identifiable) {
        if (!updatedData.containsKey(childClass)) {
            updatedData.put(childClass, new ArrayList<>());
        }
        updatedData.get(childClass).add(identifiable);
    }

    private DataObjectModification.ModificationType getModificationType(
            DataObjectModification<? extends DataObject> mod) {
        try {
            return mod.getModificationType();
        } catch (IllegalStateException e) {
            //not sure why this getter throws this exception, could be some mdsal bug
            //LOG.warn("Failed to get the modification type for mod {}", mod);
        }
        return null;
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
            if (updated != null && original != null) {
                if (updated != null && original != null) {
                    if (!nodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
                        onGlobalNodeUpdate(key, updated, original, tx);
                    } else {
                        onPsNodeUpdate(key, updated, original, tx);
                    }
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

    void extractUpdatedData(Collection<DataTreeModification<Node>> changes, BatchedTransaction tx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        Map<String, Boolean> processedNodes = new HashMap<>();
        for (DataTreeModification<Node> change : changes) {
            InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            DataObjectModification<Node> mod = change.getRootNode();
            Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = new HashMap<>();
            Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = new HashMap<>();
            extractDataChanged(key, mod, updatedData, deletedData);
            tx.setUpdatedData(updatedData);
            tx.setDeletedData(deletedData);
        }
    }

    void processConnectedNodes(Collection<DataTreeModification<Node>> changes,
                               ReadWriteTransaction tx)
            throws ReadFailedException, ExecutionException,
        InterruptedException {
        Map<String, Boolean> processedNodes = new HashMap<>();
        for (DataTreeModification<Node> change : changes) {
            InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            DataObjectModification<Node> mod = change.getRootNode();
            final Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = new HashMap<>();
            final Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = new HashMap<>();
            extractDataChanged(key, mod, updatedData, deletedData);

            Node node = HwvtepHAUtil.getCreated(mod);
            String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
            if (node != null) {
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
    public void close() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }

    ReadWriteTransaction getTx() {
        return new BatchedTransaction(db);
    }

    //default methods
    void onGlobalNodeDelete(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
    }

    void onPsNodeDelete(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)
            throws ReadFailedException {

    }

    void onGlobalNodeAdd(InstanceIdentifier<Node> key, Node added, ReadWriteTransaction tx) {

    }

    void onPsNodeAdd(InstanceIdentifier<Node> key, Node addedPSNode, ReadWriteTransaction tx)
            throws ReadFailedException, InterruptedException, ExecutionException {

    }

    void onGlobalNodeUpdate(InstanceIdentifier<Node> key, Node updated, Node original, ReadWriteTransaction tx)
            throws ReadFailedException, InterruptedException, ExecutionException {

    }

    void onPsNodeUpdate(InstanceIdentifier<Node> key, Node updated, Node original, ReadWriteTransaction tx)
            throws ReadFailedException, InterruptedException, ExecutionException {

    }

}
