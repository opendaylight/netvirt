/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.util.UUID;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronNetwork {
    private final MdsalUtils mdsalUtils;
    private final String segId;
    private final String ipPfx;
    private final String tenantId;
    private final String networkId;
    private final String subnetId;
    private Network network;
    private Subnet subnet;

    NeutronNetwork(final MdsalUtils mdsalUtils, final String segId, final String ipPfx) {
        this.mdsalUtils = mdsalUtils;
        this.segId = segId;
        this.ipPfx = ipPfx;
        tenantId = UUID.randomUUID().toString();
        networkId = UUID.randomUUID().toString();
        subnetId = UUID.randomUUID().toString();
    }

    String getNetworkId() {
        return networkId;
    }

    String getSubnetId() {
        return subnetId;
    }

    String getIpPfx() {
        return ipPfx;
    }

    void createNetwork(final String name) {
        NetworkProviderExtension networkProviderExtension = new NetworkProviderExtensionBuilder()
                .setNetworkType(NetworkTypeVxlan.class)
                .setSegmentationId(segId)
                .build();

        network = new NetworkBuilder()
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(networkId))
                .setAdminStateUp(true)
                .setShared(false)
                .setStatus("ACTIVE")
                .setName(name)
                .addAugmentation(NetworkProviderExtension.class, networkProviderExtension)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Networks.class).child(Network.class, network.getKey()), network);
    }

    void deleteNetwork() {
        if (network == null) {
            return;
        }

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Networks.class).child(Network.class, network.getKey()));

    }

    void createSubnet(final String name) {
        String cidr = ipPfx + "0/24";
        subnet = new SubnetBuilder()
                .setName(name)
                .setTenantId(new Uuid(tenantId))
                .setUuid(new Uuid(subnetId))
                .setNetworkId(new Uuid(networkId))
                .setCidr(new IpPrefix(cidr.toCharArray()))
                .setGatewayIp(new IpAddress(new Ipv4Address(ipPfx + NetvirtITConstants.GATEWAY_SUFFIX)))
                .setIpVersion(IpVersionV4.class)
                .setEnableDhcp(true)
                .build();

        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Subnets.class).child(Subnet.class, subnet.getKey()), subnet);
    }

    void deleteSubnet() {
        if (subnet == null) {
            return;
        }

        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron.class)
                .child(Subnets.class).child(Subnet.class, subnet.getKey()));
    }
}
