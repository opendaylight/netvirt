package org.opendaylight.idmanager.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.id.pool.GeneratedIds;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Future;


@RunWith(MockitoJUnitRunner.class)
public class IdManagerTest {
    private static final Logger LOG = LoggerFactory.getLogger(IdManagerTest.class);
    Map<InstanceIdentifier<?>,DataObject> written = new HashMap<>();
    Map<InstanceIdentifier<?>,DataObject> updated = new HashMap<>();
    @Mock DataBroker dataBroker;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    CreateIdPoolInput createPoolTest;
    GetUniqueIdInput  getIdInputTest;
    IdManager idManager;
    IdPool idPoolTest;
    InstanceIdentifier<IdPool> identifier;

    @Before
    public void setUp() throws Exception {

        idManager = new IdManager(dataBroker) {
            protected  <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                             InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
                written.put(path, data);
            }
            protected  <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                                                              InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
                updated.put(path, data);
            }

        };
        setupMocks();
    }

    private void setupMocks() {
        createPoolTest = buildCreateIdPool("vpn", 100, 100);
        getIdInputTest = buildUniqueId("vpn", "vpn1");
        idPoolTest = buildIdPool("vpn", 100, 100);
        identifier = buildInstanceIdentifier("vpn");
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testCreateIdPool()
    {
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, identifier);

        Future<RpcResult<Void>> result = idManager.createIdPool(createPoolTest);
        Collection<DataObject> idPoolVal  = new ArrayList< >();
        idPoolVal = written.values();
        assertEquals(1,written.size());
            for (DataObject poolData: idPoolVal) {
                IdPool pool = null;
                if (poolData instanceof IdPool) {
                    pool = (IdPool) poolData;
                    assertEquals(createPoolTest.getPoolName(), pool.getPoolName());
                    assertEquals(createPoolTest.getIdStart(), pool.getIdStart());
                    assertEquals(createPoolTest.getPoolSize(), pool.getPoolSize());
                }
            }
    }

    @Test
    public void testUniqueId()
    {
        Optional<IdPool> expected = Optional.of(idPoolTest);
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, identifier);

        idManager.getUniqueId(getIdInputTest);
        Collection<DataObject> idPoolVal  = new ArrayList< >();
        idPoolVal = updated.values();
        assertEquals(1,updated.size());
            for (DataObject poolData: idPoolVal) {
                IdPool pool = null;
                if (poolData instanceof IdPool) {
                    pool = (IdPool) poolData;
                    assertEquals(getIdInputTest.getPoolName(), pool.getPoolName());
                    List <GeneratedIds> genIds = pool.getGeneratedIds();
                    assertEquals(1,genIds.size());
                }
            }
    }

    private CreateIdPoolInput buildCreateIdPool(String poolName, long idStart, long poolSize) {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(poolName)
                .setIdStart(idStart)
                .setPoolSize(BigInteger.valueOf(poolSize))
                .build();
        return createPool;
    }

    private IdPool buildIdPool(String poolName, long idStart, long poolSize) {
        IdPool idPool = new IdPoolBuilder().setPoolName(poolName)
                .setIdStart(idStart)
                .setPoolSize(BigInteger.valueOf(poolSize))
                .build();
        return idPool;
    }

    private GetUniqueIdInput buildUniqueId(String poolName, String idKey) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder().setPoolName(poolName)
                .setIdKey(idKey).build();
        return getIdInput;
    }

    private InstanceIdentifier<IdPool> buildInstanceIdentifier(String poolName){
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                InstanceIdentifier.builder(Pools.class).child(IdPool.class, new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idBuilder.build();
        return id;
    }
}

