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
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.netvirt.federation.plugin.SubnetVpnAssociationManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationVpnInterfaceFilter implements FederationPluginFilter<VpnInterface, VpnInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationVpnInterfaceFilter.class);

    private final DataBroker dataBroker;
    private final SubnetVpnAssociationManager subnetVpnAssociationManager;

    @Inject
    public FederationVpnInterfaceFilter(final DataBroker dataBroker,
            final SubnetVpnAssociationManager subnetVpnAssociationManager) {
        this.dataBroker = dataBroker;
        this.subnetVpnAssociationManager = subnetVpnAssociationManager;
        FederationPluginFilterRegistry.registerFilter(FederationPluginConstants.VPN_INTERFACE_KEY, this);
    }

    @Override
    public FilterResult applyEgressFilter(VpnInterface vpnInterface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<VpnInterface> dataTreeModification) {
        String vpnInterfaceName = vpnInterface.getName();
        String interfaceName = vpnInterfaceName;
        if (isShadow(vpnInterface)) {
            LOG.trace("Interface {} filtered out. Reason: shadow interface", interfaceName);
            return FilterResult.DENY;
        }
        Boolean isRouterInterface = vpnInterface.isIsRouterInterface();
        if (isRouterInterface != null && vpnInterface.isIsRouterInterface()) {
            LOG.trace("Interface {} filtered out. Reason: router interface", interfaceName);
            return FilterResult.DENY;
        }

        String subnetId = FederationPluginUtils.getSubnetIdFromVpnInterface(vpnInterface);
        if (subnetId == null) {
            LOG.trace("Interface {} filtered out. Reason: subnet id missing", interfaceName);
            return FilterResult.DENY;
        }

        if (!federatedMappings.containsProducerSubnetId(subnetId)) {
            LOG.trace("Interface {} filtered out. Reason:  subnet {} not federated", vpnInterfaceName, subnetId);
            return FilterResult.DENY;
        }

        if (FederationPluginUtils.isDhcpInterface(dataBroker, vpnInterfaceName)) {
            LOG.trace("Interface {} filtered out. Reason: dhcp interface", vpnInterfaceName);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    @Override
    public FilterResult applyIngressFilter(String listenerKey, VpnInterfaces vpnInterfaces) {
        VpnInterface vpnInterface = vpnInterfaces.getVpnInterface().get(0);
        String subnetId = FederationPluginUtils.getSubnetIdFromVpnInterface(vpnInterface);
        if (subnetId == null) {
            LOG.warn("Interface {} filtered out. Reason: subnet id not found", vpnInterface.getName());
            return FilterResult.DENY;
        }

        String vpnId = subnetVpnAssociationManager.getSubnetVpn(subnetId);
        if (vpnId == null) {
            LOG.debug("Interface {} filtered out. Reason: VPN id not found for subnet id {}", vpnInterface.getName(),
                    subnetId);
            return FilterResult.DENY;
        }

        return FilterResult.ACCEPT;
    }

    private boolean isShadow(VpnInterface vpnInterface) {
        VpnShadowProperties vpnShadowProperties = vpnInterface.getAugmentation(VpnShadowProperties.class);
        return vpnShadowProperties != null && Boolean.TRUE.equals(vpnShadowProperties.isShadow());
    }

}
