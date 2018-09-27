/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cache.impl.l2gw;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Service;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;

/**
 * Implementation of L2GatewayCache.
 *
 * @author Thomas Pantelis
 */
@Singleton
@Service(classes = L2GatewayCache.class)
public class L2GatewayCacheImpl implements L2GatewayCache {
    private final ConcurrentMap<String, L2GatewayDevice> cache = new ConcurrentHashMap<>();

    @Override
    public L2GatewayDevice addOrGet(String deviceName) {
        return cache.computeIfAbsent(deviceName, key -> new L2GatewayDevice(deviceName));
    }

    @Override
    public L2GatewayDevice remove(String deviceName) {
        return deviceName != null ? cache.remove(deviceName) : null;
    }

    @Override
    public L2GatewayDevice get(String deviceName) {
        return deviceName != null ? cache.get(deviceName) : null;
    }

    @Override
    public Collection<L2GatewayDevice> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }
}
