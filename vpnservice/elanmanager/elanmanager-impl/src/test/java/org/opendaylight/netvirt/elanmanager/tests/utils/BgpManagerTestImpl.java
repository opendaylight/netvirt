/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import java.util.Collections;
import java.util.List;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public abstract class BgpManagerTestImpl implements IBgpManager {

    DataBroker dataBroker;

    public static BgpManagerTestImpl newInstance(DataBroker dataBroker) {
        BgpManagerTestImpl instance = Mockito.mock(BgpManagerTestImpl.class, realOrException());
        instance.dataBroker = dataBroker;
        return instance;
    }

    @Override
    public void advertisePrefix(String rd, String macAddress, String prefix, List<String> nextHopList,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) throws Exception {
        addPrefix(rd, macAddress, prefix, nextHopList,
                encapType, vpnLabel, l3vni, l2vni, gatewayMac);
    }

    @Override
    public void advertisePrefix(String rd, String macAddress, String prefix, String nextHop,
                                VrfEntry.EncapType encapType, long vpnLabel, long l3vni, long l2vni,
                                String gatewayMac) throws Exception {
        addPrefix(rd, macAddress, prefix, Collections.singletonList(nextHop), encapType,
                vpnLabel, l3vni, l2vni, gatewayMac);
    }

    public void addPrefix(String rd, String macAddress, String pfx, List<String> nhList,
                            VrfEntry.EncapType encapType, long lbl, long l3vni, long l2vni, String gatewayMac)
                            throws TransactionCommitFailedException {
        for (String nh : nhList) {
            Ipv4Address nexthop = nh != null ? new Ipv4Address(nh) : null;
            Long label = lbl;
            InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                    .child(Networks.class, new NetworksKey(pfx, rd)).build();
            NetworksBuilder networksBuilder = new NetworksBuilder().setRd(rd).setPrefixLen(pfx).setNexthop(nexthop)
                    .setLabel(label).setEthtag(0L);
            buildVpnEncapSpecificInfo(networksBuilder, encapType, label, l3vni, l2vni, macAddress, gatewayMac);
            ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
            tx.put(LogicalDatastoreType.CONFIGURATION, iid, networksBuilder.build(), true);
            tx.submit().checkedGet();
        }
    }

    private static void buildVpnEncapSpecificInfo(NetworksBuilder builder, VrfEntry.EncapType encapType, long label,
                                                  long l3vni, long l2vni, String macAddress, String gatewayMac) {
        if (encapType.equals(VrfEntry.EncapType.Mplsgre)) {
            builder.setLabel(label).setBgpControlPlaneType(BgpControlPlaneType.PROTOCOLL3VPN)
                    .setEncapType(EncapType.GRE);
        } else {
            builder.setL3vni(l3vni).setL2vni(l2vni).setMacaddress(macAddress).setRoutermac(gatewayMac)
                    .setBgpControlPlaneType(BgpControlPlaneType.PROTOCOLEVPN).setEncapType(EncapType.VXLAN);
        }
    }
}
