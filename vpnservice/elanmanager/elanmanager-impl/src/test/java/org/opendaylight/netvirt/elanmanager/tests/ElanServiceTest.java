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

import javax.inject.Inject;

import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
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
import org.opendaylight.netvirt.elan.rpc.ScaleInRpcManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.tests.utils.EvpnTestHelper;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesStartInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesStartInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIdsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;


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
    private @Inject ScaleInRpcManager scaleInRpcManager;

    @Before public void before() throws TransactionCommitFailedException {
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

    @Test public void nodeBuilderTest(){
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(new NodeId("tada"));
        OvsdbBridgeAugmentation xx = new OvsdbBridgeAugmentationBuilder().build();
        nodeBuilder.addAugmentation(OvsdbBridgeAugmentation.class,xx);
        tx.put(LogicalDatastoreType.CONFIGURATION,createInstanceIdentifier("tada"),
                nodeBuilder.build(),true);
        tx.submit();
        awaitForData(LogicalDatastoreType.CONFIGURATION, createInstanceIdentifier("tada"));
        LOG.error("await done");
        tx = this.dataBroker.newReadWriteTransaction();
        CheckedFuture<Optional<Node>, ReadFailedException> x =
                tx.read(LogicalDatastoreType.CONFIGURATION,createInstanceIdentifier("tada"));
        try {
            Node node = x.checkedGet().get();
            OvsdbBridgeAugmentation ya = node.getAugmentation(OvsdbBridgeAugmentation.class);
            if(ya!=null) {
                LOG.error("Present");
            }else{
                LOG.error("Not present");

            }
        } catch (ReadFailedException e) {
            e.printStackTrace();
            LOG.error("Read failed");
        }
        LOG.error("Successfgully node is created now time to write tomstned flag and read it back");
        checkRPC("tada");
    }

    public void checkRPC(String nodeId) {
        ScaleinComputesStartInput s =
                new ScaleinComputesStartInputBuilder().setScaleinNodeIds(Collections.singletonList(nodeId)).build();
        scaleInRpcManager.scaleinComputesStart(s);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        InstanceIdentifier<BridgeExternalIds> bridgeExternalIdsInstanceIdentifier = createInstanceIdentifier(nodeId)
                .augmentation(OvsdbBridgeAugmentation.class)
                .child(BridgeExternalIds.class, new BridgeExternalIdsKey("TOMBSTONED"));
        Optional<BridgeExternalIds> x =MDSALUtil.read(dataBroker, CONFIGURATION,bridgeExternalIdsInstanceIdentifier);
        LOG.error("something {}",x.get().getBridgeExternalIdKey());
        LOG.error("something {}",x.get().getBridgeExternalIdValue());
    }

    public static InstanceIdentifier<Node> createInstanceIdentifier(String nodeIdString) {
        NodeId nodeId = new NodeId(new Uri(nodeIdString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("ovsdb:1")));
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

}
