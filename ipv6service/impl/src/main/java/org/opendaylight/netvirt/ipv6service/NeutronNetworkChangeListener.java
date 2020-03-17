/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.mtu.ext.rev181114.NetworkMtuExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronNetworkChangeListener extends AbstractClusteredAsyncDataTreeChangeListener<Network> {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronNetworkChangeListener.class);

    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    @Inject
    public NeutronNetworkChangeListener(final DataBroker dataBroker, IfMgr ifMgr) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Networks.class).child(Network.class),
                Executors.newListeningSingleThreadExecutor("NeutronNetworkChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<Network> identifier, Network input) {
        int mtu = 0;
        LOG.debug("Add Network notification handler is invoked {} ", input);
        if (input.augmentation(NetworkMtuExtension.class) != null) {
            mtu = input.augmentation(NetworkMtuExtension.class).getMtu().toJava();
        }
        ifMgr.addNetwork(input.getUuid(), mtu);
    }

    @Override
    public void remove(InstanceIdentifier<Network> identifier, Network input) {
        LOG.debug("Remove Network notification handler is invoked {} ", input);
        ifMgr.removeNetwork(input.getUuid());
    }

    @Override
    public void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        LOG.debug("Update Network notification handler is invoked...");
    }
}
