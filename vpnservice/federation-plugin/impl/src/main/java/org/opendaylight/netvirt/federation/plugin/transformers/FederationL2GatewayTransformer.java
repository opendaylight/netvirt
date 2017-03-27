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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gatewaysBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.NeutronBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayTransformer implements FederationPluginTransformer<L2gateway, Neutron> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayTransformer.class);

    @Inject
    public FederationL2GatewayTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.L2_GATEWAY_KEY, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public Neutron applyEgressTransformation(L2gateway l2Gateway, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        String l2gwTenantName = l2Gateway.getTenantId().getValue();
        String consumerL2gwTenantName = federatedMappings.getConsumerTenantId(l2gwTenantName);
        if (consumerL2gwTenantName == null) {
            LOG.debug("Failed to transform l2Gateway {}. No transformation found for l2Gateway instance {}",
                    l2Gateway.getName(), consumerL2gwTenantName);
            return null;
        }

        L2gatewayBuilder l2gwBuilder = new L2gatewayBuilder(l2Gateway);
        l2gwBuilder.setTenantId(new Uuid(consumerL2gwTenantName));
        l2gwBuilder.addAugmentation(L2gwShadowProperties.class,
                new L2gwShadowPropertiesBuilder().setShadow(true).build());
        return new NeutronBuilder()
                .setL2gateways(
                        new L2gatewaysBuilder().setL2gateway(Collections.singletonList(l2gwBuilder.build())).build())
                .build();
    }

    @Override
    public Pair<InstanceIdentifier<L2gateway>, L2gateway> applyIngressTransformation(Neutron neutron,
            ModificationType modificationType, int generationNumber, String remoteIp) {
        L2gateways l2Gws = neutron.getL2gateways();
        if (l2Gws == null) {
            LOG.error("no L2 gateways");
            return null;
        }

        List<L2gateway> l2GwsList = l2Gws.getL2gateway();
        if (l2GwsList == null || l2GwsList.isEmpty()) {
            LOG.error("L2 gateway is empty");
            return null;
        }

        L2gateway l2gw = l2GwsList.get(0);
        L2gatewayBuilder l2gwBuilder = new L2gatewayBuilder(l2gw);
        l2gwBuilder.addAugmentation(L2gwShadowProperties.class,
                new L2gwShadowPropertiesBuilder(l2gwBuilder.getAugmentation(L2gwShadowProperties.class)).setShadow(true)
                        .setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        l2gw = l2gwBuilder.build();
        return Pair.of(
                InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class, l2gw.getKey()),
                l2gw);
    }
}
