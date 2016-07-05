/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.junit.After;
import org.junit.Before;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceTest;

public class AclServiceImplTest2 extends AbstractAclServiceTest {

    private AclServiceTestModule testModule;

    @Before
    public void setUp() {
        testModule = new AclServiceTestModule();
        dataBroker = testModule.dataBroker();
        mdsalApiManager = testModule.mdsalApiManager();
    }

    @After
    public void tearDown() throws Exception {
        if (testModule != null) {
            testModule.close();
        }
    }

}
