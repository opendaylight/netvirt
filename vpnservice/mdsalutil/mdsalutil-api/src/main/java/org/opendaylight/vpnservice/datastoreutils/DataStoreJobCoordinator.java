/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.datastoreutils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class DataStoreJobCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(DataStoreJobCoordinator.class);

    private static final int THREADPOOL_SIZE = Runtime.getRuntime().availableProcessors();

    private ForkJoinPool fjPool;
    private Map<Integer,Map<String, JobQueue>> jobQueueMap = new ConcurrentHashMap<>();

    private static DataStoreJobCoordinator instance;

    static {
        instance = new DataStoreJobCoordinator();
    }

    public static DataStoreJobCoordinator getInstance() {
        return instance;
    }

    /**
     *
     */
    private DataStoreJobCoordinator() {
        fjPool = new ForkJoinPool();

        for (int i = 0; i < THREADPOOL_SIZE; i++) {
            Map<String, JobQueue> jobEntriesMap = new ConcurrentHashMap<String, JobQueue>();
            jobQueueMap.put(i, jobEntriesMap);
        }

        new Thread(new JobQueueHandler()).start();
    }

    public void enqueueJob(String key,
                           Callable<List<ListenableFuture<Void>>> mainWorker) {
        enqueueJob(key, mainWorker, null, 0);
    }

    public void enqueueJob(String key,
                           Callable<List<ListenableFuture<Void>>> mainWorker,
                           RollbackCallable rollbackWorker) {
        enqueueJob(key, mainWorker, rollbackWorker, 0);
    }

    public void enqueueJob(String key,
                           Callable<List<ListenableFuture<Void>>> mainWorker,
                           int maxRetries) {
        enqueueJob(key, mainWorker, null, maxRetries);
    }

    /**
     *
     * @param key
     * @param mainWorker
     * @param rollbackWorker
     * @param maxRetries
     *
     * This is used by the external applications to enqueue a Job with an appropriate key.
     * A JobEntry is created and queued appropriately.
     */

    public void enqueueJob(String key,
                           Callable<List<ListenableFuture<Void>>> mainWorker,
                           RollbackCallable rollbackWorker,
                           int maxRetries) {
        JobEntry jobEntry = new JobEntry(key, mainWorker, rollbackWorker, maxRetries);
        Integer hashKey = getHashKey(key);
        LOG.debug("Obtained Hashkey: {}, for jobkey: {}", hashKey, key);

        Map<String, JobQueue> jobEntriesMap = jobQueueMap.get(hashKey);
        synchronized (jobEntriesMap) {
            JobQueue jobQueue = jobEntriesMap.get(key);
            if (jobQueue == null) {
                jobQueue = new JobQueue();
            }
            jobQueue.addEntry(jobEntry);
            jobEntriesMap.put(key, jobQueue);
        }

        jobQueueMap.put(hashKey, jobEntriesMap); // Is this really needed ?
    }

    /**
     * clearJob is used to cleanup the submitted job from the jobqueue.
     **/
    private void clearJob(JobEntry jobEntry) {
        Map<String, JobQueue> jobEntriesMap = jobQueueMap.get(getHashKey(jobEntry.getKey()));
        synchronized (jobEntriesMap) {
            JobQueue jobQueue = jobEntriesMap.get(jobEntry.getKey());
            jobQueue.setExecutingEntry(null);
            if (jobQueue.getWaitingEntries().isEmpty()) {
                jobEntriesMap.remove(jobEntry.getKey());
            }
        }
    }

    /**
     *
     * @param key
     * @return generated hashkey
     *
     * Used to generate the hashkey in to the jobQueueMap.
     */
    private Integer getHashKey(String key) {
        int code = key.hashCode();
        return (code % THREADPOOL_SIZE + THREADPOOL_SIZE) % THREADPOOL_SIZE;
    }

    /**
     * JobCallback class is used as a future callback for
     * main and rollback workers to handle success and failure.
     */
    private class JobCallback implements FutureCallback<List<Void>> {
        private JobEntry jobEntry;

        public JobCallback(JobEntry jobEntry) {
            this.jobEntry = jobEntry;
        }

        /**
         * @param voids
         * This implies that all the future instances have returned success. -- TODO: Confirm this
         */
        @Override
        public void onSuccess(List<Void> voids) {
            clearJob(jobEntry);
        }

        /**
         *
         * @param throwable
         * This method is used to handle failure callbacks.
         * If more retry needed, the retrycount is decremented and mainworker is executed again.
         * After retries completed, rollbackworker is executed.
         * If rollbackworker fails, this is a double-fault. Double fault is logged and ignored.
         */

        @Override
        public void onFailure(Throwable throwable) {
            LOG.warn("Job: {} failed with exception: {}", jobEntry, throwable.getStackTrace());
            if (jobEntry.getMainWorker() == null) {
                LOG.error("Job: {} failed with Double-Fault. Bailing Out.", jobEntry);
                clearJob(jobEntry);
                return;
            }

            if (jobEntry.decrementRetryCountAndGet() > 0) {
                MainTask worker = new MainTask(jobEntry);
                fjPool.execute(worker);
                return;
            }

            if (jobEntry.getRollbackWorker() != null) {
                jobEntry.setMainWorker(null);
                RollbackTask rollbackTask = new RollbackTask(jobEntry);
                fjPool.execute(rollbackTask);
                return;
            }

            clearJob(jobEntry);
        }
    }

    /**
     * RollbackTask is used to execute the RollbackCallable provided by the application
     * in the eventuality of a failure.
     */

    private class RollbackTask implements Runnable {
        private JobEntry jobEntry;

        public RollbackTask(JobEntry jobEntry) {
            this.jobEntry = jobEntry;
        }

        @Override
        public void run() {
            RollbackCallable callable = jobEntry.getRollbackWorker();
            callable.setFutures(jobEntry.getFutures());
            List<ListenableFuture<Void>> futures = null;

            try {
                futures = callable.call();
            } catch (Exception e){
                LOG.error("Exception when executing jobEntry: {}, exception: {}", jobEntry, e.getStackTrace());
                e.printStackTrace();
            }

            if (futures == null || futures.isEmpty()) {
                clearJob(jobEntry);
                return;
            }

            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture, new JobCallback(jobEntry));
            jobEntry.setFutures(futures);
        }
    }

    /**
     * MainTask is used to execute the MainWorker callable.
     */

    private class MainTask implements Runnable {
        private JobEntry jobEntry;

        public MainTask(JobEntry jobEntry) {
            this.jobEntry = jobEntry;
        }

        @Override
        public void run() {
            List<ListenableFuture<Void>> futures = null;
            try {
                futures = jobEntry.getMainWorker().call();
            } catch (Exception e){
                LOG.error("Exception when executing jobEntry: {}, exception: {}", jobEntry, e.getStackTrace());
                e.printStackTrace();
            }

            if (futures == null || futures.isEmpty()) {
                clearJob(jobEntry);
                return;
            }

            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture, new JobCallback(jobEntry));
            jobEntry.setFutures(futures);
        }
    }

    private class JobQueueHandler implements Runnable {
        @Override
        public void run() {
            LOG.debug("Starting JobQueue Handler Thread.");
            while (true) {
                try {
                    boolean jobAddedToPool = false;
                    for (int i = 0; i < THREADPOOL_SIZE; i++) {
                        Map<String, JobQueue> jobEntriesMap = jobQueueMap.get(i);
                        if (jobEntriesMap.isEmpty()) {
                            continue;
                        }

                        synchronized (jobEntriesMap) {
                            Iterator it = jobEntriesMap.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry<String, JobQueue> entry = (Map.Entry)it.next();
                                if (entry.getValue().getExecutingEntry() != null) {
                                    continue;
                                }
                                JobEntry jobEntry = entry.getValue().getWaitingEntries().poll();
                                if (jobEntry != null) {
                                    entry.getValue().setExecutingEntry(jobEntry);
                                    MainTask worker = new MainTask(jobEntry);
                                    fjPool.execute(worker);
                                    jobAddedToPool = true;
                                } else {
                                    it.remove();
                                }
                            }
                        }
                    }

                    if (!jobAddedToPool) {
                        TimeUnit.SECONDS.sleep(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }
}