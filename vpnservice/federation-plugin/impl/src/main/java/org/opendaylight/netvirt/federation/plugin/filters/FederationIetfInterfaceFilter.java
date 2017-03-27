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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationIetfInterfaceFilter implements FederationPluginFilter<Interface, Interfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationIetfInterfaceFilter.class);

    private final DataBroker dataBroker;
    private final IElanService elanService;

    @Inject
    public FederationIetfInterfaceFilter(final DataBroker dataBroker, final IElanService elanService) {
        this.dataBroker = dataBroker;
        this.elanService = elanService;
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.IETF_INTERFACE_KEY, this);
    }

    @Override
    public FilterResult applyEgressFilter(Interface iface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<Interface> dataTreeModification) {
        String interfaceName = iface.getName();
        if (isShadow(iface)) {
            LOG.trace("Interface {} filtered out. Reason: shadow interface", interfaceName);
            return FilterResult.DENY;
        }

        if (!L2vlan.class.equals(iface.getType())) {
            LOG.trace("Interface {} filtered out. Reason: type {}", interfaceName, iface.getType());
            return FilterResult.DENY;
        }

        if (elanService.isExternalInterface(interfaceName)) {
            LOG.trace("Interface {} filtered out. Reason: external interface", interfaceName);
            return FilterResult.DENY;
        }

        if (FederationPluginUtils.isDhcpInterface(dataBroker, interfaceName)) {
            LOG.trace("Interface {} filtered out. Reason: dhcp interface", interfaceName);
            return FilterResult.DENY;
        }

        ElanInterface elanInterface = elanService.getElanInterfaceByElanInterfaceName(interfaceName);
        if (elanInterface == null) {
            elanInterface = FederationPluginUtils.getAssociatedDataObjectFromPending(
                    FederationPluginConstants.ELAN_INTERFACE_KEY, iface, pendingModifications);
            if (elanInterface == null) {
                LOG.debug("ELAN Interface {} not found. Queueing IETF interface", interfaceName);
                return FilterResult.QUEUE;
            }
        }

        String elanInstanceName = elanInterface.getElanInstanceName();
        if (!federatedMappings.containsProducerNetworkId(elanInstanceName)) {
            LOG.trace("Interface {} filtered out. Reason: network {} not federated", elanInterface.getName(),
                    elanInstanceName);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, Interfaces iface) {
        return FilterResult.ACCEPT;
    }

    private boolean isShadow(Interface iface) {
        IfShadowProperties ifShadowProperties = iface.getAugmentation(IfShadowProperties.class);
        return ifShadowProperties != null && Boolean.TRUE.equals(ifShadowProperties.isShadow());
    }

}
