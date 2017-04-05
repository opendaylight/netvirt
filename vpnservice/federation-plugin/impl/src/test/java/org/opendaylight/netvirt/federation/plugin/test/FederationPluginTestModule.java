/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.test;

import static org.mockito.Mockito.mock;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.federation.service.api.IConsumerManagement;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.messagequeue.IMessageBusClient;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.federation.plugin.FederationPluginMgr;
import org.opendaylight.netvirt.federation.plugin.SubnetVpnAssociationManager;
import org.opendaylight.netvirt.federation.plugin.creators.FederationElanInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationIetfInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationInventoryNodeModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationL2GatewayConnectionModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationL2GatewayModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationTopologyHwvtepNodeModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationTopologyNodeModificationCreator;
import org.opendaylight.netvirt.federation.plugin.creators.FederationVpnInterfaceModificationCreator;
import org.opendaylight.netvirt.federation.plugin.filters.FederationElanInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationIetfInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationInventoryNodeFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationL2GatewayConnectionFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationL2GatewayFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationTopologyHwvtepNodeFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationTopologyNodeFilter;
import org.opendaylight.netvirt.federation.plugin.filters.FederationVpnInterfaceFilter;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationElanInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationIetfInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationInventoryNodeIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationL2GatewayConnectionIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationL2GatewayIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationTopologyHwvtepNodeIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationTopologyNodeIdentifier;
import org.opendaylight.netvirt.federation.plugin.identifiers.FederationVpnInterfaceIdentifier;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationElanInterfaceTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationIetfInterfaceTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationInventoryNodeTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationL2GatewayConnectionTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationL2GatewayTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationTopologyHwvtepNodeTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationTopologyNodeTransformer;
import org.opendaylight.netvirt.federation.plugin.transformers.FederationVpnInterfaceTransformer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.federation.service.config.rev161110.FederationConfigData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.federation.service.config.rev161110.FederationConfigDataBuilder;

public class FederationPluginTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() {
        bind(FederationInventoryNodeTransformer.class);
        bind(FederationInventoryNodeFilter.class);
        bind(FederationInventoryNodeModificationCreator.class);
        bind(FederationInventoryNodeIdentifier.class);
        bind(FederationTopologyNodeTransformer.class);
        bind(FederationTopologyNodeFilter.class);
        bind(FederationTopologyNodeModificationCreator.class);
        bind(FederationTopologyNodeIdentifier.class);
        bind(FederationTopologyHwvtepNodeTransformer.class);
        bind(FederationTopologyHwvtepNodeFilter.class);
        bind(FederationTopologyHwvtepNodeModificationCreator.class);
        bind(FederationTopologyHwvtepNodeIdentifier.class);
        bind(FederationIetfInterfaceTransformer.class);
        bind(FederationIetfInterfaceFilter.class);
        bind(FederationIetfInterfaceModificationCreator.class);
        bind(FederationIetfInterfaceIdentifier.class);
        bind(FederationElanInterfaceTransformer.class);
        bind(FederationElanInterfaceFilter.class);
        bind(FederationElanInterfaceModificationCreator.class);
        bind(FederationElanInterfaceIdentifier.class);
        bind(FederationL2GatewayTransformer.class);
        bind(FederationL2GatewayFilter.class);
        bind(FederationL2GatewayModificationCreator.class);
        bind(FederationL2GatewayIdentifier.class);
        bind(FederationL2GatewayConnectionTransformer.class);
        bind(FederationL2GatewayConnectionFilter.class);
        bind(FederationL2GatewayConnectionModificationCreator.class);
        bind(FederationL2GatewayConnectionIdentifier.class);
        bind(FederationVpnInterfaceFilter.class);
        bind(FederationVpnInterfaceTransformer.class);
        bind(FederationVpnInterfaceModificationCreator.class);
        bind(FederationVpnInterfaceIdentifier.class);
        bind(FederationConfigData.class).toInstance(new FederationConfigDataBuilder().setMqPortNumber(5672)
                .setMqUser("guest").setMqUserPwd("guest").setMqBrokerIp("2.2.2.2").build());

        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(IElanService.class).toInstance(mock(IElanService.class));
        bind(SubnetVpnAssociationManager.class).toInstance(mock(SubnetVpnAssociationManager.class));
        bind(IMessageBusClient.class).toInstance(mock(IMessageBusClient.class));
        bind(ClusterSingletonServiceProvider.class).toInstance(mock(ClusterSingletonServiceProvider.class));
        bind(IConsumerManagement.class).toInstance(mock(IConsumerManagement.class));
        bind(FederationPluginMgr.class).toInstance(mock(FederationPluginMgr.class));
    }

}
