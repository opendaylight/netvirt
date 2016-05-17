package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.providers.impl.rev150513;

import org.opendaylight.controller.sal.common.util.NoopAutoCloseable;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class NetvirtProvidersImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.providers.impl.rev150513.AbstractNetvirtProvidersImplModule {

    public NetvirtProvidersImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetvirtProvidersImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.providers.impl.rev150513.NetvirtProvidersImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // Instances are created via blueprint so this in a no-op.
        return NoopAutoCloseable.INSTANCE;
    }
}
