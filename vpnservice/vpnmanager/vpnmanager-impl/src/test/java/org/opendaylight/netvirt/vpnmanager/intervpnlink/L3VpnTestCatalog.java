/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opendaylight.netvirt.vpnmanager.VpnOperDsUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4Family;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;

/**
 * Gathers a collections of 'fake' L3VPN objects that can be used in JUnits
 */
public class L3VpnTestCatalog {

    static class L3VpnComposite {
        VpnInstance vpnCfgData;
        VpnInstanceOpDataEntry vpnOpData;

        L3VpnComposite(VpnInstance vpnInst, VpnInstanceOpDataEntry vpnOpData) {
            this.vpnCfgData = vpnInst;
            this.vpnOpData = vpnOpData;
        }
    }

    static VpnTargets makeVpnTargets(List<String> irts, List<String> erts) {
        List<String> commonRT = new ArrayList<>(irts);
        commonRT.retainAll(erts);
        List<String> pureIrts = new ArrayList<>(irts);
        pureIrts.removeAll(commonRT);
        List<String> pureErts = new ArrayList<>(erts);
        pureErts.removeAll(commonRT);
        List<VpnTarget> vpnTargetList = new ArrayList<>();
        vpnTargetList.addAll(makeVpnTargetsByType(commonRT, VpnTarget.VrfRTType.Both));
        vpnTargetList.addAll(makeVpnTargetsByType(pureIrts, VpnTarget.VrfRTType.ImportExtcommunity));
        vpnTargetList.addAll(makeVpnTargetsByType(pureErts, VpnTarget.VrfRTType.ExportExtcommunity));
        return new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();
    }

    static List<VpnTarget> makeVpnTargetsByType(List<String> rtList, VpnTarget.VrfRTType type) {
        return rtList.stream()
                     .map(rt -> new VpnTargetBuilder().setKey(new VpnTargetKey(rt))
                                                 .setVrfRTValue(rt).setVrfRTType(type).build())
                     .collect(Collectors.toList());
    }

    static L3VpnComposite build(String vpnName, String vpnRd, long vpnTag, List<String> irts, List<String> erts) {
        Objects.nonNull(irts);
        Objects.nonNull(erts);
        Ipv4Family vpnIpv4Cfg = new Ipv4FamilyBuilder().setVpnTargets(makeVpnTargets(irts, erts)).build();
        VpnInstance vpnInst =
            new VpnInstanceBuilder().setVpnInstanceName(vpnName).setIpv4Family(vpnIpv4Cfg).build();
        VpnInstanceOpDataEntry vpnOpData =
            new VpnInstanceOpDataEntryBuilder().setVpnId(vpnTag).setVpnInstanceName(vpnName).setVrfId(vpnRd)
                                               .setVpnTargets(VpnOperDsUtils.makeVpnTargets(irts, erts)).build();
        return new L3VpnComposite(vpnInst, vpnOpData);
    }

    static L3VpnComposite VPN_1 =
        build("50525a08-3c2d-4d2a-8826-000000000001", "100:100", 100,
              Arrays.asList("1000:1000", "2000:2000", "3000:3000"), Arrays.asList("2000:2000"));

    static L3VpnComposite VPN_2 =
        build("50525a08-3c2d-4d2a-8826-000000000002", "200:200", 200,
              Arrays.asList("1000:1000", "2000:2000", "3000:3000"), Arrays.asList("2000:2000", "3000:3000"));

    static L3VpnComposite VPN_3 =
        build("50525a08-3c2d-4d2a-8826-000000000003", "300:300", 300,
              Arrays.asList("2000:2000"), Arrays.asList("2000:2000", "3000:3000"));

    static L3VpnComposite VPN_4 =
        build("50525a08-3c2d-4d2a-8826-000000000004", "400:400", 400, new ArrayList<String>(), new ArrayList<String>());

    static L3VpnComposite VPN_5 =
        build("50525a08-3c2d-4d2a-8826-000000000005", "500:500", 500, // has same iRTs as VPN_1 and VPN2
              Arrays.asList("1000:1000", "2000:2000", "3000:3000"), Arrays.asList("2000:2000"));

    static L3VpnComposite VPN_6 =
        build("50525a08-3c2d-4d2a-8826-000000000006", "600:600", 600,  // has same iRTs as VPN_1 and VPN_2
              Arrays.asList("1000:1000", "2000:2000", "3000:3000"), Arrays.asList("2000:2000", "6000:6000"));

    static List<L3VpnComposite> ALL_VPNS = Arrays.asList(VPN_1, VPN_2, VPN_3, VPN_4, VPN_5, VPN_6);

}
