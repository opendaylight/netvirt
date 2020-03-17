/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronRouterChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Router,
        NeutronRouterChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    @Inject
    public NeutronRouterChangeListener(final DataBroker dataBroker, IfMgr ifMgr) {
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Router> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router.class);
    }

    @Override
    protected void add(InstanceIdentifier<Router> identifier, Router input) {
        LOG.info("Add Router notification handler is invoked {}.", input.getUuid());
        ifMgr.addRouter(input.getUuid(), input.getName(), input.getTenantId());
    }

    @Override
    protected void remove(InstanceIdentifier<Router> identifier, Router input) {
        LOG.info("Remove Router notification handler is invoked {}.", input.getUuid());
        ifMgr.removeRouter(input.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        LOG.debug("Update Router notification handler is invoked. Original: {}, Updated: {}.", original, update);
    }

    @Override
    protected NeutronRouterChangeListener getDataTreeChangeListener() {
        return NeutronRouterChangeListener.this;
    }

}
