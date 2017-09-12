/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.ApplyLabelBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.apply.label.apply.label.mode.PerRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4Family;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

@RunWith(MockitoJUnitRunner.class)
public class VpnServiceTest {
    @Mock
    DataBroker dataBroker;
    @Mock
    IBgpManager bgpManager;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void test() {

        List<VpnTarget> vpnTargetList = new ArrayList<>();

        VpnTarget vpneRTarget = new VpnTargetBuilder().setKey(new VpnTargetKey("100:1")).setVrfRTValue("100:1")
            .setVrfRTType(VpnTarget.VrfRTType.ExportExtcommunity).build();
        VpnTarget vpniRTarget = new VpnTargetBuilder().setKey(new VpnTargetKey("100:2")).setVrfRTValue("100:2")
            .setVrfRTType(VpnTarget.VrfRTType.ImportExtcommunity).build();

        vpnTargetList.add(vpneRTarget);
        vpnTargetList.add(vpniRTarget);

        VpnTargets vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();

        Ipv4Family ipv4Family = new Ipv4FamilyBuilder().setRouteDistinguisher(Arrays.asList("100:1","100:2:"))
                .setVpnTargets(vpnTargets).setApplyLabel(new ApplyLabelBuilder().setApplyLabelMode(
                            new PerRouteBuilder().setApplyLabelPerRoute(true).build()).build()).build();

        VpnInstanceBuilder builder =
            new VpnInstanceBuilder().setKey(new VpnInstanceKey("Vpn1")).setIpv4Family(ipv4Family);
        VpnInstance instance = builder.build();
        //TODO: Need to enhance the test case to handle ds read/write ops
        //vpnManager.onDataChanged(event);
    }

    private InstanceIdentifier<VpnInstance> createVpnId(String name) {
        InstanceIdentifierBuilder<VpnInstance> idBuilder =
            InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class, new VpnInstanceKey(name));
        InstanceIdentifier<VpnInstance> id = idBuilder.build();
        return id;
    }

}
