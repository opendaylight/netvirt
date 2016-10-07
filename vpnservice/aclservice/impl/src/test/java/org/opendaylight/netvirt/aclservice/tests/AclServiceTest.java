/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.assertTrue;
import static org.opendaylight.netvirt.aclservice.tests.InterfaceBuilderHelper.putNewInterface;
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;
import static org.opendaylight.netvirt.aclservice.tests.infra.AssertBuilderBeans.assertEqualBeans;

import java.util.List;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;

public class AclServiceTest {

    public @Rule GuiceRule guice = new GuiceRule(AclServiceModule.class, AclServiceTestModule.class);

    @Inject DataBroker dataBroker;
    @Inject TestIMdsalApiManager mdsalApiManager;

    @Test
    public void newInterface() throws Exception {
        // Given
        putNewInterface(dataBroker, "port1", true);

        // When
        putNewStateInterface(dataBroker, "port1", "0D:AA:D8:42:30:F3");

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"));
    }


    private void assertFlows(List<FlowEntity> expectedFlows) {
        List<FlowEntity> flows = mdsalApiManager.getFlows();
        if (!expectedFlows.isEmpty()) {
            assertTrue("No Flows created (bean wiring may be broken?)", !flows.isEmpty());
        }
        assertEqualBeans(expectedFlows, flows);
    }

}
