/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CacheUtil {
    private static final ConcurrentMap<String, ConcurrentMap<?, ?>> m_mapCache = 
            new ConcurrentHashMap<String, ConcurrentMap<?, ?>>();

    public static ConcurrentMap<?, ?> getCache(String sCacheName) {
        return m_mapCache.get(sCacheName);
    }

    public static void createCache(String sCacheName) {
        if (m_mapCache.get(sCacheName) == null)
            m_mapCache.put(sCacheName, new ConcurrentHashMap<Object, Object>());
    }

    public static void destroyCache(String sCacheName) {
        m_mapCache.remove(sCacheName);
    }
}
