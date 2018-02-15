/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import com.google.common.base.Optional;
import java.util.Objects;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
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
    private final MergeCommand<T, ?, ?> mergeCommand;
    private final ResourceBatchingManager.ShardResource datastoreType;

    public HwvtepNodeDataListener(DataBroker broker,
                                  Class<T> clazz,
                                  Class<HwvtepNodeDataListener<T>> eventClazz,
                                  MergeCommand<T, ?, ?> mergeCommand,
                                  ResourceBatchingManager.ShardResource datastoreType) {
        super(clazz, eventClazz);
        this.broker = broker;
        this.mergeCommand = mergeCommand;
        this.datastoreType = datastoreType;
        registerListener(this.datastoreType.getDatastoreType() , broker);
    }

    @Override
    protected abstract InstanceIdentifier<T> getWildCardPath();

    @Override
    protected void add(InstanceIdentifier<T> identifier, T dataAdded) {
        HAJobScheduler.getInstance().submitJob(() -> {
            try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
                if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                    copyToParent(identifier, dataAdded, tx);
                } else {
                    copyToChildren(identifier, dataAdded, tx);
                }
            } catch (ReadFailedException e) {
                LOG.error("Error processing added HWVTEP node", e);
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
            try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
                if (LogicalDatastoreType.OPERATIONAL == datastoreType.getDatastoreType()) {
                    if (isNodeConnected(identifier, tx)) {
                        //Do not process the remove from disconnected child node
                        deleteFromParent(identifier, dataRemoved, tx);
                    }
                } else {
                    deleteFromChildren(identifier, dataRemoved, tx);
                }
            } catch (ReadFailedException e) {
                LOG.error("Error processing removed HWVTEP node", e);
            }
        });
    }

    private boolean isNodeConnected(InstanceIdentifier<T> identifier, ReadTransaction tx)
            throws ReadFailedException {
        return tx.read(LogicalDatastoreType.OPERATIONAL, identifier.firstIdentifierOf(Node.class))
                .checkedGet().isPresent();
    }

    private static <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }

    private void copyToParent(InstanceIdentifier<T> identifier, T data, ReadTransaction tx)
            throws ReadFailedException {
        if (clazz == RemoteUcastMacs.class) {
            LOG.trace("Skipping remote ucast macs to parent");
            return;
        }
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent != null) {
            LOG.trace("Copy child op data {} to parent {}", mergeCommand.getDescription(), getNodeId(parent));
            data = mergeCommand.transform(parent, data);
            identifier = mergeCommand.generateId(parent, data);
            writeToMdsal(tx, data, identifier);
        }
    }

    private void deleteFromParent(InstanceIdentifier<T> identifier, T data, ReadTransaction tx)
            throws ReadFailedException {
        if (clazz == RemoteUcastMacs.class) {
            LOG.trace("Skipping remote ucast macs to parent");
            return;
        }
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent != null) {
            LOG.trace("Copy child op data {} to parent {} create:{}", mergeCommand.getDescription(),
                    getNodeId(parent), false);
            data = mergeCommand.transform(parent, data);
            identifier = mergeCommand.generateId(parent, data);
            deleteFromMdsal(tx, identifier);
        }
    }

    private void copyToChildren(final InstanceIdentifier<T> parentIdentifier, final T parentData,
            final ReadTransaction tx) throws ReadFailedException {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(parentIdentifier);
        if (children != null) {
            for (InstanceIdentifier<Node> child : children) {
                LOG.trace("Copy parent config data {} to child {}", mergeCommand.getDescription(), getNodeId(child));
                final T childData = mergeCommand.transform(child, parentData);
                final InstanceIdentifier<T> identifier = mergeCommand.generateId(child, childData);
                writeToMdsal(tx, childData, identifier);
            }
        }
    }

    private void deleteFromChildren(final InstanceIdentifier<T> parentIdentifier, final T parentData,
            final ReadTransaction tx) throws ReadFailedException {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(parentIdentifier);
        if (children != null) {
            for (InstanceIdentifier<Node> child : children) {
                LOG.trace("Delete parent config data {} to child {}", mergeCommand.getDescription(), getNodeId(child));
                final T childData = mergeCommand.transform(child, parentData);
                final InstanceIdentifier<T> identifier = mergeCommand.generateId(child, childData);
                deleteFromMdsal(tx, identifier);
            }
        }
    }

    private void writeToMdsal(final ReadTransaction tx, final T data, final InstanceIdentifier<T> identifier)
            throws ReadFailedException {
        if (isDataUpdated(tx.read(datastoreType.getDatastoreType(), identifier).checkedGet(), data)) {
            ResourceBatchingManager.getInstance().put(datastoreType, identifier, data);
        }
    }

    private void deleteFromMdsal(final ReadTransaction tx,
            final InstanceIdentifier<T> identifier) throws ReadFailedException {
        if (tx.read(datastoreType.getDatastoreType(), identifier).checkedGet().isPresent()) {
            ResourceBatchingManager.getInstance().delete(datastoreType, identifier);
        }
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
