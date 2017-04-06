/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.transformers;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.ElanShadowPropertiesBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationElanInterfaceTransformer implements FederationPluginTransformer<ElanInterface, ElanInterfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationElanInterfaceTransformer.class);

    @Inject
    public FederationElanInterfaceTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.ELAN_INTERFACE_KEY, this);
    }

    @Override
    public ElanInterfaces applyEgressTransformation(ElanInterface elanInterface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        String elanInstanceName = elanInterface.getElanInstanceName();
        String consumerElanInstaceName = federatedMappings.getConsumerNetworkId(elanInstanceName);
        if (consumerElanInstaceName == null) {
            LOG.error("Failed to transform ELAN interface {}. No transformation found for ELAN instance {}",
                    elanInterface.getName(), consumerElanInstaceName);
            return null;
        }

        ElanInterfaceBuilder interfaceBuilder = new ElanInterfaceBuilder(elanInterface);
        interfaceBuilder.setElanInstanceName(consumerElanInstaceName);
        interfaceBuilder.addAugmentation(ElanShadowProperties.class,
                new ElanShadowPropertiesBuilder().setShadow(true).build());
        return new ElanInterfacesBuilder().setElanInterface(Collections.singletonList(interfaceBuilder.build()))
                .build();
    }

    @Override
    public Pair<InstanceIdentifier<ElanInterface>, ElanInterface> applyIngressTransformation(
            ElanInterfaces elanInterfaces, ModificationType modificationType, int generationNumber, String remoteIp) {
        List<ElanInterface> elanInterfaceList = elanInterfaces.getElanInterface();
        if (elanInterfaceList == null || elanInterfaceList.isEmpty()) {
            LOG.error("ELAN interfaces is empty");
            return null;
        }

        ElanInterface elanInterface = elanInterfaceList.get(0);
        ElanInterfaceBuilder interfaceBuilder = new ElanInterfaceBuilder(elanInterface);
        interfaceBuilder.addAugmentation(ElanShadowProperties.class,
                new ElanShadowPropertiesBuilder(interfaceBuilder.getAugmentation(ElanShadowProperties.class))
                        .setShadow(true).setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        elanInterface = interfaceBuilder.build();
        return Pair.of(
                InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class, elanInterface.getKey()),
                elanInterface);
    }
}
