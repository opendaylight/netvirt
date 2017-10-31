/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.l2gw.utils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;

public class L2GatewayCacheUtils {
    public static final String L2GATEWAY_CACHE_NAME = "L2GW";

    private static final ConcurrentMap<String, L2GatewayDevice> CACHE = new ConcurrentHashMap<>();

    public static void addL2DeviceToCache(String devicename, L2GatewayDevice l2GwDevice) {
        CACHE.put(devicename, l2GwDevice);
    }

    public static L2GatewayDevice removeL2DeviceFromCache(String devicename) {
        return CACHE.remove(devicename);
    }

    public static L2GatewayDevice getL2DeviceFromCache(String devicename) {
        return CACHE.get(devicename);
    }

    public static ConcurrentMap<String, L2GatewayDevice> getCache() {
        return CACHE;
    }

    public static L2GatewayDevice updateCacheUponL2GatewayAdd(final String psName, final Uuid l2gwUuid) {
        final L2GatewayDevice l2GwDevice = CACHE.computeIfAbsent(psName, key -> new L2GatewayDevice(psName));
        l2GwDevice.addL2GatewayId(l2gwUuid);
        return l2GwDevice;
    }

    public static L2GatewayDevice updateCacheUponSwitchConnect(final String psName, final String
            hwvtepNodeId, final List<TunnelIps> tunnelIps) {
        final L2GatewayDevice l2GwDevice = CACHE.computeIfAbsent(psName, key -> new L2GatewayDevice(psName));
        l2GwDevice.setConnected(true);
        l2GwDevice.setHwvtepNodeId(hwvtepNodeId);

        if (tunnelIps != null) {
            for (TunnelIps tunnelIp : tunnelIps) {
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }

        return l2GwDevice;
    }

    public static L2GatewayDevice updateL2GatewayCache(String psName, String hwvtepNodeId, List<TunnelIps> tunnelIps) {
        final L2GatewayDevice l2GwDevice = CACHE.computeIfAbsent(psName, key -> new L2GatewayDevice(psName));
        l2GwDevice.setConnected(true);
        l2GwDevice.setHwvtepNodeId(hwvtepNodeId);

        if (tunnelIps != null) {
            for (TunnelIps tunnelIp : tunnelIps) {
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }

        return l2GwDevice;
    }
}
