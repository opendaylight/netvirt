/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

/**
 * Test Dependency Injection (DI) Wiring (currently through Guice).
 *
 * @author Michael Vorburger
 */
public class AclServiceTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new AclServiceModule());

        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(AclserviceConfig.class).toInstance(aclServiceConfig());

        bind(AclClusterUtil.class).toInstance(() -> true);

        TestIMdsalApiManager singleton = TestIMdsalApiManager.newInstance();
        bind(IMdsalApiManager.class).toInstance(singleton);
        bind(TestIMdsalApiManager.class).toInstance(singleton);
    }

    private AclserviceConfig aclServiceConfig() {
        AclserviceConfig aclServiceConfig = mock(AclserviceConfig.class);
        Mockito.when(aclServiceConfig.getSecurityGroupMode()).thenReturn(SecurityGroupMode.Stateful);
        return aclServiceConfig;
    }
}
