/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.idmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.id.pool.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;


public class IdManager implements IdManagerService, AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(IdManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;



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

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                    InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                                                    InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    @Override
    public Future<RpcResult<Void>> createIdPool(CreateIdPoolInput input)
    {

        String poolName = input.getPoolName();
        long startIndx = input.getIdStart();
        long poolSize = input.getPoolSize().longValue();
        RpcResultBuilder<Void> rpcResultBuilder;

        LOG.debug("poolName: %s, startIndx: %d , poolSize: %d ", poolName, startIndx,  poolSize);

        try {
            InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                    InstanceIdentifier.builder(Pools.class).child(IdPool.class, new IdPoolKey(poolName));
            InstanceIdentifier<IdPool> id = idBuilder.build();
            Optional<IdPool> pool = read(LogicalDatastoreType.OPERATIONAL, id);
            if (!pool.isPresent()) {
                LOG.debug("Creating a new global pool: {} ", poolName);
                IdPool newPool = getPoolInterface(poolName, startIndx, poolSize);
                LOG.debug("NewPool: {}", newPool);
                asyncWrite(LogicalDatastoreType.OPERATIONAL, id, newPool, DEFAULT_CALLBACK);

            }

            rpcResultBuilder = RpcResultBuilder.success();
        }
        catch(Exception e){
            LOG.error("Creation of global pool {} failed due to {}" ,poolName, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }

        return Futures.immediateFuture(rpcResultBuilder.build());
    }


    @Override
    public Future<RpcResult<GetUniqueIdOutput>> getUniqueId(GetUniqueIdInput input){

        String poolName = input.getPoolName();
        String idKey = input.getIdKey();

        LOG.debug("poolName: {} ,idKey: {}", poolName, idKey);
        RpcResultBuilder<GetUniqueIdOutput> rpcResultBuilder;

        try {
            InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                    InstanceIdentifier.builder(Pools.class).child(IdPool.class, new IdPoolKey(poolName));
            InstanceIdentifier<IdPool> id = idBuilder.build();
            Optional<IdPool> globalPool = read(LogicalDatastoreType.OPERATIONAL, id);
            GetUniqueIdOutputBuilder output = new GetUniqueIdOutputBuilder();
            Long newIdValue = null;
            GeneratedIds newGenId = null;
            if (globalPool.isPresent()) {
                IdPool pool = globalPool.get();
                List<GeneratedIds> generatedIds = pool.getGeneratedIds();

                if (generatedIds == null) {
                    generatedIds = new ArrayList<GeneratedIds>();
                }
                if (!generatedIds.isEmpty()) {
                    for (GeneratedIds gen_id : generatedIds) {
                        if (gen_id.getIdKey().equals(idKey)) {
                            newIdValue = gen_id.getIdValue();
                            LOG.debug("Existing id {} for the key {} ", idKey, newIdValue);
                        }

                    }
                }
                synchronized (this) {
                    if (newIdValue == null) {
                        newIdValue = (long) generatedIds.size() + 1;
                        LOG.debug("Creating a new id {} for the pool: {} ", newIdValue, poolName);
                        newGenId = getIdsInterface(idKey, newIdValue);
                        generatedIds.add(newGenId);
                        pool = new IdPoolBuilder(pool).setGeneratedIds(generatedIds).build();
                        asyncUpdate(LogicalDatastoreType.OPERATIONAL, id, pool, DEFAULT_CALLBACK);
                    }
                }
                output.setIdValue(newIdValue);
            }

            rpcResultBuilder = RpcResultBuilder.success();
            rpcResultBuilder.withResult(output.build());
        }
        catch(Exception e){
            LOG.error("Creation of id for the key {} , global pool {} failed due to {}" ,idKey, poolName, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }


    private IdPool getPoolInterface(String poolName, long startIndx, long poolSize) {
        BigInteger size = BigInteger.valueOf(poolSize);
        return new IdPoolBuilder().setKey(new IdPoolKey(poolName)).setPoolName(poolName).setIdStart(startIndx)
                .setPoolSize(size).build();
    }

    private GeneratedIds getIdsInterface(String idKey, long newIdVal) {
        return new GeneratedIdsBuilder().setKey(new GeneratedIdsKey(idKey)).setIdKey(idKey)
                .setIdValue(newIdVal).build();
    }

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
        new FutureCallback<Void>() {
            public void onSuccess(Void result) {
                LOG.debug("Success in Datastore write operation");
            }

            public void onFailure(Throwable error) {
                LOG.error("Error in Datastore write operation", error);
            };
        };

}
