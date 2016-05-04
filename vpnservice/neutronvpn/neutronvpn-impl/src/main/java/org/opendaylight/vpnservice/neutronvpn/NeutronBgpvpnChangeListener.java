/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class NeutronBgpvpnChangeListener extends AbstractDataChangeListener<Bgpvpn> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private NeutronvpnManager nvpnManager;


    public NeutronBgpvpnChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Bgpvpn.class);
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
        LOG.info("N_Bgpvpn listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Bgpvpns.class).child(Bgpvpn.class),
                    NeutronBgpvpnChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Bgpvpn DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Bgpvpn DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Bgpvpn : key: " + identifier + ", value=" + input);
        }
        // Create internal VPN
        // handle route-target
        List<String> irt = new ArrayList<String>();
        List<String> ert = new ArrayList<String>();
        List<String> inrt = input.getRouteTargets();
        List<String> inirt = input.getImportTargets();
        List<String> inert = input.getExportTargets();
        if (inrt != null && !inrt.isEmpty()) {
            irt.addAll(inrt);
            ert.addAll(inrt);
        }
        if (inirt != null && !inirt.isEmpty()) {
            irt.addAll(inirt);
        }
        if (inert != null && !inert.isEmpty()) {
            ert.addAll(inert);
        }
        List<String> rd = input.getRouteDistinguishers();

        if (rd == null || rd.isEmpty()) {
            // generate new RD
        }
        Uuid router = null;
        if (input.getRouters() != null && !input.getRouters().isEmpty()) {
            // currently only one router
            router = input.getRouters().get(0);
        }
        nvpnManager.createL3Vpn(input.getUuid(), input.getName(), input.getTenantId(),
                rd, irt, ert, router, input.getNetworks());
    }

    @Override
    protected void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Bgpvpn : key: " + identifier + ", value=" + input);
        }
        nvpnManager.removeL3Vpn(input.getUuid());

    }

    @Override
    protected void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
        List<Uuid> oldNetworks = original.getNetworks();
        List<Uuid> newNetworks = update.getNetworks();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Update Bgpvpn : key: " + identifier + ", value=" + update);
        }
        if (newNetworks != null && !newNetworks.isEmpty()) {
            if (oldNetworks != null && !oldNetworks.isEmpty()) {
                if (oldNetworks != newNetworks) {
                    Iterator<Uuid> iter = newNetworks.iterator();
                    while (iter.hasNext()) {
                        Object net = iter.next();
                        if (oldNetworks.contains(net)) {
                            oldNetworks.remove(net);
                            iter.remove();
                        }
                    }
                    //clear removed networks
                    if (!oldNetworks.isEmpty()) {
                        LOG.trace("Removing old networks {} ", oldNetworks);
                        nvpnManager.dissociateNetworksFromVpn(update.getUuid(), oldNetworks);
                    }
                    //add new (Delta) Networks
                    if (!newNetworks.isEmpty()) {
                        LOG.trace("Adding delta New networks {} ", newNetworks);
                        nvpnManager.associateNetworksToVpn(update.getUuid(), newNetworks);
                    }
                }
            } else {
                //add new Networks
                LOG.trace("Adding New networks {} ", newNetworks);
                nvpnManager.associateNetworksToVpn(update.getUuid(), newNetworks);
            }
        }
        // ### TBD : Handle routers
    }

}