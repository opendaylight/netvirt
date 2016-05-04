package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsalutil.impl.rev150403;

import org.opendaylight.vpnservice.mdsalutil.internal.MDSALUtilProvider;

public class MdsaluttilimplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsalutil.impl.rev150403.AbstractMdsaluttilimplModule {
    public MdsaluttilimplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public MdsaluttilimplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsalutil.impl.rev150403.MdsaluttilimplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
    	// TODO:implement
//      Can use the following to get a handle to data broker
    	
    	MDSALUtilProvider mdsalUtilProvider = new MDSALUtilProvider();
        getBrokerDependency().registerConsumer(mdsalUtilProvider);
     	//DataBroker dataBrokerService = getDataBrokerDependency();
    	//mdsalUtilMgr.setDataProvider(dataBrokerService);
        return mdsalUtilProvider ;
      }

}
