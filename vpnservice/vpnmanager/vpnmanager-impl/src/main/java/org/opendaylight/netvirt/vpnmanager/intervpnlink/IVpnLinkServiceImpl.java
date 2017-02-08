/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IVpnLinkServiceImpl implements IVpnLinkService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(IVpnLinkServiceImpl.class);

    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;

    // A couple of listener in order to maintain the InterVpnLink cache
    private InterVpnLinkCacheFeeder ivpnLinkCacheFeeder;
    private InterVpnLinkStateCacheFeeder ivpnLinkStateCacheFeeder;


    public IVpnLinkServiceImpl(final DataBroker dataBroker, final IdManagerService idMgr, final IBgpManager bgpMgr,
                               final IFibManager fibMgr) {
        this.dataBroker = dataBroker;
        this.idManager = idMgr;
        this.bgpManager = bgpMgr;
        this.fibManager = fibMgr;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        InterVpnLinkCache.createInterVpnLinkCaches(dataBroker);
        ivpnLinkCacheFeeder = new InterVpnLinkCacheFeeder(dataBroker);
        ivpnLinkStateCacheFeeder = new InterVpnLinkStateCacheFeeder(dataBroker);
    }

    @Override
    public void close() throws Exception {
        InterVpnLinkCache.destroyCaches();
    }


    @Override
    public void leakRouteIfNeeded(String vpnName, String prefix, List<String> nextHopList, int label,
                                  RouteOrigin origin, int addOrRemove) {

        Optional<InterVpnLinkDataComposite> optIVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if ( !optIVpnLink.isPresent() ) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        InterVpnLinkDataComposite ivpnLink = optIVpnLink.get();
        if ( addOrRemove == NwConstants.ADD_FLOW && !ivpnLink.isActive() ) {
            // Note: for the removal case it is not necessary that ivpnlink is ACTIVE
            LOG.debug("Route to {} in VPN {} cannot be leaked because InterVpnLink {} is not ACTIVE",
                      prefix, vpnName, ivpnLink.getInterVpnLinkName());
            return;
        }

        switch (origin) {
            case BGP:
                if (!ivpnLink.isBgpRoutesLeaking() ) {
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
                if (!ivpnLink.isStaticRoutesLeaking() ) {
                    LOG.debug("Static route to {} not leaked because static-routes-leaking is disabled", prefix);
                    return;
                }
                leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
                break;
            case CONNECTED:
                if (!ivpnLink.isConnectedRoutesLeaking() ) {
                    LOG.debug("Connected route to {} not leaked because connected-routes-leaking is disabled", prefix);
                    return;
                }
                leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
                break;
            default:
                LOG.warn("origin {} not considered in Route-leaking", origin.getValue());
        }

    }

    private void leakRoute(String vpnName, String prefix, List<String> nextHopList, int label, int addOrRemove) {
        LOG.trace("leakRoute: vpnName={}  prefix={}  nhList={}  label={}", vpnName, prefix, nextHopList, label);
        Optional<InterVpnLinkDataComposite> optIVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if ( !optIVpnLink.isPresent() ) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        leakRoute(optIVpnLink.get(), vpnName, prefix, nextHopList, label, addOrRemove);
    }

    private void leakRoute(InterVpnLinkDataComposite interVpnLink, String vpnName, String prefix,
                           List<String> nextHopList, int label, int addOrRemove) {

        String dstVpnName = interVpnLink.getOtherVpnName(vpnName);

        LOG.trace("leakingRoute: from VPN={} to VPN={}: prefix={}  nhList={}  label={}",
                  vpnName, dstVpnName, prefix, nextHopList, label);

        // For leaking, we need the InterVpnLink to be active.
        if ( addOrRemove == NwConstants.ADD_FLOW && !interVpnLink.isActive()) {
            LOG.warn("Cannot leak route [prefix={}, label={}] from VPN {} to VPN {} because "
                     + "InterVpnLink {} is not active",
                     prefix, label, vpnName, dstVpnName, interVpnLink.getInterVpnLinkName());
            return;
        }

        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnName);
        if ( addOrRemove == NwConstants.ADD_FLOW ) {
            LOG.debug("Leaking route (prefix={}, nexthop={}) from Vpn={} to Vpn={} (RD={})",
                      prefix, nextHopList, vpnName, dstVpnName, dstVpnRd);
            String key = dstVpnRd + VpnConstants.SEPARATOR + prefix;
            long leakedLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, key);
            String leakedNexthop = interVpnLink.getEndpointIpAddr(vpnName);
            fibManager.addOrUpdateFibEntry(dataBroker, dstVpnRd, null /*macAddress*/, prefix,
                                           Collections.singletonList(leakedNexthop), VrfEntry.EncapType.Mplsgre,
                                           (int) leakedLabel, 0 /*l3vni*/, null /*gatewayMacAddress*/,
                                           RouteOrigin.INTERVPN, null /*writeConfigTxn*/);
            List<String> ivlNexthops =
                interVpnLink.getEndpointDpnsByVpnName(dstVpnName).stream()
                            .map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                            .collect(Collectors.toList());
            bgpManager.advertisePrefix(dstVpnRd, prefix, ivlNexthops, (int) leakedLabel);
        } else {
            LOG.debug("Removing leaked route to {} from VPN {}", prefix, dstVpnName);
            fibManager.removeFibEntry(dataBroker, dstVpnRd, prefix, null /*writeConfigTxn*/);
            bgpManager.withdrawPrefix(dstVpnRd, prefix);
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
        Predicate<VpnMap> isExternalVpn =
            (vpnMap) -> vpnMap.getRouterId() != null
                        && ! vpnMap.getVpnId().getValue().equalsIgnoreCase(vpnMap.getRouterId().getValue());

        return optVpnMaps.get().getVpnMap().stream()
                                           .filter(isExternalVpn)
                                           .collect(Collectors.toMap(v -> v.getRouterId().getValue(),
                                               v -> v.getVpnId().getValue()));
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
        InstanceIdentifier<Routers> routersIid = InstanceIdentifier.builder(Neutron.class).child(Routers.class).build();
        Optional<Routers> routerOpData = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routersIid);
        if ( !routerOpData.isPresent() ) {

            return;
        }
        List<Router> routers = routerOpData.get().getRouter();
        for ( Router router : routers ) {
            String vpnId = routerXL3VpnMap.get(router.getUuid().getValue());
            if ( vpnId == null ) {
                LOG.warn("Could not find suitable VPN for router {}", router.getUuid());
                continue; // with next router
            }
            List<Routes> routerRoutes = router.getRoutes();
            if ( routerRoutes != null ) {
                for ( Routes route : routerRoutes ) {
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
    private void handleStaticRoute(String vpnId, Routes route, InterVpnLinkDataComposite ivpnLink) {

        IpAddress nhIpAddr = route.getNexthop();
        String routeNextHop = (nhIpAddr.getIpv4Address() != null) ? nhIpAddr.getIpv4Address().getValue()
                                                                  : nhIpAddr.getIpv6Address().getValue();
        String destination = String.valueOf(route.getDestination().getValue());

        // is nexthop the other endpoint's IP
        String otherEndpoint = ivpnLink.getOtherEndpoint(vpnId);
        if ( !routeNextHop.equals(otherEndpoint) ) {
            LOG.debug("VPN {}: Route to {} nexthop={} points to an InterVpnLink endpoint, but its not "
                      + "the other endpoint. Other endpoint is {}",
                      vpnId, destination, routeNextHop, otherEndpoint);
            return;
        }

        // Lets work: 1) write Fibentry, 2) advertise to BGP and 3) check if it must be leaked
        String vpnRd = VpnUtil.getVpnRd(dataBroker, vpnId);
        if ( vpnRd == null ) {
            LOG.warn("Could not find Route-Distinguisher for VpnName {}", vpnId);
            return;
        }

        int label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                        VpnUtil.getNextHopLabelKey(vpnId, destination));

        InterVpnLinkUtil.handleStaticRoute(ivpnLink, vpnId, destination, routeNextHop, label,
                                           dataBroker, fibManager, bgpManager);
    }
}
