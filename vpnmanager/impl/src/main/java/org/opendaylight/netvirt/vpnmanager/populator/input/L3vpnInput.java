/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.input;

import java.math.BigInteger;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;

public class L3vpnInput {
    private String rd;
    private String primaryRd;
    private Adjacency nextHop;
    private String nextHopIp;
    private String gatewayMac;
    private String subnetGatewayMacAddress;
    private Long l3vni;
    private String interfaceName;
    private String vpnName;
    private BigInteger dpnId;
    private VrfEntry.EncapType encapType;
    private RouteOrigin routeOrigin;
    private String subnetIp;
    private Long label;
    private Long elanTag;
    private String networkName;
    private String ipAddress;

    public String getRd() {
        return rd;
    }

    public String getPrimaryRd() {
        return primaryRd;
    }

    public Adjacency getNextHop() {
        return nextHop;
    }

    public String getNextHopIp() {
        return nextHopIp;
    }

    public String getGatewayMac() {
        return gatewayMac;
    }

    public String getSubnetGatewayMacAddress() {
        return subnetGatewayMacAddress;
    }

    public Long getL3vni() {
        return l3vni;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getVpnName() {
        return vpnName;
    }

    public BigInteger getDpnId() {
        return dpnId;
    }

    public VrfEntry.EncapType getEncapType() {
        return encapType;
    }

    public RouteOrigin getRouteOrigin() {
        return routeOrigin;
    }

    public String getNetworkName() {
        return networkName;
    }

    public Long getElanTag() {
        return elanTag;
    }

    public Long getLabel() {
        return label;
    }

    public String getSubnetIp() {
        return subnetIp;
    }

    public String getIpAddress() {
        return  ipAddress;
    }

    public L3vpnInput setPrimaryRd(String primaryRd) {
        this.primaryRd = primaryRd;
        return this;
    }

    public L3vpnInput setRd(String rd) {
        this.rd = rd;
        return this;
    }

    public L3vpnInput setNextHop(Adjacency nextHop) {
        this.nextHop = nextHop;
        return this;
    }

    public L3vpnInput setNextHopIp(String nextHopIp) {
        this.nextHopIp = nextHopIp;
        return this;
    }

    public L3vpnInput setGatewayMac(String gatewayMac) {
        this.gatewayMac = gatewayMac;
        return this;
    }

    public L3vpnInput setSubnetGatewayMacAddress(String subnetGatewayMacAddress) {
        this.subnetGatewayMacAddress = subnetGatewayMacAddress;
        return this;
    }

    public L3vpnInput setL3vni(Long l3vni) {
        this.l3vni = l3vni;
        return this;
    }

    public L3vpnInput setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
        return this;
    }

    public L3vpnInput setVpnName(String vpnName) {
        this.vpnName = vpnName;
        return this;
    }

    public L3vpnInput setDpnId(BigInteger dpnId) {
        this.dpnId = dpnId;
        return this;
    }

    public L3vpnInput setEncapType(VrfEntry.EncapType encapType) {
        this.encapType = encapType;
        return this;
    }

    public L3vpnInput setRouteOrigin(RouteOrigin routeOrigin) {
        this.routeOrigin = routeOrigin;
        return this;
    }

    public L3vpnInput setSubnetIp(String subnetIp) {
        this.subnetIp = subnetIp;
        return this;
    }

    public L3vpnInput setLabel(Long label) {
        this.label = label;
        return this;
    }

    public L3vpnInput setElanTag(Long elanTag) {
        this.elanTag = elanTag;
        return this;
    }

    public L3vpnInput setNetworkName(String networkName) {
        this.networkName = networkName;
        return this;
    }

    public L3vpnInput setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }
}
