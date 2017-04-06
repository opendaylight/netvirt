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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayModificationCreator
        implements FederationPluginModificationCreator<L2gateway, L2gateways> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayModificationCreator.class);

    @Inject
    public FederationL2GatewayModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.L2_GATEWAY_KEY, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public Collection<DataTreeModification<L2gateway>> createDataTreeModifications(L2gateways l2gateways) {
        if (l2gateways == null || l2gateways.getL2gateway() == null) {
            LOG.debug("No L2 Gateway found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<L2gateway>> modifications = new ArrayList<>();
        for (L2gateway l2gateway : l2gateways.getL2gateway()) {
            modifications.add(new FullSyncDataTreeModification<>(l2gateway));
        }

        return modifications;
    }

}
