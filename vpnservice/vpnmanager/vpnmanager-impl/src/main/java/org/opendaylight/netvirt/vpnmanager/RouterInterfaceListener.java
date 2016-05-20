/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
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

    private final DataBroker broker;
    private final VpnInterfaceManager vpnInterfaceManager;

    public RouterInterfaceListener(final DataBroker db, final VpnInterfaceManager vpnInterfaceManager) {
        super(Interfaces.class);
        this.broker = db;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    public void start() {
        listenerRegistration = broker.registerDataChangeListener(
                LogicalDatastoreType.CONFIGURATION, getWildCardPath(),
                RouterInterfaceListener.this, DataChangeScope.SUBTREE);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(RouterInterfacesMap.class).child(RouterInterfaces.class).child(Interfaces.class);
    }

    @Override
    protected void add(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("Add event - key: {}, value: {}", identifier, interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        String interfaceName = interfaceInfo.getInterfaceId();

        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                VpnUtil.getRouterInterfaceId(interfaceName), VpnUtil.getRouterInterface(interfaceName, routerId));

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface interfaceState =
                InterfaceUtils.getInterfaceStateFromOperDS(broker, interfaceName);
        if (interfaceState != null) {
            LOG.debug("Handling interface {} in router {} add scenario", interfaceName, routerId);
            vpnInterfaceManager.addToNeutronRouterDpnsMap(routerId, interfaceName);
        } else {
            LOG.warn("Interface {} not yet operational to handle router interface add event in router {}", interfaceName, routerId);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interfaces> identifier, Interfaces interfaceInfo) {
        LOG.trace("Remove event - key: {}, value: {}", identifier, interfaceInfo);
        final String routerId = identifier.firstKeyOf(RouterInterfaces.class).getRouterId().getValue();
        String interfaceName = interfaceInfo.getInterfaceId();
        vpnInterfaceManager.removeFromNeutronRouterDpnsMap(routerId, interfaceName);
    }

    @Override
    protected void update(InstanceIdentifier<Interfaces> identifier, Interfaces original, Interfaces update) {
        LOG.trace("Update event - key: {}, original: {}, update: {}", identifier, original, update);
    }

}
