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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.TypedReadTransaction;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;

public interface IVpnManager {
    void addExtraRoute(String vpnName, String destination, String nextHop, String rd, @Nullable String routerID,
        Long l3vni, RouteOrigin origin, @Nullable String intfName, @Nullable Adjacency operationalAdj,
        VrfEntry.EncapType encapType, Set<String> prefixListForRefreshFib,
        @NonNull TypedWriteTransaction<Configuration> confTx);

    void delExtraRoute(String vpnName, String destination, String nextHop, String rd, @Nullable String routerID,
        @Nullable String intfName, @NonNull TypedWriteTransaction<Configuration> confTx,
        @NonNull TypedWriteTransaction<Operational> operTx);

    void removePrefixFromBGP(String vpnName, String primaryRd, String extraRouteRd, String vpnInterfaceName,
                                    String prefix, String nextHop, String nextHopTunnelIp, BigInteger dpnId,
                                    TypedWriteTransaction<Configuration> confTx,
                                    TypedWriteTransaction<Operational> operTx);

    boolean isVPNConfigured();

    String getPrimaryRdFromVpnInstance(VpnInstance vpnInstance);

    void addSubnetMacIntoVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
        BigInteger dpnId, TypedWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException;

    void removeSubnetMacFromVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
        BigInteger dpnId, TypedReadWriteTransaction<Configuration> confTx)
        throws ExecutionException, InterruptedException;

    void addRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
        String subnetVpnName, TypedWriteTransaction<Configuration> confTx)
        throws ExecutionException, InterruptedException;

    void removeRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
        String subnetVpnName, TypedReadWriteTransaction<Configuration> confTx)
        throws ExecutionException, InterruptedException;

    void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
        BigInteger dpnId, Uuid extNetworkId);

    void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String routerGwMac,
            BigInteger dpnId, String extInterfaceName, int lportTag);

    void removeArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, Uuid extNetworkId);

    void removeArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps,
            BigInteger dpnId, String extInterfaceName, int lportTag);

    void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag);

    void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn);

    VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName);

    @Deprecated
    String getVpnRd(DataBroker broker, String vpnName);

    String getVpnRd(TypedReadTransaction<Configuration> confTx, String vpnName);

    @Deprecated
    VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp);

    VpnPortipToPort getNeutronPortFromVpnPortFixedIp(TypedReadTransaction<Configuration> confTx, String vpnName,
        String fixedIp);

    void updateRouteTargetsToSubnetAssociation(Set<VpnTarget> routeTargets, String cidr, String vpnName);

    void removeRouteTargetsToSubnetAssociation(Set<VpnTarget> routeTargets, String cidr, String vpnName);

    boolean checkForOverlappingSubnets(Uuid network, List<Subnetmap> subnetmapList, Uuid vpn,
                                       Set<VpnTarget> routeTargets, List<String> failedNwList);

    Set<VpnTarget> getRtListForVpn(String vpnName);
}
