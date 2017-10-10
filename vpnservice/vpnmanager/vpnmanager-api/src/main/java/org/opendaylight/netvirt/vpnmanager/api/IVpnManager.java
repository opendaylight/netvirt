/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;


public interface IVpnManager {
    void addExtraRoute(String vpnName, String destination, String nextHop,
            String rd, String routerID, int label, RouteOrigin origin);

    void delExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID);

    /**
     * Returns true if the specified VPN exists.
     *
     * @param vpnName it must match against the vpn-instance-name attrib in one of the VpnInstances
     */
    boolean existsVpn(String vpnName);

    boolean isVPNConfigured();

    /**
     * Retrieves the list of DPNs where the specified VPN has footprint.
     *
     * @param vpnInstanceName The name of the Vpn instance
     * @return The list of DPNs
     */
    List<BigInteger> getDpnsOnVpn(String vpnInstanceName);

    String getPrimaryRdFromVpnInstance(VpnInstance vpnInstance);

    void addSubnetMacIntoVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx);

    void removeSubnetMacFromVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx);

    void addRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx);

    void removeRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx);

    void setupArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, Uuid extNetworkId, WriteTransaction writeTx, int addOrRemove);

    void setupArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String routerGwMac,
            BigInteger dpnId, long vpnId, String extInterfaceName, int lportTag, WriteTransaction writeTx,
            int addOrRemove);

    void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag);

    void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn);

    List<MatchInfoBase> getEgressMatchesForVpn(String vpnName);

    VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName);

    String getVpnRd(DataBroker broker, String vpnName);

    VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp);
}
