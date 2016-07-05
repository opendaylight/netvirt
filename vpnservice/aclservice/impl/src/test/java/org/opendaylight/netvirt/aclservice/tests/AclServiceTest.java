/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static ch.vorburger.xtendbeans.AssertBeans.assertEqualBeans;
import static org.opendaylight.netvirt.aclservice.api.tests.InterfaceBuilderHelper.putNewInterface;
import static org.opendaylight.netvirt.aclservice.api.tests.StateInterfaceBuilderHelper.putNewStateInterface;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.tests.FlowEntryObjects;

public class AclServiceTest {

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
        assertEqualBeans(FlowEntryObjects.expectedFlows("0D:AA:D8:42:30:F3"), mdsalApiManager.getFlows());
    }


    // TODO Factor this out into a.. Rule (or Runner, or superclass, but Rule is best)

    private Injector injector;

    @Before
    public void setUp() {
        injector = Guice.createInjector(
                new CloseableModule(), new Jsr250Module(),
                new AclServiceModule(), new AclServiceTestModule());
        injector.injectMembers(this);
        injector.getInstance(AclServiceManager.class);
    }

    @After
    public void tearDown() throws Exception {
        // http://code.mycila.com/guice/#3-jsr-250
        injector.getInstance(CloseableInjector.class).close();
    }
}
