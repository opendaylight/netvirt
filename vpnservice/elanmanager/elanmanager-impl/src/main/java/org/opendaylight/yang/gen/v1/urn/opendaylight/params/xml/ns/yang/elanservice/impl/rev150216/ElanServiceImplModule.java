/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.elanservice.impl.rev150216;

public class ElanServiceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.elanservice.impl.rev150216.AbstractElanServiceImplModule {
    public ElanServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ElanServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.elanservice.impl.rev150216.ElanServiceImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // RpcProviderRegistry rpcregistryDependency =
        // getRpcregistryDependency();
        // IdManagerService idManager =
        // rpcregistryDependency.getRpcService(IdManagerService.class);
        // // ElanServiceProvider provider = new
        // // ElanServiceProvider(rpcregistryDependency);
        // ElanServiceProvider provider = new ElanServiceProvider();
        // provider.setNotificationService(getNotificationServiceDependency());
        // provider.setMdsalManager(getMdsalutilDependency());
        // provider.setInterfaceManager(getOdlinterfaceDependency());
        // provider.setInterfaceManagerRpcService(rpcregistryDependency.getRpcService(OdlInterfaceRpcService.class));
        // provider.setItmRpcService(rpcregistryDependency.getRpcService(ItmRpcService.class));
        // provider.setItmManager(getItmmanagerDependency());
        // provider.setIdManager(idManager);
        // provider.setEntityOwnershipService(getEntityOwnershipServiceDependency());
        // getBrokerDependency().registerProvider(provider);
        // return provider;
        return null;
    }

}
