/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class QosAlertManager implements Runnable {

    private short threshold;
    private boolean enable;
    private int pollInterval;
    private QosalertConfig defaultConfig;
    private boolean initDone;
    private final DataBroker dataBroker;
    private final OpendaylightDirectStatisticsService odlDirectStatisticsService;
    private Thread thread;
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertManager.class);
    private static QosAlertManager instance;


    private QosAlertManager(final DataBroker dataBroker, OpendaylightDirectStatisticsService odlDirectStatisticsService,
                            QosalertConfig defaultConfig) {
        LOG.info("QosAlert Manager created");
        initDone = false;
        this.dataBroker = dataBroker;
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.defaultConfig = defaultConfig;
        LOG.info("QosAlert default config poll enable:{} threshold:{} pollInterval:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                defaultConfig.getQosAlertPollInterval());
        setDefault();

    }

    public static QosAlertManager getInstance(final DataBroker dataBroker,
                                              OpendaylightDirectStatisticsService odlDirectStatisticsService,
                                              QosalertConfig qosalertConfig) {

        LOG.info("QosAlertManager: getting the instance");
        if (instance == null) {
            instance = new QosAlertManager(dataBroker, odlDirectStatisticsService, qosalertConfig);
        }
        return instance;
    }

    public void init() {
        LOG.info("QosAlertManager: init");
        thread = new Thread(this);
        thread.setDaemon(true);
        initDone = true;
        thread.start();
        LOG.info("QosAlertManager: init done");
    }

    public void close() {
        LOG.info("QosAlertManager: close");
        initDone = false;
        thread.interrupt();
        LOG.info("QosAlertManager: close done");
    }

    @Override
    public void run() {
        LOG.info("Qos alert poll thread started");
        while (initDone) {
            LOG.debug("Thread loop polling :{} threshold:{} pollInterval:{}", enable, threshold,
                    pollInterval);

            try {
                if (enable) {
                    pollDirectStatisticsForAllNodes();
                }
                Thread.sleep(pollInterval * 60 * 1000); // pollInterval in minutes
            } catch (final InterruptedException e) {
                LOG.error("Qos polling thread interrupted");
            }
        }
        LOG.info("Qos alert poll thread stopped");
    }

    private void setDefault() {
        enable = defaultConfig.isQosAlertEnabled();
        threshold = defaultConfig.getQosDropPacketThreshold();
        pollInterval = defaultConfig.getQosAlertPollInterval();
    }

    public void setQosalertConfig(QosalertConfig config) {

        LOG.info("New QoS alert config threshold:{} polling enabled:{} interval:{}",
                config.getQosDropPacketThreshold(), config.isQosAlertEnabled(),
                config.getQosAlertPollInterval());

        threshold = config.getQosDropPacketThreshold().shortValue();
        enable = config.isQosAlertEnabled().booleanValue();
        pollInterval = config.getQosAlertPollInterval();

        thread.interrupt();
    }

    public void restoreDefaultConfig() {
        LOG.info("Restoring default configuration");
        setDefault();
        thread.interrupt();
    }


    private void pollDirectStatisticsForAllNodes() {
        LOG.debug("Polling direct statistics from all nodes");
        // TODO: Add all polling logic here
    }

}
