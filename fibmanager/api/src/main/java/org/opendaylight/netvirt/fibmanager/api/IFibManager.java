/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import com.google.common.util.concurrent.FutureCallback;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

public interface IFibManager {
    void populateFibOnNewDpn(Uint64 dpnId, Uint32 vpnId, String rd,
                             @Nullable FutureCallback<List<?>> callback);

    void cleanUpExternalRoutesOnDpn(Uint64 dpnId, Uint32 vpnId, String rd,
                                    String localNextHopIp, String remoteNextHopIp);

    void populateExternalRoutesOnDpn(Uint64 localDpnId, Uint32 vpnId, String rd,
                                     String localNextHopIp, String remoteNextHopIp);

    void cleanUpDpnForVpn(Uint64 dpnId, Uint32 vpnId, String rd,
                          @Nullable FutureCallback<List<?>> callback);

    void setConfTransType(String service, String transportType);

    String getConfTransType();

    boolean isVPNConfigured();

    void writeConfTransTypeConfigDS();

    String getReqTransType();

    String getTransportTypeStr(String tunType);

    void manageRemoteRouteOnDPN(boolean action,
                                Uint64 localDpnId,
                                Uint32 vpnId,
                                String rd,
                                String destPrefix,
                                String destTepIp,
                                Uint32 label);

    void addOrUpdateFibEntry(String rd, @Nullable String macAddress, String prefix, List<String> nextHopList,
                             VrfEntry.EncapType encapType, Uint32 label, Uint32 l3vni, @Nullable String gwMacAddress,
                             @Nullable String parentVpnRd, RouteOrigin origin,  String vpnInterfaceName,
                             String eventSource, @Nullable TypedWriteTransaction<Configuration> writeConfigTxn);

    void addFibEntryForRouterInterface(String rd, String prefix,
                                       RouterInterface routerInterface, Uint32 label,
                                       TypedWriteTransaction<Configuration> writeConfigTxn);

    void removeOrUpdateFibEntry(String rd, String prefix, String nextHopToRemove, String vpnInterfaceName,
                                String eventSource, TypedWriteTransaction<Configuration> writeConfigTxn);

    void removeFibEntry(String rd, String prefix, String vpnInterfaceName, String eventSource,
                        @Nullable TypedWriteTransaction<Configuration> writeConfigTxn);

    void updateRoutePathForFibEntry(String rd, String prefix, String nextHop,
                                    Uint32 label, boolean nextHopAdd, String vpnInterfaceName, String eventSource,
                                    TypedWriteTransaction<Configuration> writeConfigTxn);

    void addVrfTable(String rd, WriteTransaction writeConfigTxn);

    void removeVrfTable(String rd, TypedWriteTransaction<Configuration> writeConfigTxn);

    void removeInterVPNLinkRouteFlows(String interVpnLinkName,
                                      boolean isVpnFirstEndPoint,
                                      VrfEntry vrfEntry);

    boolean checkFibEntryExist(DataBroker broker, String rd, String prefix, String nextHopIp);

    void programDcGwLoadBalancingGroup(Uint64 dpnId,
            String destinationIp, int addRemoveOrUpdate, boolean isTunnelUp,
                                       Class<? extends TunnelTypeBase> tunnelType);

    void refreshVrfEntry(String rd, String prefix);
}
