/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import java.util.Arrays;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnOverMplsGrePopulator extends L3vpnPopulator {
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnOverMplsGrePopulator.class);

    public L3vpnOverMplsGrePopulator(VpnInterfaceManager vpnInterfaceManager, IBgpManager bgpManager,
                                     IFibManager fibManager) {
        super(vpnInterfaceManager, bgpManager, fibManager);
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        L3vpnRegistry.registerL3vpnPopulator(VrfEntry.EncapType.Mplsgre, this);
    }

    public void close() {
        LOG.trace("L3vpnOverMplsGrePopulator Closed");
    }

    @Override
    public void populateFib(L3vpnInput input, DataBroker broker, WriteTransaction writeConfigTxn,
                            WriteTransaction writeOperTxn) {
        Adjacency nextHop = input.getNextHop();
        long label = nextHop.getLabel();
        String vpnName = input.getVpnName();
        String rd = input.getRd();
        String nextHopIp = input.getNextHopIp();
        VrfEntry.EncapType encapType = input.getEncapType();
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnInterfaceManager.getVpnsImportingMyRoute(vpnName);
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        if (rd != null) {
            vpnInterfaceManager.addToLabelMapper(label, input.getDpnId(), nextHop.getIpAddress(),
                    Arrays.asList(nextHopIp), vpnId, input.getInterfaceName(), null,false, rd, writeOperTxn);
            addPrefixToBGP(rd, null /*macAddress*/, nextHop.getIpAddress(), nextHopIp, encapType, label,
                    0 /*l3vni*/, null /*gatewayMacAddress*/, broker, writeConfigTxn);
            //TODO: ERT - check for VPNs importing my route
            for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                String vpnRd = vpn.getVrfId();
                if (vpnRd != null) {
                    LOG.debug("Exporting route with rd {} prefix {} nexthop {} label {} to VPN {}", vpnRd,
                            nextHop.getIpAddress(), nextHopIp, label, vpn);
                    fibManager.addOrUpdateFibEntry(broker, vpnRd, null /*macAddress*/,
                            nextHop.getIpAddress(), Arrays.asList(nextHopIp), encapType, (int) label,
                            0 /*l3vni*/, null /*gatewayMacAddress*/, RouteOrigin.SELF_IMPORTED, writeConfigTxn);
                }
            }
        } else {
            // ### add FIB route directly
            fibManager.addOrUpdateFibEntry(broker, vpnName, null /*macAddress*/,
                    nextHop.getIpAddress(), Arrays.asList(nextHopIp), encapType, (int) label,
                    0 /*l3vni*/, null /*gatewayMacAddress*/, RouteOrigin.STATIC, writeConfigTxn);
        }
    }

}
