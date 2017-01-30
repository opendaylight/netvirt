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
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnPopulator implements VpnPopulator {
    protected final VpnInterfaceManager vpnInterfaceManager;
    protected final IBgpManager bgpManager;
    protected final IFibManager fibManager;
    protected final DataBroker broker;
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnPopulator.class);

    protected L3vpnPopulator(DataBroker dataBroker, VpnInterfaceManager vpnInterfaceManager,
                             IBgpManager bgpManager, IFibManager fibManager) {
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.broker = dataBroker;
    }

    @Override
    public void populateFib(L3vpnInput input, WriteTransaction writeCfgTxn,
                            WriteTransaction writeOperTxn) {}

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        return new AdjacencyBuilder().build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void addPrefixToBGP(String rd, String prefix, String nextHopIp, VrfEntry.EncapType encapType,
                                  long label, long l3vni, String macAddress, String gatewayMac,
                                  DataBroker broker, WriteTransaction writeConfigTxn) {
        try {
            List<String> nextHopList = Collections.singletonList(nextHopIp);
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nextHop {} label {} l3vni {}", rd, prefix, nextHopIp,
                    label, l3vni);
            fibManager.addOrUpdateFibEntry(broker, rd, macAddress, prefix, nextHopList,
                    encapType, (int)label, l3vni, gatewayMac, RouteOrigin.STATIC, writeConfigTxn);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nextHop {} label {}, l3vni {}", rd, prefix, nextHopIp,
                    label, l3vni);
            if (encapType.equals(VrfEntry.EncapType.Mplsgre)) {
                // Advertise the prefix to BGP only if nexthop ip is available
                if (nextHopList != null && !nextHopList.isEmpty()) {
                    bgpManager.advertisePrefix(rd, prefix, nextHopList, (int)label);
                } else {
                    LOG.warn("NextHopList is null/empty. Hence rd {} prefix {} is not advertised to BGP", rd, prefix);
                }
            }
        } catch (Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }
}
