/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.ELAN1;

import com.google.common.util.concurrent.Futures;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;


public abstract class IdHelper implements
        org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService {
    static Map<String, Long> ids = new HashMap<>();

    static {
        ids.put(ELAN1, 5001L);
    }

    @Override
    public Future<RpcResult<AllocateIdOutput>> allocateId(AllocateIdInput allocateIdInput) {
        Long id = ids.get(allocateIdInput.getIdKey());
        return Futures.immediateFuture(RpcResultBuilder.success(
                new AllocateIdOutputBuilder().setIdValue(id).build()).build());
    }
}
