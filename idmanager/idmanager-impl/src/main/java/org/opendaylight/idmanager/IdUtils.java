/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.idmanager;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.DelayedIdEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.AvailableIdsHolder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.AvailableIdsHolderBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ChildPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ChildPoolsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ChildPoolsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.IdEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.IdEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.IdEntriesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ReleasedIdsHolder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.id.pool.ReleasedIdsHolderBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.released.ids.DelayedIdEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.released.ids.DelayedIdEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.UnlockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.UnlockInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

class IdUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdUtils.class);
    private static final long DEFAULT_DELAY_TIME = 30;
    private static final long DEFAULT_AVAILABLE_ID_COUNT = 0;
    private static final int DEFAULT_BLOCK_SIZE_DIFF = 50;

    private static int BLADE_ID;
    static {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
            BLADE_ID = InetAddresses.coerceToInteger(InetAddress.getLocalHost());
            if (hostName.indexOf("-") > 0) {
                BLADE_ID = new Integer(hostName.split("-")[1].toString()).intValue();
            } else {
                LOGGER.error("Host name {} is not matching with the condition!! PL-X is expected", hostName);
            }
        } catch (Exception e) {
            LOGGER.error("IdManager - Exception - {}", e.getMessage());
        }
    }

    protected static InstanceIdentifier<IdEntries> getIdEntry(InstanceIdentifier<IdPool> poolName, String idKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdEntries> idEntriesBuilder = poolName
                .builder().child(IdEntries.class, new IdEntriesKey(idKey));
        InstanceIdentifier<IdEntries> idEntry = idEntriesBuilder.build();
        return idEntry;
    }

    protected static IdEntries createIdEntries(String idKey, long newIdVal) {
        return new IdEntriesBuilder().setKey(new IdEntriesKey(idKey))
                .setIdKey(idKey).setIdValue(newIdVal).build();
    }

    protected static DelayedIdEntries createDelayedIdEntry(long idValue, long delayTime) {
        return new DelayedIdEntriesBuilder()
                .setId(idValue)
                .setReadyTimeSec(delayTime).build();
    }

    protected static IdPool createGlobalPool(String poolName, long low, long high, long blockSize) {
        AvailableIdsHolder availableIdsHolder = createAvailableIdsHolder(low, high, low - 1);
        ReleasedIdsHolder releasedIdsHolder = createReleasedIdsHolder(DEFAULT_AVAILABLE_ID_COUNT, 0);
        int size = (int) blockSize;
        return new IdPoolBuilder().setKey(new IdPoolKey(poolName))
                .setBlockSize(size).setPoolName(poolName)
                .setAvailableIdsHolder(availableIdsHolder)
                .setReleasedIdsHolder(releasedIdsHolder).build();
    }

    protected static AvailableIdsHolder createAvailableIdsHolder(long low, long high, long cursor) {
        AvailableIdsHolder availableIdsHolder = new AvailableIdsHolderBuilder()
                .setStart(low).setEnd(high).setCursor(cursor).build();
        return availableIdsHolder;
    }

    protected static ReleasedIdsHolder createReleasedIdsHolder(long availableIdCount, long delayTime) {
        ReleasedIdsHolder releasedIdsHolder = new ReleasedIdsHolderBuilder()
                .setAvailableIdCount(availableIdCount)
                .setDelayedTimeSec(delayTime).build();
        return releasedIdsHolder;
    }

    protected static InstanceIdentifier<IdPool> getIdPoolInstance(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idPoolBuilder = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idPoolBuilder.build();
        return id;
    }

    protected static InstanceIdentifier<ReleasedIdsHolder> getReleasedIdsHolderInstance(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<ReleasedIdsHolder> releasedIdsHolder = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName)).child(ReleasedIdsHolder.class);
        InstanceIdentifier<ReleasedIdsHolder> releasedIds = releasedIdsHolder.build();
        return releasedIds;
    }

    /**
     * Changes made to releasedIds are not persisted in the datastore.
     * @param releasedIds
     * @return
     */
    protected static long getIdFromReleaseIdsIfAvailable(ReleasedIdsHolderBuilder releasedIds) {
        List<DelayedIdEntries> delayedIdEntries = releasedIds.getDelayedIdEntries();
        long newIdValue = -1;
        if (delayedIdEntries != null && !delayedIdEntries.isEmpty()) {
            processDelayList(releasedIds);
            if (releasedIds.getAvailableIdCount() > 0) {
                DelayedIdEntries delayedIdEntry= delayedIdEntries.get(0);
                newIdValue = delayedIdEntry.getId();
                delayedIdEntries.remove(delayedIdEntry);
                releasedIds.setDelayedIdEntries(delayedIdEntries);
                releasedIds.setAvailableIdCount(releasedIds.getAvailableIdCount() - 1);
            }
        }
        return newIdValue;
    }

    /**
     * Changes made to availableIds are not persisted to datastore.
     * @param availableIds
     * @return
     */
    protected static long getIdFromAvailableIds(AvailableIdsHolderBuilder availableIds) {
        long newIdValue = -1;
        if (availableIds != null && isIdAvailable(availableIds)) {
            newIdValue = availableIds.getCursor() + 1;
            availableIds.setCursor(newIdValue);
        }
        return newIdValue;
    }

    protected static boolean isIdAvailable(AvailableIdsHolderBuilder availableIds) {
        if (availableIds.getCursor() != null && availableIds.getEnd() != null)
            return availableIds.getCursor() < availableIds.getEnd();
        return false;
    }

    protected static String getLocalPoolName(String poolName) {
        return (poolName + "." + BLADE_ID);
    }

    protected static IdPool createLocalIdPool(String localPoolName, IdPool parentIdPool) {
        ReleasedIdsHolder releasedIdsHolder = createReleasedIdsHolder(DEFAULT_AVAILABLE_ID_COUNT, DEFAULT_DELAY_TIME);
        return new IdPoolBuilder().setKey(new IdPoolKey(localPoolName))
                .setPoolName(localPoolName)
                .setParentPoolName(parentIdPool.getPoolName())
                .setBlockSize(parentIdPool.getBlockSize())
                .setReleasedIdsHolder(releasedIdsHolder).build();
    }

    protected static ChildPools createChildPool(String childPoolName) {
        return new ChildPoolsBuilder().setKey(new ChildPoolsKey(childPoolName)).setChildPoolName(childPoolName).setLastAccessTime(System.currentTimeMillis() / 1000).build();
    }

    protected static AvailableIdsHolderBuilder getAvailableIdsHolderBuilder(IdPool pool) {
        AvailableIdsHolder availableIds = pool.getAvailableIdsHolder();
        if (availableIds != null )
            return new AvailableIdsHolderBuilder(availableIds);
        return new AvailableIdsHolderBuilder();
    }

    protected static ReleasedIdsHolderBuilder getReleaseIdsHolderBuilder(IdPool pool) {
        ReleasedIdsHolder releasedIds = pool.getReleasedIdsHolder();
        if (releasedIds != null)
            return new ReleasedIdsHolderBuilder(releasedIds);
        return new ReleasedIdsHolderBuilder();
    }

    /**
     * Changes made to releaseIds are not persisted to the Datastore. Method invoking should ensure that releaseIds gets persisted.
     * @param releasedIds
     */
    protected static void processDelayList(ReleasedIdsHolderBuilder releasedIds) {
        List<DelayedIdEntries> delayedIdEntries = releasedIds.getDelayedIdEntries();
        if (delayedIdEntries ==  null)
            return;
        long availableIdCount = releasedIds.getAvailableIdCount() == null ? 0 : releasedIds.getAvailableIdCount();
        int index = (int) availableIdCount;
        long currentTimeSec = System.currentTimeMillis() / 1000;
        DelayedIdEntry delayedIdEntry;
        while (index < delayedIdEntries.size()) {
            delayedIdEntry = delayedIdEntries.get(index);
            if (delayedIdEntry.getReadyTimeSec() > currentTimeSec) {
                break;
            }
            availableIdCount++;
            index++;
        }
        releasedIds.setAvailableIdCount(availableIdCount);
    }

    /**
     * Changes made to the parameters passed are not persisted to the Datastore. Method invoking should ensure that these gets persisted.
     * @param releasedIdsChild
     * @param releasedIdsParent
     * @param idCountToBeFreed
     */
    protected static void freeExcessAvailableIds(ReleasedIdsHolderBuilder releasedIdsChild, ReleasedIdsHolderBuilder releasedIdsParent, int idCountToBeFreed) {
        List<DelayedIdEntries> existingDelayedIdEntriesInParent = releasedIdsParent.getDelayedIdEntries();
        List<DelayedIdEntries> delayedIdEntriesChild = releasedIdsChild.getDelayedIdEntries();
        long availableIdCountParent = releasedIdsParent.getAvailableIdCount();
        long availableIdCountChild = releasedIdsChild.getAvailableIdCount();
        if (existingDelayedIdEntriesInParent == null) {
            existingDelayedIdEntriesInParent = new LinkedList<>();
        }
        idCountToBeFreed = Math.min(idCountToBeFreed, delayedIdEntriesChild.size());
        for (int index = 0; index < idCountToBeFreed; index++) {
            existingDelayedIdEntriesInParent.add(delayedIdEntriesChild.get(0));
            delayedIdEntriesChild.remove(0);
        }
        releasedIdsChild.setDelayedIdEntries(delayedIdEntriesChild).setAvailableIdCount(availableIdCountChild - idCountToBeFreed);
        releasedIdsParent.setDelayedIdEntries(existingDelayedIdEntriesInParent).setAvailableIdCount(availableIdCountParent + idCountToBeFreed);
    }

    protected static InstanceIdentifier<IdEntries> getIdEntriesInstanceIdentifier(String poolName, String idKey) {
        InstanceIdentifier<IdEntries> idEntries = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName)).child(IdEntries.class, new IdEntriesKey(idKey)).build();
        return idEntries;
    }

    protected static InstanceIdentifier<ChildPools> getChildPoolsInstanceIdentifier(String poolName, String localPoolName) {
        InstanceIdentifier<ChildPools> childPools = InstanceIdentifier
                .builder(IdPools.class)
                .child(IdPool.class, new IdPoolKey(poolName))
                .child(ChildPools.class, new ChildPoolsKey(localPoolName)).build();
        return childPools;
    }

    protected static long computeBlockSize(long low, long high) {
        long blockSize;

        long diff = high - low;
        if (diff > DEFAULT_BLOCK_SIZE_DIFF) {
            blockSize = diff / DEFAULT_BLOCK_SIZE_DIFF;
        } else {
            blockSize = 1;
        }
        return blockSize;
    }

    public static long getAvailableIdsCount(AvailableIdsHolderBuilder availableIds) {
        if(availableIds != null && isIdAvailable(availableIds)) {
            return availableIds.getEnd() - availableIds.getCursor();
        }
        return 0;
    }

    public static void lockPool(LockManagerService lockManager, String poolName) {
         LockInput input = new LockInputBuilder().setLockName(poolName).build();
         Future<RpcResult<Void>> result = lockManager.lock(input);
         try {
             if ((result != null) && (result.get().isSuccessful())) {
                 LOGGER.debug("Acquired lock {}", poolName);
             } else {
                 throw new RuntimeException(String.format("Unable to getLock for pool %s", poolName));
             }
         } catch (InterruptedException | ExecutionException e) {
             LOGGER.error("Unable to getLock for pool {}", poolName);
             throw new RuntimeException(String.format("Unable to getLock for pool %s", poolName), e.getCause());
         }
    }

    public static void unlockPool(LockManagerService lockManager, String poolName) {
        UnlockInput input = new UnlockInputBuilder().setLockName(poolName).build();
        Future<RpcResult<Void>> result = lockManager.unlock(input);
        try {
            if ((result != null) && (result.get().isSuccessful())) {
                LOGGER.debug("Unlocked {}", poolName);
            } else {
                LOGGER.debug("Unable to unlock pool {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Unable to unlock for pool {}", poolName);
            throw new RuntimeException(String.format("Unable to unlock pool %s", poolName), e.getCause());
        }
   }
}