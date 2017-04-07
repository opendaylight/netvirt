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
import static org.mockito.Matchers.any;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetworkBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MDSALUtil.class)
public class NetvirtProviderTest {
    private static final String NW_UUID_STR = "177bef73-514e-4922-990f-d7aba0f3b0f4";
    private static final String NW_UUID_NOEXIST_STR = "177bef73-5555-2222-0000-d7aba0f3b0f4";
    private static final String SUBNET_UUID_STR = "33333333-514e-4922-990f-d7aba0f3b0f4";
    private static final String PORT_UUID_STR = "44444444-5555-2222-0000-d7aba0f3b0f4";
    private static final String EMPTY_STR = "";
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtProviderTest.class);

    @Mock
    private DataBroker dataBroker;
    private final NetvirtProvider netvirtProvider;
    private final Map<Uuid, NetworkMap> uuidToNetwork;
    private final Map<Uuid, Subnetmap> uuidToSubnet;

    public NetvirtProviderTest() {
        this.netvirtProvider = new NetvirtProvider(dataBroker);
        uuidToNetwork = new HashMap<>();
        uuidToSubnet = new HashMap<>();
    }

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(MDSALUtil.class);
        //
        // Mock what gets returned by MDSALUtil.read()
        // Checks if the Iid is an RSP or an SF and return the corresponding
        // object from either sfNamesToSfs or rspNamesToRsps
        //
        InstanceIdentifier<DataObject> anyIid = any();
        PowerMockito.when(MDSALUtil.read(any(DataBroker.class), any(LogicalDatastoreType.class), anyIid))
            .thenAnswer(invocation -> {
                InstanceIdentifier<DataObject> objectIid =
                        (InstanceIdentifier<DataObject>) invocation.getArguments()[2];

                if (objectIid.getTargetType().equals(NetworkMap.class)) {
                    InstanceIdentifier<NetworkMap> nwMapIid =
                            (InstanceIdentifier<NetworkMap>) invocation.getArguments()[2];
                    NetworkMapKey key = InstanceIdentifier.keyOf(nwMapIid);

                    return com.google.common.base.Optional.fromNullable(uuidToNetwork.get(key.getNetworkId()));
                } else if (objectIid.getTargetType().equals(Subnetmap.class)) {
                    InstanceIdentifier<Subnetmap> subnetIid =
                            (InstanceIdentifier<Subnetmap>) invocation.getArguments()[2];
                    SubnetmapKey key = InstanceIdentifier.keyOf(subnetIid);

                    return com.google.common.base.Optional.fromNullable(uuidToSubnet.get(key.getId()));
                } else {
                    LOG.info("MDSALUtil.read() TargetType not recognized [{}]", objectIid.getTargetType());
                    return com.google.common.base.Optional.absent();
                }
            });
    }

    @After
    public void tearDown() throws Exception {
        uuidToNetwork.clear();
        uuidToSubnet.clear();
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
        uuidToNetwork.put(nwUuid, nwMapBuilder.build());

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
        uuidToNetwork.put(new Uuid(nwUuidStr), nwMapBuilder.build());

        // Simulates NetworkMap has subnet list, but subnets dont exist
        if (storeSubnet) {
            uuidToSubnet.put(subnetUuid, subnetBuilder.build());
        }
    }
}
