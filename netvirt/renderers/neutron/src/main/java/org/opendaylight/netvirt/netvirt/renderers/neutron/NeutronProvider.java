/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.netvirt.renderers.neutron;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronProvider.class);

    private final DataBroker dataBroker;

//    private static final Entity ownerInstanceEntity = new Entity(Constants.NETVIRT_NEUTRON_OWNER_ENTITY_TYPE,
//            Constants.NETVIRT_NEUTRON_OWNER_ENTITY_TYPE);

    private NeutronPortChangeListener neutronPortChangeListener;
    private NeutronNetworkChangeListener neutronNetworkChangeListener;

    public NeutronProvider(final DataBroker dataBroker, final EntityOwnershipService eos) {
        LOG.info("Netvirt NeutronProvider created");
        this.dataBroker = dataBroker;
    }

//    public static boolean isMasterProviderInstance() {
//        if (entityOwnershipService != null) {
//            Optional<EntityOwnershipState> state = entityOwnershipService.getOwnershipState(ownerInstanceEntity);
//            return state.isPresent() && state.get().isOwner();
//        }
//        return false;
//    }

    public void start() {
        LOG.info("Netvirt NeutronProvider: start", dataBroker);
        neutronPortChangeListener = new NeutronPortChangeListener(dataBroker);
        neutronNetworkChangeListener = new NeutronNetworkChangeListener(dataBroker);
    }

    @Override
    public void close() throws Exception {
        if (neutronPortChangeListener != null) {
            neutronPortChangeListener.close();
        }
        if (neutronNetworkChangeListener != null) {
            neutronNetworkChangeListener.close();
        }
        LOG.info("Netvirt NeutronProvider Closed");
    }
}