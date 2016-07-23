/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.utils;

import org.opendaylight.genius.utils.cache.CacheUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a per-blade cache, which is feeded by a clustered data change
 * listener.
 *
 */
public class VpnPseudoPortCache {
    public static final String VPNPSEUDOPORT_CACHE_NAME = "VrfToVpnPseudoPortCache";

    public static void createVpnPseudoPortCache() {
        if (CacheUtil.getCache(VPNPSEUDOPORT_CACHE_NAME) == null) {
            CacheUtil.createCache(VPNPSEUDOPORT_CACHE_NAME);
        }
    }

    public static void addVpnPseudoPortToCache(String vrfId, long vpnPseudoLportTag) {
        ConcurrentHashMap<String, Long> cache =
            (ConcurrentHashMap<String, Long>) CacheUtil.getCache(VPNPSEUDOPORT_CACHE_NAME);
        cache.put(vrfId, Long.valueOf(vpnPseudoLportTag));
    }

    public static Long getVpnPseudoPortTagFromCache(String vrfId) {
        ConcurrentHashMap<String, Long> cache =
            (ConcurrentHashMap<String, Long>) CacheUtil.getCache(VPNPSEUDOPORT_CACHE_NAME);
        return cache.get(vrfId);
    }

    public static void removeVpnPseudoPortFromCache(String vrfId) {
        ConcurrentHashMap<String, Long> cache =
            (ConcurrentHashMap<String, Long>) CacheUtil.getCache(VPNPSEUDOPORT_CACHE_NAME);
        cache.remove(vrfId);
    }


}
