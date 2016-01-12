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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronSubnetChangeListener extends AbstractDataChangeListener<Subnet> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;


    public NeutronSubnetChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Subnet.class);
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
        LOG.info("N_Subnet listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet.class),
                    NeutronSubnetChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager Subnet DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Subnet DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnet> identifier, Subnet input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Subnet : key: " + identifier + ", value=" + input);
        }
        nvpnManager.handleNeutronSubnetCreated(input.getUuid(), input.getNetworkId(), input.getTenantId());
    }

    @Override
    protected void remove(InstanceIdentifier<Subnet> identifier, Subnet input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing subnet : key: " + identifier + ", value=" + input);
        }
        nvpnManager.handleNeutronSubnetDeleted(input.getUuid(), input.getNetworkId(), null);
    }

    @Override
    protected void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Subnet : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
        nvpnManager.handleNeutronSubnetUpdated(update.getUuid(), update.getNetworkId(), update.getTenantId());
    }
}
