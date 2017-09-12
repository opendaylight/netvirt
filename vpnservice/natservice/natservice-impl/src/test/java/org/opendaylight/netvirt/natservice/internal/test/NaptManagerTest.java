/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.natservice.internal.IPAddress;
import org.opendaylight.netvirt.natservice.internal.NaptManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MDSALUtil.class)
public class NaptManagerTest {

    @Mock
    IdManagerService idMgr;
    @Mock
    DataBroker dataBroker;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
        .intext.ip.map.ip.mapping.IpMap> ipmapId = null;
    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext
        .ip.map.ip.mapping.IpMap ipmap = null;

    private NaptManager naptManager;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        naptManager = new NaptManager(dataBroker, idMgr);
        when(idMgr.createIdPool(any(CreateIdPoolInput.class)))
            .thenReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build()));

        PowerMockito.mockStatic(MDSALUtil.class);
    }

    @Ignore
    @Test
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testRegisterMappingIpIP() {
        // TODO : This needs to be modified to make it work
        // TODO : Issue with Mockito.any() usage, so for now run registerMapping testcases as seperate Tests.
        // This needs to be fixed properly.
        ipmapId = InstanceIdentifier.builder(
            IntextIpMap.class).child(IpMapping.class, new IpMappingKey(5L))
            .child(IpMap.class, new IpMapKey("10.0.0.1")).build();
        ipmap = new IpMapBuilder().setKey(new IpMapKey("10.0.0.1")).setInternalIp("10.0.0.1")
            .setExternalIp("192.17.13.1").build();
        try {
            PowerMockito.doNothing()
                .when(MDSALUtil.class, "syncWrite", dataBroker, LogicalDatastoreType.OPERATIONAL, ipmapId, ipmap);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        IPAddress internal = new IPAddress("10.0.0.1", 0);
        IPAddress external = new IPAddress("192.17.13.1", 0);
        naptManager.registerMapping(5, internal, external);
        PowerMockito.verifyStatic();

    }

    @Ignore
    @Test
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testRegisterMappingIpSubnet() {
        // TODO : This needs to be modified to make it work
        ipmapId = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(5L)).child(IpMap.class, new IpMapKey("10.0.0.1")).build();
        ipmap = new IpMapBuilder().setKey(new IpMapKey("10.0.0.1")).setInternalIp("10.0.0.1")
            .setExternalIp("192.17.13.1/24").build();
        try {
            PowerMockito.doNothing()
                .when(MDSALUtil.class, "syncWrite", dataBroker, LogicalDatastoreType.OPERATIONAL, ipmapId, ipmap);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        IPAddress internal = new IPAddress("10.0.0.1", 0);
        IPAddress external = new IPAddress("192.17.13.1", 24);
        naptManager.registerMapping(5, internal, external);
        PowerMockito.verifyStatic();
    }

    @Ignore
    @Test
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testRegisterMappingSubnetIp() {
        // TODO : This needs to be modified to make it work
        ipmapId = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(6L)).child(IpMap.class, new IpMapKey("10.0.2.1/16")).build();
        ipmap = new IpMapBuilder().setKey(new IpMapKey("10.0.0.1")).setInternalIp("10.0.0.1")
            .setExternalIp("192.19.15.3").build();
        try {
            PowerMockito.doNothing()
                .when(MDSALUtil.class, "syncWrite", dataBroker, LogicalDatastoreType.OPERATIONAL, ipmapId, ipmap);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        IPAddress internal = new IPAddress("10.0.2.1", 16);
        IPAddress external = new IPAddress("192.19.15.3", 0);
        naptManager.registerMapping(6, internal, external);
        PowerMockito.verifyStatic();
    }

    @Ignore
    @Test
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testRegisterMappingSubnetSubnet() {
        // TODO : This needs to be modified to make it work
        ipmapId = InstanceIdentifier.builder(IntextIpMap.class)
            .child(IpMapping.class, new IpMappingKey(6L)).child(IpMap.class, new IpMapKey("10.2.0.1/24")).build();
        ipmap = new IpMapBuilder().setKey(new IpMapKey("10.2.0.1/24")).setInternalIp("10.2.0.1/24")
            .setExternalIp("192.21.16.1/16").build();
        try {
            PowerMockito.doNothing()
                .when(MDSALUtil.class, "syncWrite", dataBroker, LogicalDatastoreType.OPERATIONAL, ipmapId, ipmap);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        IPAddress internal = new IPAddress("10.2.0.1", 24);
        IPAddress external = new IPAddress("192.21.16.1", 16);
        naptManager.registerMapping(6, internal, external);
        PowerMockito.verifyStatic();
    }


    @Test
    public void testgetExternalAddressMapping() {
        // TODO : This needs to be modified to make it work
        // Testcase to test when no entry exists in ip-pot-map
        /*SessionAddress internalIpPort = new SessionAddress("10.0.0.1", 2);
        InstanceIdentifierBuilder<IpPortMapping> idBuilder =
                InstanceIdentifier.builder(IntextIpPortMap.class).child(IpPortMapping.class, new IpPortMappingKey(5L));
        InstanceIdentifier<IpPortMapping> id = idBuilder.build();
        try {
             PowerMockito.when(MDSALUtil.class, "read", dataBroker, LogicalDatastoreType.CONFIGURATION, id)
             .thenReturn(null);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        naptManager.getExternalAddressMapping(5, internalIpPort);
        PowerMockito.verifyStatic(); */
    }

    @Test
    public void testReleaseAddressMapping() {
        // TODO : Below needs to be modified to make it work
      /*  InstanceIdentifierBuilder<IpMapping> idBuilder =
                InstanceIdentifier.builder(IntextIpMap.class).child(IpMapping.class, new IpMappingKey(5L));
        InstanceIdentifier<IpMapping> id = idBuilder.build();
        try {
            PowerMockito.doNothing().when(MDSALUtil.class, "read", dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        IPAddress internal = new IPAddress("10.0.0.1",0);
        IPAddress external = new IPAddress("192.17.13.1", 0);
        naptManager.registerMapping(5, internal, external);
        SessionAddress internalSession = new SessionAddress("10.0.0.1", 0);
        naptManager.releaseAddressMapping(5L, internalSession);*/
    }


}
