/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Method;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class FibmanagerImplModule extends AbstractFibmanagerImplModule {

    private BundleContext bundleContext;

    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public FibmanagerImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.fibmanager.impl.rev150325.FibmanagerImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public AutoCloseable createInstance() {
        // The service is provided via blueprint so wait for and return it here for backwards compatibility.
        final String typeFilter = String.format("(type=%s)", getIdentifier().getInstanceName());
        final WaitingServiceTracker<IFibManager> tracker = WaitingServiceTracker.create(
                IFibManager.class, bundleContext, typeFilter);
        final IFibManager fibManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        // We don't want to call close on the actual service as its life cycle is controlled by blueprint but
        // we do want to close the tracker so create a proxy to override close appropriately.
        return Reflection.newProxy(AutoCloseableIFibManager.class, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(final Object proxy, final Method method, final Object[] args) throws Throwable {
                if (method.getName().equals("close")) {
                    tracker.close();
                    return null;
                } else {
                    return method.invoke(fibManager, args);
                }
            }
        });
    }

    public void setBundleContext(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public boolean canReuseInstance(final AbstractFibmanagerImplModule oldModule) {
        return true;
    }

    private static interface AutoCloseableIFibManager
            extends AutoCloseable, IFibManager {
    }

}
