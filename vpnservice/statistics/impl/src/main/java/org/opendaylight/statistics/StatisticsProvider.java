/*
 * Copyright © 2017 HPE, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statistics;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StatisticsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsProvider.class);

    private final DataBroker dataBroker;

    private final RpcProviderRegistry rpcProviderRegistry;

    private CounterRetriever counterRetriever;

    @Inject
    public StatisticsProvider(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
            CounterRetriever counterRetriever) {
        this.dataBroker = dataBroker;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.counterRetriever = counterRetriever;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        rpcProviderRegistry.addRpcImplementation(StatisticsService.class,
                new StatisticsImpl(dataBroker, counterRetriever));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("{} destroy", getClass().getSimpleName());
    }
}
