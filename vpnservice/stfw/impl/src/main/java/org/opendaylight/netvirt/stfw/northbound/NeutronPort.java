/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.northbound;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.binding.attributes.VifDetails;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronPort {
    Uuid portId;
    String macAddress;
    Uuid networkId;
    String portType = "trunkport";
    Uuid tenantId;
    boolean adminStatus = true;
    boolean portFilter = false;
    String vifType = "ovs";
    String vnicType = "normal";
    String hostId = "ubuntu";
    Uuid subnetId;
    List<FixedIps> fixedIps;
    final int index;

    public NeutronPort(int index, NeutronSubNet subnet) {
        this.index = index;
        portId = RandomUtils.createUuid();
        macAddress = RandomUtils.createMac();
        this.networkId = subnet.getNetworkId();
        this.subnetId = subnet.getSubnetId();
        tenantId = subnet.getTenantId();
        fixedIps = new ArrayList<FixedIps>();
        FixedIps fixedIp = createFixedIp(subnetId);
        fixedIps.add(fixedIp);
    }

    public FixedIps createFixedIp(Uuid subnetId) {
        Ipv4Address ipv4Address = new Ipv4Address(RandomUtils.createIp(index));
        FixedIps fixedIp = new FixedIpsBuilder().setIpAddress(new IpAddress(ipv4Address)).setSubnetId(subnetId).build();
        return fixedIp;
    }

    public Port build() {
        List<VifDetails> listVifDetails = new ArrayList<VifDetails>();
        //listVifDetails.add(new VifDetailsBuilder().setPortFilter(portFilter).build());
        PortBindingExtensionBuilder portBindingExtensionBuilder = new PortBindingExtensionBuilder().setHostId(hostId)
            .setVifType(vifType).setVnicType(vnicType).setVifDetails(listVifDetails);
        PortBuilder portBuilder = new PortBuilder();
        portBuilder.setAdminStateUp(adminStatus).setUuid(portId).setDeviceId(portId.getValue()).setDeviceOwner("test")
            .setNetworkId(networkId).setFixedIps(fixedIps)
            .setKey(new PortKey(portId)).setMacAddress(new MacAddress(macAddress))
            .setTenantId(tenantId).addAugmentation(PortBindingExtension.class, portBindingExtensionBuilder.build());
        return portBuilder.build();
    }

    public InstanceIdentifier<Port> getIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class, new PortKey(portId));
    }

    public Uuid getPortId() {
        return portId;
    }

    public void setPortId(Uuid portId) {
        this.portId = portId;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public Uuid getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Uuid networkId) {
        this.networkId = networkId;
    }

    public String getPortType() {
        return portType;
    }

    public void setPortType(String portType) {
        this.portType = portType;
    }

    public Uuid getTenantId() {
        return tenantId;
    }

    public void setTenantId(Uuid tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAdminStatus() {
        return adminStatus;
    }

    public void setAdminStatus(boolean adminStatus) {
        this.adminStatus = adminStatus;
    }

    public boolean isPortFilter() {
        return portFilter;
    }

    public void setPortFilter(boolean portFilter) {
        this.portFilter = portFilter;
    }

    public String getVifType() {
        return vifType;
    }

    public void setVifType(String vifType) {
        this.vifType = vifType;
    }

    public String getVnicType() {
        return vnicType;
    }

    public void setVnicType(String vnicType) {
        this.vnicType = vnicType;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public Uuid getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(Uuid subnetId) {
        this.subnetId = subnetId;
    }

    public int getIndex() {
        return index;
    }
}
