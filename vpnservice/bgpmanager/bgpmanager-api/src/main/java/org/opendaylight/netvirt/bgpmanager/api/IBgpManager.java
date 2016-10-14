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

    void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts, LayerType layerType)
            throws Exception;

    void deleteVrf(String rd, boolean removeFibTable);

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
                                VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                                String gatewayMac) throws Exception;

    /**
     * Advertises a Prefix to a BGP neighbour. Only sends the BGP messages, no writing to MD-SAL.
     */
    void advertisePrefix(String rd, String macAddress, String prefix, String nextHop,
                                VrfEntry.EncapType encapType, int vpnLabel, long l3vni,
                                String gatewayMac) throws Exception;

    void withdrawPrefix(String rd, String prefix);

    String getDCGwIP();

    void sendNotificationEvent(String pfx, int code, int subcode);

    void setQbgprestartTS(long qbgprestartTS);

    void bgpRestarted();
}
