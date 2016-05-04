/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.interfacemgr.impl.rev150325;

import org.opendaylight.vpnservice.interfacemgr.InterfacemgrProvider;

public class InterfacemgrImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.interfacemgr.impl.rev150325.AbstractInterfacemgrImplModule {
    public InterfacemgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public InterfacemgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.interfacemgr.impl.rev150325.InterfacemgrImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
    
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        InterfacemgrProvider provider = new InterfacemgrProvider();
        provider.setRpcProviderRegistry(getRpcRegistryDependency());
        provider.setNotificationService(getNotificationServiceDependency());
        provider.setMdsalManager(getMdsalutilDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
