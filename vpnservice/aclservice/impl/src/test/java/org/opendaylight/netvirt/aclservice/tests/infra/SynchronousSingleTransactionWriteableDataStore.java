/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import com.google.common.base.Preconditions;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Utility around {@link DataBroker} for simple synchronous operations, each in new transactions.
 *
 * <p>This is mainly intended for writing simple readable tests.  In production code, you typically
 * will not want to create a new transaction for each of your operations, but instead create one once,
 * and re-use it for a number of operations.
 *
 * @see WriteTransaction
 *
 * @author Michael Vorburger
 */
public class SynchronousSingleTransactionWriteableDataStore implements WriteableDataStore {
    // TODO Remove this later... replaced by SynchronousEachOperationNewWriteTransaction

    // TODO MOVE elsewhere.. I've already written a similar class elsewhere in a merged Gerrit.. find it, de-dupe this!

    // TODO @deprecate org.opendaylight.genius.mdsalutil.MDSALUtil.syncWrite() with this

    private final DataBroker broker;

    @Inject
    private SynchronousSingleTransactionWriteableDataStore(DataBroker broker) {
        super();
        this.broker = Preconditions.checkNotNull(broker);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(store, path, data, true);
        tx.submit().checkedGet();
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(store, path, data, true);
        tx.submit().checkedGet();
    }

    @Override
    public void delete(LogicalDatastoreType store, InstanceIdentifier<?> path) throws TransactionCommitFailedException {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(store, path);
        tx.submit().checkedGet();
    }

}
