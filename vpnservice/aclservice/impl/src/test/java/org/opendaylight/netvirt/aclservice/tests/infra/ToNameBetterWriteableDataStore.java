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
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * TODO Doc.
 *
 * @see SynchronousSingleTransactionWriteableDataStore
 *
 * @author Michael Vorburger
 */
public class ToNameBetterWriteableDataStore implements WriteableDataStore {
    // TODO Remove this later... replaced by SynchronousSingleWriteTransaction

    private final WriteTransaction tx;

    @Inject
    private ToNameBetterWriteableDataStore(DataBroker broker) {
        super();
        tx = Preconditions.checkNotNull(broker, "broker").newWriteOnlyTransaction();
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException {
        tx.put(store, path, data, true);
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException {
        tx.merge(store, path, data, true);
    }

    @Override
    public void delete(LogicalDatastoreType store, InstanceIdentifier<?> path) throws TransactionCommitFailedException {
        tx.delete(store, path);
    }

    @PreDestroy
    public void close() throws TransactionCommitFailedException {
        CheckedFuture<Void, TransactionCommitFailedException> future = tx.submit();
        // TODO??? future.addListener(listener, executor);
        // TODO try { } catch (...) { LOG.error() }
        future.checkedGet();
    }

}
