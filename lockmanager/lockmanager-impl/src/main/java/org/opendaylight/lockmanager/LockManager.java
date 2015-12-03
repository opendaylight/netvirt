/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.lockmanager;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.TryLockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.UnlockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.locks.Lock;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

public class LockManager implements LockManagerService {
    private static final Logger LOG = LoggerFactory.getLogger(LockManager.class);

    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final int DEFAULT_WAIT_TIME_IN_MILLIS = 1000;

    private final DataBroker broker;

    public LockManager(final DataBroker db) {
        broker = db;
    }

    @Override
    public Future<RpcResult<Void>> lock(LockInput input) {
        String lockName = input.getLockName();
        LOG.info("Locking {}" , lockName);
        InstanceIdentifier<Lock> lockInstanceIdentifier = LockManagerUtils.getLockInstanceIdentifier(lockName);
        Lock lockData = LockManagerUtils.buildLockData(lockName);
        try {
            getLock(lockInstanceIdentifier, lockData);
            RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.success();
            LOG.info("Acquired lock {}" , lockName);
            return Futures.immediateFuture(lockRpcBuilder.build());
        } catch (InterruptedException e) {
            RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.failed();
            LOG.info("Failed to get lock {}" , lockName);
            return Futures.immediateFuture(lockRpcBuilder.build());
        }
    }

    @Override
    public Future<RpcResult<Void>> tryLock(TryLockInput input) {
        String lockName = input.getLockName();
        LOG.info("Locking {}" , lockName);
        long waitTime = input.getTime() == null ? DEFAULT_WAIT_TIME_IN_MILLIS * DEFAULT_RETRY_COUNT : input.getTime();
        TimeUnit timeUnit = (TimeUnit) (input.getTimeUnit() == null ? TimeUnit.MILLISECONDS: LockManagerUtils.convertToTimeUnit(input.getTimeUnit()));
        waitTime = LockManagerUtils.convertToMillis(waitTime, timeUnit);
        long retryCount = waitTime / DEFAULT_WAIT_TIME_IN_MILLIS;
        InstanceIdentifier<Lock> lockInstanceIdentifier = LockManagerUtils.getLockInstanceIdentifier(lockName);
        Lock lockData = LockManagerUtils.buildLockData(lockName);
        try {
            if (getLock(lockInstanceIdentifier, lockData, retryCount)) {
                RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.success();
                LOG.info("Acquired lock {}" , lockName);
                return Futures.immediateFuture(lockRpcBuilder.build());
            }
            RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.failed();
            LOG.info("Failed to get lock {}" , lockName);
            return Futures.immediateFuture(lockRpcBuilder.build());
        } catch (Exception e) {
            RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.failed();
            LOG.info("Failed to get lock {}" , lockName);
            return Futures.immediateFuture(lockRpcBuilder.build());
        }
    }

    @Override
    public Future<RpcResult<Void>> unlock(UnlockInput input) {
        String lockName = input.getLockName();
        LOG.info("Unlocking {}" , lockName);
        InstanceIdentifier<Lock> lockInstanceIdentifier = LockManagerUtils.getLockInstanceIdentifier(lockName);
        unlock(lockName, lockInstanceIdentifier);
        RpcResultBuilder<Void> lockRpcBuilder = RpcResultBuilder.success();
        return Futures.immediateFuture(lockRpcBuilder.build());
    }

    /**
     * Try to acquire lock indefinitely until it is successful.
     * @param lockInstanceIdentifier
     * @param lockData
     */
    private void getLock(final InstanceIdentifier<Lock> lockInstanceIdentifier, final Lock lockData) throws InterruptedException {
        try {
            if (!readWriteLock(lockInstanceIdentifier, lockData)) {
                LOG.debug("Already locked trying after {}", DEFAULT_WAIT_TIME_IN_MILLIS);
                LockManagerUtils.sleep(DEFAULT_WAIT_TIME_IN_MILLIS);
                getLock(lockInstanceIdentifier, lockData);
            }
        } catch (ExecutionException e) {
            LOG.error("In getLock unable to get lock due to {}, trying again", e.getMessage());
            LockManagerUtils.sleep(DEFAULT_WAIT_TIME_IN_MILLIS);
            getLock(lockInstanceIdentifier, lockData);
        }
    }

    /**
     * Try to acquire lock for mentioned retryCount. Returns true if successfully acquired lock.
     * @param lockInstanceIdentifier
     * @param lockData
     * @param retryCount
     * @return
     * @throws InterruptedException
     */
    private boolean getLock(InstanceIdentifier<Lock> lockInstanceIdentifier,
            Lock lockData, long retryCount) throws InterruptedException {
        if (retryCount < 0) {
            return false;
        }
        try {
            if (!readWriteLock(lockInstanceIdentifier, lockData)) {
                LOG.debug("Already locked trying after {}, retry value {}", DEFAULT_WAIT_TIME_IN_MILLIS, retryCount);
                LockManagerUtils.sleep(DEFAULT_WAIT_TIME_IN_MILLIS);
                return getLock(lockInstanceIdentifier, lockData, retryCount - 1);
            }
        } catch (ExecutionException e) {
            LOG.error("In getLock unable to get lock due to {}, trying again, retry value {}", e.getMessage(), retryCount);
            LockManagerUtils.sleep(DEFAULT_WAIT_TIME_IN_MILLIS);
            return getLock(lockInstanceIdentifier, lockData, retryCount - 1);
        }
        return true;
    }

    /**
     * Read and write the lock immediately if available. Returns true if successfully locked.
     * @param lockInstanceIdentifier
     * @param lockData
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private boolean readWriteLock (final InstanceIdentifier<Lock> lockInstanceIdentifier, final Lock lockData) throws InterruptedException, ExecutionException {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        Optional<Lock> result = Optional.absent();
        result = tx.read(LogicalDatastoreType.OPERATIONAL, lockInstanceIdentifier).get();
        if (!result.isPresent()) {
            tx.put(LogicalDatastoreType.OPERATIONAL, lockInstanceIdentifier, lockData, true);
            CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
            futures.get();
            return true;
        }
        if (result.get().getLockOwner() == Thread.currentThread().getName()) {
            return true;
        }
        return false;
    }

    private void unlock(final String lockName, final InstanceIdentifier<Lock> lockInstanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        Optional<Lock> result = Optional.absent();
        try {
            result = tx.read(LogicalDatastoreType.OPERATIONAL, lockInstanceIdentifier).get();
            if (!result.isPresent()) {
                LOG.info("{} is already unlocked", lockName);
                return;
            }
            tx.delete(LogicalDatastoreType.OPERATIONAL, lockInstanceIdentifier);
            CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
            futures.get();
        } catch (Exception e) {
            LOG.error("In unlock unable to unlock due to {}", e.getMessage());
        }
    }


}
