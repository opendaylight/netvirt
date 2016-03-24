/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;

/**
 * Created by eanraju on 02-Feb-16.
 */
public class HwVtep {

    private String transportZone;
    private Class<? extends TunnelTypeBase> tunnel_type;
    private IpPrefix ipPrefix;
    private IpAddress gatewayIP;
    private int vlanID;
    private String topo_id;
    private String node_id;
    IpAddress hwIp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HwVtep HwVtep = (HwVtep) o;

        if (vlanID != HwVtep.vlanID) return false;
        if (!transportZone.equals(HwVtep.transportZone)) return false;
        if (tunnel_type != null ? !tunnel_type.equals(HwVtep.tunnel_type) : HwVtep.tunnel_type != null) return false;
        if (!ipPrefix.equals(HwVtep.ipPrefix)) return false;
        if (gatewayIP != null ? !gatewayIP.equals(HwVtep.gatewayIP) : HwVtep.gatewayIP != null) return false;
        if (!topo_id.equals(HwVtep.topo_id)) return false;
        if (!node_id.equals(HwVtep.node_id)) return false;
        return hwIp.equals(HwVtep.hwIp);

    }

    @Override
    public String toString() {
        return "HwVtep{" +
                "transportZone='" + transportZone + '\'' +
                ", tunnel_type=" + tunnel_type +
                ", ipPrefix=" + ipPrefix +
                ", gatewayIP=" + gatewayIP +
                ", vlanID=" + vlanID +
                ", topo_id='" + topo_id + '\'' +
                ", node_id='" + node_id + '\'' +
                ", hwIp=" + hwIp +
                '}';
    }

    @Override
    public int hashCode() {
        int result = transportZone.hashCode();
        result = 31 * result + (tunnel_type != null ? tunnel_type.hashCode() : 0);
        result = 31 * result + ipPrefix.hashCode();
        result = 31 * result + (gatewayIP != null ? gatewayIP.hashCode() : 0);
        result = 31 * result + vlanID;
        result = 31 * result + topo_id.hashCode();
        result = 31 * result + node_id.hashCode();
        result = 31 * result + hwIp.hashCode();
        return result;
    }

    public String getTransportZone() {
        return transportZone;
    }

    public void setTransportZone(String transportZone) {
        this.transportZone = transportZone;
    }

    public Class<? extends TunnelTypeBase> getTunnel_type() {
        return tunnel_type;
    }

    public void setTunnel_type(Class<? extends TunnelTypeBase> tunnel_type) {
        this.tunnel_type = tunnel_type;
    }

    public IpPrefix getIpPrefix() {
        return ipPrefix;
    }

    public void setIpPrefix(IpPrefix ipPrefix) {
        this.ipPrefix = ipPrefix;
    }

    public IpAddress getGatewayIP() {
        return gatewayIP;
    }

    public void setGatewayIP(IpAddress gatewayIP) {
        this.gatewayIP = gatewayIP;
    }

    public int getVlanID() {
        return vlanID;
    }

    public void setVlanID(int vlanID) {
        this.vlanID = vlanID;
    }

    public String getTopo_id() {
        return topo_id;
    }

    public void setTopo_id(String topo_id) {
        this.topo_id = topo_id;
    }

    public String getNode_id() {
        return node_id;
    }

    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }

    public IpAddress getHwIp() {
        return hwIp;
    }

    public void setHwIp(IpAddress hwIp) {
        this.hwIp = hwIp;
    }
}
