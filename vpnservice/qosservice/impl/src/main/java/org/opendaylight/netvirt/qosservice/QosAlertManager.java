/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;


import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev170301.QosalertConfigBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
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

    private static final FutureCallback<Void> DEFAULT_FUTURE_CALLBACK;

    static {
        DEFAULT_FUTURE_CALLBACK = new FutureCallback<Void>() {

            @Override
            public void onSuccess(Void result) {
                LOG.info("Datastore operation completed successfully");
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in datastore operation {}", error);
            }

        };
    }

    @Inject
    public QosAlertManager(final DataBroker dataBroker, OpendaylightDirectStatisticsService odlDirectStatisticsService,
                            QosalertConfig defaultConfig) {
        LOG.info("{} created",  getClass().getSimpleName());
        initDone = false;
        this.dataBroker = dataBroker;
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.defaultConfig = defaultConfig;
        LOG.info("QosAlert default config poll enable:{} threshold:{} pollInterval:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                defaultConfig.getQosAlertPollInterval());
        setDefault();

    }

    @PostConstruct
    public void init() {
        thread = new Thread(this);
        thread.setDaemon(true);
        initDone = true;
        thread.start();
        LOG.info("{} init done", getClass().getSimpleName());
    }

    @PreDestroy
    public void close() {
        initDone = false;
        thread.interrupt();
        LOG.info("{} close done", getClass().getSimpleName());
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

    public void setThreshold(short threshold) {
        LOG.info("setting threshold {} in config data store", threshold);
        this.threshold = threshold;
        writeConfigDataStore();
    }

    public void setPollInterval(int pollInterval) {
        LOG.info("setting interval {} in config data store", pollInterval);
        this.pollInterval = pollInterval;
        writeConfigDataStore();
    }

    public void setEnable(boolean enable) {
        LOG.info("setting QoS poll to {} in config data store", enable);
        this.enable = enable;
        writeConfigDataStore();
    }

    private static <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path, T data, DataBroker broker,
                                                          FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private void writeConfigDataStore() {

        InstanceIdentifier<QosalertConfig> path = InstanceIdentifier.builder(QosalertConfig.class).build();

        QosalertConfig qosAlertConfig = new QosalertConfigBuilder()
                .setQosDropPacketThreshold(threshold)
                .setQosAlertEnabled(enable)
                .setQosAlertPollInterval(pollInterval)
                .build();

        asyncWrite(LogicalDatastoreType.CONFIGURATION, path, qosAlertConfig, dataBroker, DEFAULT_FUTURE_CALLBACK);
    }

    private void pollDirectStatisticsForAllNodes() {
        LOG.debug("Polling direct statistics from all nodes");
        // TODO: Add all polling logic here
    }

}
