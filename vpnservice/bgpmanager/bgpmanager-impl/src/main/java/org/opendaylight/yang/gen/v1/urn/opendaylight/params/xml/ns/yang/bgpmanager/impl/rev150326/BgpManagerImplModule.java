package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgpmanager.impl.rev150326;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Method;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.osgi.framework.BundleContext;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class BgpManagerImplModule extends AbstractBgpManagerImplModule {

    private BundleContext bundleContext;

    public BgpManagerImplModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public BgpManagerImplModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgpmanager.impl.rev150326.BgpManagerImplModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public AutoCloseable createInstance() {
        // The service is provided via blueprint so wait for and return it here for backwards compatibility.
        final String typeFilter = String.format("(type=%s)", getIdentifier().getInstanceName());
        final WaitingServiceTracker<IBgpManager> tracker = WaitingServiceTracker.create(
                IBgpManager.class, bundleContext, typeFilter);
        final IBgpManager bgpManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        // We don't want to call close on the actual service as its life cycle is controlled by blueprint but
        // we do want to close the tracker so create a proxy to override close appropriately.
        return Reflection.newProxy(AutoCloseableIBgpManager.class, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(final Object proxy, final Method method, final Object[] args) throws Throwable {
                if (method.getName().equals("close")) {
                    tracker.close();
                    return null;
                } else {
                    return method.invoke(bgpManager, args);
                }
            }
        });
    }

    public void setBundleContext(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public boolean canReuseInstance(final AbstractBgpManagerImplModule oldModule) {
        return true;
    }

    private static interface AutoCloseableIBgpManager
            extends AutoCloseable, IBgpManager {
    }
}
