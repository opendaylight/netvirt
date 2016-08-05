package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.impl.rev150710;

import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.dhcpservice.DhcpProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;

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
        RpcProviderRegistry rpcregistryDependency = getRpcregistryDependency();
        DhcpProvider dhcpProvider = new DhcpProvider();
        dhcpProvider.setControllerDhcpEnabled(getControllerDhcpEnabled());
        dhcpProvider.setNotificationProviderService(getNotificationServiceDependency());
        dhcpProvider.setMdsalManager(getMdsalutilDependency());
        //dhcpProvider.setNeutronVpnManager(getNeutronvpnDependency());
        dhcpProvider.setInterfaceManagerRpc(rpcregistryDependency.getRpcService(OdlInterfaceRpcService.class));
        dhcpProvider.setItmRpcService(rpcregistryDependency.getRpcService(ItmRpcService.class));
        dhcpProvider.setEntityOwnershipService(getEntityOwnershipServiceDependency());
        dhcpProvider.setInterfaceManager(getOdlinterfaceDependency());
        getBrokerDependency().registerProvider(dhcpProvider);
        return dhcpProvider;
    }

}
