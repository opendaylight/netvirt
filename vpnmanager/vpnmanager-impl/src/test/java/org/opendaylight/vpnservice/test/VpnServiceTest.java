/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.opendaylight.vpnservice.VpnManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.*;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.*;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.apply.label.apply.label.mode.*;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.apply.label.ApplyLabelMode;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.ApplyLabelBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;

@RunWith(MockitoJUnitRunner.class)
public class VpnServiceTest {
    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    MockDataChangedEvent event;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        event = new MockDataChangedEvent();
    }

    @Test
    public void test() {
        VpnInstanceBuilder builder = new VpnInstanceBuilder().setKey(new VpnInstanceKey("Vpn1")).
                setIpv4Family(new Ipv4FamilyBuilder().setRouteDistinguisher("100:1").setImportRoutePolicy("100:2").
                    setExportRoutePolicy("100:1").setApplyLabel(new ApplyLabelBuilder().setApplyLabelMode(
                        new PerRouteBuilder().setApplyLabelPerRoute(true).build()).build()).build());
        VpnInstance instance = builder.build();
        VpnManager vpnManager = new VpnManager(dataBroker);
        event.created.put(createVpnId("Vpn1"), instance);
        vpnManager.onDataChanged(event);
    }

    private InstanceIdentifier<VpnInstance> createVpnId(String name) {
       InstanceIdentifierBuilder<VpnInstance> idBuilder = 
           InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class, new VpnInstanceKey(name));
       InstanceIdentifier<VpnInstance> id = idBuilder.build();
       return id;
    }

}
