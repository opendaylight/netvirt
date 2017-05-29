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
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;

public interface IBgpManager {


    /**
     * Create one VPN table
     * afi parameter takes one of those values: 1 ( AFI_IP), or 2 ( AFI_IP6)
     * safi parameter takes one of those values : 5 ( SAFI_MPLS_VPN) or 6 ( SAFI_EVPN)
     */
    void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts, LayerType layerType, long afi, long safi)
            throws Exception;

    /**
     * Delete onv VPN table
     * afi parameter takes one of those values: 1 ( AFI_IP), or 2 ( AFI_IP6)
     * safi parameter takes one of those values : 5 ( SAFI_MPLS_VPN) or 6 ( SAFI_EVPN)
     */
    void deleteVrf(String rd, boolean removeFibTable, long afi, long safi);

    /**
     * Adds one or more routes, as many as nexthops provided, in a BGP neighbour. It persists VrfEntry in datastore
     * and sends the BGP message.
     */
    void addPrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni, String gatewayMac,
                          RouteOrigin origin) throws Exception;

    /**
     * Adds a route in a BGP neighbour. It persists the VrfEntry in Datastore and sends the BGP message.
     */
    void addPrefix(String rd, String macAddress, String prefix, String nextHop,
                          VrfEntry.EncapType encapType, int vpnLabel, long l3vni, String gatewayMac,
                          RouteOrigin origin) throws Exception;

    void deletePrefix(String rd, String prefix);

    void setQbgpLog(String fileName, String logLevel);

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

    String getDCGwIP();

    void sendNotificationEvent(String pfx, int code, int subcode);

    void setQbgprestartTS(long qbgprestartTS);

    void bgpRestarted();
}

public enum af_safi implements org.apache.thrift.TEnum {
	SAFI_MPLS_VPN(5),
	SAFI_EVPN(6);

    private final int value;

    private af_safi(int value) {
	this.value = value;
    }


    public enum af_afi implements org.apache.thrift.TEnum {
	AFI_IP(1),
	    AFI_IPV6(2),

	private final int value;

	private af_afi(int value) {
	    this.value = value;
	}

	
