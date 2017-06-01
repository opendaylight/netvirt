/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.tests;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.clustering.testutil.TestEntityOwnershipService;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.interfacemanager.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.genius.testutils.TestInterfaceManager;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.lockmanager.LockListener;
import org.opendaylight.lockmanager.LockManager;
import org.opendaylight.netvirt.fibmanager.FibManagerImpl;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.SalGroupService;

/**
 * Wiring for {@link FibManagerComponentTest}.
 *
 * @author Michael Vorburger.ch
 */
public class FibManagerTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() throws Exception {
        // mdsal
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(EntityOwnershipService.class).toInstance(TestEntityOwnershipService.newInstance());

        // genius
        bind(IMdsalApiManager.class).toInstance(TestIMdsalApiManager.newInstance());
        bind(IdManagerService.class).to(IdManager.class);
        // TODO or org.opendaylight.genius.arputil.test.TestOdlInterfaceRpcService?
        bind(OdlInterfaceRpcService.class).to(InterfaceManagerRpcService.class);
        bindTypesToInstance(IInterfaceManager.class, TestInterfaceManager.class, TestInterfaceManager.newInstance());
        // TODO ItmRpcService, real or fake (as below) ?
        bind(LockManagerService.class).to(LockManager.class);
        bind(LockListener.class);

        // netvirt (other than this)
        // TODO decide if we want to use the "real" ElanServiceProvider, then need to do:
        // bind(IElanService.class).to(ElanServiceProvider.class);
        // but that's not enough, we would need to (refactor to use part of) the ElanServiceTestModule
        // or whether to write a fake IElanService impl, in a new netvirt/testutils JAR, similar to genius/testutils

        // openflowplugin
        // TODO not sure Mockito'd RPC service ops will really work too well in a component test... TBC.
        bind(SalGroupService.class).toInstance(Mockito.mock(SalGroupService.class));

        // this project
        bind(IFibManager.class).to(FibManagerImpl.class);
    }

}
