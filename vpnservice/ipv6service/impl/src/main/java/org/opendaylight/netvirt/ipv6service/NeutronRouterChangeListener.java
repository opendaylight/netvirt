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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronRouterChangeListener extends AbstractDataChangeListener<Router> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private IfMgr ifMgr;
    private final DataBroker broker;

    public NeutronRouterChangeListener(final DataBroker db) {
        super(Router.class);
        broker = db;
        this.ifMgr = IfMgr.getIfMgrInstance();
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
        LOG.info("Neutron Router listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router.class),
                    NeutronRouterChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("IPv6Service Neutron Router DataChange listener registration fail!", e);
            throw new IllegalStateException("IPv6Service Neutron Router DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Router> identifier, Router input) {
        LOG.info("Add Router notification handler is invoked...");
        ifMgr.addRouter(input.getUuid(), input.getName(), input.getTenantId(), input.isAdminStateUp());
    }

    @Override
    protected void remove(InstanceIdentifier<Router> identifier, Router input) {
        LOG.info("Remove Router notification handler is invoked...");
        ifMgr.removeRouter(input.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        LOG.info("Update Router notification handler is invoked...");
    }
}
