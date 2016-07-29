/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216;

import org.opendaylight.netvirt.vpnmanager.VpnserviceProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.osgi.framework.BundleContext;

public class VpnserviceImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216.AbstractVpnserviceImplModule {
    private BundleContext bundleContext = null;

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
        IdManagerService idManager = getRpcregistryDependency().getRpcService(IdManagerService.class);
        OdlArputilService arpManager =  getRpcregistryDependency().getRpcService(OdlArputilService.class);
        OdlInterfaceRpcService odlInterfaceRpcService = getRpcregistryDependency().getRpcService(OdlInterfaceRpcService.class);
        ItmRpcService itmRpcService = getRpcregistryDependency().getRpcService(ItmRpcService.class);

        VpnserviceProvider provider = new VpnserviceProvider(bundleContext);
        provider.setNotificationService(getNotificationServiceDependency());
        provider.setBgpManager(getBgpmanagerDependency());
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setOdlInterfaceRpcService(odlInterfaceRpcService);
        provider.setIdManager(idManager);
        provider.setArpManager(arpManager);
        provider.setITMProvider(itmRpcService);
        provider.setRpcProviderRegistry(getRpcregistryDependency());
        provider.setNotificationPublishService(getNotificationPublishServiceDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
