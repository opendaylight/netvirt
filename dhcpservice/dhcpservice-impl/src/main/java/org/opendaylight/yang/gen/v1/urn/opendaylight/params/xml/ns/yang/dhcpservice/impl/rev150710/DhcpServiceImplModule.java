package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.impl.rev150710;

import org.opendaylight.vpnservice.dhcpservice.DhcpProvider;

public class DhcpServiceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.impl.rev150710.AbstractDhcpServiceImplModule {
    public DhcpServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public DhcpServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.impl.rev150710.DhcpServiceImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        DhcpProvider dhcpProvider = new DhcpProvider();
        dhcpProvider.setNotificationProviderService(getNotificationServiceDependency());
        getBrokerDependency().registerProvider(dhcpProvider);
        return dhcpProvider;
    }

}
