/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.statehelpers.FlowBasedServicesStateBindHelper;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.statehelpers.FlowBasedServicesStateUnbindHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class FlowBasedServicesInterfaceStateListener extends AsyncDataTreeChangeListenerBase<Interface, FlowBasedServicesInterfaceStateListener> {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesInterfaceStateListener.class);
    private DataBroker dataBroker;

    public FlowBasedServicesInterfaceStateListener(final DataBroker dataBroker) {
        super(Interface.class, FlowBasedServicesInterfaceStateListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface interfaceStateOld) {
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        RendererStateInterfaceUnbindWorker stateUnbindWorker =
                new RendererStateInterfaceUnbindWorker(interfaceStateOld);
        coordinator.enqueueJob(interfaceStateOld.getName(), stateUnbindWorker);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface interfaceStateOld, Interface interfaceStateNew) {
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        if (interfaceStateNew.getOperStatus() == Interface.OperStatus.Down) {
            RendererStateInterfaceUnbindWorker stateUnbindWorker =
                    new RendererStateInterfaceUnbindWorker(interfaceStateNew);
            coordinator.enqueueJob(interfaceStateNew.getName(), stateUnbindWorker);
            return;
        }

        RendererStateInterfaceBindWorker stateBindWorker = new RendererStateInterfaceBindWorker(interfaceStateNew);
        coordinator.enqueueJob(interfaceStateNew.getName(), stateBindWorker);
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface interfaceStateNew) {
        if (interfaceStateNew.getOperStatus() == Interface.OperStatus.Down) {
            LOG.info("Interface: {} operstate is down when adding. Not Binding services", interfaceStateNew.getName());
            return;
        }

        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        RendererStateInterfaceBindWorker stateBindWorker = new RendererStateInterfaceBindWorker(interfaceStateNew);
        coordinator.enqueueJob(interfaceStateNew.getName(), stateBindWorker);
    }

    @Override
    protected FlowBasedServicesInterfaceStateListener getDataTreeChangeListener() {
        return FlowBasedServicesInterfaceStateListener.this;
    }

    private class RendererStateInterfaceBindWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface iface;

        public RendererStateInterfaceBindWorker(Interface iface) {
            this.iface = iface;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return FlowBasedServicesStateBindHelper.bindServicesOnInterface(iface, dataBroker);
        }
    }

    private class RendererStateInterfaceUnbindWorker implements Callable<List<ListenableFuture<Void>>> {
        Interface iface;

        public RendererStateInterfaceUnbindWorker(Interface iface) {
            this.iface = iface;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return FlowBasedServicesStateUnbindHelper.unbindServicesFromInterface(iface, dataBroker);
        }
    }
}