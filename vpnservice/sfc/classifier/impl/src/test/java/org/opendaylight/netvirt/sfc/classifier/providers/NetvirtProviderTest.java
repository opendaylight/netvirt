/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.networkmaps.NetworkMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetworkBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetvirtProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    private static final String NW_UUID_STR = "177bef73-514e-4922-990f-d7aba0f3b0f4";
    private static final String NW_UUID_NOEXIST_STR = "177bef73-5555-2222-0000-d7aba0f3b0f4";
    private static final String SUBNET_UUID_STR = "33333333-514e-4922-990f-d7aba0f3b0f4";
    private static final String PORT_UUID_STR = "44444444-5555-2222-0000-d7aba0f3b0f4";
    private static final String EMPTY_STR = "";

    private NetvirtProvider netvirtProvider;

    @Before
    public void setUp() throws Exception {
        this.netvirtProvider = new NetvirtProvider(getDataBroker());
    }

    @Test
    public void getLogicalInterfacesFromNeutronNetwork() {

        // Network doesnt exist
        NeutronNetworkBuilder nwBuilder = new NeutronNetworkBuilder();
        nwBuilder.setNetworkUuid(NW_UUID_NOEXIST_STR);
        List<String> interfaces = netvirtProvider.getLogicalInterfacesFromNeutronNetwork(nwBuilder.build());
        assertTrue(interfaces.isEmpty());

        // Network exists, subnet list empty
        createNetworkMap(NW_UUID_STR);
        nwBuilder = new NeutronNetworkBuilder();
        nwBuilder.setNetworkUuid(NW_UUID_STR);
        interfaces = netvirtProvider.getLogicalInterfacesFromNeutronNetwork(nwBuilder.build());
        assertTrue(interfaces.isEmpty());

        // Network exists, subnet does not exist
        createNetworkMap(NW_UUID_STR, SUBNET_UUID_STR, false, EMPTY_STR);
        nwBuilder = new NeutronNetworkBuilder();
        nwBuilder.setNetworkUuid(NW_UUID_STR);
        interfaces = netvirtProvider.getLogicalInterfacesFromNeutronNetwork(nwBuilder.build());
        assertTrue(interfaces.isEmpty());

        // Network exists, subnet exists, no ports
        createNetworkMap(NW_UUID_STR, SUBNET_UUID_STR, true, EMPTY_STR);
        nwBuilder = new NeutronNetworkBuilder();
        nwBuilder.setNetworkUuid(NW_UUID_STR);
        interfaces = netvirtProvider.getLogicalInterfacesFromNeutronNetwork(nwBuilder.build());
        assertTrue(interfaces.isEmpty());

        // Network exists, subnet exists, port exists
        createNetworkMap(NW_UUID_STR, SUBNET_UUID_STR, true, PORT_UUID_STR);
        nwBuilder = new NeutronNetworkBuilder();
        nwBuilder.setNetworkUuid(NW_UUID_STR);
        interfaces = netvirtProvider.getLogicalInterfacesFromNeutronNetwork(nwBuilder.build());
        assertFalse(interfaces.isEmpty());
    }

    private NetworkMapBuilder createNetworkMap(String nwUuidStr) {
        Uuid nwUuid = new Uuid(nwUuidStr);
        NetworkMapBuilder nwMapBuilder = new NetworkMapBuilder();
        nwMapBuilder.setNetworkId(nwUuid);
        storeNetworkMap(nwUuid, nwMapBuilder.build());

        return nwMapBuilder;
    }

    private void createNetworkMap(String nwUuidStr, String subnetUuidStr, boolean storeSubnet, String portUuidStr) {
        SubnetmapBuilder subnetBuilder = new SubnetmapBuilder();
        if (!portUuidStr.isEmpty()) {
            List<Uuid> portIdList = new ArrayList<>();
            portIdList.add(new Uuid(portUuidStr));
            subnetBuilder.setPortList(portIdList);
        }

        Uuid subnetUuid = new Uuid(subnetUuidStr);
        subnetBuilder.setId(subnetUuid);
        List<Uuid> subnetIdList = new ArrayList<>();
        subnetIdList.add(subnetUuid);

        NetworkMapBuilder nwMapBuilder = createNetworkMap(nwUuidStr);
        nwMapBuilder.setSubnetIdList(subnetIdList);
        storeNetworkMap(new Uuid(nwUuidStr), nwMapBuilder.build());

        // Simulates NetworkMap has subnet list, but subnets dont exist
        if (storeSubnet) {
            storeSubnetMap(subnetUuid, subnetBuilder.build());
        }
    }

    @SuppressWarnings("deprecation")
    private void storeNetworkMap(Uuid nwUuid, NetworkMap nwMap) {
        InstanceIdentifier<NetworkMap> networkMapIdentifier =
                InstanceIdentifier.builder(NetworkMaps.class)
                    .child(NetworkMap.class,new NetworkMapKey(nwUuid)).build();

        MDSALUtil.syncWrite(getDataBroker(), LogicalDatastoreType.CONFIGURATION, networkMapIdentifier, nwMap);
    }

    @SuppressWarnings("deprecation")
    private void storeSubnetMap(Uuid subnetUuid, Subnetmap subnetMap) {
        InstanceIdentifier<Subnetmap> subnetId =
                InstanceIdentifier.builder(Subnetmaps.class)
                    .child(Subnetmap.class, new SubnetmapKey(subnetUuid)).build();

        MDSALUtil.syncWrite(getDataBroker(), LogicalDatastoreType.CONFIGURATION, subnetId, subnetMap);
    }
}
