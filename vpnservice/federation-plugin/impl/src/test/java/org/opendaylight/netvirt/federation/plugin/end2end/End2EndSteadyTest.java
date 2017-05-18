/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.end2end;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.federation.service.api.message.SubscribeMessage;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;

@RunWith(MockitoJUnitRunner.class)
public class End2EndSteadyTest extends AbstractEnd2EndTest {

    public End2EndSteadyTest() {

    }

    @Before
    public void setUp() {
        super.initialization();
        prepareMocks();
        producer.handleSubscribeMsg(
                new SubscribeMessage(CONSUMER1_QUEUE, "netvirt", CONSUMER1_QUEUE, "1.2.3.4", CONSUMER1_QUEUE));
        sentMessages.clear();// Clear the commit message
    }

    private void prepareMocks() {
        when(mockReadTx.read(any(), any())).thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
        setGenerationNumberMock();
    }

    @Test
    public void topologyNodeWithFilteredInName() { // Every nodeId should pass
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).build();
        dcn(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, node);
        assertEquals(1, sentMessages.size());
        assertEquals(INTEGRATION_BRIDGE_PREFIX, removeLastMerged(Node.class).getNodeId().getValue());
    }

    @Test
    public void vpnInterfaceWithoutAdjacenciesShouldBeFilteredOut() {
        VpnInterface vpnIface = new VpnInterfaceBuilder().build();
        dcn(FederationPluginConstants.VPN_INTERFACE_KEY, vpnIface);
        assertEquals(0, sentMessages.size());
    }

    @Test
    public void vpnInterfaceValid() {
        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(Arrays.asList(new AdjacencyBuilder()
                .setSubnetId(new Uuid(PRODUCER_SUBNET_ID)).setAdjacencyType(AdjacencyType.PrimaryAdjacency)
                .setIpAddress("7.7.7.7").build())).build();
        VpnInterface vpnIface = new VpnInterfaceBuilder().addAugmentation(Adjacencies.class, adjacencies)
                .setName(DUMMYINTERFACE).build();
        dcn(FederationPluginConstants.VPN_INTERFACE_KEY, vpnIface);
        assertEquals(1, sentMessages.size());
        assertEquals(CONSUMER_SUBNET_ID, removeLastMerged(VpnInterface.class).getAugmentation(Adjacencies.class)
                .getAdjacency().get(0).getSubnetId().getValue());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void inventoryNodeTruncatedStatisticsAugmentations() {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder
            nodeBuilder = new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder()
                .setId(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("dummynode"));
        FlowCapableStatisticsGatheringStatus statistics = new FlowCapableStatisticsGatheringStatusBuilder()
                .setSnapshotGatheringStatusEnd(new SnapshotGatheringStatusEndBuilder()
                        .setEnd(new DateAndTime("1986-09-04T15:12:10.5Z")).build())
                .build();
        nodeBuilder.addAugmentation(FlowCapableStatisticsGatheringStatus.class, statistics);
        dcn(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, nodeBuilder.build());
        assertEquals(1, sentMessages.size());
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node lastMergedNode = removeLastMerged(
                org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class);
        assertNull(lastMergedNode.getAugmentation(FlowCapableStatisticsGatheringStatus.class));
    }

    @Test
    public void topologyNodeWithFilteredOutTerminationPoint() {
        List<TerminationPoint> tps = Arrays.asList(new TerminationPointBuilder().setTpId(new TpId("tunDummy")).build());
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).setTerminationPoint(tps).build();
        dcn(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, node);

        assertEquals(1, sentMessages.size());
        assertEquals(0, removeLastMerged(Node.class).getTerminationPoint().size());
    }

    @Test
    public void topologyNodeWithFilteredInTerminationPoint() {
        List<TerminationPoint> tps = Arrays
                .asList(new TerminationPointBuilder().setTpId(new TpId("nottunDummy")).build());
        Node node = new NodeBuilder().setNodeId(new NodeId(INTEGRATION_BRIDGE_PREFIX)).setTerminationPoint(tps).build();
        dcn(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, node);
        assertEquals(1, sentMessages.size());
        assertEquals("nottunDummy", removeLastMerged(Node.class).getTerminationPoint().get(0).getTpId().getValue());
    }

    @Test
    public void elanInterfaceWrongNetworkFilteredOut() {
        ElanInterface elanIface = new ElanInterfaceBuilder().setElanInstanceName("dummynetwork").setName(DUMMYINTERFACE)
                .setKey(new ElanInterfaceKey(DUMMYINTERFACE)).build();
        dcn(FederationPluginConstants.ELAN_INTERFACE_KEY, elanIface);
        assertEquals(0, sentMessages.size());
    }

    @Test
    public void interfaceQueuedAndThenReleased() {
        Interface iface = new InterfaceBuilder().setType(L2vlan.class).setName(DUMMYINTERFACE)
                .setKey(new InterfaceKey(DUMMYINTERFACE)).build();
        when(elanService.isExternalInterface(any())).thenReturn(false);
        when(elanService.getElanInterfaceByElanInterfaceName(any())).thenReturn(null);
        dcn(FederationPluginConstants.IETF_INTERFACE_KEY, iface);
        assertEquals(0, sentMessages.size());

        ElanInterface elanIface = new ElanInterfaceBuilder().setElanInstanceName(PRODUCER_NETWORK_ID)
                .setName(DUMMYINTERFACE).setKey(new ElanInterfaceKey(DUMMYINTERFACE)).build();
        dcn(FederationPluginConstants.ELAN_INTERFACE_KEY, elanIface);
        assertEquals(2, sentMessages.size());
        assertEquals(DUMMYINTERFACE, removeFirstMerged(Interface.class).getName());
        assertEquals(CONSUMER_NETWORK_ID, removeFirstMerged(ElanInterface.class).getElanInstanceName());

        when(elanService.getElanInterfaceByElanInterfaceName(any())).thenReturn(elanIface);
        dcn(FederationPluginConstants.IETF_INTERFACE_KEY, iface);
        assertEquals(3, sentMessages.size());
        assertEquals(DUMMYINTERFACE, removeFirstMerged(Interface.class).getName());
    }
}
