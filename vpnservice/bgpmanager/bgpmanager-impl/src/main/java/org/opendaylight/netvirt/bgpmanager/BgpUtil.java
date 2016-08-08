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
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BgpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static DataBroker  dataBroker;
    private static BindingTransactionChain fibTransact;
    public static final int PERIODICITY = 500;
    private static AtomicInteger pendingWrTransaction = new AtomicInteger(0);
    public static final int BATCH_SIZE = 1000;
    public static Integer batchSize;
    public static Integer batchInterval;
    private static int txChainAttempts = 0;

    private static BlockingQueue<ActionableResource> bgpResourcesBufferQ = new LinkedBlockingQueue<>();

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

    static void registerWithBatchManager(ResourceHandler resourceHandler) {
        ResourceBatchingManager resBatchingManager = ResourceBatchingManager.getInstance();
        resBatchingManager.registerBatchableResource("BGP-RESOURCES", bgpResourcesBufferQ, resourceHandler);
    }

    static <T extends DataObject> void update(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    public static <T extends DataObject> void write(DataBroker broker, final LogicalDatastoreType datastoreType,
                                                    final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    static <T extends DataObject> void delete(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(null);
        bgpResourcesBufferQ.add(actResource);
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
