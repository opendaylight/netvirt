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
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnNatManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
        .ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
        .ExternalGatewayInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
        .external_gateway_info.ExternalFixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
        .external_gateway_info.ExternalFixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router
        .external_gateway_info.ExternalFixedIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks
        .NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMapsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NeutronvpnNatManagerTest {

    Uuid vpnId = Uuid.getDefaultInstance("54947df8-0e9e-4471-a2f9-9af509fb5889");
    Uuid subnetId = Uuid.getDefaultInstance("55947df8-0e9e-4471-a2f9-9af509fb5889");
    Uuid routerId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5885");
    Uuid tenantId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5889");
    Uuid networkId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5884");
    Uuid portId = Uuid.getDefaultInstance("54947df8-0e9e-4571-a2f9-9af509fb5884");
    String name = "abc";
    String subnetIp = "10.1.1.24";
    Network networkTest = null;
    Networks networksTest = null;
    VpnMaps vpnMapsTest = null;
    VpnMap vpnMapTest = null;
    Routers routersTest = null;
    Router routerNew = null;
    Router routerOld = null;
    Port portTest = null;
    Ports ports = null;
    ExternalGatewayInfo externalGatewayInfo = null;
    ExternalFixedIps externalFixedIpsTest = null;
    IpAddress ipAddressTest = null;
    MacAddress macAddress = null;
    List<Uuid> routerList = new ArrayList<>();
    List<Uuid> networkList = new ArrayList<>();
    List<VpnMap> vpnMapList = new ArrayList<>();
    List<Uuid> subnetList = new ArrayList<>();
    List<Port> portList = new ArrayList<>();
    List<ExternalFixedIps> externalFixedIpsList = new ArrayList<>();
    List<String> externalIpList = new ArrayList<>();
    NetworksBuilder networksBuilder = new NetworksBuilder();

    InstanceIdentifier<Networks> instNetworks = InstanceIdentifier.builder(ExternalNetworks.class).
            child(Networks.class, new NetworksKey(networkId)).build();
    InstanceIdentifier<VpnMaps> instVpnMaps = InstanceIdentifier.builder(VpnMaps.class).build();
    InstanceIdentifier<Routers> instRouters = InstanceIdentifier.builder(ExtRouters.class).child(org.opendaylight
            .yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers.class, new RoutersKey
            (routerId.getValue())).build();
    InstanceIdentifier<Ports> instPorts = InstanceIdentifier.create(Neutron.class).child(Ports.class);
    InstanceIdentifier<Port> instPort = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
            new PortKey(portId));

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock IMdsalApiManager iMdsalApiManager;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;

    NeutronvpnNatManager neutronvpnNatManager;

    Optional<VpnMaps> optionalVpnMaps;
    Optional<Networks> optionalNets;
    Optional<Routers> optionalRouters;
    Optional<Ports> optionalPorts;
    Optional<Port> optionalPort;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(AsyncDataBroker.DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        setupMocks();

        neutronvpnNatManager = new NeutronvpnNatManager(dataBroker, iMdsalApiManager);

        optionalVpnMaps = Optional.of(vpnMapsTest);
        optionalNets = Optional.of(networksTest);
        optionalRouters = Optional.of(routersTest);
        optionalPorts = Optional.of(ports);
        optionalPort = Optional.of(portTest);

        doReturn(Futures.immediateCheckedFuture(optionalPort)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPort);
        doReturn(Futures.immediateCheckedFuture(optionalPorts)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instPorts);
        doReturn(Futures.immediateCheckedFuture(optionalRouters)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instRouters);
        doReturn(Futures.immediateCheckedFuture(optionalNets)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetworks);
        doReturn(Futures.immediateCheckedFuture(optionalVpnMaps)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instVpnMaps);
    }

    @After
    public void cleanUp() {
    }

    private void setupMocks() {

        networkList.add(networkId);
        externalIpList.add(subnetIp);
        ipAddressTest = IpAddressBuilder.getDefaultInstance(subnetIp);
        vpnMapTest = new VpnMapBuilder().setVpnId(vpnId).setRouterId(routerId).setTenantId(tenantId)
                .setNetworkIds(networkList).setName(name).setKey(new VpnMapKey(vpnId)).build();
        vpnMapList.add(vpnMapTest);
        networkTest = new NetworkBuilder().setAdminStateUp(true).setShared(true).setUuid(networkId).setTenantId
                (tenantId).setName(name).setKey(new NetworkKey(networkId)).build();
        networksBuilder.setId(networkId).setKey(new NetworksKey(networkId)).setRouterIds(routerList);
        networksTest = networksBuilder.build();
        vpnMapsTest = new VpnMapsBuilder().setVpnMap(vpnMapList).build();
        macAddress = MacAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA");
        portTest = new PortBuilder().setTenantId(tenantId).setName(name).setNetworkId(networkId).setUuid(portId)
                .setKey(new PortKey(portId)).setAdminStateUp(true)
                .setMacAddress(macAddress).setDeviceOwner("network:router_gateway").build();
        portList.add(portTest);
        ports = new PortsBuilder().setPort(portList).build();
        routersTest = new RoutersBuilder().setNetworkId(networkId).setEnableSnat(true).setRouterName(name)
                .setExtGwMacAddress("AA:AA:AA:AA:AA:AA")
                .setExternalIps(externalIpList).setSubnetIds(subnetList).build();
        externalFixedIpsTest = new ExternalFixedIpsBuilder().setSubnetId(subnetId).setKey(new ExternalFixedIpsKey
                (subnetId)).setIpAddress(ipAddressTest).build();
        externalFixedIpsList.add(externalFixedIpsTest);
        externalGatewayInfo = new ExternalGatewayInfoBuilder().setEnableSnat(true).setExternalNetworkId(networkId)
                .setExternalFixedIps(externalFixedIpsList).build();
        routerNew = new RouterBuilder().setAdminStateUp(true).setDistributed(true).setName(name).setGatewayPortId
                (portId).setTenantId(tenantId).setKey(new RouterKey(routerId))
                .setUuid(routerId).setExternalGatewayInfo(externalGatewayInfo).build();
        routerOld = new RouterBuilder().setAdminStateUp(false).setDistributed(true).setName(name).setGatewayPortId
                (portId).setTenantId(tenantId).setKey(new RouterKey(routerId)).setUuid(routerId).build();

        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();

    }

    @Test
    public void testAddExternalNetwork() {

        Optional<Networks> optionalNets = Optional.absent();

        doReturn(Futures.immediateCheckedFuture(optionalNets)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetworks);

        neutronvpnNatManager.addExternalNetwork(networkTest);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instNetworks, networksBuilder.setVpnid(vpnId)
                .build(), true);

    }

    @Test
    public void testRemoveExternalNetwork() {

        neutronvpnNatManager.removeExternalNetwork(networkTest);

        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, instNetworks);

    }

    @Test
    public void testAddExternalNetworkToVpn() {

        neutronvpnNatManager.addExternalNetworkToVpn(networkTest, vpnId);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instNetworks, networksBuilder.setVpnid(vpnId)
                .build(), true);

    }

    @Test
    public void testRemoveExternalNetworkFromVpn() {

        Optional<Networks> optionalNets = Optional.of(networksBuilder.build());

        doReturn(Futures.immediateCheckedFuture(optionalNets)).when(mockReadTx).read(LogicalDatastoreType
                .CONFIGURATION, instNetworks);

        neutronvpnNatManager.removeExternalNetworkFromVpn(networkTest);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instNetworks, networksBuilder.build(), true);

    }

    @Test
    public void testHandleSubnetsForExternalRouter() {

        neutronvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instRouters, routersTest, true);

    }

    @Test
    public void testHandleExternalNetworkForRouter() {

        neutronvpnNatManager.handleExternalNetworkForRouter(routerOld, routerNew);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instRouters, routersTest, true);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instNetworks, networksTest, true);

    }

    @Test
    public void testRemoveExternalNetworkFromRouter() {

        neutronvpnNatManager.removeExternalNetworkFromRouter(networkId, routerOld);

        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION, instRouters);
        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION, instNetworks, networksTest, true);

    }
}
