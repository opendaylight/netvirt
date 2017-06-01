/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.tests;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.FibManagerImpl;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;

/**
 * Wiring for {@link FibManagerComponentTest}.
 *
 * @author Michael Vorburger.ch
 */
public class FibManagerTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() throws Exception {
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(IMdsalApiManager.class).toInstance(TestIMdsalApiManager.newInstance());
        bind(IFibManager.class).to(FibManagerImpl.class);
        bind(IElanService.class).to(ElanServiceProvider.class);
        bind(IdManagerService.class).to(IdManager.class);
        // TODO or org.opendaylight.genius.arputil.test.TestOdlInterfaceRpcService?
        bind(OdlInterfaceRpcService.class).to(InterfaceManagerRpcService.class);
    }

}
