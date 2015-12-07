package org.opendaylight.idmanager.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInputBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.UnlockInput;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.Futures;


@RunWith(MockitoJUnitRunner.class)
public class IdManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(IdManagerTest.class);
    private static int BLADE_ID;
    static {
        try {
            BLADE_ID = InetAddresses.coerceToInteger(InetAddress.getLocalHost());
        } catch (Exception e) {
            LOG.error("IdManager - Exception - {}", e.getMessage());
        }
    }

    Map<InstanceIdentifier<?>,DataObject> configDataStore = new HashMap<>();
    @Mock DataBroker dataBroker;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock LockManagerService lockManager;
    Future<RpcResult<Void>> rpcResult;
    IdManager idManager;
    IdPool globalIdPool;
    InstanceIdentifier<IdPool> identifier;
    InstanceIdentifier<IdPool> childIdentifier;
    InstanceIdentifier<ChildPools> childPoolIdentifier;
    String globalPoolName = "test-pool";
    String localPoolName = new StringBuilder(globalPoolName).append(".").append(BLADE_ID).toString();
    String idKey = "test-key1";
    int idStart = 100;
    int idEnd = 200;
    int blockSize = 2;
    String idKey2 = "test-key2";
    int idValue = 25;

    @Before
    public void setUp() throws Exception {

        idManager = new IdManager(dataBroker);
        idManager.setLockManager(lockManager);
        setupMocks();
    }

    private void setupMocks() {
        globalIdPool = buildGlobalIdPool(globalPoolName, idStart, idEnd, blockSize, buildChildPool(localPoolName)).build();
        identifier = buildInstanceIdentifier(globalPoolName);
        childIdentifier = buildInstanceIdentifier(localPoolName);
        childPoolIdentifier = buildChildPoolInstanceIdentifier(globalPoolName, localPoolName);
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
        when(lockManager.lock(any(LockInput.class))).thenReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build()));
        when(lockManager.unlock(any(UnlockInput.class))).thenReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build()));
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();
        doReturn(Futures.immediateCheckedFuture(null)).when(mockReadTx).read(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
    }

    @Test
    public void testCreateIdPool() throws Exception
    {
        CreateIdPoolInput createPoolTest = buildCreateIdPool(globalPoolName, idStart, idEnd);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, identifier);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, childIdentifier);
        Future<RpcResult<Void>> result = idManager.createIdPool(createPoolTest);
        DataObject dataObject;
        IdPool pool;
        assertTrue(result.get().isSuccessful());
        assertEquals(0,configDataStore.size());
        dataObject = configDataStore.get(childIdentifier);
        if (dataObject instanceof IdPool) {
            pool = (IdPool) dataObject;
            assertEquals(localPoolName, pool.getPoolName());
            assertEquals(createPoolTest.getPoolName(), pool.getParentPoolName());
            assertEquals(null, pool.getAvailableIdsHolder());
            assertEquals(30, pool.getReleasedIdsHolder().getDelayedTimeSec().longValue());
            assertEquals(0, pool.getReleasedIdsHolder().getAvailableIdCount().longValue());
            assertEquals(blockSize, pool.getBlockSize().longValue());
        }
        dataObject = configDataStore.get(identifier);
        if (dataObject instanceof IdPool) {
            pool = (IdPool) dataObject;
            assertEquals(createPoolTest.getPoolName(), pool.getPoolName());
            assertEquals(0, pool.getReleasedIdsHolder().getDelayedTimeSec().longValue());
            assertEquals(0, pool.getReleasedIdsHolder().getAvailableIdCount().longValue());
            assertEquals(createPoolTest.getLow(), pool.getAvailableIdsHolder().getStart());
            assertEquals(createPoolTest.getHigh(), pool.getAvailableIdsHolder().getEnd());
            assertEquals(createPoolTest.getLow() - 1, pool.getAvailableIdsHolder().getCursor().intValue());
            assertEquals(blockSize, pool.getBlockSize().longValue());
        }
        dataObject = configDataStore.get(childPoolIdentifier);
        if (dataObject instanceof ChildPools) {
            ChildPools childPool = (ChildPools) dataObject;
            assertEquals(localPoolName, childPool.getChildPoolName());
        }
    }

    @Test
    public void testAllocateId() throws Exception
    {
        AllocateIdInput allocateIdInput = buildAllocateId(globalPoolName, idKey);
        Optional<IdPool> expected = Optional.of(globalIdPool);
        List<IdEntries> idEntries = new ArrayList<IdEntries>();
        idEntries.add(buildIdEntry(idKey2, idValue));
        Optional<IdPool> expectedLocalPool = Optional.of(buildLocalIdPool(blockSize, localPoolName, globalPoolName).setIdEntries(idEntries).build());
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, identifier);
        doReturn(Futures.immediateCheckedFuture(expectedLocalPool)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, childIdentifier);
        InstanceIdentifier<IdEntries> idEntriesIdentifier = buildIdEntriesIdentifier(idKey);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, idEntriesIdentifier);
        InstanceIdentifier<AvailableIdsHolder> availableIdsIdentifier = buildAvailbaleIdsIdentifier(globalPoolName);

        Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(allocateIdInput);
        assertTrue(result.get().isSuccessful());
        assertEquals(0,configDataStore.size());
        DataObject dataObject = configDataStore.get(childIdentifier);
        if (dataObject instanceof IdPool) {
            IdPool pool = (IdPool) dataObject;
            assertEquals(localPoolName, pool.getPoolName());
            assertEquals(idStart, pool.getAvailableIdsHolder().getStart().intValue());
            assertEquals(idStart + blockSize - 1 , pool.getAvailableIdsHolder().getEnd().intValue());
            assertEquals(idStart, pool.getAvailableIdsHolder().getCursor().intValue());
            assertEquals(2, pool.getIdEntries().size());
        }
        dataObject = configDataStore.get(availableIdsIdentifier);
        if (dataObject instanceof AvailableIdsHolder) {
            AvailableIdsHolder availableIds = (AvailableIdsHolder) dataObject;
            assertEquals(idEnd, availableIds.getEnd().intValue());
            assertEquals(idStart, availableIds.getStart().intValue());
            assertEquals(idStart + blockSize - 1, availableIds.getCursor().intValue());
        }
    }

    @Test
    public void testReleaseId() throws Exception {
        ReleaseIdInput releaseIdInput = createReleaseIdInput(globalPoolName, idKey);
        List<IdEntries> idEntries = new ArrayList<IdEntries>();
        idEntries.add(buildIdEntry(idKey, idValue));
        Optional<IdPool> expectedLocalPool = Optional.of(buildLocalIdPool(blockSize, localPoolName, globalPoolName).setIdEntries(idEntries).build());
        doReturn(Futures.immediateCheckedFuture(expectedLocalPool)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, childIdentifier);
        InstanceIdentifier<IdEntries> idEntriesIdentifier = buildIdEntriesIdentifier(idKey);
        Optional<IdEntries> expectedIdEntry = Optional.of(buildIdEntry(idKey, idValue));
        doReturn(Futures.immediateCheckedFuture(expectedIdEntry)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, idEntriesIdentifier);
        Future<RpcResult<Void>> result = idManager.releaseId(releaseIdInput);
        assertTrue(result.get().isSuccessful());
        assertEquals(0, configDataStore.size());
        DataObject idPoolVal = configDataStore.get(childIdentifier);
        if (idPoolVal instanceof IdPool) {
            IdPool pool = (IdPool) idPoolVal;
            assertEquals(0, pool.getIdEntries().size());
            assertEquals(0, pool.getReleasedIdsHolder().getAvailableIdCount().intValue());
            assertEquals(idValue, pool.getReleasedIdsHolder().getDelayedIdEntries().get(0).getId().intValue());
        }
    }

    @Test
    public void testCleanupReleasedIds() throws Exception {
        AllocateIdInput allocateIdInput = buildAllocateId(globalPoolName, idKey2);
        Optional<ReleasedIdsHolder> expected = Optional.of(createReleasedIdsHolder(0, null, 0));
        long[] excessIds = new long[] { 1, 2, 3, 4, 5 };
        List<IdEntries> idEntries = new ArrayList<IdEntries>();
        idEntries.add(buildIdEntry(idKey2, idValue));
        ReleasedIdsHolder excessReleasedIds = createReleasedIdsHolder(0, buildDelayedIdEntries(excessIds), (long) 30);
        Optional<IdPool> expectedLocalPool = Optional.of(buildLocalIdPool(blockSize, localPoolName, globalPoolName)
                .setIdEntries(idEntries).setReleasedIdsHolder(excessReleasedIds)
                .build());
        InstanceIdentifier<ReleasedIdsHolder> releaseIdsIdentifier = buildReleaseIdsIdentifier(globalPoolName);
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx)
                .read(LogicalDatastoreType.CONFIGURATION, releaseIdsIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedLocalPool)).when(
                mockReadTx).read(LogicalDatastoreType.CONFIGURATION,
                childIdentifier);
        InstanceIdentifier<IdEntries> idEntriesIdentifier = buildIdEntriesIdentifier(idKey2);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(
                mockReadTx).read(LogicalDatastoreType.CONFIGURATION,
                idEntriesIdentifier);

        Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(allocateIdInput);
        assertTrue(result.get().isSuccessful());
        assertEquals(0, configDataStore.size());
        DataObject dataObject = configDataStore.get(childIdentifier);
        if (dataObject instanceof IdPool) {
            IdPool pool = (IdPool) dataObject;
            assertEquals(localPoolName, pool.getPoolName());
            assertEquals(excessIds.length - 3, pool.getReleasedIdsHolder().getAvailableIdCount().intValue());
            assertEquals(2, pool.getIdEntries().size());
        }
        dataObject = configDataStore.get(releaseIdsIdentifier);
        if (dataObject instanceof ReleasedIdsHolder) {
            ReleasedIdsHolder releasedIds = (ReleasedIdsHolder) dataObject;
            assertEquals(2, releasedIds.getAvailableIdCount().intValue());
            assertEquals(2, releasedIds.getDelayedIdEntries().size());
        }
    }

    @Test
    public void testAllocateIdBlockFromReleasedIds() throws Exception {
        AllocateIdInput allocateIdInput = buildAllocateId(globalPoolName, globalPoolName);
        List<DelayedIdEntries> delayedIdEntries = buildDelayedIdEntries(new long[] {1, 2, 3});
        ReleasedIdsHolder expectedReleasedIds = createReleasedIdsHolder(3, delayedIdEntries , 0);
        IdPool globalIdPool = buildGlobalIdPool(globalPoolName, idStart, idEnd, blockSize, buildChildPool(localPoolName)).setReleasedIdsHolder(expectedReleasedIds).build();
        Optional<IdPool> expected = Optional.of(globalIdPool);
        Optional<IdPool> expectedLocalPool = Optional.of(buildLocalIdPool(blockSize, localPoolName, globalPoolName).build());
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, identifier);
        doReturn(Futures.immediateCheckedFuture(expectedLocalPool)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, childIdentifier);
        InstanceIdentifier<IdEntries> idEntriesIdentifier = buildIdEntriesIdentifier(globalPoolName);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, idEntriesIdentifier);
        InstanceIdentifier<ReleasedIdsHolder> releaseIdsIdentifier = buildReleaseIdsIdentifier(globalPoolName);

        Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(allocateIdInput);
        assertTrue(result.get().isSuccessful());
        assertEquals(0,configDataStore.size());
        DataObject dataObject = configDataStore.get(childIdentifier);
        if (dataObject instanceof IdPool) {
            IdPool pool = (IdPool) dataObject;
            assertEquals(localPoolName, pool.getPoolName());
            assertEquals(1, pool.getReleasedIdsHolder().getDelayedIdEntries().size());
            assertEquals(1, pool.getIdEntries().size());
            assertEquals(1, pool.getReleasedIdsHolder().getAvailableIdCount().intValue());
        }
        dataObject = configDataStore.get(releaseIdsIdentifier);
        if (dataObject instanceof ReleasedIdsHolder) {
            ReleasedIdsHolder releasedIds = (ReleasedIdsHolder) dataObject;
            assertEquals(1, releasedIds.getAvailableIdCount().intValue());
            assertEquals(1, releasedIds.getDelayedIdEntries().size());
        }
    }

    @Test
    public void testDeletePool() throws Exception {
        IdPool globalIdPool = buildGlobalIdPool(globalPoolName, idStart, idEnd, blockSize, buildChildPool(localPoolName)).build();
        Optional<IdPool> expected = Optional.of(globalIdPool);
        Optional<IdPool> expectedLocalPool = Optional.of(buildLocalIdPool(blockSize, localPoolName, globalPoolName).build());
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, identifier);
        doReturn(Futures.immediateCheckedFuture(expectedLocalPool)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, childIdentifier);
        DeleteIdPoolInput deleteIdPoolInput = createDeleteIdPoolInput(globalPoolName);
        configDataStore.put(childIdentifier, null);
        configDataStore.put(identifier, null);
        Future<RpcResult<Void>> result = idManager.deleteIdPool(deleteIdPoolInput);
        assertTrue(result.get().isSuccessful());
        assertEquals(2, configDataStore.size());
    }

    private InstanceIdentifier<ReleasedIdsHolder> buildReleaseIdsIdentifier(
            String poolName) {
        InstanceIdentifier<ReleasedIdsHolder> releasedIds = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName)).child(ReleasedIdsHolder.class).build();
        return releasedIds;
    }

    private InstanceIdentifier<AvailableIdsHolder> buildAvailbaleIdsIdentifier(
            String poolName) {
        InstanceIdentifier<AvailableIdsHolder> availableIds = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName)).child(AvailableIdsHolder.class).build();
        return availableIds;
    }

    private InstanceIdentifier<ChildPools> buildChildPoolInstanceIdentifier(String poolName, String childPoolName) {
        InstanceIdentifier<ChildPools> childPool = InstanceIdentifier
                .builder(IdPools.class).child(IdPool.class,
                        new IdPoolKey(poolName)).child(ChildPools.class, new ChildPoolsKey(childPoolName)).build();
        return childPool;
    }

    private ReleaseIdInput createReleaseIdInput(String poolName, String idKey) {
        return new ReleaseIdInputBuilder().setIdKey(idKey).setPoolName(poolName).build();
    }

    private IdEntries buildIdEntry(String idKey, long idValue) {
        return new IdEntriesBuilder().setIdKey(idKey).setIdValue(idValue).build();
    }

    private InstanceIdentifier<IdEntries> buildIdEntriesIdentifier(String idKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdEntries> idEntriesBuilder = childIdentifier
                .builder().child(IdEntries.class, new IdEntriesKey(idKey));
        InstanceIdentifier<IdEntries> idEntry = idEntriesBuilder.build();
        return idEntry;
    }

    private CreateIdPoolInput buildCreateIdPool(String poolName, long low, long high) {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(poolName)
                .setLow(low)
                .setHigh(high)
                .build();
        return createPool;
    }

    private IdPoolBuilder buildGlobalIdPool(String poolName, long idStart, long poolSize, int blockSize, List<ChildPools> childPools) {
        AvailableIdsHolder availableIdsHolder = createAvailableIdsHolder(idStart, poolSize, idStart - 1);
        ReleasedIdsHolder releasedIdsHolder = createReleasedIdsHolder(0, null, 0);
        return new IdPoolBuilder().setKey(new IdPoolKey(poolName))
                .setPoolName(poolName)
                .setBlockSize(blockSize)
                .setChildPools(childPools)
                .setAvailableIdsHolder(availableIdsHolder)
                .setReleasedIdsHolder(releasedIdsHolder);
    }

    private IdPoolBuilder buildLocalIdPool(int blockSize, String localPoolName, String parentPoolName) {
        ReleasedIdsHolder releasedIdsHolder = createReleasedIdsHolder(0, null, (long) 30);
        return new IdPoolBuilder().setBlockSize(blockSize)
                .setKey(new IdPoolKey(localPoolName))
                .setParentPoolName(parentPoolName)
                .setReleasedIdsHolder(releasedIdsHolder);
    }

    private AllocateIdInput buildAllocateId(String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName)
                .setIdKey(idKey).build();
        return getIdInput;
    }

    private InstanceIdentifier<IdPool> buildInstanceIdentifier(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                InstanceIdentifier.builder(IdPools.class).child(IdPool.class, new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idBuilder.build();
        return id;
    }

    private AvailableIdsHolder createAvailableIdsHolder(long low, long high, long cursor) {
        AvailableIdsHolder availableIdsHolder = new AvailableIdsHolderBuilder()
                .setStart(low).setEnd(high).setCursor(cursor).build();
        return availableIdsHolder;
    }

    private ReleasedIdsHolder createReleasedIdsHolder(long availableIdCount, List<DelayedIdEntries> delayedIdEntries, long delayTime) {
        ReleasedIdsHolder releasedIdsHolder = new ReleasedIdsHolderBuilder()
                .setAvailableIdCount(availableIdCount)
                .setDelayedIdEntries(delayedIdEntries)
                .setDelayedTimeSec(delayTime).build();
        return releasedIdsHolder;
    }

    private DeleteIdPoolInput createDeleteIdPoolInput(String poolName) {
        return new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
    }

    private List<DelayedIdEntries> buildDelayedIdEntries(long[] idValues) {
        List<DelayedIdEntries> delayedIdEntriesList = new ArrayList<DelayedIdEntries>();
        for (long idValue : idValues) {
            DelayedIdEntries delayedIdEntries = new DelayedIdEntriesBuilder().setId(idValue).setReadyTimeSec(0L).build();
            delayedIdEntriesList.add(delayedIdEntries);
        }
        return delayedIdEntriesList;
    }

    private List<ChildPools> buildChildPool(String childPoolName) {
        ChildPools childPools = new ChildPoolsBuilder().setChildPoolName(childPoolName).build();
        List<ChildPools> childPoolsList = new ArrayList<ChildPools>();
        childPoolsList.add(childPools);
        return childPoolsList;
    }
}