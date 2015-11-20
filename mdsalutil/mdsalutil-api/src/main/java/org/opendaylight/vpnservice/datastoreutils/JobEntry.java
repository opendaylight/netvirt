/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.datastoreutils;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JobEntry is the entity built per job submitted by the application and
 * enqueued to the book-keeping data structure.
 */
public class JobEntry {
    final private String key;
    private Callable<List<ListenableFuture<Void>>> mainWorker;
    final private RollbackCallable rollbackWorker;
    private AtomicInteger retryCount;
    private List<ListenableFuture<Void>> futures;

    public JobEntry(String key,
                    Callable<List<ListenableFuture<Void>>> mainWorker,
                    RollbackCallable rollbackWorker,
                    int maxRetries) {
        this.key = key;
        this.mainWorker = mainWorker;
        this.rollbackWorker = rollbackWorker;
        retryCount = new AtomicInteger(maxRetries);
    }

    /**
     *
     * @return
     *
     * The key provided by the application that segregates the
     * callables that can be run parallely.
     * NOTE: Currently, this is a string. Can be converted to Object where
     * Object implementation should provide the hashcode and equals methods.
     */
    public String getKey() {
        return key;
    }

    public Callable<List<ListenableFuture<Void>>> getMainWorker() {
        return mainWorker;
    }

    public void setMainWorker(Callable<List<ListenableFuture<Void>>> mainWorker) {
        this.mainWorker = mainWorker;
    }

    public RollbackCallable getRollbackWorker() {
        return rollbackWorker;
    }

    public int decrementRetryCountAndGet() {
        return retryCount.decrementAndGet();
    }

    public List<ListenableFuture<Void>> getFutures() {
        return futures;
    }

    public void setFutures(List<ListenableFuture<Void>> futures) {
        this.futures = futures;
    }

    @Override
    public String toString() {
        return "JobEntry{" +
                "key='" + key + '\'' +
                ", mainWorker=" + mainWorker +
                ", rollbackWorker=" + rollbackWorker +
                ", retryCount=" + retryCount +
                ", futures=" + futures +
                '}';
    }
}