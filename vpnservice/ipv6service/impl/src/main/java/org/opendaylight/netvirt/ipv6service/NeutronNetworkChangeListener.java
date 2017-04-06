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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronNetworkChangeListener extends AsyncDataTreeChangeListenerBase<Network, NeutronNetworkChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    @Inject
    public NeutronNetworkChangeListener(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.ifMgr = IfMgr.getIfMgrInstance();
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Network> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class);
    }

    @Override
    protected void add(InstanceIdentifier<Network> identifier, Network input) {
        LOG.debug("Add Network notification handler is invoked {} ", input);
        ifMgr.addNetwork(input.getUuid());
    }

    @Override
    protected void remove(InstanceIdentifier<Network> identifier, Network input) {
        LOG.debug("Remove Network notification handler is invoked {} ", input);
        ifMgr.removeNetwork(input.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        LOG.debug("Update Network notification handler is invoked...");
    }

    /* (non-Javadoc)
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
    @Override
    protected NeutronNetworkChangeListener getDataTreeChangeListener() {
        return NeutronNetworkChangeListener.this;
    }

}
