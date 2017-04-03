/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.RoutedContainer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.routed.container.RouteKeyItem;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.routed.container.RouteKeyItemKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219.FederationPluginRoutedRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219.UpdateFederatedNetworksInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.FederationPluginRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.UpdateFederatedNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.update.federated.networks.input.FederatedAclsIn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.update.federated.networks.input.FederatedNetworksIn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationPluginRpcServiceImpl implements FederationPluginRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(FederationPluginRpcServiceImpl.class);

    private final RpcProviderRegistry rpcRegistry;

    @Inject
    public FederationPluginRpcServiceImpl(final RpcProviderRegistry rpcRegistry) {
        this.rpcRegistry = rpcRegistry;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        rpcRegistry.addRpcImplementation(FederationPluginRpcService.class, this);
    }

    @Override
    public Future<RpcResult<Void>> updateFederatedNetworks(UpdateFederatedNetworksInput input) {
        FederationPluginRoutedRpcService routedRpcService = rpcRegistry
                .getRpcService(FederationPluginRoutedRpcService.class);
        if (routedRpcService == null) {
            return Futures.immediateFuture(RpcResultBuilder.<Void>failed()
                    .withError(ErrorType.RPC, "Failed to get routed RPC service for federation plugin").build());
        }

        List<FederatedNetworksIn> federatedNetworks = input.getFederatedNetworksIn();
        UpdateFederatedNetworksInputBuilder builder = new UpdateFederatedNetworksInputBuilder()
                .setFederatedNetworksIn(convertFederatedNetworks(federatedNetworks))
                .setFederatedAclsIn(convertFederatedAcls(input.getFederatedAclsIn()))
                .setRouteKeyItem(buildtRouteKeyInstanceIdentifier());

        return routedRpcService.updateFederatedNetworks(builder.build());
    }

    private List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219
        .update.federated.networks.input.FederatedAclsIn> convertFederatedAcls(
            List<FederatedAclsIn> federatedAclsIn) {
        if (federatedAclsIn == null) {
            return null;
        }

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219
            .update.federated.networks.input.FederatedAclsIn> routedFederatedAcls = new ArrayList<>();
        for (FederatedAclsIn federatedAcl : federatedAclsIn) {
            routedFederatedAcls
                    .add(new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc
                            .rev170219.update.federated.networks.input.FederatedAclsInBuilder(
                            federatedAcl).build());
        }

        return routedFederatedAcls;
    }

    private static List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219
        .update.federated.networks.input.FederatedNetworksIn> convertFederatedNetworks(
            List<FederatedNetworksIn> federatedNetworks) {
        if (federatedNetworks == null) {
            return null;
        }

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc.rev170219
            .update.federated.networks.input.FederatedNetworksIn> routedFederatedNetworks = new ArrayList<>();
        for (FederatedNetworksIn federatedNetwork : federatedNetworks) {
            routedFederatedNetworks
                    .add(new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.routed.rpc
                            .rev170219.update.federated.networks.input.FederatedNetworksInBuilder(
                            federatedNetwork).build());
        }

        return routedFederatedNetworks;
    }

    private static InstanceIdentifier<RouteKeyItem> buildtRouteKeyInstanceIdentifier() {
        return InstanceIdentifier.create(RoutedContainer.class).child(RouteKeyItem.class,
                new RouteKeyItemKey(FederationPluginConstants.RPC_ROUTE_KEY));
    }

}
