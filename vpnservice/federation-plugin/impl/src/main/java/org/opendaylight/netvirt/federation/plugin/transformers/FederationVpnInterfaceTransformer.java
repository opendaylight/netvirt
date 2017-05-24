/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.transformers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.FederationPluginUtils;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.netvirt.federation.plugin.SubnetVpnAssociationManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.VpnShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class FederationVpnInterfaceTransformer implements FederationPluginTransformer<VpnInterface, VpnInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationVpnInterfaceTransformer.class);

    private final SubnetVpnAssociationManager subnetVpnAssociationManager;

    @Inject
    public FederationVpnInterfaceTransformer(final SubnetVpnAssociationManager subnetVpnAssociationManager) {
        this.subnetVpnAssociationManager = subnetVpnAssociationManager;
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.VPN_INTERFACE_KEY, this);
    }

    @Override
    public VpnInterfaces applyEgressTransformation(VpnInterface vpnInterface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
        if (adjacencies == null) {
            LOG.error("No adjacencies found for VPN interface {}", vpnInterface.getName());
            return null;
        }

        List<Adjacency> federatedAdjacencies = transformAdjacencies(vpnInterface, federatedMappings, adjacencies);
        if (federatedAdjacencies.isEmpty()) {
            LOG.error("No federated adjacencies found for VPN interface {} federated mappings {}", vpnInterface,
                    federatedMappings);
            return null;
        }

        VpnInterfaceBuilder vpnInterfaceBuilder = new VpnInterfaceBuilder(vpnInterface);
        vpnInterfaceBuilder.addAugmentation(Adjacencies.class,
                new AdjacenciesBuilder().setAdjacency(federatedAdjacencies).build());
        vpnInterfaceBuilder.addAugmentation(VpnShadowProperties.class,
                new VpnShadowPropertiesBuilder().setShadow(true).build());
        return new VpnInterfacesBuilder().setVpnInterface(Collections.singletonList(vpnInterfaceBuilder.build()))
                .build();
    }

    @Override
    public Pair<InstanceIdentifier<VpnInterface>, VpnInterface> applyIngressTransformation(VpnInterfaces vpnInterfaces,
            ModificationType modificationType, int generationNumber, String remoteIp) {
        List<VpnInterface> vpnInterfaceList = vpnInterfaces.getVpnInterface();
        if (vpnInterfaceList == null || vpnInterfaceList.isEmpty()) {
            LOG.error("VPN interfaces is empty");
            return null;
        }

        VpnInterface vpnInterface = vpnInterfaceList.get(0);
        VpnInterface transformedVpnInterface = null;
        if (!ModificationType.DELETE.equals(modificationType)) {
            transformedVpnInterface = transformVpnInterface(vpnInterface, generationNumber, remoteIp);
            if (transformedVpnInterface == null) {
                return null;
            }
        }

        return Pair.of(InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class, vpnInterface.getKey()),
                transformedVpnInterface);
    }

    private List<Adjacency> transformAdjacencies(VpnInterface vpnInterface, FederatedMappings federatedMappings,
            Adjacencies adjacencies) {
        List<Adjacency> federatedAdjacencies = new ArrayList<>();
        for (Adjacency adjacency : adjacencies.getAdjacency()) {
            Uuid subnetId = adjacency.getSubnetId();
            if (subnetId != null) {
                String federatedSubnetId = federatedMappings.getConsumerSubnetId(subnetId.getValue());
                if (federatedSubnetId != null) {
                    Adjacency federatedAdjacency = new AdjacencyBuilder(adjacency)
                            .setSubnetId(new Uuid(federatedSubnetId)).build();
                    federatedAdjacencies.add(federatedAdjacency);
                } else {
                    LOG.warn("Adjacency {} not federated. Federated subnet id not found", adjacency);
                }
            } else {
                federatedAdjacencies.add(adjacency);
            }
        }

        return federatedAdjacencies;
    }

    private VpnInterface transformVpnInterface(VpnInterface vpnInterface, int generationNumber, String remoteIp) {
        String subnetId = FederationPluginUtils.getSubnetIdFromVpnInterface(vpnInterface);
        if (subnetId == null) {
            LOG.error("Subnet id not found for VPN interface {}", vpnInterface.getName());
            return null;
        }

        String vpnId = subnetVpnAssociationManager.getSubnetVpn(subnetId);
        if (vpnId == null) {
            LOG.error("No VPN id found for subnet id {} for interface {}", subnetId, vpnInterface.getName());
            return null;
        }

        VpnInterfaceBuilder vpnInterfaceBuilder = new VpnInterfaceBuilder(vpnInterface);
        vpnInterfaceBuilder.setVpnInstanceName(Collections.singletonList(vpnId));
        vpnInterfaceBuilder.addAugmentation(VpnShadowProperties.class,
                new VpnShadowPropertiesBuilder(vpnInterfaceBuilder.getAugmentation(VpnShadowProperties.class))
                        .setShadow(true).setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        return vpnInterfaceBuilder.build();
    }
}
