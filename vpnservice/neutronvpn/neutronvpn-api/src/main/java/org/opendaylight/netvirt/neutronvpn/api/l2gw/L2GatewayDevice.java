/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.api.l2gw;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;

/**
 * The Class L2GatewayDevice.
 */
public class L2GatewayDevice {

    private final String deviceName;
    private final Set<IpAddress> tunnelIps = ConcurrentHashMap.newKeySet();
    private final Set<Uuid> l2GatewayIds = ConcurrentHashMap.newKeySet();
    private final List<LocalUcastMacs> ucastLocalMacs = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Map<Uuid,Set<Devices>> l2gwConnectionIdToDevices = new ConcurrentHashMap<>();
    private volatile String hwvtepNodeId;

    public L2GatewayDevice(String deviceName) {
        this.deviceName = deviceName;
    }

    /**
     * VTEP device name mentioned with L2 Gateway.
     *
     * @return the device name
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * VTEP Node id for the device mentioned with L2 Gateway.
     *
     * @return the hwvtep node id
     */
    public String getHwvtepNodeId() {
        return hwvtepNodeId;
    }

    /**
     * Sets the hwvtep node id.
     *
     * @param nodeId
     *            the new hwvtep node id
     */
    public void setHwvtepNodeId(String nodeId) {
        this.hwvtepNodeId = nodeId;
    }

    /**
     * Tunnel IP created with in the device mentioned with L2 Gateway.
     *
     * @return the tunnel ips
     */
    @Nonnull
    public Set<IpAddress> getTunnelIps() {
        return tunnelIps;
    }

    public void addL2gwConnectionIdToDevice(Uuid connectionId, Devices device) {
        l2gwConnectionIdToDevices.computeIfAbsent(connectionId, key -> Sets.newConcurrentHashSet()).add(device);
    }

    @Nonnull
    public Collection<Devices> getDevicesForL2gwConnectionId(Uuid connectionId) {
        final Set<Devices> devices = l2gwConnectionIdToDevices.get(connectionId);
        return devices != null ? devices : Collections.emptyList();
    }

    /**
     * Gets the tunnel ip.
     *
     * @return the tunnel ip
     */
    public IpAddress getTunnelIp() {
        if (!tunnelIps.isEmpty()) {
            return tunnelIps.iterator().next();
        }
        return null;
    }

    /**
     * Adds the tunnel ip.
     *
     * @param tunnelIp
     *            the tunnel ip
     */
    public void addTunnelIp(IpAddress tunnelIp) {
        tunnelIps.add(tunnelIp);
    }

    /**
     * UUID representing L2Gateway.
     *
     * @return the l2 gateway ids
     */
    @Nonnull
    public Set<Uuid> getL2GatewayIds() {
        return l2GatewayIds;
    }

    /**
     * Adds the l2 gateway id.
     *
     * @param l2GatewayId
     *            the l2 gateway id
     */
    public void addL2GatewayId(Uuid l2GatewayId) {
        l2GatewayIds.add(l2GatewayId);
    }

    /**
     * Removes the l2 gateway id.
     *
     * @param l2GatewayId
     *            the l2 gateway id
     */
    public void removeL2GatewayId(Uuid l2GatewayId) {
        l2GatewayIds.remove(l2GatewayId);
    }

    /**
     * Clear hwvtep node data.
     */
    public void clearHwvtepNodeData() {
        tunnelIps.clear();
        hwvtepNodeId = null;
    }

    /**
     * Sets the tunnel ips.
     *
     * @param tunnelIps
     *            the new tunnel ips
     */
    public void setTunnelIps(Set<IpAddress> tunnelIps) {
        this.tunnelIps.clear();
        this.tunnelIps.addAll(tunnelIps);
    }

    /**
     * Gets the ucast local macs.
     *
     * @return the ucast local macs
     */
    @Nonnull
    public Collection<LocalUcastMacs> getUcastLocalMacs() {
        return new ArrayList<>(ucastLocalMacs);
    }

    /**
     * Adds the ucast local mac.
     *
     * @param localUcastMacs
     *            the local ucast macs
     */
    public void addUcastLocalMac(LocalUcastMacs localUcastMacs) {
        ucastLocalMacs.add(localUcastMacs);
    }

    /**
     * Removes the ucast local mac.
     *
     * @param localUcastMacs
     *            the local ucast macs
     */
    public void removeUcastLocalMac(LocalUcastMacs localUcastMacs) {
        ucastLocalMacs.remove(localUcastMacs);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setConnected(boolean connected) {
        this.connected.set(connected);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deviceName == null ? 0 : deviceName.hashCode());
        result = prime * result + (hwvtepNodeId == null ? 0 : hwvtepNodeId.hashCode());
        result = prime * result + l2GatewayIds.hashCode();
        result = prime * result + tunnelIps.hashCode();
        result = prime * result + ucastLocalMacs.hashCode();
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        L2GatewayDevice other = (L2GatewayDevice) obj;
        if (deviceName == null) {
            if (other.deviceName != null) {
                return false;
            }
        } else if (!deviceName.equals(other.deviceName)) {
            return false;
        }
        if (hwvtepNodeId == null) {
            if (other.hwvtepNodeId != null) {
                return false;
            }
        } else if (!hwvtepNodeId.equals(other.hwvtepNodeId)) {
            return false;
        }
        if (!l2GatewayIds.equals(other.l2GatewayIds)) {
            return false;
        }
        if (!tunnelIps.equals(other.tunnelIps)) {
            return false;
        }
        if (!ucastLocalMacs.equals(other.ucastLocalMacs)) {
            return false;
        }
        return true;
    }

    public boolean containsUcastMac(LocalUcastMacs mac) {
        return ucastLocalMacs.contains(mac);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        List<String> lstTunnelIps = new ArrayList<>();
        for (IpAddress ip : this.tunnelIps) {
            lstTunnelIps.add(String.valueOf(ip.getValue()));
        }

        List<String> lstMacs =
                this.ucastLocalMacs.stream().map(localUcastMac -> localUcastMac.getMacEntryKey().getValue()).collect(
                        Collectors.toList());

        return "L2GatewayDevice [deviceName=" + deviceName + ", hwvtepNodeId=" + hwvtepNodeId + ", tunnelIps="
                + lstTunnelIps + ", l2GatewayIds=" + l2GatewayIds + ", ucastLocalMacs=" + lstMacs + "]";
    }

}
