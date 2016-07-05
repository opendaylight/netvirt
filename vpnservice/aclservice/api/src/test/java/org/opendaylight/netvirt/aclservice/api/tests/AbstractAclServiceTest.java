/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests;

import static ch.vorburger.xtendbeans.AssertBeans.assertEqualBeans;
import static org.opendaylight.netvirt.aclservice.api.tests.InterfaceBuilderHelper.putNewInterface;
import static org.opendaylight.netvirt.aclservice.api.tests.StateInterfaceBuilderHelper.putNewStateInterface;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;

public abstract class AbstractAclServiceTest {

    protected DataBroker dataBroker;
    protected TestIMdsalApiManager mdsalApiManager;

    @Test
    public void newInterface() throws Exception {
        // Given
        putNewInterface(dataBroker, "port1", true);

        // When
        putNewStateInterface(dataBroker, "port1", "0D:AA:D8:42:30:F3");

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertEqualBeans(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"), mdsalApiManager.getFlows());
    }


}
