/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 */
public class ElanServiceCreate100InstancesTest {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceCreate100InstancesTest.class);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(ElanServiceCreate100InstancesTestModule.class);

    // private @Inject IElanService elanService;
    private @Inject DataBroker broker;
    private @Inject ElanServiceCreate100InstancesListener listener;

    @Test public void createElanInstance() throws Exception {
        for (int i = 0; i < 1000; i++) {
            LOG.info("createElanInstance({})", i);
            String elanInstanceName = "TestElanName" + i;
            // elanService.createElanInstance(elanInstanceName, 12345, "TestElan description");
            ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                    .setMacTimeout(12345L).setDescription("TestElan description")
                    .setKey(new ElanInstanceKey(elanInstanceName))
                    .build();
            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName), elanInstance);
            LOG.info("Creating the new Elan Instance {}", elanInstance);
        }
    }

}
