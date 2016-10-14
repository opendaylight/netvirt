/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import java.util.Arrays;
import java.util.Collections;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnPopulator implements VpnPopulator {
    protected final VpnInterfaceManager vpnInterfaceManager;
    protected final IBgpManager bgpManager;
    protected final IFibManager fibManager;
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnPopulator.class);

    protected L3vpnPopulator(VpnInterfaceManager vpnInterfaceManager, IBgpManager bgpManager, IFibManager fibManager) {
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
    }

    @Override
    public void populateFib(L3vpnInput input, DataBroker broker, WriteTransaction writeCfgTxn,
                            WriteTransaction writeOperTxn) {}

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void addPrefixToBGP(String rd, String macAddress, String prefix, String nextHopIp,
                                  VrfEntry.EncapType encapType, long label, long l3vni, String gatewayMac,
                                  DataBroker broker, WriteTransaction writeConfigTxn) {
        try {
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopIp, label);
            fibManager.addOrUpdateFibEntry(broker, rd, macAddress, prefix, Arrays.asList(nextHopIp),
                    encapType, (int)label, l3vni, gatewayMac, RouteOrigin.STATIC, writeConfigTxn);
            bgpManager.advertisePrefix(rd, macAddress, prefix, Collections.singletonList(nextHopIp),
                    encapType, (int)label, l3vni, gatewayMac);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nextHop {} label {}", rd, prefix, nextHopIp, label);
        } catch (Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }
}
