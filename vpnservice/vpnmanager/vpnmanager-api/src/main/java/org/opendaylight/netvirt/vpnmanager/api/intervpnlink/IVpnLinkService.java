/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import java.util.List;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;

public interface IVpnLinkService {

    /**
     * Leaks a route belonging to a L3VPN to other L3VPN if the neccessary
     * circumstances are met, like there is an InterVpnLink linking both L3VPNs
     * and the corresponding leaking flag is active (bgp/static/connected).
     *
     * @param vpnName Vpn name of the L3VPN that holds the original route
     * @param prefix Prefix/destination of the route
     * @param nextHopList List of nexthops (ECMP) of the route
     * @param label Label of the route to be leaked
     * @param origin Origin of the route (BGP|STATIC|CONNECTED)
     * @param addOrRemove states if the routes must be leaked or withdrawn
     */
    void leakRouteIfNeeded(String vpnName, String prefix, List<String> nextHopList, int label, RouteOrigin origin,
                           int addOrRemove);

    /**
     * Leaks a route from one VPN to another.
     *
     * @param interVpnLink     Reference to the object that holds the info about the link between the 2 VPNs
     * @param srcVpnUuid       UUID of the VPN that has the route that is going to be leaked to the other VPN
     * @param dstVpnUuid       UUID of the VPN that is going to receive the route
     * @param prefix           Prefix of the route
     * @param label            Label of the route in the original VPN
     * @param forcedOrigin     By default, origin for leaked routes is INTERVPN, however it is possible to
     *                         provide a different origin if desired.
     * @param addOrRemove states if the routes must be leaked or withdrawn
     */
    void leakRoute(InterVpnLinkDataComposite interVpnLink, String srcVpnUuid, String dstVpnUuid,
                   String prefix, Long label, RouteOrigin forcedOrigin, int addOrRemove);

    /**
     * Similar to leakRouteIfNeeded but the only requisite to be met is that
     * there exists an InterVpnLink linking both VPNs.
     *
     * @param vpnName Vpn name of the L3VPN that holds the original route
     * @param prefix Prefix/destination of the route
     * @param nextHopList List of nexthops (ECMP) of the route
     * @param label Label of the route to be leaked
     * @param addOrRemove states if the routes must be leaked or withdrawn
     */
    void leakRoute(String vpnName, String prefix, List<String> nextHopList, int label, int addOrRemove);


    /**
     * Checks both L3VPNs linked by the InterVpnLink and performs all the
     * corresponding route leaking between them.
     *
     * @param interVpnLinkDataComposite InterVpnLink to be considered
     */
    void exchangeRoutes(InterVpnLinkDataComposite interVpnLinkDataComposite);

    /**
     * Requests IVpnLinkService to take care of those static routes that point
     * to the specified InterVpnLink and that may be configured in any Neutron
     * Router.
     *
     * @param interVpnLink InterVpnLink to be considered.
     */
    void handleStaticRoutes(InterVpnLinkDataComposite interVpnLink);
}
