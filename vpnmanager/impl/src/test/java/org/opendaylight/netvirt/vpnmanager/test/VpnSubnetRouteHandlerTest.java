/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.SubnetOpDpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.VpnNodeListener;
import org.opendaylight.netvirt.vpnmanager.VpnOpDataSyncer;
import org.opendaylight.netvirt.vpnmanager.VpnSubnetRouteHandler;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.TaskState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint64;

@RunWith(MockitoJUnitRunner.class)
public class VpnSubnetRouteHandlerTest {

    Uint64 dpId = Uint64.valueOf(1);
    SubnetToDpn subnetToDpn = null;
    String subnetIp = "10.1.1.24";
    List<String> routeDistinguishers = Arrays.asList("100:1","100:2");
    String primaryRd = "100:1";
    String nexthopIp = null;
    String poolName = null;
    String interfaceName = "VPN";
    Uuid subnetId = Uuid.getDefaultInstance("067e6162-3b6f-4ae2-a171-2470b63dff00");
    Uuid portId = Uuid.getDefaultInstance("54947df8-0e9e-4471-a2f9-9af509fb5889");
    Uuid tenantId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5889");
    String portKey = portId.getValue();
    Long elanTag = null;
    Long longId = null;
    PortOpDataEntry portOp = null;
    PortOpData portOpData = null;
    SubnetOpDataEntry subnetOp = null;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
        stateInterface;
    List<String> lowerLayerIfList = new ArrayList<>();
    NodeConnectorId nodeConnectorId = null;
    VpnInterfaces vpnIntfaces = null;
    VpnInstance vpnInstance = null;
    Subnetmap subnetmap = null;
    DPNTEPsInfo dpntePsInfo = null;
    TunnelEndPoints tunlEndPts = null;
    IpAddress ipAddress = null;
    String idKey = null;
    AllocateIdOutput allocateIdOutput = null;
    AllocateIdInput allocateIdInput = null;
    Networks networks = null;
    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204
            .vpn.instances.VpnInstance vpnInstnce;

    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .state.Interface> ifStateId = InterfaceUtils.buildStateInterfaceId(portKey);
    InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
        InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
            new SubnetOpDataEntryKey(subnetId)).build();
    InstanceIdentifier<SubnetToDpn> dpnOpId = subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpId));
    InstanceIdentifier<DPNTEPsInfo> tunnelInfoId =
        InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpId)).build();
    InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
            new PortOpDataEntryKey(portKey)).build();
    InstanceIdentifier<PortOpDataEntry> instPortOp =
        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
            new PortOpDataEntryKey(interfaceName)).build();
    InstanceIdentifier<Subnetmap> subMapid = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new
        SubnetmapKey(subnetId)).build();
    InstanceIdentifier<PortOpData> portOpIdentifr = InstanceIdentifier.builder(PortOpData.class).build();
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
        .VpnInstance> instVpnInstance = getVpnInstanceToVpnIdIdentifier(interfaceName);
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
        .VpnInstance> vpnInstanceIdentifier = InstanceIdentifier.builder(VpnInstances.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
                    .VpnInstance.class,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
                        .VpnInstanceKey(interfaceName)).build();
    InstanceIdentifier<Networks> netsIdentifier =
        InstanceIdentifier.builder(ExternalNetworks.class).child(Networks.class, new NetworksKey(portId)).build();

    @Mock
    DataBroker dataBroker;
    @Mock
    ReadTransaction mockReadTx;
    @Mock
    WriteTransaction mockWriteTx;
    @Mock
    IBgpManager bgpManager;
    @Mock
    VpnInterfaceManager vpnInterfaceManager;
    @Mock
    IdManagerService idManager;
    @Mock
    LockManagerService lockManager;
    @Mock
    SubnetOpDpnManager subnetOpDpnManager;
    @Mock
    LockManagerService lockManagerService;
    @Mock
    VpnOpDataSyncer vpnOpDataSyncer;
    @Mock
    VpnNodeListener vpnNodeListener;
    @Mock
    IFibManager fibManager;
    @Mock
    INeutronVpnManager neutronVpnService;
    @Mock
    IMdsalApiManager mdsalManager;
    @Mock
    JobCoordinator jobCoordinator;
    @Mock
    IInterfaceManager interfaceManager;
    @Mock
    OdlInterfaceRpcService ifmRpcService;

    VpnUtil vpnUtil;

    VpnSubnetRouteHandler vpnSubnetRouteHandler;

    Optional<Interface> optionalIfState;
    Optional<SubnetOpDataEntry> optionalSubs;
    Optional<SubnetToDpn> optionalSubDpn;
    Optional<DPNTEPsInfo> optionalTunnelInfo;
    Optional<PortOpDataEntry> optionalPortOp;
    Optional<PortOpData> optionalPtOp;
    Optional<Subnetmap> optionalSubnetMap;
    Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
        optionalVpnInstnce;
    Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance>
        vpnInstanceOptional;
    Optional<Networks> optionalNetworks;



    @Before
    public void setUp() throws Exception {
        setupMocks();

        vpnUtil = new VpnUtil(dataBroker, idManager, fibManager, bgpManager, lockManager, neutronVpnService,
                mdsalManager, jobCoordinator, interfaceManager, ifmRpcService);
        vpnSubnetRouteHandler = new VpnSubnetRouteHandler(dataBroker, subnetOpDpnManager, bgpManager, vpnOpDataSyncer,
                vpnNodeListener, fibManager, vpnUtil);
        final Future<RpcResult<AllocateIdOutput>> idOutputOptional =
            RpcResultBuilder.success(allocateIdOutput).buildFuture();

        optionalIfState = Optional.of(stateInterface);
        optionalSubs = Optional.of(subnetOp);
        optionalSubDpn = Optional.of(subnetToDpn);
        optionalTunnelInfo = Optional.of(dpntePsInfo);
        optionalPortOp = Optional.of(portOp);
        optionalPtOp = Optional.of(portOpData);
        optionalSubnetMap = Optional.of(subnetmap);
        optionalVpnInstnce = Optional.of(vpnInstance);
        vpnInstanceOptional = Optional.of(vpnInstnce);
        optionalNetworks = Optional.of(networks);

        doReturn(FluentFutures.immediateFluentFuture(optionalIfState)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, ifStateId);
        doReturn(FluentFutures.immediateFluentFuture(optionalSubs)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, subOpIdentifier);
        doReturn(FluentFutures.immediateFluentFuture(optionalSubDpn)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, dpnOpId);
        doReturn(FluentFutures.immediateFluentFuture(optionalTunnelInfo)).when(mockReadTx).read(LogicalDatastoreType
            .CONFIGURATION, tunnelInfoId);
        doReturn(FluentFutures.immediateFluentFuture(optionalPortOp)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifier);
        doReturn(FluentFutures.immediateFluentFuture(optionalPtOp)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifr);
        doReturn(FluentFutures.immediateFluentFuture(optionalPortOp)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, instPortOp);
        doReturn(FluentFutures.immediateFluentFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
            .CONFIGURATION, subMapid);
        doReturn(FluentFutures.immediateFluentFuture(optionalVpnInstnce)).when(mockReadTx).read(LogicalDatastoreType
            .CONFIGURATION, instVpnInstance);
        doReturn(FluentFutures.immediateFluentFuture(vpnInstanceOptional)).when(mockReadTx).read(LogicalDatastoreType
            .CONFIGURATION, vpnInstanceIdentifier);
        doReturn(FluentFutures.immediateFluentFuture(Optional.empty())).when(mockReadTx).read(LogicalDatastoreType
            .CONFIGURATION, netsIdentifier);
        doReturn(idOutputOptional).when(idManager).allocateId(allocateIdInput);

        when(subnetOpDpnManager.getPortOpDataEntry(anyString())).thenReturn(portOp);
    }

    private void setupMocks() {

        nexthopIp = "10.1.1.25";
        idKey = "100:1.10.1.1.24";
        poolName = "vpnservices";
        elanTag = 2L;
        longId = Long.valueOf("100");
        nodeConnectorId = buildNodeConnectorId(dpId, 2L);
        ipAddress = IpAddressBuilder.getDefaultInstance(nexthopIp);
        vpnIntfaces = new VpnInterfacesBuilder().setInterfaceName(interfaceName).withKey(
            new VpnInterfacesKey(interfaceName)).build();
        List<VpnInterfaces> vpnInterfaces = new ArrayList<>();
        final List<SubnetToDpn> subToDpn = new ArrayList<>();
        final List<Uuid> portList = new ArrayList<>();
        final List<PortOpDataEntry> listPortOpDataEntry = new ArrayList<>();
        final List<TunnelEndPoints> tunnelEndPoints = new ArrayList<>();
        List<Uuid> subnetIdList = new ArrayList<>();
        subnetIdList.add(subnetId);
        vpnInterfaces.add(vpnIntfaces);
        lowerLayerIfList.add(nodeConnectorId.getValue());
        portOp = new PortOpDataEntryBuilder().setDpnId(dpId).withKey(new PortOpDataEntryKey(tenantId.getValue()))
            .setSubnetIds(subnetIdList).setPortId(tenantId.getValue()).build();
        subnetToDpn = new SubnetToDpnBuilder().setDpnId(dpId).withKey(new SubnetToDpnKey(dpId)).setVpnInterfaces(
            vpnInterfaces).build();
        allocateIdOutput = new AllocateIdOutputBuilder().setIdValue(longId).build();
        allocateIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        subToDpn.add(subnetToDpn);
        portList.add(portId);
        listPortOpDataEntry.add(portOp);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.InterfaceBuilder();
        ifaceBuilder.setLowerLayerIf(lowerLayerIfList).setType(L2vlan.class)
            .setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.state.Interface.AdminStatus.Up).setOperStatus(Interface.OperStatus.Up)
            .setIfIndex(100).withKey(new InterfaceKey(interfaceName)).setName(interfaceName)
            .setPhysAddress(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                .PhysAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"));
        stateInterface = ifaceBuilder.build();
        subnetOp = new SubnetOpDataEntryBuilder().setElanTag(elanTag).setNhDpnId(dpId).setSubnetCidr(subnetIp)
            .setSubnetId(subnetId).withKey(new SubnetOpDataEntryKey(subnetId)).setVpnName(interfaceName)
            .setVrfId(primaryRd).setSubnetToDpn(subToDpn).setRouteAdvState(TaskState.Advertised).build();
        vpnInstance = new VpnInstanceBuilder().setVpnId(elanTag).setVpnInstanceName(interfaceName)
            .setVrfId(interfaceName).withKey(new VpnInstanceKey(interfaceName)).build();
        subnetmap = new SubnetmapBuilder().setSubnetIp(subnetIp).setId(subnetId).setNetworkId(portId).withKey(new
            SubnetmapKey(subnetId)).setRouterId(portId).setVpnId(subnetId)
            .setTenantId(tenantId).setPortList(portList).build();
        portOpData = new PortOpDataBuilder().setPortOpDataEntry(listPortOpDataEntry).build();
        dpntePsInfo = new DPNTEPsInfoBuilder().setDPNID(dpId).setUp(true).withKey(new DPNTEPsInfoKey(dpId))
            .setTunnelEndPoints(tunnelEndPoints).build();
        tunlEndPts =
            new TunnelEndPointsBuilder().setInterfaceName(interfaceName).setIpAddress(ipAddress).build();
        tunnelEndPoints.add(tunlEndPts);
        vpnInstnce = new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
            .VpnInstanceBuilder().withKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn
                .rev200204.vpn.instances.VpnInstanceKey(interfaceName)).setVpnInstanceName(interfaceName)
            .setRouteDistinguisher(routeDistinguishers).build();
        networks = new NetworksBuilder().setId(portId).withKey(new NetworksKey(portId)).build();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(FluentFutures.immediateNullFluentFuture()).when(mockWriteTx).commit();
    }

    @Ignore
    @Test
    public void testOnPortAddedToSubnet() {

        vpnSubnetRouteHandler.onPortAddedToSubnet(subnetmap, portId);

        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, portOpIdentifier, portOp);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, dpnOpId, subnetToDpn);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subnetOp);
    }

    @Ignore
    @Test
    public void testOnPortRemovedFromSubnet() {

        vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmap, portId);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, dpnOpId, subnetToDpn);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subnetOp);

    }

    @Ignore
    @Test
    public void testOnInterfaceUp() {

        vpnSubnetRouteHandler.onInterfaceUp(dpId, interfaceName, subnetId);

        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, instPortOp, portOp);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, dpnOpId, subnetToDpn);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subnetOp);
    }

    @Ignore
    @Test
    public void testOnInterfaceDown() {

        vpnSubnetRouteHandler.onInterfaceDown(dpId, interfaceName, subnetId);

        // TODO: subnetOpDpnManager is mocked so not sure how this delete ever worked.
        //verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, dpnOpId);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subnetOp);

    }

    @Ignore
    @Test
    public void testOnSubnetAddedToVpn() {

        doReturn(Futures.immediateFuture(Optional.empty())).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, subOpIdentifier);

        vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, true, elanTag);

        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, dpnOpId, subnetToDpn);
        verify(mockWriteTx).mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, portOpIdentifier, portOp);


    }

    @Ignore
    @Test
    public void testOnSubnetUpdatedInVpn() {

        vpnSubnetRouteHandler.onSubnetUpdatedInVpn(subnetmap, elanTag);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, subOpIdentifier);

    }

    @Ignore
    @Test
    public void testOnSubnetDeletedFromVpn() {

        vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmap, true);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, subOpIdentifier);

    }

    public static NodeConnectorId buildNodeConnectorId(Uint64 dpn, long portNo) {
        return new NodeConnectorId(buildNodeConnectorString(dpn, portNo));
    }

    public static String buildNodeConnectorString(Uint64 dpn, long portNo) {
        return new StringBuilder().append(IfmConstants.OF_URI_PREFIX).append(dpn)
            .append(IfmConstants.OF_URI_SEPARATOR).append(portNo).toString();
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to
        .vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class).child(org.opendaylight.yang.gen.v1.urn
                .opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
            new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstanceKey(vpnName)).build();
    }

}
