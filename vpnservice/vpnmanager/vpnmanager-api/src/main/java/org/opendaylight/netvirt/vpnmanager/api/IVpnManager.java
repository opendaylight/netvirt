/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import java.math.BigInteger;
import java.util.List;

public interface IVpnManager {
    void setFibManager(IFibManager fibManager);
    void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label, RouteOrigin origin);
    void delExtraRoute(String destination, String nextHop, String rd, String routerID);

    /**
     * Returns true if the specified VPN exists
     *
     * @param vpnName it must match against the vpn-instance-name attrib in one of the VpnInstances
     *
     * @return
     */
    boolean existsVpn(String vpnName);
    boolean isVPNConfigured();

    long getArpCacheTimeoutMillis();
    /**
     * Retrieves the list of DPNs where the specified VPN has footprint
     *
     * @param vpnInstanceName The name of the Vpn instance
     * @return The list of DPNs
     */
    List<BigInteger> getDpnsOnVpn(String vpnInstanceName);
    void setupSubnetMacIntoVpnInstance(String vpnName, String srcMacAddress, BigInteger dpnId,
                                       WriteTransaction writeTx, int addOrRemove);
}
