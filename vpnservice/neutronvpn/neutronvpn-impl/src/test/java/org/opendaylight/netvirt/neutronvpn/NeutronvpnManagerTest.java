/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;


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
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnManager;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnNatManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets
        .VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4Family;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance
        .Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3ExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.RoutesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks
        .NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMapsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602
        .GetFixedIPsForNeutronPortInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.createl3vpn.input.L3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.createl3vpn.input.L3vpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data
        .PortFixedipToPortName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data
        .PortFixedipToPortNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.port.data
        .PortFixedipToPortNameKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NeutronvpnManagerTest {

    Uuid vpnId = Uuid.getDefaultInstance("54947df8-0e9e-4471-a2f9-9af509fb5889");
    Uuid subnetId = Uuid.getDefaultInstance("55947df8-0e9e-4471-a2f9-9af509fb5889");
    Uuid tenantId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5889");
    Uuid routerId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5885");
    Uuid networkId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5884");
    Uuid portId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5884");
    String name = "abc";
    String routeDisting = "100:1";
    String vpnName = "VPN";
    String subnetIp = "10.1.1.24";
    Long elanTag = Long.valueOf(10);
    Long macTimeOut = Long.valueOf(100);
    List<String> rd = new ArrayList<>();
    List<String> routeDistinguisher = new ArrayList<>();
    List<Uuid> networks = new CopyOnWriteArrayList<>();//
    List<Uuid> subnets = new ArrayList<>();
    List<Uuid> vpns = new ArrayList<>();
    List<Port> portList = new ArrayList<>();
    List<Port> portListNew = new ArrayList<>();
    List<FixedIps> fixedIps = new ArrayList<>();
    List<VpnTarget> vpnTargetList = new ArrayList<>();
    List<L3vpn> l3vpnList = new ArrayList<>();
    List<VpnMap> vpnMapList = new ArrayList<>();
    List<Routes> routesList = new ArrayList<>();
    NetworkL3Extension networkL3Extension = null;
    VpnInstance vpnInstance = null;
    LockInput lockInput = null;
    UnlockInput unlockInput = null;
    VpnMap vpnMap = null;
    VpnMapBuilder vpnMapBuilder = new VpnMapBuilder();
    Ports ports = null;
    Ports portsNew = null;
    Network network = null;
    NetworkMap networkMap = null;
    ElanInstance elanInstance = null;
    Subnetmap subnetmap = null;
    Port portTest = null;
    Router routerTest = null;
    FixedIps fixedIpsTest = null;
    IpAddress ipAddressTest = null;
    Ipv4Family ipv4FamilyTest = null;
    VpnTargets vpnTargets = null;
    VpnTarget vpnTargetTest = null;
    AssociateNetworksInput associateNetworksInput = null;
    AssociateRouterInput associateRouterInput = null;
    DissociateNetworksInput dissociateNetworksInput = null;
    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks
            extNetworks = null;
    DissociateRouterInput dissociateRouterInput = null;
    L3vpn l3vpnTest = null;
    GetL3VPNInput getL3VPNInput = null;
    GetFixedIPsForNeutronPortInput getFixedIPsForNeutronPortInput = null;
    DeleteL3VPNInput deleteL3VPNInput = null;
    CreateL3VPNInput createL3VPNInput = null;
    VpnMaps vpnMapsTest = null;
    VpnInterface vpnInterface = null;
    Routes routesTest = null;
    IpPrefix ipPrefixTest = null;
    PortFixedipToPortName portFixedipToPortName = null;
    SubnetmapBuilder subnetmapBuilder = new SubnetmapBuilder();
    PortBuilder portBuilder = new PortBuilder();
    L3vpnBuilder l3vpnBuilder = new L3vpnBuilder();

    InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class).
            child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue())).build();
    InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
            .child(VpnMap.class, new VpnMapKey(vpnId)).build();
    InstanceIdentifier<Ports> instPorts = InstanceIdentifier.create(Neutron.class).child(Ports.class);
    InstanceIdentifier<Network> instNetwork = InstanceIdentifier.create(Neutron.class).child(Networks.class).child
            (Network.class, new NetworkKey(networkId));
    InstanceIdentifier<NetworkMap> instNetworkMap = InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap
            .class, new NetworkMapKey(networkId)).build();
    InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
            .child(ElanInstance.class, new ElanInstanceKey(networkId.getValue())).build();
    InstanceIdentifier<Subnetmap> instSubnetMap = InstanceIdentifier.builder(Subnetmaps.class).
            child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
    InstanceIdentifier<Port> instPort = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
            new PortKey(portId));
    InstanceIdentifier<Router> instRouter = InstanceIdentifier.create(Neutron.class).child(Routers.class).child
            (Router.class, new RouterKey(routerId));
    InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
    InstanceIdentifier<VpnInterface> instVpnInterface = InstanceIdentifier.builder(VpnInterfaces.class).child
            (VpnInterface.class, new VpnInterfaceKey(networkId.getValue())).build();
    InstanceIdentifier<PortFixedipToPortName> instPortFixedipToPortName = InstanceIdentifier.builder(NeutronPortData
            .class).child(PortFixedipToPortName.class, new PortFixedipToPortNameKey(subnetIp)).build();
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
            .networks.Networks> instNetworks = InstanceIdentifier.builder(ExternalNetworks.class).
            child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks
                    .Networks.class, new NetworksKey(networkId)).build();

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;
    @Mock IMdsalApiManager iMdsalApiManager;
    @Mock NeutronvpnNatManager neutronvpnNatManager;
    @Mock NotificationPublishService notificationPublishService;
    @Mock NotificationService notificationService;
    @Mock LockManagerService lockManagerService;

    NeutronvpnManager neutronvpnManager;

    Optional<VpnInstance> optionalVpn;
    Optional<VpnMap> optionalVpnMap;
    Optional<Ports> optionalPorts;
    Optional<Network> optionalNetwork;
    Optional<NetworkMap> optionalNetworkMap;
    Optional<ElanInstance> optionalElanInstance;
    Optional<Subnetmap> optionalSubnetMap;
    Optional<Port> optionalPort;
    Optional<Router> optionalRouter;
    Optional<VpnMaps> optionalVpnMaps;
    Optional<VpnInterface> optionalVpnInterface;
    Optional<PortFixedipToPortName> optionalPortFixedipToPortName;
    Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks
            .Networks> optionalNetworks;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(AsyncDataBroker.DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        setupMocks();

        optionalVpn = Optional.of(vpnInstance);
        optionalVpnMap = Optional.of(vpnMap);
        optionalPorts = Optional.of(ports);
        optionalNetwork = Optional.of(network);
        optionalNetworkMap = Optional.of(networkMap);
        optionalElanInstance = Optional.of(elanInstance);
        optionalSubnetMap = Optional.of(subnetmap);
        optionalPort = Optional.of(portTest);
        optionalRouter = Optional.of(routerTest);
        optionalVpnMaps = Optional.absent();
        optionalVpnInterface = Optional.of(vpnInterface);
        optionalPortFixedipToPortName = Optional.of(portFixedipToPortName);
        optionalNetworks = Optional.of(extNetworks);

        doReturn(Futures.immediateCheckedFuture(optionalVpn)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnIdentifier);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);
        doReturn(Futures.immediateCheckedFuture(optionalPorts)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPorts);
        doReturn(Futures.immediateCheckedFuture(optionalNetwork)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetwork);
        doReturn(Futures.immediateCheckedFuture(optionalNetworkMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetworkMap);
        doReturn(Futures.immediateCheckedFuture(optionalElanInstance)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, elanIdentifierId);
        doReturn(Futures.immediateCheckedFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instSubnetMap);
        doReturn(Futures.immediateCheckedFuture(optionalPort)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPort);
        doReturn(Futures.immediateCheckedFuture(optionalRouter)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instRouter);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMaps)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapsIdentifier);
        doReturn(Futures.immediateCheckedFuture(optionalVpnInterface)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instVpnInterface);
        doReturn(Futures.immediateCheckedFuture(optionalPortFixedipToPortName)).when(mockReadTx).read
                (LogicalDatastoreType.CONFIGURATION, instPortFixedipToPortName);
        doReturn(Futures.immediateCheckedFuture(optionalNetworks)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetworks);

        neutronvpnManager = new NeutronvpnManager(dataBroker, iMdsalApiManager,
                notificationPublishService, notificationService, neutronvpnNatManager);
        neutronvpnManager.setLockManager(lockManagerService);

    }

    @After
    public void cleanUp() {
    }

    private void setupMocks() {

        rd.add(routeDisting);
        networks.add(networkId);
        subnets.add(subnetId);
        vpns.add(vpnId);
        routeDistinguisher.add(routeDisting);
        l3vpnBuilder.setId(vpnId).setName(vpnName).setTenantId(tenantId).setRouteDistinguisher(routeDistinguisher)
                .setExportRT(rd).setImportRT(rd);
        ipAddressTest = IpAddressBuilder.getDefaultInstance(subnetIp);
        ipPrefixTest = IpPrefixBuilder.getDefaultInstance(subnetIp + "/24");
        fixedIpsTest = new FixedIpsBuilder().setSubnetId(subnetId).setIpAddress(ipAddressTest).
                setKey(new FixedIpsKey(ipAddressTest, subnetId)).build();
        fixedIps.add(fixedIpsTest);
        vpnTargetTest = new VpnTargetBuilder().setVrfRTValue(routeDisting).setKey(new VpnTargetKey(routeDisting))
                .setVrfRTType(VpnTarget.VrfRTType.Both).build();
        vpnTargetList.add(vpnTargetTest);
        vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();
        ipv4FamilyTest = new Ipv4FamilyBuilder().setVpnTargets(vpnTargets).setRouteDistinguisher(routeDisting).build();
        vpnInstance = new VpnInstanceBuilder().setDescription("Create VPN Instance").setVpnInstanceName(vpnId
                .getValue()).setIpv4Family(ipv4FamilyTest).setKey(new VpnInstanceKey(vpnId.getValue())).build();
        lockInput = new LockInputBuilder().setLockName("true").build();
        vpnMapBuilder.setVpnId(vpnId).setName(name).setTenantId(tenantId).setKey(new VpnMapKey(vpnId));
        vpnMap = vpnMapBuilder.build();
        portBuilder.setTenantId(tenantId).setName(name).setNetworkId(networkId).setUuid(portId).setKey(new PortKey
                (portId)).setAdminStateUp(true).setFixedIps(fixedIps);
        portTest = portBuilder.build();
        portList.add(portTest);
        portListNew.add(portBuilder.setDeviceOwner("network:router_interface").setDeviceId(routerId.getValue()).build());
        unlockInput = new UnlockInputBuilder().setLockName("true").build();
        networkL3Extension = new NetworkL3ExtensionBuilder().setExternal(true).build();
        network = new NetworkBuilder().setAdminStateUp(true).setName(name).setTenantId(tenantId).setUuid(networkId)
                .setKey(new NetworkKey(networkId))
                .addAugmentation(NetworkL3Extension.class, networkL3Extension).build();
        ports = new PortsBuilder().setPort(portListNew).build();
        portsNew = new PortsBuilder().setPort(portListNew).build();
        networkMap = new NetworkMapBuilder().setNetworkId(networkId).setKey(new NetworkMapKey(networkId))
                .setSubnetIdList(subnets).build();
        elanInstance = new ElanInstanceBuilder().setElanInstanceName(networkId.getValue()).setKey(new ElanInstanceKey
                (networkId.getValue())).setElanTag(elanTag).setMacTimeout(macTimeOut).build();
        subnetmapBuilder.setId(subnetId).setNetworkId(networkId).setTenantId(tenantId).setKey
                (new SubnetmapKey(subnetId)).setPortList(networks).setSubnetIp(subnetIp);
        subnetmap = subnetmapBuilder.build();
        routerTest = new RouterBuilder().setAdminStateUp(true).setGatewayPortId(portId).setName(name).setTenantId
                (tenantId).setUuid(routerId).setDistributed(true)
                .setKey(new RouterKey(routerId)).setRoutes(routesList).build();
        associateNetworksInput = new AssociateNetworksInputBuilder().setVpnId(vpnId).setNetworkId(networks).build();
        dissociateNetworksInput = new DissociateNetworksInputBuilder().setNetworkId(networks).setVpnId(vpnId).build();
        associateRouterInput = new AssociateRouterInputBuilder().setVpnId(vpnId).setRouterId(routerId).build();
        dissociateRouterInput = new DissociateRouterInputBuilder().setRouterId(routerId).setVpnId(vpnId).build();
        extNetworks = new NetworksBuilder().setId(networkId).setVpnid(vpnId).setKey(new NetworksKey(networkId)).build();
        getL3VPNInput = new GetL3VPNInputBuilder().setId(vpnId).build();
        getFixedIPsForNeutronPortInput = new GetFixedIPsForNeutronPortInputBuilder().setPortId(portId).build();
        deleteL3VPNInput = new DeleteL3VPNInputBuilder().setId(vpns).build();
        vpnMapList.add(vpnMap);
        routesTest = new RoutesBuilder().setNexthop(ipAddressTest).setDestination(ipPrefixTest).build();
        routesList.add(routesTest);
        vpnMapsTest = new VpnMapsBuilder().setVpnMap(vpnMapList).build();
        vpnInterface = new VpnInterfaceBuilder().setName(networkId.getValue()).setVpnInstanceName(vpnId.getValue())
                .setKey(new VpnInterfaceKey(networkId.getValue())).build();
        portFixedipToPortName = new PortFixedipToPortNameBuilder().setPortName(networkId.getValue()).setPortFixedip
                (subnetIp).setKey(new PortFixedipToPortNameKey(subnetIp)).build();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();

        when(lockManagerService.lock(any(LockInput.class))).thenReturn(Futures.immediateFuture(RpcResultBuilder
                .<Void>success().build()));
        when(lockManagerService.unlock(any(UnlockInput.class))).thenReturn(Futures.immediateFuture(RpcResultBuilder
                .<Void>success().build()));


    }

    @Test
    public void testCreateL3Vpn() {

        vpnMap = vpnMapBuilder.setRouterId(routerId).build();
        subnetmap = subnetmapBuilder.setVpnId(vpnId).build();
        l3vpnTest = l3vpnBuilder.setRouterId(routerId).setNetworkIds(networks).build();
        l3vpnList.add(l3vpnTest);
        createL3VPNInput = new CreateL3VPNInputBuilder().setL3vpn(l3vpnList).build();

        optionalSubnetMap = Optional.of(subnetmap);
        optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instSubnetMap);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.createL3VPN(createL3VPNInput);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnIdentifier, vpnInstance, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instSubnetMap, subnetmap, true);

    }

    @Test
    public void testCreateL3VpnWithoutRouter() {

        vpnMap = vpnMapBuilder.setNetworkIds(networks).setRouterId(routerId).build();
        subnetmap = subnetmapBuilder.setVpnId(vpnId).build();
        l3vpnTest = l3vpnBuilder.setNetworkIds(networks).build();
        l3vpnList.add(l3vpnTest);
        createL3VPNInput = new CreateL3VPNInputBuilder().setL3vpn(l3vpnList).build();

        optionalSubnetMap = Optional.of(subnetmap);
        optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instSubnetMap);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.createL3VPN(createL3VPNInput);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnIdentifier, vpnInstance, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMap, true);

    }

    @Test
    public void testCreateL3VpnWithoutNetwork() {

        vpnMap = vpnMapBuilder.setRouterId(routerId).build();
        subnetmap = subnetmapBuilder.setVpnId(vpnId).build();
        l3vpnTest = l3vpnBuilder.setRouterId(routerId).build();
        l3vpnList.add(l3vpnTest);
        createL3VPNInput = new CreateL3VPNInputBuilder().setL3vpn(l3vpnList).build();

        optionalSubnetMap = Optional.of(subnetmap);
        optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instSubnetMap);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.createL3VPN(createL3VPNInput);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnIdentifier, vpnInstance, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMap, true);


    }

    @Test
    public void testAssociateNetworks() {

        vpnMap = vpnMapBuilder.setNetworkIds(networks).build();
        subnetmap = subnetmapBuilder.setVpnId(vpnId).build();
        optionalSubnetMap = Optional.of(subnetmap);
        optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalSubnetMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instSubnetMap);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.associateNetworks(associateNetworksInput);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMap, true);

    }

    @Test
    public void testAssociateRouter() {

        Optional<Ports> optionalPorts = Optional.of(portsNew);

        doReturn(Futures.immediateCheckedFuture(optionalPorts)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPorts);

        neutronvpnManager.associateRouter(associateRouterInput);

        verify(mockWriteTx).merge(LogicalDatastoreType.CONFIGURATION, instVpnInterface, vpnInterface, true);
        verify(mockReadTx).read(LogicalDatastoreType.CONFIGURATION, instRouter);

    }

    @Test
    public void testDissociateNetworks() {

        vpnMap = vpnMapBuilder.setNetworkIds(subnets).build();

        Optional<VpnMap> optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.dissociateNetworks(dissociateNetworksInput);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMap, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instSubnetMap, subnetmap, true);

    }

    @Test
    public void testDissociateRouter() {

        vpnMap = vpnMapBuilder.setNetworkIds(networks).build();
        vpnMapList.add(vpnMap);
        vpnMapsTest = new VpnMapsBuilder().setVpnMap(vpnMapList).build();
        optionalVpnMap = Optional.of(vpnMap);
        optionalVpnMaps = Optional.of(vpnMapsTest);

        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMaps)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapsIdentifier);

        neutronvpnManager.dissociateRouter(dissociateRouterInput);

        verify(mockReadTx).read(LogicalDatastoreType.CONFIGURATION,instRouter);

    }

    @Test
    public void testGetL3VpnInput() {

        neutronvpnManager.getL3VPN(getL3VPNInput);

        verify(mockReadTx).read(LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
        verify(mockReadTx).read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);

    }

    @Test
    public void testGetFixedIPsForNeutronPort() {

        neutronvpnManager.getFixedIPsForNeutronPort(getFixedIPsForNeutronPortInput);

        verify(mockReadTx).read(LogicalDatastoreType.CONFIGURATION, instPort);

    }

    @Test
    public void testDeleteL3Vpn() {

        vpnMap = vpnMapBuilder.setRouterId(vpnId).setNetworkIds(subnets).build();
        Optional<Ports> optionalPorts = Optional.of(portsNew);
        Optional<VpnMap> optionalVpnMap = Optional.of(vpnMap);

        doReturn(Futures.immediateCheckedFuture(optionalPorts)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPorts);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMap)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, vpnMapIdentifier);

        neutronvpnManager.deleteL3VPN(deleteL3VPNInput);

        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, vpnIdentifier);

    }

}
