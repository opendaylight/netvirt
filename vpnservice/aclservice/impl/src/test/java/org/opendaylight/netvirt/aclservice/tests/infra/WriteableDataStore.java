/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree store access without transaction semantics exposed.
 *
 * <p>Different implementations will manage transaction boundaries differently;
 * one may open a new Tx for each operation, another one execute operations within an
 * already open Tx, etc.
 *
 * @author Michael Vorburger
 */
public interface WriteableDataStore {
    // TODO Remove this later... no need for this, just use WriteTransaction (or it's new super interface, on this)

    // TODO ReadableDataStore ... see ReadTransaction

    /**
     * Put data at the specified path.
     * This method *DOES* automatically create missing parent nodes (contrary to WriteTransaction's put()).
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be written to the specified path
     *
     * @see org.opendaylight.controller.md.sal.binding.api.WriteTransaction
     *             #put(LogicalDatastoreType, InstanceIdentifier, DataObject)
     * @see org.opendaylight.controller.md.sal.binding.api.WriteTransaction
     *             #put(LogicalDatastoreType, InstanceIdentifier, DataObject, boolean)
     */
    <T extends DataObject> void put(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException;

    /**
     * Merge data with the existing data at the specified path.
     * This method *DOES* automatically create missing parent nodes (contrary to WriteTransaction's merge()).
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @param data the data object to be merged to the specified path
     *
     * @see org.opendaylight.controller.md.sal.binding.api.WriteTransaction
     *     #merge(LogicalDatastoreType, InstanceIdentifier, DataObject)
     * @see org.opendaylight.controller.md.sal.binding.api.WriteTransaction
     *     #merge(LogicalDatastoreType, InstanceIdentifier, DataObject, boolean)
     */
    <T extends DataObject> void merge(LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws TransactionCommitFailedException;

    /**
     * Deletes data from the specified path.
     * This operation does not fail if the specified path does not exist.
     *
     * @param store Logical data store which should be modified
     * @param path Data object path
     *
     * @see org.opendaylight.controller.md.sal.common.api.data.AsyncWriteTransaction#delete(LogicalDatastoreType, P)
     */
    // <P extends Path<P>> void delete(LogicalDatastoreType store, P path) throws TransactionCommitFailedException;
    void delete(LogicalDatastoreType store, InstanceIdentifier<?> path) throws TransactionCommitFailedException;
}
