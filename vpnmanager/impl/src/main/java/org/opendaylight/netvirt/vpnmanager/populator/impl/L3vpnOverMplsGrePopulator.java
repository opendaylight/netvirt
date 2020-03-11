/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import static java.util.Collections.singletonList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L3vpnOverMplsGrePopulator extends L3vpnPopulator {
    private final IdManagerService idManager;
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnOverMplsGrePopulator.class);

    @Inject
    public L3vpnOverMplsGrePopulator(DataBroker dataBroker, IBgpManager bgpManager, IFibManager fibManager,
            IdManagerService idManager, VpnUtil vpnUtil) {
        super(dataBroker, bgpManager, fibManager, vpnUtil);
        this.idManager = idManager;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        L3vpnRegistry.registerL3vpnPopulator(VrfEntry.EncapType.Mplsgre, this);
    }

    @PreDestroy
    public void close() {
        LOG.trace("L3vpnOverMplsGrePopulator Closed");
    }

    @Override
    public void populateFib(L3vpnInput input, String vpnInterface, String source,
                            TypedWriteTransaction<Configuration> writeConfigTxn) {
        if (input.getRouteOrigin() == RouteOrigin.CONNECTED) {
            LOG.info("populateFib : Found SubnetRoute for subnet {} rd {}", input.getSubnetIp(), input.getPrimaryRd());
            addSubnetRouteFibEntry(input);
            return;
        }
        Adjacency nextHop = input.getNextHop();
        Uint32 label = nextHop.getLabel();
        String vpnName = input.getVpnName();
        String primaryRd = input.getPrimaryRd();
        String rd = input.getRd();
        String nextHopIp = input.getNextHopIp();
        VrfEntry.EncapType encapType = input.getEncapType();
        LOG.info("populateFib : Found Interface Adjacency with prefix {} rd {}", nextHop.getIpAddress(), primaryRd);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
        String nextHopIpAddress = nextHop.getIpAddress(); // it is a valid case for nextHopIpAddress to be null
        // Not advertising the prefix to BGP for InternalVpn (where rd is vpnName),
        // transparentInternetVpn (where rd is Network name)
        // and internalVpnForExtraRoute (where rd is DpnId)
        if (VpnUtil.isEligibleForBgp(primaryRd, input.getVpnName(), input.getDpnId(), input.getNetworkName())) {
            // the DpnId is set as rd in case of extra routes present in router based VPN
            addToLabelMapper(label, input.getDpnId(), nextHopIpAddress,
                    singletonList(nextHopIp), vpnId, input.getInterfaceName(), null,false,
                    primaryRd);
            Objects.requireNonNull(input.getRouteOrigin(), "RouteOrigin is mandatory");
            addPrefixToBGP(rd, primaryRd, null /*macAddress*/, nextHopIpAddress, nextHopIp, encapType,
                    label, Uint32.ZERO /*l3vni*/, input.getGatewayMac(), input.getRouteOrigin(), vpnInterface,
                    source, writeConfigTxn);
            //TODO: ERT - check for VPNs importing my route
            for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                String vpnRd = vpn.getVrfId();
                if (vpnRd != null) {
                    fibManager.addOrUpdateFibEntry(vpnRd, null /*macAddress*/,
                            nextHopIpAddress, Arrays.asList(nextHopIp), encapType, label,
                            Uint32.ZERO /*l3vni*/, input.getGatewayMac(), primaryRd, RouteOrigin.SELF_IMPORTED,
                            vpnInterface, source, writeConfigTxn);
                    LOG.info("populateFib: Exported route with rd {} prefix {} nexthop {} label {}"
                            + " to VPN {} for interface {} on dpn {}", vpnRd, nextHop.getIpAddress(), nextHopIp, label,
                            vpn, input.getInterfaceName(), input.getDpnId());
                }
            }
        } else {
            // ### add FIB route directly
            fibManager.addOrUpdateFibEntry(vpnName, null /*macAddress*/,
                    nextHopIpAddress, Arrays.asList(nextHopIp), encapType, label,
                    Uint32.ZERO /*l3vni*/, input.getGatewayMac(), null /*parentVpnRd*/,
                    input.getRouteOrigin(), vpnInterface, source, writeConfigTxn);
            LOG.info("populateFib: Added internal FIB entry for prefix {} nexthop {} label {}"
                    + " to VPN {} for interface {} on dpn {}", nextHop.getIpAddress(), nextHopIp, label, vpnName,
                    input.getInterfaceName(), input.getDpnId());
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
        Uint32 label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME, VpnUtil.getNextHopLabelKey(primaryRd, prefix));
        if (label.longValue() == VpnConstants.INVALID_LABEL) {
            String error = "Unable to fetch label from Id Manager. Bailing out of creation of operational "
                    + "vpn interface adjacency " + prefix + "for vpn " + vpnName;
            throw new NullPointerException(error);
        }
        List<String> nextHopList = adjNextHop != null && !adjNextHop.isEmpty() ? adjNextHop
                : nextHopIp == null ? Collections.emptyList() : singletonList(nextHopIp);

        return new AdjacencyBuilder(nextHop).setLabel(label).setNextHopIpList(nextHopList)
                .setIpAddress(prefix).setVrfId(rd).withKey(new AdjacencyKey(prefix))
                .setAdjacencyType(nextHop.getAdjacencyType())
                .setSubnetGatewayMacAddress(nextHop.getSubnetGatewayMacAddress()).build();
    }
}
