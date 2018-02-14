/*
 * Copyright © 2015, 2017 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.netvirt.ipv6service.api.IVirtualSubnet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public final class VirtualSubnet implements IVirtualSubnet  {

    private final Uuid subnetUUID;
    private final Uuid tenantID;
    private final String name;
    private final IpAddress gatewayIp;
    private final IpPrefix subnetCidr;
    private final String ipVersion;
    private final String ipv6AddressMode;
    private final String ipv6RAMode;

    // interface list
    private final ConcurrentMap<Uuid, VirtualPort> interfaces = new ConcurrentHashMap<>();

    // associated router
    private volatile VirtualRouter router;

    private VirtualSubnet(Builder builder) {
        this.subnetUUID = builder.subnetUUID;
        this.tenantID = builder.tenantID;
        this.name = builder.name;
        this.gatewayIp = builder.gatewayIp;
        this.subnetCidr = builder.subnetCidr;
        this.ipVersion = builder.ipVersion;
        this.ipv6AddressMode = builder.ipv6AddressMode;
        this.ipv6RAMode = builder.ipv6RAMode;
    }

    @Override
    public Uuid getSubnetUUID() {
        return subnetUUID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Uuid getTenantID() {
        return tenantID;
    }

    public IpAddress getGatewayIp() {
        return gatewayIp;
    }

    @Override
    public String getIpVersion() {
        return ipVersion;
    }

    @Override
    public IpPrefix getSubnetCidr() {
        return subnetCidr;
    }

    public String getIpv6AddressMode() {
        return ipv6AddressMode;
    }

    public String getIpv6RAMode() {
        return ipv6RAMode;
    }

    public void setRouter(VirtualRouter rtr) {
        this.router = rtr;
    }

    public VirtualRouter getRouter() {
        return router;
    }

    public void addInterface(VirtualPort intf) {
        Uuid intfUUID = intf.getIntfUUID();
        if (intfUUID != null) {
            interfaces.put(intfUUID, intf);
        }
    }

    public void removeInterface(VirtualPort intf) {
        Uuid intfUUID = intf.getIntfUUID();
        if (intfUUID != null) {
            interfaces.remove(intfUUID);
        }
    }

    public void removeSelf() {
        interfaces.values().forEach(intf -> intf.removeSubnetInfo(subnetUUID));

        if (router != null) {
            router.removeSubnet(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Uuid subnetUUID;
        private Uuid tenantID;
        private String name;
        private IpAddress gatewayIp;
        private IpPrefix subnetCidr;
        private String ipVersion;
        private String ipv6AddressMode;
        private String ipv6RAMode;

        public Builder subnetUUID(Uuid newSubnetUUID) {
            this.subnetUUID = newSubnetUUID;
            return this;
        }

        public Builder tenantID(Uuid newTenantID) {
            this.tenantID = newTenantID;
            return this;
        }

        public Builder name(String newName) {
            this.name = newName;
            return this;
        }

        public Builder gatewayIp(IpAddress newGatewayIp) {
            this.gatewayIp = newGatewayIp;
            return this;
        }

        public Builder subnetCidr(IpPrefix newSubnetCidr) {
            this.subnetCidr = newSubnetCidr;
            return this;
        }

        public Builder ipVersion(String newIpVersion) {
            this.ipVersion = newIpVersion;
            return this;
        }

        public Builder ipv6AddressMode(String newIpv6AddressMode) {
            this.ipv6AddressMode = newIpv6AddressMode;
            return this;
        }

        public Builder ipv6RAMode(String newIpv6RAMode) {
            this.ipv6RAMode = newIpv6RAMode;
            return this;
        }

        public VirtualSubnet build() {
            return new VirtualSubnet(this);
        }
    }
}
