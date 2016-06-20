package org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.impl.rev160620;

import org.opendaylight.netvirt.ipv6service.Ipv6ServiceProvider;

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
        Ipv6ServiceProvider provider = new Ipv6ServiceProvider();
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
