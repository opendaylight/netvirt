package org.opendaylight.vpnmanager.api;

import java.math.BigInteger;

import java.util.Collection;
import org.opendaylight.fibmanager.api.IFibManager;

public interface IVpnManager {
    Collection<BigInteger> getDpnsForVpn(long vpnId);
    void setFibService(IFibManager fibManager);
}
