/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import io.netty.util.Timeout;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6PeriodicTimer;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6PeriodicTrQueue;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class needs to be overridden by Mockito
@SuppressWarnings("checkstyle:FinalClass")
public class VirtualPort implements IVirtualPort  {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualPort.class);

    private final Uuid intfUUID;
    private final Uuid networkID;
    private final String macAddress;
    private final boolean routerIntfFlag;
    private final String deviceOwner;
    private final ConcurrentMap<Uuid, SubnetInfo> snetInfo = new ConcurrentHashMap<>();

    private volatile Long ofPort;
    private volatile BigInteger dpId;
    private volatile boolean serviceBindingStatus;
    private volatile Ipv6PeriodicTimer periodicTimer;
    private volatile Timeout periodicTimeout;

    // associated router if any
    private volatile VirtualRouter router;

    // TODO:: Need Openflow port

    private VirtualPort(Builder builder) {
        this.intfUUID = builder.intfUUID;
        this.networkID = builder.networkID;
        this.macAddress = builder.macAddress;
        this.routerIntfFlag = builder.routerIntfFlag;
        this.deviceOwner = builder.deviceOwner;
    }

    @Override
    public Uuid getIntfUUID() {
        return intfUUID;
    }

    @Override
    public Uuid getNetworkID() {
        return networkID;
    }

    public void setSubnetInfo(Uuid snetID, IpAddress fixedIp) {
        if (snetID != null) {
            snetInfo.computeIfAbsent(snetID, key -> new SubnetInfo(snetID, fixedIp)).setIpAddr(fixedIp);
        }
    }

    public void clearSubnetInfo() {
        snetInfo.clear();
    }

    public void removeSubnetInfo(Uuid snetID) {
        if (snetID != null) {
            this.snetInfo.remove(snetID);
        }
    }

    public void setSubnet(Uuid snetID, VirtualSubnet subnet) {
        SubnetInfo subnetInfo = snetID != null ? snetInfo.get(snetID) : null;
        if (subnetInfo == null) {
            LOG.info("Subnet {} not associated with the virtual port {}",
                snetID, intfUUID);
            return;
        }
        subnetInfo.setSubnet(subnet);
    }

    public List<VirtualSubnet> getSubnets() {
        List<VirtualSubnet> subnetList = new ArrayList<>();
        for (SubnetInfo subnetInfo : snetInfo.values()) {
            if (subnetInfo.getSubnet() != null) {
                subnetList.add(subnetInfo.getSubnet());
            }
        }

        return subnetList;
    }

    public List<IpAddress> getIpAddresses() {
        return snetInfo.values().stream().flatMap(subnetInfo -> Stream.of(subnetInfo.getIpAddr()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Ipv6Address> getIpv6Addresses() {
        List<Ipv6Address> ipv6AddrList = snetInfo.values().stream().flatMap(
            subnetInfo -> Stream.of(subnetInfo.getIpAddr().getIpv6Address())).collect(Collectors.toList());

        if (deviceOwner.equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
            Ipv6Address llAddr = Ipv6Util.getIpv6LinkLocalAddressFromMac(new MacAddress(macAddress));
            ipv6AddrList.add(llAddr);
        }
        return ipv6AddrList;
    }

    public List<Ipv6Address> getIpv6AddressesWithoutLLA() {
        return snetInfo.values().stream().flatMap(
            subnetInfo -> Stream.of(subnetInfo.getIpAddr().getIpv6Address())).collect(Collectors.toList());
    }

    @Override
    public String getMacAddress() {
        return macAddress;
    }

    public boolean getRouterIntfFlag() {
        return routerIntfFlag;
    }

    public void setRouter(VirtualRouter rtr) {
        this.router = rtr;
    }

    public VirtualRouter getRouter() {
        return router;
    }

    @Override
    public String getDeviceOwner() {
        return deviceOwner;
    }

    public void setDpId(BigInteger dpId) {
        this.dpId = dpId;
    }

    @Override
    public BigInteger getDpId() {
        return dpId;
    }

    public void setOfPort(Long ofPort) {
        this.ofPort = ofPort;
    }

    public Long getOfPort() {
        return ofPort;
    }

    public void setServiceBindingStatus(Boolean status) {
        this.serviceBindingStatus = status;
    }

    public boolean getServiceBindingStatus() {
        return serviceBindingStatus;
    }

    public void removeSelf() {
        if (routerIntfFlag) {
            if (router != null) {
                router.removeInterface(this);
            }
        }

        for (SubnetInfo subnetInfo: snetInfo.values()) {
            if (subnetInfo.getSubnet() != null) {
                subnetInfo.getSubnet().removeInterface(this);
            }
        }
    }

    @Override
    public String toString() {
        return "VirtualPort[IntfUUid=" + intfUUID + " subnetInfo="
                + snetInfo + " NetworkId=" + networkID + " mac=" + macAddress + " ofPort="
                + ofPort + " routerFlag=" + routerIntfFlag + " dpId=" + dpId + "]";
    }

    public void setPeriodicTimer(Ipv6PeriodicTrQueue ipv6Queue) {
        periodicTimer = new Ipv6PeriodicTimer(intfUUID, ipv6Queue);
    }

    public Ipv6PeriodicTimer getPeriodicTimer() {
        return periodicTimer;
    }

    public void setPeriodicTimeout(Timeout timeout) {
        periodicTimeout = timeout;
    }

    public void resetPeriodicTimeout() {
        periodicTimeout = null;
    }

    public Timeout getPeriodicTimeout() {
        return periodicTimeout;
    }

    private static class SubnetInfo {
        private final Uuid subnetID;
        private volatile IpAddress ipAddr;
        // associated subnet
        private volatile VirtualSubnet subnet;

        SubnetInfo(Uuid subnetId, IpAddress ipAddr) {
            this.subnetID = subnetId;
            this.ipAddr = ipAddr;
        }

        public Uuid getSubnetID() {
            return subnetID;
        }

        public IpAddress getIpAddr() {
            return ipAddr;
        }

        public void setIpAddr(IpAddress ipAddr) {
            this.ipAddr = ipAddr;
        }

        public VirtualSubnet getSubnet() {
            return subnet;
        }

        public void setSubnet(VirtualSubnet subnet) {
            this.subnet = subnet;
        }

        @Override
        public String toString() {
            return "subnetInfot[subnetId=" + subnetID + " ipAddr=" + ipAddr + " ]";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Uuid intfUUID;
        private Uuid networkID;
        private String macAddress;
        private boolean routerIntfFlag;
        private String deviceOwner;

        public Builder intfUUID(Uuid newIntfUUID) {
            this.intfUUID = newIntfUUID;
            return this;
        }

        public Builder networkID(Uuid newNetworkID) {
            this.networkID = newNetworkID;
            return this;
        }

        public Builder macAddress(String newMacAddress) {
            this.macAddress = newMacAddress;
            return this;
        }

        public Builder routerIntfFlag(boolean value) {
            this.routerIntfFlag = value;
            return this;
        }

        public Builder deviceOwner(String newDeviceOwner) {
            this.deviceOwner = newDeviceOwner;
            return this;
        }

        public VirtualPort build() {
            return new VirtualPort(this);
        }
    }
}
