/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.interfacemanager.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.lockmanager.LockManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;

/**
 * Equivalent of src/main/resources/org/opendaylight/blueprint/elanmanager.xml,
 * in 2016 syntax (i.e. Java; strongly typed!) instead of late 1990s XML syntax.
 *
 * @author Michael Vorburger
 */
@SuppressWarnings("deprecation")
public class ElanServiceTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() {
        // Bindings for services from this project
        bind(IElanService.class).to(ElanServiceProvider.class);

        // Bindings for external services to "real" implementations
        bind(IdManagerService.class).to(IdManager.class);
        bind(LockManagerService.class).to(LockManager.class);
        bind(OdlInterfaceRpcService.class).to(InterfaceManagerRpcService.class);

        // Bindings to test infra (fakes & mocks)
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(IMdsalApiManager.class).toInstance(TestIMdsalApiManager.newInstance());
        bind(IInterfaceManager.class).toInstance(TestInterfaceManager.newInstance());
        bind(ItmRpcService.class).toInstance(Mockito.mock(ItmRpcService.class)); // new ItmManagerRpcService();
        bind(ElanStatusMonitor.class).toInstance(Mockito.mock(ElanStatusMonitor.class));
        bind(EntityOwnershipService.class).toInstance(Mockito.mock(EntityOwnershipService.class));
    }

}
