package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.openflowplugin.app.fcaps.app.rev151211;

import org.opendaylight.controller.sal.common.util.NoopAutoCloseable;
import org.opendaylight.netvirt.fcapsapp.FcapsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Replaced by blueprint wiring
 */
@Deprecated
public class FcapsAppModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.openflowplugin.app.fcaps.app.rev151211.AbstractFcapsAppModule {
    public FcapsAppModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public FcapsAppModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.openflowplugin.app.fcaps.app.rev151211.FcapsAppModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // FcapsProvider instance is created via blueprint so this in a no-op.
        return NoopAutoCloseable.INSTANCE;
    }
}
