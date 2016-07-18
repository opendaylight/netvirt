/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.fibmanager.FibManagerProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;

public class FibmanagerImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325.AbstractFibmanagerImplModule {
    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325.FibmanagerImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        FibManagerProvider provider = new FibManagerProvider();
        RpcProviderRegistry rpcProviderRegistry = getRpcregistryDependency();
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setVpnmanager(getVpnmanagerDependency());
        provider.setIdManager(rpcProviderRegistry.getRpcService(IdManagerService.class));
        provider.setInterfaceManager(rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class));
        provider.setITMProvider(rpcProviderRegistry.getRpcService(ItmRpcService.class));
        provider.setRpcProviderRegistry(rpcProviderRegistry);
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}