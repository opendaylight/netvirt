package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nexthopmgr.impl.rev150325;

import org.opendaylight.vpnservice.nexthopmgr.NexthopmgrProvider;

public class NexthopmgrImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nexthopmgr.impl.rev150325.AbstractNexthopmgrImplModule {
    public NexthopmgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NexthopmgrImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nexthopmgr.impl.rev150325.NexthopmgrImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        NexthopmgrProvider provider = new NexthopmgrProvider();
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
