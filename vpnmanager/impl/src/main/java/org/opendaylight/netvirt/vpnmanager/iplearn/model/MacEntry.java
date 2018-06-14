/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn.model;

import java.net.InetAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;

public class MacEntry {
    private String vpnName;
    private final MacAddress macAddress;
    private final InetAddress ipAddress;
    private String interfaceName;
    private final String createdTime;

    public MacEntry(String vpnName, MacAddress macAddress, InetAddress inetAddress,
                    String interfaceName, String createdTime) {
        this.vpnName = vpnName;
        this.macAddress = macAddress;
        this.ipAddress = inetAddress;
        this.interfaceName = interfaceName;
        this.createdTime = createdTime;
    }

    public String getVpnName() {
        return vpnName;
    }

    public void setVpnName(String vpnName) {
        this.vpnName = vpnName;
    }

    public MacAddress getMacAddress() {
        return macAddress;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public String getCreatedTime() {
        return  createdTime;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
            + (macAddress == null ? 0 : macAddress.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        } else {
            MacEntry other = (MacEntry) obj;
            return vpnName.equals(other.vpnName)
                    && macAddress.equals(other.macAddress)
                    && ipAddress.equals(other.ipAddress)
                    && interfaceName.equals(other.interfaceName)
                    && createdTime.equals(other.getCreatedTime());
        }
    }

    @Override
    public String toString() {
        return "MacEntry [vpnName=" + vpnName + ", macAddress=" + macAddress + ", ipAddress=" + ipAddress
            + ", interfaceName=" + interfaceName + ", createdTime=" + createdTime + "]";
    }
}
