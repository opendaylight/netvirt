package org.opendaylight.vpnservice;

import java.util.List;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.next.hop.list.L3NextHops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInterface1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInterface1Builder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class VpnUtil {
    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static VpnInterface getVpnInterface(String intfName, String vpnName, VpnInterface1 aug) {
        return new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(intfName)).setVpnInstanceName(vpnName)
                .addAugmentation(VpnInterface1.class, aug).build();
    }

    static VpnInterface1 getVpnInterfaceAugmentation(List<L3NextHops> nextHops) {
        return new VpnInterface1Builder().setL3NextHops(nextHops).build();
    }
}
