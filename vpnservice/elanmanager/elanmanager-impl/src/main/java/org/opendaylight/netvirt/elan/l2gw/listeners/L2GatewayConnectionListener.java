/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L2GatewayConnectionListener extends AsyncClusteredDataTreeChangeListenerBase<L2gatewayConnection,
        L2GatewayConnectionListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionListener.class);

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;

    public L2GatewayConnectionListener(final DataBroker db, L2GatewayConnectionUtils l2GatewayConnectionUtils) {
        super(L2gatewayConnection.class, L2GatewayConnectionListener.class);
        this.broker = db;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
    }

    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected void add(final InstanceIdentifier<L2gatewayConnection> identifier, final L2gatewayConnection input) {
        LOG.trace("Adding L2gatewayConnection: {}", input);

        // Get associated L2GwId from 'input'
        // Create logical switch in each of the L2GwDevices part of L2Gw
        // Logical switch name is network UUID
        // Add L2GwDevices to ELAN
        l2GatewayConnectionUtils.addL2GatewayConnection(input);
    }

    @Override
    protected void remove(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection input) {
        LOG.trace("Removing L2gatewayConnection: {}", input);

        l2GatewayConnectionUtils.deleteL2GatewayConnection(input);
    }

    @Override
    protected void update(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection original,
            L2gatewayConnection update) {
        LOG.trace("Updating L2gatewayConnection : original value={}, updated value={}", original, update);
    }

    @Override
    protected InstanceIdentifier<L2gatewayConnection> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
            .child(L2gatewayConnection.class);
    }

    @Override
    protected L2GatewayConnectionListener getDataTreeChangeListener() {
        return this;
    }
}
