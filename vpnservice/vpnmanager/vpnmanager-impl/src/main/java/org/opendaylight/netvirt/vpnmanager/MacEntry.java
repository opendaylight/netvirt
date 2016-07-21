/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;

import java.net.InetAddress;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class MacEntry implements Delayed {

    private long expiryTime;
    private String vpnName;
    private MacAddress macAddress;
    private InetAddress ipAddress;
    private String interfaceName;

    public MacEntry(long delay, String vpnName, MacAddress macAddress, InetAddress inetAddress, String interfaceName) {
        this.expiryTime = System.currentTimeMillis() + delay;
        this.vpnName = vpnName;
        this.macAddress = macAddress;
        this.ipAddress = inetAddress;
        this.interfaceName = interfaceName;
    }

    public String getVpnName() {
        return vpnName;
    }

    public void setVpnName(String vpnName) {
        this.vpnName = vpnName;
    }


    @Override
    public String toString() {
        return "MacEntry [expiryTime=" + expiryTime + ", vpnName=" + vpnName + ", macAddress=" + macAddress
              + ", ipAddress=" + ipAddress + ", interfaceName=" + interfaceName + "]";
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


    @Override
    public int compareTo(Delayed obj) {
        if (this.expiryTime > ((MacEntry) obj).expiryTime) {
            return -1;
        } else if (this.expiryTime < ((MacEntry) obj).expiryTime) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public long getDelay(TimeUnit arg0) {
        long diff = expiryTime - System.currentTimeMillis();
        return diff;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((macAddress == null) ? 0 : macAddress.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if (getClass() != obj.getClass())
            return result;
        else{
        MacEntry other = (MacEntry) obj;
        result = vpnName.equals(other.vpnName) && macAddress.equals(other.macAddress) && ipAddress.equals(other.ipAddress) && interfaceName.equals(other.interfaceName);
        }
        return result;
    }


}
