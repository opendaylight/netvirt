/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.math.BigInteger;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qosalert.config.rev170301.QosalertConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public final class QosAlertManager implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertManager.class);

    private volatile boolean alertEnabled;
    private volatile int pollInterval;
    private volatile Thread thread;
    private volatile boolean statsPollThreadStart;

    private final ManagedNewTransactionRunner txRunner;
    private final QosalertConfig defaultConfig;
    private final OpendaylightDirectStatisticsService odlDirectStatisticsService;
    private final QosNeutronUtils qosNeutronUtils;
    private final QosEosHandler qosEosHandler;
    private final IInterfaceManager interfaceManager;
    private final Set unprocessedInterfaceIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<BigInteger, ConcurrentMap<String, QosAlertPortData>> qosAlertDpnPortNumberMap =
            new ConcurrentHashMap<>();
    private final AlertThresholdSupplier alertThresholdSupplier = new AlertThresholdSupplier();

    @Inject
    public QosAlertManager(final DataBroker dataBroker,
            final OpendaylightDirectStatisticsService odlDirectStatisticsService, final QosalertConfig defaultConfig,
            final QosNeutronUtils qosNeutronUtils, final QosEosHandler qosEosHandler,
            final IInterfaceManager interfaceManager) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.odlDirectStatisticsService = odlDirectStatisticsService;
        this.interfaceManager = interfaceManager;
        this.defaultConfig = defaultConfig;
        this.qosNeutronUtils = qosNeutronUtils;
        this.qosEosHandler = qosEosHandler;
        LOG.trace("QosAlert default config poll alertEnabled:{} threshold:{} pollInterval:{}",
                defaultConfig.isQosAlertEnabled(), defaultConfig.getQosDropPacketThreshold(),
                defaultConfig.getQosAlertPollInterval());
        getDefaultConfig();
    }

    @PostConstruct
    public void init() {
        qosEosHandler.addLocalOwnershipChangedListener(this::setQosAlertOwner);
        qosAlertDpnPortNumberMap.clear();
        statsPollThreadStart = true;
        startStatsPollThread();
        LOG.trace("{} init done", getClass().getSimpleName());
    }

    @PreDestroy
    public void close() {
        statsPollThreadStart = false;
        if (thread != null) {
            thread.interrupt();
        }
        LOG.trace("{} close done", getClass().getSimpleName());
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
            LOG.trace("Thread loop polling :{} threshold:{} pollInterval:{}",
                    alertEnabled, alertThresholdSupplier.get(), pollInterval);

            try {
                pollDirectStatisticsForAllNodes();
                Thread.sleep(pollInterval * 60L * 1000L); // pollInterval in minutes
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
        pollInterval = defaultConfig.getQosAlertPollInterval();

        alertThresholdSupplier.set(defaultConfig.getQosDropPacketThreshold());
    }

    public void setQosalertConfig(QosalertConfig config) {

        LOG.debug("New QoS alert config threshold:{} polling alertEnabled:{} interval:{}",
                config.getQosDropPacketThreshold(), config.isQosAlertEnabled(),
                config.getQosAlertPollInterval());

        alertEnabled = config.isQosAlertEnabled().booleanValue();
        pollInterval = config.getQosAlertPollInterval();

        alertThresholdSupplier.set(config.getQosDropPacketThreshold().shortValue());

        if (thread != null) {
            thread.interrupt();
        } else {
            startStatsPollThread();
        }

    }

    public void restoreDefaultConfig() {
        LOG.debug("Restoring default configuration");
        getDefaultConfig();
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
        writeConfigDataStore(alertEnabled, alertThresholdSupplier.get().shortValue(), pollInterval);
    }

    public void setEnable(boolean enable) {
        LOG.debug("setting QoS poll to {} in config data store", enable);
        writeConfigDataStore(enable, alertThresholdSupplier.get().shortValue(), pollInterval);
    }

    public void addInterfaceIdInQoSAlertCache(String ifaceId) {
        LOG.trace("Adding interface id {} in cache", ifaceId);
        InterfaceInfo interfaceInfo =
                interfaceManager.getInterfaceInfoFromOperationalDataStore(ifaceId);
        if (interfaceInfo == null) {
            LOG.debug("Interface not found {}. Added in cache now to process later ", ifaceId);
            unprocessedInterfaceIds.add(ifaceId);
        } else {
            addToQosAlertCache(interfaceInfo);
        }
    }

    public void processInterfaceUpEvent(String ifaceId) {
        LOG.trace("processInterfaceUpEvent {}", ifaceId);
        if (unprocessedInterfaceIds.remove(ifaceId)) {
            addInterfaceIdInQoSAlertCache(ifaceId);
        }
    }

    private void addToQosAlertCache(InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        if (dpnId.equals(IfmConstants.INVALID_DPID)) {
            LOG.warn("Interface {} could not be added to Qos Alert Cache because Dpn Id is not found",
                    interfaceInfo.getInterfaceName());
            return;
        }

        Port port = qosNeutronUtils.getNeutronPort(interfaceInfo.getInterfaceName());
        if (port == null) {
            LOG.warn("Port {} not added to Qos Alert Cache because it is not found", interfaceInfo.getInterfaceName());
            return;
        }

        String portNumber = String.valueOf(interfaceInfo.getPortNo());

        LOG.trace("Adding DPN ID {} with port {} port number {}", dpnId, port.getUuid(), portNumber);

        qosAlertDpnPortNumberMap.computeIfAbsent(dpnId, key -> new ConcurrentHashMap<>())
                .put(portNumber, new QosAlertPortData(port, qosNeutronUtils, alertThresholdSupplier));
    }

    public void removeInterfaceIdFromQosAlertCache(String ifaceId) {

        LOG.trace("If present, remove interface {} from cache", ifaceId);
        unprocessedInterfaceIds.remove(ifaceId);
        InterfaceInfo interfaceInfo =
                interfaceManager.getInterfaceInfoFromOperationalDataStore(ifaceId);
        if (interfaceInfo == null) {
            return;
        }
        BigInteger dpnId = interfaceInfo.getDpId();
        String portNumber = String.valueOf(interfaceInfo.getPortNo());
        removeFromQosAlertCache(dpnId, portNumber);
    }

    public void removeLowerLayerIfFromQosAlertCache(String lowerLayerIf) {
        LOG.trace("If present, remove lowerLayerIf {} from cache", lowerLayerIf);
        BigInteger dpnId = qosNeutronUtils.getDpnIdFromLowerLayerIf(lowerLayerIf);
        String portNumber = qosNeutronUtils.getPortNumberFromLowerLayerIf(lowerLayerIf);
        if (dpnId == null || portNumber == null) {
            LOG.warn("Interface {} not in openflow:dpnid:portnum format, could not remove from cache", lowerLayerIf);
            return;
        }
        removeFromQosAlertCache(dpnId, portNumber);
    }

    private void removeFromQosAlertCache(BigInteger dpnId, String portNumber) {
        if (qosAlertDpnPortNumberMap.containsKey(dpnId)
                && qosAlertDpnPortNumberMap.get(dpnId).containsKey(portNumber)) {
            qosAlertDpnPortNumberMap.get(dpnId).remove(portNumber);
            LOG.trace("Removed interace {}:{} from cache", dpnId, portNumber);
            if (qosAlertDpnPortNumberMap.get(dpnId).isEmpty()) {
                LOG.trace("DPN {} empty. Removing dpn from cache", dpnId);
                qosAlertDpnPortNumberMap.remove(dpnId);
            }
        }
    }

    private void writeConfigDataStore(boolean qosAlertEnabled, short dropPacketThreshold, int alertPollInterval) {

        InstanceIdentifier<QosalertConfig> path = InstanceIdentifier.builder(QosalertConfig.class).build();

        QosalertConfig qosAlertConfig = new QosalertConfigBuilder()
                .setQosDropPacketThreshold(dropPacketThreshold)
                .setQosAlertEnabled(qosAlertEnabled)
                .setQosAlertPollInterval(alertPollInterval)
                .build();

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.put(LogicalDatastoreType.CONFIGURATION, path, qosAlertConfig,
                    WriteTransaction.CREATE_MISSING_PARENTS)), LOG, "Error writing to the config data store");
    }

    private void pollDirectStatisticsForAllNodes() {
        LOG.trace("Polling direct statistics from nodes");

        for (Entry<BigInteger, ConcurrentMap<String, QosAlertPortData>> entry : qosAlertDpnPortNumberMap.entrySet()) {
            BigInteger dpn = entry.getKey();
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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not get Direct-Statistics for node {} Exception occurred ", dpn, e);
                }
                else {
                    LOG.info("Could not get Direct-Statistics for node {}", dpn);
                }
            }
            if (rpcResult != null && rpcResult.isSuccessful() && rpcResult.getResult() != null) {

                GetNodeConnectorStatisticsOutput nodeConnectorStatisticsOutput = rpcResult.getResult();

                List<NodeConnectorStatisticsAndPortNumberMap> nodeConnectorStatisticsAndPortNumberMapList =
                        nodeConnectorStatisticsOutput.getNodeConnectorStatisticsAndPortNumberMap();

                ConcurrentMap<String, QosAlertPortData> portDataMap = entry.getValue();
                for (NodeConnectorStatisticsAndPortNumberMap stats : nodeConnectorStatisticsAndPortNumberMapList) {
                    QosAlertPortData portData = portDataMap.get(stats.getNodeConnectorId().getValue());
                    if (portData != null) {
                        portData.updatePortStatistics(stats);
                    }
                }
            } else {
                LOG.info("Direct-Statistics not available for node {}", dpn);
            }

        }
    }

    private void initPortStatsData() {
        qosAlertDpnPortNumberMap.values().forEach(portDataMap -> portDataMap.values()
                .forEach(QosAlertPortData::initPortData));
    }

    private static class AlertThresholdSupplier implements Supplier<BigInteger> {
        private volatile BigInteger alertThreshold = BigInteger.valueOf(0);

        void set(short threshold) {
            alertThreshold = BigInteger.valueOf(threshold);
        }

        @Override
        public BigInteger get() {
            return alertThreshold;
        }
    }
}
