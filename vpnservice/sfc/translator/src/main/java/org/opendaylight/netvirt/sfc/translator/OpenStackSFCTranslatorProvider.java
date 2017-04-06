/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.netvirt.sfc.translator.flowclassifier.NeutronFlowClassifierListener;
import org.opendaylight.netvirt.sfc.translator.portchain.NeutronPortChainListener;
import org.opendaylight.netvirt.sfc.translator.portchain.NeutronPortPairGroupListener;
import org.opendaylight.netvirt.sfc.translator.portchain.NeutronPortPairListener;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePathService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OpenStackSFCTranslatorProvider extends AbstractLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackSFCTranslatorProvider.class);
    private final DataBroker dataBroker;
    private final RenderedServicePathService rspService;
    private NeutronFlowClassifierListener neutronFlowClassifierListener;
    private NeutronPortPairListener neutronPortPairListener;
    private NeutronPortPairGroupListener neutronPortPairGroupListener;
    private NeutronPortChainListener neutronPortChainListener;

    @Inject
    public OpenStackSFCTranslatorProvider(final DataBroker dataBroker, final RenderedServicePathService rspService) {
        LOG.info("OpenStackSFCTranslatorProvider2 constructor");
        this.dataBroker = dataBroker;
        this.rspService = rspService;
    }

    @Override
    protected void start() {
        LOG.info("{} start", getClass().getSimpleName());
        neutronFlowClassifierListener = new NeutronFlowClassifierListener(dataBroker);
        neutronPortPairListener = new NeutronPortPairListener(dataBroker);
        neutronPortPairGroupListener = new NeutronPortPairGroupListener(dataBroker);
        neutronPortChainListener = new NeutronPortChainListener(dataBroker, rspService);
        if (this.rspService == null) {
            LOG.warn("RenderedServicePath Service is not available. Translation layer might not work as expected.");
        }
    }

    @Override
    protected void stop() {
        neutronFlowClassifierListener.close();
        neutronPortPairListener.close();
        neutronPortPairGroupListener.close();
        neutronPortChainListener.close();
        LOG.info("{} close", getClass().getSimpleName());
    }
}
