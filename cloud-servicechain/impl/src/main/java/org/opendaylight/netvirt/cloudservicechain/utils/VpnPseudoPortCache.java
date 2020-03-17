/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.utils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a per-blade cache, which is fed by a clustered data change listener.
 */
@Singleton
public class VpnPseudoPortCache {
    private static final Logger LOG = LoggerFactory.getLogger(VpnPseudoPortCache.class);

    private final DataBroker broker;
    private final ConcurrentMap<String, Long> cache =  new ConcurrentHashMap<>();

    @Inject
    public VpnPseudoPortCache(DataBroker broker) {
        this.broker = broker;
    }

    @PostConstruct
    public void init() {
        LOG.info("Initial read of Vpn to VpnPseudoPort map from Datastore");
        try {
            List<VpnToPseudoPortData> allVpnToPseudoPortData = VpnServiceChainUtils.getAllVpnToPseudoPortData(broker);
            for (VpnToPseudoPortData vpnToPseudoPort : allVpnToPseudoPortData) {
                add(vpnToPseudoPort.getVrfId(), vpnToPseudoPort.getVpnLportTag());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading VPN to pseudo-port map", e);
        }
    }

    public void add(@NonNull String vrfId, long vpnPseudoLportTag) {
        LOG.debug("Adding vpn {} and vpnPseudoLportTag {} to VpnPseudoPortCache", vrfId, vpnPseudoLportTag);
        cache.put(vrfId, Long.valueOf(vpnPseudoLportTag));
    }

    @Nullable
    public Long get(@NonNull String vrfId) {
        return cache.get(vrfId);
    }

    @Nullable
    public Long remove(@NonNull String vrfId) {
        LOG.debug("Removing vpn {} from VpnPseudoPortCache", vrfId);
        return cache.remove(vrfId);
    }
}
