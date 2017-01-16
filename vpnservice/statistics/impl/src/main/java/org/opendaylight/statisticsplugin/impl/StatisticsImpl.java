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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.infrautils.counters.api.OccurenceCounter;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.StatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.CounterResult;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.CounterResultBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.Groups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.GroupsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.groups.Counters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.groups.CountersBuilder;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsImpl implements StatisticsService {
    private static final Logger LOG = LoggerFactory.getLogger(StatisticsImpl.class);
    private final DataBroker db;
    private final CounterRetriever counterRetriever;

    public StatisticsImpl(DataBroker db, CounterRetriever counterRetriever) {
        this.db = db;
        this.counterRetriever = counterRetriever;
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public Future<RpcResult<GetNodeCountersOutput>> getNodeCounters(GetNodeCountersInput input) {
        BigInteger dpId = input.getNodeId();
        LOG.trace("getting node counters for node {}", dpId);
        GetNodeCountersOutputBuilder gncob = new GetNodeCountersOutputBuilder();

        List<CounterResult> counterResults = new ArrayList<>();
        try {
            if (!getNodeResult(counterResults, dpId)) {
                StatisticsPluginImplCounters.failed_getting_node_counters.inc();
                return RpcResultBuilder.<GetNodeCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get node counters for node: " + dpId)
                        .buildFuture();
            }
        } catch (Exception e) {
            LOG.warn("failed to get counter result for node " + dpId, e);
            return RpcResultBuilder.<GetNodeCountersOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get node counters for node: " + dpId).buildFuture();
        }

        gncob.setCounterResult(counterResults);
        return RpcResultBuilder.success(gncob.build()).buildFuture();
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public Future<RpcResult<GetNodeAggregatedCountersOutput>> getNodeAggregatedCounters(
            GetNodeAggregatedCountersInput input) {
        BigInteger dpId = input.getNodeId();
        LOG.trace("getting aggregated node counters for node {}", dpId);
        GetNodeAggregatedCountersOutputBuilder gnacob = new GetNodeAggregatedCountersOutputBuilder();

        List<CounterResult> aggregatedCounterResults = new ArrayList<>();
        try {
            if (!getNodeAggregatedResult(aggregatedCounterResults, dpId)) {
                StatisticsPluginImplCounters.failed_getting_aggregated_node_counters.inc();
                return RpcResultBuilder.<GetNodeAggregatedCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get node aggregated counters for node " + dpId)
                        .buildFuture();
            }
        } catch (Exception e) {
            LOG.warn("failed to get counter result for node " + dpId, e);
            return RpcResultBuilder.<GetNodeAggregatedCountersOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get node aggregated counters for node " + dpId)
                    .buildFuture();
        }

        gnacob.setCounterResult(aggregatedCounterResults);
        return RpcResultBuilder.success(gnacob.build()).buildFuture();
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public Future<RpcResult<GetNodeConnectorCountersOutput>> getNodeConnectorCounters(
            GetNodeConnectorCountersInput input) {
        String portId = input.getPortId();
        LOG.trace("getting port counters of port {}", portId);

        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(db, portId);
        if (interfaceState == null) {
            LOG.warn("trying to get counters for non exist port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }

        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(interfaceState);
        if (interfaceState.getLowerLayerIf() == null || interfaceState.getLowerLayerIf().size() == 0) {
            LOG.warn("Lower layer if wasn't found for port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }

        String portNumber = interfaceState.getLowerLayerIf().get(0);
        portNumber = portNumber.split(":")[2];
        List<CounterResult> counterResults = new ArrayList<>();

        try {
            if (!getNodeConnectorResult(counterResults, dpId, portNumber)) {
                StatisticsPluginImplCounters.failed_getting_node_connector_counters.inc();
                return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get port counters").buildFuture();
            }
        } catch (Exception e) {
            LOG.warn("failed to get counter result for port " + portId, e);
        }

        GetNodeConnectorCountersOutputBuilder gpcob = new GetNodeConnectorCountersOutputBuilder();
        gpcob.setCounterResult(counterResults);
        return RpcResultBuilder.success(gpcob.build()).buildFuture();
    }

    private boolean getNodeConnectorResult(List<CounterResult> counters, BigInteger dpId, String portNumber)
            throws InterruptedException, ExecutionException {
        CounterResultDataStructure counterResultDS = counterRetriever.getNodeConnectorCounters(dpId, portNumber);
        if (counterResultDS == null) {
            return false;
        }

        CounterResultBuilder crb = new CounterResultBuilder();
        String resultId = CountersUtils.getNodeConnectorId(dpId, portNumber);
        crb.setId(resultId);

        createGroups(counters, counterResultDS, crb, resultId);

        return !counters.isEmpty();
    }

    private boolean getNodeResult(List<CounterResult> counters, BigInteger dpId)
            throws InterruptedException, ExecutionException {
        CounterResultDataStructure counterResultDS = counterRetriever.getNodeCounters(dpId);
        if (counterResultDS == null) {
            return false;
        }

        createCounterResults(counters, counterResultDS);

        return !counters.isEmpty();
    }

    private boolean getNodeAggregatedResult(List<CounterResult> aggregatedCounters, BigInteger dpId) {
        CounterResultDataStructure counters = counterRetriever.getNodeCounters(dpId);
        if (counters == null || counters.isEmpty()) {
            return false;
        }

        CounterResultDataStructure aggregatedResultsDS =
                CountersUtils.aggregateCounters(counters, CountersUtils.getNodeId(dpId));
        createCounterResults(aggregatedCounters, aggregatedResultsDS);
        return !aggregatedCounters.isEmpty();

    }

    private void createCounterResults(List<CounterResult> counters, CounterResultDataStructure counterResultDS) {
        for (String nodeConnectorId : counterResultDS.getResults().keySet()) {
            CounterResultBuilder crb = new CounterResultBuilder();
            crb.setId(nodeConnectorId);
            createGroups(counters, counterResultDS, crb, nodeConnectorId);
        }
    }

    private void createGroups(List<CounterResult> counters, CounterResultDataStructure counterResultDS,
            CounterResultBuilder crb, String resultId) {
        List<Groups> groups = new ArrayList<>();
        Map<String, Map<String, BigInteger>> counterGroups = counterResultDS.getGroups(resultId);
        if (counterGroups != null && !counterGroups.isEmpty()) {
            for (String groupName : counterGroups.keySet()) {
                groups.add(createGroupsResult(groupName, counterGroups.get(groupName)));
            }
            crb.setGroups(groups);
            counters.add(crb.build());
        }
    }

    private Groups createGroupsResult(String groupName, Map<String, BigInteger> countersMap) {
        GroupsBuilder gb = new GroupsBuilder();
        gb.setName(groupName);

        Map<String, Counters> counters = new HashMap<>();
        List<Counters> countersList = new ArrayList<>();
        for (String counterName : countersMap.keySet()) {
            addCountersToMap(countersMap, counters, counterName);
        }
        for (Counters counter : counters.values()) {
            countersList.add(counter);
        }

        gb.setCounters(countersList);
        return gb.build();
    }

    private Counters buildCounter(String counterName, BigInteger value, Counters prevCounter) {
        BigInteger prevValue = BigInteger.ZERO;
        if (prevCounter != null) {
            prevValue = prevCounter.getValue();
        }
        CountersBuilder cb = new CountersBuilder();
        cb.setName(counterName);
        cb.setValue(value.add(prevValue));
        return cb.build();
    }

    private void addCountersToMap(Map<String, BigInteger> result, Map<String, Counters> counters, String counterName) {
        counters.put(counterName, buildCounter(counterName, result.get(counterName), counters.get(counterName)));
    }

    enum StatisticsPluginImplCounters {
        failed_getting_node_counters, //
        failed_getting_node_connector_counters, //
        failed_getting_aggregated_node_counters, //
        ;
        private OccurenceCounter counter;

        StatisticsPluginImplCounters() {
            counter = new OccurenceCounter(getClass().getEnclosingClass().getSimpleName(), name(), "");
        }

        public void inc() {
            counter.inc();
        }
    }
}
