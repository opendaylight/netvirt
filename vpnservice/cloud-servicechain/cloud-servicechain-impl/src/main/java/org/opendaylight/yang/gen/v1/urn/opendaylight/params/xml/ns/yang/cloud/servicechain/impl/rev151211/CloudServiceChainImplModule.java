package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.cloud.servicechain.impl.rev151211;

import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;

public class CloudServiceChainImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.cloud.servicechain.impl.rev151211.AbstractCloudServiceChainImplModule {
    public CloudServiceChainImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public CloudServiceChainImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.cloud.servicechain.impl.rev151211.CloudServiceChainImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        FibRpcService fibRpcService = getRpcregistryDependency().getRpcService(FibRpcService.class);
        CloudServiceChainProvider provider = new CloudServiceChainProvider();
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setFibRpcService(fibRpcService);
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
