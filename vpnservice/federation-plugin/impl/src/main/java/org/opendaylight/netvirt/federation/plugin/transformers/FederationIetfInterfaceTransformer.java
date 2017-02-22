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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.IfShadowPropertiesBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationIetfInterfaceTransformer implements FederationPluginTransformer<Interface, Interfaces> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationIetfInterfaceTransformer.class);

    @Inject
    public FederationIetfInterfaceTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.IETF_INTERFACE_KEY, this);
    }

    @Override
    public Interfaces applyEgressTransformation(Interface iface, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder(iface);
        interfaceBuilder.addAugmentation(IfShadowProperties.class,
                new IfShadowPropertiesBuilder().setShadow(true).build());
        return new InterfacesBuilder().setInterface(Collections.singletonList(interfaceBuilder.build())).build();
    }

    @Override
    public Pair<InstanceIdentifier<Interface>, Interface> applyIngressTransformation(Interfaces interfaces,
            ModificationType modificationType, int generationNumber, String remoteIp) {
        List<Interface> interfaceList = interfaces.getInterface();
        if (interfaceList == null || interfaceList.isEmpty()) {
            LOG.error("IETF interfaces is empty");
            return null;
        }

        Interface iface = interfaceList.get(0);
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder(iface);
        interfaceBuilder.addAugmentation(IfShadowProperties.class,
                new IfShadowPropertiesBuilder(interfaceBuilder.getAugmentation(IfShadowProperties.class))
                        .setShadow(true).setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        return Pair.of(InstanceIdentifier.create(Interfaces.class).child(Interface.class, iface.getKey()),
                interfaceBuilder.build());
    }

}
