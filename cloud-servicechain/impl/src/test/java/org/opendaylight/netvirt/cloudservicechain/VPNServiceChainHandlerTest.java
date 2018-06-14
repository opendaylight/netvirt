/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.genius.mdsalutil.NWUtil.getEtherTypeFromIpPrefix;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.internal.JobCoordinatorImpl;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.metrics.testimpl.TestMetricProviderImpl;
import org.opendaylight.netvirt.cloudservicechain.matchers.FlowEntityMatcher;
import org.opendaylight.netvirt.cloudservicechain.matchers.FlowMatcher;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RunWith(MockitoJUnitRunner.class)
public class VPNServiceChainHandlerTest {

    static final Logger LOG = LoggerFactory.getLogger(VPNServiceChainHandler.class);

    static final String RD = "100:100";
    static final String VPN_NAME = "AccessVPN";
    static final long VPN_ID = 1;
    static final long SCF_TAG = 1L;
    static final int SERV_CHAIN_TAG = 100;
    static final BigInteger DPN_ID = BigInteger.valueOf(1L);
    static final int LPORT_TAG = 1;
    static final String DC_GW_IP = "3.3.3.3";

    private static MetricProvider metricProvider = new TestMetricProviderImpl();

    private static JobCoordinatorImpl jobCoordinator = new JobCoordinatorImpl(metricProvider);

    private VPNServiceChainHandler vpnsch; // SUT

    @Mock DataBroker broker;
    @Mock ReadOnlyTransaction readTx;
    @Mock WriteTransaction writeTx;
    @Mock IMdsalApiManager mdsalMgr;
    @Mock IVpnFootprintService vpnFootprintService;
    @Mock IInterfaceManager ifaceMgr;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        jobCoordinator.destroy();
    }

    @Before
    public void setUp() throws Exception {

        when(broker.newReadOnlyTransaction()).thenReturn(readTx);
        when(broker.newWriteOnlyTransaction()).thenReturn(writeTx);
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(writeTx.submit()).thenReturn(chkdFuture);

        // SUT
        vpnsch = new VPNServiceChainHandler(broker, mdsalMgr, vpnFootprintService, ifaceMgr, jobCoordinator);
    }

    @After
    public void tearDown() throws Exception {
    }

    private <T extends DataObject> Matcher<InstanceIdentifier<T>> isIIdType(final Class<T> klass) {
        return new TypeSafeMatcher<InstanceIdentifier<T>>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("Instance Identifier should have Target Type " + klass);
            }

            @Override
            protected boolean matchesSafely(InstanceIdentifier<T> id) {
                return id.getTargetType().equals(klass);
            }
        };
    }

    private void stubGetRouteDistinguisher(String vpnName, String rd) throws Exception {
        VpnInstance instance = new VpnInstanceBuilder().withKey(new VpnInstanceKey(vpnName)).setVrfId(rd)
                                                       .setVpnInstanceName(vpnName).build();

        InstanceIdentifier<VpnInstance> id = VpnServiceChainUtils.getVpnInstanceToVpnIdIdentifier(vpnName);
        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        when(chkdFuture.checkedGet()).thenReturn(Optional.of(instance));
        // when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(id))).thenReturn(chkdFuture);
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         argThat(isIIdType(VpnInstance.class)))).thenReturn(chkdFuture);
    }


    private void stubNoRdForVpnName(String vpnName) throws Exception {
        CheckedFuture<Optional<VpnInstance>, ReadFailedException> chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.absent());
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(VpnServiceChainUtils.getVpnInstanceToVpnIdIdentifier(vpnName))))
            .thenReturn(chkdFuture);
    }

    private void stubNoVpnInstanceForRD(String rd) throws Exception {
        CheckedFuture<Optional<VpnInstanceOpDataEntry>, ReadFailedException> chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.absent());

        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));

        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), eq(id))).thenReturn(chkdFuture);
    }

    private void stubGetVpnInstance(String rd, String ipAddress, String ifaceName) throws Exception {

        IpAddresses ipAddr =
            new IpAddressesBuilder().setIpAddress(ipAddress).withKey(new IpAddressesKey(ipAddress)).build();
        List<VpnInterfaces> ifacesList =
            Collections.singletonList(new VpnInterfacesBuilder().setInterfaceName(ifaceName).build());
        VpnToDpnListBuilder vtdlb =
            new VpnToDpnListBuilder().withKey(new VpnToDpnListKey(DPN_ID))
                                     .setDpnId(DPN_ID)
                                     .setIpAddresses(Collections.singletonList(ipAddr))
                                     .setVpnInterfaces(ifacesList);

        VpnInstanceOpDataEntry vpnInstanceOpDataEntry =
            new VpnInstanceOpDataEntryBuilder().withKey(new VpnInstanceOpDataEntryKey(rd))
                                               .setVpnId(VPN_ID)
                                               .setVpnToDpnList(Collections.singletonList(vtdlb.build()))
                                               .setVrfId("1").build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.of(vpnInstanceOpDataEntry));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL),
                         eq(VpnServiceChainUtils.getVpnInstanceOpDataIdentifier(rd)))).thenReturn(chkdFuture);
    }

    private void stubGetVrfEntries(String rd, List<VrfEntry> vrfEntryList)
        throws Exception {

        VrfTables tables = new VrfTablesBuilder().withKey(new VrfTablesKey(rd)).setRouteDistinguisher(rd)
                                                 .setVrfEntry(vrfEntryList).build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.of(tables));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(VpnServiceChainUtils.buildVrfId(rd))))
                .thenReturn(chkdFuture);

    }

    private void stubReadVpnToDpnList(String rd, BigInteger dpnId, List<String> vpnIfacesOnDpn)
        throws Exception {

        List<VpnInterfaces> vpnIfacesList =
            vpnIfacesOnDpn.stream()
                          .map((ifaceName) -> new VpnInterfacesBuilder().withKey(new VpnInterfacesKey(ifaceName))
                                                                        .setInterfaceName(ifaceName).build())
                          .collect(Collectors.toList());

        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.of(vpnIfacesList));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL),
                         eq(VpnServiceChainUtils.getVpnToDpnListIdentifier(rd, dpnId))))
             .thenReturn(chkdFuture);
    }

    private void stubScfIsBoundOnIface(long scfTag, String ifName) throws Exception {
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        BoundServices boundService =
            InterfaceServiceUtil.getBoundServices(ifName, NwConstants.SCF_SERVICE_INDEX,
                                                  CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY,
                                                  CloudServiceChainConstants.COOKIE_SCF_BASE,
                                                  null /*instructions*/);

        when(chkdFuture.checkedGet()).thenReturn(Optional.of(boundService));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(VpnServiceChainUtils.buildBoundServicesIid(NwConstants.SCF_SERVICE_INDEX, ifName))))
             .thenReturn(chkdFuture);
    }

    private void stubScfIsNotBoundOnIface(long scfTag, String ifName) throws Exception {
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.absent());
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(VpnServiceChainUtils.buildBoundServicesIid(NwConstants.SCF_SERVICE_INDEX, ifName))))
             .thenReturn(chkdFuture);
    }

    @Test
    public void testprogramScfToVpnPipelineNullRd() throws Exception {
        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubNoRdForVpnName(VPN_NAME);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID.longValue(), LPORT_TAG,
                                       /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);
        // verify that nothing is written in Open Flow tables

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert installedFlowsCaptured.isEmpty();

    }

    @Test
    public void testprogramScfToVpnPipelineNullVpnInstance() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(VPN_NAME, RD);
        stubNoVpnInstanceForRD(RD);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID.longValue(), LPORT_TAG,
                                       /* lastServiceChain */ false, NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert installedFlowsCaptured.isEmpty();

    }

    @Test
    public void testprogramScfToVpnPipeline() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(VPN_NAME, RD);
        stubGetVpnInstance(RD, "1.2.3.4", "eth0");
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder("11.12.13.14", 2000L, DC_GW_IP, RouteOrigin.STATIC, null)
                .build();
        stubGetVrfEntries(RD, Collections.singletonList(vrfEntry));
        stubReadVpnToDpnList(RD, DPN_ID, Collections.singletonList("iface1"));
        /////////
        // SUT //
        /////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID.longValue(), LPORT_TAG,
                                       /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);
        ////////////
        // Verify //
        ////////////

        // Verifying installed flows
        ArgumentCaptor<Flow> argumentCaptor = ArgumentCaptor.forClass(Flow.class);
        verify(mdsalMgr, times(1)).installFlow((BigInteger)anyObject(), argumentCaptor.capture());
        List<Flow> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert installedFlowsCaptured.size() == 1;
        Flow expectedLportDispatcherFlowEntity =
            VpnServiceChainUtils.buildLPortDispFromScfToL3VpnFlow(VPN_ID, DPN_ID, LPORT_TAG, NwConstants.ADD_FLOW);
        assert new FlowMatcher(expectedLportDispatcherFlowEntity).matches(installedFlowsCaptured.get(0));

        // Verifying VpnToDpn update
        String vpnPseudoPortIfaceName =
            VpnServiceChainUtils.buildVpnPseudoPortIfName(DPN_ID.longValue(), SCF_TAG, SERV_CHAIN_TAG, LPORT_TAG);
        verify(vpnFootprintService).updateVpnToDpnMapping(eq(DPN_ID), eq(VPN_NAME), eq(RD), eq(vpnPseudoPortIfaceName),
                                                          eq(null), eq(Boolean.TRUE));
    }


    @Test
    public void testProgramVpnToScfWithVpnIfacesAlreadyBound() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        String ifaceName = "eth0";
        stubGetRouteDistinguisher(VPN_NAME, RD);
        stubGetVpnInstance(RD, "1.2.3.4", ifaceName);
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder("11.12.13.14", 2000L, DC_GW_IP, RouteOrigin.STATIC, null)
                .build();
        stubGetVrfEntries(RD, Collections.singletonList(vrfEntry));
        stubReadVpnToDpnList(RD, DPN_ID, Collections.singletonList(ifaceName));
        stubScfIsBoundOnIface(SCF_TAG, ifaceName);

        /////////
        // SUT //
        /////////
        short tableId = 10;
        vpnsch.programVpnToScfPipeline(VPN_NAME, tableId, SCF_TAG, LPORT_TAG, NwConstants.ADD_FLOW);

        ////////////
        // Verify //
        ////////////
        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(2)).installFlow(argumentCaptor.capture());
        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert installedFlowsCaptured.size() == 2;

        RoutePaths routePath = vrfEntry.getRoutePaths().get(0);
        FlowEntity expectedLFibFlowEntity =
            VpnServiceChainUtils.buildLFibVpnPseudoPortFlow(DPN_ID, routePath.getLabel(),
                    getEtherTypeFromIpPrefix(vrfEntry.getDestPrefix()),
                    routePath.getNexthopAddress(), LPORT_TAG);
        assert new FlowEntityMatcher(expectedLFibFlowEntity).matches(installedFlowsCaptured.get(0));

        FlowEntity expectedLPortDispatcher =
            VpnServiceChainUtils.buildLportFlowDispForVpnToScf(DPN_ID, LPORT_TAG, SCF_TAG, tableId);
        assert new FlowEntityMatcher(expectedLPortDispatcher).matches(installedFlowsCaptured.get(1));

    }

    @Test
    public void testProgramVpnToScfWithIfacesNotBound() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        String ifaceName = "eth0";
        stubGetRouteDistinguisher(VPN_NAME, RD);
        stubGetVpnInstance(RD, "1.2.3.4", ifaceName);
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder("11.12.13.14", 2000L, DC_GW_IP, RouteOrigin.STATIC, null)
                .build();
        stubGetVrfEntries(RD, Collections.singletonList(vrfEntry));
        stubReadVpnToDpnList(RD, DPN_ID, Collections.singletonList(ifaceName));
        stubScfIsNotBoundOnIface(SCF_TAG, ifaceName);

        /////////
        // SUT //
        /////////
        short tableId = 10;
        vpnsch.programVpnToScfPipeline(VPN_NAME, tableId, SCF_TAG, LPORT_TAG, NwConstants.ADD_FLOW);

        ////////////
        // Verify //
        ////////////
        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(ifaceMgr).bindService(eq(ifaceName), eq(ServiceModeIngress.class), anyObject());
        verify(mdsalMgr, times(2)).installFlow(argumentCaptor.capture());
        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert installedFlowsCaptured.size() == 2;

        RoutePaths routePath = vrfEntry.getRoutePaths().get(0);
        FlowEntity expectedLFibFlowEntity =
            VpnServiceChainUtils.buildLFibVpnPseudoPortFlow(DPN_ID, routePath.getLabel(),
                    getEtherTypeFromIpPrefix(vrfEntry.getDestPrefix()),
                    routePath.getNexthopAddress(), LPORT_TAG);
        assert new FlowEntityMatcher(expectedLFibFlowEntity).matches(installedFlowsCaptured.get(0));

        FlowEntity expectedLPortDispatcher =
            VpnServiceChainUtils.buildLportFlowDispForVpnToScf(DPN_ID, LPORT_TAG, SCF_TAG, tableId);
        assert new FlowEntityMatcher(expectedLPortDispatcher).matches(installedFlowsCaptured.get(1));

    }

    @Test
    public void testBindScfOnVpnInterfaceWithScfAlreadyBound() throws Exception {
        String ifName = "eth4";
        int scfTag = 30;
        stubScfIsBoundOnIface(scfTag, ifName);

        vpnsch.bindScfOnVpnInterface(ifName, scfTag);

        verify(ifaceMgr, never()).bindService(anyObject(), anyObject(), anyObject());
    }

    @Test
    public void testBindScfOnVpnInterfaceWithScfNotBound() throws Exception {
        String ifName = "eth4";
        int scfTag = 30;
        stubScfIsNotBoundOnIface(scfTag, ifName);

        vpnsch.bindScfOnVpnInterface(ifName, scfTag);

        verify(ifaceMgr).bindService(eq(ifName), eq(ServiceModeIngress.class), anyObject());
    }
}
