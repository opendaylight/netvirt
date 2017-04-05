/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.end2end;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.federation.service.api.IConsumerManagement;
import org.opendaylight.federation.service.api.message.WrapperEntityFederationMessage;
import org.opendaylight.federation.service.impl.FederationProducerMgr;
import org.opendaylight.federation.service.impl.WrapperConsumer;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.messagequeue.AbstractFederationMessage;
import org.opendaylight.messagequeue.IMessageBusClient;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.federation.plugin.FederatedAclPair;
import org.opendaylight.netvirt.federation.plugin.FederatedNetworkPair;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginEgress;
import org.opendaylight.netvirt.federation.plugin.FederationPluginIngress;
import org.opendaylight.netvirt.federation.plugin.FederationPluginMgr;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.SubnetVpnAssociationManager;
import org.opendaylight.netvirt.federation.plugin.test.FederationPluginTestModule;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.FederationGenerations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.federation.service.config.rev161110.FederationConfigData;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

public class AbstractEnd2EndTest {

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(FederationPluginTestModule.class,
            TestableDataTreeChangeListenerModule.class);

    protected static final String REMOTE_IP = "1.1.1.1";
    protected static final String DUMMYINTERFACE = "dummyinterface";
    protected static final String REMOTE_TENANT_ID = "remoteTenantId";
    protected static final String LOCAL_TENANT_ID = "localTenantId";
    protected static final String PRODUCER_SUBNET_ID = "11112222-ffff-aaaa-2222-333444555666";
    protected static final String CONSUMER_SUBNET_ID = "11113333-ffff-aaaa-2222-333444555666";
    protected static final String PRODUCER_NETWORK_ID = "remoteNetworkId";
    protected static final String CONSUMER_NETWORK_ID = "localNetworkId";
    protected static final String CONSUMER1_QUEUE = "consumer1Queue";
    protected static final String CONSUMER2_QUEUE = "consumer2Queue";
    protected static final String INTEGRATION_BRIDGE_PREFIX = "bridge/br-int";
    protected static final Uuid CONSUMER_SEC_GROUP_ID = new Uuid("11112222-ffff-a55a-2222-333444555666");
    protected static final Uuid PRODUCER_SEC_GROUP_ID = new Uuid("11117777-ffff-a55a-2222-333444555666");

    @Inject
    protected DataBroker dataBroker;
    @Inject
    protected IElanService elanService;
    @Inject
    protected SubnetVpnAssociationManager associationMgr;
    @Inject
    FederationConfigData federationConfigData;
    @Inject
    protected IMessageBusClient msgBusConsumerMock;
    @Inject
    protected ClusterSingletonServiceProvider singletonService;
    @Inject
    protected IConsumerManagement consumerMgr;
    @Inject
    protected FederationPluginMgr mgr;

    protected FederationProducerMgr producer;
    protected FederationPluginEgress egressPlugin;
    protected FederationPluginIngress ingressPlugin;
    protected WrapperConsumer wrapperConsumer;
    protected LinkedList<AbstractFederationMessage> sentMessages = new LinkedList<>();

    @Before
    public void setup() {
        producer = new FederationProducerMgr(msgBusConsumerMock, dataBroker, federationConfigData, singletonService,
                consumerMgr);
        producer.attachPluginFactory("netvirt", (payload, queueName, contextId) -> egressPlugin);
        List<FederatedNetworkPair> federatedNetworkPairs = Arrays.asList(new FederatedNetworkPair(CONSUMER_NETWORK_ID,
                PRODUCER_NETWORK_ID, CONSUMER_SUBNET_ID, PRODUCER_SUBNET_ID, LOCAL_TENANT_ID, REMOTE_TENANT_ID));
        List<FederatedAclPair> federatedSecurityGroupsPairs = Arrays
                .asList(new FederatedAclPair(CONSUMER_SEC_GROUP_ID, PRODUCER_SEC_GROUP_ID));
        egressPlugin = new FederationPluginEgress(producer, federatedNetworkPairs, federatedSecurityGroupsPairs,
                CONSUMER1_QUEUE, CONSUMER1_QUEUE);
        ingressPlugin = new FederationPluginIngress(mgr, dataBroker, REMOTE_IP, federatedNetworkPairs,
                federatedSecurityGroupsPairs);
        wrapperConsumer = new WrapperConsumer(REMOTE_IP, ingressPlugin);
        setupMocks();

    }

    private void setupMocks() {
        when(associationMgr.getSubnetVpn(any())).thenReturn("dummySubnetVpn");
    }

    protected String lastSentJson() {
        AbstractFederationMessage lastMsg = sentMessages.getLast();
        WrapperEntityFederationMessage wrapper = (WrapperEntityFederationMessage) lastMsg;
        return wrapper.getPayload().getJsonInput();
    }

    protected <T extends DataObject> void addFederationEntity(String listenerKey,
            InstanceIdentifier<T> instanceIdentifier, T newObject) {
        LogicalDatastoreType datastoreType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
        DataTreeModificationStub<
                T> modification = new DataTreeModificationStub<>(instanceIdentifier, datastoreType, newObject);
        egressPlugin.steadyData(listenerKey, Collections.singletonList(modification));
    }

    protected <T extends DataObject> T getFederationEntity(String listenerKey, InstanceIdentifier<T> instanceIdentifier)
            throws ReadFailedException {
        LogicalDatastoreType datastoreType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        return tx.read(datastoreType, instanceIdentifier).checkedGet().get();
    }

    protected void setGenerationNumber() throws TransactionCommitFailedException {
        int generationNumberValue = 2;
        KeyedInstanceIdentifier<RemoteSiteGenerationInfo,
                RemoteSiteGenerationInfoKey> generationNumberPath = InstanceIdentifier
                        .create(FederationGenerations.class)
                        .child(RemoteSiteGenerationInfo.class, new RemoteSiteGenerationInfoKey(REMOTE_IP));
        RemoteSiteGenerationInfo generationNumber = new RemoteSiteGenerationInfoBuilder().setRemoteIp(REMOTE_IP)
                .setGenerationNumber(generationNumberValue).build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, generationNumberPath, generationNumber, true);
        tx.submit().checkedGet();
    }

    protected void assertSentMessages(int noMsg) {
        assertEquals(noMsg, sentMessages.size());
    }

    protected void assertNoSentMessages() {
        assertSentMessages(0);
    }

    protected class MergedObject {
        public DataObject obj;
        public InstanceIdentifier<?> insId;

        public MergedObject(DataObject obj, InstanceIdentifier<?> insId) {
            this.obj = obj;
            this.insId = insId;
        }
    }


    protected static InstanceIdentifier<Interface> getIetfInterfaceInstanceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName))
                .build();
    }

    protected static InstanceIdentifier<ElanInterface> getElanInterfaceInstanceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    protected static InstanceIdentifier<VpnInterface> getVpnInterfaceInstanceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(interfaceName)).build();
    }

    protected static InstanceIdentifier<Node> getTopologyNodeInstanceIdentifier(String nodeName) {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, FederationPluginConstants.OVSDB_TOPOLOGY_KEY)
                .child(Node.class, new NodeKey(new NodeId(new Uri(nodeName))));
    }

    @SuppressWarnings("deprecation")
    protected static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node>
        getInventoryNodeInstanceIdentifier(String nodeName) {
        return InstanceIdentifier.create(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey(
                                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(
                                        nodeName)));
    }
}
