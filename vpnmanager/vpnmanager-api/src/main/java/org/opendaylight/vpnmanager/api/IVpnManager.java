package org.opendaylight.vpnmanager.api;

import java.util.Collection;

import org.opendaylight.fibmanager.api.IFibManager;

public interface IVpnManager {
    Collection<Long> getDpnsForVpn(long vpnId);
    void setFibService(IFibManager fibManager);
}
