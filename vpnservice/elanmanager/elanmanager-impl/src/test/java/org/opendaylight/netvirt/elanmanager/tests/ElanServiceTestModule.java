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
import org.opendaylight.netvirt.elan.internal.ElanBridgeManager;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elan.utils.ElanForwardingEntriesHandler;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
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
public class ElanServiceTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() {
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(IElanService.class).to(ElanServiceProvider.class);
        bind(IdManagerService.class).to(IdManager.class);
        bind(LockManagerService.class).to(LockManager.class);
    }

    private OdlInterfaceRpcService interfaceManagerRpcService() {
        if (interfaceManagerRpcService == null) {
            interfaceManagerRpcService = new InterfaceManagerRpcService(dataBroker(), mdsalApiManager());
        }
        return interfaceManagerRpcService;
    }

    private ItmRpcService itmRpcService() {
        if (itmRpcService == null) {
            itmRpcService = Mockito.mock(ItmRpcService.class); // new ItmManagerRpcService();
        }
        return itmRpcService;
    }

    public IMdsalApiManager mdsalApiManager() {
        if (mdsalApiManager == null) {
            mdsalApiManager = TestIMdsalApiManager.newInstance();
        }
        return mdsalApiManager;
    }

    private IInterfaceManager interfaceManager() {
        if (interfaceManager == null) {
            interfaceManager = TestInterfaceManager.newInstance();
        }
        return interfaceManager;
    }

    private ElanStatusMonitor elanStatusMonitor() {
        if (elanStatusMonitor == null) {
            elanStatusMonitor = Mockito.mock(ElanStatusMonitor.class);
        }
        return elanStatusMonitor;
    }

    private EntityOwnershipService entityOwnershipService() {
        if (entityOwnershipService == null) {
            entityOwnershipService = Mockito.mock(EntityOwnershipService.class);
        }
        return entityOwnershipService;
    }

    @Override
    public void close() throws Exception {
        idManager.close();
    }

}
