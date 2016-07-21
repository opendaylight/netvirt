/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import java.math.BigInteger;
import java.util.List;

public interface IFibManager {
    void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd);
    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd,
                          String localNextHopIp, String remoteNextHopIp);
    void populateFibOnDpn(BigInteger localDpnId, long vpnId, String rd,
                          String localNextHopIp, String remoteNextHopIp);
    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd);
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
}
