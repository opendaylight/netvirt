/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L3vpnOverVxlanPopulator extends L3vpnPopulator {
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnOverVxlanPopulator.class);

    @Inject
    public L3vpnOverVxlanPopulator(DataBroker dataBroker, IBgpManager bgpManager, IFibManager fibManager,
                                   VpnUtil vpnUtil) {
        super(dataBroker, bgpManager, fibManager, vpnUtil);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        L3vpnRegistry.registerL3vpnPopulator(VrfEntry.EncapType.Vxlan, this);
    }

    @PreDestroy
    public void close() {
        LOG.trace("L3vpnOverVxlanPopulator Closed");
    }

    @Override
    public void populateFib(L3vpnInput input, String vpnInterface, String source,
                            TypedWriteTransaction<Configuration> writeConfigTxn) {
        if (input.getRouteOrigin() == RouteOrigin.CONNECTED) {
            LOG.info("populateFib : Found SubnetRoute for subnet {} rd {}", input.getSubnetIp(), input.getPrimaryRd());
            addSubnetRouteFibEntry(input);
            return;
        }
        String rd = input.getRd();
        String primaryRd = input.getPrimaryRd();
        Adjacency nextHop = input.getNextHop();
        LOG.info("populateFib : Found Interface Adjacency with prefix {} rd {}", nextHop.getIpAddress(), primaryRd);
        if (!rd.equalsIgnoreCase(input.getVpnName()) && !rd.equals(input.getNetworkName())) {
            Objects.requireNonNull(input.getRouteOrigin(), "populateFib: RouteOrigin is mandatory");
            addPrefixToBGP(rd, primaryRd, nextHop.getMacAddress(), nextHop.getIpAddress(), input.getNextHopIp(),
                    input.getEncapType(), Uint32.ZERO /*label*/, Uint32.valueOf(input.getL3vni()),
                    input.getGatewayMac(), input.getRouteOrigin(), vpnInterface, source, writeConfigTxn);
        } else {
            LOG.error("Internal VPN for L3 Over VxLAN is not supported. Aborting.");
            return;
        }
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        Adjacency nextHop = input.getNextHop();
        String nextHopIp = input.getNextHopIp();
        String rd = input.getRd();
        String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
        List<String> adjNextHop = nextHop.getNextHopIpList();
        List<String> nextHopList = adjNextHop != null && !adjNextHop.isEmpty() ? adjNextHop
                : nextHopIp == null ? Collections.emptyList() : Collections.singletonList(nextHopIp);

        return new AdjacencyBuilder(nextHop).setNextHopIpList(nextHopList).setIpAddress(prefix).setVrfId(rd)
                .withKey(new AdjacencyKey(prefix)).setAdjacencyType(nextHop.getAdjacencyType())
                .setSubnetGatewayMacAddress(nextHop.getSubnetGatewayMacAddress()).build();
    }
}
