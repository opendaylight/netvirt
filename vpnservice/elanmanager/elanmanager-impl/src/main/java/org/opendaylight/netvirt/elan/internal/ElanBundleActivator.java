package org.opendaylight.netvirt.elan.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanBundleActivator implements BundleActivator {

    private static final Logger LOG = LoggerFactory.getLogger(ElanBundleActivator.class);

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.info("hello, world demo xoxo");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.info("goodbye");
    }

}
