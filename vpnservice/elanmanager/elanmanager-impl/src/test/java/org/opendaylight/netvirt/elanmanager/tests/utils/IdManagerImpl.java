/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import com.google.common.collect.Lists;
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


public class IdManagerImpl implements IdManagerService {

    Map<String, AtomicLong> ids = new ConcurrentHashMap<>();
    Map<String, Map<String, Long>> allocatedIds = new ConcurrentHashMap<>();

    public IdManagerImpl() {

    }

    @Override
    public Future<RpcResult<Void>> createIdPool(CreateIdPoolInput input) {
        ids.putIfAbsent(input.getPoolName(), new AtomicLong(input.getLow()));
        allocatedIds.putIfAbsent(input.getPoolName(), new ConcurrentHashMap<>());
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<AllocateIdRangeOutput>> allocateIdRange(AllocateIdRangeInput input) {
        List<Long> idValues = Lists.newArrayList(1L, 2L);
        AllocateIdRangeOutput output = new AllocateIdRangeOutputBuilder().setIdValues(idValues).build();
        String txt = "elan.ids.pool";
        return RpcResultBuilder.success(output).buildFuture();

    }

    @Override
    public synchronized Future<RpcResult<AllocateIdOutput>> allocateId(AllocateIdInput input) {
        Long id = null;
        if (allocatedIds.get(input.getPoolName()) == null) {
            throw new NullPointerException("NPE " + input.getPoolName());
        }
        if (allocatedIds.get(input.getPoolName()).containsKey(input.getIdKey())) {
            id = allocatedIds.get(input.getPoolName()).get(input.getIdKey());
        } else {
            id = ids.get(input.getPoolName()).getAndIncrement();
            allocatedIds.get(input.getPoolName()).put(input.getIdKey(), id);
        }
        AllocateIdOutput output = new AllocateIdOutputBuilder().setIdValue(id).build();

        return RpcResultBuilder.success(output).buildFuture();
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
