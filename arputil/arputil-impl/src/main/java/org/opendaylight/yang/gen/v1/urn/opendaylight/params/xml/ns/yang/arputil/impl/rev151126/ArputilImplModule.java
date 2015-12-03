package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.impl.rev151126;

import org.opendaylight.vpnservice.arputil.internal.ArpUtilProvider;

public class ArputilImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.impl.rev151126.AbstractArputilImplModule {
    public ArputilImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ArputilImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.impl.rev151126.ArputilImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        ArpUtilProvider provider = new ArpUtilProvider(getRpcRegistryDependency(),
                getNotificationPublishServiceDependency(),
                getNotificationServiceDependency(),
                getMdsalutilDependency()
                );
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
