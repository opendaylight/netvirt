/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;

public final class ElanL2GwCacheUtils {

    private static final LoadingCache<String, ConcurrentMap<String, L2GatewayDevice>> CACHES = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, ConcurrentMap<String, L2GatewayDevice>>() {
                @Override
                public ConcurrentMap<String, L2GatewayDevice> load(String key) {
                    return new ConcurrentHashMap<>();
                }
            });

    private ElanL2GwCacheUtils() {
    }

    public static void addL2GatewayDeviceToCache(String elanName, L2GatewayDevice l2GwDevice) {
        CACHES.getUnchecked(elanName).put(l2GwDevice.getHwvtepNodeId(), l2GwDevice);
    }

    public static void removeL2GatewayDeviceFromAllElanCache(String deviceName) {
        CACHES.asMap().values().forEach(deviceMap -> deviceMap.remove(deviceName));
    }

    @Nullable
    public static L2GatewayDevice removeL2GatewayDeviceFromCache(String elanName, String l2gwDeviceNodeId) {
        ConcurrentMap<String, L2GatewayDevice> deviceMap = CACHES.getIfPresent(elanName);
        return deviceMap == null ? null : deviceMap.remove(l2gwDeviceNodeId);
    }

    @Nullable
    public static L2GatewayDevice getL2GatewayDeviceFromCache(String elanName, String l2gwDeviceNodeId) {
        ConcurrentMap<String, L2GatewayDevice> deviceMap = CACHES.getIfPresent(elanName);
        return deviceMap == null ? null : deviceMap.get(l2gwDeviceNodeId);
    }

    public static Collection<L2GatewayDevice> getInvolvedL2GwDevices(String elanName) {
        ConcurrentMap<String, L2GatewayDevice> result = CACHES.getIfPresent(elanName);
        return result == null ? Collections.emptyList() : result.values();
    }

    public static Set<Entry<String, ConcurrentMap<String, L2GatewayDevice>>> getCaches() {
        return CACHES.asMap().entrySet();
    }

    @NonNull
    public static List<L2GatewayDevice> getAllElanDevicesFromCache() {
        List<L2GatewayDevice> l2GwDevices = new ArrayList<>();
        for (ConcurrentMap<String, L2GatewayDevice> cache : CACHES.asMap().values()) {
            l2GwDevices.addAll(cache.values());
        }
        return l2GwDevices;
    }
}
