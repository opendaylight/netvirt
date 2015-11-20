/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceTopologyStateAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.statehelpers.OvsInterfaceTopologyStateRemoveHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class InterfaceTopologyStateListener extends AsyncDataChangeListenerBase<OvsdbBridgeAugmentation, InterfaceTopologyStateListener> {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceTopologyStateListener.class);
    private DataBroker dataBroker;

    public InterfaceTopologyStateListener(DataBroker dataBroker) {
        super(OvsdbBridgeAugmentation.class, InterfaceTopologyStateListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    protected InstanceIdentifier<OvsdbBridgeAugmentation> getWildCardPath() {
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class)
                .child(Node.class).augmentation(OvsdbBridgeAugmentation.class).build();
    }

    @Override
    protected DataChangeListener getDataChangeListener() {
        return InterfaceTopologyStateListener.this;
    }

    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    @Override
    protected void remove(InstanceIdentifier<OvsdbBridgeAugmentation> identifier, OvsdbBridgeAugmentation bridgeOld) {
        LOG.debug("Received Remove DataChange Notification for identifier: {}, ovsdbBridgeAugmentation: {}",
                identifier, bridgeOld);
        DataStoreJobCoordinator jobCoordinator = DataStoreJobCoordinator.getInstance();
        RendererStateRemoveWorker rendererStateRemoveWorker = new RendererStateRemoveWorker(identifier, bridgeOld);
        jobCoordinator.enqueueJob(bridgeOld.getBridgeName().getValue() + bridgeOld.getDatapathId(), rendererStateRemoveWorker);
    }

    @Override
    protected void update(InstanceIdentifier<OvsdbBridgeAugmentation> identifier, OvsdbBridgeAugmentation bridgeOld,
                          OvsdbBridgeAugmentation bridgeNew) {
        LOG.info("Received Update DataChange Notification for identifier: {}, ovsdbBridgeAugmentation old: {}, new: {}." +
                "No Action Performed.", identifier, bridgeOld, bridgeNew);
    }

    @Override
    protected void add(InstanceIdentifier<OvsdbBridgeAugmentation> identifier, OvsdbBridgeAugmentation bridgeNew) {
        LOG.debug("Received Add DataChange Notification for identifier: {}, ovsdbBridgeAugmentation: {}",
                identifier, bridgeNew);
        DataStoreJobCoordinator jobCoordinator = DataStoreJobCoordinator.getInstance();
        RendererStateAddWorker rendererStateAddWorker = new RendererStateAddWorker(identifier, bridgeNew);
        jobCoordinator.enqueueJob(bridgeNew.getBridgeName().getValue() + bridgeNew.getDatapathId(), rendererStateAddWorker);
    }

    private class RendererStateAddWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<OvsdbBridgeAugmentation> instanceIdentifier;
        OvsdbBridgeAugmentation bridgeNew;


        public RendererStateAddWorker(InstanceIdentifier<OvsdbBridgeAugmentation> instanceIdentifier,
                                      OvsdbBridgeAugmentation bridgeNew) {
            this.instanceIdentifier = instanceIdentifier;
            this.bridgeNew = bridgeNew;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            return OvsInterfaceTopologyStateAddHelper.addPortToBridge(instanceIdentifier,
                    bridgeNew, dataBroker);
        }
    }

    private class RendererStateRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<OvsdbBridgeAugmentation> instanceIdentifier;
        OvsdbBridgeAugmentation bridgeNew;


        public RendererStateRemoveWorker(InstanceIdentifier<OvsdbBridgeAugmentation> instanceIdentifier,
                                         OvsdbBridgeAugmentation bridgeNew) {
            this.instanceIdentifier = instanceIdentifier;
            this.bridgeNew = bridgeNew;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            return OvsInterfaceTopologyStateRemoveHelper.removePortFromBridge(instanceIdentifier,
                    bridgeNew, dataBroker);
        }
    }
}