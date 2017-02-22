/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.end2end;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.federation.service.api.IConsumerManagement;
import org.opendaylight.federation.service.api.message.WrapperEntityFederationMessage;
import org.opendaylight.federation.service.impl.FederationProducerMgr;
import org.opendaylight.federation.service.impl.WrapperConsumer;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.messagequeue.AbstractFederationMessage;
import org.opendaylight.messagequeue.IMessageBusClient;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.federation.plugin.FederatedNetworkPair;
import org.opendaylight.netvirt.federation.plugin.FederationPluginEgress;
import org.opendaylight.netvirt.federation.plugin.FederationPluginIngress;
import org.opendaylight.netvirt.federation.plugin.FederationPluginMgr;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.SubnetVpnAssociationManager;
import org.opendaylight.netvirt.federation.plugin.creators.FederationElanInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationIetfInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationInventoryNodeModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationTopologyNodeModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationVpnInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.filters.FederationElanInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationIetfInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationInventoryNodeFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationTopologyNodeFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationVpnInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationElanInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationIetfInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationInventoryNodeIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationTopologyNodeIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationVpnInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationElanInterfaceTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationIetfInterfaceTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationInventoryNodeTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationTopologyNodeTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationVpnInterfaceTransformer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.FederationGenerations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.federation.service.config.rev161110.FederationConfigData;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

public class AbstractEnd2EndTest {

    protected DataBroker dataBroker = mock(DataBroker.class);
    protected IElanService elanService = mock(IElanService.class);
    protected SubnetVpnAssociationManager associationMgr = mock(SubnetVpnAssociationManager.class);
    protected LinkedList<AbstractFederationMessage> sentMessages;
    public LinkedList<MergedObject> mergedObjects = new LinkedList<>();

    protected FederationProducerMgr producer;
    protected FederationPluginEgress egressPlugin;
    protected final HashMap<String, DataTreeChangeListener<?>> keyToListener = new HashMap<>();

    @Mock
    protected ReadOnlyTransaction mockReadTx;

    protected WriteTransaction stubWriteTx = mock(WriteTransaction.class);

    protected IMessageBusClient msgBusConsumerMock = mock(IMessageBusClient.class);

    protected ClusterSingletonServiceProvider singletonService = mock(ClusterSingletonServiceProvider.class);

    protected IConsumerManagement consumerMgr = mock(IConsumerManagement.class);

    @Mock
    protected FederationPluginMgr mgr;

    protected FederationPluginIngress ingressPlugin;

    protected WrapperConsumer wrapperConsumer;

    @Mock
    FederationConfigData configMock;

    @SuppressWarnings("rawtypes")
    protected HashMap<String, ArgumentCaptor<DataTreeChangeListener>> listenerKeyToCaptor;

    protected final ArgumentCaptor<AbstractFederationMessage> msgCaptor = ArgumentCaptor
            .forClass(AbstractFederationMessage.class);

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

    public AbstractEnd2EndTest() {
        FederationInventoryNodeTransformer inventoryNodeTransformer = new FederationInventoryNodeTransformer();
        new FederationInventoryNodeFilter(dataBroker, inventoryNodeTransformer);
        new FederationInventoryNodeModificationCreator();
        new FederationInventoryNodeIdentifier();
        new FederationTopologyNodeTransformer();
        new FederationTopologyNodeFilter();
        new FederationTopologyNodeModificationCreator();
        new FederationTopologyNodeIdentifier();
        new FederationIetfInterfaceTransformer();
        new FederationIetfInterfaceFilter(dataBroker, elanService);
        new FederationIetfInterfaceModificationCreator();
        new FederationIetfInterfaceIdentifier();
        new FederationElanInterfaceTransformer();
        new FederationElanInterfaceFilter(dataBroker, elanService);
        new FederationElanInterfaceModificationCreator();
        new FederationElanInterfaceIdentifier();
        new FederationVpnInterfaceFilter(dataBroker, associationMgr);
        new FederationVpnInterfaceTransformer(associationMgr);
        new FederationVpnInterfaceModificationCreator();
        new FederationVpnInterfaceIdentifier();
    }

    public void initialization() {
        sentMessages = new LinkedList<>();
        producer = new FederationProducerMgr(msgBusConsumerMock, dataBroker, configMock, singletonService, consumerMgr);
        producer.attachPluginFactory("netvirt", (payload, queueName, contextId) -> egressPlugin);
        List<FederatedNetworkPair> federatedNetworkPairs = Arrays.asList(new FederatedNetworkPair(CONSUMER_NETWORK_ID,
                PRODUCER_NETWORK_ID, CONSUMER_SUBNET_ID, PRODUCER_SUBNET_ID, LOCAL_TENANT_ID, REMOTE_TENANT_ID));
        egressPlugin = new FederationPluginEgress(producer, federatedNetworkPairs, CONSUMER1_QUEUE, CONSUMER1_QUEUE);
        ingressPlugin = new FederationPluginIngress(mgr, dataBroker, REMOTE_IP, federatedNetworkPairs);
        wrapperConsumer = new WrapperConsumer(REMOTE_IP, ingressPlugin);

        prepareMocks();
    }

    private void prepareMocks() {
        HashMap<String, DataTreeIdentifier<?>> listenerKeyToIdentifer = new HashMap<>();
        listenerKeyToCaptor = new HashMap<>();
        egressPlugin.getListenersData().forEach(p -> listenerKeyToIdentifer.put(p.listenerId, p.listenerPath));
        egressPlugin.getListenersData().forEach(
            p -> listenerKeyToCaptor.put(p.listenerId, ArgumentCaptor.forClass(DataTreeChangeListener.class)));
        for (String key : FederationPluginUtils.getOrderedListenerKeys()) {
            when(dataBroker.registerDataTreeChangeListener(eq(listenerKeyToIdentifer.get(key)),
                    listenerKeyToCaptor.get(key).capture())).thenReturn(null);
        }

        doAnswer(invocation -> {
            AbstractFederationMessage msg = (AbstractFederationMessage) invocation.getArguments()[0];
            sentMessages.add(msg);
            wrapperConsumer.consumeMsg(msg);
            return null;
        }).when(msgBusConsumerMock).sendMsg(any(), anyString());

        doReturn("2.2.2.2").when(configMock).getMqBrokerIp();
        doReturn(stubWriteTx).when(dataBroker).newWriteOnlyTransaction();
        when(associationMgr.getSubnetVpn(any())).thenReturn("dummySubnetVpn");
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();

        when(stubWriteTx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        doAnswer(invocation -> {
            InstanceIdentifier<?> path = (InstanceIdentifier<?>) invocation.getArguments()[1];
            DataObject data = (DataObject) invocation.getArguments()[2];
            mergedObjects.add(new MergedObject(data, path));
            return null;
        }).when(stubWriteTx).merge(any(), any(), any());
    }

    protected class MergedObject {
        public DataObject obj;
        public InstanceIdentifier<?> insId;

        public MergedObject(DataObject obj, InstanceIdentifier<?> insId) {
            this.obj = obj;
            this.insId = insId;
        }
    }

    public int mergedObjectsAmount() {
        return mergedObjects.size();
    }

    protected <T extends DataObject> T removeLastMerged(Class<T> type) {
        return type.cast(mergedObjects.removeLast().obj);
    }

    protected <T extends DataObject> T removeFirstMerged(Class<T> type) {
        return type.cast(mergedObjects.removeFirst().obj);
    }

    protected String lastSentJson() {
        AbstractFederationMessage lastMsg = sentMessages.getLast();
        WrapperEntityFederationMessage wrapper = (WrapperEntityFederationMessage) lastMsg;
        return wrapper.getPayload().getJsonInput();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void dcn(String listenerKey, DataObject newObject) {
        DataTreeChangeListener listener = getListenerForKey(listenerKey);
        listener.onDataTreeChanged(change(newObject, FederationPluginUtils.getListenerDatastoreType(listenerKey),
                FederationPluginUtils.getInstanceIdentifier(listenerKey)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void dcns(String listenerKey, List<? extends DataObject> newObjects) {
        DataTreeChangeListener listener = getListenerForKey(listenerKey);
        listener.onDataTreeChanged(changes(FederationPluginUtils.getListenerDatastoreType(listenerKey),
                FederationPluginUtils.getInstanceIdentifier(listenerKey), newObjects));
    }

    protected DataTreeChangeListener<?> getListenerForKey(String listenerKey) {
        return listenerKeyToCaptor.get(listenerKey).getValue();
    }

    protected Collection<DataTreeModification<?>> changes(LogicalDatastoreType datastoreType,
            InstanceIdentifier<? extends DataObject> instanceIdentifier, List<? extends DataObject> dataObjects) {
        ArrayList<DataTreeModification<?>> changes = new ArrayList<>();
        dataObjects.forEach(data -> changes.add(new FakeDataTreeModification(data, datastoreType, instanceIdentifier)));
        return changes;
    }

    protected Collection<?> change(DataObject newObject, LogicalDatastoreType datastoreType,
            InstanceIdentifier<? extends DataObject> instanceIdentifier) {
        ArrayList<DataTreeModification<?>> changes = new ArrayList<>();
        changes.add(new FakeDataTreeModification(newObject, datastoreType, instanceIdentifier));
        return changes;
    }

    protected void setGenerationNumberMock() {
        int generationNumberValue = 2;
        KeyedInstanceIdentifier<RemoteSiteGenerationInfo, RemoteSiteGenerationInfoKey>
            generationNumberPath = InstanceIdentifier.create(FederationGenerations.class)
                .child(RemoteSiteGenerationInfo.class, new RemoteSiteGenerationInfoKey(REMOTE_IP));
        RemoteSiteGenerationInfo generationNumber = new RemoteSiteGenerationInfoBuilder().setRemoteIp(REMOTE_IP)
                .setGenerationNumber(generationNumberValue).build();
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION, generationNumberPath))
                .thenReturn(Futures.immediateCheckedFuture(Optional.of(generationNumber)));
    }

    @SuppressWarnings("rawtypes")
    public class FakeDataTreeModification implements DataTreeModification {

        private final FakeDataObjectModification fakeMod;
        private final LogicalDatastoreType dataStoreType;
        private final InstanceIdentifier<? extends DataObject> instanceIdentifier;

        public FakeDataTreeModification(DataObject theObject, LogicalDatastoreType datastoreType,
                InstanceIdentifier<? extends DataObject> instanceIdentifier) {
            fakeMod = new FakeDataObjectModification(theObject);
            this.dataStoreType = datastoreType;
            this.instanceIdentifier = instanceIdentifier;
        }

        @SuppressWarnings("unchecked")
        @Override
        public DataTreeIdentifier<?> getRootPath() {
            return new DataTreeIdentifier(dataStoreType, instanceIdentifier);
        }

        @Override
        public DataObjectModification<?> getRootNode() {
            return fakeMod;
        }

    }

    @SuppressWarnings("rawtypes")
    public class FakeDataObjectModification implements DataObjectModification {

        private final DataObject theObject;

        private final ModificationType modType = ModificationType.WRITE;

        public FakeDataObjectModification(DataObject theObject) {
            this.theObject = theObject;
        }

        @Override
        public PathArgument getIdentifier() {
            return null;
        }

        @Override
        public Class getDataType() {
            return null;
        }

        @Override
        public ModificationType getModificationType() {
            return modType;
        }

        @Override
        public DataObject getDataBefore() {
            return null;
        }

        @Override
        public DataObject getDataAfter() {
            return theObject;
        }

        @Override
        public Collection<?> getModifiedChildren() {
            return null;
        }

        @Override
        public DataObjectModification<?> getModifiedChildContainer(Class child) {
            return null;
        }

        @Override
        public DataObjectModification<?> getModifiedAugmentation(Class augmentation) {
            return null;
        }

        @Override
        public DataObjectModification<?> getModifiedChildListItem(Class listItem, Identifier listKey) {
            return null;
        }

        @Override
        public DataObjectModification<?> getModifiedChild(PathArgument childArgument) {
            return null;
        }

    }
}
