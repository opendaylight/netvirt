/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IVpnLinkServiceImpl implements IVpnLinkService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(IVpnLinkServiceImpl.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final InterVpnLinkCache interVpnLinkCache;

    @Inject
    public IVpnLinkServiceImpl(final DataBroker dataBroker, final IdManagerService idMgr, final IBgpManager bgpMgr,
                               final IFibManager fibMgr, final InterVpnLinkCache interVpnLinkCache) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idMgr;
        this.bgpManager = bgpMgr;
        this.fibManager = fibMgr;
        this.interVpnLinkCache = interVpnLinkCache;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
    }

    @Override
    public void leakRoute(String vpnName, String prefix, List<String> nextHopList, int label, int addOrRemove) {
        LOG.trace("leakRoute: vpnName={}  prefix={}  nhList={}  label={}", vpnName, prefix, nextHopList, label);
        Optional<InterVpnLinkDataComposite> optIVpnLink = interVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if (!optIVpnLink.isPresent()) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        leakRoute(optIVpnLink.get(), vpnName, prefix, nextHopList, label, addOrRemove);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void leakRoute(InterVpnLinkDataComposite interVpnLink, String vpnName, String prefix,
                           List<String> nextHopList, int label, int addOrRemove) {

        String dstVpnName = interVpnLink.getOtherVpnName(vpnName);

        LOG.trace("leakingRoute: from VPN={} to VPN={}: prefix={}  nhList={}  label={}",
                  vpnName, dstVpnName, prefix, nextHopList, label);

        // For leaking, we need the InterVpnLink to be active.
        if (addOrRemove == NwConstants.ADD_FLOW && !interVpnLink.isActive()) {
            LOG.warn("Cannot leak route [prefix={}, label={}] from VPN {} to VPN {} because "
                     + "InterVpnLink {} is not active",
                     prefix, label, vpnName, dstVpnName, interVpnLink.getInterVpnLinkName());
            return;
        }

        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnName);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Leaking route (prefix={}, nexthop={}) from Vpn={} to Vpn={} (RD={})",
                      prefix, nextHopList, vpnName, dstVpnName, dstVpnRd);
            String key = dstVpnRd + VpnConstants.SEPARATOR + prefix;
            long leakedLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, key);
            String leakedNexthop = interVpnLink.getEndpointIpAddr(vpnName);
            fibManager.addOrUpdateFibEntry(dstVpnRd, null /*macAddress*/, prefix,
                                           Collections.singletonList(leakedNexthop), VrfEntry.EncapType.Mplsgre,
                                           (int) leakedLabel, 0 /*l3vni*/, null /*gatewayMacAddress*/,
                                           null /*parentVpnRd*/, RouteOrigin.INTERVPN, null /*writeConfigTxn*/);

            List<String> ivlNexthops =
                interVpnLink.getEndpointDpnsByVpnName(dstVpnName).stream()
                            .map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                            .collect(Collectors.toList());
            try {
                bgpManager.advertisePrefix(dstVpnRd, null /*macAddress*/, prefix, ivlNexthops,
                                           VrfEntry.EncapType.Mplsgre, (int)leakedLabel, 0 /*l3vni*/, 0 /*l2vni*/,
                                           null /*gwMacAddress*/);
            } catch (Exception e) {
                LOG.error("Exception while advertising prefix {} on vpnRd {} for intervpn link", prefix, dstVpnRd, e);
            }
        } else {
            LOG.debug("Removing leaked route to {} from VPN {}", prefix, dstVpnName);
            fibManager.removeFibEntry(dstVpnRd, prefix, null /*writeConfigTxn*/);
            bgpManager.withdrawPrefix(dstVpnRd, prefix);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void leakRoute(InterVpnLinkDataComposite interVpnLink, String srcVpnUuid, String dstVpnUuid,
                          String prefix, Long label, RouteOrigin forcedOrigin) {
        String ivpnLinkName = interVpnLink.getInterVpnLinkName();
        // The source VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.isVpnLinked(srcVpnUuid),
                                    "The source VPN {} does not participate in the interVpnLink {}",
                                    srcVpnUuid, ivpnLinkName);
        // The destination VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.isVpnLinked(dstVpnUuid),
                                    "The destination VPN {} does not participate in the interVpnLink {}",
                                    dstVpnUuid, ivpnLinkName);

        String endpointIp = interVpnLink.getOtherEndpointIpAddr(dstVpnUuid);
        String leakedOrigin = forcedOrigin != null ? forcedOrigin.getValue() : RouteOrigin.INTERVPN.getValue();
        FibHelper.buildRoutePath(endpointIp, label);
        VrfEntry newVrfEntry =
            new VrfEntryBuilder().withKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                                 .setRoutePaths(Collections.singletonList(FibHelper.buildRoutePath(endpointIp, label)))
                                 .setOrigin(leakedOrigin).build();

        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
        InstanceIdentifier<VrfEntry> newVrfEntryIid =
            InstanceIdentifier.builder(FibEntries.class)
                              .child(VrfTables.class, new VrfTablesKey(dstVpnRd))
                              .child(VrfEntry.class, new VrfEntryKey(newVrfEntry.getDestPrefix()))
                              .build();
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
            tx.put(LogicalDatastoreType.CONFIGURATION, newVrfEntryIid, newVrfEntry)),
                LOG, "Error adding VRF entry {}", newVrfEntry);

        // Finally, route is advertised it to the DC-GW. But while in the FibEntries the nexthop is the other
        // endpoint's IP, in the DC-GW the nexthop for those prefixes are the IPs of those DPNs where the target
        // VPN has been instantiated
        List<BigInteger> srcDpnList = interVpnLink.getEndpointDpnsByVpnName(srcVpnUuid);
        List<String> nexthops =
            srcDpnList.stream().map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                               .collect(Collectors.toList());

        LOG.debug("Advertising route in VPN={} [prefix={} label={}  nexthops={}] to DC-GW",
                  dstVpnRd, newVrfEntry.getDestPrefix(), label.intValue(), nexthops);
        try {
            bgpManager.advertisePrefix(dstVpnRd, null /*macAddress*/, prefix, nexthops,
                                       VrfEntry.EncapType.Mplsgre, label.intValue(), 0 /*l3vni*/, 0 /*l2vni*/,
                                       null /*gwMacAddress*/);
        } catch (Exception e) {
            LOG.error("Exception while advertising prefix {} on vpnRd {} for intervpn link", prefix, dstVpnRd, e);
        }
    }

    @Override
    public void leakRouteIfNeeded(String vpnName, String prefix, List<String> nextHopList, int label,
                                  RouteOrigin origin, int addOrRemove) {

        Optional<InterVpnLinkDataComposite> optIVpnLink = interVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if (!optIVpnLink.isPresent()) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        InterVpnLinkDataComposite ivpnLink = optIVpnLink.get();
        if (addOrRemove == NwConstants.ADD_FLOW && !ivpnLink.isActive()) {
            // Note: for the removal case it is not necessary that ivpnlink is ACTIVE
            LOG.debug("Route to {} in VPN {} cannot be leaked because InterVpnLink {} is not ACTIVE",
                      prefix, vpnName, ivpnLink.getInterVpnLinkName());
            return;
        }

        switch (origin) {
            case BGP:
                if (!ivpnLink.isBgpRoutesLeaking()) {
                    LOG.debug("BGP route to {} not leaked because bgp-routes-leaking is disabled", prefix);
                    return;
                }
                leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
                break;
            case STATIC:
                /* NOTE: There are 2 types of static routes depending on the next hop:
                    + static route when next hop is a VM, the DC-GW or a DPNIP
                    + static route when next hop is an Inter-VPN Link
                 Only the 1st type should be considered since the 2nd has a special treatment */
                if (!ivpnLink.isStaticRoutesLeaking()) {
                    LOG.debug("Static route to {} not leaked because static-routes-leaking is disabled", prefix);
                    return;
                }
                leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
                break;
            case CONNECTED:
                if (!ivpnLink.isConnectedRoutesLeaking()) {
                    LOG.debug("Connected route to {} not leaked because connected-routes-leaking is disabled", prefix);
                    return;
                }
                leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
                break;
            default:
                LOG.warn("origin {} not considered in Route-leaking", origin.getValue());
        }

    }

    @Override
    public void exchangeRoutes(InterVpnLinkDataComposite ivpnLink) {
        if (!ivpnLink.isComplete()) {
            return;
        }

        // The type of routes to exchange depend on the leaking flags that have been activated
        List<RouteOrigin> originsToConsider = new ArrayList<>();
        if (ivpnLink.isBgpRoutesLeaking()) {
            originsToConsider.add(RouteOrigin.BGP);
        }
        if (ivpnLink.isStaticRoutesLeaking()) {
            originsToConsider.add(RouteOrigin.STATIC);
        }
        if (ivpnLink.isConnectedRoutesLeaking()) {
            originsToConsider.add(RouteOrigin.CONNECTED);
        }

        String vpn1Uuid = ivpnLink.getFirstEndpointVpnUuid().get();
        String vpn2Uuid = ivpnLink.getSecondEndpointVpnUuid().get();

        if (! originsToConsider.isEmpty()) {
            // 1st Endpoint ==> 2nd endpoint
            leakRoutes(ivpnLink, vpn1Uuid, vpn2Uuid, originsToConsider);


            // 2nd Endpoint ==> 1st endpoint
            leakRoutes(ivpnLink, vpn2Uuid, vpn1Uuid, originsToConsider);
        }

        // Static routes in Vpn1 pointing to Vpn2's endpoint
        leakExtraRoutesToVpnEndpoint(ivpnLink, vpn1Uuid, vpn2Uuid);

        // Static routes in Vpn2 pointing to Vpn1's endpoint
        leakExtraRoutesToVpnEndpoint(ivpnLink, vpn2Uuid, vpn1Uuid);
    }

    /*
     * Checks if there are static routes in Vpn1 whose nexthop is Vpn2's endpoint.
     * Those routes must be leaked to Vpn1.
     *
     * @param vpnLink
     * @param vpn1Uuid
     * @param vpn2Uuid
     */
    private void leakExtraRoutesToVpnEndpoint(InterVpnLinkDataComposite vpnLink, String vpn1Uuid, String vpn2Uuid) {

        String vpn1Rd = VpnUtil.getVpnRd(dataBroker, vpn1Uuid);
        String vpn2Endpoint = vpnLink.getOtherEndpointIpAddr(vpn2Uuid);
        List<VrfEntry> allVpnVrfEntries = VpnUtil.getAllVrfEntries(dataBroker, vpn1Rd);
        for (VrfEntry vrfEntry : allVpnVrfEntries) {
            vrfEntry.getRoutePaths().stream()
                    .filter(routePath -> routePath.getNexthopAddress().equals(vpn2Endpoint))
                    .forEach(routePath -> {
                        // Vpn1 has a route pointing to Vpn2's endpoint. Forcing the leaking of the route will update
                        // the BGP accordingly
                        long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                         VpnUtil.getNextHopLabelKey(vpn1Rd, vrfEntry.getDestPrefix()));
                        if (label == VpnConstants.INVALID_LABEL) {
                            LOG.error("Unable to fetch label from Id Manager. Bailing out of leaking extra routes for "
                                      + "InterVpnLink {} rd {} prefix {}",
                                      vpnLink.getInterVpnLinkName(), vpn1Rd, vrfEntry.getDestPrefix());
                        } else {
                            leakRoute(vpnLink, vpn2Uuid, vpn1Uuid, vrfEntry.getDestPrefix(), label,
                                      RouteOrigin.value(vrfEntry.getOrigin()));
                        }
                    });

        }
    }

    private void leakRoutes(InterVpnLinkDataComposite vpnLink, String srcVpnUuid, String dstVpnUuid,
                            List<RouteOrigin> originsToConsider) {
        String srcVpnRd = VpnUtil.getVpnRd(dataBroker, srcVpnUuid);
        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
        List<VrfEntry> srcVpnRemoteVrfEntries = VpnUtil.getVrfEntriesByOrigin(dataBroker, srcVpnRd, originsToConsider);
        for (VrfEntry vrfEntry : srcVpnRemoteVrfEntries) {
            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                             VpnUtil.getNextHopLabelKey(dstVpnRd, vrfEntry.getDestPrefix()));
            if (label == VpnConstants.INVALID_LABEL) {
                LOG.error("Unable to fetch label from Id Manager. Bailing out of leaking routes for InterVpnLink {} "
                          + "rd {} prefix {}",
                        vpnLink.getInterVpnLinkName(), dstVpnRd, vrfEntry.getDestPrefix());
                continue;
            }
            leakRoute(vpnLink, srcVpnUuid, dstVpnUuid, vrfEntry.getDestPrefix(), label, null /*NotForcedOrigin*/);
        }
    }

    private Map<String, String> buildRouterXL3VPNMap() {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optVpnMaps =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (!optVpnMaps.isPresent()) {
            LOG.info("Could not retrieve VpnMaps object from Configurational DS");
            return new HashMap<>();
        }
        Map<String,String> vmap = new HashMap<String,String>();
        final List<VpnMap> VpnMapList = optVpnMaps.get().getVpnMap();
        for (VpnMap map : VpnMapList) {
            if (map.getRouterIds() == null) {
                continue;
            }
            final List<Uuid> vpnRouterIds = NeutronUtils.getVpnMapRouterIdsListUuid(map.getRouterIds());
            for (Uuid routerId : vpnRouterIds) {
                if (map.getVpnId().getValue().equalsIgnoreCase(routerId.getValue())) {
                    break; // VPN is internal
                }
                vmap.put(routerId.getValue(), map.getVpnId().getValue());
            }
        }
        return vmap;
    }


    @Override
    public void handleStaticRoutes(InterVpnLinkDataComposite ivpnLink) {
        /*
         * Checks if there are any static routes pointing to any of both
         * InterVpnLink's endpoints. Goes through all routers checking if they have
         * a route whose nexthop is an InterVpnLink endpoint
         */

        // Map that corresponds a routerId with the L3VPN that it's been assigned to.
        Map<String, String> routerXL3VpnMap = buildRouterXL3VPNMap();

        // Retrieving all Routers
        InstanceIdentifier<Routers> routersIid = InstanceIdentifier.builder(Neutron.class)
                .child(Routers.class).build();
        Optional<Routers> routerOpData = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routersIid);
        if (!routerOpData.isPresent()) {

            return;
        }
        List<Router> routers = routerOpData.get().getRouter();
        for (Router router : routers) {
            String vpnId = routerXL3VpnMap.get(router.getUuid().getValue());
            if (vpnId == null) {
                LOG.warn("Could not find suitable VPN for router {}", router.getUuid());
                continue; // with next router
            }
            List<Routes> routerRoutes = router.getRoutes();
            if (routerRoutes != null) {
                for (Routes route : routerRoutes) {
                    handleStaticRoute(vpnId, route, ivpnLink);
                }
            }
        }
    }

    /*
     * Takes care of an static route to see if flows related to interVpnLink
     * must be installed in tables 20 and 17
     *
     * @param vpnId Vpn to which the route belongs
     * @param route Route to handle. Will only be considered if its nexthop is the VPN's endpoint IpAddress
     *              at the other side of the InterVpnLink
     * @param iVpnLink
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleStaticRoute(String vpnId, Routes route, InterVpnLinkDataComposite ivpnLink) {

        IpAddress nhIpAddr = route.getNexthop();
        String routeNextHop = nhIpAddr.getIpv4Address() != null ? nhIpAddr.getIpv4Address().getValue()
                                                                  : nhIpAddr.getIpv6Address().getValue();
        String destination = String.valueOf(route.getDestination().getValue());

        // is nexthop the other endpoint's IP
        String otherEndpoint = ivpnLink.getOtherEndpoint(vpnId);
        if (!routeNextHop.equals(otherEndpoint)) {
            LOG.debug("VPN {}: Route to {} nexthop={} points to an InterVpnLink endpoint, but its not "
                      + "the other endpoint. Other endpoint is {}",
                      vpnId, destination, routeNextHop, otherEndpoint);
            return;
        }

        // Lets work: 1) write Fibentry, 2) advertise to BGP and 3) check if it must be leaked
        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnId);
        if (vpnRd == null) {
            LOG.warn("Could not find Route-Distinguisher for VpnName {}", vpnId);
            return;
        }

        int label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                        VpnUtil.getNextHopLabelKey(vpnId, destination));

        try {
            InterVpnLinkUtil.handleStaticRoute(ivpnLink, vpnId, destination, routeNextHop, label,
                                               dataBroker, fibManager, bgpManager);
        } catch (Exception e) {
            LOG.error("Exception while advertising prefix for intervpn link, {}", e);
        }
    }
}
