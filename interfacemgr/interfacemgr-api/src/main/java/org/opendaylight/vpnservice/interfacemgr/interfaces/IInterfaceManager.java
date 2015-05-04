package org.opendaylight.vpnservice.interfacemgr.interfaces;

import java.util.List;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;

public interface IInterfaceManager {

    public Long getPortForInterface(String ifName);
    public long getDpnForInterface(String ifName);
    public String getEndpointIpForDpn(long dpnId);
    public List<MatchInfo> getInterfaceIngressRule(String ifName);
    public List<ActionInfo> getInterfaceEgressActions(String ifName);

}