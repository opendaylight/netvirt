/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.alivenessmonitor.impl.rev150706;

import org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessMonitorProvider;

public class AlivenessMonitorModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.alivenessmonitor.impl.rev150706.AbstractAlivenessMonitorModule {
    public AlivenessMonitorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public AlivenessMonitorModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.alivenessmonitor.impl.rev150706.AlivenessMonitorModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        AlivenessMonitorProvider provider = new AlivenessMonitorProvider(getRpcRegistryDependency());
        provider.setNotificationPublishService(getNotificationPublishServiceDependency());
        provider.setNotificationService(getNotificationServiceDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
