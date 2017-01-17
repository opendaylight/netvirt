/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosalert;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev161205.QosalertConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.qosalert.config.rev161205.QosalertConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.flow.capable.node.connector.statistics.FlowCapableNodeConnectorStatistics;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class QosAlertManager implements Runnable {

    private int threshold;
    private boolean enable;
    private String alertLogFile;
    private QosalertConfig defaultConfig;
    private boolean initDone;
    private Map<String, QosPort> portMap;
    private final DataBroker dataBroker;
    private Thread thread;
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertManager.class);
    private static QosAlertManager instance;
    private static int QOS_ALERT_MANAGER_POLLING_TIME_MILLI_SEC = 300000; // 5 minutes

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

    private QosAlertManager(final DataBroker dataBroker, QosalertConfig defaultConfig) {
        LOG.info("QosAlert Manager created");
        this.dataBroker = dataBroker;
        this.defaultConfig = defaultConfig;
        LOG.info("QosAlert default config poll enable:{} threshold:{} logfile:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                                                                defaultConfig.getQosAlertLogFile());
        setDefault();
        portMap = new HashMap<String, QosPort>();
    }

    public static QosAlertManager getInstance(final DataBroker dataBroker, QosalertConfig qosalertConfig) {
        LOG.info("QosAlertManager: getting the instance");
        if (instance == null) {
            instance = new QosAlertManager(dataBroker, qosalertConfig);
        }
        return instance;
    }

    public void init() {
        LOG.info("QosAlertManager: init");
        portMap.clear();
        QosPort.setAlertThreshold(threshold);
        QosAlertGenerator.initAlertLogFile(alertLogFile);
        thread = new Thread(this);
        thread.setDaemon(true);
        initDone = true;
        thread.start();
        LOG.info("QosAlertManager: init done");
    }

    public void close() {
        LOG.info("QosAlertManager: close");
        initDone = false;
        portMap.clear();
        QosAlertGenerator.close();
        thread.interrupt();
        LOG.info("QosAlertManager: close done");
    }

    @Override
    public void run() {
        LOG.info("Qos alert poll thread started");
        while (initDone && (!thread.isInterrupted())) {
            LOG.debug("Thread loop polling :{} threshold:{}", enable, threshold);

            try {
                if (enable) {
                    pollAllPorts();
                    QosAlertGenerator.flushAlerts();
                }
                Thread.sleep(QOS_ALERT_MANAGER_POLLING_TIME_MILLI_SEC);
            } catch (final InterruptedException e) {
                LOG.error("Error in polling {}", e);
            }
        }
        LOG.info("Qos alert poll thread stopped");
    }

    private void setDefault() {
        enable = defaultConfig.isQosAlertEnabled();
        threshold = defaultConfig.getQosDropPacketThreshold();
        alertLogFile = defaultConfig.getQosAlertLogFile();
    }

    public void setThreshold(int threshold) {
        LOG.info("setting threshold {} in config data store", threshold);
        writeConfigDataStore(threshold, enable, alertLogFile);
    }

    public void setEnable(boolean enable) {
        LOG.info("setting QoS poll to {} in config data store", enable);
        writeConfigDataStore(threshold, enable, alertLogFile);
    }

    public void setQosAlertLogFileName(String alertLogFile) {
        LOG.info("setting QoS alert log file {} in config data store", alertLogFile);
        writeConfigDataStore(threshold, enable, alertLogFile);
    }

    private void writeConfigDataStore(int threshold, boolean enable, String alertLogFile) {

        InstanceIdentifier<QosalertConfig> path = InstanceIdentifier.builder(QosalertConfig.class).build();

        QosalertConfig qosAlertConfig = new QosalertConfigBuilder()
                .setQosDropPacketThreshold(threshold)
                .setQosAlertEnabled(enable)
                .setQosAlertLogFile(alertLogFile).build();

        asyncWrite(LogicalDatastoreType.CONFIGURATION, path, qosAlertConfig, dataBroker, DEFAULT_FUTURE_CALLBACK);
    }


    public void setQosalertConfig(QosalertConfig config) {

        LOG.info("New QoS alert config threshold:{} polling enabled:{} logfile:{}",
                config.getQosDropPacketThreshold(), config.isQosAlertEnabled(), config.getQosAlertLogFile());

        threshold = config.getQosDropPacketThreshold().intValue();
        enable    = config.isQosAlertEnabled().booleanValue();
        alertLogFile = config.getQosAlertLogFile();

        if (enable == false) {
            portMap.clear();
        }
        QosPort.setAlertThreshold(threshold);
        QosAlertGenerator.initAlertLogFile(alertLogFile);
    }

    public void restoreDefaultConfig() {
        LOG.info("Restoring default configuration");
        setDefault();
        if (enable == false) {
            portMap.clear();
        }
        QosPort.setAlertThreshold(threshold);
        QosAlertGenerator.initAlertLogFile(alertLogFile);
    }

    private static <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                           InstanceIdentifier<T> path, DataBroker broker) {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (InterruptedException e) {
            LOG.error("QoS error in data store operation {}", e);
        } catch (ExecutionException e) {
            LOG.error("QoS error in data store operation {}", e);
        }

        return result;
    }

    private static <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path, T data, DataBroker broker,
                                                          FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private void pollAllPorts() {

        InstanceIdentifier<Nodes> path = InstanceIdentifier.builder(Nodes.class).build();

        LOG.debug("Polling all nodes");

        Optional<Nodes> nodesOptional = read(LogicalDatastoreType.OPERATIONAL, path, dataBroker);
        if (nodesOptional.isPresent()) {

            Nodes nodes = nodesOptional.get();
            List<Node> nodeList = nodes.getNode();

            for (Node node : nodeList) {
                LOG.debug("Polling node  {}", node.getKey());
                for (NodeConnector nodeConnector : node.getNodeConnector()) {

                    FlowCapableNodeConnector port = nodeConnector.getAugmentation(FlowCapableNodeConnector.class);

                    LOG.debug("Polling port stats of port {}", port.getName());

                    FlowCapableNodeConnectorStatisticsData statsData = nodeConnector.getAugmentation(
                                                                         FlowCapableNodeConnectorStatisticsData.class);
                    FlowCapableNodeConnectorStatistics stats =  statsData.getFlowCapableNodeConnectorStatistics();
                    QosPort qosPort = portMap.get(port.getName());

                    if (qosPort == null) {
                        qosPort = new QosPort(port.getName(), stats);
                        portMap.put(port.getName(),qosPort);
                    } else {
                        qosPort.updatePortStatistics(stats);
                    }
                }

            }

        } else {
            LOG.error("Error: can not find any node");
        }
    }


}
