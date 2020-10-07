/*
 * Copyright (c) 2020 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.query.QueryExpression;
import org.opendaylight.mdsal.binding.api.query.QueryResult;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAJobScheduler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchedTransaction<D extends Datastore> implements TypedReadWriteTransaction<D> {

    private static final Logger LOG = LoggerFactory.getLogger(BatchedTransaction.class);
    private static Map<InstanceIdentifier, ListenableFuture<Void>> configInProgress = new ConcurrentHashMap<>();
    private static Map<InstanceIdentifier, ListenableFuture<Void>> opInProgress = new ConcurrentHashMap<>();

    private Map<InstanceIdentifier, ListenableFuture<Void>> currentOps = new ConcurrentHashMap<>();

    private SettableFuture<Void> result = SettableFuture.create();
    private boolean updateMetric;
    private NodeId srcNodeId;
    private Class<D> type;

    public BatchedTransaction(Class<D> logicalDatastoreType) {
        this.type = logicalDatastoreType;
    }

    public ListenableFuture<Void> getResult() {
        return result;
    }

    @Override
    public <T extends DataObject> FluentFuture<Optional<T>> read(InstanceIdentifier<T> instanceIdentifier) {
        try {
            return ResourceBatchingManager.getInstance()
                .read(getShard().name(), instanceIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("BatchTxn failed to Read {}", instanceIdentifier);
        }
        return FluentFutures.immediateFailedFluentFuture(new Throwable());
    }

    @Override
    public FluentFuture<Boolean> exists(InstanceIdentifier<?> path) {
        // NOT SUPPORTED
        return FluentFutures.immediateFailedFluentFuture(new Throwable());
    }

    ResourceBatchingManager.ShardResource getShard() {
        if (Configuration.class.equals(type)) {
            return ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY;
        }
        return ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY;
    }

    public static synchronized <D extends Datastore> void markUpdateInProgress(Class<D> type,
        InstanceIdentifier instanceIdentifier, ListenableFuture<Void> ft) {
        markUpdateInProgress(type, instanceIdentifier, ft, "");

    }

    public static synchronized <D extends Datastore> void markUpdateInProgress(Class<D> type,
        InstanceIdentifier instanceIdentifier,ListenableFuture<Void> ft, String desc) {
        if (Configuration.class.equals(type)) {
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
                    LOG.error("Failed to update mdsal op {}", instanceIdentifier, throwable);
                }
            }, MoreExecutors.directExecutor());
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
            }, MoreExecutors.directExecutor());
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
            }, MoreExecutors.directExecutor());
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
    public <T extends DataObject> void put(InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().put(getShard(),
                instanceIdentifier, dataObj);
        markUpdateInProgress(type, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void mergeParentStructurePut(InstanceIdentifier<T> path, T data) {

    }

    @Override
    public <T extends DataObject> void merge(InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().merge(getShard(),
                instanceIdentifier, dataObj);
        markUpdateInProgress(type, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    @Override
    public <T extends DataObject> void mergeParentStructureMerge(InstanceIdentifier<T> path,
        T data) {
        //NOT SUPPORTED
    }

    public ListenableFuture<Void> getFt(InstanceIdentifier instanceIdentifier) {
        return currentOps.get(instanceIdentifier);
    }

    @Override
    public void delete(InstanceIdentifier<?> instanceIdentifier) {
        ListenableFuture<Void> ft = ResourceBatchingManager.getInstance().delete(getShard(),
                instanceIdentifier);
        markUpdateInProgress(type, instanceIdentifier, ft);
        currentOps.put(instanceIdentifier, ft);
    }

    public ListenableFuture<Void> submit() {
        if (currentOps.isEmpty()) {
            return Futures.immediateFuture(null);
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
            }, MoreExecutors.directExecutor());
        }
        return result;
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

    @Override
    public <T extends @NonNull DataObject> FluentFuture<QueryResult<T>> execute(QueryExpression<T> query) {
        throw new UnsupportedOperationException();
    }
}
