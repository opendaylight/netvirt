package org.opendaylight.netvirt.vpnmanager.utilities;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.api.IVpnUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnUtils implements IVpnUtils {
    private static final Logger LOG = LoggerFactory.getLogger(VpnUtils.class);

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    public VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        return null;//VpnUtil.getVpnInstance(broker, vpnInstanceName);
    }

    public String getVpnRd(DataBroker broker, String vpnName) {
        return null;//VpnUtil.getVpnRd(broker, vpnName);
    }

    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        return null;//VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnName, fixedIp);
    }

    public String getMacAddressForInterface(DataBroker dataBroker, String interfaceName) {
        return null;//InterfaceUtils.getMacAddressForInterface(dataBroker, interfaceName).get();
    }
}
