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
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public final class QosAlertManager implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertManager.class);

    private static final FutureCallback<Void> DEFAULT_FUTURE_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("Datastore operation completed successfully");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in datastore operation {}", error);
        }

    };

    private volatile short threshold;
    private volatile boolean alertEnabled;
    private volatile int pollInterval;
    private volatile Thread thread;
    private volatile boolean statsPollThreadStart;

    private final DataBroker dataBroker;
    private final QosalertConfig defaultConfig;
    private final OpendaylightDirectStatisticsService odlDirectStatisticsService;
    private final QosNeutronUtils qosNeutronUtils;
    private final QosEosHandler qosEosHandler;
    private final INeutronVpnManager neutronVpnManager;
    private final ConcurrentHashMap<BigInteger, ConcurrentHashMap<String, QosAlertPortData>>
                                                         qosAlertDpnPortNumberMap = new ConcurrentHashMap<>();


    @Inject
    public QosAlertManager(final DataBroker dataBroker,
            final OpendaylightDirectStatisticsService odlDirectStatisticsService, final QosalertConfig defaultConfig,
            final QosNeutronUtils qosNeutronUtils, final QosEosHandler qosEosHandler,
            final INeutronVpnManager neutronVpnManager) {

        LOG.debug("{} created",  getClass().getSimpleName());
        this.dataBroker = dataBroker;
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.defaultConfig = defaultConfig;
        this.qosNeutronUtils = qosNeutronUtils;
        this.qosEosHandler = qosEosHandler;
        this.neutronVpnManager = neutronVpnManager;
        LOG.debug("QosAlert default config poll alertEnabled:{} threshold:{} pollInterval:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                defaultConfig.getQosAlertPollInterval());
        getDefaultConfig();
    }

    @PostConstruct
    public void init() {
        qosEosHandler.addLocalOwnershipChangedListener(this::setQosAlertOwner);
        qosAlertDpnPortNumberMap.clear();
        QosAlertPortData.setAlertThreshold(threshold);
        statsPollThreadStart = true;
        startStatsPollThread();
        LOG.debug("{} init done", getClass().getSimpleName());
    }

    @PreDestroy
    public void close() {
        statsPollThreadStart = false;
        if (thread != null) {
            thread.interrupt();
        }
        LOG.debug("{} close done", getClass().getSimpleName());
    }

    private void setQosAlertOwner(boolean isOwner) {
        LOG.trace("qos alert set owner : {}", isOwner);
        statsPollThreadStart = isOwner;
        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }
    }

    @Override
    public void run() {
        LOG.debug("Qos alert poll thread started");
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
        LOG.debug("Qos alert poll thread stopped");
    }

    private void startStatsPollThread() {
        if (statsPollThreadStart && alertEnabled && thread == null) {
            initPortStatsData();
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

        LOG.debug("New QoS alert config threshold:{} polling alertEnabled:{} interval:{}",
                config.getQosDropPacketThreshold(), config.isQosAlertEnabled(),
                config.getQosAlertPollInterval());

        threshold = config.getQosDropPacketThreshold().shortValue();
        alertEnabled = config.isQosAlertEnabled().booleanValue();
        pollInterval = config.getQosAlertPollInterval();

        QosAlertPortData.setAlertThreshold(threshold);

        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }

    }

    public void restoreDefaultConfig() {
        LOG.debug("Restoring default configuration");
        getDefaultConfig();
        QosAlertPortData.setAlertThreshold(threshold);
        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }
    }

    public void setThreshold(short threshold) {
        LOG.debug("setting threshold {} in config data store", threshold);
        writeConfigDataStore(alertEnabled, threshold, pollInterval);
    }

    public void setPollInterval(int pollInterval) {
        LOG.debug("setting interval {} in config data store", pollInterval);
        writeConfigDataStore(alertEnabled, threshold, pollInterval);
    }

    public void setEnable(boolean alertEnabled) {
        LOG.debug("setting QoS poll to {} in config data store", alertEnabled);
        writeConfigDataStore(alertEnabled, threshold, pollInterval);
    }

    public void addToQosAlertCache(Port port) {
        LOG.trace("Adding port {} in cache", port.getUuid());

        BigInteger dpnId = qosNeutronUtils.getDpnForInterface(port.getUuid().getValue());

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.debug("DPN ID for port {} not found", port.getUuid());
            return;
        }

        String portNumber = qosNeutronUtils.getPortNumberForInterface(port.getUuid().getValue());

        if (qosAlertDpnPortNumberMap.containsKey(dpnId)) {
            LOG.trace("Adding port {}  port number {} in DPN {}", port.getUuid(), portNumber, dpnId);
            qosAlertDpnPortNumberMap.get(dpnId).put(portNumber, new QosAlertPortData(port, qosNeutronUtils));
        } else {
            LOG.trace("Adding DPN ID {} with port {} port number {}", dpnId, port.getUuid(), portNumber);
            ConcurrentHashMap<String, QosAlertPortData> portDataMap = new ConcurrentHashMap<>();
            portDataMap.put(portNumber, new QosAlertPortData(port, qosNeutronUtils));
            qosAlertDpnPortNumberMap.put(dpnId, portDataMap);
        }
    }

    public void addToQosAlertCache(Network network) {
        LOG.trace("Adding network {} in cache", network.getUuid());

        List<Uuid> subnetIds = qosNeutronUtils.getSubnetIdsFromNetworkId(network.getUuid());

        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = qosNeutronUtils.getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = neutronVpnManager.getNeutronPort(portId);
                if (port != null && !qosNeutronUtils.portHasQosPolicy(port)) {
                    LOG.trace("Adding network {} port {} in cache", network.getUuid(), port.getUuid());
                    addToQosAlertCache(port);
                }
            }
        }
    }

    public void removeFromQosAlertCache(Port port) {
        LOG.trace("Removing port {} from cache", port.getUuid());

        BigInteger dpnId = qosNeutronUtils.getDpnForInterface(port.getUuid().getValue());

        if (dpnId.equals(BigInteger.ZERO)) {
            LOG.debug("DPN ID for port {} not found", port.getUuid());
            return;
        }

        String portNumber = qosNeutronUtils.getPortNumberForInterface(port.getUuid().getValue());

        if (qosAlertDpnPortNumberMap.containsKey(dpnId)
                                         && qosAlertDpnPortNumberMap.get(dpnId).containsKey(portNumber)) {
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

    public void removeFromQosAlertCache(NodeConnectorId nodeConnectorId) {
        LOG.trace("Removing node connector {} from cache", nodeConnectorId.getValue());

        long nodeId = MDSALUtil.getDpnIdFromPortName(nodeConnectorId);

        if (nodeId == -1) {
            LOG.debug("Node ID for node connector {} not found", nodeConnectorId.getValue());
            return;
        }

        BigInteger dpnId = new BigInteger(String.valueOf(nodeId));

        long portId = MDSALUtil.getOfPortNumberFromPortName(nodeConnectorId);

        String portNumber = String.valueOf(portId);

        if (qosAlertDpnPortNumberMap.containsKey(dpnId)
                             && qosAlertDpnPortNumberMap.get(dpnId).containsKey(portNumber)) {
            qosAlertDpnPortNumberMap.get(dpnId).remove(portNumber);
            LOG.trace("Removed DPN {} port number {} from cache", dpnId, portNumber);
        } else {
            LOG.trace("DPN {} port number {} not found in cache", dpnId, portNumber);
        }

    }

    public void removeFromQosAlertCache(Network network) {
        LOG.trace("Removing network {} from cache", network.getUuid());

        List<Uuid> subnetIds = qosNeutronUtils.getSubnetIdsFromNetworkId(network.getUuid());

        for (Uuid subnetId : subnetIds) {
            List<Uuid> portIds = qosNeutronUtils.getPortIdsFromSubnetId(subnetId);
            for (Uuid portId : portIds) {
                Port port = neutronVpnManager.getNeutronPort(portId);
                if (port != null && !qosNeutronUtils.portHasQosPolicy(port)) {
                    LOG.trace("Removing network {} port {} from cache", network.getUuid(), port.getUuid());
                    removeFromQosAlertCache(port);
                }
            }
        }
    }

    private static <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path, T data, DataBroker broker,
                                                          FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, WriteTransaction.CREATE_MISSING_PARENTS);
        Futures.addCallback(tx.submit(), callback, MoreExecutors.directExecutor());
    }

    private void writeConfigDataStore(boolean alertEnabled, short threshold, int pollInterval) {

        InstanceIdentifier<QosalertConfig> path = InstanceIdentifier.builder(QosalertConfig.class).build();

        QosalertConfig qosAlertConfig = new QosalertConfigBuilder()
                .setQosDropPacketThreshold(threshold)
                .setQosAlertEnabled(alertEnabled)
                .setQosAlertPollInterval(pollInterval)
                .build();

        asyncWrite(LogicalDatastoreType.CONFIGURATION, path, qosAlertConfig, dataBroker,
                DEFAULT_FUTURE_CALLBACK);
    }

    private void pollDirectStatisticsForAllNodes() {
        LOG.trace("Polling direct statistics from nodes");

        for (BigInteger dpn : qosAlertDpnPortNumberMap.keySet()) {
            LOG.trace("Polling DPN ID {}", dpn);
            GetNodeConnectorStatisticsInputBuilder input = new GetNodeConnectorStatisticsInputBuilder()
                    .setNode(new NodeRef(InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(IfmConstants.OF_URI_PREFIX + dpn))).build()))
                    .setStoreStats(false);
            Future<RpcResult<GetNodeConnectorStatisticsOutput>> rpcResultFuture =
                    odlDirectStatisticsService.getNodeConnectorStatistics(input.build());

            RpcResult<GetNodeConnectorStatisticsOutput> rpcResult = null;
            try {
                rpcResult = rpcResultFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Exception {} occurred with node {} Direct-Statistics get", e, dpn);
            }
            if (rpcResult != null && rpcResult.isSuccessful() && rpcResult.getResult() != null) {

                GetNodeConnectorStatisticsOutput nodeConnectorStatisticsOutput = rpcResult.getResult();

                List<NodeConnectorStatisticsAndPortNumberMap> nodeConnectorStatisticsAndPortNumberMapList =
                        nodeConnectorStatisticsOutput.getNodeConnectorStatisticsAndPortNumberMap();

                ConcurrentHashMap<String, QosAlertPortData> portDataMap = qosAlertDpnPortNumberMap.get(dpn);
                for (NodeConnectorStatisticsAndPortNumberMap stats : nodeConnectorStatisticsAndPortNumberMapList) {
                    QosAlertPortData portData = portDataMap.get(stats.getNodeConnectorId().getValue());
                    if (portData != null) {
                        portData.updatePortStatistics(stats);
                    }
                }
            } else {
                LOG.error("Direct-Statistics not available for node {}", dpn);
            }

        }
    }

    private void initPortStatsData() {
        for (BigInteger dpn : qosAlertDpnPortNumberMap.keySet()) {
            ConcurrentHashMap<String, QosAlertPortData> portDataMap = qosAlertDpnPortNumberMap.get(dpn);
            for (String portNumber : portDataMap.keySet()) {
                portDataMap.get(portNumber).initPortData();
            }
        }
    }


}
