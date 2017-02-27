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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayFilter implements FederationPluginFilter<L2gateway, Neutron> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayFilter.class);

    @Inject
    public FederationL2GatewayFilter(final DataBroker dataBroker) {
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.L2_GATEWAY_KEY, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public FilterResult applyEgressFilter(L2gateway l2Gateway, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<L2gateway> dataTreeModification) {
        if (isShadow(l2Gateway)) {
            LOG.trace("Interface {} filtered out. Reason: shadow interface", l2Gateway.getName());
            return FilterResult.DENY;
        }

        String tenantName = l2Gateway.getTenantId().getValue();
        if (!federatedMappings.containsProducerTenantId(tenantName)) {
            LOG.trace("Interface {} filtered out. Reason: tenant {} not federated", l2Gateway.getName(),
                    tenantName);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, Neutron neutron) {
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(L2gateway l2Gateway) {
        L2gwShadowProperties l2ShadowProperties = l2Gateway.getAugmentation(L2gwShadowProperties.class);
        return l2ShadowProperties != null && Boolean.TRUE.equals(l2ShadowProperties.isShadow());
    }

}
