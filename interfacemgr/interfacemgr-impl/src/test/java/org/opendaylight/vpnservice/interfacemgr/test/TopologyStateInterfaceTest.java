/**
 * Created by eranjsu on 28-Dec-15.
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
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceTopologyStateAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceTopologyStateRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceTopologyStateUpdateHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceBfdStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceBfdStatusBuilder;
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
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class TopologyStateInterfaceTest {

    BigInteger dpId = BigInteger.valueOf(1);
    InstanceIdentifier<OvsdbBridgeAugmentation>bridgeIid = null;
    InstanceIdentifier<BridgeEntry> bridgeEntryIid = null;
    OvsdbBridgeAugmentation bridgeNew;
    OvsdbBridgeAugmentation bridgeOld;
    BridgeEntry bridgeEntry = null;
    ParentRefs parentRefs = null;
    InstanceIdentifier<Interface> interfaceInstanceIdentifier = null;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier = null;
    Interface tunnelInterfaceEnabled = null;
    BridgeInterfaceEntry bridgeInterfaceEntry;
    BridgeInterfaceEntryKey bridgeInterfaceEntryKey;

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock OvsdbTerminationPointAugmentation newTerminationPoint;

    OvsInterfaceTopologyStateAddHelper addHelper;
    OvsInterfaceTopologyStateRemoveHelper removeHelper;
    OvsInterfaceTopologyStateUpdateHelper updateHelper;

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

    private void setupMocks()
    {
        Node node = new NodeBuilder().setKey(null).setNodeId(null).build();
        tunnelInterfaceEnabled = InterfaceManagerTestUtil.buildTunnelInterface(dpId, InterfaceManagerTestUtil.tunnelInterfaceName, "Test Interface1", true, TunnelTypeGre.class
                , "192.168.56.101", "192.168.56.102");
        bridgeIid = InterfaceManagerTestUtil.getOvsdbAugmentationInstanceIdentifier(InterfaceManagerTestUtil.interfaceName, node);
        bridgeNew = InterfaceManagerTestUtil.getOvsdbBridgeRef("s1");
        bridgeOld = InterfaceManagerTestUtil.getOvsdbBridgeRef("s1");
        bridgeEntryIid = InterfaceMetaUtils.getBridgeEntryIdentifier(new BridgeEntryKey(dpId));
        interfaceInstanceIdentifier = IfmUtil.buildId(InterfaceManagerTestUtil.tunnelInterfaceName);
        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(newTerminationPoint.getName());
        bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(InterfaceManagerTestUtil.tunnelInterfaceName);
        bridgeInterfaceEntry =
                new BridgeInterfaceEntryBuilder().setKey(bridgeInterfaceEntryKey)
                        .setInterfaceName(tunnelInterfaceEnabled.getName()).build();
        bridgeEntry = InterfaceManagerTestUtil.buildBridgeEntry(dpId, bridgeInterfaceEntry);

        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testAddTopologyStateInterface()
    {
        Optional<OvsdbBridgeAugmentation>expectedOvsdbBridgeAugmentation = Optional.of(bridgeNew);
        Optional<BridgeEntry> expectedBridgeEntry = Optional.of(bridgeEntry);
        Optional<Interface> expectedInterface = Optional.of(tunnelInterfaceEnabled);

        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedBridgeEntry)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION,bridgeEntryIid);

        OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentation = new OvsdbBridgeAugmentationBuilder(bridgeNew);
        ovsdbBridgeAugmentation.setDatapathId(DatapathId.getDefaultInstance("00:00:00:00:00:00:00:01"));
        ovsdbBridgeAugmentation.setBridgeName(OvsdbBridgeName.getDefaultInstance("a"));
        bridgeNew = ovsdbBridgeAugmentation.build();

        addHelper.addPortToBridge(bridgeIid,bridgeNew,dataBroker);

        BigInteger ovsdbDpId = BigInteger.valueOf(1);
        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(ovsdbDpId);
        InstanceIdentifier<BridgeRefEntry> bridgeEntryId =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(bridgeRefEntryKey);
        BridgeRefEntryBuilder tunnelDpnBridgeEntryBuilder =
                new BridgeRefEntryBuilder().setKey(bridgeRefEntryKey).setDpid(ovsdbDpId)
                        .setBridgeReference(new OvsdbBridgeRef(bridgeIid));

        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, bridgeEntryId, tunnelDpnBridgeEntryBuilder.build(), true);
    }

    @Test
    public void testDeleteTopologyStateInterface()
    {
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, bridgeIid);
        OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentation = new OvsdbBridgeAugmentationBuilder(bridgeOld);
        ovsdbBridgeAugmentation.setDatapathId(DatapathId.getDefaultInstance("00:00:00:00:00:00:00:01"));
        ovsdbBridgeAugmentation.setBridgeName(OvsdbBridgeName.getDefaultInstance("b"));
        bridgeOld = ovsdbBridgeAugmentation.build();

        removeHelper.removePortFromBridge(bridgeIid,bridgeOld,dataBroker);

        BigInteger ovsdbDpId = BigInteger.valueOf(1);
        BridgeRefEntryKey bridgeRefEntryKey = new BridgeRefEntryKey(ovsdbDpId);
        InstanceIdentifier<BridgeRefEntry> bridgeEntryId =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(bridgeRefEntryKey);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL,bridgeEntryId);
    }

    @Test
    public void testUpdateBridgeReferenceEntry()
    {
        Optional<OvsdbBridgeAugmentation>expectedOvsdbBridgeAugmentation = Optional.of(bridgeNew);
        Optional<BridgeEntry> expectedBridgeEntry = Optional.of(bridgeEntry);
        Optional<Interface> expectedInterface = Optional.of(tunnelInterfaceEnabled);

        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedBridgeEntry)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION,bridgeEntryIid);
        doReturn(Futures.immediateCheckedFuture(expectedOvsdbBridgeAugmentation)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, bridgeIid);

        OvsdbBridgeAugmentationBuilder ovsdbBridgeAugmentation = new OvsdbBridgeAugmentationBuilder(bridgeNew);
        ovsdbBridgeAugmentation.setDatapathId(DatapathId.getDefaultInstance("00:00:00:00:00:00:00:01"));
        ovsdbBridgeAugmentation.setBridgeName(OvsdbBridgeName.getDefaultInstance("a"));
        bridgeNew = ovsdbBridgeAugmentation.build();

        updateHelper.updateBridgeRefEntry(bridgeIid, bridgeNew, bridgeOld, dataBroker);
    }

    @Test
    public void testUpdateTunnelState(){

        List<InterfaceBfdStatus> interfaceBfdStatus = new ArrayList<InterfaceBfdStatus>();
        interfaceBfdStatus.add(new InterfaceBfdStatusBuilder().setBfdStatusKey(SouthboundUtils.BFD_OP_STATE).setBfdStatusValue(SouthboundUtils.BFD_STATE_UP).build());
        List bfdStatusSpy = spy(interfaceBfdStatus);
        when(newTerminationPoint.getInterfaceBfdStatus()).thenReturn(bfdStatusSpy);

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState = new InterfaceBuilder().setKey(new InterfaceKey(InterfaceManagerTestUtil.interfaceName)).build();
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedInterface = Optional.of(ifState);

        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        updateHelper.updateTunnelState(dataBroker, newTerminationPoint, null);
        
        //verify
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                IfmUtil.buildStateInterfaceId(null);
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setOperStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(null));
        verify(mockWriteTx).merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());

    }
}
