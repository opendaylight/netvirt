/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.l2gw.utils;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.genius.utils.cache.CacheUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;

public class L2GatewayCacheUtils {
    public static final String L2GATEWAY_CACHE_NAME = "L2GW";

    static {
        createL2DeviceCache();
    }

    public static void createL2DeviceCache() {
        if (CacheUtil.getCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME) == null) {
            CacheUtil.createCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME);
        }
    }

    public static void addL2DeviceToCache(String devicename, L2GatewayDevice l2GwDevice) {
        ConcurrentMap<String, L2GatewayDevice> cachedMap = (ConcurrentMap<String, L2GatewayDevice>) CacheUtil
                .getCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME);
        cachedMap.put(devicename, l2GwDevice);
    }

    public static L2GatewayDevice removeL2DeviceFromCache(String devicename) {
        ConcurrentMap<String, L2GatewayDevice> cachedMap = (ConcurrentMap<String, L2GatewayDevice>) CacheUtil
                .getCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME);
        return cachedMap.remove(devicename);
    }

    public static L2GatewayDevice getL2DeviceFromCache(String devicename) {
        ConcurrentMap<String, L2GatewayDevice> cachedMap = (ConcurrentMap<String, L2GatewayDevice>) CacheUtil
                .getCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME);
        return cachedMap.get(devicename);
    }

    public static ConcurrentMap<String, L2GatewayDevice> getCache() {
        return (ConcurrentMap<String, L2GatewayDevice>) CacheUtil
                .getCache(L2GatewayCacheUtils.L2GATEWAY_CACHE_NAME);
    }

    public static synchronized  L2GatewayDevice updateCacheUponL2GatewayAdd(final String psName, final Uuid l2gwUuid) {
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
        if (l2GwDevice == null) {
            l2GwDevice = new L2GatewayDevice();
            l2GwDevice.setDeviceName(psName);
            l2GwDevice.addL2GatewayId(l2gwUuid);
        } else {
            l2GwDevice.addL2GatewayId(l2gwUuid);
        }
        addL2DeviceToCache(psName, l2GwDevice);
        return l2GwDevice;
    }

    public static synchronized  L2GatewayDevice updateCacheUponSwitchConnect(final String psName, final String
            hwvtepNodeId, final List<TunnelIps> tunnelIps) {
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
        if (l2GwDevice == null) {
            l2GwDevice = new L2GatewayDevice();
            l2GwDevice.setDeviceName(psName);
        }
        l2GwDevice.setConnected(true);
        l2GwDevice.setHwvtepNodeId(hwvtepNodeId);

        if (tunnelIps != null && !tunnelIps.isEmpty()) {
            for (TunnelIps tunnelIp : tunnelIps) {
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }
        addL2DeviceToCache(psName, l2GwDevice);
        return l2GwDevice;
    }
}
