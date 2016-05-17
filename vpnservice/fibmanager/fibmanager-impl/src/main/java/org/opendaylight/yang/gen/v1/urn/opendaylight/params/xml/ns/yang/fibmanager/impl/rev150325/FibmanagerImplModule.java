/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325;
import org.opendaylight.controller.sal.common.util.NoopAutoCloseable;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class FibmanagerImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325.AbstractFibmanagerImplModule {
    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325.FibmanagerImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // FibManagerProvider instance is created via blueprint so this in a no-op.
        return NoopAutoCloseable.INSTANCE;
    }

}
