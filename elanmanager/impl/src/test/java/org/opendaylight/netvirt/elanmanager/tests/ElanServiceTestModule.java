/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.daexim.DataImportBootReady;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.genius.interfacemanager.commons.InterfaceMetaUtils;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.interfacemanager.renderer.ovs.utilities.BatchingUtils;
import org.opendaylight.genius.itm.api.IITMProvider;
import org.opendaylight.genius.lockmanager.impl.LockManagerServiceImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.internal.MDSALManager;
import org.opendaylight.genius.testutils.TestInterfaceManager;
import org.opendaylight.genius.testutils.TestItmProvider;
import org.opendaylight.genius.testutils.itm.ItmRpcTestImpl;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.internal.HwvtepNodeHACacheImpl;
import org.opendaylight.infrautils.diagstatus.DiagStatusService;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.metrics.testimpl.TestMetricProviderImpl;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.EntityOwnershipState;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.cache.impl.l2gw.L2GatewayCacheImpl;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.BgpManagerTestImpl;
import org.opendaylight.netvirt.elanmanager.tests.utils.ElanEgressActionsHelper;
import org.opendaylight.netvirt.elanmanager.tests.utils.IdHelper;
import org.opendaylight.netvirt.elanmanager.tests.utils.VpnManagerTestImpl;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnManagerImpl;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.ovsdb.utils.mdsal.utils.ControllerMdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
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
        EntityOwnershipService mockedEntityOwnershipService = mock(EntityOwnershipService.class);
        EntityOwnershipState mockedEntityOwnershipState = EntityOwnershipState.IS_OWNER;
        Mockito.when(mockedEntityOwnershipService.getOwnershipState(Mockito.any()))
                .thenReturn(Optional.of(mockedEntityOwnershipState));
        bind(EntityOwnershipService.class).toInstance(mockedEntityOwnershipService);
        bind(L2GatewayCache.class).to(L2GatewayCacheImpl.class);
        bind(HwvtepNodeHACache.class).to(HwvtepNodeHACacheImpl.class);
        bind(ServiceRecoveryRegistry.class).toInstance(mock(ServiceRecoveryRegistry.class));
        bind(INeutronVpnManager.class).toInstance(mock(NeutronvpnManagerImpl.class));
        IVpnManager ivpnManager = mock(VpnManagerTestImpl.class, CALLS_REAL_METHODS);
        MDSALManager mockedMdsalManager = new MDSALManager(dataBroker, mock(PacketProcessingService.class));
        bind(IMdsalApiManager.class).toInstance(mockedMdsalManager);

        // Bindings for external services to "real" implementations
        bind(LockManagerService.class).to(LockManagerServiceImpl.class);
        bind(ElanConfig.class).toInstance(new ElanConfigBuilder().setIntBridgeGenMac(true)
                        .setTempSmacLearnTimeout(10).build());
        bind(MetricProvider.class).toInstance(new TestMetricProviderImpl());

        // Bindings of all listeners (which are not directly referenced in the code)
        // This is required to be explicit here, because these are referenced neither from src/main nor src/test
        // and we, intentionally, don't use "classpath scanning for auto-discovery"
        // so this list must kept, manually, in line with the rc/main/resources/org/opendaylight/blueprint/*.xml
        // and target/generated-resources/org/opendaylight/blueprint/autowire.xml
        // bind(ElanGroupListener.class);
        // TODO complete this list!!! after Gerrit which adds @Inject to all listeners

        // Bindings to test infra (fakes & mocks)

        TestInterfaceManager testInterfaceManager = TestInterfaceManager.newInstance(dataBroker);
        IITMProvider testItmProvider = TestItmProvider.newInstance();
        ItmRpcService itmRpcService = new ItmRpcTestImpl();

        bind(DataBroker.class).toInstance(dataBroker);
        bind(IdManagerService.class).toInstance(mock(IdHelper.class,  CALLS_REAL_METHODS));
        bind(IInterfaceManager.class).toInstance(testInterfaceManager);
        bind(TestInterfaceManager.class).toInstance(testInterfaceManager);
        bind(IITMProvider.class).toInstance(testItmProvider);
        InterfaceMetaUtils interfaceMetaUtils = new InterfaceMetaUtils(dataBroker,
                mock(IdHelper.class,  CALLS_REAL_METHODS),
                mock(BatchingUtils.class));

        InterfaceManagerCommonUtils interfaceManagerCommonUtils = new InterfaceManagerCommonUtils(
                dataBroker,
                mockedMdsalManager,
                mock(IdHelper.class,  CALLS_REAL_METHODS),
                interfaceMetaUtils,
                mock(BatchingUtils.class));


        bind(OdlInterfaceRpcService.class).toInstance(ElanEgressActionsHelper.newInstance(interfaceManagerCommonUtils,
                testInterfaceManager));
        SingleTransactionDataBroker singleTransactionDataBroker = new SingleTransactionDataBroker(dataBroker);
        bind(SingleTransactionDataBroker.class).toInstance(singleTransactionDataBroker);
        IBgpManager ibgpManager = BgpManagerTestImpl.newInstance(singleTransactionDataBroker);
        bind(ItmRpcService.class).toInstance(itmRpcService);
        bind(ItmRpcTestImpl.class).toInstance((ItmRpcTestImpl)itmRpcService);
        bind(DataImportBootReady.class).toInstance(new DataImportBootReady() {});
        bind(DiagStatusService.class).toInstance(mock(DiagStatusService.class));
        bind(IVpnManager.class).toInstance(ivpnManager);
        bind(IBgpManager.class).toInstance(ibgpManager);
        bind(IElanService.class).to(ElanServiceProvider.class);
        bind(SalFlowService.class).toInstance(mock(SalFlowService.class));

        ControllerMdsalUtils mdsalUtils = new ControllerMdsalUtils(dataBroker);
        bind(ControllerMdsalUtils.class).toInstance(mdsalUtils);
        bind(SouthboundUtils.class).toInstance(new SouthboundUtils(mdsalUtils));
    }
}
