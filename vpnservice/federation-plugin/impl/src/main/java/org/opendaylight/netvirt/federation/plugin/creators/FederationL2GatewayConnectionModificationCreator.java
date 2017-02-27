/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayConnectionModificationCreator
        implements FederationPluginModificationCreator<L2gatewayConnection, L2gatewayConnections> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayConnectionModificationCreator.class);

    @Inject
    public FederationL2GatewayConnectionModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.L2_GATEWAY_CONNECTION_KEY, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public Collection<DataTreeModification<L2gatewayConnection>> createDataTreeModifications(
            L2gatewayConnections l2gatewayConnections) {
        if (l2gatewayConnections == null || l2gatewayConnections.getL2gatewayConnection() == null) {
            LOG.debug("No L2 Gateway connection found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<L2gatewayConnection>> modifications = new ArrayList<>();
        for (L2gatewayConnection l2gateway : l2gatewayConnections.getL2gatewayConnection()) {
            modifications.add(new FullSyncDataTreeModification<>(l2gateway));
        }

        return modifications;
    }

}
