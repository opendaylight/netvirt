/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.vpnmaps.VpnMap;
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


    public NeutronRouterChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Router.class);
        broker = db;
        nvpnManager = nVpnMgr;
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
        // check if this router has internal-VPN
        Uuid routerId = input.getUuid();
        VpnMap vpnmap = NeutronvpnUtils.getVpnMap(broker, routerId);
        if (vpnmap != null) {
            // if yes, remove corresponding internal vpn
            LOG.trace("removing internal-vpn for router {}", routerId);
            nvpnManager.removeL3Vpn(routerId);
        } else {
            // if not, it is associated with some VPN
            // remove VPN-router association
            Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId);
            LOG.trace("dissociating router {} from vpn {}", routerId, vpnId);
            nvpnManager.dissociateRouterFromVpn(vpnId, routerId);
        }

    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Router : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
        Uuid routerId = update.getUuid();
        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId);
        List<Interfaces> oldInterfaces = (original.getInterfaces() != null) ? original.getInterfaces() : new
                ArrayList<Interfaces>();
        List<Interfaces> newInterfaces = (update.getInterfaces() != null) ? update.getInterfaces() : new
                ArrayList<Interfaces>();
        List<String> oldRoutes = (original.getRoutes() != null) ? original.getRoutes() : new ArrayList<String>();
        List<String> newRoutes = (update.getRoutes() != null) ? update.getRoutes() : new ArrayList<String>();

        if (!oldInterfaces.equals(newInterfaces)) {
            for (Interfaces intrf : newInterfaces) {
                if (!oldInterfaces.remove(intrf)) {
                    // add new subnet
                    nvpnManager.addSubnetToVpn(vpnId, intrf.getSubnetId());
                }
            }
            //clear remaining old subnets
            for (Interfaces intrf : oldInterfaces) {
                nvpnManager.removeSubnetFromVpn(vpnId, intrf.getSubnetId());
            }
        }
        if (!oldRoutes.equals(newRoutes)) {
            Iterator<String> iterator = newRoutes.iterator();
            while (iterator.hasNext()) {
                String route = iterator.next();
                if (oldRoutes.remove(route)) {
                    iterator.remove();
                }
            }
            nvpnManager.addAdjacencyforExtraRoute(newRoutes, true, null);
            if (!oldRoutes.isEmpty()) {
                nvpnManager.removeAdjacencyforExtraRoute(oldRoutes);
            }
        }
    }
}
