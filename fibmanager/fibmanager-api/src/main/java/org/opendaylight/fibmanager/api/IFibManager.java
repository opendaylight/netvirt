package org.opendaylight.fibmanager.api;

import java.math.BigInteger;

public interface IFibManager {
    void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd);
    void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd);
}
