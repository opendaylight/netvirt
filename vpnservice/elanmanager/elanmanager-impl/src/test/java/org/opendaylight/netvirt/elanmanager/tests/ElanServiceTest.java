/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
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
public class ElanServiceTest {

    public @Rule MethodRule guice = new GuiceRule(new ElanServiceTestModule());

    @Inject DataBroker dataBroker;
    @Inject IElanService elanService;
    @Inject ElanServiceTestModule testModule;
    @Inject IMdsalApiManager mdsalApiManager;

    @Test
    public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to test the ElanServiceTestModule
    }

    @Test
    public void createElanInstance() throws Exception {
        elanService.createElanInstance("TestELanName", 12345, "TestELan description");
        ElanInstances actualElanInstance = read(CONFIGURATION, ElanInstances.class);
        AssertDataObjects.assertEqualBeans(ExpectedObjects.createElanInstance(), actualElanInstance);
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

}
