package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.impl.rev141210;

import org.opendaylight.vpnservice.impl.VpnintentProvider;

public class VpnintentImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.impl.rev141210.AbstractVpnintentImplModule {
    public VpnintentImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public VpnintentImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.impl.rev141210.VpnintentImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        VpnintentProvider provider = new VpnintentProvider();
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
