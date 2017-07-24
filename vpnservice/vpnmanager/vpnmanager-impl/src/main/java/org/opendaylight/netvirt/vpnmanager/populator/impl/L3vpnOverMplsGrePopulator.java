/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnOverMplsGrePopulator extends L3vpnPopulator {
    private final IdManagerService idManager;
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnOverMplsGrePopulator.class);

    public L3vpnOverMplsGrePopulator(DataBroker dataBroker, VpnInterfaceManager vpnInterfaceManager,
                                     IBgpManager bgpManager, IFibManager fibManager, IdManagerService idManager) {
        super(dataBroker, vpnInterfaceManager, bgpManager, fibManager);
        this.idManager = idManager;
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        L3vpnRegistry.registerL3vpnPopulator(VrfEntry.EncapType.Mplsgre, this);
    }

    public void close() {
        LOG.trace("L3vpnOverMplsGrePopulator Closed");
    }

    @Override
    public void populateFib(L3vpnInput input, WriteTransaction writeConfigTxn, WriteTransaction writeOperTxn) {
        if (input.getRouteOrigin() == RouteOrigin.CONNECTED) {
            LOG.info("populateFib : Found SubnetRoute for subnet {} rd {}", input.getSubnetIp(), input.getPrimaryRd());
            addSubnetRouteFibEntry(input);
            return;
        }
        Adjacency nextHop = input.getNextHop();
        long label = nextHop.getLabel();
        String vpnName = input.getVpnName();
        String primaryRd = input.getPrimaryRd();
        String rd = input.getRd();
        String nextHopIp = input.getNextHopIp();
        VrfEntry.EncapType encapType = input.getEncapType();
        LOG.info("populateFib : Found Interface Adjacency with prefix {} rd {}", nextHop.getIpAddress(), primaryRd);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnInterfaceManager.getVpnsImportingMyRoute(vpnName);
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        String nextHopIpAddress = nextHop.getIpAddress(); // it is a valid case for nextHopIpAddress to be null
        // Not advertising the prefix to BGP for InternalVpn (where rd is vpnName),
        // transparentInternetVpn (where rd is Network name)
        // and internalVpnForExtraRoute (where rd is DpnId)
        if (VpnUtil.isEligibleForBgp(rd, input.getVpnName(), input.getDpnId(), input.getNetworkName())) {
            // the DpnId is set as rd in case of extra routes present in router based VPN
            vpnInterfaceManager.addToLabelMapper(label, input.getDpnId(), nextHopIpAddress,
                    Arrays.asList(nextHopIp), vpnId, input.getInterfaceName(), null,false,
                    primaryRd);
            Objects.requireNonNull(input.getRouteOrigin(), "RouteOrigin is mandatory");
            addPrefixToBGP(rd, primaryRd, null /*macAddress*/, nextHopIpAddress, nextHopIp, encapType,
                    label, 0 /*l3vni*/, input.getGatewayMac(), input.getRouteOrigin(), writeConfigTxn);
            //TODO: ERT - check for VPNs importing my route
            for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                String vpnRd = vpn.getVrfId();
                if (vpnRd != null) {
                    LOG.debug("Exporting route with rd {} prefix {} nexthop {} label {} to VPN {}", vpnRd,
                            nextHopIpAddress, nextHopIp, label, vpn);
                    fibManager.addOrUpdateFibEntry(broker, vpnRd, null /*macAddress*/,
                            nextHopIpAddress, Arrays.asList(nextHopIp), encapType, (int) label,
                            0 /*l3vni*/, input.getGatewayMac(), null /*parentVpnRd*/, RouteOrigin.SELF_IMPORTED,
                            writeConfigTxn);
                }
            }
        } else {
            // ### add FIB route directly
            fibManager.addOrUpdateFibEntry(broker, vpnName, null /*macAddress*/,
                    nextHopIpAddress, Arrays.asList(nextHopIp), encapType, (int) label,
                    0 /*l3vni*/, input.getGatewayMac(), null /*parentVpnRd*/, input.getRouteOrigin(), writeConfigTxn);
        }
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        Adjacency nextHop = input.getNextHop();
        String nextHopIp = input.getNextHopIp();
        String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
        List<String> adjNextHop = nextHop.getNextHopIpList();
        String rd = input.getRd();
        String primaryRd = input.getPrimaryRd();
        String vpnName = input.getVpnName();
        long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                VpnUtil.getNextHopLabelKey(primaryRd, prefix));
        if (label == VpnConstants.INVALID_LABEL) {
            String error = "Unable to fetch label from Id Manager. Bailing out of creation of operational "
                    + "vpn interface adjacency " + prefix + "for vpn " + vpnName;
            throw new NullPointerException(error);
        }
        List<String> nextHopList = (adjNextHop != null && !adjNextHop.isEmpty()) ? adjNextHop
                : (nextHopIp == null ? Collections.emptyList() : Collections.singletonList(nextHopIp));

        return new AdjacencyBuilder(nextHop).setLabel(label).setNextHopIpList(nextHopList)
                .setIpAddress(prefix).setVrfId(rd).setKey(new AdjacencyKey(prefix))
                .setAdjacencyType(nextHop.getAdjacencyType())
                .setSubnetGatewayMacAddress(nextHop.getSubnetGatewayMacAddress()).build();
    }
}
