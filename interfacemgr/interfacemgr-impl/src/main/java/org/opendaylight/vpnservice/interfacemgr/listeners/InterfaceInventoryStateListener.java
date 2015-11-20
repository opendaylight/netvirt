/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceStateUpdateHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 *
 * This Class is a Data Change Listener for FlowCapableNodeConnector updates.
 * This creates an entry in the interface-state OperDS for every node-connector used.
 *
 * NOTE: This class just creates an ifstate entry whose interface-name will be the same as the node-connector portname.
 * If PortName is not unique across DPNs, this implementation can have problems.
 */

public class InterfaceInventoryStateListener extends AsyncDataChangeListenerBase<FlowCapableNodeConnector, InterfaceInventoryStateListener> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceInventoryStateListener.class);
    private DataBroker dataBroker;

    public InterfaceInventoryStateListener(final DataBroker dataBroker) {
        super(FlowCapableNodeConnector.class, InterfaceInventoryStateListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    protected InstanceIdentifier<FlowCapableNodeConnector> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).child(NodeConnector.class)
                .augmentation(FlowCapableNodeConnector.class);
    }

    @Override
    protected DataChangeListener getDataChangeListener() {
        return InterfaceInventoryStateListener.this;
    }

    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNodeConnector> key,
                          FlowCapableNodeConnector flowCapableNodeConnectorOld) {
        LOG.debug("Received NodeConnector Remove Event: {}, {}", key, flowCapableNodeConnectorOld);
        String portName = flowCapableNodeConnectorOld.getName();
        NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(key.firstIdentifierOf(NodeConnector.class)).getId();
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();

        InterfaceStateRemoveWorker interfaceStateRemoveWorker = new InterfaceStateRemoveWorker(key,
                flowCapableNodeConnectorOld, portName);
        coordinator.enqueueJob(portName, interfaceStateRemoveWorker);
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNodeConnector> key, FlowCapableNodeConnector fcNodeConnectorOld,
                          FlowCapableNodeConnector fcNodeConnectorNew) {
        LOG.debug("Received NodeConnector Update Event: {}, {}, {}", key, fcNodeConnectorOld, fcNodeConnectorNew);
        String portName = fcNodeConnectorNew.getName();
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();

        InterfaceStateUpdateWorker interfaceStateUpdateWorker = new InterfaceStateUpdateWorker(key, fcNodeConnectorOld,
                fcNodeConnectorNew, portName);
        coordinator.enqueueJob(portName, interfaceStateUpdateWorker);
    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNodeConnector> key, FlowCapableNodeConnector fcNodeConnectorNew) {
        LOG.debug("Received NodeConnector Add Event: {}, {}", key, fcNodeConnectorNew);
        String portName = fcNodeConnectorNew.getName();
        NodeConnectorId nodeConnectorId = InstanceIdentifier.keyOf(key.firstIdentifierOf(NodeConnector.class)).getId();

        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceStateAddWorker ifStateAddWorker = new InterfaceStateAddWorker(nodeConnectorId,
                fcNodeConnectorNew, portName);
        coordinator.enqueueJob(portName, ifStateAddWorker);
    }

    private class InterfaceStateAddWorker implements Callable {
        private final NodeConnectorId nodeConnectorId;
        private final FlowCapableNodeConnector fcNodeConnectorNew;
        private final String portName;

        public InterfaceStateAddWorker(NodeConnectorId nodeConnectorId,
                                       FlowCapableNodeConnector fcNodeConnectorNew,
                                       String portName) {
            this.nodeConnectorId = nodeConnectorId;
            this.fcNodeConnectorNew = fcNodeConnectorNew;
            this.portName = portName;
        }

        @Override
        public Object call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
             return OvsInterfaceStateAddHelper.addState(dataBroker, nodeConnectorId,
                     portName, fcNodeConnectorNew);
        }

        @Override
        public String toString() {
            return "InterfaceStateAddWorker{" +
                    "nodeConnectorId=" + nodeConnectorId +
                    ", fcNodeConnectorNew=" + fcNodeConnectorNew +
                    ", portName='" + portName + '\'' +
                    '}';
        }
    }

    private class InterfaceStateUpdateWorker implements Callable {
        private InstanceIdentifier<FlowCapableNodeConnector> key;
        private final FlowCapableNodeConnector fcNodeConnectorOld;
        private final FlowCapableNodeConnector fcNodeConnectorNew;
        private String portName;


        public InterfaceStateUpdateWorker(InstanceIdentifier<FlowCapableNodeConnector> key,
                                          FlowCapableNodeConnector fcNodeConnectorOld,
                                          FlowCapableNodeConnector fcNodeConnectorNew,
                                          String portName) {
            this.key = key;
            this.fcNodeConnectorOld = fcNodeConnectorOld;
            this.fcNodeConnectorNew = fcNodeConnectorNew;
            this.portName = portName;
        }

        @Override
        public Object call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            return OvsInterfaceStateUpdateHelper.updateState(key, dataBroker, portName,
                    fcNodeConnectorNew, fcNodeConnectorOld);
        }

        @Override
        public String toString() {
            return "InterfaceStateUpdateWorker{" +
                    "key=" + key +
                    ", fcNodeConnectorOld=" + fcNodeConnectorOld +
                    ", fcNodeConnectorNew=" + fcNodeConnectorNew +
                    ", portName='" + portName + '\'' +
                    '}';
        }
    }

    private class InterfaceStateRemoveWorker implements Callable {
        InstanceIdentifier<FlowCapableNodeConnector> key;
        FlowCapableNodeConnector fcNodeConnectorOld;
        private final String portName;

        public InterfaceStateRemoveWorker(InstanceIdentifier<FlowCapableNodeConnector> key,
                                          FlowCapableNodeConnector fcNodeConnectorOld,
                                          String portName) {
            this.key = key;
            this.fcNodeConnectorOld = fcNodeConnectorOld;
            this.portName = portName;
        }

        @Override
        public Object call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            return OvsInterfaceStateRemoveHelper.removeState(key, dataBroker, portName, fcNodeConnectorOld);
        }

        @Override
        public String toString() {
            return "InterfaceStateRemoveWorker{" +
                    "key=" + key +
                    ", fcNodeConnectorOld=" + fcNodeConnectorOld +
                    ", portName='" + portName + '\'' +
                    '}';
        }
    }
}