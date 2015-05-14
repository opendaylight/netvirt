/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.InterfaceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIdsBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class InterfaceManagerTest {

    private String ifName = "dpn1-if0";
    Map<InstanceIdentifier<?>,DataObject> written = new HashMap<>();
    Map<InstanceIdentifier<?>,DataObject> updated = new HashMap<>();
    Set<InstanceIdentifier<?>> removed = new HashSet<>();

    @Mock DataBroker dataBroker;
    @Mock IdManager idManager;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;

    MockDataChangedEvent dataChangeEvent;
    InterfaceManager imgr;

    NodeConnectorId ncId;
    NodeConnector nc;
    Interface interf;
    InstanceIdentifier<Interface> ifIdent;
    InstanceIdentifier<NodeConnector> ncIdent;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifsIdent;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateIface;
    InstanceIdentifier<IdPool> poolIdent;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        dataChangeEvent = new MockDataChangedEvent();
        imgr = new InterfaceManager(dataBroker, idManager) {
            protected <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
                /*
                 * Do nothing for now. Ideally we should capture this information
                 * and use it to verify results.
                 */
                written.put(path, data);
            }
            protected <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
                updated.put(path, data);
            }

            protected <T extends DataObject> void asyncRemove(LogicalDatastoreType datastoreType,
                            InstanceIdentifier<T> path, FutureCallback<Void> callback) {
                removed.add(path);
            }

        };
        setupMocks();
    }

    private void setupMocks() {
        ncId = new NodeConnectorId("openflow:10:111");
        nc = buildNodeConnector(ncId);
        interf = buildInterface(ifName, "Test Interface1", true, L2vlan.class, ncId);
        ifIdent = IfmUtil.buildId(ifName);
        ncIdent = getNcIdent("openflow:10",ncId);
        ifsIdent = IfmUtil.buildStateInterfaceId(interf.getName());
        stateIface = buildStateInterface(ifName);
        poolIdent = IfmUtil.getPoolId("interfaces");

        // Setup mocks
        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testAdd() {
        Optional<Interface> expected = Optional.of(interf);
        Optional<NodeConnector> expectedNc = Optional.of(nc);
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                        LogicalDatastoreType.CONFIGURATION, ifIdent);
        doReturn(Futures.immediateCheckedFuture(expectedNc)).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, ncIdent);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, ifsIdent);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, poolIdent);

        dataChangeEvent.created.put(IfmUtil.buildId(ifName), interf);
        imgr.onDataChanged(dataChangeEvent);
        //Add some verifications
        assertEquals(1,written.size());
        assertEquals(0,updated.size());
        assertEquals(0, removed.size());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface writtenIface =
                        (org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface)written.get(ifsIdent);
        assertEquals(stateIface.getKey(), writtenIface.getKey());
        assertEquals(65536, writtenIface.getIfIndex().intValue());
        assertEquals(OperStatus.Up, writtenIface.getOperStatus());
    }

    @Test
    public void testUpdate() {
        Optional<Interface> expected = Optional.of(interf);
        Optional<NodeConnector> expectedNc = Optional.of(nc);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateIf = Optional.of(stateIface);
        doReturn(Futures.immediateCheckedFuture(expected)).when(mockReadTx).read(
                        LogicalDatastoreType.CONFIGURATION, ifIdent);
        doReturn(Futures.immediateCheckedFuture(expectedNc)).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, ncIdent);
        doReturn(Futures.immediateCheckedFuture(expectedStateIf)).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, ifsIdent);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(mockReadTx).read(
                        LogicalDatastoreType.OPERATIONAL, poolIdent);
        dataChangeEvent.original.put(IfmUtil.buildId(ifName), interf);
        dataChangeEvent.updated.put(IfmUtil.buildId(ifName), interf);
        imgr.onDataChanged(dataChangeEvent);
        //Add some verifications

        assertEquals(0,written.size());
        assertEquals(1,updated.size());
        assertEquals(0, removed.size());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface updatedIface =
                        (org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface)updated.get(ifsIdent);
        assertNotEquals(stateIface.getKey(),updatedIface.getKey());
        assertNull(updatedIface.getIfIndex());
        assertEquals(OperStatus.Up, updatedIface.getOperStatus());
    }

    private org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface buildStateInterface(
                    String ifName) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder =
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifName));
        return ifaceBuilder.build();
    }

    private InstanceIdentifier<NodeConnector> getNcIdent(String nodeKey, NodeConnectorId ncId) {
        return InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(new NodeId(nodeKey)))
                        .child(NodeConnector.class, new NodeConnectorKey(ncId))
                        .build();
    }

    private Interface buildInterface(String ifName, String desc, boolean enabled, Class<? extends InterfaceType> ifType, NodeConnectorId ncId) {
        InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
                        .setDescription(desc).setEnabled(enabled).setType(ifType);

        BaseIds baseId = new BaseIdsBuilder().setOfPortId(ncId).build();
        builder.addAugmentation(BaseIds.class, baseId);
        return builder.build();
    }

    private NodeConnector buildNodeConnector(NodeConnectorId ncId) {
        NodeConnectorBuilder ncBuilder = new NodeConnectorBuilder()
                        .setId(ncId)
                        .setKey(new NodeConnectorKey(ncId));
        return ncBuilder.build();
    }

}
