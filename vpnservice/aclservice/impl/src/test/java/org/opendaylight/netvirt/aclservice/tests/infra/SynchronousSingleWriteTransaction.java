/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;

/**
 * WriteTransaction (TODO WriteableDataStore) with a single transaction.
 *
 * <p>The Tx is opened in the constructor from the DataBroker, and closed
 * in the close method.
 *
 * <p>TODO document usage...
 *
 * @see SynchronousEachOperationNewWriteTransaction
 *
 * @author Michael Vorburger
 */
@SuppressWarnings("deprecation")
public class SynchronousSingleWriteTransaction implements WriteTransaction {
    // TODO when (tbd gerrit) is merged, then rename class & implements WriteableDataStore instead of WriteTransaction

    // TODO wire a similar ReadTransaction, and explore using it in AclServiceUtils, instead of new for each

    // TODO explore using this in elanmanager by changing all src/main code to use this instead of creating new Txs

    private final WriteTransaction tx;

    @Inject
    private SynchronousSingleWriteTransaction(DataBroker broker) {
        super();
        tx = Preconditions.checkNotNull(broker, "broker").newWriteOnlyTransaction();
    }

    @PreDestroy
    public void close() throws TransactionCommitFailedException {
        tx.submit().checkedGet();
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data) {
        tx.put(store, path, data);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data,
            boolean createMissingParents) {
        tx.put(store, path, data, createMissingParents);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType store, InstanceIdentifier<T> path, T data) {
        tx.merge(store, path, data);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType store, InstanceIdentifier<T> path, T data,
            boolean createMissingParents) {
        tx.merge(store, path, data, createMissingParents);
    }

    @Override
    public void delete(LogicalDatastoreType store, InstanceIdentifier<?> path) {
        tx.delete(store, path);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getIdentifier() {
        throw new UnsupportedOperationException();
    }

}
