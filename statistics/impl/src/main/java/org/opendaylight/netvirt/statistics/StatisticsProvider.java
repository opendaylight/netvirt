/*
 * Copyright © 2017 HPE, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StatisticsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsProvider.class);

    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final IMdsalApiManager mdsalApiManager;
    private final IdManagerService idManagerService;
    private final CounterRetriever counterRetriever;
    private final StatisticsCounters statisticsCounters;
    private CountersServiceInterfaceListener csil;

    @Inject
    public StatisticsProvider(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
            CounterRetriever counterRetriever, IInterfaceManager interfaceManager, IMdsalApiManager mdsalApiManager,
            IdManagerService idManagerService, StatisticsCounters statisticsCounters) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.mdsalApiManager = mdsalApiManager;
        this.counterRetriever = counterRetriever;
        this.idManagerService = idManagerService;
        this.statisticsCounters = statisticsCounters;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        StatisticsImpl statisticsImpl =
                new StatisticsImpl(dataBroker, counterRetriever, interfaceManager, mdsalApiManager, idManagerService,
                        statisticsCounters);
        rpcProviderRegistry.addRpcImplementation(StatisticsService.class, statisticsImpl);
        csil = new CountersServiceInterfaceListener(dataBroker, statisticsImpl);
    }

    @PreDestroy
    public void destroy() {
        LOG.info("{} close", getClass().getSimpleName());

        if (csil != null) {
            csil.close();
        }
    }
}
