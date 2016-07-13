package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgpmanager.impl.rev150326;

import org.opendaylight.netvirt.bgpmanager.BgpManager;

public class BgpManagerImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgpmanager.impl.rev150326.AbstractBgpManagerImplModule {
    public BgpManagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public BgpManagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgpmanager.impl.rev150326.BgpManagerImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // TODO:implement
        //throw new java.lang.UnsupportedOperationException();
        BgpManager provider = new BgpManager();
        provider.setEntityOwnershipService(getEntityOwnershipServiceDependency());
        //provider.setITMProvider(getItmDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
