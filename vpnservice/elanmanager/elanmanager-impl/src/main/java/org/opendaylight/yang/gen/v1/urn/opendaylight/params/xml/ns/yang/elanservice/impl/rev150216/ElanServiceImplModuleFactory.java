/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.elanservice.impl.rev150216;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class ElanServiceImplModuleFactory extends AbstractElanServiceImplModuleFactory {

    @Override
    public ElanServiceImplModule instantiateModule(final String instanceName, final DependencyResolver dependencyResolver,
            final ElanServiceImplModule oldModule, final AutoCloseable oldInstance, final BundleContext bundleContext) {
        final ElanServiceImplModule module = super.instantiateModule(instanceName, dependencyResolver, oldModule,
                oldInstance, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }

    @Override
    public ElanServiceImplModule instantiateModule(final String instanceName, final DependencyResolver dependencyResolver,
            final BundleContext bundleContext) {
        final ElanServiceImplModule module = super.instantiateModule(instanceName, dependencyResolver, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }
}
