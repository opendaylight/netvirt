/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.impl.rev160620;

import org.opendaylight.netvirt.ipv6service.Ipv6ServiceProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

public class Ipv6ServiceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.impl.rev160620.AbstractIpv6ServiceImplModule {
    public Ipv6ServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public Ipv6ServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.impl.rev160620.Ipv6ServiceImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        RpcProviderRegistry rpcregistryDependency = getRpcregistryDependency();
        Ipv6ServiceProvider provider = new Ipv6ServiceProvider();
        provider.setInterfaceManager(getOdlinterfaceDependency());
        provider.setNotificationProviderService(getNotificationServiceDependency());
        provider.setInterfaceManagerRpc(rpcregistryDependency.getRpcService(OdlInterfaceRpcService.class));
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
