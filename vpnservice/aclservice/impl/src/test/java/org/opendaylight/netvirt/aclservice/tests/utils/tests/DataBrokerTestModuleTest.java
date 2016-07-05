/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.MembersInjector;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.aclservice.tests.utils.DataBrokerTestModule;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.junit.InjectorRule;

public class DataBrokerTestModuleTest {

    @Singleton
    @Component(modules = DataBrokerTestModule.class)
    interface Configuration extends MembersInjector<DataBrokerTestModuleTest> {
        @Override
        void injectMembers(DataBrokerTestModuleTest test);
    }

    @Rule
    public InjectorRule injector = new InjectorRule(DaggerDataBrokerTestModuleTest_Configuration.create());

    @Inject
    DataBroker service;

    @Test
    public void ensureDataBrokerTestModuleWorksWithoutException() {
        assertThat(service).isNotNull();
    }
}
