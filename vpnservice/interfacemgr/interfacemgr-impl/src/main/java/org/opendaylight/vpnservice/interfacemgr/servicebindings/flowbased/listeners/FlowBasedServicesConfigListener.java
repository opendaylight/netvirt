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
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers.FlowBasedServicesConfigBindHelper;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers.FlowBasedServicesConfigUnbindHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class FlowBasedServicesConfigListener extends AsyncDataTreeChangeListenerBase<BoundServices, FlowBasedServicesConfigListener> {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesConfigListener.class);
    private DataBroker dataBroker;

    public FlowBasedServicesConfigListener(final DataBroker dataBroker) {
        super(BoundServices.class, FlowBasedServicesConfigListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    protected InstanceIdentifier<BoundServices> getWildCardPath() {
        return InstanceIdentifier.create(ServiceBindings.class).child(ServicesInfo.class)
                .child(BoundServices.class);
    }

    @Override
    protected void remove(InstanceIdentifier<BoundServices> key, BoundServices boundServiceOld) {
        String interfaceName = InstanceIdentifier.keyOf(key.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();
        LOG.info("Service Binding Entry removed for Interface: {}, Data: {}",
                interfaceName, boundServiceOld);

        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        RendererConfigRemoveWorker configWorker = new RendererConfigRemoveWorker(key, boundServiceOld);
        coordinator.enqueueJob(interfaceName, configWorker);
    }

    @Override
    protected void update(InstanceIdentifier<BoundServices> key, BoundServices boundServiceOld,
                          BoundServices boundServiceNew) {
        LOG.error("Service Binding entry update not allowed for: {}, Data: {}",
                InstanceIdentifier.keyOf(key.firstIdentifierOf(ServicesInfo.class)).getInterfaceName(), boundServiceNew);
    }

    @Override
    protected void add(InstanceIdentifier<BoundServices> key, BoundServices boundServicesNew) {
        String interfaceName = InstanceIdentifier.keyOf(key.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();
        LOG.info("Service Binding Entry created for Interface: {}, Data: {}",
                interfaceName, boundServicesNew);

        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        RendererConfigAddWorker configWorker = new RendererConfigAddWorker(key, boundServicesNew);
        coordinator.enqueueJob(interfaceName, configWorker);
    }

    @Override
    protected FlowBasedServicesConfigListener getDataTreeChangeListener() {
        return FlowBasedServicesConfigListener.this;
    }

    private class RendererConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<BoundServices> instanceIdentifier;
        BoundServices boundServicesNew;

        public RendererConfigAddWorker(InstanceIdentifier<BoundServices> instanceIdentifier,
                                       BoundServices boundServicesNew) {
            this.instanceIdentifier = instanceIdentifier;
            this.boundServicesNew = boundServicesNew;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return FlowBasedServicesConfigBindHelper.bindService(instanceIdentifier,
                    boundServicesNew, dataBroker);
        }
    }

    private class RendererConfigRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<BoundServices> instanceIdentifier;
        BoundServices boundServicesNew;

        public RendererConfigRemoveWorker(InstanceIdentifier<BoundServices> instanceIdentifier,
                                       BoundServices boundServicesNew) {
            this.instanceIdentifier = instanceIdentifier;
            this.boundServicesNew = boundServicesNew;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return FlowBasedServicesConfigUnbindHelper.unbindService(instanceIdentifier,
                    boundServicesNew, dataBroker);
        }
    }
}