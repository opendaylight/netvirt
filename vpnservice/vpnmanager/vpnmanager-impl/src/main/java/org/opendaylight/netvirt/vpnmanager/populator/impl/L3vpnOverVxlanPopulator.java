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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnOverVxlanPopulator extends L3vpnPopulator {
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnOverVxlanPopulator.class);

    public L3vpnOverVxlanPopulator(DataBroker dataBroker, VpnInterfaceManager vpnInterfaceManager,
                                   IBgpManager bgpManager, IFibManager fibManager) {
        super(dataBroker, vpnInterfaceManager, bgpManager, fibManager);
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        L3vpnRegistry.registerL3vpnPopulator(VrfEntry.EncapType.Vxlan, this);
    }

    public void close() {
        LOG.trace("L3vpnOverVxlanPopulator Closed");
    }

    @Override
    public void populateFib(L3vpnInput input, WriteTransaction writeConfigTxn,
                            WriteTransaction writeOperTxn) {
        String rd = input.getRd();
        Adjacency nextHop = input.getNextHop();
        if (rd != null) {
            addPrefixToBGP(rd, nextHop.getIpAddress(), input.getNextHopIp(), input.getEncapType(), 0 /*label*/,
                    Long.valueOf(input.getL3vni()), nextHop.getMacAddress(), input.getGatewayMac(),
                    broker, writeConfigTxn);
        } else {
            LOG.error("Internal VPN for L3 Over VxLAN is not supported. Aborting.");
            return;
        }
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        Adjacency nextHop = input.getNextHop();
        String nextHopIp = input.getNextHopIp();
        String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
        List<String> adjNextHop = nextHop.getNextHopIpList();
        List<String> nextHopList = (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop
                : (nextHopIp == null ? Collections.emptyList() : Collections.singletonList(nextHopIp));

        return new AdjacencyBuilder(nextHop).setNextHopIpList(nextHopList).setIpAddress(prefix)
                .setKey(new AdjacencyKey(prefix)).build();
    }
}
