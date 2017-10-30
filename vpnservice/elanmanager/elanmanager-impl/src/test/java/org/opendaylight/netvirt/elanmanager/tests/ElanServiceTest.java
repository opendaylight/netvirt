/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.junit.Assert.assertNotNull;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.testutils.TestInterfaceManager;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogCaptureRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class ElanServiceTest {

    private static final String TEST_ELAN_NAME = "TestElanName";
    private static final String TEST_INTERFACE_NAME = "TestElanInterfaceName";

    // TODO as-is, this test is flaky; as uncommenting will show
    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues (such as https://bugs.opendaylight.org/show_bug.cgi?id=7538)
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    public @Rule LogRule logRule = new LogRule();
    public @Rule LogCaptureRule logCaptureRule = new LogCaptureRule();

    public @Rule MethodRule guice = new GuiceRule(JobCoordinatorTestModule.class,
            ElanServiceTestModule.class, TestableDataTreeChangeListenerModule.class);

    private @Inject DataBroker dataBroker;
    private @Inject IElanService elanService;
    private @Inject AsyncEventsWaiter asyncEventsWaiter;
    private @Inject TestInterfaceManager testInterfaceManager;
    private SingleTransactionDataBroker singleTxdataBroker;

    @Before public void before() {
        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }

    @Test public void createElanInstance() throws Exception {
        // Given
        // When
        elanService.createElanInstance(TEST_ELAN_NAME, 12345, "TestElan description");
        asyncEventsWaiter.awaitEventsConsumption();
        // Then
        ElanInstances actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION,
                InstanceIdentifier.builder(ElanInstances.class).build());
        AssertDataObjects.assertEqualBeans(ExpectedObjects.createElanInstance(), actualElanInstances);
        assertNotNull(elanService.getElanInstance(TEST_ELAN_NAME));
    }

    @Test public void addElanInterface() throws Exception {
        // Given
        elanService.createElanInstance(TEST_ELAN_NAME, 12345, "...");
        asyncEventsWaiter.awaitEventsConsumption();
        testInterfaceManager.addInterfaceInfo(newInterfaceInfo(TEST_INTERFACE_NAME));
        // When
        List<String> macAddresses = Collections.singletonList("11:22:33:44:55:66");
        elanService.addElanInterface(TEST_ELAN_NAME, TEST_INTERFACE_NAME, macAddresses, "...");
        asyncEventsWaiter.awaitEventsConsumption();
        // Then
        ElanInterfaces actualElanInterfaces = singleTxdataBroker.syncRead(CONFIGURATION,
                InstanceIdentifier.builder(ElanInterfaces.class).build());
        AssertDataObjects.assertEqualBeans(ExpectedObjects.addElanInterface(), actualElanInterfaces);
    }

    private InterfaceInfo newInterfaceInfo(String testInterfaceName) {
        InterfaceInfo interfaceInfo = new InterfaceInfo(BigInteger.valueOf(789), "TestPortName");
        interfaceInfo.setInterfaceName(TEST_INTERFACE_NAME);
        return interfaceInfo;
    }

}
