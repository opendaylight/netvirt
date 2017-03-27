/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwConnectionShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayConnectionFilter implements FederationPluginFilter<L2gatewayConnection,
        Neutron> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayConnectionFilter.class);

    @Inject
    public FederationL2GatewayConnectionFilter(final DataBroker dataBroker) {
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.L2_GATEWAY_CONNECTION_KEY, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public FilterResult applyEgressFilter(L2gatewayConnection l2Gateway, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<L2gatewayConnection> dataTreeModification) {
        if (isShadow(l2Gateway)) {
            LOG.trace("Interface {} filtered out. Reason: shadow interface", l2Gateway.getName());
            return FilterResult.DENY;
        }

        String networkName = l2Gateway.getNetworkId().getValue();
        if (!federatedMappings.containsProducerNetworkId(networkName)) {
            LOG.trace("Interface {} filtered out. Reason: network {} not federated", l2Gateway.getName(), networkName);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, Neutron neutron) {
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(L2gatewayConnection l2Gateway) {
        L2gwConnectionShadowProperties l2ShadowProperties = l2Gateway
                .getAugmentation(L2gwConnectionShadowProperties.class);
        return l2ShadowProperties != null && Boolean.TRUE.equals(l2ShadowProperties.isShadow());
    }

}
