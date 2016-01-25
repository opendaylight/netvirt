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
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigUpdateHelper;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TunnelInterfaceConfigurationTest {
    BigInteger dpId = BigInteger.valueOf(1);
    NodeConnectorId nodeConnectorId = null;
    NodeConnector nodeConnector = null;
    Interface tunnelInterfaceEnabled = null;
    Interface tunnelInterfaceDisabled = null;
    ParentRefs parentRefs = null;
    InstanceIdentifier<Interface> interfaceInstanceIdentifier = null;
    InstanceIdentifier<NodeConnector> nodeConnectorInstanceIdentifier = null;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier = null;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateInterface;

    org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey BridgeRefEntryKey;
    InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid = null;
    BridgeEntryKey bridgeEntryKey = null;
    BridgeEntry bridgeEntry = null;
    InstanceIdentifier<BridgeEntry> bridgeEntryIid = null;
    InstanceIdentifier<BridgeInterfaceEntry> bridgeInterfaceEntryInstanceIdentifier;
    BridgeInterfaceEntry bridgeInterfaceEntry;
    BridgeInterfaceEntryKey bridgeInterfaceEntryKey;
    BridgeRefEntry bridgeRefEntry = null;
    OvsdbBridgeAugmentation ovsdbBridgeAugmentation;
    InstanceIdentifier<OvsdbBridgeAugmentation> ovsdbBridgeAugmentationInstanceIdentifier;
    InstanceIdentifier<TerminationPoint> terminationPointInstanceIdentifier;
    TerminationPoint terminationPoint;

    @Mock DataBroker dataBroker;
    @Mock IdManagerService idManager;
    @Mock AlivenessMonitorService alivenessMonitorService;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock
    IMdsalApiManager mdsalApiManager;
    OvsInterfaceConfigAddHelper addHelper;
    OvsInterfaceConfigRemoveHelper removeHelper;
    OvsInterfaceConfigUpdateHelper updateHelper;

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
        nodeConnector = InterfaceManagerTestUtil.buildNodeConnector(nodeConnectorId);
        tunnelInterfaceEnabled = InterfaceManagerTestUtil.buildTunnelInterface(dpId, InterfaceManagerTestUtil.tunnelInterfaceName, "Test Interface1", true, TunnelTypeGre.class
        , "192.168.56.101", "192.168.56.102");
        tunnelInterfaceDisabled = InterfaceManagerTestUtil.buildTunnelInterface(dpId, InterfaceManagerTestUtil.tunnelInterfaceName, "Test Interface1", false, TunnelTypeGre.class
                , "192.168.56.101", "192.168.56.102");
        interfaceInstanceIdentifier = IfmUtil.buildId(InterfaceManagerTestUtil.tunnelInterfaceName);
        nodeConnectorInstanceIdentifier = InterfaceManagerTestUtil.getNcIdent("openflow:1", nodeConnectorId);
        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(tunnelInterfaceEnabled.getName());
        stateInterface = InterfaceManagerTestUtil.buildStateInterface(InterfaceManagerTestUtil.tunnelInterfaceName, nodeConnectorId);
        parentRefs = tunnelInterfaceEnabled.getAugmentation(ParentRefs.class);
        BridgeRefEntryKey = new BridgeRefEntryKey(parentRefs.getDatapathNodeIdentifier());
        dpnBridgeEntryIid = InterfaceMetaUtils.getBridgeRefEntryIdentifier(BridgeRefEntryKey);
        bridgeEntryKey = new BridgeEntryKey(parentRefs.getDatapathNodeIdentifier());

        bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(InterfaceManagerTestUtil.tunnelInterfaceName);
        bridgeInterfaceEntryInstanceIdentifier = InterfaceMetaUtils.getBridgeInterfaceEntryIdentifier(bridgeEntryKey,
                bridgeInterfaceEntryKey);
        bridgeInterfaceEntry =
                new BridgeInterfaceEntryBuilder().setKey(bridgeInterfaceEntryKey)
                        .setInterfaceName(tunnelInterfaceEnabled.getName()).build();

        bridgeEntryIid = InterfaceMetaUtils.getBridgeEntryIdentifier(bridgeEntryKey);
        bridgeEntry = InterfaceManagerTestUtil.buildBridgeEntry(dpId, bridgeInterfaceEntry);

        Node node = new NodeBuilder().setKey(null).setNodeId(null).build();
        ovsdbBridgeAugmentationInstanceIdentifier = InterfaceManagerTestUtil.getOvsdbAugmentationInstanceIdentifier(
                InterfaceManagerTestUtil.tunnelInterfaceName, node);
        ovsdbBridgeAugmentation = InterfaceManagerTestUtil.getOvsdbBridgeRef("s1");
        bridgeRefEntry = new BridgeRefEntryBuilder().setKey(BridgeRefEntryKey).setDpid(dpId).
                setBridgeReference(new OvsdbBridgeRef(ovsdbBridgeAugmentationInstanceIdentifier)).build();

        terminationPointInstanceIdentifier = InterfaceManagerTestUtil.getTerminationPointId(ovsdbBridgeAugmentationInstanceIdentifier,
                InterfaceManagerTestUtil.tunnelInterfaceName);
        terminationPoint = InterfaceManagerTestUtil.getTerminationPoint(ovsdbBridgeAugmentationInstanceIdentifier, ovsdbBridgeAugmentation,
                InterfaceManagerTestUtil.tunnelInterfaceName, 0, InterfaceTypeGre.class, tunnelInterfaceEnabled);
        // Setup mocks
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testAddGreInterfaceWhenSwitchIsNotConnected() {
        Optional<Interface> expectedInterface = Optional.of(tunnelInterfaceEnabled);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateInterface = Optional.of(stateInterface);

        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                        LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
               LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid);

        addHelper.addConfiguration(dataBroker, parentRefs, tunnelInterfaceEnabled, idManager);

        //Add some verifications
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, bridgeInterfaceEntryInstanceIdentifier, bridgeInterfaceEntry, true);
    }

    @Test
    public void testAddGreInterfaceWhenSwitchIsConnected() {
        Optional<BridgeRefEntry> expectedBridgeRefEntry = Optional.of(bridgeRefEntry);
        Optional<OvsdbBridgeAugmentation> expectedOvsdbBridgeAugmentation = Optional.of(ovsdbBridgeAugmentation);

        doReturn(Futures.immediateCheckedFuture(expectedBridgeRefEntry)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid);
        doReturn(Futures.immediateCheckedFuture(expectedOvsdbBridgeAugmentation)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, ovsdbBridgeAugmentationInstanceIdentifier);

        addHelper.addConfiguration(dataBroker, parentRefs, tunnelInterfaceEnabled, idManager);

        //Add some verifications
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, bridgeInterfaceEntryInstanceIdentifier ,
                bridgeInterfaceEntry, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, terminationPointInstanceIdentifier ,
                terminationPoint, true);
    }


    @Test
    public void testDeleteGreInterfaceWhenSwitchIsConnected() {
        Optional<BridgeRefEntry> expectedBridgeRefEntry = Optional.of(bridgeRefEntry);
        Optional<BridgeEntry> expectedBridgeEntry = Optional.of(bridgeEntry);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedInterfaceState = Optional.of(stateInterface);
        doReturn(Futures.immediateCheckedFuture(expectedBridgeRefEntry)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, dpnBridgeEntryIid);
        doReturn(Futures.immediateCheckedFuture(expectedBridgeEntry)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, bridgeEntryIid);
        doReturn(Futures.immediateCheckedFuture(expectedInterfaceState)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        ifaceBuilder.setOperStatus(OperStatus.Down);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(tunnelInterfaceEnabled.getName()));
        stateInterface = ifaceBuilder.build();

        removeHelper.removeConfiguration(dataBroker, alivenessMonitorService, tunnelInterfaceEnabled, idManager,
                mdsalApiManager, parentRefs);

        //Add some verifications
        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, bridgeEntryIid);
        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, terminationPointInstanceIdentifier);
    }


    @Test
    public void testUpdateAdminStateForGreInterface() {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface>
                expectedStateInterface = Optional.of(stateInterface);
        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);

        updateHelper.updateConfiguration(dataBroker, alivenessMonitorService, idManager, mdsalApiManager,
                tunnelInterfaceDisabled,tunnelInterfaceEnabled);

        //verify whether operational data store is updated with the new oper state.
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        ifaceBuilder.setOperStatus(OperStatus.Down);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(stateInterface.getName()));

        verify(mockWriteTx).merge(LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier,
                ifaceBuilder.build());
    }
    @Test
    public void testEnableAdminStateForGreInterface() {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface>
                expectedStateInterface = Optional.of(stateInterface);
        Optional<NodeConnector>expectedNodeConnector = Optional.of(nodeConnector);

        doReturn(Futures.immediateCheckedFuture(expectedNodeConnector)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, nodeConnectorInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);

        updateHelper.updateConfiguration(dataBroker, alivenessMonitorService, idManager, mdsalApiManager,
                tunnelInterfaceEnabled,tunnelInterfaceDisabled);

        //verify whether operational data store is updated with the new oper state.
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        ifaceBuilder.setOperStatus(OperStatus.Down);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(stateInterface.getName()));

        verify(mockWriteTx).merge(LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier,
                ifaceBuilder.build());
    }
}
