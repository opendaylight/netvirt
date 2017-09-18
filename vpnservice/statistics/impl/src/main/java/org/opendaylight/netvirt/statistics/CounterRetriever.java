/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.counters.api.OccurenceCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.config.rev170326.StatisticsConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class CounterRetriever {
    protected static final Logger LOG = LoggerFactory.getLogger(CounterRetriever.class);
    private final OpendaylightDirectStatisticsService odlDirectStatsService;
    private static long nodeResultTimeout;

    @Inject
    public CounterRetriever(final StatisticsConfig statisticsConfig,
            final OpendaylightDirectStatisticsService odlDirectStatsService) {
        this.odlDirectStatsService = odlDirectStatsService;
        nodeResultTimeout = statisticsConfig.getNodeCounterResultTimeout();
    }

    @PreDestroy
    public void destroy() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    public CounterResultDataStructure getNodeConnectorCountersDirect(NodeId nodeId, NodeConnectorId nodeConnectorId) {
        GetNodeConnectorStatisticsInput gncsi = getNodeConnectorStatisticsInputBuilder(nodeId, nodeConnectorId);

        Future<RpcResult<GetNodeConnectorStatisticsOutput>> rpcResultFuture =
                odlDirectStatsService.getNodeConnectorStatistics(gncsi);
        RpcResult<GetNodeConnectorStatisticsOutput> rpcResult = null;
        try {
            rpcResult = rpcResultFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            CounterRetrieverCounters.failed_getting_node_connector_counters.inc();
            LOG.warn("Unable to retrieve node connector counters for port {}", nodeConnectorId);
            return null;
        }

        if (rpcResult != null && rpcResult.isSuccessful() && rpcResult.getResult() != null) {
            GetNodeConnectorStatisticsOutput nodeConnectorStatsOutput = rpcResult.getResult();
            return createNodeConnectorResultMapDirect(nodeConnectorStatsOutput, nodeConnectorId);
        } else {
            CounterRetrieverCounters.failed_getting_rpc_result_for_node_connector_counters.inc();
            LOG.warn("Unable to retrieve node connector counters for port {}", nodeConnectorId);
            return null;
        }
    }

    public CounterResultDataStructure getNodeCountersDirect(Node node) {
        List<CounterResultDataStructure> countersResults = new ArrayList<CounterResultDataStructure>();
        List<CompletableFuture<NodeConnectorStatisticsSupplierOutput>> futureList = new ArrayList<>();
        for (NodeConnector nodeConnector : node.getNodeConnector()) {
            GetNodeConnectorStatisticsInput gncsi =
                    getNodeConnectorStatisticsInputBuilder(node.getId(), nodeConnector.getId());
            futureList.add(CompletableFuture.supplyAsync(
                    new NodeConnectorStatisticsSupplier(odlDirectStatsService, gncsi, nodeConnector.getId())));
        }
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
            allOf.get(nodeResultTimeout, TimeUnit.SECONDS);
            for (int i = 0; i < futureList.size(); i++) {
                CompletableFuture<NodeConnectorStatisticsSupplierOutput> completableCurrentResult = futureList.get(i);
                if (completableCurrentResult == null) {
                    LOG.warn("Unable to retrieve node counters");
                    CounterRetrieverCounters.failed_getting_node_counters.inc();
                    return null;
                }
                RpcResult<GetNodeConnectorStatisticsOutput> currentResult =
                        completableCurrentResult.get().getNodeConnectorStatisticsOutput();
                if (currentResult != null && currentResult.isSuccessful() && currentResult.getResult() != null) {
                    GetNodeConnectorStatisticsOutput nodeConnectorStatsOutput = currentResult.getResult();
                    countersResults.add(createNodeConnectorResultMapDirect(nodeConnectorStatsOutput,
                            completableCurrentResult.get().getNodeConnectrId()));
                } else {
                    CounterRetrieverCounters.failed_getting_node_counters.inc();
                    LOG.warn("Unable to retrieve node counters");
                    return null;
                }
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            CounterRetrieverCounters.failed_getting_node_counters.inc();
            LOG.warn("Unable to retrieve node counters");
            return null;
        }

        return mergeCountersResults(countersResults);
    }

    private CounterResultDataStructure mergeCountersResults(List<CounterResultDataStructure> countersResults) {
        CounterResultDataStructure crds = new CounterResultDataStructure();
        for (CounterResultDataStructure currentCrds : countersResults) {
            for (String resultId : currentCrds.getResults().keySet()) {
                crds.addCounterResult(resultId, currentCrds.getResults().get(resultId));
            }
        }
        return crds;
    }

    private GetNodeConnectorStatisticsInput getNodeConnectorStatisticsInputBuilder(NodeId nodeId,
            NodeConnectorId nodeConnectorId) {
        NodeRef nodeRef = new NodeRef(
                InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId)).toInstance());
        GetNodeConnectorStatisticsInputBuilder nodeConnectorBuilder =
                new GetNodeConnectorStatisticsInputBuilder().setNode(nodeRef).setNodeConnectorId(nodeConnectorId);
        GetNodeConnectorStatisticsInput gncsi = nodeConnectorBuilder.build();
        return gncsi;
    }

    private CounterResultDataStructure createNodeConnectorResultMapDirect(
            GetNodeConnectorStatisticsOutput nodeConnectorStatsOutput, NodeConnectorId nodeConnectorId) {
        List<NodeConnectorStatisticsAndPortNumberMap> nodeConnectorUpdates =
                nodeConnectorStatsOutput.getNodeConnectorStatisticsAndPortNumberMap();
        if (nodeConnectorUpdates == null || nodeConnectorUpdates.isEmpty()) {
            CounterRetrieverCounters.failed_getting_result_map_for_node_connector.inc();
            LOG.warn("Unable to retrieve statistics info for node connector");
            return null;
        }

        CounterResultDataStructure crds = new CounterResultDataStructure();
        for (NodeConnectorStatisticsAndPortNumberMap nodeConnectorUpdate : nodeConnectorUpdates) {
            if (nodeConnectorUpdate.getNodeConnectorId() == null) {
                continue;
            }
            String resultId = nodeConnectorId.getValue();
            crds.addCounterResult(resultId);
            if (nodeConnectorUpdate.getBytes() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.BYTES_GROUP_NAME,
                        CountersUtils.BYTES_RECEIVED_COUNTER_NAME, nodeConnectorUpdate.getBytes().getReceived());
                crds.addCounterToGroup(resultId, CountersUtils.BYTES_GROUP_NAME,
                        CountersUtils.BYTES_TRANSMITTED_COUNTER_NAME, nodeConnectorUpdate.getBytes().getTransmitted());
            }
            if (nodeConnectorUpdate.getPackets() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.PACKETS_GROUP_NAME,
                        CountersUtils.PACKETS_RECEIVED_COUNTER_NAME, nodeConnectorUpdate.getPackets().getReceived());
                crds.addCounterToGroup(resultId, CountersUtils.PACKETS_GROUP_NAME,
                        CountersUtils.PACKETS_TRANSMITTED_COUNTER_NAME,
                        nodeConnectorUpdate.getPackets().getTransmitted());
            }
            if (nodeConnectorUpdate.getDuration() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                        CountersUtils.DURATION_SECOND_COUNTER_NAME,
                        big(nodeConnectorUpdate.getDuration().getSecond().getValue()));
                crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                        CountersUtils.DURATION_NANO_SECOND_COUNTER_NAME,
                        big(nodeConnectorUpdate.getDuration().getNanosecond().getValue()));
            }
        }

        return crds;
    }

    public CounterResultDataStructure getSwitchFlowCountersDirect(BigInteger dpId, Match match) {
        NodeRef nodeRef = new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.getNodeId(dpId)))).toInstance());
        GetFlowStatisticsInputBuilder gfsib = new GetFlowStatisticsInputBuilder();
        gfsib.setNode(nodeRef);
        gfsib.setMatch(match);
        gfsib.setStoreStats(false);

        Future<RpcResult<GetFlowStatisticsOutput>> rpcResultFuture =
                odlDirectStatsService.getFlowStatistics(gfsib.build());
        RpcResult<GetFlowStatisticsOutput> rpcResult = null;
        try {
            rpcResult = rpcResultFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            CounterRetrieverCounters.failed_getting_flow_counters.inc();
            LOG.warn("Unable to retrieve flow counters for match {}", match);
            return null;
        }

        if (rpcResult != null && rpcResult.isSuccessful() && rpcResult.getResult() != null) {
            GetFlowStatisticsOutput flowStatsOutput = rpcResult.getResult();
            return createSwitchFlowResultMapDirect(flowStatsOutput);
        } else {
            CounterRetrieverCounters.failed_getting_flow_counters.inc();
            LOG.warn("Unable to retrieve flow counters for match {}", match);
            return null;
        }
    }

    private CounterResultDataStructure createSwitchFlowResultMapDirect(GetFlowStatisticsOutput flowStatsOutput) {
        List<FlowAndStatisticsMapList> flowUpdates = flowStatsOutput.getFlowAndStatisticsMapList();
        if (flowUpdates == null || flowUpdates.isEmpty()) {
            LOG.warn("Unable to retrieve flows statistics info");
            return null;
        }

        CounterResultDataStructure crds = new CounterResultDataStructure();
        for (FlowAndStatisticsMapList flowUpdate : flowUpdates) {
            String resultId = flowUpdate.getTableId().toString() + CountersUtils.OF_DELIMITER + UUID.randomUUID();
            crds.addCounterResult(resultId);
            if (flowUpdate.getByteCount() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.BYTES_GROUP_NAME, CountersUtils.BYTE_COUNTER_NAME,
                        flowUpdate.getByteCount().getValue());
            }
            if (flowUpdate.getPacketCount() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.PACKETS_GROUP_NAME, CountersUtils.PACKET_COUNTER_NAME,
                        flowUpdate.getPacketCount().getValue());
            }
            if (flowUpdate.getDuration() != null) {
                crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                        CountersUtils.DURATION_SECOND_COUNTER_NAME,
                        big(flowUpdate.getDuration().getSecond().getValue()));
                crds.addCounterToGroup(resultId, CountersUtils.DURATION_GROUP_NAME,
                        CountersUtils.DURATION_NANO_SECOND_COUNTER_NAME,
                        big(flowUpdate.getDuration().getNanosecond().getValue()));
            }
        }
        return crds;
    }

    private BigInteger big(Long longValue) {
        return BigInteger.valueOf(longValue);
    }

    enum CounterRetrieverCounters {
        failed_getting_node_connector_counters, //
        failed_getting_rpc_result_for_node_connector_counters, //
        failed_getting_node_counters, //
        failed_getting_result_map_for_node_connector, //
        failed_getting_flow_counters, //
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
