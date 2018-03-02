/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.api;

import java.util.Collection;
import java.util.List;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;

public interface IBgpManager {

    /** Create one VPN Context per address-family.
     * VPN contexts apply to MPLS or VXLAN overlays.
     * Passing IPv4 or IPv6 will create VPN context for MPLS, with IPv4 or IPv6 (or both)
     * If L2VPN is passed as parameter, then IPv4 EVPN will be set too.
     * @param rd is the route distinguisher to used for this vrf for the VPN.
     * @param importRts the import rd(s) for this vrf
     * @param exportRts the export rd(s) for this vrf
     * @param addressFamily  is used to pass the nature of the VPN context : IPv4, IPv6, or EVPN.
     */
    void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts, AddressFamily addressFamily);

    /** Delete onv VPN table.
     * VPN contexts apply to MPLS or VXLAN overlays.
     * Passing IPv4 or IPv6 will unset VPN context for MPLS, with IPv4 or IPv6 (or both)
     * If L2VPN is passed as parameter, then IPv4 EVPN will be unset too.
     *
     * @param rd the route distinguisher to define the vrf to delete
     * @param removeFibTable true to remove to fib table
     * @param addressFamily is used to pass the nature of the VPN context : IPv4, IPv6, or EVPN.
     */
    void deleteVrf(String rd, boolean removeFibTable, AddressFamily addressFamily);

    /**
     * Adds one or more routes, as many as nexthops provided, in a BGP neighbour. It persists VrfEntry in datastore
     * and sends the BGP message.
     */
    void addPrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni, String gatewayMac,
                          RouteOrigin origin);

    /**
     * Adds a route in a BGP neighbour. It persists the VrfEntry in Datastore and sends the BGP message.
     */
    void addPrefix(String rd, String macAddress, String prefix, String nextHop,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni, String gatewayMac,
                          RouteOrigin origin);

    void deletePrefix(String rd, String prefix);

    /**
     * Advertises a Prefix to a BGP neighbour, using several nexthops. Only sends the BGP messages, no writing to
     * MD-SAL.
     */
    void advertisePrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) throws Exception;

    /**
     * Advertises a Prefix to a BGP neighbour. Only sends the BGP messages, no writing to MD-SAL.
     */
    void advertisePrefix(String rd, String macAddress, String prefix, String nextHop,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) throws Exception;

    void withdrawPrefix(String rd, String prefix);

    void withdrawPrefixIfPresent(String rd, String prefix);

    String getDCGwIP();

    void sendNotificationEvent(int code, int subcode);

    void setQbgprestartTS(long qbgprestartTS);

    void bgpRestarted();
}

