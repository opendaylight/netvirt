/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.netvirt.aclservice.api.tests.DataBrokerExtensions.put;
import static org.opendaylight.netvirt.aclservice.api.tests.InterfaceBuilderHelper.newInterfacePair;
import static org.opendaylight.netvirt.aclservice.api.tests.StateInterfaceBuilderHelper.newStateInterfacePair;
import static somewhere.testutils.xtend.AssertBeans.assertEqualBeans;

import javax.inject.Inject;
import org.junit.After;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;

public abstract class AbstractAclServiceTest {

    @Inject DataBroker dataBroker;
    @Inject TestIMdsalApiManager mdsalApiManager;
    @Inject AutoCloseable serviceProvider;

    @After
    public void tearDown() throws Exception {
        if (serviceProvider != null) {
            serviceProvider.close();
        }
    }

    @Test
    public void newInterface() throws Exception {
        // Given
        put(dataBroker, CONFIGURATION, newInterfacePair("port1", true));

        // When
        put(dataBroker, OPERATIONAL, newStateInterfacePair("port1", "0D:AA:D8:42:30:F3"));

        // Then
        // TODO must do better synchronization here.. this is multi-thread, must
        // wait for completion - how-to? Use https://github.com/awaitility/awaitility, but wait on what?
        assertEqualBeans(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"), mdsalApiManager.getFlows());
    }


}
