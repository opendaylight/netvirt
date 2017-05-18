/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.end2end;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.federation.service.api.message.EndFullSyncFederationMessage;
import org.opendaylight.federation.service.api.message.SubscribeMessage;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class End2EndFullSyncTest extends AbstractEnd2EndTest {

    private final HashMap<String, DataObject> listenerToObjects = new HashMap<>();
    private final HashMap<String, LogicalDatastoreType> listenerToDataStoreType = new HashMap<>();

    @Before
    public void setUp() {
        super.initialization();
        listenerToObjects.clear();
        egressPlugin.getListenersData().forEach(p -> {
            listenerToObjects.put(p.listenerId, null);
            listenerToDataStoreType.put(p.listenerId, p.checkExistingDataPath.getDatastoreType());
        });
    }

    @SuppressWarnings("deprecation")
    @Test
    public void mixedEntitiesOrderAndAmount() {
        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(Arrays.asList(new AdjacencyBuilder()
                .setSubnetId(new Uuid(PRODUCER_SUBNET_ID)).setAdjacencyType(AdjacencyType.PrimaryAdjacency)
                .setIpAddress("7.7.7.7").build())).build();

        VpnInterface vpnIface = new VpnInterfaceBuilder().addAugmentation(Adjacencies.class, adjacencies)
                .setName(DUMMYINTERFACE).build();
        prepareDataObject(FederationPluginConstants.VPN_INTERFACE_KEY,
                new VpnInterfacesBuilder().setVpnInterface(Arrays.asList(vpnIface)).build());

        Node operNode = new NodeBuilder().setNodeId(new NodeId("bridge/br-int-opernode")).build();
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

        Node configNode = new NodeBuilder().setNodeId(new NodeId("bridge/br-int-confignode")).build();
        prepareDataObject(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                new TopologyBuilder().setNode(Arrays.asList(configNode)).build());

        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node
            inventoryNode = new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder()
                .setId(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("j")).build();
        prepareDataObject(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                new NodesBuilder().setNode(Arrays.asList(inventoryNode)).build());

        when(mockReadTx.read(any(), any())).thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(VpnInterfaces.class)
                        .child(VpnInterface.class, new VpnInterfaceKey(DUMMYINTERFACE)).build()))
                                .thenReturn(Futures.immediateCheckedFuture(Optional.of(vpnIface)));
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(VpnInterfaces.class)
                        .child(VpnInterface.class, new VpnInterfaceKey("other interface")).build()))
                                .thenReturn(Futures.immediateCheckedFuture(Optional.of(vpnIface)));
        subscribe();
        assertEquals(8, sentMessages.size());
        assertEquals(6, mergedObjectsAmount());
        assertTrue(lastMessageIsCommitMessage());
        assertEquals("bridge/br-int-confignode", removeFirstMerged(Node.class).getNodeId().getValue());
        assertEquals("bridge/br-int-opernode", removeFirstMerged(Node.class).getNodeId().getValue());
        assertEquals("j",
                removeFirstMerged(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class)
                        .getId().getValue());
        assertEquals(DUMMYINTERFACE, removeFirstMerged(Interface.class).getName());
        assertEquals(DUMMYINTERFACE, removeFirstMerged(ElanInterface.class).getName());
        assertEquals(DUMMYINTERFACE, removeFirstMerged(VpnInterface.class).getName());
    }

    private boolean lastMessageIsCommitMessage() {
        return sentMessages.removeLast() instanceof EndFullSyncFederationMessage;
    }

    @SuppressWarnings("unchecked")
    private void subscribe() {
        for (Entry<String, DataObject> listener : listenerToObjects.entrySet()) {
            if (listener.getValue() == null) {
                when(mockReadTx.read(listenerToDataStoreType.get(listener.getKey()),
                        FederationPluginUtils.getParentInstanceIdentifier(listener.getKey())))
                                .thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
            } else {
                when(mockReadTx.read(listenerToDataStoreType.get(listener.getKey()),
                        FederationPluginUtils.getParentInstanceIdentifier(listener.getKey()))).thenReturn(
                                Futures.immediateCheckedFuture(Optional.of(preparedObject(listener.getKey()))),
                                Futures.immediateCheckedFuture(Optional.absent()));
            }
        }
        producer.handleSubscribeMsg(
                new SubscribeMessage(CONSUMER1_QUEUE, "netvirt", CONSUMER1_QUEUE, "1.2.3.4", CONSUMER1_QUEUE));
    }

    @SuppressWarnings("unchecked")
    private <T extends DataObject> T preparedObject(String key) {
        return (T) listenerToObjects.get(key);
    }

    private void prepareDataObject(String listenerKey, DataObject containerNode) {
        listenerToObjects.put(listenerKey, containerNode);
    }

}
