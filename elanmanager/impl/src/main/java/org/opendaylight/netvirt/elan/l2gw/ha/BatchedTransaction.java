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
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class BatchedTransaction implements ReadWriteTransaction {

    @Override
    public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier) {
        return ResourceBatchingManager.getInstance().read(getShard(logicalDatastoreType).name(), instanceIdentifier);
    }

    ResourceBatchingManager.ShardResource getShard(LogicalDatastoreType logicalDatastoreType) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            return ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY;
        }
        return ResourceBatchingManager.ShardResource.OPERATIONAL_TOPOLOGY;
    }

    @Override
    public <T extends DataObject> void put(
            LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ResourceBatchingManager.getInstance().put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
                                           InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ResourceBatchingManager.getInstance().put(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj) {
        ResourceBatchingManager.getInstance().merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
                                             InstanceIdentifier<T> instanceIdentifier, T dataObj, boolean flag) {
        ResourceBatchingManager.getInstance().merge(getShard(logicalDatastoreType), instanceIdentifier, dataObj);
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier) {
        ResourceBatchingManager.getInstance().delete(getShard(logicalDatastoreType), instanceIdentifier);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return Futures.immediateCheckedFuture(null);
    }

    @Override
    public @NonNull FluentFuture<? extends @NonNull CommitInfo> commit() {
        return CommitInfo.emptyFluentFuture();
    }

    @Override
    public Object getIdentifier() {
        return "BatchedTransaction";
    }
}
