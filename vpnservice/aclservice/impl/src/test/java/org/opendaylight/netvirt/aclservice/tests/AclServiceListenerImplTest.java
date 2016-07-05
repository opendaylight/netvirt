/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.ExceptionAnswer;

import java.math.BigInteger;
import java.util.concurrent.Future;
import org.junit.Before;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceListenerTest;
import org.opendaylight.netvirt.aclservice.api.tests.FakeIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.TestDataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class AclServiceListenerImplTest extends AbstractAclServiceListenerTest {

    // Mocked other services which the main service under test depends on
    OdlInterfaceRpcService odlInterfaceRpcService;

    @Before public void setUp() {
        dataBroker = TestDataBroker.newTestDataBroker();

        // Using "classical" Mockito here (could also implement this using Mikito; useful if more complex)
        odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, ExceptionAnswer);
        Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder.success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
        doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());

        mdsalApiManager = Mikito.stub(FakeIMdsalApiManager.class);

        aclService = new EgressAclServiceImpl(dataBroker, odlInterfaceRpcService, mdsalApiManager);
    }

}
