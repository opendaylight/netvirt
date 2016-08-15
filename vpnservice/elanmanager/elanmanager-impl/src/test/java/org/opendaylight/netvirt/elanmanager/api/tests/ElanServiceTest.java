/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import static ch.vorburger.xtendbeans.AssertBeans.assertEqualBeans;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.DataRoot;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class ElanServiceTest extends AbstractDataBrokerTest {
    // TODO remove "extends AbstractDataBrokerTest" when https://git.opendaylight.org/gerrit/#/c/43645/ is merged

    DataBroker dataBroker;
    IElanService elanService;
    ElanServiceTestModule testModule;
    IMdsalApiManager mdsalApiManager;

    @Test
    public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to test the ElanServiceTestModule
    }

    @Test
    public void createElanInstance() throws Exception {
        elanService.createElanInstance("TestELanName", 12345, "TestELan description");
        assertEqualBeans(null, read(CONFIGURATION, ElanInstances.class));
    }

    // TODO MOVE THIS CODE TO A HELPER; see idea in https://git.opendaylight.org/gerrit/#/c/44099/
    <T extends ChildOf<? extends DataRoot>>
        T read(LogicalDatastoreType store, final Class<T> container) throws ReadFailedException {

        InstanceIdentifier<T> containerInstanceIdentifier = InstanceIdentifier.builder(container).build();
        try ( ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction() ) {
            Optional<T> optional = tx.read(store, containerInstanceIdentifier).checkedGet();
            if (optional.isPresent()) {
                return optional.get();
            } else {
                throw new ReadFailedException("Nothing in the " + store
                        + " datastore at: " + containerInstanceIdentifier);
            }
        }
    }

    @Before
    public void setUp() {
        testModule = new ElanServiceTestModule(getDataBroker());
        dataBroker = testModule.dataBroker();
        mdsalApiManager = testModule.mdsalApiManager();
        elanService = testModule.elanService();
        testModule.start();
    }

    @After
    public void tearDown() throws Exception {
        if (testModule != null) {
            testModule.close();
        }
    }
}
