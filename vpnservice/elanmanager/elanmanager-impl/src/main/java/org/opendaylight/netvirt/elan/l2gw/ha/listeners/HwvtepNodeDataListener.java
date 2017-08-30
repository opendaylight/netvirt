/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.ha.BatchedTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
import org.opendaylight.netvirt.elan.l2gw.listeners.ChildListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for the node children data updates and propagates the updates between ha child and parent nodes.
 * When an operational child node data is updated, it is copied to parent
 * When a config parent node data is updated , it is copied to all its children.
 */
public abstract class HwvtepNodeDataListener<T extends DataObject>
        extends ChildListener<Node, T, String> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeDataListener.class);

    private final DataBroker broker;
    protected final MergeCommand mergeCommand;
    private final ResourceBatchingManager.ShardResource datastoreType;
    private final String dataTypeName;
    private final int maxRetries = 10;
    protected final Class<T> clazz;

    public HwvtepNodeDataListener(DataBroker broker,
                                  Class<T> clazz,
                                  Class<HwvtepNodeDataListener<T>> eventClazz,
                                  MergeCommand mergeCommand,
                                  ResourceBatchingManager.ShardResource datastoreType) throws Exception {
        super(broker, false);
        this.broker = broker;
        this.clazz = clazz;
        this.mergeCommand = mergeCommand;
        this.datastoreType = datastoreType;
        this.dataTypeName = getClassTypeName();
        init();
    }

    public void init() throws Exception {
        registerListener(this.datastoreType.getDatastoreType() , getParentWildCardPath());
    }

    @Override
    protected InstanceIdentifier<Node> getParentWildCardPath() {
        return HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()
                .child(Node.class);
    }

    @Override
    protected boolean proceed(final InstanceIdentifier<Node> parent) {
        return true;
    }

    @Override
    protected String getGroup(final InstanceIdentifier<T> childIid,
                              final T localUcastMacs) {
        return "NO_GROUP";
    }

    @Override
    protected void onUpdate(final Map<String, Map<InstanceIdentifier, T>> updatedMacsGrouped,
                            final Map<String, Map<InstanceIdentifier, T>> deletedMacsGrouped) {
        HAJobScheduler.getInstance().submitJob(() -> {
            BatchedTransaction tx = new BatchedTransaction(dataBroker);
            updatedMacsGrouped.entrySet().forEach((entry) -> {
                entry.getValue().entrySet().forEach((entry2) -> {
                    add(entry2.getKey(), entry2.getValue(), tx);
                });
            });
            deletedMacsGrouped.entrySet().forEach((entry) -> {
                entry.getValue().entrySet().forEach((entry2) -> {
                    remove(entry2.getKey(), entry2.getValue(), tx);
                });
            });
        });
    }

    abstract Collection<DataObjectModification<? extends DataObject>> getChildMod2(
            InstanceIdentifier<Node> parentIid,
            DataObjectModification<Node> mod);

    @Override
    protected Map<InstanceIdentifier<T>, DataObjectModification<T>> getChildMod(
            final InstanceIdentifier<Node> parentIid,
            final DataObjectModification<Node> mod) {

        Map<InstanceIdentifier<T>, DataObjectModification<T>> result = new HashMap<>();
        Collection<DataObjectModification<? extends DataObject>> children = getChildMod2(parentIid, mod);
        if (children != null) {
            children.stream()
                    .filter(childMod -> getModificationType(childMod) != null)
                    .filter(childMod -> childMod.getDataType() == clazz)
                    .forEach(childMod -> {
                        T afterMac = (T) childMod.getDataAfter();
                        T mac = afterMac != null ? afterMac : (T)childMod.getDataBefore();
                        InstanceIdentifier<T> iid = getIid(parentIid, mac);
                        result.put(iid, (DataObjectModification<T>) childMod);
                    });
        }
        return result;
    }

    abstract InstanceIdentifier<T> getIid(InstanceIdentifier<Node> parentIid, T object);

    @Override
    protected void onParentAdded(final DataTreeModification<Node> modification) {
    }

    protected void onParentRemoved(InstanceIdentifier<Node> parent) {
        LOG.trace("on parent removed {}", parent);
    }

    protected void add(InstanceIdentifier<T> identifier, T dataAdded, BatchedTransaction tx) {
        try {
            boolean create = true;
            if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                copyToParent(identifier, dataAdded, create, tx);
            } else {
                copyToChild(identifier, dataAdded, create, tx);
            }
        } catch (ReadFailedException e) {
            LOG.error("Exception caught while writing ", e.getMessage());
        }
    }

    protected void remove(InstanceIdentifier<T> identifier,
                          T dataRemoved,
                          BatchedTransaction tx) {
        if (clazz == RemoteUcastMacs.class && LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
            LOG.trace("Skipping remote ucast macs to parent");
            return;
        }
        try {
            String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
            boolean create = false;
            if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                copyToParent(identifier, dataRemoved, create, tx);
            } else {
                copyToChild(identifier, dataRemoved, create, tx);
            }
        } catch (ReadFailedException e) {
            LOG.error("Exception caught while writing ", e.getMessage());
        }
    }

    <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }

    void copyToParent(InstanceIdentifier<T> identifier, T data, boolean create,
                      ReadWriteTransaction tx) throws ReadFailedException {
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent == null) {
            return;
        }
        LOG.trace("Copy child op data {} to parent {} create:{}", mergeCommand.getDescription(),
                getNodeId(parent), create);
        data = (T) mergeCommand.transform(parent, data);
        identifier = mergeCommand.generateId(parent, data);
        writeToMdsal(create, tx, data, identifier, false, maxRetries);
        tx.submit();
    }

    void copyToChild(final InstanceIdentifier<T> parentIdentifier,final T parentData,
                     final boolean create,final ReadWriteTransaction tx)
            throws ReadFailedException {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(parentIdentifier);
        if (children == null) {
            return;
        }
        for (InstanceIdentifier<Node> child : children) {
            LOG.trace("Copy parent config data {} to child {} create:{} ", mergeCommand.getDescription(),
                    getNodeId(child), create);
            final T childData = (T) mergeCommand.transform(child, parentData);
            final InstanceIdentifier<T> identifier = mergeCommand.generateId(child, childData);
            writeToMdsal(create, tx, childData, identifier, true, maxRetries);
        }
        tx.submit();
    }

    private void writeToMdsal(final boolean create,
                              final ReadWriteTransaction tx,
                              final T data,
                              final InstanceIdentifier<T> identifier,
                              final boolean copyToChild,
                              final int trialNo) {
        String destination = copyToChild ? "child" : "parent";
        String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        InstanceIdentifier<Node> iid = identifier.firstIdentifierOf(Node.class);

        if (trialNo == 0) {
            LOG.error("All trials exceed for tx iid {}", identifier);
            return;
        }
        final int trial = trialNo - 1;
        if (BatchedTransaction.isInProgress(datastoreType.getDatastoreType(), identifier)) {
            if (BatchedTransaction.addCallbackIfInProgress(datastoreType.getDatastoreType(), identifier,
                () -> writeToMdsal(create, broker.newReadWriteTransaction(), data, identifier, copyToChild, trial))) {
                return;
            }
        }
        Optional<T> existingDataOptional = null;
        try {
            existingDataOptional = tx.read(datastoreType.getDatastoreType(), identifier).checkedGet();
        } catch (ReadFailedException ex) {
            LOG.error("Failed to read data {} from {}", identifier, datastoreType.getDatastoreType());
            return;
        }
        if (create) {
            if (isDataUpdated(existingDataOptional, data)) {
                tx.put(datastoreType.getDatastoreType(), identifier, data);
            } else {
                LOG.info("ha copy skipped for {} destination with nodei id {} "
                        + " dataTypeName {} " , destination, nodeId, dataTypeName);
            }
        } else {
            if (existingDataOptional.isPresent()) {
                tx.delete(datastoreType.getDatastoreType(), identifier);
            } else {
                LOG.info("ha delete skipped for {} destination with nodei id {} "
                        + " dataTypeName {} " , destination, nodeId, dataTypeName);
            }
        }
    }

    public String getClassTypeName() {
        return clazz.getSimpleName();
    }

    private String getNodeId(InstanceIdentifier<Node> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    protected Set<InstanceIdentifier<Node>> getChildrenForHANode(InstanceIdentifier identifier) {
        InstanceIdentifier<Node> parent = identifier.firstIdentifierOf(Node.class);
        return HwvtepHACache.getInstance().getChildrenForHANode(parent);
    }

    protected InstanceIdentifier<Node> getHAParent(InstanceIdentifier identifier) {
        InstanceIdentifier<Node> child = identifier.firstIdentifierOf(Node.class);
        return HwvtepHACache.getInstance().getParent(child);
    }
}
