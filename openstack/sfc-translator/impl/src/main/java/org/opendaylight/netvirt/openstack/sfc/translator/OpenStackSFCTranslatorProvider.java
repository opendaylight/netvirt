/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.sfc.translator;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.openstack.sfc.translator.config.rev160720.OpenstackSfcTranslatorConfig;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenStackSFCTranslatorProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(OpenStackSFCTranslatorProvider.class);

    private final DataBroker dataBroker;
    private final BundleContext bundleContext;

    private ServiceRegistration<?> reg;

    public OpenStackSFCTranslatorProvider(
            final DataBroker dataBroker,
            final OpenstackSfcTranslatorConfig openstackSfcTranslatorConfig,
            final BundleContext bundleContext) {
        LOG.info("OpenStack SFC Translator started");
        this.dataBroker = dataBroker;
        this.bundleContext = bundleContext;
    }

    public void start() {
        LOG.info("OpenStack SFC Translator Session Initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("OpenStack SFC Translator Closed");
        if (reg != null) {
            reg.unregister();
        }
    }
}