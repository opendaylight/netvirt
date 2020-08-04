/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAJobScheduler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchedTransaction implements ReadWriteTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(BatchedTransaction.class);
    private static Map<InstanceIdentifier, ListenableFuture<Void>> configInProgress = new ConcurrentHashMap<>();
    private static Map<InstanceIdentifier, ListenableFuture<Void>> opInProgress = new ConcurrentHashMap<>();

    private Map<InstanceIdentifier, ListenableFuture<Void>> currentOps = new ConcurrentHashMap<>();

    private SettableFuture<Void> result = SettableFuture.create();
    private boolean updateMetric;
    private NodeId srcNodeId;

    public ListenableFuture<Void> getResult() {
        return result;
    }

    @Override
    public <T extends DataObject> FluentFuture<Optional<T>> read(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier)  {
        return ResourceBatchingManager.getInstance().read(getShard(logicalDatastoreType).name(), instanceIdentifier);
    }

    ResourceBatchingManager.ShardResource getShard(LogicalDatastoreType logicalDatastoreType) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            return ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY;
        }
        return ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY;
    }

    public static synchronized void markUpdateInProgress(LogicalDatastoreType logicalDatastoreType,
                                                         InstanceIdentifier instanceIdentifier,
                                                         ListenableFuture<Void> ft) {
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft, "");

    }

    public static synchronized void markUpdateInProgress(LogicalDatastoreType logicalDatastoreType,
                                                         InstanceIdentifier instanceIdentifier,
                                                         ListenableFuture<Void> ft,
                                                         String desc) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
//            NodeKey nodeKey = (NodeKey) instanceIdentifier.firstKeyOf(Node.class);
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
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().put(getShard(logicalDatastoreType),
                instanceIdentifier, dataObj);
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
                                           InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().put(getShard(logicalDatastoreType),
                instanceIdentifier, dataObj);
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().merge(getShard(logicalDatastoreType),
                instanceIdentifier, dataObj);
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().merge(getShard(logicalDatastoreType),
                instanceIdentifier, dataObj);
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    public ListenableFuture<Void> getFt(InstanceIdentifier instanceIdentifier) {
        return currentOps.get(instanceIdentifier);
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().delete(getShard(logicalDatastoreType),
                instanceIdentifier);
        markUpdateInProgress(logicalDatastoreType, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        if (currentOps.isEmpty()) {
            return Futures.immediateCheckedFuture(null);
        }
        Collection<ListenableFuture<Void>> fts = currentOps.values();
        AtomicInteger waitCount = new AtomicInteger(fts.size());
        for (ListenableFuture<Void> ft : fts) {
            Futures.addCallback(ft, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void voidResult) {
                    if (waitCount.decrementAndGet() == 0) {
                        result.set(null);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    result.setException(throwable);
                }
            });
        }
        return Futures.makeChecked((ListenableFuture<Void>)result,
            (exception) -> new TransactionCommitFailedException("", exception, null));
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return Futures.immediateCheckedFuture(RpcResultBuilder.success(TransactionStatus.COMMITED).build());
    }

    @Override
    public Object getIdentifier() {
        return "BatchedTransaction";
    }

    public void setSrcNodeId(NodeId srcNodeId) {
        this.srcNodeId = srcNodeId;
    }

    public NodeId getSrcNodeId() {
        return srcNodeId;
    }

    public boolean updateMetric() {
        return updateMetric;
    }

    public void updateMetric(Boolean update) {
        this.updateMetric = update;
    }
}
