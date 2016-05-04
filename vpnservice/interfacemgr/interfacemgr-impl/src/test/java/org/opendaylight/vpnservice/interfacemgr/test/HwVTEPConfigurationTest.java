/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.test;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
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
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers.HwVTEPConfigRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers.HwVTEPInterfaceConfigAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.statehelpers.HwVTEPInterfaceStateRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.statehelpers.HwVTEPInterfaceStateUpdateHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.TunnelInstanceInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.tunnel.instance._interface.map.TunnelInstanceInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.tunnel.instance._interface.map.TunnelInstanceInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.tunnel.instance._interface.map.TunnelInstanceInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HwVTEPConfigurationTest {

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock InstanceIdentifier<TunnelInstanceInterface> tunnelInterfaceId;
    @Mock TunnelInstanceInterface tunnelInterface;
    @Mock Tunnels newTunnel;
    @Mock Tunnels oldTunnel;
    HwVTEPInterfaceConfigAddHelper addHelper;
    HwVTEPConfigRemoveHelper removeHelper;
    HwVTEPInterfaceStateUpdateHelper updateHelper;
    HwVTEPInterfaceStateRemoveHelper stateRemoveHelper;

    BigInteger dpId = BigInteger.valueOf(1);
    Interface hwVTEPInterfaceEnabled;
    Interface hwVTEPInterfaceDisabled;
    ParentRefs parentRefs;
    IfTunnel ifTunnel;
    InstanceIdentifier<TerminationPoint> tpPath;
    TerminationPoint terminationPoint;
    InstanceIdentifier<Node> physicalSwitchId;
    InstanceIdentifier<Node> globalId;
    InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier;

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

    @After
    public void cleanUp(){
    }

    private void setupMocks() {
        hwVTEPInterfaceEnabled = InterfaceManagerTestUtil.buildTunnelInterface(dpId, InterfaceManagerTestUtil.tunnelInterfaceName ,"Test hwVTEP Interface1", true, TunnelTypeVxlan.class, "192.168.56.101", "192.168.56.102");
        hwVTEPInterfaceDisabled = InterfaceManagerTestUtil.buildTunnelInterface(dpId, InterfaceManagerTestUtil.tunnelInterfaceName ,"Test hwVTEP Interface1", false, TunnelTypeVxlan.class, "192.168.56.101", "192.168.56.102");
        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(hwVTEPInterfaceEnabled.getName());
        parentRefs = hwVTEPInterfaceEnabled.getAugmentation(ParentRefs.class);
        ifTunnel = hwVTEPInterfaceEnabled.getAugmentation(IfTunnel.class);
        physicalSwitchId = SouthboundUtils.createPhysicalSwitchInstanceIdentifier("s1");
        globalId = SouthboundUtils.createPhysicalSwitchInstanceIdentifier("s1");
        tunnelsInstanceIdentifier = org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils.
                createTunnelsInstanceIdentifier(physicalSwitchId,
                        ifTunnel.getTunnelSource(), ifTunnel.getTunnelDestination());
        tunnelInterfaceId = InstanceIdentifier.builder(TunnelInstanceInterfaceMap.class).
                child(TunnelInstanceInterface.class, new TunnelInstanceInterfaceKey(tunnelsInstanceIdentifier.toString())).build();
        tunnelInterface = new TunnelInstanceInterfaceBuilder().
                setTunnelInstanceIdentifier(tunnelsInstanceIdentifier.toString()).setKey(new TunnelInstanceInterfaceKey(tunnelsInstanceIdentifier.toString())).setInterfaceName(hwVTEPInterfaceEnabled.getName()).build();

        //Setup termination points
        TerminationPointKey tpKey = SouthboundUtils.getTerminationPointKey(ifTunnel.getTunnelDestination().getIpv4Address().getValue());
        tpPath = SouthboundUtils.createInstanceIdentifier(globalId, tpKey);
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        HwvtepPhysicalLocatorAugmentationBuilder tpAugmentationBuilder =
                new HwvtepPhysicalLocatorAugmentationBuilder();
        tpBuilder.setKey(tpKey);
        tpBuilder.setTpId(tpKey.getTpId());
        tpAugmentationBuilder.setEncapsulationType(EncapsulationTypeVxlanOverIpv4.class);
        SouthboundUtils.setDstIp(tpAugmentationBuilder, ifTunnel.getTunnelDestination());
        tpBuilder.addAugmentation(HwvtepPhysicalLocatorAugmentation.class, tpAugmentationBuilder.build());
        terminationPoint = tpBuilder.build();
        // Setup mocks
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testAddHwVTEPInterfaceWithGlobalNodeId() {
        addHelper.addConfiguration(dataBroker, physicalSwitchId, globalId,
                hwVTEPInterfaceEnabled, ifTunnel);

        //Verify
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, tpPath, terminationPoint, true);
        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, tunnelInterfaceId, tunnelInterface, true);
    }

    @Test
    public void testAddHwVTEPInterfaceWithoutGlobalNodeId() {

        addHelper.addConfiguration(dataBroker, physicalSwitchId ,null ,
                hwVTEPInterfaceEnabled, ifTunnel);

        //Verification already performed in testAddHwVTEPInterfaceWithGlobalNodeId()
    }

    @Test
    public void testAddHwVTEPInterfaceWhenAdminStateDisabled() {

        addHelper.addConfiguration(dataBroker, physicalSwitchId, globalId,
                hwVTEPInterfaceDisabled, ifTunnel);

        //Verification already performed in testAddHwVTEPInterfaceWithGlobalNodeId()
    }
    @Test
    public void testDeleteHWVTEPInterface() {
        removeHelper.removeConfiguration(dataBroker, hwVTEPInterfaceEnabled, physicalSwitchId, globalId);
        //verification
        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, tpPath);
        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, interfaceStateIdentifier);
        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, tunnelsInstanceIdentifier);
    }

    @Test
    public void testUpdatePhysicalSwitch(){

        Optional<TunnelInstanceInterface> tunnelInterfaceOptional = Optional.of(tunnelInterface);

        doReturn(Futures.immediateCheckedFuture(tunnelInterfaceOptional)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL, tunnelInterfaceId);
        List<BfdStatus> bfdStatus = new ArrayList<BfdStatus>();
        bfdStatus.add(new BfdStatusBuilder().setBfdStatusKey(SouthboundUtils.BFD_OP_STATE).setBfdStatusValue(SouthboundUtils.BFD_STATE_UP).build());
        List bfdStatusSpy = spy(bfdStatus);
        when(newTunnel.getBfdStatus()).thenReturn(bfdStatusSpy);
        updateHelper.updatePhysicalSwitch(dataBroker, tunnelsInstanceIdentifier, newTunnel, oldTunnel);

        //verify
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                IfmUtil.buildStateInterfaceId(hwVTEPInterfaceEnabled.getName());
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setOperStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up);
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(hwVTEPInterfaceEnabled.getName()));
        verify(mockWriteTx).merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());
    }

    @Test
    public void testStartBFDMonitoring(){

        updateHelper.startBfdMonitoring(dataBroker, tunnelsInstanceIdentifier, newTunnel);

        TunnelsBuilder tBuilder = new TunnelsBuilder();
        tBuilder.setKey(new TunnelsKey(newTunnel.getLocalLocatorRef(), newTunnel.getRemoteLocatorRef()));
        tBuilder.setLocalLocatorRef(newTunnel.getLocalLocatorRef());
        tBuilder.setRemoteLocatorRef(newTunnel.getLocalLocatorRef());
        List <BfdParams> bfdParams = new ArrayList<>();
        SouthboundUtils.fillBfdParameters(bfdParams, null);
        tBuilder.setBfdParams(bfdParams);
        //Verify
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier, tBuilder.build(), true);
    }

    @Test
    public void testRemoveExternalTunnels(){

        stateRemoveHelper.removeExternalTunnel(dataBroker, tunnelsInstanceIdentifier);

        //Verify
        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier);
    }
}
