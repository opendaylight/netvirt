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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAJobScheduler;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BatchedTransaction implements ReadWriteTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(BatchedTransaction.class);
    private static Map<InstanceIdentifier, ListenableFuture<Void>> configInProgress = new ConcurrentHashMap<>();
    private static Map<InstanceIdentifier, ListenableFuture<Void>> opInProgress = new ConcurrentHashMap<>();

    private final DataBroker broker;
    private ResourceBatchingManager batchingManager = ResourceBatchingManager.getInstance();
    private Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = new ConcurrentHashMap<>();
    private Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = new ConcurrentHashMap<>();
    private Map<InstanceIdentifier, ListenableFuture<Void>> currentOps = new ConcurrentHashMap<>();

    SettableFuture<Void> result = SettableFuture.create();

    public BatchedTransaction(DataBroker broker) {
        this.broker = broker;
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

    public static synchronized void markUpdateInProgress(LogicalDatastoreType logicalDatastoreType,
                                                         InstanceIdentifier instanceIdentifier) {
        SettableFuture ft = SettableFuture.create();
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            configInProgress.put(instanceIdentifier, ft);
            Futures.addCallback(ft, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    configInProgress.remove(instanceIdentifier);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    configInProgress.remove(instanceIdentifier);
                    LOG.error("Failed to update mdsal op " + instanceIdentifier, throwable);
                }
            });
        } else {
            opInProgress.put(instanceIdentifier, ft);
            Futures.addCallback(ft, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    opInProgress.remove(instanceIdentifier);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    opInProgress.remove(instanceIdentifier);
                }
            });
        }
    }

    public static synchronized boolean isInProgress(LogicalDatastoreType logicalDatastoreType,
                                                    InstanceIdentifier instanceIdentifier) {

        ListenableFuture<Void> ft = getInprogressFt(logicalDatastoreType, instanceIdentifier);
        return ft != null && !ft.isDone() && !ft.isCancelled();
    }

    public static synchronized boolean addCallbackIfInProgress(LogicalDatastoreType logicalDatastoreType,
                                                               InstanceIdentifier instanceIdentifier,
                                                               Runnable runnable) {

        ListenableFuture<Void> ft = getInprogressFt(logicalDatastoreType, instanceIdentifier);
        if (ft != null && !ft.isDone() && !ft.isCancelled()) {
            Futures.addCallback(ft, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    HAJobScheduler.getInstance().submitJob(runnable);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    HAJobScheduler.getInstance().submitJob(runnable);
                }
            });
            return true;
        }
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            configInProgress.remove(instanceIdentifier);
        } else {
            opInProgress.remove(instanceIdentifier);
        }
        return false;
    }

    static ListenableFuture<Void> getInprogressFt(LogicalDatastoreType logicalDatastoreType,
                                                  InstanceIdentifier instanceIdentifier) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            return configInProgress.get(instanceIdentifier);
        } else {
            return opInProgress.get(instanceIdentifier);
        }
    }

    public void waitForCompletion() {
        if (currentOps.isEmpty()) {
            return;
        }
        Collection<ListenableFuture<Void>> fts = currentOps.values();
        for (ListenableFuture<Void> ft : fts) {
            try {
                ft.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Failed to get ft result ", e);
            }
        }
    }

    @Override
    public <T extends DataObject> void put(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier);
        batchingManager.put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
                                           InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier);
        batchingManager.put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier);
        batchingManager.merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier);
        batchingManager.merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier);
        batchingManager.delete(getShard(logicalDatastoreType), instanceIdentifier);
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
        return null;
    }
}
