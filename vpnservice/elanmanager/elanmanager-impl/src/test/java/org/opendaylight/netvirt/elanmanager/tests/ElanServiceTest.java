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
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.jobcoordinator.internal.JobCoordinatorImpl;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.evpn.listeners.ElanMacEntryListener;
import org.opendaylight.netvirt.elan.evpn.listeners.EvpnElanInstanceListener;
import org.opendaylight.netvirt.elan.evpn.listeners.MacVrfEntryListener;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.internal.ElanDpnInterfaceClusteredListener;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepLocalUcastMacListener;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepPhysicalSwitchListener;
import org.opendaylight.netvirt.elan.l2gw.listeners.L2GatewayConnectionListener;
import org.opendaylight.netvirt.elan.l2gw.nodehandlertest.DataProvider;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.elanmanager.tests.utils.EvpnTestHelper;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.netvirt.neutronvpn.l2gw.L2GatewayListener;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
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
    public @Rule MethodRule guice = new GuiceRule(ElanServiceTestModule.class, JobCoordinatorTestModule.class);
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
    private @Inject EvpnTestHelper evpnTestHelper;
    private @Inject OdlInterfaceRpcService odlInterfaceRpcService;
    private @Inject ElanL2GatewayUtils elanL2GatewayUtils;
    private @Inject ElanInterfaceManager elanInterfaceManager;
    private @Inject HwvtepPhysicalSwitchListener hwvtepPhysicalSwitchListener;
    private @Inject L2GatewayConnectionListener l2GatewayConnectionListener;
    private @Inject HwvtepLocalUcastMacListener hwvtepLocalUcastMacListener;
    private @Inject ElanDpnInterfaceClusteredListener elanDpnInterfaceClusteredListener;
    private @Inject EntityOwnershipService mockedEntityOwnershipService;
    private L2GatewayListener l2gwListener;

    private Verifications verifications;
    private L2gwBuilders l2gwBuilders;

    private SingleTransactionDataBroker singleTxdataBroker;

    @Before public void before() throws Exception {

        singleTxdataBroker = new SingleTransactionDataBroker(dataBroker);
        verifications = new Verifications(singleTxdataBroker, odlInterfaceRpcService, EXTN_INTFS);
        l2gwBuilders = new L2gwBuilders(singleTxdataBroker);
        JobCoordinator jobCoordinator = new JobCoordinatorImpl();

        l2gwListener = new L2GatewayListener(dataBroker, mockedEntityOwnershipService,
                Mockito.mock(ItmRpcService.class), Mockito.mock(IL2gwService.class), jobCoordinator);
        l2gwListener.init();
        setupItm();
        l2gwBuilders.buildTorNode(TOR2NODEID, "s4", TOR2_TEPIP);
        l2gwBuilders.buildTorNode(TOR1NODEID, "s3", TOR1_TEPIP);
    }

    @After public void after() throws Exception {
        for (ResourceBatchingManager.ShardResource i : ResourceBatchingManager.ShardResource.values()) {
            ResourceBatchingManager.getInstance().deregisterBatchableResource(i.name());
        }
        L2GatewayCacheUtils.removeL2DeviceFromCache("s3");
        L2GatewayCacheUtils.removeL2DeviceFromCache("s4");

        ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(TOR1NODEID);
        ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(TOR2NODEID);

        ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(ExpectedObjects.ELAN1, TOR1NODEID);
        ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(ExpectedObjects.ELAN1, TOR2NODEID);

        Set<DpnInterfaces> dpns =  ElanUtils.getElanInvolvedDPNsFromCache(ExpectedObjects.ELAN1);
        if (dpns != null) {
            dpns.clear();
        }
    }

    @Test public void elanServiceTestModule() {
        // Intentionally empty; the goal is just to first test the ElanServiceTestModule
    }

    void createL2gwL2gwconn(InstanceIdentifier<Node> nodePath, String l2gwName, String deviceName,
                                    List<String> ports, String connectionName) throws InterruptedException {

        //Create l2gw
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gwBuilders.buildL2gwIid(l2gwName), l2gwBuilders.buildL2gw(l2gwName, deviceName, ports));
        awaitForData(LogicalDatastoreType.CONFIGURATION, l2gwBuilders.buildL2gwIid(l2gwName));

        //Create l2gwconn
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                l2gwBuilders.buildConnectionIid(connectionName), l2gwBuilders.buildConnection(connectionName,
                        l2gwName, ExpectedObjects.ELAN1, 100));
        awaitForData(LogicalDatastoreType.CONFIGURATION, l2gwBuilders.buildConnectionIid(connectionName));

        //check for Config Logical Switch creation
        InstanceIdentifier logicalSwitchesInstanceIdentifier =
                HwvtepSouthboundUtils.createLogicalSwitchesInstanceIdentifier(
                        nodePath.firstKeyOf(Node.class).getNodeId(),
                        new HwvtepNodeName(ExpectedObjects.ELAN1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, logicalSwitchesInstanceIdentifier);

        //create operational logical switch
        Optional<LogicalSwitches> logicalSwitchesOptional =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, logicalSwitchesInstanceIdentifier);
        LogicalSwitches logicalSwitches = logicalSwitchesOptional.isPresent() ? logicalSwitchesOptional.get() : null ;
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                logicalSwitchesInstanceIdentifier, logicalSwitches);
        awaitForData(LogicalDatastoreType.OPERATIONAL, logicalSwitchesInstanceIdentifier);
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
        createElanInstanceAndInterfaceAndAttachEvpn();


        AssertDataObjects.assertEqualBeans(
                ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC1, DPN1_TEPIP, DPN1IP1, RD),
                readBgpNetworkFromDS(DPN1IP1));
    }

    @Test public void checkEvpnAdvRT2NewInterface() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        AssertDataObjects.assertEqualBeans(
                ExpectedObjects.checkEvpnAdvertiseRoute(ELAN1_SEGMENT_ID, DPN1MAC2, DPN1_TEPIP, DPN1IP2, RD),
                readBgpNetworkFromDS(DPN1IP2));
    }

    @Test public void checkEvpnWithdrawRT2DelIntf() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();

        InstanceIdentifier<Networks> iid = evpnTestHelper.buildBgpNetworkIid(DPN1IP1);
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        evpnTestHelper.deleteRdtoNetworks();

        deleteElanInterface(ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft());
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, iid);
    }

    @Test public void checkEvpnWithdrawRouteDetachEvpn() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        evpnTestHelper.detachEvpnToNetwork(ExpectedObjects.ELAN1);

        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));
    }

    @Test public void checkEvpnInstalDmacFlow() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        // Verify advertise RT2 route success for both MAC's
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        // RT2 received from Peer
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC1, EVPNRECVIP1);
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC2, EVPNRECVIP2);

        // verify successful installation of DMAC flow for recvd rt2
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));
    }

    @Test public void checkEvpnUnInstalDmacFlow() throws Exception {
        createElanInstanceAndInterfaceAndAttachEvpn();
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft(), DPN1IP2);

        // Verify advertise RT2 route success for both MAC's
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildBgpNetworkIid(DPN1IP2));

        // RT2 received from Peer
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC1, EVPNRECVIP1);
        evpnTestHelper.handleEvpnRt2Recvd(EVPNRECVMAC2, EVPNRECVIP2);

        // verify successful installation of DMAC flow for recvd rt2
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForData(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));

        // withdraw RT2 received from Peer
        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC1);
        evpnTestHelper.deleteMacVrfEntryToDS(RD, EVPNRECVMAC2);

        // verify successful un-installation of DMAC flow for recvd rt2
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC1));
        awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, evpnTestHelper.buildMacVrfEntryIid(EVPNRECVMAC2));
    }

    public void createElanInstanceAndInterfaceAndAttachEvpn() throws ReadFailedException,
            TransactionCommitFailedException {
        // Create Elan instance
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        // Read Elan Instance
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance elanInstance = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);

        // Add Elan interface
        addElanInterface(ExpectedObjects.ELAN1, ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft(), DPN1IP1);

        // Attach EVPN to networks
        evpnTestHelper.attachEvpnToNetwork(elanInstance);
    }

    public Networks readBgpNetworkFromDS(String prefix) throws ReadFailedException {
        InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(prefix, RD))
                .build();
        awaitForData(LogicalDatastoreType.CONFIGURATION, iid);

        return singleTxdataBroker.syncRead(CONFIGURATION, iid);
    }

    private void awaitForElanTag(String elanName) {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanName)).build();
        getAwaiter().until(() -> {
            Optional<ElanInstance> elanInstance = MDSALUtil.read(dataBroker, CONFIGURATION, elanInstanceIid);
            return elanInstance.isPresent() && elanInstance.get().getElanTag() != null;
        });
    }

    @Test
    public void verifyL2gw1Connection() throws Exception {

        //Create ELAN
        createElanInstance(ExpectedObjects.ELAN1, ExpectedObjects.ELAN1_SEGMENT_ID);
        awaitForElanTag(ExpectedObjects.ELAN1);

        //Add Elan MAC1, MAC2 in DPN1
        InterfaceInfo interfaceInfo = null;
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN1MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN1IP2);

        //Add Elan MAC1, MAC2 in DPN2
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP1);
        interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN2MAC2).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN2IP2);

        createL2gwL2gwconn(tor1NodeIid, "l2gw1", "s3", DataProvider.getPortNameListD1(),
                "l2gwConnection1");

        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor1NodeIid, true, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP));
        verifications.checkForRemoteUcastMacs(tor1NodeIid, Arrays.asList(DPN1MAC1, DPN1MAC2, DPN2MAC1, DPN2MAC2), true);

        verifications.validateDPNGroup(DPN1_ID, Lists.newArrayList(DPN2_ID), Lists.newArrayList(TOR1_TEPIP),
                true);
        verifications.validateDPNGroup(DPN2_ID, Lists.newArrayList(DPN1_ID), Lists.newArrayList(TOR1_TEPIP),
                true);
    }

    @Test
    public void verifyL2gwMac1InDpns() throws Exception {
        verifyL2gw1Connection();
        l2gwBuilders.createLocalUcastMac(tor1NodeIid, TOR1MAC1, "10.0.0.1", "192.168.122.30");
        verifications.verifyDmacFlowOfTORInDpns(Arrays.asList(DPN1_ID, DPN2_ID), TOR1NODEID, TOR1MAC1, true);
    }

    @Test
    public void verifyL2gw2Connection() throws Exception {
        verifyL2gwMac1InDpns();
        // TOR Node 2 creation
        createL2gwL2gwconn(tor2NodeIid, "l2gw2", "s4", DataProvider.getPortNameListTor2(),
                "l2gwConnection2");
        //check for remote mcast mac in tor2 against TEPs of dpn1, dpn2 and dpn3, tor1)
        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor2NodeIid,
                true, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP, TOR1_TEPIP));
        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor1NodeIid,
                true, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP, TOR2_TEPIP));
    }

    @Test
    public void verifyL2gwMac2InTors() throws Exception {
        verifyL2gw2Connection();
        l2gwBuilders.createLocalUcastMac(tor1NodeIid, TOR1MAC2, "10.0.0.2", "192.168.122.30");
        verifications.checkForRemoteUcastMac(tor2NodeIid, TOR1MAC2, true);
    }

    @Test
    public void verifyL2gwMacDeleteInTors() throws Exception {
        verifyL2gwMac2InTors();

        LocalUcastMacs localUcastMacs1 = l2gwBuilders.createLocalUcastMac(tor1NodeIid, TOR1MAC1, "10.0.0.1",
                "192.168.122.30");
        InstanceIdentifier<LocalUcastMacs> localUcastMacsId = l2gwBuilders.buildMacIid(tor1NodeIid, localUcastMacs1);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, localUcastMacsId);
        verifications.verifyDmacFlowOfTOR(DPN1_ID, TOR1NODEID, TOR1MAC1, false);
        verifications.verifyDmacFlowOfTOR(DPN2_ID, TOR1NODEID, TOR1MAC1, false);
        verifications.checkForRemoteUcastMac(tor2NodeIid, TOR1MAC1, false);
    }

    @Test
    public void verifyAddDpnAfterL2gwConnection() throws Exception {
        verifyL2gwMac2InTors();
        //Add Elan MAC1, MAC2 in DPN3
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        addElanInterface(ExpectedObjects.ELAN1, interfaceInfo, DPN3IP1);

        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor2NodeIid,
                true, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP, DPN3_TEPIP, TOR1_TEPIP));
        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor1NodeIid,
                true, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP, DPN3_TEPIP, TOR2_TEPIP));

        verifications.checkForRemoteUcastMacs(tor1NodeIid, Arrays.asList(DPN3MAC1), true);
        verifications.checkForRemoteUcastMacs(tor2NodeIid, Arrays.asList(DPN3MAC1), true);
        verifications.verifyDmacFlowOfTOR(DPN3_ID, TOR1NODEID, TOR1MAC1, true);
    }

    @Test
    public void verifyDeleteDpnAfterL2gwConnection() throws Exception {
        verifyAddDpnAfterL2gwConnection();
        InterfaceInfo interfaceInfo = ELAN_INTERFACES.get(ELAN1 + ":" + DPN3MAC1).getLeft();
        deleteElanInterface(interfaceInfo);

        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor2NodeIid,
                false, Arrays.asList(DPN3_TEPIP));
        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor1NodeIid,
                false, Arrays.asList(DPN3_TEPIP));

        verifications.checkForRemoteUcastMacs(tor1NodeIid, Arrays.asList(DPN3MAC1), false);
        verifications.checkForRemoteUcastMacs(tor2NodeIid, Arrays.asList(DPN3MAC1), false);
    }

    @Test
    public void verifyDeleteL2gw1Connection() throws Exception {
        verifyL2gw2Connection();
        //delete node 2 (tor2) l2gw connection
        l2gwBuilders.deletel2GWConnection("l2gwConnection1");

        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor1NodeIid,
                false, Arrays.asList(DPN1_TEPIP, DPN2_TEPIP, TOR2_TEPIP));
        verifications.checkForRemoteMcastMacInTorTowardsTeps(tor2NodeIid,
                false, Arrays.asList(TOR1_TEPIP));
        verifications.checkForRemoteUcastMacs(tor1NodeIid,
                Arrays.asList(DPN1MAC1, DPN1MAC2, DPN2MAC1, DPN2MAC2), false);
        verifications.checkForRemoteUcastMacs(tor2NodeIid,
                Arrays.asList(TOR1MAC1), false);
        verifications.verifyDmacFlowOfTORInDpns(Arrays.asList(DPN1_ID, DPN2_ID), TOR1NODEID, TOR1MAC1, false);
    }
}
