/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterInterfaceListener extends AbstractDataChangeListener<Interfaces> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RouterInterfaceListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;

    public RouterInterfaceListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager) {
        super(Interfaces.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                getWildCardPath(), this, DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(RouterInterfacesMap.class).child(RouterInterfaces.class).
                child(Interfaces.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    protected void add(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("Add event - key: {}, value: {}", identifier, interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        final String interfaceName = interfaceInfo.getInterfaceId();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
                InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(interfaceName,
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                        LOG.debug("Handling interface {} in router {} add scenario", interfaceName, routerId);
                        writeTxn.put(LogicalDatastoreType.CONFIGURATION,
                                VpnUtil.getRouterInterfaceId(interfaceName),
                                VpnUtil.getRouterInterface(interfaceName, routerId), true);
                        LOG.debug("Added the Router {} and interface {} in the ODL-L3VPN RouterInterface map",
                                routerId, interfaceName);
                        vpnInterfaceManager.addToNeutronRouterDpnsMap(routerId, interfaceName, writeTxn);
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(writeTxn.submit());
                        return futures;
                    }
                });
    }

    @Override
    protected void remove(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("Remove event - key: {}, value: {}", identifier, interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        final String interfaceName = interfaceInfo.getInterfaceId();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(interfaceName,
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                        vpnInterfaceManager.removeFromNeutronRouterDpnsMap(routerId, interfaceName, writeTxn);
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(writeTxn.submit());
                        return futures;
                    }
                });
    }

    @Override
    protected void update(InstanceIdentifier<Interfaces> identifier, Interfaces original, Interfaces update) {
        LOG.trace("Update event - key: {}, original: {}, update: {}", identifier, original, update);
    }
}
