/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.test;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateUpdateHelper;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.port.rev130925.flow.capable.port.StateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.mockito.Mockito.*;



@RunWith(MockitoJUnitRunner.class)
public class StateInterfaceTest {
    BigInteger dpId = BigInteger.valueOf(1);
    NodeConnectorId nodeConnectorId = null;
    NodeConnector nodeConnector = null;
    FlowCapableNodeConnector fcNodeConnectorNew = null;
    Interface vlanInterfaceEnabled = null;
    Interface vlanInterfaceDisabled = null;
    InterfaceParentEntryKey interfaceParentEntryKey = null;
    IfIndexInterface IfindexInterface = null;
    InstanceIdentifier<Interface> interfaceInstanceIdentifier = null;
    InstanceIdentifier<FlowCapableNodeConnector> fcNodeConnectorId = null;
    InstanceIdentifier<IfIndexInterface> ifIndexId =null;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier = null;
    InstanceIdentifier<InterfaceParentEntry> interfaceParentEntryIdentifier = null;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateInterface;
    InterfaceParentEntry interfaceParentEntry;

    @Mock DataBroker dataBroker;
    @Mock IdManagerService idManager;
    @Mock IMdsalApiManager mdsalManager;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock AlivenessMonitorService alivenessMonitorService;

    OvsInterfaceStateAddHelper addHelper;
    OvsInterfaceStateRemoveHelper removeHelper;
    OvsInterfaceStateUpdateHelper updateHelper;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        setupMocks();
    }

    private void setupMocks() {
        nodeConnectorId = InterfaceManagerTestUtil.buildNodeConnectorId(BigInteger.valueOf(1), 2);
        nodeConnector = InterfaceManagerTestUtil.buildFlowCapableNodeConnector(nodeConnectorId);
        fcNodeConnectorNew = nodeConnector.getAugmentation(FlowCapableNodeConnector.class);
        fcNodeConnectorId = InterfaceManagerTestUtil.getFlowCapableNodeConnectorIdentifier("openflow:1", nodeConnectorId);
        IfindexInterface = InterfaceManagerTestUtil.buildIfIndexInterface(100, InterfaceManagerTestUtil.interfaceName);
        ifIndexId = InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class, new IfIndexInterfaceKey(100)).build();
        interfaceInstanceIdentifier = InterfaceManagerCommonUtils.getInterfaceIdentifier(new InterfaceKey(InterfaceManagerTestUtil.interfaceName));
        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(InterfaceManagerTestUtil.interfaceName);
        vlanInterfaceEnabled = InterfaceManagerTestUtil.buildInterface(InterfaceManagerTestUtil.interfaceName, "Test Vlan Interface1", true, L2vlan.class, BigInteger.valueOf(1));
        vlanInterfaceDisabled = InterfaceManagerTestUtil.buildInterface(InterfaceManagerTestUtil.interfaceName, "Test Vlan Interface1", false, L2vlan.class, BigInteger.valueOf(1));
        interfaceParentEntryKey = new InterfaceParentEntryKey(InterfaceManagerTestUtil.interfaceName);
        interfaceParentEntryIdentifier = InterfaceMetaUtils.getInterfaceParentEntryIdentifier(interfaceParentEntryKey);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        List<String> lowerLayerIfList = new ArrayList<>();
        lowerLayerIfList.add(nodeConnectorId.getValue());
        ifaceBuilder.setOperStatus(OperStatus.Up).setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up)
                .setPhysAddress(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"))
                .setIfIndex(100)
                .setLowerLayerIf(lowerLayerIfList);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(InterfaceManagerTestUtil.interfaceName));

        stateInterface = ifaceBuilder.build();

        InterfaceParentEntryBuilder ifaceParentEntryBuilder = new InterfaceParentEntryBuilder();
        List<InterfaceChildEntry> ifaceChildEntryList= new ArrayList<>();
        interfaceParentEntry = ifaceParentEntryBuilder.setInterfaceChildEntry(ifaceChildEntryList).build();

        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testAddStateInterface() {
        Optional<Interface> expectedInterface = Optional.of(vlanInterfaceEnabled);
        AllocateIdOutput expectedId = new AllocateIdOutputBuilder().setIdValue(Long.valueOf("100")).build();

        Future<RpcResult<AllocateIdOutput>> idOutputOptional = RpcResultBuilder.success(expectedId).buildFuture();

        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME)
                .setIdKey(InterfaceManagerTestUtil.interfaceName).build();
        doReturn(idOutputOptional).when(idManager).allocateId(getIdInput);

        addHelper.addState(dataBroker, idManager, mdsalManager, alivenessMonitorService,
                nodeConnectorId, InterfaceManagerTestUtil.interfaceName, fcNodeConnectorNew);

        //Add some verifications
        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier,
              stateInterface,true);
    }
    @Test
    public void testDeleteStateInterface() {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface>
                expectedStateInterface = Optional.of(stateInterface);
        Optional<IfIndexInterface> expectedIfindexInterface = Optional.of(IfindexInterface);
        doReturn(Futures.immediateCheckedFuture(expectedIfindexInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, ifIndexId);
        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);

        ReleaseIdInput getIdInput = new ReleaseIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME)
                .setIdKey(InterfaceManagerTestUtil.interfaceName).build();

        doReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build())).when(idManager).releaseId(getIdInput);

        removeHelper.removeState(idManager, mdsalManager, alivenessMonitorService, fcNodeConnectorId, dataBroker, InterfaceManagerTestUtil.interfaceName, fcNodeConnectorNew);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, ifIndexId);

    }
    @Test
     public void testUpdateStateInterface(){

        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface>
                expectedStateInterface = Optional.of(stateInterface);
        Optional<InterfaceParentEntry>expectedParentEntry = Optional.of(interfaceParentEntry);

        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedParentEntry)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceParentEntryIdentifier);

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        ifaceBuilder.setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up)
                .setPhysAddress(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"));
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(InterfaceManagerTestUtil.interfaceName));

        stateInterface = ifaceBuilder.build();

        FlowCapableNodeConnectorBuilder fcNodeConnectorOldupdate = new FlowCapableNodeConnectorBuilder().setHardwareAddress(MacAddress.getDefaultInstance("AA:AA:AA:AA:AA:AB"));
        FlowCapableNodeConnectorBuilder fcNodeConnectorNewupdate = new FlowCapableNodeConnectorBuilder().setHardwareAddress(MacAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"));

        StateBuilder b2 = new StateBuilder().setBlocked(true).setLinkDown(true);
        StateBuilder b3 = new StateBuilder().setBlocked(false).setLinkDown(true);

        fcNodeConnectorOldupdate.setState(b2.build());
        fcNodeConnectorNewupdate.setState(b3.build());

        updateHelper.updateState(fcNodeConnectorId, alivenessMonitorService, dataBroker, InterfaceManagerTestUtil.interfaceName, fcNodeConnectorNewupdate.build(), fcNodeConnectorOldupdate.build());

        verify(mockWriteTx).merge(LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier,stateInterface);
    }
}