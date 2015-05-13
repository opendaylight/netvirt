package org.opendaylight.fibmanager.api;

public interface IFibManager {
    void populateFibOnNewDpn(long dpnId, long vpnId, String rd);
    void cleanUpDpnForVpn(long dpnId, long vpnId, String rd);
}
