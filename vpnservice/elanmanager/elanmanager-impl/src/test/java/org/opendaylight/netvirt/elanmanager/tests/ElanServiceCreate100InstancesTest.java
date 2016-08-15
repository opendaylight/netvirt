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
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
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
    public @Rule MethodRule guice = new GuiceRule(ElanServiceTestModule.class);

    private @Inject IElanService elanService;

    @Test public void createElanInstance() throws Exception {
        for (int i = 0; i < 1000; i++) {
            LOG.info("createElanInstance({})", i);
            elanService.createElanInstance("TestElanName" + i, 12345, "TestElan description");
        }
    }

}
