/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers.HwVTEPConfigRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers.HwVTEPInterfaceConfigAddHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.interfaces._interface.NodeIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class HwVTEPConfigListener extends AsyncDataTreeChangeListenerBase<Interface, HwVTEPConfigListener> {
    private static final Logger LOG = LoggerFactory.getLogger(HwVTEPConfigListener.class);
    private DataBroker dataBroker;

    public HwVTEPConfigListener(final DataBroker dataBroker) {
        super(Interface.class, HwVTEPConfigListener.class);
        this.dataBroker = dataBroker;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface interfaceOld) {
        // HwVTEPs support only vxlan
        IfTunnel ifTunnel = interfaceOld.getAugmentation(IfTunnel.class);
        if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            ParentRefs parentRefs = interfaceOld.getAugmentation(ParentRefs.class);
            if (parentRefs != null) {
                for(NodeIdentifier nodeIdentifier : parentRefs.getNodeIdentifier()) {
                    if(nodeIdentifier.getNodeId().equals("hwvtep:1")) {
                        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                        RendererConfigRemoveWorker configWorker = new RendererConfigRemoveWorker(key, interfaceOld, parentRefs);
                        coordinator.enqueueJob(interfaceOld.getName(), configWorker);
                    }
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface interfaceOld, Interface interfaceNew) {
        // TODO
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface interfaceNew) {
        // HwVTEPs support only vxlan
        IfTunnel ifTunnel = interfaceNew.getAugmentation(IfTunnel.class);
        if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            ParentRefs parentRefs = interfaceNew.getAugmentation(ParentRefs.class);
            if (parentRefs != null) {
                for(NodeIdentifier nodeIdentifier : parentRefs.getNodeIdentifier()) {
                    if(nodeIdentifier.getNodeId().equals("hwvtep:1")) {
                        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                        RendererConfigAddWorker configWorker = new RendererConfigAddWorker(key, interfaceNew, parentRefs);
                        coordinator.enqueueJob(interfaceNew.getName(), configWorker);
                    }
                }
            }
        }
    }

    @Override
    protected HwVTEPConfigListener getDataTreeChangeListener() {
        return HwVTEPConfigListener.this;
    }

    private class RendererConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<Interface> key;
        Interface interfaceNew;
        IfL2vlan ifL2vlan;
        ParentRefs parentRefs;

        public RendererConfigAddWorker(InstanceIdentifier<Interface> key, Interface interfaceNew,
                                       ParentRefs parentRefs) {
            this.key = key;
            this.interfaceNew = interfaceNew;
            this.ifL2vlan = ifL2vlan;
            this.parentRefs = parentRefs;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return HwVTEPInterfaceConfigAddHelper.addConfiguration(dataBroker, parentRefs, interfaceNew);
        }
    }

    private class RendererConfigRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<Interface> key;
        Interface interfaceOld;
        ParentRefs parentRefs;

        public RendererConfigRemoveWorker(InstanceIdentifier<Interface> key, Interface interfaceOld,
                                          ParentRefs parentRefs) {
            this.key = key;
            this.interfaceOld = interfaceOld;
            this.parentRefs = parentRefs;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            return HwVTEPConfigRemoveHelper.removeConfiguration(dataBroker, interfaceOld, parentRefs);
        }
    }
}