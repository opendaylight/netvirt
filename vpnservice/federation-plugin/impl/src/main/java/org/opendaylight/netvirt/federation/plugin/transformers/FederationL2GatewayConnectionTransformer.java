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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwConnectionShadowProperties;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rev170219.L2gwConnectionShadowPropertiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnectionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.NeutronBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationL2GatewayConnectionTransformer
        implements FederationPluginTransformer<L2gatewayConnection, Neutron> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationL2GatewayConnectionTransformer.class);

    @Inject
    public FederationL2GatewayConnectionTransformer() {
        FederationPluginTransformerRegistry.registerTransformer(FederationPluginConstants.L2_GATEWAY_CONNECTION_KEY,
                this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public Neutron applyEgressTransformation(L2gatewayConnection l2Gateway, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications) {
        String l2gwNetworkId = l2Gateway.getNetworkId().getValue();
        String consumerL2gwNetworkName = federatedMappings.getConsumerNetworkId(l2gwNetworkId);
        if (consumerL2gwNetworkName == null) {
            LOG.debug("Failed to transform l2Gateway {}. No transformation found for l2Gateway instance {}",
                    l2Gateway.getName(), consumerL2gwNetworkName);
            return null;
        }

        String l2gwTenantId = l2Gateway.getTenantId().getValue();
        String consumerL2gwTenantName = federatedMappings.getConsumerTenantId(l2gwTenantId);
        L2gatewayConnectionBuilder l2gwBuilder = new L2gatewayConnectionBuilder(l2Gateway);
        l2gwBuilder.setTenantId(new Uuid(consumerL2gwTenantName));
        l2gwBuilder.setNetworkId(new Uuid(consumerL2gwNetworkName));
        l2gwBuilder.addAugmentation(L2gwConnectionShadowProperties.class,
                new L2gwConnectionShadowPropertiesBuilder().setShadow(true).build());
        L2gatewayConnections l2gatewayConnections = new L2gatewayConnectionsBuilder()
                .setL2gatewayConnection(Collections.singletonList(l2gwBuilder.build())).build();
        NeutronBuilder nb = new NeutronBuilder();
        nb.setL2gatewayConnections(l2gatewayConnections);
        return nb.build();
    }

    @Override
    public Pair<InstanceIdentifier<L2gatewayConnection>, L2gatewayConnection> applyIngressTransformation(
            Neutron neutron, ModificationType modificationType, int generationNumber,
            String remoteIp) {
        L2gatewayConnections l2Gws = neutron.getL2gatewayConnections();
        if (l2Gws == null) {
            LOG.error("L2 gateway connections is null");
            return null;
        }

        List<L2gatewayConnection> l2GwsList = l2Gws.getL2gatewayConnection();
        if (l2GwsList == null || l2GwsList.isEmpty()) {
            LOG.error("L2 gateway connections is null");
            return null;
        }

        L2gatewayConnection l2gw = l2GwsList.get(0);
        L2gatewayConnectionBuilder l2gwBuilder = new L2gatewayConnectionBuilder(l2gw);
        l2gwBuilder.addAugmentation(L2gwConnectionShadowProperties.class,
                new L2gwConnectionShadowPropertiesBuilder(
                        l2gwBuilder.getAugmentation(L2gwConnectionShadowProperties.class)).setShadow(true)
                                .setGenerationNumber(generationNumber).setRemoteIp(remoteIp).build());
        l2gw = l2gwBuilder.build();
        return Pair.of(
                InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class, l2gw.getKey()), l2gw);
    }
}
