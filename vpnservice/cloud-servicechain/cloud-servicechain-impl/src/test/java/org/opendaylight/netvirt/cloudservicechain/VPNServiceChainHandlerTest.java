/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyObject;

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
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
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

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

@RunWith(MockitoJUnitRunner.class)
public class VPNServiceChainHandlerTest {

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

    final String RD = "1.1.1.1:10";
    final String vpnName = "1";
    final long vpnId = 1;
    final int scfTag = 1;
    final int servChainTag = 100;
    final int dpnId = 1;
    final int lportTag = 1;
    final String dcgwIp = "3.3.3.3";

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
        vpnsch = new VPNServiceChainHandler(broker, fibRpcService);
        vpnsch.setMdsalManager(mdsalMgr);
        vpnsch.setFibRpcService(fibRpcService);
    }

    @After
    public void tearDown() throws Exception {
    }

    private void stubGetRouteDistinguisher(String vpnName) throws InterruptedException, ExecutionException {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        VpnInstanceBuilder vib = new VpnInstanceBuilder();
        vib.setDescription("aeiou");
        vib.setKey(new VpnInstanceKey(RD));
        Ipv4FamilyBuilder ipv4fb = new Ipv4FamilyBuilder().setRouteDistinguisher(RD);
        vib.setIpv4Family(ipv4fb.build());

        VpnInstance instance = vib.build();
        VpnAfConfig config = instance.getIpv4Family();
        String myrd = config.getRouteDistinguisher();

        // vib.setIpv4Family()
        when(chkdFuture.get()).thenReturn(Optional.of(instance));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(id))).thenReturn(chkdFuture);

    }

    private InstanceIdentifier<VpnInstance> buildVpnInstance() {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
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

        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        IpAddressesBuilder iab = new IpAddressesBuilder();

        iab.setIpAddress("1.3.4.5");
        iab.setKey(new IpAddressesKey("1.3.4.5"));

        LinkedList<IpAddresses> ipadd = new LinkedList<IpAddresses>();
        ipadd.add(iab.build());

        VpnToDpnListBuilder vtdlb = new VpnToDpnListBuilder();
        vtdlb.setDpnId(new BigInteger(Integer.toString(dpnId)));
        vtdlb.setIpAddresses(ipadd);
        vtdlb.setKey(new VpnToDpnListKey(new BigInteger(Integer.toString(dpnId))));

        VpnInterfacesBuilder vib = new VpnInterfacesBuilder();
        // final VpnInterfacesKey a =new VpnInterfacesKey("eth0");
        // vib.setKey(a);
        vib.setInterfaceName("eth0");
        LinkedList<VpnInterfaces> interfaces = new LinkedList<VpnInterfaces>();
        interfaces.add(vib.build());
        vtdlb.setVpnInterfaces(interfaces);

        LinkedList<VpnToDpnList> v = new LinkedList<VpnToDpnList>();
        v.add(vtdlb.build());

        VpnInstanceOpDataEntryBuilder vi = new VpnInstanceOpDataEntryBuilder();
        vi.setVpnId(Long.parseLong(vpnName));
        vi.setVpnToDpnList(v);

        LinkedList<Long> ids = new LinkedList<>();
        ids.add(1L);



        vi.setRouteEntryId(ids);
        vi.setVpnInterfaceCount(1L);
        vi.setVrfId("1");
        vi.setKey(new VpnInstanceOpDataEntryKey(rd));

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
        list.add(dcgwIp);
        vrb.setNextHopAddressList(list);
        return vrb.build();
    }

    private void stubGetVrfEntries(String rd) throws InterruptedException, ExecutionException {

        CheckedFuture chkdFuture = mock(CheckedFuture.class);

        VrfTablesBuilder tables = new VrfTablesBuilder();
        tables.setKey(new VrfTablesKey(RD));
        tables.setRouteDistinguisher(RD);
        List<VrfEntry> vrfs = new LinkedList<>();
        vrfs.add(stubCreateDCGWVrfEntry());
        tables.setVrfEntry(vrfs);
        when(chkdFuture.get()).thenReturn(Optional.of(tables.build()));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(VpnServiceChainUtils.buildVrfId(rd))))
                .thenReturn(chkdFuture);

    }

    @Test
    public void testprogramScfToVpnPipelineNullRd() {
        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher_null(vpnName);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(vpnName, scfTag, servChainTag, dpnId, lportTag, /* lastServiceChain */ false,
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
        stubGetRouteDistinguisher(vpnName);
        stubGetVpnInstanceNull(RD);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(vpnName, scfTag, servChainTag, dpnId, lportTag, /* lastServiceChain */ false,
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
        stubGetRouteDistinguisher(vpnName);
        stubGetVpnInstance(RD);
        stubGetVrfEntries(RD);
        /////////////////////
        // SUT //
        /////////////////////
        vpnsch.programScfToVpnPipeline(vpnName, scfTag, servChainTag, dpnId, lportTag, /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);
        /////////////////////
        // Verify //
        /////////////////////

        VrfEntry ve = stubCreateDCGWVrfEntry();

        ArgumentCaptor<Flow> argumentCaptor = ArgumentCaptor.forClass(Flow.class);
        verify(mdsalMgr, times(1)).installFlow((BigInteger)anyObject(), argumentCaptor.capture());

        List<Flow> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 1);

        Flow expectedLportDispatcherFlowEntity = VpnServiceChainUtils.buildLPortDispFromScfToL3VpnFlow(vpnId,
                new BigInteger(String.valueOf(dpnId)), dpnId, NwConstants.ADD_FLOW);
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
        vpnsch.programScfToVpnPipeline(vpnName, sftag, servChainTag, dpnId, lportTag, /* lastServiceChain */ false,
                                       NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(0)).installFlow(argumentCaptor.capture());

        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 0);

    }

    @Test
    public void testprogramSCFinVPNPipeline() throws Exception {

        short tableId = 10;
        /////////////////////
        // Basic stubbing //
        /////////////////////
        stubGetRouteDistinguisher(vpnName);
        stubGetVpnInstance(RD);
        stubGetVrfEntries(RD);
        VrfEntry ve = stubCreateDCGWVrfEntry();

        vpnsch.programVpnToScfPipeline(vpnName, tableId, scfTag, lportTag, NwConstants.ADD_FLOW);

        ArgumentCaptor<FlowEntity> argumentCaptor = ArgumentCaptor.forClass(FlowEntity.class);
        verify(mdsalMgr, times(2)).installFlow(argumentCaptor.capture());
        List<FlowEntity> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 2);

        FlowEntity expectedLFibFlowEntity = VpnServiceChainUtils.buildLFibVpnPseudoPortFlow(new BigInteger(String.valueOf(dpnId)),
                ve.getLabel(), ve.getNextHopAddressList().get(0), lportTag);
        assert (new FlowEntityMatcher(expectedLFibFlowEntity).matches(installedFlowsCaptured.get(0)));

        FlowEntity expectedLPortDispatcher = VpnServiceChainUtils.buildLportFlowDispForVpnToScf(
                new BigInteger(String.valueOf(dpnId)), lportTag, scfTag, tableId);
        assert (new FlowEntityMatcher(expectedLPortDispatcher).matches(installedFlowsCaptured.get(1)));

    }

}
