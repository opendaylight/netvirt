/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.idmanager;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.AvailableIdsHolder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.AvailableIdsHolderBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ChildPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.IdEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ReleasedIdsHolder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ReleasedIdsHolderBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.released.ids.DelayedIdEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.released.ids.DelayedIdEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

public class IdManager implements IdManagerService, AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(IdManager.class);

	private static final long DEFAULT_IDLE_TIME = 24 * 60 * 60;

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private LockManagerService lockManager;

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("IDManager Closed");
    }

    public IdManager(final DataBroker db) {
        broker = db;
    }

    public void setLockManager(LockManagerService lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public Future<RpcResult<Void>> createIdPool(CreateIdPoolInput input) {
        LOG.debug("createIdPool called with input {}", input);
        String poolName = input.getPoolName();
        long low = input.getLow();
        long high = input.getHigh();
        long blockSize = IdUtils.computeBlockSize(low, high);
        RpcResultBuilder<Void> createIdPoolRpcBuilder;
        IdUtils.lockPool(lockManager, poolName);
        try {
            InstanceIdentifier<IdPool> idPoolInstanceIdentifier = IdUtils.getIdPoolInstance(poolName);
            poolName = poolName.intern();
            IdPool idPool;
            idPool = createGlobalPool(poolName, low, high, blockSize, idPoolInstanceIdentifier);
            String localPoolName = IdUtils.getLocalPoolName(poolName);
            if (createLocalPool(localPoolName, idPool)) {
                LOG.debug("Updating global id pool {} with childPool {}", poolName, localPoolName);
                updateChildPool(poolName, localPoolName);
            }
            createIdPoolRpcBuilder = RpcResultBuilder.success();
        } catch (Exception ex) {
            LOG.error("Creation of Id Pool {} failed due to {}", poolName, ex);
            createIdPoolRpcBuilder = RpcResultBuilder.failed();
            createIdPoolRpcBuilder.withError(ErrorType.APPLICATION, ex.getMessage());
        } finally {
            IdUtils.unlockPool(lockManager, poolName);
        }
        return Futures.immediateFuture(createIdPoolRpcBuilder.build());
    }

    @Override
    public Future<RpcResult<AllocateIdOutput>> allocateId(AllocateIdInput input) {
        LOG.debug("AllocateId called with input {}", input);
        String idKey = input.getIdKey();
        String poolName = input.getPoolName();
        String localPoolName = IdUtils.getLocalPoolName(poolName);
        RpcResultBuilder<AllocateIdOutput> allocateIdRpcBuilder;
        long newIdValue = -1;
        AllocateIdOutputBuilder output = new AllocateIdOutputBuilder();
        try {
            newIdValue = allocateIdFromLocalPool(localPoolName, idKey);
            output.setIdValue(newIdValue);
            allocateIdRpcBuilder = RpcResultBuilder.success();
            allocateIdRpcBuilder.withResult(output.build());
        } catch (Exception ex) {
            LOG.error("Allocate id in pool {} failed due to {}", poolName, ex);
            allocateIdRpcBuilder = RpcResultBuilder.failed();
            allocateIdRpcBuilder.withError(ErrorType.APPLICATION, ex.getMessage());
        }
        return Futures.immediateFuture(allocateIdRpcBuilder.build());
    }

    @Override
    public Future<RpcResult<Void>> deleteIdPool(DeleteIdPoolInput input) {
        LOG.debug("DeleteIdPool called with input {}", input);
        String poolName = input.getPoolName();
        RpcResultBuilder<Void> deleteIdPoolRpcBuilder;
        try {
            InstanceIdentifier<IdPool> idPoolToBeDeleted = IdUtils.getIdPoolInstance(poolName);
            poolName = poolName.intern();
            synchronized(poolName) {
                IdPool idPool = getIdPool(idPoolToBeDeleted);
                List<ChildPools> childPoolList = idPool.getChildPools();
                if (childPoolList != null) {
                    for (ChildPools childPoolName : childPoolList) {
                        deletePool(childPoolName.getChildPoolName());
                    }
                }
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, idPoolToBeDeleted);
                LOG.debug("Deleted id pool {}", poolName);
            }
            deleteIdPoolRpcBuilder = RpcResultBuilder.success();
        }
        catch (Exception ex) {
            LOG.error("Delete id in pool {} failed due to {}", poolName, ex);
            deleteIdPoolRpcBuilder = RpcResultBuilder.failed();
            deleteIdPoolRpcBuilder.withError(ErrorType.APPLICATION, ex.getMessage());
        }
        return Futures.immediateFuture(deleteIdPoolRpcBuilder.build());
    }

    @Override
    public Future<RpcResult<Void>> releaseId(ReleaseIdInput input) {
        String poolName = input.getPoolName();
        String idKey = input.getIdKey();
        RpcResultBuilder<Void> releaseIdRpcBuilder;
        try {
            releaseIdFromLocalPool(IdUtils.getLocalPoolName(poolName), idKey);
            releaseIdRpcBuilder = RpcResultBuilder.success();
        } catch (Exception ex) {
            LOG.error("Release id {} from pool {} failed due to {}", idKey, poolName, ex);
            releaseIdRpcBuilder = RpcResultBuilder.failed();
            releaseIdRpcBuilder.withError(ErrorType.APPLICATION, ex.getMessage());
        }
        return Futures.immediateFuture(releaseIdRpcBuilder.build());
    }

    private long allocateIdFromLocalPool(String localPoolName, String idKey) {
        long newIdValue = -1;
        InstanceIdentifier<IdPool> idPoolInstanceIdentifier = IdUtils.getIdPoolInstance(localPoolName);
        localPoolName = localPoolName.intern();
        synchronized (localPoolName) {
            IdEntries newIdEntry;
            IdPool pool = getIdPool(idPoolInstanceIdentifier);
            List<IdEntries> idEntries = pool.getIdEntries();

            AvailableIdsHolderBuilder availableIds = IdUtils.getAvailableIdsHolderBuilder(pool);
            ReleasedIdsHolderBuilder releasedIds = IdUtils.getReleaseIdsHolderBuilder(pool);
            //Calling cleanupExcessIds since there could be excessive ids.
            cleanupExcessIds(availableIds, releasedIds, pool.getParentPoolName(), pool.getBlockSize());
            if (idEntries == null) {
                idEntries = new LinkedList<IdEntries>();
            } else {
                InstanceIdentifier<IdEntries> existingId = IdUtils.getIdEntry(idPoolInstanceIdentifier, idKey);
                Optional<IdEntries> existingIdEntry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, existingId);
                if (existingIdEntry.isPresent()) {
                    newIdValue = existingIdEntry.get().getIdValue();
                    LOG.debug("Existing id {} for the key {} ", idKey, newIdValue);
                    InstanceIdentifier<ReleasedIdsHolder> releasedIdsHolderInstanceIdentifier = InstanceIdentifier
                                .builder(IdPools.class).child(IdPool.class, new IdPoolKey(localPoolName)).child(ReleasedIdsHolder.class).build();
                        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, releasedIdsHolderInstanceIdentifier, releasedIds.build());
                    return newIdValue;
                }
            }
            newIdValue = getIdFromPool(pool, availableIds, releasedIds);
            newIdEntry = IdUtils.createIdEntries(idKey, newIdValue);
            idEntries.add(newIdEntry);
            pool = new IdPoolBuilder(pool).setIdEntries(idEntries)
                   .setAvailableIdsHolder(availableIds.build()).setReleasedIdsHolder(releasedIds.build()).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier, pool);
            updateChildPool(pool.getParentPoolName(), localPoolName);
        }
        return newIdValue;
    }

    private long getIdFromPool(IdPool pool, AvailableIdsHolderBuilder availableIds, ReleasedIdsHolderBuilder releasedIds) {
        long newIdValue = -1;
        while (true) {
            newIdValue = IdUtils.getIdFromReleaseIdsIfAvailable(releasedIds);
            if (newIdValue != -1) {
                LOG.debug("Retrieved id value {} from released id holder", newIdValue);
                return newIdValue;
            }
            newIdValue = IdUtils.getIdFromAvailableIds(availableIds);
            if (newIdValue != -1) {
                LOG.debug("Creating a new id {} for the pool: {} ", newIdValue, pool.getPoolName());
                return newIdValue;
            }
            long idCount = allocateIdBlockFromParentPool(pool.getParentPoolName(), availableIds, releasedIds);
            if (idCount <= 0) {
                LOG.debug("Unable to allocate Id block from global pool");
                throw new RuntimeException(String.format("Ids exhausted for pool : %s", pool.getPoolName()));
            }
        }
    }

    /**
     * Changes made to releaseIds and AvailableIds are not persisted.
     * @param availableIds
     * @param releasedIds
     * @param parentPoolName
     * @param blockSize
     */
    private void cleanupExcessIds(AvailableIdsHolderBuilder availableIds, ReleasedIdsHolderBuilder releasedIds, String parentPoolName, int blockSize) {
        IdUtils.processDelayList(releasedIds);
        long totalAvailableIdCount = releasedIds.getAvailableIdCount() + IdUtils.getAvailableIdsCount(availableIds);
        if (totalAvailableIdCount > blockSize * 2) {
            parentPoolName = parentPoolName.intern();
            InstanceIdentifier<ReleasedIdsHolder> releasedIdInstanceIdentifier = IdUtils.getReleasedIdsHolderInstance(parentPoolName);
            IdUtils.lockPool(lockManager, parentPoolName);
            try {
                Optional<ReleasedIdsHolder> releasedIdsHolder = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, releasedIdInstanceIdentifier);
                ReleasedIdsHolderBuilder releasedIdsParent;
                if (!releasedIdsHolder.isPresent()) {
                    LOG.error("ReleasedIds not present in parent pool. Unable to cleanup excess ids");
                    return;
                }
                releasedIdsParent = new ReleasedIdsHolderBuilder(releasedIdsHolder.get());
                LOG.debug("Releasing excesss Ids from local pool");
                IdUtils.freeExcessAvailableIds(releasedIds, releasedIdsParent, blockSize);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, releasedIdInstanceIdentifier, releasedIdsParent.build());
            } finally {
                IdUtils.unlockPool(lockManager, parentPoolName);
            }
        }
    }

    /**
     * Changes made to availableIds and releasedIds will not be persisted to the datastore
     * @param parentPoolName
     * @param availableIdsBuilder
     * @param releasedIdsBuilder
     * @return
     */
    private long allocateIdBlockFromParentPool(String parentPoolName,
            AvailableIdsHolderBuilder availableIdsBuilder, ReleasedIdsHolderBuilder releasedIdsBuilder) {
        LOG.debug("Allocating block of id from parent pool {}", parentPoolName);
        InstanceIdentifier<IdPool> idPoolInstanceIdentifier = IdUtils.getIdPoolInstance(parentPoolName);
        parentPoolName = parentPoolName.intern();
        long idCount = -1;
        IdUtils.lockPool(lockManager, parentPoolName);
        try {
            IdPool parentIdPool = getIdPool(idPoolInstanceIdentifier);
            ReleasedIdsHolderBuilder releasedIdsBuilderParent = IdUtils.getReleaseIdsHolderBuilder(parentIdPool);
            while (true) {
                idCount = allocateIdBlockFromReleasedIdsHolder(releasedIdsBuilder, releasedIdsBuilderParent, parentIdPool);
                if (idCount > 0) {
                    return idCount;
                }
                idCount = allocateIdBlockFromAvailableIdsHolder(availableIdsBuilder, parentIdPool);
                if (idCount > 0) {
                    return idCount;
                }
                idCount = getIdsFromOtherChildPools(releasedIdsBuilderParent, parentIdPool);
                if (idCount <= 0) {
                    LOG.debug("Unable to allocate Id block from global pool");
                    throw new RuntimeException(String.format("Ids exhausted for pool : %s", parentPoolName));
                }
            }
        }
        finally {
            IdUtils.unlockPool(lockManager, parentPoolName);
        }
    }

    private long getIdsFromOtherChildPools(ReleasedIdsHolderBuilder releasedIdsBuilderParent, IdPool parentIdPool) {
        List<ChildPools> childPoolsList = parentIdPool.getChildPools();
        // Sorting the child pools on last accessed time so that the pool that was not accessed for a long time comes first.
        Collections.sort(childPoolsList, new Comparator<ChildPools>() {
            @Override
            public int compare(ChildPools childPool1, ChildPools childPool2) {
                return childPool1.getLastAccessTime().compareTo(childPool2.getLastAccessTime());
            }
        });
        long currentTime = System.currentTimeMillis() / 1000;
        for (ChildPools childPools : childPoolsList) {
            if (childPools.getLastAccessTime() + DEFAULT_IDLE_TIME < currentTime) {
                break;
            }
            if (!childPools.getChildPoolName().equals(IdUtils.getLocalPoolName(parentIdPool.getPoolName()))) {
                InstanceIdentifier<IdPool> idPoolInstanceIdentifier = IdUtils.getIdPoolInstance(childPools.getChildPoolName());
                IdPool otherChildPool = getIdPool(idPoolInstanceIdentifier);
                ReleasedIdsHolderBuilder releasedIds = IdUtils.getReleaseIdsHolderBuilder(otherChildPool);
                AvailableIdsHolderBuilder availableIds = IdUtils.getAvailableIdsHolderBuilder(otherChildPool);
                long totalAvailableIdCount = releasedIds.getDelayedIdEntries().size() + IdUtils.getAvailableIdsCount(availableIds);
                List<DelayedIdEntries> delayedIdEntriesChild = releasedIds.getDelayedIdEntries();
                List<DelayedIdEntries> delayedIdEntriesParent = releasedIdsBuilderParent.getDelayedIdEntries();
                if (delayedIdEntriesParent == null) {
                    delayedIdEntriesParent = new LinkedList<>();
                }
                delayedIdEntriesParent.addAll(delayedIdEntriesChild);
                delayedIdEntriesChild.removeAll(delayedIdEntriesChild);
                while (IdUtils.isIdAvailable(availableIds)) {
                    long cursor = availableIds.getCursor() + 1;
                    delayedIdEntriesParent.add(new DelayedIdEntriesBuilder().setId(cursor).setReadyTimeSec(System.currentTimeMillis()).build());
                    availableIds.setCursor(cursor);
                }
                long count = releasedIdsBuilderParent.getAvailableIdCount() + totalAvailableIdCount;
                releasedIdsBuilderParent.setDelayedIdEntries(delayedIdEntriesParent).setAvailableIdCount(count);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier,
                        new IdPoolBuilder(otherChildPool).setAvailableIdsHolder(availableIds.build()).setReleasedIdsHolder(releasedIds.build()).build());
                return totalAvailableIdCount;
            }
        }
        return 0;
    }

	private long allocateIdBlockFromReleasedIdsHolder(ReleasedIdsHolderBuilder releasedIdsBuilderChild, ReleasedIdsHolderBuilder releasedIdsBuilderParent, IdPool parentIdPool) {
        if (releasedIdsBuilderParent.getAvailableIdCount() == 0) {
            LOG.debug("Ids unavailable in releasedIds of parent pool {}", parentIdPool);
            return 0;
        }
        List<DelayedIdEntries> delayedIdEntriesParent = releasedIdsBuilderParent.getDelayedIdEntries();
        List<DelayedIdEntries> delayedIdEntriesChild = releasedIdsBuilderChild.getDelayedIdEntries();
        if (delayedIdEntriesChild == null) {
            delayedIdEntriesChild = new LinkedList<DelayedIdEntries>();
        }
        int idCount = Math.min(delayedIdEntriesParent.size(), parentIdPool.getBlockSize());
        List<DelayedIdEntries> idEntriesToBeRemoved = delayedIdEntriesParent.subList(0, idCount);
        delayedIdEntriesChild.addAll(0, idEntriesToBeRemoved);
        delayedIdEntriesParent.removeAll(idEntriesToBeRemoved);
        releasedIdsBuilderParent.setDelayedIdEntries(delayedIdEntriesParent);
        releasedIdsBuilderChild.setDelayedIdEntries(delayedIdEntriesChild);
        releasedIdsBuilderChild.setAvailableIdCount(releasedIdsBuilderChild.getAvailableIdCount() + idCount);
        InstanceIdentifier<ReleasedIdsHolder> releasedIdsHolderInstanceIdentifier = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(parentIdPool.getPoolName())).child(ReleasedIdsHolder.class).build();
        releasedIdsBuilderParent.setAvailableIdCount(releasedIdsBuilderParent.getAvailableIdCount() - idCount);
        LOG.debug("Allocated {} ids from releasedIds of parent pool {}", idCount, parentIdPool);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, releasedIdsHolderInstanceIdentifier, releasedIdsBuilderParent.build());
        return idCount;
    }

    private long allocateIdBlockFromAvailableIdsHolder(AvailableIdsHolderBuilder availableIdsBuilder, IdPool parentIdPool) {
        long idCount = 0;
        AvailableIdsHolderBuilder availableIdsBuilderParent = IdUtils.getAvailableIdsHolderBuilder(parentIdPool);
        long end = availableIdsBuilderParent.getEnd();
        long cur = availableIdsBuilderParent.getCursor();
        if (!IdUtils.isIdAvailable(availableIdsBuilderParent)) {
            LOG.debug("Ids exhausted in parent pool {}", parentIdPool);
            return idCount;
        }
        // Update availableIdsHolder of Local Pool
        availableIdsBuilder.setStart(cur + 1);
        idCount = Math.min(end - cur, parentIdPool.getBlockSize());
        availableIdsBuilder.setEnd(cur + idCount);
        availableIdsBuilder.setCursor(cur);
        // Update availableIdsHolder of Global Pool
        InstanceIdentifier<AvailableIdsHolder> availableIdsHolderInstanceIdentifier = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(parentIdPool.getPoolName())).child(AvailableIdsHolder.class).build();
        availableIdsBuilderParent.setCursor(cur + idCount);
        LOG.debug("Allocated {} ids from availableIds of global pool {}", idCount, parentIdPool);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, availableIdsHolderInstanceIdentifier, availableIdsBuilderParent.build());
        return idCount;
    }

    private void releaseIdFromLocalPool(String poolName, String idKey) {
        InstanceIdentifier<IdPool> idPoolInstanceIdentifier = IdUtils.getIdPoolInstance(poolName);
        poolName = poolName.intern();
        synchronized (poolName) {
            IdPool pool = getIdPool(idPoolInstanceIdentifier);
            List<IdEntries> idEntries = pool.getIdEntries();
            List<IdEntries> newIdEntries = idEntries;
            if (idEntries == null) {
                throw new RuntimeException("Id Entries does not exist");
            }
            InstanceIdentifier<IdEntries> existingId = IdUtils.getIdEntry(idPoolInstanceIdentifier, idKey);
            Optional<IdEntries> existingIdEntryObject = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, existingId);
            if (!existingIdEntryObject.isPresent()) {
                throw new RuntimeException(String.format("Specified Id key %s does not exist in id pool %s", idKey, poolName));
            }
            IdEntries existingIdEntry = existingIdEntryObject.get();
            long idValue = existingIdEntry.getIdValue();
            newIdEntries.remove(existingIdEntry);
            ReleasedIdsHolderBuilder releasedIds = IdUtils.getReleaseIdsHolderBuilder(pool);
            AvailableIdsHolderBuilder availableIds = IdUtils.getAvailableIdsHolderBuilder(pool);
            long delayTime = System.currentTimeMillis() / 1000 + releasedIds.getDelayedTimeSec();
            DelayedIdEntries delayedIdEntry = IdUtils.createDelayedIdEntry(idValue, delayTime);
            List<DelayedIdEntries> delayedIdEntries = releasedIds.getDelayedIdEntries();
            if (delayedIdEntries == null) {
                delayedIdEntries = new LinkedList<DelayedIdEntries>();
            }
            delayedIdEntries.add(delayedIdEntry);
            long availableIdCount = releasedIds
                    .getAvailableIdCount() == null ? 0
                            : releasedIds.getAvailableIdCount();
            releasedIds.setDelayedIdEntries(delayedIdEntries);
            releasedIds.setAvailableIdCount(availableIdCount);
            //Calling cleanupExcessIds since there could be excessive ids.
            cleanupExcessIds(availableIds, releasedIds, pool.getParentPoolName(), pool.getBlockSize());
            pool = new IdPoolBuilder(pool).setIdEntries(newIdEntries)
                    .setAvailableIdsHolder(availableIds.build())
                    .setReleasedIdsHolder(releasedIds.build()).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier, pool);
            LOG.debug("Released id ({}, {}) from pool {}", idKey, idValue, poolName);
        }
    }

    private IdPool createGlobalPool(String poolName, long low, long high,
            long blockSize, InstanceIdentifier<IdPool> idPoolInstanceIdentifier) {
        IdPool idPool;
        Optional<IdPool> existingIdPool = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier);
        if (!existingIdPool.isPresent()) {
            LOG.debug("Creating new global pool {}", poolName);
            idPool = IdUtils.createGlobalPool(poolName, low, high, blockSize);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier, idPool);
        }
        else {
            idPool = existingIdPool.get();
            LOG.debug("GlobalPool exists {}", idPool);
        }
        return idPool;
    }

    private boolean createLocalPool(String localPoolName, IdPool idPool) {
        localPoolName = localPoolName.intern();
        synchronized (localPoolName) {
            InstanceIdentifier<IdPool> localIdPoolInstanceIdentifier = IdUtils.getIdPoolInstance(localPoolName);
            Optional<IdPool> localIdPool = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, localIdPoolInstanceIdentifier);
            if (!localIdPool.isPresent()) {
                LOG.debug("Creating new local pool");
                IdPool newLocalIdPool = IdUtils.createLocalIdPool(localPoolName, idPool);
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, localIdPoolInstanceIdentifier, newLocalIdPool);
                LOG.debug("Local pool created {}", newLocalIdPool);
                return true;
            }
        }
        return false;
    }

    private void deletePool(String poolName) {
        InstanceIdentifier<IdPool> idPoolToBeDeleted = IdUtils.getIdPoolInstance(poolName);
        synchronized (poolName) {
            Optional<IdPool> idPool = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, idPoolToBeDeleted);
            if (idPool.isPresent()) {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, idPoolToBeDeleted);
                LOG.debug("Deleted local pool {}", poolName);
            }
        }
    }

    private IdPool getIdPool(InstanceIdentifier<IdPool> idPoolInstanceIdentifier) {
        Optional<IdPool> idPool = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, idPoolInstanceIdentifier);
        if (!idPool.isPresent()) {
            throw new RuntimeException(String.format("Specified pool %s does not exist" , idPool));
        }
        return idPool.get();
    }

    private void updateChildPool(String poolName, String localPoolName) {
        ChildPools childPool = IdUtils.createChildPool(localPoolName);
        InstanceIdentifier<ChildPools> childPoolInstanceIdentifier = IdUtils.getChildPoolsInstanceIdentifier(poolName, localPoolName);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, childPoolInstanceIdentifier, childPool);
    }
}
