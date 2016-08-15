/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.interfacemanager.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
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
@SuppressWarnings("deprecation")
public class ElanServiceTestModule implements AutoCloseable {

    // TODO Later, post-merge, propose replacing this kind of code with Dagger generated cod
    // (as originally proposed in https://git.opendaylight.org/gerrit/#/c/42109/

    // TODO Later, post-merge, propose replacing the non-OSGi part of elanmanager.xml with this
    // as proposed e.g. on https://git.opendaylight.org/gerrit/#/c/43754/

    private DataBroker dataBroker;
    private ElanServiceProvider elanService;
    private IdManager idManager;
    private IInterfaceManager interfaceManager;
    private ElanInstanceManager elanInstanceManager;
    private ElanBridgeManager bridgeMgr;
    private ElanInterfaceManager elanInterfaceManager;
    private ElanStatusMonitor elanStatusMonitor;
    private ElanUtils elanUtils;
    private TestIMdsalApiManager mdsalApiManager;
    private EntityOwnershipService entityOwnershipService;
    private ItmRpcService itmRpcService;
    private InterfaceManagerRpcService interfaceManagerRpcService;
    private ElanForwardingEntriesHandler elanForwardingEntriesHandler;
    private LockManagerService lockManager;

    public DataBroker dataBroker() {
        if (dataBroker == null) {
            dataBroker = DataBrokerTestModule.dataBroker();
        }
        return dataBroker;
    }

    public IElanService elanService() {
        if (elanService == null) {
            elanService = new ElanServiceProvider(idManager(), interfaceManager(), elanInstanceManager(), bridgeMgr(),
                    dataBroker(), elanInterfaceManager(), elanStatusMonitor(), elanUtils());
            elanService.init();
        }
        return elanService;
    }

    private ElanUtils elanUtils() {
        if (elanUtils == null) {
            elanUtils = new ElanUtils(dataBroker(), mdsalApiManager(), elanInstanceManager(),
                    interfaceManagerRpcService(), itmRpcService(), elanInterfaceManager(), entityOwnershipService());
        }
        return elanUtils;
    }

    private ElanInterfaceManager elanInterfaceManager() {
        if (elanInterfaceManager == null) {
            elanInterfaceManager = new ElanInterfaceManager(dataBroker(), idManager(), mdsalApiManager(),
                    interfaceManager(), elanForwardingEntriesHandler());
            elanInterfaceManager.init();
        }
        return elanInterfaceManager;
    }

    private ElanBridgeManager bridgeMgr() {
        if (bridgeMgr == null) {
            bridgeMgr = new ElanBridgeManager(dataBroker());
        }
        return bridgeMgr;
    }

    private ElanInstanceManager elanInstanceManager() {
        if (elanInstanceManager == null) {
            elanInstanceManager = new ElanInstanceManager(dataBroker(), idManager(), elanInterfaceManager(),
                    interfaceManager());
            elanInstanceManager.init();
        }
        return elanInstanceManager;
    }

    private ElanForwardingEntriesHandler elanForwardingEntriesHandler() {
        if (elanForwardingEntriesHandler == null) {
            elanForwardingEntriesHandler = new ElanForwardingEntriesHandler(dataBroker());
        }
        return elanForwardingEntriesHandler;
    }

    private IdManagerService idManager() {
        if (idManager == null) {
            idManager = new IdManager(dataBroker());
            idManager.setLockManager(lockManager());
        }
        return idManager;
    }

    private LockManagerService lockManager() {
        if (lockManager == null) {
            lockManager = new LockManager(dataBroker());
        }
        return lockManager;
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

    public void start() {
        elanService();
    }

    @Override
    public void close() throws Exception {
        idManager.close();
        elanInterfaceManager.close();
        elanInstanceManager.close();
    }
}
