/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.mockito.Mockito.CALLS_REAL_METHODS;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.daexim.DataImportBootReady;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.lockmanager.LockManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.internal.MDSALManager;
import org.opendaylight.genius.testutils.TestInterfaceManager;
import org.opendaylight.genius.testutils.itm.ItmRpcTestImpl;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.ElanEgressActionsHelper;
import org.opendaylight.netvirt.elanmanager.tests.utils.IdHelper;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnManagerImpl;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;


/**
 * Equivalent of src/main/resources/org/opendaylight/blueprint/elanmanager.xml,
 * in 2016 syntax (i.e. Java; strongly typed!) instead of late 1990s XML syntax.
 *
 * @author Michael Vorburger
 */
public class ElanServiceTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() {
        DataBroker dataBroker = DataBrokerTestModule.dataBroker();
        bind(EntityOwnershipService.class).toInstance(Mockito.mock(EntityOwnershipService.class));
        bind(ElanStatusMonitor.class).toInstance(Mockito.mock(ElanStatusMonitor.class));
        bind(INeutronVpnManager.class).toInstance(Mockito.mock(NeutronvpnManagerImpl.class));
        //IVpnManager ivpnManager = Mockito.mock(VpnManagerTestImpl.class, CALLS_REAL_METHODS);
        bind(IMdsalApiManager.class).toInstance(new MDSALManager(dataBroker,
                Mockito.mock(PacketProcessingService.class)));

        // Bindings for external services to "real" implementations
        bind(LockManagerService.class).to(LockManager.class);
        bind(ElanConfig.class).toInstance(new ElanConfigBuilder().setIntBridgeGenMac(true)
                        .setTempSmacLearnTimeout(10).build());

        // Bindings of all listeners (which are not directly referenced in the code)
        // This is required to be explicit here, because these are referenced neither from src/main nor src/test
        // and we, intentionally, don't use "classpath scanning for auto-discovery"
        // so this list must kept, manually, in line with the rc/main/resources/org/opendaylight/blueprint/*.xml
        // and target/generated-resources/org/opendaylight/blueprint/autowire.xml
        // bind(ElanGroupListener.class);
        // TODO complete this list!!! after Gerrit which adds @Inject to all listeners

        // Bindings to test infra (fakes & mocks)

        TestInterfaceManager obj = TestInterfaceManager.newInstance(dataBroker);
        ItmRpcService itmRpcService = new ItmRpcTestImpl();
        //IBgpManager ibgpManager = BgpManagerTestImpl.newInstance(dataBroker);
        bind(DataBroker.class).toInstance(dataBroker);
        bind(IdManagerService.class).toInstance(Mockito.mock(IdHelper.class,  CALLS_REAL_METHODS));
        bind(IInterfaceManager.class).toInstance(obj);
        bind(TestInterfaceManager.class).toInstance(obj);
        bind(OdlInterfaceRpcService.class).toInstance(ElanEgressActionsHelper.newInstance(dataBroker));
        bind(ItmRpcService.class).toInstance(itmRpcService);
        bind(ItmRpcTestImpl.class).toInstance((ItmRpcTestImpl)itmRpcService);
        //bind(IVpnManager.class).toInstance(ivpnManager);
        //bind(IBgpManager.class).toInstance(ibgpManager);
        bind(DataImportBootReady.class).toInstance(new DataImportBootReady() {});
        bind(IElanService.class).to(ElanServiceProvider.class);
    }

}
