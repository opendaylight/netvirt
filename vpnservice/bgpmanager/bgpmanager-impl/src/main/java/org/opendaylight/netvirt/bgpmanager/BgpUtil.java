/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.*;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by emhamla on 8/31/2015.
 */

public class BgpUtil {

    private static final long FIB_READ_TIMEOUT = 1;
    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static DataBroker  dataBroker;
    private static BindingTransactionChain fibTransact;
    private static AtomicInteger pendingWrTransaction = new AtomicInteger(0);
    private static int txChainAttempts = 0;

    // return number of pending Write Transactions with BGP-Util (no read)
    public static int getGetPendingWrTransaction() {
        return pendingWrTransaction.get();
    }

    static ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("bgp-util-mdsal-%d").build();

    static ExecutorService threadPool = Executors.newFixedThreadPool(1, namedThreadFactory);


    static synchronized BindingTransactionChain getTransactionChain() {
        return fibTransact;
    }

    static <T extends DataObject> void update(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path, final T data) {
        threadPool.submit(new MdsalDsTask<>(datastoreType, path, data, TransactionType.UPDATE));
    }


    public static <T extends DataObject> void write(DataBroker broker, final LogicalDatastoreType datastoreType,
                                                    final InstanceIdentifier<T> path, final T data) {
        threadPool.submit(new MdsalDsTask<>(datastoreType, path, data, TransactionType.WRITE));
    }

    static <T extends DataObject> void delete(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path) {
        threadPool.submit(new MdsalDsTask<>(datastoreType, path, null, TransactionType.DELETE));
    }

    static enum TransactionType {
        WRITE, UPDATE, DELETE;
    }

    static  class MdsalDsTask<T extends DataObject> implements Runnable {
        LogicalDatastoreType datastoreType;
        InstanceIdentifier<T> path;
        T data;
        TransactionType type;

        public MdsalDsTask(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path, T data, TransactionType type) {
            this.datastoreType = datastoreType;
            this.path = path;
            this.data = data;
            this.type = type;
        }

        @Override
        public void run() {
            try {
                LOG.trace("BgpUtil MDSAL task started ");
                WriteTransaction tx = getTransactionChain().newWriteOnlyTransaction();
                switch (type) {
                    case WRITE:
                        tx.put(datastoreType, path, data, true);
                        break;
                    case UPDATE:
                        tx.merge(datastoreType, path, data, true);
                        break;
                    case DELETE:
                        tx.delete(datastoreType, path);
                        break;
                    default:
                        LOG.error("Invalid Transaction type: {}", type);
                }
                pendingWrTransaction.incrementAndGet();
                addFutureCallback(tx, path, data);
                LOG.trace("Transaction type: {} submitted", type);
            } catch (final Exception e) {
                LOG.error("TxChain transaction submission failed, re-init TxChain", e);
                initTransactionChain();
            }
        }
    }


    static  <T extends DataObject> void addFutureCallback(WriteTransaction tx, final InstanceIdentifier<T> path,
                                                          final T data) {
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                pendingWrTransaction.decrementAndGet();
                LOG.trace("DataStore entry success data:{} path:{} ", path);
            }

            @Override
            public void onFailure(final Throwable t) {
                pendingWrTransaction.decrementAndGet();
                LOG.error("DataStore  entry failed data:{} path:{} cause:{} , retry initTransactionChain", data, path, t.getCause());
           }
        });
    }


    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path)
            throws ExecutionException, InterruptedException, TimeoutException {

        ReadTransaction tx = broker.newReadOnlyTransaction();
        CheckedFuture<?,?> result = tx.read(datastoreType, path);

        try {
            return (Optional<T>) result.get();
        } catch (Exception e) {
            LOG.error("DataStore  read exception {} ", e);
        }
        return Optional.absent();
    }

    public static void setBroker(final DataBroker broker) {
        BgpUtil.dataBroker = broker;
        initTransactionChain();
    }

    static synchronized void initTransactionChain() {
        try {
            if (fibTransact != null) {
                fibTransact.close();
                LOG.error("*** TxChain Close, *** Attempts: {}", txChainAttempts);
                fibTransact = null;
            }
        } catch (Exception ignore) {
        }
        BgpUtil.fibTransact = dataBroker.createTransactionChain(new BgpUtilTransactionChainListener());
        txChainAttempts++;
    }

    static class BgpUtilTransactionChainListener implements TransactionChainListener {
        @Override
        public void onTransactionChainFailed(TransactionChain<?, ?> transactionChain, AsyncTransaction<?, ?> asyncTransaction, Throwable throwable) {
            LOG.error("*** TxChain Creation Failed *** Attempts: {}", txChainAttempts);
            initTransactionChain();
        }

        @Override
        public void onTransactionChainSuccessful(TransactionChain<?, ?> transactionChain) {
            LOG.trace("TxChain Creation Success");
        }
    }

    public static DataBroker getBroker() {
        return dataBroker;
    }
}
