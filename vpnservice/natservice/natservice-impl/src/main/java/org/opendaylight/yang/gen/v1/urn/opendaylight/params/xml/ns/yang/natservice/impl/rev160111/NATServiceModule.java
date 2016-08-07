/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.natservice.impl.rev160111;

import org.opendaylight.netvirt.natservice.internal.NatServiceProvider;

public class NATServiceModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.natservice.impl.rev160111.AbstractNATServiceModule {
    public NATServiceModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NATServiceModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.natservice.impl.rev160111.NATServiceModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        NatServiceProvider provider = new NatServiceProvider(getRpcRegistryDependency());
        provider.setNotificationService(getNotificationServiceDependency());
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setInterfaceManager(getOdlinterfaceDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
