package org.opendaylight.vpnservice;

import java.util.List;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class VpnUtil {
    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static VpnInterface getVpnInterface(String intfName, String vpnName, Adjacencies aug) {
        return new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(intfName)).setVpnInstanceName(vpnName)
                .addAugmentation(Adjacencies.class, aug).build();
    }

    static Adjacencies getVpnInterfaceAugmentation(List<Adjacency> nextHops) {
        return new AdjacenciesBuilder().setAdjacency(nextHops).build();
    }
}
