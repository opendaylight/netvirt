/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchedTransaction implements ReadWriteTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(BatchedTransaction.class);
    private static final Map<InstanceIdentifier, ListenableFuture<Void>> configInProgress = new ConcurrentHashMap<>();
    private static final Map<InstanceIdentifier, ListenableFuture<Void>> opInProgress = new ConcurrentHashMap<>();

    private final DataBroker broker;
    private final ResourceBatchingManager batchingManager = ResourceBatchingManager.getInstance();
    private Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = new ConcurrentHashMap<>();
    private Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = new ConcurrentHashMap<>();
    private final IidTracker iidTracker;

    public BatchedTransaction(DataBroker broker, IidTracker iidTracker) {
        this.broker = broker;
        this.iidTracker = iidTracker;
    }

    public DataBroker getBroker() {
        return broker;
    }

    public Map<Class<? extends Identifiable>, List<Identifiable>> getDeletedData() {
        return deletedData;
    }

    public void setDeletedData(Map<Class<? extends Identifiable>, List<Identifiable>> deletedData) {
        this.deletedData = deletedData;
    }

    public Map<Class<? extends Identifiable>, List<Identifiable>> getUpdatedData() {
        return updatedData;
    }

    public void setUpdatedData(Map<Class<? extends Identifiable>, List<Identifiable>> updatedData) {
        this.updatedData = updatedData;
    }

    @Override
    public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier) {
        return broker.newReadOnlyTransaction().read(logicalDatastoreType, instanceIdentifier);
    }

    ResourceBatchingManager.ShardResource getShard(LogicalDatastoreType logicalDatastoreType) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            return ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY;
        }
        return ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY;
    }

    public boolean isInProgress(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier iid) {

        return iidTracker.isInProgress(logicalDatastoreType, iid);
    }

    public boolean addCallbackIfInProgress(LogicalDatastoreType logicalDatastoreType,
                                           InstanceIdentifier iid,
                                           Runnable runnable) {
        return iidTracker.addCallbackIfInProgress(logicalDatastoreType, iid, runnable);
    }

    @Override
    public <T extends DataObject> void put(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ListenableFuture<Void> ft = batchingManager.put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
        iidTracker.markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
                                           InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ListenableFuture<Void> ft = batchingManager.put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
        iidTracker.markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ListenableFuture<Void> ft = batchingManager.merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
        iidTracker.markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ListenableFuture<Void> ft = batchingManager.merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
        iidTracker.markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier) {
        ListenableFuture<Void> ft = batchingManager.delete(getShard(logicalDatastoreType), instanceIdentifier);
        iidTracker.markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return Futures.immediateCheckedFuture(null);
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return Futures.immediateCheckedFuture(RpcResultBuilder.success(TransactionStatus.COMMITED).build());
    }

    @Override
    public Object getIdentifier() {
        return "BatchedTransaction";
    }
}
