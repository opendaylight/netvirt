/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.northbound;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronSubNet {

    private Uuid networkId;
    private Uuid tenantId;
    private Uuid subnetId;
    private boolean enableDhcp;
    private String cidr;
    private String subnetName;
    private int ipsAllocated;
    private Class<? extends IpVersionBase> ipVersion;

    public Uuid getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Uuid networkId) {
        this.networkId = networkId;
    }

    public NeutronSubNet(NeutronNetwork neutronNetwork, int idx) {
        networkId = neutronNetwork.getId();
        tenantId = neutronNetwork.getTenantId();
        subnetId = RandomUtils.createUuid();
        enableDhcp = Boolean.FALSE;
        cidr = RandomUtils.createIpPrefix(idx);
        subnetName = "SUBNET-" + neutronNetwork.getId().getValue();
        ipsAllocated = 0;
        ipVersion = IpVersionV4.class;
    }

    public InstanceIdentifier<Subnet> getSubnetIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(Subnets.class)
            .child(Subnet.class, new SubnetKey(subnetId));
    }

    public Uuid getSubnetId() {
        return this.subnetId;
    }

    public Subnet build() {
        SubnetBuilder subnet = new SubnetBuilder();
        subnet.setEnableDhcp(Boolean.FALSE);
        subnet.setNetworkId(this.networkId);
        subnet.setTenantId(this.tenantId);
        subnet.setUuid(this.subnetId);
        subnet.setName(this.subnetName);
        subnet.setIpVersion(this.ipVersion);
        subnet.setCidr(new IpPrefix(this.cidr.toCharArray()));
        return subnet.build();
    }

    public Uuid getTenantId() {
        return tenantId;
    }

    public NeutronSubNet createNeutronSubNet(WriteTransaction tx, NeutronNetwork neutronNetwork, int idx) {
        NeutronSubNet subnet = new NeutronSubNet(neutronNetwork, idx);
        tx.put(LogicalDatastoreType.CONFIGURATION, subnet.getSubnetIdentifier(), subnet.build(), true);
        return subnet;
    }

    public String allocateIp() {
        String ip = RandomUtils.createIp(cidr, ipsAllocated + 1);
        if (ip != null) {
            ipsAllocated = ipsAllocated + 1;
        }
        return ip;
    }
}
