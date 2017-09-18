/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionUtil.class);

    private TransactionUtil() {
    }

    public static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("onSuccess: Success in Datastore operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("onFailure: Error in Datastore operation", error);
        }
    };

    public static <T extends DataObject> Optional<T> read(DataBroker dataBroker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();

        Optional<T> result;
        try {
            result = tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.debug("read: Error while reading data from path {}", path);
            throw new RuntimeException(e);
        }
        return result;
    }

    public static <T extends DataObject> void asyncWrite(DataBroker dataBroker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback, MoreExecutors.directExecutor());
    }

    public static <T extends DataObject> void syncWrite(DataBroker dataBroker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, WriteTransaction.CREATE_MISSING_PARENTS);
        try {
            tx.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("syncWrite: Error writing VPN instance to ID info to datastore (path, data) : ({}, {})", path,
                    data);
            throw new RuntimeException(e.getMessage());
        }
    }
}
