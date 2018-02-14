/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
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
        extends AsyncDataTreeChangeListenerBase<T, HwvtepNodeDataListener<T>> {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeDataListener.class);

    private final DataBroker broker;
    private final MergeCommand mergeCommand;
    private final ResourceBatchingManager.ShardResource datastoreType;
    private final String dataTypeName;

    public HwvtepNodeDataListener(DataBroker broker,
                                  Class<T> clazz,
                                  Class<HwvtepNodeDataListener<T>> eventClazz,
                                  MergeCommand mergeCommand,
                                  ResourceBatchingManager.ShardResource datastoreType) {
        super(clazz, eventClazz);
        this.broker = broker;
        this.mergeCommand = mergeCommand;
        this.datastoreType = datastoreType;
        this.dataTypeName = getClassTypeName();
        registerListener(this.datastoreType.getDatastoreType() , broker);
    }

    @Override
    protected abstract InstanceIdentifier<T> getWildCardPath();

    @Override
    protected void add(InstanceIdentifier<T> identifier, T dataAdded) {
        HAJobScheduler.getInstance().submitJob(() -> {
            try {
                boolean create = true;
                ReadWriteTransaction tx = broker.newReadWriteTransaction();
                if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                    copyToParent(identifier, dataAdded, create, tx);
                } else {
                    copyToChild(identifier, dataAdded, create, tx);
                }
            } catch (ReadFailedException e) {
                LOG.error("Exception caught while writing ", e.getMessage());
            }
        });
    }

    @Override
    protected void update(InstanceIdentifier<T> key, T before, T after) {
        HAJobScheduler.getInstance().submitJob(() -> {
            if (Objects.equals(before, after)) {
                //incase of cluter reboots tx.put will rewrite the data and fire unnecessary updates
                return;
            }
            add(key, after);
        });
    }

    @Override
    protected void remove(InstanceIdentifier<T> identifier, T dataRemoved) {
        HAJobScheduler.getInstance().submitJob(() -> {
            try {
                boolean create = false;
                ReadWriteTransaction tx = broker.newReadWriteTransaction();
                if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                    if (isNodeConnected(identifier, tx)) {
                        //Do not process the remove from disconnected child node
                        copyToParent(identifier, dataRemoved, create, tx);
                    }
                } else {
                    copyToChild(identifier, dataRemoved, create, tx);
                }
            } catch (ReadFailedException e) {
                LOG.error("Exception caught while writing ", e.getMessage());
            }
        });
    }

    protected boolean isNodeConnected(InstanceIdentifier<T> identifier, ReadWriteTransaction tx)
            throws ReadFailedException {
        return tx.read(LogicalDatastoreType.OPERATIONAL, identifier.firstIdentifierOf(Node.class))
                .checkedGet().isPresent();
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
        if (clazz == RemoteUcastMacs.class) {
            LOG.trace("Skipping remote ucast macs to parent");
        }
        LOG.trace("Copy child op data {} to parent {} create:{}", mergeCommand.getDescription(),
                getNodeId(parent), create);
        data = (T) mergeCommand.transform(parent, data);
        identifier = mergeCommand.generateId(parent, data);
        writeToMdsal(create, tx, data, identifier, false);
        tx.submit();
    }

    Map<InstanceIdentifier<T>, ListenableFuture<Boolean>> inprogressOps = new ConcurrentHashMap<>();

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
            writeToMdsal(create, tx, childData, identifier, true);
        }
        tx.submit();
    }

    private void writeToMdsal(final boolean create,
                              final ReadWriteTransaction tx,
                              final T data,
                              final InstanceIdentifier<T> identifier,
                              final boolean copyToChild) throws ReadFailedException {
        Optional<T> existingDataOptional = tx.read(datastoreType.getDatastoreType(), identifier).checkedGet();
        if (create) {
            if (isDataUpdated(existingDataOptional, data)) {
                ResourceBatchingManager.getInstance().put(datastoreType, identifier, data);
            }
        } else {
            if (existingDataOptional.isPresent()) {
                ResourceBatchingManager.getInstance().delete(datastoreType, identifier);
            }
        }
    }

    public String getClassTypeName() {
        return clazz.getSimpleName();
    }

    private String getNodeId(InstanceIdentifier<Node> iid) {
        return iid.firstKeyOf(Node.class).getNodeId().getValue();
    }

    @Override
    protected HwvtepNodeDataListener<T> getDataTreeChangeListener() {
        return HwvtepNodeDataListener.this;
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
