/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;

import java.math.BigInteger;
import java.util.List;

public interface IFibManager {
    void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd,
                             final FutureCallback<List<Void>> callback);
    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd,
                          String localNextHopIp, String remoteNextHopIp,
                          final FutureCallback<List<Void>> callback);
    void populateFibOnDpn(BigInteger localDpnId, long vpnId, String rd,
                          String localNextHopIp, String remoteNextHopIp);
    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd,
                          final FutureCallback<List<Void>> callback);
    List<String> printFibEntries();

    // TODO Feels like this method is not used anywhere
    void addStaticRoute(String prefix, String nextHop, String rd, int label);
    void deleteStaticRoute(String prefix, String nextHop, String rd);
    void setConfTransType(String service, String transportType);
    String getConfTransType();
    boolean isVPNConfigured();
    void writeConfTransTypeConfigDS();
    String getReqTransType();
    String getTransportTypeStr(String tunType);
    void handleRemoteRoute(boolean action, BigInteger localDpnId,
                           BigInteger remoteDpnId, long vpnId,
                           String rd, String destPrefix,
                           String localNextHopIp,
                           String remoteNextHopIP);


    void addOrUpdateFibEntry(DataBroker broker, String rd, String prefix, List<String> nextHopList,
                             int label, RouteOrigin origin, WriteTransaction writeConfigTxn);
    void removeOrUpdateFibEntry(DataBroker broker, String rd, String prefix, String nextHopToRemove, WriteTransaction writeConfigTxn);
    void removeFibEntry(DataBroker broker, String rd, String prefix, WriteTransaction writeConfigTxn);
    void addVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn);
    void removeVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn);
}
