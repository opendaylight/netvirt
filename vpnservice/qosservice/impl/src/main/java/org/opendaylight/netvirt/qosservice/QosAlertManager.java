/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;


import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public final class QosAlertManager implements Runnable {

    private short threshold;
    private boolean alertEnabled;
    private int pollInterval;
    private final QosalertConfig defaultConfig;
    private boolean statsPollThreadStart;
    private final DataBroker dataBroker;
    private final OpendaylightDirectStatisticsService odlDirectStatisticsService;
    private Thread thread;
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertManager.class);

    @Inject
    public QosAlertManager(final DataBroker dataBroker, OpendaylightDirectStatisticsService odlDirectStatisticsService,
                            QosalertConfig defaultConfig) {
        LOG.info("{} created",  getClass().getSimpleName());
        this.dataBroker = dataBroker;
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.defaultConfig = defaultConfig;
        thread = null;
        LOG.info("QosAlert default config poll alertEnabled:{} threshold:{} pollInterval:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                defaultConfig.getQosAlertPollInterval());
        getDefaultConfig();

    }

    @PostConstruct
    public void init() {
        statsPollThreadStart = true;
        startStatsPollThread();
        LOG.info("{} init done", getClass().getSimpleName());
    }

    @PreDestroy
    public void close() {
        statsPollThreadStart = false;
        if (thread != null) {
            thread.interrupt();
        }
        LOG.info("{} close done", getClass().getSimpleName());
    }

    @Override
    public void run() {
        LOG.info("Qos alert poll thread started");

        while (statsPollThreadStart && alertEnabled) {
            LOG.debug("Thread loop polling :{} threshold:{} pollInterval:{}", alertEnabled, threshold,
                    pollInterval);

            try {
                pollDirectStatisticsForAllNodes();
                Thread.sleep(pollInterval * 60 * 1000); // pollInterval in minutes
            } catch (final InterruptedException e) {
                LOG.debug("Qos polling thread interrupted");
            }
        }

        thread = null;
        LOG.info("Qos alert poll thread stopped");
    }

    private void startStatsPollThread() {
        if (statsPollThreadStart && alertEnabled && (thread == null)) {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void getDefaultConfig() {
        alertEnabled = defaultConfig.isQosAlertEnabled();
        threshold = defaultConfig.getQosDropPacketThreshold();
        pollInterval = defaultConfig.getQosAlertPollInterval();
    }

    public void setQosalertConfig(QosalertConfig config) {

        LOG.info("New QoS alert config threshold:{} polling alertEnabled:{} interval:{}",
                config.getQosDropPacketThreshold(), config.isQosAlertEnabled(),
                config.getQosAlertPollInterval());

        threshold = config.getQosDropPacketThreshold().shortValue();
        alertEnabled = config.isQosAlertEnabled().booleanValue();
        pollInterval = config.getQosAlertPollInterval();

        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }

    }

    public void restoreDefaultConfig() {
        LOG.info("Restoring default configuration");
        getDefaultConfig();
        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }
    }


    private void pollDirectStatisticsForAllNodes() {
        LOG.debug("Polling direct statistics from all nodes");
        // TODO: Add all polling logic here
    }

}
