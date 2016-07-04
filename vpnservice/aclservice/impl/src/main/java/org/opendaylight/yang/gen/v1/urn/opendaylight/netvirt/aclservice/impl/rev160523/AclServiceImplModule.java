package org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523;

import org.opendaylight.netvirt.aclservice.AclServiceProvider;

public class AclServiceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523.AbstractAclServiceImplModule {

    public AclServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public AclServiceImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523.AclServiceImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        AclServiceProvider provider = new AclServiceProvider();
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setRpcProviderRegistry(getRpcRegistryDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
