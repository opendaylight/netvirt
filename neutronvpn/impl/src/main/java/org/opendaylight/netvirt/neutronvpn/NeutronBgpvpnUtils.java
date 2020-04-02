/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronBgpvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnUtils.class);

    private final ConcurrentHashMap<Uuid, List<Uuid>> unprocessedNetworksMap;
    private final ConcurrentHashMap<Uuid, List<Uuid>> unprocessedRoutersMap;
    private final NamedLocks<String> vpnLock;

    @Inject
    public NeutronBgpvpnUtils() {
        unprocessedNetworksMap = new ConcurrentHashMap<>();
        unprocessedRoutersMap = new ConcurrentHashMap<>();
        vpnLock = new NamedLocks<>();
    }

    public void addUnProcessedNetwork(Uuid vpnId, Uuid networkId) {
        LOG.trace("Adding Unprocessed Network to Bgpvpn : bgpvpnId: {}, networkId={}", vpnId.getValue(),
                networkId.getValue());
        List<Uuid> unProcessedNetworkIds = unprocessedNetworksMap.get(vpnId);
        if (unProcessedNetworkIds == null) {
            unProcessedNetworkIds = new ArrayList<>();
            unProcessedNetworkIds.add(networkId);
            unprocessedNetworksMap.putIfAbsent(vpnId, unProcessedNetworkIds);
        } else {
            if (!unProcessedNetworkIds.contains(networkId)) {
                unProcessedNetworkIds.add(networkId);
            }
        }
    }

    public void removeUnProcessedNetwork(Uuid vpnId, Uuid networkId) {
        LOG.trace("Removing Unprocessed Network to Bgpvpn : bgpvpnId: {}, networkId={}", vpnId.getValue(),
                networkId.getValue());
        List<Uuid> unProcessedNetworkIds = unprocessedNetworksMap.get(vpnId);
        if (unProcessedNetworkIds != null) {
            unProcessedNetworkIds.remove(networkId);
        }
    }

    public void addUnProcessedRouter(Uuid vpnId, Uuid routerId) {
        LOG.trace("Adding Unprocessed Router to Bgpvpn : bgpvpnId: {}, routerId={}", vpnId.getValue(),
                routerId.getValue());
        List<Uuid> unProcessedRouterIds = unprocessedRoutersMap.get(vpnId);
        if (unProcessedRouterIds == null) {
            unProcessedRouterIds =  new ArrayList<>();
            unProcessedRouterIds.add(routerId);
            unprocessedRoutersMap.putIfAbsent(vpnId, unProcessedRouterIds);
        } else {
            if (!unProcessedRouterIds.contains(routerId)) {
                unProcessedRouterIds.add(routerId);
            }
        }
    }

    public void removeUnProcessedRouter(Uuid vpnId, Uuid routerId) {
        LOG.trace("Removing Unprocessed Router to Bgpvpn : bgpvpnId: {}, routerId={}", vpnId.getValue(),
                routerId.getValue());
        List<Uuid> unProcessedRouterIds = unprocessedRoutersMap.get(vpnId);
        if (unProcessedRouterIds != null) {
            unProcessedRouterIds.remove(routerId);
        }
    }

    public NamedLocks<String> getVpnLock() {
        return vpnLock;
    }

    public ConcurrentHashMap<Uuid, List<Uuid>> getUnProcessedRoutersMap() {
        return unprocessedRoutersMap;
    }

    public ConcurrentHashMap<Uuid, List<Uuid>> getUnProcessedNetworksMap() {
        return unprocessedNetworksMap;
    }

    public List<Uuid> getUnprocessedNetworksForBgpvpn(Uuid vpnId) {
        return unprocessedNetworksMap.get(vpnId);
    }

    public List<Uuid> getUnprocessedRoutersForBgpvpn(Uuid vpnId) {
        return unprocessedRoutersMap.get(vpnId);
    }
}

