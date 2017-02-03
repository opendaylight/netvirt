/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.MergeCommand;
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
        extends AsyncDataTreeChangeListenerBase<T, HwvtepNodeDataListener<T>> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeDataListener.class);

    private final DataBroker broker;
    private final MergeCommand mergeCommand;
    private final LogicalDatastoreType datastoreType;

    public HwvtepNodeDataListener(DataBroker broker,
                                  Class<T> clazz,
                                  Class<HwvtepNodeDataListener<T>> eventClazz,
                                  MergeCommand mergeCommand,
                                  LogicalDatastoreType datastoreType) {
        super(clazz, eventClazz);
        this.broker = broker;
        this.mergeCommand = mergeCommand;
        this.datastoreType = datastoreType;
        registerListener(this.datastoreType, broker);
    }

    protected abstract InstanceIdentifier<T> getWildCardPath();

    @Override
    protected void add(InstanceIdentifier<T> identifier, T dataAdded) {
        HAJobScheduler.getInstance().submitJob( () -> {
            try {
                boolean create = true;
                ReadWriteTransaction tx = broker.newReadWriteTransaction();
                if (LogicalDatastoreType.OPERATIONAL == datastoreType) {
                    copyToParent(identifier, dataAdded, create, tx);
                } else {
                    copyToChild(identifier, dataAdded, create, tx);
                }
                tx.submit();
            } catch (ReadFailedException e) {
                LOG.error("Read failed", e.getMessage());
            }
        });
    }

    @Override
    protected void update(InstanceIdentifier<T> key, T before, T after) {
        HAJobScheduler.getInstance().submitJob( () -> {
            if (Objects.equals(before, after)) {
                //incase of cluter reboots tx.put will rewrite the data and fire unnecessary updates
                return;
            }
            add(key, after);
        });
    }

    @Override
    protected void remove(InstanceIdentifier<T> identifier, T dataRemoved) {
        HAJobScheduler.getInstance().submitJob( () -> {
            try {
                boolean create = false;
                ReadWriteTransaction tx = broker.newReadWriteTransaction();
                if (LogicalDatastoreType.OPERATIONAL == datastoreType) {
                    copyToParent(identifier, dataRemoved, create, tx);
                } else {
                    copyToChild(identifier, dataRemoved, create, tx);
                }
                tx.submit();
            } catch (ReadFailedException e) {
                LOG.error("Read failed", e.getMessage());
            }
        });
    }

    <T extends DataObject> boolean isDataUpdated(Optional<T> existingDataOptional, T newData) {
        return !existingDataOptional.isPresent() || !Objects.equals(existingDataOptional.get(), newData);
    }

    <T extends DataObject> void copyToParent(InstanceIdentifier<T> identifier, T data, boolean create,
                                             ReadWriteTransaction tx) throws ReadFailedException {
        InstanceIdentifier<Node> parent = getHAParent(identifier);
        if (parent == null) {
            return;
        }
        LOG.trace("Copy child op data {} to parent {} create:{}", mergeCommand.getDescription(),
                getNodeId(parent), create);
        data = (T) mergeCommand.transform(parent, data);
        identifier = mergeCommand.generateId(parent, data);
        Optional<T> existingDataOptional = tx.read(datastoreType, identifier).checkedGet();
        if (create) {
            if (isDataUpdated(existingDataOptional, data)) {
                tx.put(datastoreType, identifier, data);
            }
        } else {
            if (existingDataOptional.isPresent()) {
                tx.delete(datastoreType, identifier);
            }
        }
    }

    <T extends DataObject> void copyToChild(InstanceIdentifier<T> identifier, T data, boolean create,
                                            ReadWriteTransaction tx) throws ReadFailedException {
        Set<InstanceIdentifier<Node>> children = getChildrenForHANode(identifier);
        if (children == null) {
            return;
        }
        for (InstanceIdentifier<Node> child : children) {
            LOG.trace("Copy parent config data {} to child {} create:{} ", mergeCommand.getDescription(),
                    getNodeId(child), create);
            data = (T) mergeCommand.transform(child, data);
            identifier = mergeCommand.generateId(child, data);
            if (create) {
                tx.put(datastoreType, identifier, data);
            } else {
                Optional<T> existingDataOptional = tx.read(datastoreType, identifier).checkedGet();
                if (existingDataOptional.isPresent()) {
                    tx.delete(datastoreType, identifier);
                }
            }
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
