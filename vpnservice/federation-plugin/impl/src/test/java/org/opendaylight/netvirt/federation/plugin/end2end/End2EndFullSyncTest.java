/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.end2end;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.federation.service.api.message.EndFullSyncFederationMessage;
import org.opendaylight.federation.service.api.message.SubscribeMessage;
import org.opendaylight.messagequeue.AbstractFederationMessage;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class End2EndFullSyncTest extends AbstractEnd2EndTest {
    private static final Logger LOG = LoggerFactory.getLogger(End2EndFullSyncTest.class);

    private final Map<String, DataObject> listenerToObjects = new HashMap<>();
    private final Map<String, LogicalDatastoreType> listenerToDataStoreType = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        listenerToObjects.clear();
        egressPlugin.getListenersData().forEach(p -> {
            listenerToObjects.put(p.listenerId, null);
            listenerToDataStoreType.put(p.listenerId, p.checkExistingDataPath.getDatastoreType());
        });

        setupMocks();
        sentMessages.clear();
    }

    private void setupMocks() throws Exception {
        setGenerationNumber();
        doAnswer(invocation -> {
            AbstractFederationMessage msg = (AbstractFederationMessage) invocation.getArguments()[0];
            LOG.info("Federation message received {}", msg);
            sentMessages.add(msg);
            wrapperConsumer.consumeMsg(msg);
            return null;
        }).when(msgBusConsumerMock).sendMsg(any(), anyString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void mixedEntitiesOrderAndAmount() throws Exception {
        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(Arrays.asList(new AdjacencyBuilder()
                .setSubnetId(new Uuid(PRODUCER_SUBNET_ID)).setPrimaryAdjacency(true).setIpAddress("7.7.7.7").build()))
                .build();

        VpnInterface vpnIface = new VpnInterfaceBuilder().addAugmentation(Adjacencies.class, adjacencies)
                .setName(DUMMYINTERFACE).build();
        String otherVpnInterface = "other interface";
        VpnInterface otherVpnIface = new VpnInterfaceBuilder().addAugmentation(Adjacencies.class, adjacencies)
                .setName(otherVpnInterface).build();
        prepareDataObject(FederationPluginConstants.VPN_INTERFACE_KEY,
                new VpnInterfacesBuilder().setVpnInterface(Arrays.asList(vpnIface)).build());
        prepareDataObject(FederationPluginConstants.VPN_INTERFACE_KEY,
                new VpnInterfacesBuilder().setVpnInterface(Arrays.asList(otherVpnIface)).build());

        String operNodeName = "bridge/br-int-opernode";
        Node operNode = new NodeBuilder().setNodeId(new NodeId(operNodeName)).build();
        prepareDataObject(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY,
                new TopologyBuilder().setNode(Arrays.asList(operNode)).build());

        ElanInterface syncedElanInterface = new ElanInterfaceBuilder().setElanInstanceName(PRODUCER_NETWORK_ID)
                .setName(DUMMYINTERFACE).setKey(new ElanInterfaceKey(DUMMYINTERFACE)).build();
        prepareDataObject(FederationPluginConstants.ELAN_INTERFACE_KEY,
                new ElanInterfacesBuilder().setElanInterface(Arrays.asList(syncedElanInterface)).build());

        Interface iface = new InterfaceBuilder().setType(L2vlan.class).setName(DUMMYINTERFACE)
                .setKey(new InterfaceKey(DUMMYINTERFACE)).build();
        prepareDataObject(FederationPluginConstants.IETF_INTERFACE_KEY,
                new InterfacesBuilder().setInterface(Arrays.asList(iface)).build());

        String configNodeName = "bridge/br-int-confignode";
        Node configNode = new NodeBuilder().setNodeId(new NodeId(configNodeName)).build();
        prepareDataObject(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                new TopologyBuilder().setNode(Arrays.asList(configNode)).build());

        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node inventoryNode =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder()
                .setId(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("j")).build();
        prepareDataObject(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                new NodesBuilder().setNode(Arrays.asList(inventoryNode)).build());

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION,
                getVpnInterfaceInstanceIdentifier(DUMMYINTERFACE), vpnIface);
        tx.put(LogicalDatastoreType.CONFIGURATION,
                getVpnInterfaceInstanceIdentifier(otherVpnInterface), otherVpnIface);
        tx.submit().checkedGet();

        subscribe();
        assertSentMessages(8);
        assertTrue(lastMessageIsCommitMessage());
        InstanceIdentifier<Node> topoInstanceIdentifierConfig = getTopologyNodeInstanceIdentifier(configNodeName);
        Node federatedNodeConfig = getFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                topoInstanceIdentifierConfig);
        assertNotNull(federatedNodeConfig);
        assertEquals(configNodeName, federatedNodeConfig.getNodeId().getValue());
        InstanceIdentifier<Node> topoInstanceIdentifierOper = getTopologyNodeInstanceIdentifier(operNodeName);
        Node federatedNodeOper = getFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY,
                topoInstanceIdentifierOper);
        assertNotNull(federatedNodeOper);
        assertEquals(operNodeName, federatedNodeOper.getNodeId().getValue());
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node federatedInventoryNode =
                getFederationEntity(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                        getInventoryNodeInstanceIdentifier("j"));
        assertNotNull(federatedInventoryNode);
        assertNotNull(federatedInventoryNode.getAugmentation(InventoryNodeShadowProperties.class));
        Interface federatedInterface = getFederationEntity(FederationPluginConstants.IETF_INTERFACE_KEY,
                getIetfInterfaceInstanceIdentifier(DUMMYINTERFACE));
        assertNotNull(federatedInterface);
        assertNotNull(federatedInterface.getAugmentation(IfShadowProperties.class));
        ElanInterface federatedElanInterface = getFederationEntity(FederationPluginConstants.ELAN_INTERFACE_KEY,
                getElanInterfaceInstanceIdentifier(DUMMYINTERFACE));
        assertNotNull(federatedElanInterface);
        assertNotNull(federatedElanInterface.getAugmentation(ElanShadowProperties.class));
        VpnInterface federatedVpnInterface = getFederationEntity(FederationPluginConstants.VPN_INTERFACE_KEY,
                getVpnInterfaceInstanceIdentifier(DUMMYINTERFACE));
        assertNotNull(federatedVpnInterface);
        assertNotNull(federatedVpnInterface.getAugmentation(VpnShadowProperties.class));
    }

    private boolean lastMessageIsCommitMessage() {
        return sentMessages.removeLast() instanceof EndFullSyncFederationMessage;
    }

    private void subscribe() {
        producer.handleSubscribeMsg(
                new SubscribeMessage(CONSUMER1_QUEUE, "netvirt", CONSUMER1_QUEUE, "1.2.3.4", CONSUMER1_QUEUE));
    }

    private void prepareDataObject(String listenerKey, DataObject containerNode) {
        listenerToObjects.put(listenerKey, containerNode);
    }

}
