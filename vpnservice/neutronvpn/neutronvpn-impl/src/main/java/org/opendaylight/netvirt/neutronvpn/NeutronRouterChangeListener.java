/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class NeutronRouterChangeListener extends AbstractDataChangeListener<Router> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;
    private NeutronvpnNatManager nvpnNatManager;


    public NeutronRouterChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr, NeutronvpnNatManager nVpnNatMgr) {
        super(Router.class);
        broker = db;
        nvpnManager = nVpnMgr;
        nvpnNatManager = nVpnNatMgr;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Router listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router.class),
                    NeutronRouterChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Router DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Router DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Router> identifier, Router input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Router : key: " + identifier + ", value=" + input);
        }
        // Create internal VPN
        nvpnManager.createL3Vpn(input.getUuid(), null, null, null, null, null, input.getUuid(), null);
    }

    @Override
    protected void remove(InstanceIdentifier<Router> identifier, Router input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing router : key: " + identifier + ", value=" + input);
        }
        Uuid routerId = input.getUuid();
        //NOTE: Pass an empty routerSubnetIds list, as router interfaces
        //will be removed from VPN by invocations from NeutronPortChangeListener
        List<Uuid> routerSubnetIds = new ArrayList<>();
        nvpnManager.handleNeutronRouterDeleted(routerId, routerSubnetIds);
        // Handle router deletion for the NAT service
        if (input.getExternalGatewayInfo() != null) {
            Uuid extNetId = input.getExternalGatewayInfo().getExternalNetworkId();
            nvpnNatManager.removeExternalNetworkFromRouter(extNetId, input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Router : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
        Uuid routerId = update.getUuid();
        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
        // internal vpn always present in case external vpn not found
        if (vpnId == null) {
            vpnId = routerId;
        }
        List<Routes> oldRoutes = (original.getRoutes() != null) ? original.getRoutes() : new ArrayList<>();
        List<Routes> newRoutes = (update.getRoutes() != null) ? update.getRoutes() : new ArrayList<>();

        if (!oldRoutes.equals(newRoutes)) {
            Iterator<Routes> iterator = newRoutes.iterator();
            while (iterator.hasNext()) {
                Routes route = iterator.next();
                if (oldRoutes.remove(route)) {
                    iterator.remove();
                }
            }
            nvpnManager.addAdjacencyforExtraRoute(newRoutes, true, null);
            if (!oldRoutes.isEmpty()) {
                nvpnManager.removeAdjacencyforExtraRoute(oldRoutes);
            }
        }
        nvpnNatManager.handleExternalNetworkForRouter(original, update);
    }
}
