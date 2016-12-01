/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netvirt.elan.utils.ElanTestData.ELAN222VM1MAC;
import static org.opendaylight.netvirt.elan.utils.ElanTestData.elan222;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

import io.netty.util.concurrent.Future;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.internal.ElanBridgeManager;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * JUnit test suite for ElanUtils.
 */
@RunWith(MockitoJUnitRunner.class)
public class ElanUtilsTest {

    @Mock
    RpcProviderRegistry rpcRegistry;
    @Mock
    OdlInterfaceRpcService ifaceMgrRpcService;
    @Mock
    ItmRpcService itmRpcService;
    @Mock
    IdManagerService idManager;
    @Mock
    IMdsalApiManager mdsalMgr;
    @Mock
    DataBroker dataBroker;
    @Mock
    ReadOnlyTransaction readTx;
    @Mock
    WriteTransaction writeTx;
    @Mock
    IInterfaceManager interfaceManager;
    @Mock
    ElanInstanceManager elanInstanceManager;
    @Mock
    ElanBridgeManager bridgeMgr;
    @Mock
    ElanInterfaceManager elanInterfaceManager;
    @Mock
    ElanStatusMonitor elanStatusMonitor;
    // @Mock ElanUtils elanUtils;
    @Mock
    EntityOwnershipService entityOwnershipService;
    ElanServiceProvider elanServiceProvider;
    ElanUtils elanUtils;

    @Before
    public void setUp() throws Exception {
        elanServiceProvider = new ElanServiceProvider(idManager, interfaceManager, elanInstanceManager, bridgeMgr,
                dataBroker, elanInterfaceManager, elanStatusMonitor, elanUtils, entityOwnershipService);

        when(dataBroker.newReadOnlyTransaction()).thenReturn(readTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTx);
        elanUtils = new ElanUtils(dataBroker, mdsalMgr, elanInstanceManager, ifaceMgrRpcService, itmRpcService,
                elanInterfaceManager, entityOwnershipService, bridgeMgr);
        ElanTestData.setElanUtils(elanUtils);
        // Default Stubbing
        // Whenever IfaceMgr is asked about the egressActions for "dpn1.1"
        stubSuccessfulGetEgressActions(ElanTestTopo.DPN11IFACENAME);
        stubSuccessfulGetEgressActions(ElanTestTopo.DPN12IFACENAME);
        stubSuccessfulGetEgressActions(ElanTestTopo.DPN21IFACENAME);
        stubSuccessfulGetEgressActions(ElanTestTopo.DPN22IFACENAME);

        // Whenever ITM is asked about the ifaceName between DPN2 and DPN1
        GetTunnelInterfaceNameOutput output2 = new GetTunnelInterfaceNameOutputBuilder()
                .setInterfaceName(ElanTestTopo.DPN2TODPN1TRUNKIFACENAME).build();
        Future future4 = mock(Future.class);
        when(future4.get()).thenReturn(RpcResultBuilder.success(output2).build());
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;

        GetTunnelInterfaceNameInput input2 = new GetTunnelInterfaceNameInputBuilder()
                .setDestinationDpid(ElanTestTopo.DPN1ID).setSourceDpid(ElanTestTopo.DPN2ID).setTunnelType(tunType)
                .build();
        when(itmRpcService.getTunnelInterfaceName(eq(input2))).thenReturn(future4);

        // Whenever ITM is asked about the ifaceName between DPN1 and DPN2
        GetTunnelInterfaceNameOutput output3 = new GetTunnelInterfaceNameOutputBuilder()
                .setInterfaceName(ElanTestTopo.DPN1TODPN2TRUNKIFACENAME).build();
        Future future5 = mock(Future.class);
        when(future5.get()).thenReturn(RpcResultBuilder.success(output3).build());
        GetTunnelInterfaceNameInput input3 = new GetTunnelInterfaceNameInputBuilder()
                .setDestinationDpid(ElanTestTopo.DPN2ID).setSourceDpid(ElanTestTopo.DPN1ID).setTunnelType(tunType)
                .build();
        when(itmRpcService.getTunnelInterfaceName(eq(input3))).thenReturn(future5);

        // Whenever the idManager is asked for an elanTag
        AllocateIdOutput getIdOutput = new AllocateIdOutputBuilder().setIdValue(elan222.getElanTag()).build();
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(ElanConstants.ELAN_ID_POOL_NAME)
                .setIdKey(elan222.getElanInstanceName()).build();
        Future future = mock(Future.class);
        when(future.get()).thenReturn(RpcResultBuilder.success(getIdOutput).build());
        when(idManager.allocateId(eq(getIdInput))).thenReturn(future);
    }

    /////////////////////
    // Basic stubbing //
    /////////////////////
    public void stubSuccessfulGetEgressActions(String localIfName) throws InterruptedException, ExecutionException {
        List<Action> localIfEgressActions = ElanTestTopo.LOCALIFACTIONMAP.get(localIfName);
        GetEgressActionsForInterfaceOutput ifEgressActionsOutput = new GetEgressActionsForInterfaceOutputBuilder()
                .setAction(localIfEgressActions).build();
        Future getEgressActionsFuture = mock(Future.class);
        when(getEgressActionsFuture.get()).thenReturn(RpcResultBuilder.success(ifEgressActionsOutput).build());
        GetEgressActionsForInterfaceInput input = new GetEgressActionsForInterfaceInputBuilder()
                .setIntfName(ElanTestTopo.DPN11IFACENAME).build();
        when(ifaceMgrRpcService.getEgressActionsForInterface(eq(input))).thenReturn(getEgressActionsFuture);
    }

    private void stubGetInternalTunnelEgressActions(String trunkIfName, String localIfName, boolean success)
            throws InterruptedException, ExecutionException {
        List<Action> egressActions = ElanTestTopo.getTrunkEgressActions(trunkIfName, localIfName);
        GetEgressActionsForInterfaceOutput output = new GetEgressActionsForInterfaceOutputBuilder()
                .setAction(egressActions).build();
        Future future = mock(Future.class);
        if (success) {
            when(future.get()).thenReturn(RpcResultBuilder.success(output).build());
        } else {
            when(future.get()).thenReturn(RpcResultBuilder.failed().build());
        }
        int lportTag = ElanTestTopo.INTERFACEINFOMAP.get(localIfName).getInterfaceTag();
        GetEgressActionsForInterfaceInput input = new GetEgressActionsForInterfaceInputBuilder()
                .setIntfName(trunkIfName).setTunnelKey(Long.valueOf(lportTag)).build();
        when(ifaceMgrRpcService.getEgressActionsForInterface(eq(input))).thenReturn(future);
    }

    private void stubIsDpnPresent(NodeId nodeId, boolean isPresent) throws InterruptedException, ExecutionException {
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        if (isPresent) {
            when(chkdFuture.get()).thenReturn(Optional.of(new NodeBuilder().build()));
        } else {
            when(chkdFuture.get()).thenReturn(Optional.absent());
        }
        InstanceIdentifier<Node> nodeIId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).build();
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), eq(nodeIId))).thenReturn(chkdFuture);
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

    @Test
    public void testSetupMacFlows() throws Exception {

        //////////////////////////////
        // Stubbing ////////////////
        //////////////////////////////
        // Whenever broker is asked to read the ElanDpnInterfacesList
        CheckedFuture chkdFuture2 = mock(CheckedFuture.class);
        CheckedFuture chkdFuture3 = mock(CheckedFuture.class);
        when(chkdFuture2.get()).thenReturn(Optional.of(elan222.getElanDpnInterfacesList()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isIIdType(ElanDpnInterfacesList.class))))
                .thenReturn(chkdFuture2);

        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                eq(ElanUtils.getElanInterfaceConfigurationDataPathId(ElanTestTopo.DPN11INFO.getInterfaceName()))))
                        .thenReturn(chkdFuture3);
        when(chkdFuture3.get()).thenReturn(Optional.of(new ElanInterfaceBuilder().build()));
        // Whenever ifaceMgr is asked about the egressActions for Tunnel between
        // DPN2 and DPN1 ("INT_TUNNEL21") with
        // lportTag of dpn11 as tunnelKey
        stubGetInternalTunnelEgressActions(ElanTestTopo.DPN2TODPN1TRUNKIFACENAME, ElanTestTopo.DPN11IFACENAME, // to
                // retrieve
                // the
                // lportTag
                /* success */true);

        // Whenever broker is asked about if a DPN is present
        CheckedFuture chkdFuture4 = mock(CheckedFuture.class);
        when(chkdFuture4.get()).thenReturn(Optional.of(new NodeBuilder().build()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isIIdType(Node.class)))).thenReturn(chkdFuture4);

        //////////////////////////////
        // Method under test /////////
        //////////////////////////////
        // Testing that a new ElanInterface has been created on the interface
        ////////////////////////////// "dpn11"
        elanUtils.setupMacFlows(elan222.getElanInstance(), ElanTestTopo.DPN11INFO, elan222.getMacTimeout(),
                ELAN222VM1MAC.getValue(), writeTx);

        //////////////////////////////
        // Verification //////////////
        //////////////////////////////
        FlowEntity expectedSmacFlowEntity = elan222.getSmacFlowEntity(ElanTestTopo.DPN11IFACENAME);
        verify(mdsalMgr).addFlowToTx(argThat(new FlowEntityMatcher(expectedSmacFlowEntity)),
                any(WriteTransaction.class));

        // Checking that the local and remote DMAC flows has been installed
        ArgumentCaptor<Flow> argumentCaptor = ArgumentCaptor.forClass(Flow.class);
        verify(mdsalMgr, times(1)).addFlowToTx(any(BigInteger.class), argumentCaptor.capture(),
                any(WriteTransaction.class));

        // argumentCaptor should have exactly 1 values because
        // mdsalMgr.installFlow is called 1 times for the same DpId,
        // for DMAC
        List<Flow> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 1);

        Flow expectedLocalDmacFlow = elan222.getLocalDmacFlow(ElanTestTopo.DPN11IFACENAME);
        assert (new FlowMatcher(expectedLocalDmacFlow).matches(installedFlowsCaptured.get(0)));

    }

    @Test
    public void testSetupMacFlowsDpnNotPresent() throws Exception {

        //////////////////////////////
        // Stubbing ////////////////
        //////////////////////////////
        // Whenever broker is asked to read the ElanDpnInterfacesList
        CheckedFuture chkdFuture2 = mock(CheckedFuture.class);
        when(chkdFuture2.get()).thenReturn(Optional.of(elan222.getElanDpnInterfacesList()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isIIdType(ElanDpnInterfacesList.class))))
                .thenReturn(chkdFuture2);

        CheckedFuture chkdFuture3 = mock(CheckedFuture.class);
        String elanInterfaceName = ElanTestTopo.DPN11IFACE.getName();
        when(chkdFuture3.get()).thenReturn(Optional.of(elan222.getElanInterface(elanInterfaceName)));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                eq(ElanUtils.getElanInterfaceConfigurationDataPathId(elanInterfaceName)))).thenReturn(chkdFuture3);

        // Whenever broker is asked about if DPN1 is present
        stubIsDpnPresent(ElanTestTopo.DPN1NODEID, true /* is Present */);

        // Whenever broker is asked about if DPN2 is present, return
        // Optional.absent()
        stubIsDpnPresent(ElanTestTopo.DPN2NODEID, false /* is Not Present */);

        //////////////////////////////
        // Method under test /////////
        //////////////////////////////
        // Testing that a new ElanInterface has been created on the interface
        ////////////////////////////// "dpn11"
        elanUtils.setupMacFlows(elan222.getElanInstance(), ElanTestTopo.DPN11INFO, elan222.getMacTimeout(),
                ELAN222VM1MAC.getValue(), writeTx);

        //////////////////////////////
        // Verification //////////////
        //////////////////////////////
        FlowEntity expectedSmacFlowEntity = elan222.getSmacFlowEntity(ElanTestTopo.DPN11IFACENAME);
        verify(mdsalMgr).addFlowToTx(argThat(new FlowEntityMatcher(expectedSmacFlowEntity)),
                any(WriteTransaction.class));

        // Checking that the Tunnel Termination flow and DMAC flows (local and
        // remote) has been installed
        ArgumentCaptor<Flow> argumentCaptor = ArgumentCaptor.forClass(Flow.class);
        verify(mdsalMgr, times(1)).addFlowToTx(any(BigInteger.class), argumentCaptor.capture(),
                any(WriteTransaction.class));

        // argumentCaptor should have exactly 2 values because
        // mdsalMgr.installFlow is called 1 times for the same DpId,
        // for DMAC
        List<Flow> installedFlowsCaptured = argumentCaptor.getAllValues();
        assert (installedFlowsCaptured.size() == 1);

        Flow expectedLocalDmacFlow = elan222.getLocalDmacFlow(ElanTestTopo.DPN11IFACENAME);
        assert (new FlowMatcher(expectedLocalDmacFlow).matches(installedFlowsCaptured.get(0)));

        // Checking that the remote MAC is NOT installed in DPN2
        Mockito.verifyNoMoreInteractions(mdsalMgr);
    }

    @Test
    public void testGetElanInstanceByName() throws Exception {
        //////////////////////////////
        // Stubbing ////////////////
        //////////////////////////////
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.get()).thenReturn(Optional.of(elan222.getElanInstance()));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION), eq(elan222.getElanInstanceConfigurationDataPath())))
                .thenReturn(chkdFuture);

        //////////////////////////////
        // Method under test /////////
        //////////////////////////////
        ElanInstance returnedElanInstance = ElanUtils.getElanInstanceByName(dataBroker, elan222.elanName);

        //////////////////////////////
        // Verification //////////////
        //////////////////////////////
        verify(readTx, times(1)).read(eq(LogicalDatastoreType.CONFIGURATION),
                eq(elan222.getElanInstanceConfigurationDataPath()));
        assert (returnedElanInstance != null && returnedElanInstance.getElanInstanceName() != null
                && returnedElanInstance.getElanInstanceName().equals(elan222.elanName));
    }

    @Test
    public void testUpdateElanOperationalDS() throws Exception {
        //////////////////////////////
        // Stubbing ////////////////
        //////////////////////////////

        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(writeTx.submit()).thenReturn(chkdFuture);

        //////////////////////////////
        // Method under test /////////
        //////////////////////////////
        ElanUtils.updateOperationalDataStore(dataBroker, idManager, elan222.getElanInstance(), new ArrayList<String>(),
                writeTx);

        //////////////////////////////
        // Verification //////////////
        //////////////////////////////

        // Verifies that there have been 3 creations in the Elan's Operational
        // DS:a
        // one for elan-state, one for elan-mac-table and one for elan-tag-name.
        // Since elan222 does not have vni, the vni-per-elan-map is not updated.
        ArgumentCaptor<InstanceIdentifier> argumentCaptor = ArgumentCaptor.forClass(InstanceIdentifier.class);
        verify(writeTx, times(2)).put(any(LogicalDatastoreType.class), argumentCaptor.capture(), any(DataObject.class),
                any(Boolean.class));

        // Verifies that ElanInstance in Configuration DS is updated with the
        // elan
        verify(writeTx, times(1)).merge(eq(LogicalDatastoreType.CONFIGURATION),
                eq(elan222.getElanInstanceConfigurationDataPath()), any(ElanInstance.class), any(Boolean.class));
        // TODO: verify the elanTag too
    }
}
