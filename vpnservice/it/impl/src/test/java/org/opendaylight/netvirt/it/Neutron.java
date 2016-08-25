/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class Neutron {
    private MdsalUtils mdsalUtils;
    private String tenantId;
    private String networkId;
    private String subnetId;
    public String macPfx = "f4:00:00:0f:00:";
    public String ipPfx = "10.0.0.";
    public String segId = "101";

    Neutron(MdsalUtils mdsalUtils) {
        this.mdsalUtils = mdsalUtils;
        tenantId = UUID.randomUUID().toString();
        networkId = UUID.randomUUID().toString();
        subnetId = UUID.randomUUID().toString();
    }

    void createNetwork() {
        NetworkProviderExtension networkProviderExtension = new NetworkProviderExtensionBuilder()
                .setNetworkType(NetworkTypeVxlan.class)
                .setSegmentationId(segId)
                .build();

        Network network = new NetworkBuilder()
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(networkId))
                .setAdminStateUp(true)
                .setShared(false)
                .setStatus("ACTIVE")
                .setName("net1")
                .addAugmentation(NetworkProviderExtension.class, networkProviderExtension)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Networks.class).child(Network.class, network.getKey()), network);
    }

    void deleteNetwork() {
        if (networkId == null) {
            return;
        }

        Network network = new NetworkBuilder()
                .setUuid(new Uuid(networkId))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Networks.class).child(Network.class, network.getKey()));

        networkId = null;
    }

    void createSubnet() {
        String cidr = ipPfx + "0/24";
        Subnet subnet = new SubnetBuilder()
                .setName("subnet1")
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(subnetId))
                .setNetworkId(new Uuid(networkId))
                .setCidr(new IpPrefix(cidr.toCharArray()))
                .setGatewayIp(new IpAddress(new Ipv4Address(ipPfx + "254")))
                .setIpVersion(IpVersionV4.class)
                .setEnableDhcp(true)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Subnets.class).child(Subnet.class, subnet.getKey()), subnet);
    }

    void deleteSubnet() {
        if (subnetId == null) {
            return;
        }

        Subnet subnet = new SubnetBuilder()
                .setUuid(new Uuid(subnetId))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                        .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                        .child(Subnets.class).child(Subnet.class, subnet.getKey()));

        subnetId = null;
    }

    void createPort(PortInfo portInfo, String owner) {
        // fixed ips
        IpAddress ipv4 = new IpAddress(new Ipv4Address(portInfo.ip));
        FixedIpsBuilder fib = new FixedIpsBuilder();
        fib.setIpAddress(ipv4);
        fib.setSubnetId(new Uuid(subnetId));
        List<FixedIps> fixedIps = new ArrayList<>();
        fixedIps.add(fib.build());

        PortBindingExtensionBuilder portBindingExtensionBuilder = new PortBindingExtensionBuilder();
        portBindingExtensionBuilder.setVifType(NeutronConstants.VIF_TYPE_OVS);
        portBindingExtensionBuilder.setVnicType(NeutronConstants.VNIC_TYPE_NORMAL);

        // port security
        PortSecurityExtensionBuilder portSecurityBuilder = new PortSecurityExtensionBuilder();
        portSecurityBuilder.setPortSecurityEnabled(true);

        Port port = new PortBuilder()
                .addAugmentation(PortSecurityExtension.class, portSecurityBuilder.build())
                .addAugmentation(PortBindingExtension.class, portBindingExtensionBuilder.build())
                .setStatus("ACTIVE")
                .setAdminStateUp(true)
                .setName(portInfo.id)
                .setDeviceOwner(owner)
                .setUuid(new Uuid(portInfo.id))
                .setMacAddress(new MacAddress(portInfo.mac))
                .setNetworkId(new Uuid(networkId))
                .setFixedIps(fixedIps)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Ports.class).child(Port.class, port.getKey()), port);
    }

    void deletePort(String uuid) {
        if (uuid == null) {
            return;
        }

        Port port = new PortBuilder()
                .setUuid(new Uuid(uuid))
                .build();

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Ports.class).child(Port.class, port.getKey()));
    }
}
