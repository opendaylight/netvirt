/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.idmanager.impl.rev150325;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.idmanager.IdManagerServiceProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;

public class IdmanagerImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.idmanager.impl.rev150325.AbstractIdmanagerImplModule {
    public IdmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public IdmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.idmanager.impl.rev150325.IdmanagerImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        LockManagerService lockManagerService = getRpcRegistryDependency().getRpcService(LockManagerService.class);
        IdManagerServiceProvider provider = new IdManagerServiceProvider(getRpcRegistryDependency());
        provider.setLockManager(lockManagerService);
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }
}
