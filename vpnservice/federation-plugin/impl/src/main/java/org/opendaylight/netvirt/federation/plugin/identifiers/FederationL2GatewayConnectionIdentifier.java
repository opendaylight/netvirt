/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class FederationL2GatewayConnectionIdentifier
        implements FederationPluginIdentifier<L2gatewayConnection, L2gatewayConnections, Neutron> {

    @Inject
    public FederationL2GatewayConnectionIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.L2_GATEWAY_CONNECTION_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
    }

    @Override
    public InstanceIdentifier<L2gatewayConnection> getInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class);
    }

    @Override
    public InstanceIdentifier<L2gatewayConnections> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class);
    }

    @Override
    public InstanceIdentifier<Neutron> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class);
    }

}
