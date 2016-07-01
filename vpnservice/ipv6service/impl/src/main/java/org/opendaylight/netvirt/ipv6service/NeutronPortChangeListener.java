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
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronPortChangeListener extends AbstractDataChangeListener<Port> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private NotificationService notificationService;

    public NeutronPortChangeListener(final DataBroker db) {
        super(Port.class);
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("Neutron Port listener Closed");
    }


    private void registerListener(final DataBroker db) {
        listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class),
                NeutronPortChangeListener.this, DataChangeScope.SUBTREE);
    }

    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port input) {
        LOG.info("Add port notification handler is invoked...");
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        LOG.info("remove port notification handler is invoked...");
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        LOG.info("update port notification handler is invoked...");
    }
}
