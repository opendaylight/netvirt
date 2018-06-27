/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import com.google.common.util.concurrent.FutureCallback;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;

public interface IFibManager {
    void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd,
                             FutureCallback<List<Void>> callback);

    void cleanUpExternalRoutesOnDpn(BigInteger dpnId, long vpnId, String rd,
                                    String localNextHopIp, String remoteNextHopIp);

    void populateExternalRoutesOnDpn(BigInteger localDpnId, long vpnId, String rd,
                                     String localNextHopIp, String remoteNextHopIp);

    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd,
                          FutureCallback<List<Void>> callback);

    void setConfTransType(String service, String transportType);

    String getConfTransType();

    boolean isVPNConfigured();

    void writeConfTransTypeConfigDS();

    String getReqTransType();

    String getTransportTypeStr(String tunType);

    void manageRemoteRouteOnDPN(boolean action,
                                BigInteger localDpnId,
                                long vpnId,
                                String rd,
                                String destPrefix,
                                String destTepIp,
                                long label);

    void addOrUpdateFibEntry(String rd, String macAddress, String prefix, List<String> nextHopList,
                             VrfEntry.EncapType encapType, long label, long l3vni, String gwMacAddress,
                             String parentVpnRd, RouteOrigin origin, WriteTransaction writeConfigTxn);

    void addFibEntryForRouterInterface(String rd, String prefix,
                                       RouterInterface routerInterface, long label, WriteTransaction writeConfigTxn);

    void removeOrUpdateFibEntry(String rd, String prefix, String nextHopToRemove,
                                WriteTransaction writeConfigTxn);

    void removeFibEntry(String rd, String prefix, WriteTransaction writeConfigTxn);

    void updateRoutePathForFibEntry(String rd, String prefix, String nextHop,
                                    long label, boolean nextHopAdd, WriteTransaction writeConfigTxn);

    void addVrfTable(String rd, WriteTransaction writeConfigTxn);

    void removeVrfTable(String rd, TypedWriteTransaction writeConfigTxn);

    void removeInterVPNLinkRouteFlows(String interVpnLinkName,
                                      boolean isVpnFirstEndPoint,
                                      VrfEntry vrfEntry);

    void programDcGwLoadBalancingGroup(List<String> availableDcGws, BigInteger dpnId, String destinationIp,
                                       int addRemoveOrUpdate, boolean isTunnelUp,
                                       Class<? extends TunnelTypeBase> tunnelType);

    void refreshVrfEntry(String rd, String prefix);
}
