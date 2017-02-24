/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;

/**
 * There is one VpnInstance in ConfigDS and a VpnInstance in OperationalDS. The only reason
 * for this class to exist is to avoid very long fully qualified class names when these 2
 * classes coincide on the same java file. A couple of examples:
 *
 * <p>
 * org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance, versus
 * org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
 * </p>
 *
 * <p>
 * org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget, versus
 * org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
 *                                                              .instance.op.data.entry.vpntargets.VpnTarget
 * </p>
 *
 * <p>These class deals with those classes usually considered for the Operational DS.</p>
 */
public class VpnOperDsUtils {

    public static VpnInstance makeVpnInstance(String vpnName, String vpnRd, Long vpnTag) {
        return new VpnInstanceBuilder().setKey(new VpnInstanceKey(vpnName)).setVpnInstanceName(vpnName)
                                       .setVrfId(vpnRd).setVpnId(vpnTag).build();
    }

    static List<VpnTarget> makeVpnTargets(List<String> rtList, VpnTarget.VrfRTType type) {
        return rtList.stream()
                     .map(rt -> new VpnTargetBuilder().setKey(new VpnTargetKey(rt))
                                                      .setVrfRTValue(rt).setVrfRTType(type).build())
                     .collect(Collectors.toList());
    }

    public static VpnTargets makeVpnTargets(List<String> irts, List<String> erts) {
        List<String> commonRT = new ArrayList<>(irts);
        commonRT.retainAll(erts);
        List<String> pureIrts = new ArrayList<>(irts);
        pureIrts.removeAll(commonRT);
        List<String> pureErts = new ArrayList<>(erts);
        pureErts.removeAll(commonRT);
        List<VpnTarget> vpnTargetList = new ArrayList<>();
        vpnTargetList.addAll(makeVpnTargets(commonRT, VpnTarget.VrfRTType.Both));
        vpnTargetList.addAll(makeVpnTargets(pureIrts, VpnTarget.VrfRTType.ImportExtcommunity));
        vpnTargetList.addAll(makeVpnTargets(pureErts, VpnTarget.VrfRTType.ExportExtcommunity));
        return new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();
    }
}
