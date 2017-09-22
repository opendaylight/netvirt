/*
 * Copyright (c) 2016 Hewlett-Packard Development Company, L.P. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class NeutronPortChangeListenerTest {

    NeutronPortChangeListener neutronPortChangeListener;

    @Mock
    DataBroker dataBroker;
    @Mock
    NeutronvpnManager neutronvpnManager;
    @Mock
    NeutronvpnNatManager neutronvpnNatManager;
    @Mock
    NotificationPublishService notiPublishService;
    @Mock
    NotificationService notiService;
    @Mock
    NeutronFloatingToFixedIpMappingChangeListener floatingIpMapListener;
    @Mock
    WriteTransaction mockWriteTx;
    @Mock
    ReadOnlyTransaction mockReadTx;
    @Mock
    Network mockNetwork;
    @Mock
    ElanInstance elanInstance;
    @Mock
    NeutronSubnetGwMacResolver gwMacResolver;
    @Mock
    IElanService elanService;
    @Mock
    IInterfaceManager interfaceManager;

    @Before
    public void setUp() {
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        when(mockReadTx.read(any(LogicalDatastoreType.class), any(InstanceIdentifier.class)))
            .thenReturn(Futures.immediateCheckedFuture(Optional.of(mockNetwork)));
        neutronPortChangeListener = new NeutronPortChangeListener(dataBroker, neutronvpnManager, neutronvpnNatManager,
                gwMacResolver, elanService);
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class,
                        new ElanInstanceKey(new Uuid("12345678-1234-1234-1234-123456789012").getValue())).build();
        when(mockReadTx.read(any(LogicalDatastoreType.class), eq(elanIdentifierId)))
            .thenReturn(Futures.immediateCheckedFuture(Optional.of(elanInstance)));
    }

    @Test
    public void addPort__Ipv6FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv6 = new IpAddress(new Ipv6Address("1::1"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv6);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void addPort__Ipv4FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv4 = new IpAddress(new Ipv4Address("2.2.2.2"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv4);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void addPort__NoFixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        List<FixedIps> fixedIps = new ArrayList<>();
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void removePort__NoFixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        List<FixedIps> fixedIps = new ArrayList<>();
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
        neutronPortChangeListener.remove(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void removePort__Ipv4FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv4 = new IpAddress(new Ipv4Address("2.2.2.2"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv4);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
        neutronPortChangeListener.remove(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void removePort__Ipv6FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv6 = new IpAddress(new Ipv6Address("1::1"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv6);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
        neutronPortChangeListener.remove(InstanceIdentifier.create(Port.class), port);
    }

    @Test
    public void updatePort__Ipv4FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv4 = new IpAddress(new Ipv4Address("2.2.2.2"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv4);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port1 = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port1);
        PortBuilder pb1 = new PortBuilder();
        pb1.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb1.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb1.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ip4 = new IpAddress(new Ipv4Address("4.4.4.4"));
        FixedIpsBuilder fibuilder = new FixedIpsBuilder();
        fibuilder.setIpAddress(ip4);
        List<FixedIps> fixedIp = new ArrayList<>();
        fixedIp.add(fibuilder.build());
        pb1.setFixedIps(fixedIp);
        Port port2 = pb1.build();
        neutronPortChangeListener.update(InstanceIdentifier.create(Port.class), port1, port2);
    }

    @Test
    public void updatePort__Ipv6FixedIps() throws Exception {
        PortBuilder pb = new PortBuilder();
        pb.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ipv6 = new IpAddress(new Ipv6Address("1::1"));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv6);
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());
        pb.setFixedIps(fixedIps);
        Port port = pb.build();
        neutronPortChangeListener.add(InstanceIdentifier.create(Port.class), port);
        PortBuilder pb1 = new PortBuilder();
        pb1.setUuid(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb1.setNetworkId(new Uuid("12345678-1234-1234-1234-123456789012"));
        pb1.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        IpAddress ip6 = new IpAddress(new Ipv6Address("2::2"));
        FixedIpsBuilder fib1 = new FixedIpsBuilder();
        fib1.setIpAddress(ip6);
        List<FixedIps> fixedIp = new ArrayList<>();
        fixedIp.add(fib1.build());
        pb1.setFixedIps(fixedIp);
        Port port1 = pb1.build();
        neutronPortChangeListener.update(InstanceIdentifier.create(Port.class), port, port1);
    }
}
