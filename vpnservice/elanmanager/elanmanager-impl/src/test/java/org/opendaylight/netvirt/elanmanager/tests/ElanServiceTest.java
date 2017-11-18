/*
 * Copyright (C) 2016, 2017 Red Hat Inc., and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.evpn.listeners.ElanMacEntryListener;
import org.opendaylight.netvirt.elan.evpn.listeners.EvpnElanInstanceListener;
import org.opendaylight.netvirt.elan.evpn.listeners.MacVrfEntryListener;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.EvpnTestHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * End-to-end test of IElanService.
 *
 * @author Michael Vorburger
 * @author Riyazahmed Talikoti
 */
public class ElanServiceTest extends  ElanServiceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceTest.class);

    // TODO as-is, this test is flaky; as uncommenting will show
    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues (such as https://bugs.opendaylight.org/show_bug.cgi?id=7538)
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(ElanServiceTestModule.class);
    // TODO re-enable after we can await completion of listeners and DJC:
    // Otherwise this too frequently causes spurious test failures, e.g. due to error
    // logs Caused by: java.lang.RuntimeException: java.util.concurrent.ExecutionException: Operation was interrupted
    // public @Rule LogCaptureRule logCaptureRule = new LogCaptureRule();
    private @Inject IElanService elanService;
    private @Inject IdManagerService idManager;
    private @Inject EvpnElanInstanceListener evpnElanInstanceListener;
    private @Inject ElanMacEntryListener elanMacEntryListener;
    private @Inject MacVrfEntryListener macVrfEntryListener;
    private @Inject EvpnUtils evpnUtils;
    private @Inject IBgpManager bgpManager;
    private @Inject IVpnManager vpnManager;
    private SingleTransactionDataBroker singleTxdataBroker;
    private EvpnTestHelper evpnTestHelper;

    @Before public void before() throws TransactionCommitFailedException {
        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);
        evpnTestHelper = new EvpnTestHelper(dataBroker, singleTxdataBroker);
        evpnUtils.setBgpManager(bgpManager);
        evpnUtils.setVpnManager(vpnManager);
        setupItm();
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }

    @Test public void checkSMAC() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add Elan interface
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Read and Compare SMAC flow
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_SMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceInfo.getInterfaceTag())
                .append(interfaceInfo.getMacAddress())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidSrc = getFlowIid(NwConstants.ELAN_SMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidSrc);

        Flow flowSrc = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidSrc);
        flowSrc = getFlowWithoutCookie(flowSrc);

        Flow expected = ExpectedObjects.checkSmac(flowId, interfaceInfo, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowSrc);
    }

    @Test public void checkDmacSameDPN() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add Elan interface in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Read DMAC Flow in DPN1
        String flowId =  new StringBuffer()
                .append(NwConstants.ELAN_DMAC_TABLE)
                .append(actualElanInstances.getElanTag())
                .append(DPN1_ID)
                .append(interfaceInfo.getInterfaceTag())
                .append(interfaceInfo.getMacAddress())
                .toString();
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        Flow expected = ExpectedObjects.checkDmacOfSameDpn(flowId, interfaceInfo, actualElanInstances);
        AssertDataObjects.assertEqualBeans(expected, flowDst);
    }

    @Test public void checkDmacOfOtherDPN() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);

        // Read Elan instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);

        // Read and Compare DMAC flow in DPN1 for MAC1 of DPN2
        String flowId = ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        DPN1_ID,
                        DPN2_ID,
                        interfaceInfo.getMacAddress().toString(),
                        actualElanInstances.getElanTag());

        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE,
                new FlowId(flowId), DPN1_ID);
        awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);

        Flow flowDst = singleTxdataBroker.syncRead(CONFIGURATION, flowInstanceIidDst);
        flowDst = getFlowWithoutCookie(flowDst);

        TunnelInterfaceDetails tepDetails = EXTN_INTFS.get(DPN1_ID_STR + ":" + DPN2_ID_STR);
        Flow expected = ExpectedObjects.checkDmacOfOtherDPN(flowId, interfaceInfo, tepDetails,
                actualElanInstances);
        AssertDataObjects.assertEqualBeans(getSortedActions(expected), getSortedActions(flowDst));
    }

    @Test public void checkEvpnAdvRT2() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        Thread.sleep(5000);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getRight(), RD))
                .build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        Networks networks = singleTxdataBroker.syncRead(CONFIGURATION, iid);
        Networks expectedNetworks = ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC1, DPN1_TEPIP,
                ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getRight(), RD);
        AssertDataObjects.assertEqualBeans(expectedNetworks, networks);

    }

    @Test public void checkEvpnAdvRT2NewInterface() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        Thread.sleep(5000);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP1, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        Networks networks = singleTxdataBroker.syncRead(CONFIGURATION, iid);
        Networks expectedNetworks = ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC1, DPN1_TEPIP,
                ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getRight(), RD);
        AssertDataObjects.assertEqualBeans(expectedNetworks, networks);
    }

    @Test public void checkEvpnWithdrawRT2DelIntf() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        Thread.sleep(5000);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP1, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        evpnTestHelper.deleteRdtoNetworks();

        deleteElanInterface(interfaceInfo);
        singleTxdataBroker.syncDelete(CONFIGURATION, iid);

        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, iid);

    }

    @Test public void checkEvpnWithdrawRouteDetachEvpn() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        InterfaceInfo interfaceInfo1 = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo1);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo1, DPN1IP2);
        Thread.sleep(5000);

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP1, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid1 = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP2, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid1);

        evpnTestHelper.deleteEvpnNameInElan(ExpectedObjects.ELAN1);

        evpnTestHelper.deleteRdtoNetworks();

        Thread.sleep(5000);

        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, iid);
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, iid1);
    }

    @Test public void checkEvpnInstalDmacFlow() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        InterfaceInfo interfaceInfo1 = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo1);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo1, DPN1IP2);
        Thread.sleep(5000);

        List<String> nextHopList = new ArrayList<>();
        nextHopList.add(DCGW_TEPIP);
        evpnTestHelper.addMacVrfEntryToDS(RD, EVPNRECVMAC1, EVPNRECVIP1, nextHopList, VrfEntry.EncapType.Vxlan,
                ELAN1_SEGMENT_ID, null, RouteOrigin.BGP);

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP1, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid1 = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP2, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid1);


        evpnTestHelper.addMacVrfEntryToDS(RD, EVPNRECVMAC2, EVPNRECVIP2, nextHopList, VrfEntry.EncapType.Vxlan,
                ELAN1_SEGMENT_ID, null, RouteOrigin.BGP);

        InstanceIdentifier<MacVrfEntry> macVrfEntryIid =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(RD))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(EVPNRECVMAC1)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid);

        InstanceIdentifier<MacVrfEntry> macVrfEntryIid1 = InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(RD))
                .child(MacVrfEntry.class, new MacVrfEntryKey(EVPNRECVMAC2)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid1);
    }

    @Test public void checkEvpnUnInstalDmacFlow() throws Exception {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Add tap port in DPN1
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo);

        InterfaceInfo interfaceInfo1 = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        interfaceMgr.addInterfaceInfo(interfaceInfo1);

        // Add DPN1
        itmRpc.addDpn(DPN1_ID, DPN1_TEPIP);

        // Add external interface from DPN1 to DCGW
        itmRpc.addExternalInterface(DPN1_ID,
                DCGWID, EXTN_INTFS.get(DPN1_ID_STR + ":" + DCGWID).getInterfaceInfo().getInterfaceName());

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo1, DPN1IP2);

        List<String> nextHopList = new ArrayList<>();
        nextHopList.add(DCGW_TEPIP);
        evpnTestHelper.addMacVrfEntryToDS(RD, EVPNRECVMAC1, EVPNRECVIP1, nextHopList, VrfEntry.EncapType.Vxlan,
                ELAN1_SEGMENT_ID, null, RouteOrigin.BGP);

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // update RD to networks
        evpnTestHelper.updateRdtoNetworks(actualElanInstances);

        // Attach EVPN to a network
        evpnTestHelper.updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP1, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        // Read Bgp networks from datastore and compare with expected object
        InstanceIdentifier<Networks> iid1 = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(DPN1IP2, RD)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid1);


        evpnTestHelper.addMacVrfEntryToDS(RD, EVPNRECVMAC2, EVPNRECVIP2, nextHopList, VrfEntry.EncapType.Vxlan,
                ELAN1_SEGMENT_ID, null, RouteOrigin.BGP);

        InstanceIdentifier<MacVrfEntry> macVrfEntryIid =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(RD))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(EVPNRECVMAC1)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid);

        InstanceIdentifier<MacVrfEntry> macVrfEntryIid1 = InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(RD))
                .child(MacVrfEntry.class, new MacVrfEntryKey(EVPNRECVMAC2)).build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid1);


        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC1);
        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC2);
        Thread.sleep(5000);
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid);
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, macVrfEntryIid1);
    }

    private void awaitForElanTag(String elanName) {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanName)).build();
        getAwaiter().until(() -> {
            Optional<ElanInstance> elanInstance = MDSALUtil.read(dataBroker, CONFIGURATION, elanInstanceIid);
            return elanInstance.isPresent() && elanInstance.get().getElanTag() != null;
        });
    }
}
