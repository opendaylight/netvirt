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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.federation.service.api.message.SubscribeMessage;
import org.opendaylight.federation.service.api.message.WrapperEntityFederationMessage;
import org.opendaylight.messagequeue.AbstractFederationMessage;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableStatisticsGatheringStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableStatisticsGatheringStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.snapshot.gathering.status.grouping.SnapshotGatheringStatusEndBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.InventoryNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.TopologyNodeShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class End2EndSteadyTest extends AbstractEnd2EndTest {
    private static final Logger LOG = LoggerFactory.getLogger(End2EndSteadyTest.class);

    @Before
    public void setUp() throws Exception {
        setupMocks();
        producer.handleSubscribeMsg(
                new SubscribeMessage(CONSUMER1_QUEUE, "netvirt", CONSUMER1_QUEUE, "1.2.3.4", CONSUMER1_QUEUE));
        sentMessages.clear();
    }

    private void setupMocks() throws Exception {
        setGenerationNumber();
        doAnswer(invocation -> {
            AbstractFederationMessage msg = (AbstractFederationMessage) invocation.getArguments()[0];
            if (msg instanceof WrapperEntityFederationMessage) {
                LOG.info("Federation message received {}", msg);
                sentMessages.add(msg);
            }
            wrapperConsumer.consumeMsg(msg);
            return null;
        }).when(msgBusConsumerMock).sendMsg(any(), anyString());
    }

    @Test
    public void topologyNodeWithFilteredInName() throws Exception {
        // Every nodeId should pass
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).build();
        InstanceIdentifier<Node> instanceIdentifier = getTopologyNodeInstanceIdentifier(INTEGRATION_BRIDGE_PREFIX);
        addFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, instanceIdentifier, node);
        assertSentMessages(1);
        Node federatedNode = getFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                instanceIdentifier);
        assertNotNull(federatedNode);
        TopologyNodeShadowProperties shadowProperties = federatedNode
                .getAugmentation(TopologyNodeShadowProperties.class);
        assertNotNull(shadowProperties);
        assertTrue(shadowProperties.isShadow());
        assertEquals(INTEGRATION_BRIDGE_PREFIX, federatedNode.getNodeId().getValue());
    }

    @Test
    public void vpnInterfaceWithoutAdjacenciesShouldBeFilteredOut() throws Exception {
        VpnInterface vpnIface = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(DUMMYINTERFACE)).build();
        addFederationEntity(FederationPluginConstants.VPN_INTERFACE_KEY,
                getVpnInterfaceInstanceIdentifier(DUMMYINTERFACE), vpnIface);
        assertNoSentMessages();
    }

    @Test
    public void vpnInterfaceValid() throws Exception {
        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(Arrays.asList(new AdjacencyBuilder()
                .setSubnetId(new Uuid(PRODUCER_SUBNET_ID)).setPrimaryAdjacency(true).setIpAddress("7.7.7.7").build()))
                .build();
        VpnInterface vpnIface = new VpnInterfaceBuilder().addAugmentation(Adjacencies.class, adjacencies)
                .setName(DUMMYINTERFACE).build();
        InstanceIdentifier<VpnInterface> instanceIdentifier = getVpnInterfaceInstanceIdentifier(DUMMYINTERFACE);
        addFederationEntity(FederationPluginConstants.VPN_INTERFACE_KEY, instanceIdentifier, vpnIface);
        assertSentMessages(1);
        VpnInterface federatedVpnInterface = getFederationEntity(FederationPluginConstants.VPN_INTERFACE_KEY,
                instanceIdentifier);
        assertNotNull(federatedVpnInterface);
        VpnShadowProperties shadowProperties = federatedVpnInterface.getAugmentation(VpnShadowProperties.class);
        assertNotNull(shadowProperties);
        assertTrue(shadowProperties.isShadow());
        Adjacencies federatedAdjacencies = federatedVpnInterface.getAugmentation(Adjacencies.class);
        assertNotNull(federatedAdjacencies);
        assertEquals(CONSUMER_SUBNET_ID, federatedAdjacencies.getAdjacency().get(0).getSubnetId().getValue());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void inventoryNodeTruncatedStatisticsAugmentations() throws Exception {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder nodeBuilder =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder()
                .setId(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("dummynode"));
        FlowCapableStatisticsGatheringStatus statistics = new FlowCapableStatisticsGatheringStatusBuilder()
                .setSnapshotGatheringStatusEnd(new SnapshotGatheringStatusEndBuilder()
                        .setEnd(new DateAndTime("1986-09-04T15:12:10.5Z")).build())
                .build();
        nodeBuilder.addAugmentation(FlowCapableStatisticsGatheringStatus.class, statistics);
        InstanceIdentifier<
                org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> instanceIdentifier =
                getInventoryNodeInstanceIdentifier("dummynode");
        addFederationEntity(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, instanceIdentifier,
                nodeBuilder.build());
        assertSentMessages(1);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node federatedNode =
                getFederationEntity(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, instanceIdentifier);
        assertNotNull(federatedNode);
        InventoryNodeShadowProperties shadowProperties = federatedNode
                .getAugmentation(InventoryNodeShadowProperties.class);
        assertNotNull(shadowProperties);
        assertTrue(shadowProperties.isShadow());
        assertNull(federatedNode.getAugmentation(FlowCapableStatisticsGatheringStatus.class));
    }

    @Test
    public void topologyNodeWithFilteredOutTerminationPoint() throws Exception {
        List<TerminationPoint> tps = Arrays.asList(new TerminationPointBuilder().setTpId(new TpId("tunDummy")).build());
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).setTerminationPoint(tps).build();
        InstanceIdentifier<Node> instanceIdentifier = getTopologyNodeInstanceIdentifier(INTEGRATION_BRIDGE_PREFIX);
        addFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, instanceIdentifier, node);
        assertSentMessages(1);
        Node federatedNode = getFederationEntity(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                instanceIdentifier);
        assertNotNull(federatedNode);
        assertEquals(0, federatedNode.getTerminationPoint().size());
    }

    @Test
    public void topologyNodeWithFilteredInTerminationPoint() throws Exception {
        List<TerminationPoint> tps = Arrays
                .asList(new TerminationPointBuilder().setTpId(new TpId("nottunDummy")).build());
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).setTerminationPoint(tps).build();
        InstanceIdentifier<Node> instanceIdentifier = getTopologyNodeInstanceIdentifier(INTEGRATION_BRIDGE_PREFIX);
        addFederationEntity(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, instanceIdentifier, node);
        assertSentMessages(1);
        Node federatedNode = getFederationEntity(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                instanceIdentifier);
        assertNotNull(federatedNode);
        assertEquals("nottunDummy", federatedNode.getTerminationPoint().get(0).getTpId().getValue());
    }

    @Test
    public void elanInterfaceWrongNetworkFilteredOut() throws Exception {
        ElanInterface elanIface = new ElanInterfaceBuilder().setElanInstanceName("dummynetwork").setName(DUMMYINTERFACE)
                .setKey(new ElanInterfaceKey(DUMMYINTERFACE)).build();
        addFederationEntity(FederationPluginConstants.ELAN_INTERFACE_KEY,
                getElanInterfaceInstanceIdentifier(DUMMYINTERFACE), elanIface);
        assertNoSentMessages();
    }


    @Test
    public void interfaceQueuedAndThenReleased() throws Exception {
        Interface iface = new InterfaceBuilder().setType(L2vlan.class).setName(DUMMYINTERFACE)
                .setKey(new InterfaceKey(DUMMYINTERFACE)).build();
        when(elanService.isExternalInterface(any())).thenReturn(false);
        when(elanService.getElanInterfaceByElanInterfaceName(any())).thenReturn(null);
        addFederationEntity(FederationPluginConstants.IETF_INTERFACE_KEY,
                getIetfInterfaceInstanceIdentifier(DUMMYINTERFACE), iface);
        assertNoSentMessages();

        ElanInterface elanIface = new ElanInterfaceBuilder().setElanInstanceName(PRODUCER_NETWORK_ID)
                .setName(DUMMYINTERFACE).setKey(new ElanInterfaceKey(DUMMYINTERFACE)).build();
        InstanceIdentifier<ElanInterface> instanceIdentifier = getElanInterfaceInstanceIdentifier(DUMMYINTERFACE);
        addFederationEntity(FederationPluginConstants.ELAN_INTERFACE_KEY, instanceIdentifier, elanIface);
        assertSentMessages(2);
        ElanInterface federatedElanInterface = getFederationEntity(FederationPluginConstants.ELAN_INTERFACE_KEY,
                instanceIdentifier);
        assertNotNull(federatedElanInterface);
        ElanShadowProperties shadowProperties = federatedElanInterface.getAugmentation(ElanShadowProperties.class);
        assertNotNull(shadowProperties);
        assertTrue(shadowProperties.isShadow());
        assertEquals(DUMMYINTERFACE, federatedElanInterface.getName());
        assertEquals(CONSUMER_NETWORK_ID, federatedElanInterface.getElanInstanceName());

        when(elanService.getElanInterfaceByElanInterfaceName(any())).thenReturn(elanIface);
        InstanceIdentifier<Interface> interfaceIdentifier = getIetfInterfaceInstanceIdentifier(DUMMYINTERFACE);
        addFederationEntity(FederationPluginConstants.IETF_INTERFACE_KEY, interfaceIdentifier, iface);
        assertEquals(3, sentMessages.size());
        Interface federatedInterface = getFederationEntity(FederationPluginConstants.IETF_INTERFACE_KEY,
                interfaceIdentifier);
        assertNotNull(federatedInterface);
        IfShadowProperties ifaceShadowProperties = federatedInterface.getAugmentation(IfShadowProperties.class);
        assertNotNull(ifaceShadowProperties);
        assertTrue(ifaceShadowProperties.isShadow());
        assertEquals(DUMMYINTERFACE, federatedInterface.getName());
    }
}
