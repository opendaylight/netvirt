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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Service;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;

/**
 * Implementation of L2GatewayCache.
 *
 * @author Thomas Pantelis
 */
@Singleton
@Service(classes = L2GatewayCache.class)
public class L2GatewayCacheImpl implements L2GatewayCache {
    private final ConcurrentMap<String, L2GatewayDevice> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, L2GatewayDevice> cacheByNodeId = new ConcurrentHashMap<>();

    @Override
    public L2GatewayDevice addOrGet(String deviceName) {
        return cache.computeIfAbsent(deviceName, key -> new L2GatewayDevice(deviceName));
    }

    @Override
    public void add(String deviceName, L2GatewayDevice l2GatewayDevice) {
        cache.put(deviceName, l2GatewayDevice);
    }

    @Override
    public L2GatewayDevice remove(String deviceName) {
        L2GatewayDevice l2GatewayDevice = deviceName != null ? cache.remove(deviceName) : null;
        if (l2GatewayDevice != null && l2GatewayDevice.getHwvtepNodeId() != null) {
            cacheByNodeId.remove(l2GatewayDevice.getHwvtepNodeId());
        }
        return l2GatewayDevice;
    }

    @Override
    public L2GatewayDevice get(String deviceName) {
        return deviceName != null ? cache.get(deviceName) : null;
    }

    @Override
    public L2GatewayDevice getByNodeId(String nodeId) {
        return nodeId != null ? (L2GatewayDevice) cacheByNodeId.get(nodeId) : null;
    }

    @Override
    public Collection<L2GatewayDevice> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public L2GatewayDevice updateL2GatewayCache(String deviceName, Uuid l2gwUuid) {
        L2GatewayDevice l2GwDevice = get(deviceName);
        if (l2GwDevice == null) {
            l2GwDevice = new L2GatewayDevice(deviceName);
            l2GwDevice.addL2GatewayId(l2gwUuid);
        } else {
            l2GwDevice.addL2GatewayId(l2gwUuid);
        }

        add(deviceName, l2GwDevice);
        return l2GwDevice;
    }

    @Override
    public L2GatewayDevice updateL2GatewayCache(String deviceName, String hwvtepNodeId, List<TunnelIps> tunnelIps) {
        L2GatewayDevice l2GwDevice = get(deviceName);
        if (l2GwDevice == null) {
            l2GwDevice = new L2GatewayDevice(deviceName);
        }

        l2GwDevice.setConnected(true);
        l2GwDevice.setHwvtepNodeId(hwvtepNodeId);
        if (tunnelIps != null && !tunnelIps.isEmpty()) {
            Iterator var4 = tunnelIps.iterator();

            while (var4.hasNext()) {
                TunnelIps tunnelIp = (TunnelIps)var4.next();
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }

        add(deviceName, l2GwDevice);
        cacheByNodeId.put(hwvtepNodeId, l2GwDevice);
        return l2GwDevice;
    }

    @Override
    public ConcurrentMap<String, L2GatewayDevice> getCache() {
        return cache;
    }
}