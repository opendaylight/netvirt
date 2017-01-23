/*
 * Copyright © 2016 HPE, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statisticsplugin.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.StatisticsPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsPluginProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsPluginProvider.class);

    private final DataBroker dataBroker;

    private final RpcProviderRegistry rpcProviderRegistry;

    private CounterRetriever counterRetriever;

    public StatisticsPluginProvider(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
            CounterRetriever counterRetriever) {
        this.dataBroker = dataBroker;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.counterRetriever = counterRetriever;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("StatisticsPluginProvider Session Initiated");
        rpcProviderRegistry.addRpcImplementation(StatisticsPluginService.class,
                new StatisticsPluginImpl(dataBroker, counterRetriever));
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("StatisticsPluginProvider Closed");
    }
}
