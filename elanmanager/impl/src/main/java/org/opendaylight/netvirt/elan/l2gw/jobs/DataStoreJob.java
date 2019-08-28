/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DataStoreJob implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(DataStoreJob.class);
    private static final long RETRY_WAIT_BASE_TIME = 1000;
    protected AtomicInteger leftTrials = new AtomicInteger(5);
    protected String jobKey;
    private final Scheduler scheduler;
    private final JobCoordinator jobCoordinator;

    public DataStoreJob(String jobKey, Scheduler scheduler, JobCoordinator jobCoordinator) {
        this.jobKey = jobKey;
        this.scheduler = scheduler;
        this.jobCoordinator = jobCoordinator;
    }

    protected void processResult(ListenableFuture<Void> ft) {
        Futures.addCallback(ft, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LOG.debug("success. {}", jobKey);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (leftTrials.decrementAndGet() > 0) {
                    long waitTime = (RETRY_WAIT_BASE_TIME * 10) / leftTrials.get();
                    scheduler.getScheduledExecutorService().schedule(() -> {
                        jobCoordinator.enqueueJob(jobKey, DataStoreJob.this);
                    }, waitTime, TimeUnit.MILLISECONDS);
                } else {
                    LOG.error("failed. {}", jobKey);
                }
            }
        }, MoreExecutors.directExecutor());
    }
}
