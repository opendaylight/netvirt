/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayIdentifier
        implements FederationPluginIdentifier<L2gateway, L2gateways, Neutron> {

    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayIdentifier.class);

    @Inject
    public FederationL2GatewayIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.L2_GATEWAY_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public InstanceIdentifier<L2gateway> getInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class);
    }

    @Override
    public InstanceIdentifier<L2gateways> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class);
    }

    @Override
    public InstanceIdentifier<Neutron> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class);
    }

}
