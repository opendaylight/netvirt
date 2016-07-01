/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
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

    public NeutronSubnetChangeListener(final DataBroker db) {
        super(Subnet.class);
        broker = db;
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
        LOG.info("Neutron Subnet listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet.class),
                    NeutronSubnetChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("IPv6Service Neutron Subnet DataChange listener registration fail!", e);
            throw new IllegalStateException("IPv6Service Neutron Subnet DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnet> identifier, Subnet input) {
        LOG.info("Add Subnet notification handler is invoked...");
    }

    @Override
    protected void remove(InstanceIdentifier<Subnet> identifier, Subnet input) {
        LOG.info("Remove Subnet notification handler is invoked...");
    }

    @Override
    protected void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        LOG.info("Update Subnet notification handler is invoked...");
    }
}
