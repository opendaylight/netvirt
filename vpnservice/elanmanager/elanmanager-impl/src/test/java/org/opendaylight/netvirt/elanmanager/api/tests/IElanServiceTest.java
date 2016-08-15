/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class IElanServiceTest {

    DataBroker dataBroker;
    IElanService elanService;
    ElanServiceTestModule testModule;
    IMdsalApiManager mdsalApiManager;

    @Test
    public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to test the ElanServiceTestModule
    }

    @Test
    @Ignore // TODO
    public void createElanInstance() {
        elanService.createElanInstance("TestELanName", 12345, "TestELan description");
        // TODO assert ...
    }

    @Before
    public void setUp() {
        testModule = new ElanServiceTestModule();
        dataBroker = testModule.dataBroker();
        mdsalApiManager = testModule.mdsalApiManager();
        testModule.start();
    }

    @After
    public void tearDown() throws Exception {
        if (testModule != null) {
            testModule.close();
        }
    }
}
