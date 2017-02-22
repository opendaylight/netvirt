/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationElanInterfaceFilter implements FederationPluginFilter<ElanInterface, ElanInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationElanInterfaceFilter.class);

    private final DataBroker dataBroker;
    private final IElanService elanService;

    @Inject
    public FederationElanInterfaceFilter(final DataBroker dataBroker, final IElanService elanService) {
        this.dataBroker = dataBroker;
        this.elanService = elanService;
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.ELAN_INTERFACE_KEY, this);
    }

    @Override
    public FilterResult applyEgressFilter(ElanInterface elanInterface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<ElanInterface> dataTreeModification) {
        if (isShadow(elanInterface)) {
            LOG.trace("Interface {} filtered out. Reason: shadow interface", elanInterface.getName());
            return FilterResult.DENY;
        }

        String elanInstanceName = elanInterface.getElanInstanceName();
        if (!federatedMappings.containsProducerNetworkId(elanInstanceName)) {
            LOG.trace("Interface {} filtered out. Reason: network {} not federated", elanInterface.getName(),
                    elanInstanceName);
            return FilterResult.DENY;
        }

        if (elanService.isExternalInterface(elanInterface.getName())) {
            LOG.trace("Interface {} filtered out. Reason: external interface", elanInterface.getName());
            return FilterResult.DENY;
        }

        if (FederationPluginUtils.isDhcpInterface(dataBroker, elanInterface.getName())) {
            LOG.trace("Interface {} filtered out. Reason: dhcp interface", elanInterface.getName());
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, ElanInterfaces elanInterface) {
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(ElanInterface elanInterface) {
        ElanShadowProperties elanShadowProperties = elanInterface.getAugmentation(ElanShadowProperties.class);
        return elanShadowProperties != null && Boolean.TRUE.equals(elanShadowProperties.isShadow());
    }

}
