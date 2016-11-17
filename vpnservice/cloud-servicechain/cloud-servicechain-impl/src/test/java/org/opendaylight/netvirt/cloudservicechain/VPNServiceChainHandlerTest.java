/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.matchers.FlowEntityMatcher;
import org.opendaylight.netvirt.cloudservicechain.matchers.FlowMatcher;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddressesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


@RunWith(MockitoJUnitRunner.class)
public class VPNServiceChainHandlerTest {

    static final String RD = "1.1.1.1:10";
    static final String VPN_NAME = "1";
    static final long VPN_ID = 1;
    static final long SCF_TAG = 1L;
    static final int SERV_CHAIN_TAG = 100;
    static final int DPN_ID = 1;
    static final int LPORT_TAG = 1;
    static final String DC_GW_IP = "3.3.3.3";

    private VPNServiceChainHandler vpnsch; // SUT

    @Mock
    DataBroker broker; // Collaborator
    @Mock
    FibRpcService fibRpcService;

    @Mock
    ReadOnlyTransaction readTx;
    @Mock
    WriteTransaction writeTx;
    @Mock
    IMdsalApiManager mdsalMgr;


    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {

        when(broker.newReadOnlyTransaction()).thenReturn(readTx);
        when(broker.newWriteOnlyTransaction()).thenReturn(writeTx);
        vpnsch = new VPNServiceChainHandler(broker, mdsalMgr, fibRpcService);
    }

    @After
    public void tearDown() throws Exception {
    }

    private void stubGetRouteDistinguisher(String vpnName) throws InterruptedException, ExecutionException {
        VpnInstanceBuilder vib = new VpnInstanceBuilder();
        vib.setDescription("aeiou");
        vib.setKey(new VpnInstanceKey(RD));
        Ipv4FamilyBuilder ipv4fb = new Ipv4FamilyBuilder().setRouteDistinguisher(RD);
        vib.setIpv4Family(ipv4fb.build());

        VpnInstance instance = vib.build();
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                                                               .child(VpnInstance.class, new VpnInstanceKey(vpnName))
                                                               .build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        // vib.setIpv4Family()
        when(chkdFuture.get()).thenReturn(Optional.of(instance));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(id))).thenReturn(chkdFuture);

    }

    private InstanceIdentifier<VpnInstance> buildVpnInstance() {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(VPN_NAME)).build();
        return id;
    }

    private void stubGetRouteDistinguisher_null(String vpnName) {

        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(buildVpnInstance()))).thenReturn(null);
    }

    /*
     * protected VpnInstanceOpDataEntry getVpnInstance(String rd) {
     * InstanceIdentifier<VpnInstanceOpDataEntry> id =
     * InstanceIdentifier.create(VpnInstanceOpData.class).child(
     * VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
     * Optional<VpnInstanceOpDataEntry> vpnInstanceOpData =
     * MDSALDataStoreUtils.read(broker, LogicalDatastoreType.OPERATIONAL, id);
     * if (vpnInstanceOpData.isPresent()) { return vpnInstanceOpData.get(); }
     * return null; }
     */

    private void stubGetVpnInstance(String rd) throws InterruptedException, ExecutionException {



        IpAddressesBuilder iab = new IpAddressesBuilder();

        iab.setIpAddress("1.3.4.5");
        iab.setKey(new IpAddressesKey("1.3.4.5"));

        LinkedList<IpAddresses> ipadd = new LinkedList<IpAddresses>();
        ipadd.add(iab.build());

        VpnToDpnListBuilder vtdlb = new VpnToDpnListBuilder();
        vtdlb.setDpnId(new BigInteger(Integer.toString(DPN_ID)));
        vtdlb.setIpAddresses(ipadd);
        vtdlb.setKey(new VpnToDpnListKey(new BigInteger(Integer.toString(DPN_ID))));

        VpnInterfacesBuilder vib = new VpnInterfacesBuilder();
        // final VpnInterfacesKey a =new VpnInterfacesKey("eth0");
        // vib.setKey(a);
        vib.setInterfaceName("eth0");
        LinkedList<VpnInterfaces> interfaces = new LinkedList<VpnInterfaces>();
        interfaces.add(vib.build());
        vtdlb.setVpnInterfaces(interfaces);

        LinkedList<VpnToDpnList> vpn2Dpn = new LinkedList<VpnToDpnList>();
        vpn2Dpn.add(vtdlb.build());

        VpnInstanceOpDataEntryBuilder vi = new VpnInstanceOpDataEntryBuilder();
        vi.setVpnId(Long.parseLong(VPN_NAME));
        vi.setVpnToDpnList(vpn2Dpn);

        LinkedList<Long> ids = new LinkedList<>();
        ids.add(1L);

        vi.setVrfId("1");
        vi.setKey(new VpnInstanceOpDataEntryKey(rd));

        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        when(chkdFuture.get()).thenReturn(Optional.of(vi.build()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), eq(id))).thenReturn(chkdFuture);
    }

    private void stubGetVpnInstanceNull(String rd) {

        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(id))).thenReturn(null);
    }

    private VrfEntry stubCreateDCGWVrfEntry() {
        VrfEntryBuilder vrb = new VrfEntryBuilder();
        vrb.setDestPrefix("123");
        vrb.setKey(new VrfEntryKey("123"));
        vrb.setLabel(1L);
        List<String> list = new ArrayList<String>();
        list.add(DC_GW_IP);
        vrb.setNextHopAddressList(list);
        return vrb.build();
    }

    private void stubGetVrfEntries(String rd) throws InterruptedException, ExecutionException {

        VrfTablesBuilder tables = new VrfTablesBuilder();
        tables.setKey(new VrfTablesKey(RD));
        tables.setRouteDistinguisher(RD);
        List<VrfEntry> vrfs = new LinkedList<>();
        vrfs.add(stubCreateDCGWVrfEntry());
        tables.setVrfEntry(vrfs);
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.get()).thenReturn(Optional.of(tables.build()));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(VpnServiceChainUtils.buildVrfId(rd))))
                .thenReturn(chkdFuture);

    }

    @Test
    public void testprogramScfToVpnPipelineNullRd() {
        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher_null(VPN_NAME);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID, LPORT_TAG,
                                       /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);
        // verify that nothing is written in Open Flow tables

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 0);

    }

    @Test
    public void testprogramScfToVpnPipelineNullVpnInstance() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(VPN_NAME);
        stubGetVpnInstanceNull(RD);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID, LPORT_TAG,
                                       /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 0);

    }

    @Test
    public void testprogramScfToVpnPipeline() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(VPN_NAME);
        stubGetVpnInstance(RD);
        stubGetVrfEntries(RD);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(VPN_NAME, SCF_TAG, SERV_CHAIN_TAG, DPN_ID, LPORT_TAG,
                                       /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);
        /////////////////////
        // Verify //
        /////////////////////

        VrfEntry ve = stubCreateDCGWVrfEntry();

        ArgumentCaptor<Flow> argumentCaptor = ArgumentCaptor.forClass(Flow.class);
        verify(mdsalMgr, times(2)).installFlow((BigInteger)anyObject(), argumentCaptor.capture());

        List<Flow> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 2);

        Flow expectedLportDispatcherFlowEntity = VpnServiceChainUtils.buildLPortDispFromScfToL3VpnFlow(VPN_ID,
                new BigInteger(String.valueOf(DPN_ID)), DPN_ID, NwConstants.ADD_FLOW);
        assert (new FlowMatcher(expectedLportDispatcherFlowEntity).matches(installedFlowsCaptured.get(0)));
    }

    @Test
    public void testprogramScfToVpnPipeline_negativeInputParameters() throws Exception {
        String vpnName = "access";
        int sftag = -1;
        int dpnId = -1;
        int lportTag = -1;
        String dcgwIp = "3.3.3.3";
        stubGetRouteDistinguisher(vpnName);
        // stub_buildVrfId(RD);
        vpnsch.programScfToVpnPipeline(vpnName, sftag, SERV_CHAIN_TAG, dpnId, lportTag, /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 0);

    }

    @Test
    public void testprogramSCFinVPNPipeline() throws Exception {

        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(VPN_NAME);
        stubGetVpnInstance(RD);
        stubGetVrfEntries(RD);

        short tableId = 10;
        vpnsch.programVpnToScfPipeline(VPN_NAME, tableId, SCF_TAG, LPORT_TAG, NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(2)).installFlow(argumentCaptor.capture());
        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 2);

        VrfEntry ve = stubCreateDCGWVrfEntry();
        FlowEntity expectedLFibFlowEntity =
            VpnServiceChainUtils.buildLFibVpnPseudoPortFlow(new BigInteger(String.valueOf(DPN_ID)),
                                                            ve.getLabel(),
                                                            ve.getNextHopAddressList().get(0),
                                                            LPORT_TAG);
        assert (new FlowEntityMatcher(expectedLFibFlowEntity).matches(installedFlowsCaptured.get(0)));

        FlowEntity expectedLPortDispatcher = VpnServiceChainUtils.buildLportFlowDispForVpnToScf(
                new BigInteger(String.valueOf(DPN_ID)), LPORT_TAG, SCF_TAG, tableId);
        assert (new FlowEntityMatcher(expectedLPortDispatcher).matches(installedFlowsCaptured.get(1)));

    }

}
