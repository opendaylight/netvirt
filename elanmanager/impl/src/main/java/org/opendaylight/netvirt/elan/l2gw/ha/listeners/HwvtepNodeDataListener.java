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
import java.util.function.BiConsumer;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
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

    private final ManagedNewTransactionRunner txRunner;
    private final MergeCommand<T, ?, ?> mergeCommand;
    private final LogicalDatastoreType datastoreType;
    private final BiConsumer<InstanceIdentifier<T>, T> addOperation;
    private final BiConsumer<InstanceIdentifier<T>, T> removeOperation;
    private final HwvtepNodeHACache hwvtepNodeHACache;

    public HwvtepNodeDataListener(DataBroker broker,
                                  HwvtepNodeHACache hwvtepNodeHACache,
                                  Class<T> clazz,
                                  Class<HwvtepNodeDataListener<T>> eventClazz,
                                  MergeCommand<T, ?, ?> mergeCommand,
                                  LogicalDatastoreType datastoreType) {
        super(clazz, eventClazz);
        this.hwvtepNodeHACache = hwvtepNodeHACache;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.mergeCommand = mergeCommand;
        this.datastoreType = datastoreType;
        if (LogicalDatastoreType.OPERATIONAL == datastoreType) {
            this.addOperation = this::copyToParent;
            this.removeOperation = this::deleteFromParent;
        } else {
            this.addOperation = this::copyToChildren;
            this.removeOperation = this::deleteFromChildren;
        }
    }

    @Override
    protected abstract InstanceIdentifier<T> getWildCardPath();

    @Override
    protected void add(InstanceIdentifier<T> identifier, T dataAdded) {
        HAJobScheduler.getInstance().submitJob(() -> addOperation.accept(identifier, dataAdded));
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
        HAJobScheduler.getInstance().submitJob(() -> removeOperation.accept(identifier, dataRemoved));
    }

    private boolean isNodeConnected(InstanceIdentifier<T> identifier, ReadTransaction tx)
            throws ReadFailedException {
        return tx.read(LogicalDatastoreType.OPERATIONAL, identifier.firstIdentifierOf(Node.class))
                .checkedGet().isPresent();
    }

    private static <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }

    private void copyToParent(InstanceIdentifier<T> identifier, T data) {
        if (clazz == RemoteUcastMacs.class) {
            LOG.trace("Skipping remote ucast macs to parent");
            return;
        }
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent != null) {
            LOG.trace("Copy child op data {} to parent {}", mergeCommand.getDescription(), getNodeId(parent));
            T parentData = mergeCommand.transform(parent, data);
            InstanceIdentifier<T> parentIdentifier = mergeCommand.generateId(parent, parentData);
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(
                tx -> writeToMdsal(tx, parentData, parentIdentifier)), LOG, "Error copying to parent");
        }
    }

    private void deleteFromParent(InstanceIdentifier<T> identifier, T data) {
        if (clazz == RemoteUcastMacs.class) {
            LOG.trace("Skipping remote ucast macs to parent");
            return;
        }
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent != null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                if (isNodeConnected(identifier, tx)) {
                    LOG.trace("Copy child op data {} to parent {} create:{}", mergeCommand.getDescription(),
                            getNodeId(parent), false);
                    T parentData = mergeCommand.transform(parent, data);
                    InstanceIdentifier<T> parentIdentifier = mergeCommand.generateId(parent, parentData);
                    deleteFromMdsal(tx, parentIdentifier);
                }
            }), LOG, "Error deleting from parent");
        }
    }

    private void copyToChildren(final InstanceIdentifier<T> parentIdentifier, final T parentData) {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(parentIdentifier);
        if (children != null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                for (InstanceIdentifier<Node> child : children) {
                    LOG.trace("Copy parent config data {} to child {}", mergeCommand.getDescription(),
                            getNodeId(child));
                    final T childData = mergeCommand.transform(child, parentData);
                    final InstanceIdentifier<T> identifier = mergeCommand.generateId(child, childData);
                    writeToMdsal(tx, childData, identifier);
                }
            }), LOG, "Error copying to children");
        }
    }

    private void deleteFromChildren(final InstanceIdentifier<T> parentIdentifier, final T parentData) {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(parentIdentifier);
        if (children != null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                for (InstanceIdentifier<Node> child : children) {
                    LOG.trace("Delete parent config data {} to child {}", mergeCommand.getDescription(),
                            getNodeId(child));
                    final T childData = mergeCommand.transform(child, parentData);
                    final InstanceIdentifier<T> identifier = mergeCommand.generateId(child, childData);
                    deleteFromMdsal(tx, identifier);
                }
            }), LOG, "Error deleting from children");
        }
    }

    private void writeToMdsal(final ReadWriteTransaction tx, final T data, final InstanceIdentifier<T> identifier)
            throws ReadFailedException {
        if (isDataUpdated(tx.read(datastoreType, identifier).checkedGet(), data)) {
            tx.put(datastoreType, identifier, data);
        }
    }

    private void deleteFromMdsal(final ReadWriteTransaction tx,
            final InstanceIdentifier<T> identifier) throws ReadFailedException {
        if (tx.read(datastoreType, identifier).checkedGet().isPresent()) {
            tx.delete(datastoreType, identifier);
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
        return hwvtepNodeHACache.getChildrenForHANode(parent);
    }

    protected InstanceIdentifier<Node> getHAParent(InstanceIdentifier identifier) {
        InstanceIdentifier<Node> child = identifier.firstIdentifierOf(Node.class);
        return hwvtepNodeHACache.getParent(child);
    }
}
