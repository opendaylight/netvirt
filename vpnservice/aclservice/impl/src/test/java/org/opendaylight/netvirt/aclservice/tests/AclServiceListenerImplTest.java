/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import org.junit.Before;
import org.opendaylight.netvirt.aclservice.EgressAclServiceImpl;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceListenerTest;
import org.opendaylight.netvirt.aclservice.api.tests.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.TestDataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;

public class AclServiceListenerImplTest extends AbstractAclServiceListenerTest {

    // Mocked other services which the main service under test depends on
    OdlInterfaceRpcService odlInterfaceRpcService;

    @Before public void setUp() {
        dataBroker = TestDataBroker.newTestDataBroker();
        odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, EXCEPTION_ANSWER);
        mdsalApiManager = Mikito.stub(TestIMdsalApiManager.class);

        aclService = new EgressAclServiceImpl(dataBroker, odlInterfaceRpcService, mdsalApiManager);
    }

}
