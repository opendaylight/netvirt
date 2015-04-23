/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216;

import org.opendaylight.vpnservice.VpnserviceProvider;

public class VpnserviceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216.AbstractVpnserviceImplModule {
    public VpnserviceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public VpnserviceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216.VpnserviceImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        VpnserviceProvider provider = new VpnserviceProvider();
        getBrokerDependency().registerProvider(provider);
        provider.setBgpManager(getBgpmanagerDependency());
        return provider;
    }
}
