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
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public final class QosAlertManager implements Runnable {

    private short threshold;
    private boolean alertEnabled;
    private int pollInterval;
    private final QosalertConfig defaultConfig;
    private boolean statsPollThreadStart;
    private static DataBroker dataBroker;
    private static OpendaylightDirectStatisticsService odlDirectStatisticsService;
    private static INeutronVpnManager neutronVpnManager;
    private Thread thread;
    private static OdlInterfaceRpcService odlInterfaceRpcService;
    private static ConcurrentHashMap<BigInteger, ConcurrentHashMap<String, QosAlertPortData>> qosAlertDpnPortNumberMap =
                                                                                              new ConcurrentHashMap<>();
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
                  QosalertConfig defaultConfig,
                           OdlInterfaceRpcService odlInterfaceRpcService, INeutronVpnManager neutronVpnManager) {
        LOG.info("{} created",  getClass().getSimpleName());
        this.dataBroker = dataBroker;
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.neutronVpnManager =  neutronVpnManager;
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

    public void setEnable(boolean alertEnabled) {
        LOG.info("setting QoS poll to {} in config data store", alertEnabled);
        this.alertEnabled = alertEnabled;
        writeConfigDataStore();
    }

    public static void addPortToQosAlertCache(Port port) {
        LOG.trace("Adding port {} in cache", port.getUuid());

        BigInteger dpnId = QosNeutronUtils.getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.debug("DPN ID for port {} not found", port.getUuid());
            return;
        }

        String portNumber = QosNeutronUtils.getPortNumberForInterface(odlInterfaceRpcService,
                                                                                            port.getUuid().getValue());

        if (qosAlertDpnPortNumberMap.containsKey(dpnId)) {
            LOG.trace("Adding port {}  port number {} in DPN {}", port.getUuid(), portNumber, dpnId);
            qosAlertDpnPortNumberMap.get(dpnId).put(portNumber, new QosAlertPortData(port));
        } else {
            LOG.trace("Adding DPN ID {} with port {} port number {}", dpnId, port.getUuid(), portNumber);
            ConcurrentHashMap<String, QosAlertPortData> portDataMap = new ConcurrentHashMap<>();
            portDataMap.put(portNumber, new QosAlertPortData(port));
            qosAlertDpnPortNumberMap.put(dpnId, portDataMap);
        }
    }

    public static void removePortFromQosAlertCache(Port port) {
        LOG.trace("Removing port {} from cache", port.getUuid());

        BigInteger dpnId = QosNeutronUtils.getDpnForInterface(odlInterfaceRpcService, port.getUuid().getValue());

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.debug("DPN ID for port {} not found", port.getUuid());
            return;
        }

        String portNumber = QosNeutronUtils.getPortNumberForInterface(odlInterfaceRpcService,
                                                                                            port.getUuid().getValue());

        if (qosAlertDpnPortNumberMap.containsKey(dpnId) && qosAlertDpnPortNumberMap.get(dpnId).containsKey(
                                                                                                       portNumber)) {
            qosAlertDpnPortNumberMap.get(dpnId).remove(portNumber);
            LOG.trace("Removed DPN {} port {} port number {} from cache", dpnId, port.getUuid(), portNumber);
            if (qosAlertDpnPortNumberMap.get(dpnId).isEmpty()) {
                LOG.trace("DPN {} empty. Removing from cache", dpnId);
                qosAlertDpnPortNumberMap.remove(dpnId);
            }
        } else {
            LOG.trace("DPN {} port {} port number {} not found in cache", dpnId, port.getUuid(), portNumber);
        }

    }

    public static void removePortFromQosAlertCache(NodeConnectorId nodeConnectorId) {
        LOG.trace("Removing node connector {} from cache", nodeConnectorId.getValue());

        long nodeId = MDSALUtil.getDpnIdFromPortName(nodeConnectorId);

        if (nodeId == -1) {
            LOG.debug("Node ID for node connector {} not found", nodeConnectorId.getValue());
            return;
        }

        BigInteger dpnId = new BigInteger(String.valueOf(nodeId));

        long portId = MDSALUtil.getOfPortNumberFromPortName(nodeConnectorId);

        String portNumber = String.valueOf(portId);

        if (qosAlertDpnPortNumberMap.containsKey(dpnId) && qosAlertDpnPortNumberMap.get(dpnId).containsKey(
                                                                                                        portNumber)) {
            qosAlertDpnPortNumberMap.get(dpnId).remove(portNumber);
            LOG.trace("Removed DPN {} port number {} from cache", dpnId, portNumber);
        } else {
            LOG.trace("DPN {} port number {} not found in cache", dpnId, portNumber);
        }

    }

    public static void addNetworkToQosAlertCache(Network network) {
        LOG.trace("Adding network {} in cache", network.getUuid());

        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = neutronVpnManager.getNeutronPort(portId);
                        if (port != null) {
                            if (!QosNeutronUtils.portHasQosPolicy(neutronVpnManager, port)) {
                                LOG.debug("Adding network {} port {} in cache", network.getUuid(), port.getUuid());
                                addPortToQosAlertCache(port);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void removeNetworkFromQosAlertCache(Network network) {
        LOG.trace("Removing network {} from cache", network.getUuid());

        List<Uuid> subnetIds = QosNeutronUtils.getSubnetIdsFromNetworkId(dataBroker, network.getUuid());

        if (subnetIds != null) {
            for (Uuid subnetId : subnetIds) {
                List<Uuid> portIds = QosNeutronUtils.getPortIdsFromSubnetId(dataBroker, subnetId);
                if (portIds != null) {
                    for (Uuid portId : portIds) {
                        Port port = neutronVpnManager.getNeutronPort(portId);
                        if (port != null) {
                            if (!QosNeutronUtils.portHasQosPolicy(neutronVpnManager, port)) {
                                LOG.debug("Removing network {} port {} from cache", network.getUuid(), port.getUuid());
                                removePortFromQosAlertCache(port);
                            }
                        }
                    }
                }
            }
        }
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
                .setQosAlertEnabled(alertEnabled)
                .setQosAlertPollInterval(pollInterval)
                .build();

        asyncWrite(LogicalDatastoreType.CONFIGURATION, path, qosAlertConfig, dataBroker, DEFAULT_FUTURE_CALLBACK);
    }

    private void pollDirectStatisticsForAllNodes() {
        LOG.debug("Polling direct statistics from nodes");
        // TODO: Add all polling logic here
        for (BigInteger dpn : qosAlertDpnPortNumberMap.keySet()) {
            LOG.debug("Polling DPN ID {}", dpn);
            for (String portNumber : qosAlertDpnPortNumberMap.get(dpn).keySet()) {
                LOG.debug("DPN ID {} PortNumber {} Port uuid {}",
                        dpn, portNumber, qosAlertDpnPortNumberMap.get(dpn).get(portNumber).port.getUuid());
            }
        }
    }

}
