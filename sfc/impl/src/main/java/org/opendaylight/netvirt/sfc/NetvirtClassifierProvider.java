/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.sfc.listeners.NetvirtSfcAclListener;
import org.opendaylight.netvirt.sfc.listeners.NetvirtSfcClassifierListener;
import org.opendaylight.netvirt.sfc.listeners.NetvirtSfcRspListener;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcAclDataProcessor;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcClassifierDataProcessor;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcRspDataProcessor;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcGeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcOpenFlow13Provider;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetvirtClassifierProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtClassifierProvider.class);

    private AutoCloseable aclListener;
    private AutoCloseable classifierListener;
    private AutoCloseable rspListener;

    private final DataBroker dataBroker;

    public NetvirtClassifierProvider(final DataBroker dataBroker) {
        LOG.info("NetvirtClassifierProvider started");
        this.dataBroker = dataBroker;
    }

    public void start() {
        LOG.info("NetvirtClassifierProvider Session Initiated");

        MdsalUtils mdsalUtils = new MdsalUtils(dataBroker);

        NetvirtSfcGeniusProvider geniusProvider = new NetvirtSfcGeniusProvider(mdsalUtils);
        NetvirtSfcOpenFlow13Provider openFlowProvider = new NetvirtSfcOpenFlow13Provider(mdsalUtils);

        NetvirtSfcAclDataProcessor aclDataProcessor = new NetvirtSfcAclDataProcessor();
        aclDataProcessor.setGeniusProvider(geniusProvider);
        aclDataProcessor.setOpenFlow13Provider(openFlowProvider);

        NetvirtSfcClassifierDataProcessor classifierDataProcessor = new NetvirtSfcClassifierDataProcessor();
        classifierDataProcessor.setGeniusProvider(geniusProvider);
        classifierDataProcessor.setOpenFlow13Provider(openFlowProvider);

        NetvirtSfcRspDataProcessor rspDataProcessor = new NetvirtSfcRspDataProcessor();
        rspDataProcessor.setGeniusProvider(geniusProvider);
        rspDataProcessor.setOpenFlow13Provider(openFlowProvider);

        aclListener = new NetvirtSfcAclListener(aclDataProcessor, dataBroker);
        classifierListener = new NetvirtSfcClassifierListener(classifierDataProcessor, dataBroker);
        rspListener = new NetvirtSfcRspListener(rspDataProcessor, dataBroker);
    }

    @Override
    public void close() throws Exception {
        LOG.info("NetvirtClassifierProvider Closed");
        if (aclListener != null) {
            aclListener.close();
        }

        if (classifierListener != null) {
            classifierListener.close();
        }

        if (rspListener != null) {
            rspListener.close();
        }

    }
}
