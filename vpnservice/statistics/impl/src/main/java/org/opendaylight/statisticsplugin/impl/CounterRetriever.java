/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statisticsplugin.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.infrautils.counters.api.OccurenceCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.AggregateFlowStatisticsUpdate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.FlowsStatisticsUpdate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.OpendaylightFlowStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.OpendaylightFlowStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.transaction.rev150304.MultipartTransactionAware;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.transaction.rev150304.TransactionAware;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.transaction.rev150304.TransactionId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetAllNodeConnectorsStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetAllNodeConnectorsStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetNodeConnectorStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetNodeConnectorStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.NodeConnectorStatisticsUpdate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class CounterRetriever implements OpendaylightFlowStatisticsListener, OpendaylightPortStatisticsListener {
    protected static final Logger LOG = LoggerFactory.getLogger(CounterRetriever.class);
    private OpendaylightFlowStatisticsService ofss;
    private OpendaylightPortStatisticsService opss;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final NotificationService notificationService;

    private static ConcurrentMap<TransactionImpl, NodeConnectorCounterResult> nodeConnectorStatCallers = null;

    @Inject
    public CounterRetriever(final RpcProviderRegistry rpcProviderRegistry,
            final NotificationService notificationService) {
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.notificationService = notificationService;
        nodeConnectorStatCallers = new ConcurrentHashMap<>();
    }

    @PreDestroy
    public void destroy() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        notificationService.registerNotificationListener(this);
        ofss = rpcProviderRegistry.getRpcService(OpendaylightFlowStatisticsService.class);
        opss = rpcProviderRegistry.getRpcService(OpendaylightPortStatisticsService.class);
    }

    static class NodeConnectorCounterResult {
        List<NodeConnectorStatisticsUpdate> result;
        boolean finished = false;
        CyclicBarrier syncObject = new CyclicBarrier(2);

        NodeConnectorCounterResult() {
            result = new ArrayList<>();
        }

        boolean isFinished() {
            return finished;
        }

        public void addResult(NodeConnectorStatisticsUpdate resultUpdate) {
            result.add(resultUpdate);
        }

        public void setFinished() {
            finished = true;
        }

        public List<NodeConnectorStatisticsUpdate> getResult() {
            return result;
        }

        public CyclicBarrier getBarrier() {
            return syncObject;
        }
    }

    static class TransactionImpl {
        private final NodeId nodeId;
        private final BigInteger treanactionId;

        TransactionImpl(NodeId nodeId, BigInteger treanactionId) {
            this.nodeId = nodeId;
            this.treanactionId = treanactionId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (nodeId == null ? 0 : nodeId.getValue().hashCode());
            result = prime * result + (treanactionId == null ? 0 : treanactionId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TransactionImpl other = (TransactionImpl) obj;
            if (nodeId == null) {
                if (other.nodeId != null) {
                    return false;
                }
            } else if (!nodeId.getValue().equals(other.nodeId.getValue())) {
                return false;
            }
            if (treanactionId == null) {
                if (other.treanactionId != null) {
                    return false;
                }
            } else if (!treanactionId.equals(other.treanactionId)) {
                return false;
            }
            return true;
        }

    }

    private <T extends TransactionAware> TransactionImpl transactifyFuture(NodeId nodeId, Future<RpcResult<T>> future,
            CounterRequestType type) {
        if (future == null) {
            CounterRetrieverCounters.failed_null_future.inc();
            return null;
        }

        TransactionId mdsalTransactionId = null;
        try {
            RpcResult<T> rpcResult = future.get();
            T output = rpcResult.getResult();
            mdsalTransactionId = output.getTransactionId();
        } catch (InterruptedException | ExecutionException e) {
            CounterRetrieverCounters.failed_getting_transaction_id.inc();
        }

        if (mdsalTransactionId == null) {
            CounterRetrieverCounters.failed_null_transaction_id.inc();
            return null;
        }

        CounterRetrieverCounters.sending_request_to_switch.inc();
        TransactionImpl transaction = new TransactionImpl(nodeId, mdsalTransactionId.getValue());

        if (CounterRequestType.OF_PORT.equals(type)) {
            nodeConnectorStatCallers.put(transaction, new NodeConnectorCounterResult());
        }
        return transaction;
    }

    @Override
    public void onAggregateFlowStatisticsUpdate(AggregateFlowStatisticsUpdate notification) {
        // DO nothing
    }

    public CounterResultDataStructure getNodeConnectorCounters(BigInteger dpId, String portNumber) {
        NodeRef nodeRef = new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.getNodeId(dpId)))).toInstance());
        GetNodeConnectorStatisticsInputBuilder nodeConnectorBuilder =
                new GetNodeConnectorStatisticsInputBuilder().setNode(nodeRef)
                        .setNodeConnectorId(new NodeConnectorId(CountersUtils.getNodeConnectorId(dpId, portNumber)));
        GetNodeConnectorStatisticsInput gncsi = nodeConnectorBuilder.build();
        TransactionImpl transactifyFuture = transactifyFuture(new NodeId(CountersUtils.getNodeId(dpId)),
                opss.getNodeConnectorStatistics(gncsi), CounterRequestType.OF_PORT);
        return transactifyFuture != null ? createNodeConnectorResultMap(transactifyFuture) : null;
    }

    public CounterResultDataStructure getNodeCounters(BigInteger dpId) {
        NodeRef nodeRef = new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.getNodeId(dpId)))).toInstance());
        GetAllNodeConnectorsStatisticsInputBuilder nodeBuilder =
                new GetAllNodeConnectorsStatisticsInputBuilder().setNode(nodeRef);
        GetAllNodeConnectorsStatisticsInput gancsi = nodeBuilder.build();
        TransactionImpl transactifyFuture = transactifyFuture(new NodeId(CountersUtils.getNodeId(dpId)),
                opss.getAllNodeConnectorsStatistics(gancsi), CounterRequestType.OF_PORT);
        return transactifyFuture != null ? createNodeConnectorResultMap(transactifyFuture) : null;
    }

    @Override
    public void onFlowsStatisticsUpdate(FlowsStatisticsUpdate notification) {

    }

    @Override
    public void onNodeConnectorStatisticsUpdate(NodeConnectorStatisticsUpdate notification) {
        CounterRetrieverCounters.got_node_connector_counters.inc();
        LOG.debug("got notification {}", notification.getTransactionId());

        NodeConnectorCounterResult result = getNodeConnectorStatTransactionResult(notification.getId(), notification);
        if (result == null) {
            LOG.debug("got notification on non exist transaction {}", notification.getTransactionId());
            return;
        }
        result.addResult(notification);
        if (!notification.isMoreReplies()) {
            try {
                result.getBarrier().await(CountersUtils.NODE_CONNECTOR_REPLIES_TIMOUT, TimeUnit.MILLISECONDS);
                LOG.trace("counter retrieval finished for transaction {}", notification.getTransactionId().getValue());
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                LOG.trace("timeout - do nothing");
            }
        }
    }

    private NodeConnectorCounterResult getNodeConnectorStatTransactionResult(NodeId nodeId,
            TransactionAware notification) {
        TransactionId mdsalTransactionId = notification.getTransactionId();

        if (mdsalTransactionId == null) {
            CounterRetrieverCounters.failed_update_null_transaction_id.inc();
            return null;
        }
        TransactionImpl transaction = new TransactionImpl(nodeId, mdsalTransactionId.getValue());

        if (!nodeConnectorStatCallers.containsKey(transaction)) {
            CounterRetrieverCounters.failed_unknown_transaction_id.inc();
            LOG.warn("unknown notification id {}, data {}", notification.getTransactionId(), notification);
            return null;
        }

        if (notification instanceof MultipartTransactionAware) {
            CounterRetrieverCounters.got_node_connector_partial_multipart_result.inc();
            return nodeConnectorStatCallers.get(transaction);
        } else if (notification instanceof FlowsStatisticsUpdate) {
            CounterRetrieverCounters.got_node_connector_partial_stat_result.inc();
            return nodeConnectorStatCallers.get(transaction);
        }
        return null;
    }

    private CounterResultDataStructure createNodeConnectorResultMap(TransactionImpl transactifyFuture) {
        NodeConnectorCounterResult result = nodeConnectorStatCallers.get(transactifyFuture);

        if (result != null) {
            try {
                result.getBarrier().await(CountersUtils.CREATE_RESULT_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                LOG.debug("got interrupt while waiting for node connector statistics result");
                return null;
            }
        } else {
            LOG.debug("didn't find node connector result waiting for transaction {}", transactifyFuture.treanactionId);
            return null;
        }

        List<NodeConnectorStatisticsUpdate> nodeConnectorUpdates = result.getResult();
        CounterResultDataStructure crds = new CounterResultDataStructure();
        for (NodeConnectorStatisticsUpdate nodeConnectorUpdate : nodeConnectorUpdates) {
            List<NodeConnectorStatisticsAndPortNumberMap> statList =
                    nodeConnectorUpdate.getNodeConnectorStatisticsAndPortNumberMap();
            for (NodeConnectorStatisticsAndPortNumberMap stat : statList) {
                if (stat.getNodeConnectorId() == null) {
                    continue;
                }
                String resultId = stat.getNodeConnectorId().getValue();
                crds.addCounterResult(resultId);
                if (stat.getBytes() != null) {
                    crds.addCounterToGroup(resultId, CountersUtils.BYTES_GROUP_NAME,
                            CountersUtils.BYTES_RECEIVED_COUNTER_NAME, stat.getBytes().getReceived());
                    crds.addCounterToGroup(resultId, CountersUtils.BYTES_GROUP_NAME,
                            CountersUtils.BYTES_TRANSMITTED_COUNTER_NAME, stat.getBytes().getTransmitted());
                }
                if (stat.getPackets() != null) {
                    crds.addCounterToGroup(resultId, CountersUtils.PACKETS_GROUP_NAME,
                            CountersUtils.PACKETS_RECEIVED_COUNTER_NAME, stat.getPackets().getReceived());
                    crds.addCounterToGroup(resultId, CountersUtils.PACKETS_GROUP_NAME,
                            CountersUtils.PACKETS_TRANSMITTED_COUNTER_NAME, stat.getPackets().getTransmitted());
                }
                if (stat.getDuration() != null) {
                    crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                            CountersUtils.DURATION_SECOND_COUNTER_NAME, big(stat.getDuration().getSecond().getValue()));
                    crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                            CountersUtils.DURATION_NANO_SECOND_COUNTER_NAME,
                            big(stat.getDuration().getNanosecond().getValue()));
                }
            }
        }

        nodeConnectorStatCallers.remove(transactifyFuture);
        return crds;
    }

    private BigInteger big(Long longValue) {
        return BigInteger.valueOf(longValue);
    }

    enum CounterRetrieverCounters {
        failed_getting_transaction_id, //
        failed_null_future, //
        failed_null_transaction_id, //
        failed_unknown_transaction_id, //
        failed_update_null_transaction_id, //
        got_switch_flow_counters, //
        got_node_connector_partial_multipart_result, //
        got_node_connector_partial_stat_result, //
        got_flow_partial_multipart_result, //
        got_flow_partial_stat_result, //
        sending_request_to_switch, //
        got_node_connector_counters, //
        ;
        private OccurenceCounter counter;

        CounterRetrieverCounters() {
            counter = new OccurenceCounter(getClass().getEnclosingClass().getSimpleName(), name(), "");
        }

        public void inc() {
            counter.inc();
        }
    }
}
