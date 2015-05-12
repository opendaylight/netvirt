package org.opendaylight.vpnmanager.api;

import java.util.Collection;

public interface IVpnManager {
    Collection<Long> getDpnsForVpn(long vpnId);
}
