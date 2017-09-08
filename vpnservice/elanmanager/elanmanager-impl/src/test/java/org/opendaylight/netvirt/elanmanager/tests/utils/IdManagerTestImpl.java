/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdRangeInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdRangeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdRangeOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;



public class IdManagerTestImpl implements IdManagerService {

    private final Map<String, AtomicLong> ids = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> allocatedIds = new ConcurrentHashMap<>();

    public IdManagerTestImpl() {
    }

    @Override
    public Future<RpcResult<Void>> createIdPool(CreateIdPoolInput input) {
        ids.putIfAbsent(input.getPoolName(), new AtomicLong(input.getLow()));
        allocatedIds.putIfAbsent(input.getPoolName(), new ConcurrentHashMap<>());
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<AllocateIdRangeOutput>> allocateIdRange(AllocateIdRangeInput input) {
        List<Long> idValues = new ArrayList<>();
        for (int i = 0 ; i < input.getSize(); i++) {
            idValues.add(allocateId(input.getPoolName(), input.getIdKey()));
        }

        AllocateIdRangeOutput output = new AllocateIdRangeOutputBuilder().setIdValues(idValues).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public synchronized Future<RpcResult<AllocateIdOutput>> allocateId(AllocateIdInput input) {
        Long id = allocateId(input.getPoolName(), input.getIdKey());
        AllocateIdOutput output = new AllocateIdOutputBuilder().setIdValue(id).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    public Long allocateId(String poolName, String key) {
        Long id = null;
        if (allocatedIds.get(poolName) == null) {
            throw new IllegalStateException("No pool found with name " + poolName);
        }
        if (allocatedIds.get(poolName).containsKey(key)) {
            id = allocatedIds.get(poolName).get(key);
        } else {
            id = ids.get(poolName).getAndIncrement();
            allocatedIds.get(poolName).put(key, id);
        }
        return id;
    }

    @Override
    public synchronized Future<RpcResult<Void>> releaseId(ReleaseIdInput input) {
        allocatedIds.remove(input.getIdKey());
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> deleteIdPool(DeleteIdPoolInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }
}
