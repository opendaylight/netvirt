/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator.tests;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.netvirt.sfc.translator.OpenStackSFCTranslatorProvider;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePathService;

/**
 * Test Dependency Injection (DI) Wiring, through Guice.
 *
 * @author Michael Vorburger.ch
 */
public class SfcTranslatorTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() throws Exception {
        // Bindings for services from this project
        bind(OpenStackSFCTranslatorProvider.class);

        // Bindings for external services to "real" implementations
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());

        // Bindings to test infra (fakes & mocks)
        bind(RenderedServicePathService.class).to(TestRenderedServicePathService.class);
    }

}
