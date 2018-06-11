/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.statistics.api.ICountersInterfaceChangeHandler;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.AcquireElementCountersRequestHandlerInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.AcquireElementCountersRequestHandlerOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.AcquireElementCountersRequestHandlerOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.CleanAllElementCounterRequestsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.CleanAllElementCounterRequestsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.EgressElementCountersRequestConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.EgressElementCountersRequestConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetElementCountersByHandlerInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetElementCountersByHandlerOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetElementCountersByHandlerOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeAggregatedCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeConnectorCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.GetNodeCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.IngressElementCountersRequestConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.IngressElementCountersRequestConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.ReleaseElementCountersRequestHandlerInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.ReleaseElementCountersRequestHandlerOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.StatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.counterrequestsconfig.CounterRequests;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.counterrequestsconfig.CounterRequestsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.counterrequestsconfig.CounterRequestsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.elementrequestdata.Filters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.elementrequestdata.filters.TcpFilter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.elementrequestdata.filters.UdpFilter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.CounterResult;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.CounterResultBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.Groups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.GroupsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.groups.Counters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.result.counterresult.groups.CountersBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsImpl implements StatisticsService, ICountersInterfaceChangeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(StatisticsImpl.class);
    private final DataBroker db;
    private final ManagedNewTransactionRunner txRunner;
    private final CounterRetriever counterRetriever;
    private final IInterfaceManager interfaceManager;
    private final IMdsalApiManager mdsalApiManager;
    private final IdManagerService idManagerService;
    private final StatisticsCounters statisticsCounters;

    public StatisticsImpl(DataBroker db, CounterRetriever counterRetriever, IInterfaceManager interfaceManager,
            IMdsalApiManager mdsalApiManager, IdManagerService idManagerService, StatisticsCounters statisticsCounters) {
        this.db = db;
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
        this.counterRetriever = counterRetriever;
        this.interfaceManager = interfaceManager;
        this.mdsalApiManager = mdsalApiManager;
        this.idManagerService = idManagerService;
        this.statisticsCounters = statisticsCounters;
        initializeCountrsConfigDataSrore();
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public ListenableFuture<RpcResult<GetNodeCountersOutput>> getNodeCounters(GetNodeCountersInput input) {
        BigInteger dpId = input.getNodeId();
        LOG.trace("getting node counters for node {}", dpId);
        GetNodeCountersOutputBuilder gncob = new GetNodeCountersOutputBuilder();

        List<CounterResult> counterResults = new ArrayList<>();
        try {
            if (!getNodeResult(counterResults, dpId)) {
                statisticsCounters.failedGettingNodeCounters();
                return RpcResultBuilder.<GetNodeCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get node counters for node: " + dpId)
                        .buildFuture();
            }
        } catch (RuntimeException e) {
            LOG.warn("failed to get counter result for node {}", dpId, e);
            return RpcResultBuilder.<GetNodeCountersOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get node counters for node: " + dpId).buildFuture();
        }

        gncob.setCounterResult(counterResults);
        return RpcResultBuilder.success(gncob.build()).buildFuture();
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public ListenableFuture<RpcResult<GetNodeAggregatedCountersOutput>> getNodeAggregatedCounters(
            GetNodeAggregatedCountersInput input) {
        BigInteger dpId = input.getNodeId();
        LOG.trace("getting aggregated node counters for node {}", dpId);
        GetNodeAggregatedCountersOutputBuilder gnacob = new GetNodeAggregatedCountersOutputBuilder();

        List<CounterResult> aggregatedCounterResults = new ArrayList<>();
        try {
            if (!getNodeAggregatedResult(aggregatedCounterResults, dpId)) {
                statisticsCounters.failedGettingAggregatedNodeCounters();
                return RpcResultBuilder.<GetNodeAggregatedCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get node aggregated counters for node " + dpId)
                        .buildFuture();
            }
        } catch (Exception e) {
            LOG.warn("failed to get counter result for node {}", dpId, e);
            return RpcResultBuilder.<GetNodeAggregatedCountersOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get node aggregated counters for node " + dpId)
                    .buildFuture();
        }

        gnacob.setCounterResult(aggregatedCounterResults);
        return RpcResultBuilder.success(gnacob.build()).buildFuture();
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public ListenableFuture<RpcResult<GetNodeConnectorCountersOutput>> getNodeConnectorCounters(
            GetNodeConnectorCountersInput input) {
        String portId = input.getPortId();
        LOG.trace("getting port counters of port {}", portId);

        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(db, portId);
        if (interfaceState == null) {
            LOG.warn("trying to get counters for non exist port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }

        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(interfaceState);
        if (interfaceState.getLowerLayerIf() == null || interfaceState.getLowerLayerIf().isEmpty()) {
            LOG.warn("Lower layer if wasn't found for port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }

        String portNumber = interfaceState.getLowerLayerIf().get(0);
        portNumber = portNumber.split(":")[2];
        List<CounterResult> counterResults = new ArrayList<>();

        try {
            if (!getNodeConnectorResult(counterResults, dpId, portNumber)) {
                statisticsCounters.failedGettingNodeConnectorCounters();
                return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get port counters").buildFuture();
            }
        } catch (RuntimeException e) {
            LOG.warn("failed to get counter result for port {}", portId, e);
        }

        GetNodeConnectorCountersOutputBuilder gpcob = new GetNodeConnectorCountersOutputBuilder();
        gpcob.setCounterResult(counterResults);
        return RpcResultBuilder.success(gpcob.build()).buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<AcquireElementCountersRequestHandlerOutput>> acquireElementCountersRequestHandler(
            AcquireElementCountersRequestHandlerInput input) {
        AcquireElementCountersRequestHandlerOutputBuilder aecrhob =
                new AcquireElementCountersRequestHandlerOutputBuilder();
        UUID randomNumber = UUID.randomUUID();
        Integer intRequestKey = allocateId(randomNumber.toString());
        if (intRequestKey == null) {
            LOG.warn("failed generating unique request identifier");
            statisticsCounters.failedGeneratingUniqueRequestId();
            return RpcResultBuilder.<AcquireElementCountersRequestHandlerOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed generating unique request identifier").buildFuture();
        }
        String requestKey = String.valueOf(intRequestKey);
        SettableFuture<RpcResult<AcquireElementCountersRequestHandlerOutput>> result = SettableFuture.create();

        ListenableFutures.addErrorLogging(
            txRunner.callWithNewReadWriteTransactionAndSubmit(transaction -> {
                if (input.getIncomingTraffic() != null) {
                    Optional<EgressElementCountersRequestConfig> eecrcOpt =
                            transaction.read(LogicalDatastoreType.CONFIGURATION,
                                    CountersServiceUtils.EECRC_IDENTIFIER).checkedGet();
                    if (!eecrcOpt.isPresent()) {
                        LOG.warn("failed creating incoming traffic counter request data container in DB");
                        statisticsCounters.failedCreatingEgressCounterDataConfig();
                        result.setFuture(RpcResultBuilder.<AcquireElementCountersRequestHandlerOutput>failed()
                                .withError(ErrorType.APPLICATION,
                                        "failed creating egress counter request data container in DB")
                                .buildFuture());
                        return;
                    }
                    if (!isIdenticalCounterRequestExist(input.getPortId(),
                            ElementCountersDirection.EGRESS.toString(),
                            input.getIncomingTraffic().getFilters(), eecrcOpt.get().getCounterRequests())) {
                        installCounterSpecificRules(input.getPortId(), getLportTag(input.getPortId()),
                                getDpn(input.getPortId()), ElementCountersDirection.EGRESS,
                                input.getIncomingTraffic().getFilters());
                    }
                    putEgressElementCounterRequestInConfig(input, ElementCountersDirection.EGRESS, transaction,
                            requestKey, CountersServiceUtils.EECRC_IDENTIFIER, eecrcOpt, randomNumber.toString());

                    aecrhob.setIncomingTrafficHandler(requestKey);

                    bindCountersServiceIfUnbound(input.getPortId(), ElementCountersDirection.EGRESS);
                }

                if (input.getOutgoingTraffic() != null) {
                    Optional<IngressElementCountersRequestConfig> iecrcOpt =
                            transaction.read(LogicalDatastoreType.CONFIGURATION,
                                    CountersServiceUtils.IECRC_IDENTIFIER).checkedGet();
                    if (!iecrcOpt.isPresent()) {
                        LOG.warn("failed creating outgoing traffc counter request data container in DB");
                        statisticsCounters.failedCreatingIngressCounterDataConfig();
                        result.setFuture(RpcResultBuilder.<AcquireElementCountersRequestHandlerOutput>failed()
                                .withError(ErrorType.APPLICATION,
                                        "failed creating ingress counter request data container in DB")
                                .buildFuture());
                        return;
                    }
                    if (!isIdenticalCounterRequestExist(input.getPortId(),
                            ElementCountersDirection.INGRESS.toString(),
                            input.getOutgoingTraffic().getFilters(), iecrcOpt.get().getCounterRequests())) {
                        installCounterSpecificRules(input.getPortId(), getLportTag(input.getPortId()),
                                getDpn(input.getPortId()), ElementCountersDirection.INGRESS,
                                input.getOutgoingTraffic().getFilters());
                    }
                    putIngressElementCounterRequestInConfig(input, ElementCountersDirection.INGRESS, transaction,
                            requestKey, CountersServiceUtils.IECRC_IDENTIFIER, iecrcOpt, randomNumber.toString());

                    aecrhob.setIncomingTrafficHandler(requestKey);

                    bindCountersServiceIfUnbound(input.getPortId(), ElementCountersDirection.INGRESS);

                    result.setFuture(RpcResultBuilder.success(aecrhob.build()).buildFuture());
                }
            }), LOG, "Error acquiring element counters");

        return result;
    }

    @Override
    public ListenableFuture<RpcResult<ReleaseElementCountersRequestHandlerOutput>> releaseElementCountersRequestHandler(
            ReleaseElementCountersRequestHandlerInput input) {
        InstanceIdentifier<CounterRequests> ingressPath =
                InstanceIdentifier.builder(IngressElementCountersRequestConfig.class)
                        .child(CounterRequests.class, new CounterRequestsKey(input.getHandler())).build();
        InstanceIdentifier<CounterRequests> egressPath =
                InstanceIdentifier.builder(EgressElementCountersRequestConfig.class)
                        .child(CounterRequests.class, new CounterRequestsKey(input.getHandler())).build();

        SettableFuture<RpcResult<ReleaseElementCountersRequestHandlerOutput>> result = SettableFuture.create();
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            Optional<IngressElementCountersRequestConfig> iecrcOpt =
                    tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.IECRC_IDENTIFIER).checkedGet();
            Optional<EgressElementCountersRequestConfig> eecrcOpt =
                    tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.EECRC_IDENTIFIER).checkedGet();
            if (!iecrcOpt.isPresent() || !eecrcOpt.isPresent()) {
                LOG.warn("Couldn't read element counters config data from DB");
                statisticsCounters.failedReadingCounterDataFromConfig();
                result.setFuture(RpcResultBuilder.<ReleaseElementCountersRequestHandlerOutput>failed()
                        .withError(ErrorType.APPLICATION, "Couldn't read element counters config data from DB")
                        .buildFuture());
                return;
            }
            Optional<CounterRequests> ingressRequestOpt =
                    tx.read(LogicalDatastoreType.CONFIGURATION, ingressPath).checkedGet();
            Optional<CounterRequests> egressRequestOpt =
                    tx.read(LogicalDatastoreType.CONFIGURATION, egressPath).checkedGet();
            if (!ingressRequestOpt.isPresent() && !egressRequestOpt.isPresent()) {
                LOG.warn("Handler does not exists");
                statisticsCounters.unknownRequestHandler();
                result.setFuture(RpcResultBuilder.<ReleaseElementCountersRequestHandlerOutput>failed()
                        .withError(ErrorType.APPLICATION, "Handler does not exists").buildFuture());
                return;
            }
            String generatedKey = null;
            if (ingressRequestOpt.isPresent()) {
                handleReleaseTransaction(tx, input, ingressPath, ingressRequestOpt,
                        iecrcOpt.get().getCounterRequests());
                generatedKey = ingressRequestOpt.get().getGeneratedUniqueId();
            }
            if (egressRequestOpt.isPresent()) {
                handleReleaseTransaction(tx, input, egressPath, egressRequestOpt, eecrcOpt.get().getCounterRequests());
                generatedKey = egressRequestOpt.get().getGeneratedUniqueId();
            }
            releaseId(generatedKey);
            result.setFuture(RpcResultBuilder.<ReleaseElementCountersRequestHandlerOutput>success().buildFuture());
        }), LOG, "Error releasing element counters");

        return result;
    }

    @Override
    public ListenableFuture<RpcResult<GetElementCountersByHandlerOutput>> getElementCountersByHandler(
            GetElementCountersByHandlerInput input) {
        InstanceIdentifier<CounterRequests> ingressPath =
                InstanceIdentifier.builder(IngressElementCountersRequestConfig.class)
                        .child(CounterRequests.class, new CounterRequestsKey(input.getHandler())).build();
        InstanceIdentifier<CounterRequests> egressPath =
                InstanceIdentifier.builder(EgressElementCountersRequestConfig.class)
                        .child(CounterRequests.class, new CounterRequestsKey(input.getHandler())).build();

        ReadOnlyTransaction tx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<CounterRequests>, ReadFailedException> ingressRequestData =
                tx.read(LogicalDatastoreType.CONFIGURATION, ingressPath);
        CheckedFuture<Optional<CounterRequests>, ReadFailedException> egressRequestData =
                tx.read(LogicalDatastoreType.CONFIGURATION, egressPath);
        List<CounterResult> counters = new ArrayList<>();

        try {
            if (!ingressRequestData.get().isPresent() && !egressRequestData.get().isPresent()) {
                LOG.warn("Handler does not exists");
                return RpcResultBuilder.<GetElementCountersByHandlerOutput>failed()
                        .withError(ErrorType.APPLICATION, "Handler does not exists").buildFuture();
            }
            if (ingressRequestData.get().isPresent()) {
                CounterRequests ingressCounterRequest = ingressRequestData.get().get();
                CounterResultDataStructure ingressCounterResultDS = createElementCountersResult(ingressCounterRequest);
                if (ingressCounterResultDS == null) {
                    LOG.warn("Unable to get counter results");
                    statisticsCounters.failedGettingCounterResults();
                    return RpcResultBuilder.<GetElementCountersByHandlerOutput>failed()
                            .withError(ErrorType.APPLICATION, "Unable to get counter results").buildFuture();
                }
                createCounterResults(counters, ingressCounterResultDS, CountersServiceUtils.INGRESS_COUNTER_RESULT_ID);
            }
            if (egressRequestData.get().isPresent()) {
                CounterRequests egressCounterRequest = egressRequestData.get().get();
                CounterResultDataStructure egressCounterResultDS = createElementCountersResult(egressCounterRequest);
                if (egressCounterResultDS == null) {
                    LOG.warn("Unable to get counter results");
                    statisticsCounters.failedGettingCounterResults();
                    return RpcResultBuilder.<GetElementCountersByHandlerOutput>failed()
                            .withError(ErrorType.APPLICATION, "Unable to get counter results").buildFuture();
                }
                createCounterResults(counters, egressCounterResultDS, CountersServiceUtils.EGRESS_COUNTER_RESULT_ID);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("failed to get counter request data from DB");
            return RpcResultBuilder.<GetElementCountersByHandlerOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get counter request data from DB").buildFuture();
        }

        GetElementCountersByHandlerOutputBuilder gecbhob = new GetElementCountersByHandlerOutputBuilder();
        gecbhob.setCounterResult(counters);
        return RpcResultBuilder.success(gecbhob.build()).buildFuture();
    }

    @Override
    public void handleInterfaceRemoval(String interfaceId) {
        CheckedFuture<Optional<IngressElementCountersRequestConfig>, ReadFailedException> iecrc;
        CheckedFuture<Optional<EgressElementCountersRequestConfig>, ReadFailedException> eecrc;
        try (ReadOnlyTransaction tx = db.newReadOnlyTransaction()) {
            iecrc = tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.IECRC_IDENTIFIER);
            eecrc = tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.EECRC_IDENTIFIER);
        }

        try {
            Optional<IngressElementCountersRequestConfig> iecrcOpt = iecrc.get();
            Optional<EgressElementCountersRequestConfig> eecrcOpt = eecrc.get();
            if (!iecrcOpt.isPresent() || !eecrcOpt.isPresent()) {
                LOG.warn("Couldn't read element counters config data from DB");
                statisticsCounters.failedReadingCounterDataFromConfig();
                return;
            }
            removeAllElementCounterRequestsOnPort(interfaceId, iecrcOpt.get().getCounterRequests());
            removeAllElementCounterRequestsOnPort(interfaceId, eecrcOpt.get().getCounterRequests());
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("failed to get counter request data from DB");
            statisticsCounters.failedGettingCounterResultsPortRemoval();
            return;
        }
    }

    @Override
    public ListenableFuture<RpcResult<CleanAllElementCounterRequestsOutput>> cleanAllElementCounterRequests(
            CleanAllElementCounterRequestsInput input) {
        ReadOnlyTransaction tx = db.newReadOnlyTransaction();
        CheckedFuture<Optional<IngressElementCountersRequestConfig>, ReadFailedException> iecrc =
                tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.IECRC_IDENTIFIER);
        CheckedFuture<Optional<EgressElementCountersRequestConfig>, ReadFailedException> eecrc =
                tx.read(LogicalDatastoreType.CONFIGURATION, CountersServiceUtils.EECRC_IDENTIFIER);
        try {
            Optional<IngressElementCountersRequestConfig> iecrcOpt = iecrc.get();
            Optional<EgressElementCountersRequestConfig> eecrcOpt = eecrc.get();
            if (!iecrcOpt.isPresent() || !eecrcOpt.isPresent()) {
                LOG.warn("Couldn't read element counters config data from DB");
                statisticsCounters.failedReadingCounterDataFromConfig();
                return RpcResultBuilder.<CleanAllElementCounterRequestsOutput>failed()
                        .withError(ErrorType.APPLICATION, "Couldn't read element counters config data from DB")
                        .buildFuture();
            }
            Set<String> idsToRelease = new HashSet<>();
            if (input.getPortId() != null && !input.getPortId().isEmpty()) {
                idsToRelease
                        .addAll(getAllPortRequestsUniqueIds(input.getPortId(), iecrcOpt.get().getCounterRequests()));
                idsToRelease
                        .addAll(getAllPortRequestsUniqueIds(input.getPortId(), eecrcOpt.get().getCounterRequests()));
                removeAllElementCounterRequestsOnPort(input.getPortId(), iecrcOpt.get().getCounterRequests());
                removeAllElementCounterRequestsOnPort(input.getPortId(), eecrcOpt.get().getCounterRequests());
            } else {
                idsToRelease.addAll(getAllRquestsUniqueIds(iecrcOpt.get().getCounterRequests()));
                idsToRelease.addAll(getAllRquestsUniqueIds(eecrcOpt.get().getCounterRequests()));
                removeAllElementCounterRequests(iecrcOpt.get().getCounterRequests());
                removeAllElementCounterRequests(eecrcOpt.get().getCounterRequests());
            }
            releaseIds(idsToRelease);
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("failed to get counter request data from DB");
            return RpcResultBuilder.<CleanAllElementCounterRequestsOutput>failed()
                    .withError(ErrorType.APPLICATION, "failed to get counter request data from DB").buildFuture();

        }
        return RpcResultBuilder.<CleanAllElementCounterRequestsOutput>success().buildFuture();
    }

    private Set<String> getAllPortRequestsUniqueIds(String interfaceId, List<CounterRequests> counterRequests) {
        Set<String> result = new HashSet<>();
        for (CounterRequests counterRequest : counterRequests) {
            if (counterRequest.getPortId().equals(interfaceId)) {
                result.add(counterRequest.getGeneratedUniqueId());
            }
        }
        return result;
    }

    private Set<String> getAllRquestsUniqueIds(List<CounterRequests> counterRequests) {
        Set<String> result = new HashSet<>();
        for (CounterRequests counterRequest : counterRequests) {
            result.add(counterRequest.getGeneratedUniqueId());
        }
        return result;
    }

    private void releaseIds(Set<String> ids) {
        for (String id : ids) {
            releaseId(id);
        }
    }

    private void removeAllElementCounterRequestsOnPort(String interfaceId, List<CounterRequests> counterRequests) {
        unbindCountersServiceIfBound(interfaceId, ElementCountersDirection.INGRESS);
        unbindCountersServiceIfBound(interfaceId, ElementCountersDirection.EGRESS);
        if (counterRequests != null) {
            for (CounterRequests counterRequest : counterRequests) {
                if (interfaceId.equals(counterRequest.getPortId())) {
                    countersRequestCleanup(counterRequest);
                }
            }
        }
    }

    private void removeAllElementCounterRequests(List<CounterRequests> counterRequests) {
        for (CounterRequests counterRequest : counterRequests) {
            unbindCountersServiceIfBound(counterRequest.getPortId(), ElementCountersDirection.INGRESS);
            unbindCountersServiceIfBound(counterRequest.getPortId(), ElementCountersDirection.EGRESS);
            countersRequestCleanup(counterRequest);
        }
    }

    private void countersRequestCleanup(CounterRequests counterRequest) {
        ElementCountersDirection direction = ElementCountersDirection.valueOf(counterRequest.getTrafficDirection());
        deleteCounterSpecificRules(counterRequest.getPortId(), counterRequest.getLportTag(), counterRequest.getDpn(),
                direction, counterRequest.getFilters());
        deleteCounterRequest(counterRequest, direction);
    }

    private void deleteCounterSpecificRules(String portId, int lportTag, BigInteger dpn,
            ElementCountersDirection direction, Filters filters) {
        List<ElementCountersRequest> ecrList = createElementCounterRequest(portId, lportTag, dpn, direction, filters);
        for (ElementCountersRequest ecr : ecrList) {
            if (ElementCountersDirection.INGRESS.equals(ecr.getDirection())) {
                IngressCountersServiceImpl icsi = new IngressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
                icsi.deleteCounterRules(ecr);
            } else if (ElementCountersDirection.EGRESS.equals(ecr.getDirection())) {
                EgressCountersServiceImpl ecsi = new EgressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
                ecsi.deleteCounterRules(ecr);
            }
        }
    }

    private void deleteCounterRequest(CounterRequests counterRequest, ElementCountersDirection direction) {
        ListenableFutures.addErrorLogging(
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                if (ElementCountersDirection.INGRESS.equals(direction)) {
                    tx.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                            .builder(IngressElementCountersRequestConfig.class)
                            .child(CounterRequests.class,
                                    new CounterRequestsKey(counterRequest.key().getRequestId()))
                            .build());
                } else if (ElementCountersDirection.EGRESS.equals(direction)) {
                    tx.delete(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                            .builder(EgressElementCountersRequestConfig.class)
                            .child(CounterRequests.class,
                                    new CounterRequestsKey(counterRequest.key().getRequestId()))
                            .build());
                }
            }), LOG, "Error deleting counter");
    }

    private CounterResultDataStructure createElementCountersResult(CounterRequests counterRequest) {
        ElementCountersRequest ecr =
                createElementCounterRequest(counterRequest.getPortId(), counterRequest.getLportTag(),
                        counterRequest.getDpn(), ElementCountersDirection.valueOf(counterRequest.getTrafficDirection()),
                        counterRequest.getFilters()).iterator().next();
        BigInteger dpId = getDpn(ecr.getPortId());
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(ecr.getPortId());
        if (interfaceInfo == null) {
            return null;
        }
        int lportTag = interfaceInfo.getInterfaceTag();
        List<MatchInfoBase> matches = CountersServiceUtils.getCounterFlowMatch(ecr, lportTag,
                ElementCountersDirection.valueOf(counterRequest.getTrafficDirection()));
        Match match = MDSALUtil.buildMatches(matches);
        return counterRetriever.getSwitchFlowCountersDirect(dpId, match);
    }

    private void initializeCountrsConfigDataSrore() {
        ListenableFutures.addErrorLogging(
            txRunner.callWithNewReadWriteTransactionAndSubmit(transaction -> {
                Optional<IngressElementCountersRequestConfig> iecrcOpt =
                        transaction.read(LogicalDatastoreType.CONFIGURATION,
                                CountersServiceUtils.IECRC_IDENTIFIER).checkedGet();
                Optional<EgressElementCountersRequestConfig> eecrcOpt =
                        transaction.read(LogicalDatastoreType.CONFIGURATION,
                                CountersServiceUtils.EECRC_IDENTIFIER).checkedGet();
                if (!iecrcOpt.isPresent()) {
                    creatIngressEelementCountersContainerInConfig(transaction,
                            CountersServiceUtils.IECRC_IDENTIFIER);
                }
                if (!eecrcOpt.isPresent()) {
                    creatEgressEelementCountersContainerInConfig(transaction,
                            CountersServiceUtils.EECRC_IDENTIFIER);
                }
            }), LOG, "Failed to create counters in config datastore");
    }

    private void handleReleaseTransaction(WriteTransaction transaction, ReleaseElementCountersRequestHandlerInput input,
            InstanceIdentifier<CounterRequests> path, Optional<CounterRequests> requestData,
            List<CounterRequests> counterRequests) {
        transaction.delete(LogicalDatastoreType.CONFIGURATION, path);
        CounterRequests counterRequest = requestData.get();
        if (shouldUnbindCountersService(counterRequest.getPortId(), counterRequest.key().getRequestId(),
                counterRequests)) {
            unbindCountersServiceIfBound(counterRequest.getPortId(),
                    ElementCountersDirection.valueOf(counterRequest.getTrafficDirection()));
        }
        if (!isIdenticalCounterRequestExist(input.getHandler(), counterRequest.getPortId(),
                counterRequest.getTrafficDirection(), counterRequest.getFilters(), counterRequests)) {
            deleteCounterSpecificRules(counterRequest.getPortId(), counterRequest.getLportTag(),
                    counterRequest.getDpn(), ElementCountersDirection.valueOf(counterRequest.getTrafficDirection()),
                    counterRequest.getFilters());
        }
    }

    private boolean getNodeConnectorResult(List<CounterResult> counters, BigInteger dpId, String portNumber) {
        CounterResultDataStructure counterResultDS =
                counterRetriever.getNodeConnectorCountersDirect(new NodeId(CountersUtils.getNodeId(dpId)),
                        new NodeConnectorId(CountersUtils.getNodeConnectorId(dpId, portNumber)));
        if (counterResultDS == null) {
            return false;
        }

        CounterResultBuilder crb = new CounterResultBuilder();
        String resultId = CountersUtils.getNodeConnectorId(dpId, portNumber);
        crb.setId(resultId);

        createGroups(counters, counterResultDS, crb, resultId);

        return !counters.isEmpty();
    }

    private boolean getNodeResult(List<CounterResult> counters, BigInteger dpId) {
        InstanceIdentifier<Node> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.getNodeId(dpId)))).build();
        Optional<Node> nodeOptional = MDSALUtil.read(db, LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier);
        if (!nodeOptional.isPresent()) {
            return false;
        }

        Node node = nodeOptional.get();
        CounterResultDataStructure counterResultDS = counterRetriever.getNodeCountersDirect(node);
        if (counterResultDS == null) {
            return false;
        }

        createCounterResults(counters, counterResultDS);

        return !counters.isEmpty();
    }

    private boolean getNodeAggregatedResult(List<CounterResult> aggregatedCounters, BigInteger dpId) {
        InstanceIdentifier<Node> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.getNodeId(dpId)))).build();
        Optional<Node> nodeOptional = MDSALUtil.read(db, LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier);
        if (!nodeOptional.isPresent()) {
            return false;
        }

        Node node = nodeOptional.get();
        CounterResultDataStructure counters = counterRetriever.getNodeCountersDirect(node);
        if (counters == null || counters.isEmpty()) {
            return false;
        }

        CounterResultDataStructure aggregatedResultsDS =
                CountersUtils.aggregateCounters(counters, CountersUtils.getNodeId(dpId));
        createCounterResults(aggregatedCounters, aggregatedResultsDS);
        return !aggregatedCounters.isEmpty();

    }

    private void createCounterResults(List<CounterResult> counters, CounterResultDataStructure counterResultDS) {
        for (String counterResultId : counterResultDS.getResults().keySet()) {
            CounterResultBuilder crb = new CounterResultBuilder();
            crb.setId(counterResultId);
            createGroups(counters, counterResultDS, crb, counterResultId);
        }
    }

    private void createCounterResults(List<CounterResult> counters, CounterResultDataStructure counterResultDS,
            String resultId) {
        for (String counterResultId : counterResultDS.getResults().keySet()) {
            CounterResultBuilder crb = new CounterResultBuilder();
            crb.setId(resultId);
            createGroups(counters, counterResultDS, crb, counterResultId);
        }
    }

    private void createGroups(List<CounterResult> counters, CounterResultDataStructure counterResultDS,
            CounterResultBuilder crb, String resultId) {
        List<Groups> groups = new ArrayList<>();
        Map<String, Map<String, BigInteger>> counterGroups = counterResultDS.getGroups(resultId);
        if (counterGroups != null && !counterGroups.isEmpty()) {
            for (Entry<String, Map<String, BigInteger>> entry : counterGroups.entrySet()) {
                String groupName = entry.getKey();
                groups.add(createGroupsResult(groupName, entry.getValue()));
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

    private void addElementCounterRequest(List<ElementCountersRequest> ecrList, String portId, int lportTag,
            BigInteger dpn, ElementCountersDirection direction, Filters filters) {
        ElementCountersRequest ecr = new ElementCountersRequest(portId);
        ecr.setLportTag(lportTag);
        ecr.setDpn(dpn);
        ecr.setElementCountersDirection(direction);
        if (filters.getIpFilter() != null) {
            String ip = filters.getIpFilter().getIp();
            if (ip != null) {
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_IP_FILTER_GROUP_NAME,
                        CountersUtils.IP_FILTER_NAME, ip);
            }
        }

        boolean isTcpPortExist = false;
        if (filters.getTcpFilter() != null && filters.getTcpFilter().isOn()) {
            TcpFilter tcpFilter = filters.getTcpFilter();
            int srcPort = tcpFilter.getSrcPort();
            int dstPort = tcpFilter.getDstPort();
            if (srcPort != -1) {
                isTcpPortExist = true;
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                        CountersUtils.TCP_SRC_PORT_FILTER_NAME, String.valueOf(srcPort));
            }
            if (dstPort != -1) {
                isTcpPortExist = true;
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                        CountersUtils.TCP_DST_PORT_FILTER_NAME, String.valueOf(dstPort));
            }
            if (!isTcpPortExist) {
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                        CountersUtils.TCP_FILTER_NAME, "");
            }

        } else if (filters.getUdpFilter() != null && filters.getUdpFilter().isOn()) {
            UdpFilter udpFilter = filters.getUdpFilter();
            int srcPort = udpFilter.getSrcPort();
            int dstPort = udpFilter.getDstPort();
            if (srcPort != -1) {
                isTcpPortExist = true;
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                        CountersUtils.UDP_SRC_PORT_FILTER_NAME, String.valueOf(srcPort));
            }
            if (dstPort != -1) {
                isTcpPortExist = true;
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                        CountersUtils.UDP_DST_PORT_FILTER_NAME, String.valueOf(dstPort));
            }
            if (!isTcpPortExist) {
                ecr.addFilterToFilterGroup(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                        CountersUtils.UDP_FILTER_NAME, "");
            }
        }

        if (!ecr.getFilters().isEmpty()) {
            ecrList.add(ecr);
        }
    }

    @SuppressFBWarnings("SLF4J_FORMAT_SHOULD_BE_CONST")
    private void logElementCounterRequests(List<ElementCountersRequest> ecrList) {
        for (ElementCountersRequest counterRequest : ecrList) {
            LOG.debug(counterRequest.toString());
        }
    }

    private void bindCountersServiceIfUnbound(String interfaceId, ElementCountersDirection direction) {
        if (ElementCountersDirection.INGRESS.equals(direction) && !interfaceManager.isServiceBoundOnInterfaceForIngress(
                CountersServiceUtils.INGRESS_COUNTERS_SERVICE_INDEX, interfaceId)) {
            IngressCountersServiceImpl icsi = new IngressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
            icsi.bindService(interfaceId);
            statisticsCounters.ingressCountersServiceBind();
        } else if (ElementCountersDirection.EGRESS.equals(direction) && !interfaceManager
                .isServiceBoundOnInterfaceForEgress(CountersServiceUtils.EGRESS_COUNTERS_SERVICE_INDEX, interfaceId)) {
            EgressCountersServiceImpl ecsi = new EgressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
            ecsi.bindService(interfaceId);
            statisticsCounters.egressCountersServiceBind();
        }
    }

    private void unbindCountersServiceIfBound(String interfaceId, ElementCountersDirection direction) {
        if (ElementCountersDirection.INGRESS.equals(direction) && interfaceManager.isServiceBoundOnInterfaceForIngress(
                CountersServiceUtils.INGRESS_COUNTERS_SERVICE_INDEX, interfaceId)) {
            IngressCountersServiceImpl icsi = new IngressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
            icsi.unBindService(interfaceId);
            statisticsCounters.ingressCountersServiceUnbind();
        } else if (ElementCountersDirection.EGRESS.equals(direction) && interfaceManager
                .isServiceBoundOnInterfaceForEgress(CountersServiceUtils.EGRESS_COUNTERS_SERVICE_INDEX, interfaceId)) {
            EgressCountersServiceImpl ecsi = new EgressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
            ecsi.unBindService(interfaceId);
            statisticsCounters.egressCountersServiceUnbind();
        }
    }

    private boolean shouldUnbindCountersService(String portId, String requesId, List<CounterRequests> counterRequests) {
        for (CounterRequests counterRequest : counterRequests) {
            if (portId.equals(counterRequest.getPortId()) && !requesId.equals(counterRequest.key().getRequestId())) {
                return false;
            }
        }
        return true;
    }

    private void installCounterSpecificRules(String portId, int lportTag, BigInteger dpn,
            ElementCountersDirection direction, Filters filters) {
        List<ElementCountersRequest> ecrList = createElementCounterRequest(portId, lportTag, dpn, direction, filters);
        for (ElementCountersRequest ecr : ecrList) {
            if (ElementCountersDirection.INGRESS.equals(ecr.getDirection())) {
                IngressCountersServiceImpl icsi = new IngressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
                icsi.installCounterRules(ecr);
            } else if (ElementCountersDirection.EGRESS.equals(ecr.getDirection())) {
                EgressCountersServiceImpl ecsi = new EgressCountersServiceImpl(db, interfaceManager, mdsalApiManager);
                ecsi.installCounterRules(ecr);
            }
        }
    }

    private boolean areFiltersEqual(Filters filterGroup1, Filters filterGroup2) {
        if (filterGroup1 == null && filterGroup2 == null) {
            return true;
        }
        if (filterGroup1 == null || filterGroup2 == null) {
            return false;
        }

        return filterGroup1.toString().equals(filterGroup2.toString());
    }

    private void putIngressElementCounterRequestInConfig(AcquireElementCountersRequestHandlerInput input,
            ElementCountersDirection direcion, ReadWriteTransaction transaction, String requestKey,
            InstanceIdentifier<IngressElementCountersRequestConfig> ecrcIdentifier,
            Optional<IngressElementCountersRequestConfig> iecrcOpt, String generatedUniqueId) {
        IngressElementCountersRequestConfig requestConfig = iecrcOpt.get();
        CounterRequestsBuilder crb = new CounterRequestsBuilder();
        crb.setRequestId(requestKey);
        crb.withKey(new CounterRequestsKey(requestKey));
        crb.setFilters(input.getOutgoingTraffic().getFilters());
        crb.setPortId(input.getPortId());
        crb.setLportTag(getLportTag(input.getPortId()));
        crb.setDpn(getDpn(input.getPortId()));
        crb.setTrafficDirection(direcion.toString());
        crb.setGeneratedUniqueId(generatedUniqueId);
        List<CounterRequests> counterRequests = requestConfig.getCounterRequests();
        counterRequests.add(crb.build());

        IngressElementCountersRequestConfigBuilder ecrcb = new IngressElementCountersRequestConfigBuilder();
        ecrcb.setCounterRequests(counterRequests);
        requestConfig = ecrcb.build();
        transaction.put(LogicalDatastoreType.CONFIGURATION, ecrcIdentifier, requestConfig,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    private void putEgressElementCounterRequestInConfig(AcquireElementCountersRequestHandlerInput input,
            ElementCountersDirection direcion, ReadWriteTransaction transaction, String requestKey,
            InstanceIdentifier<EgressElementCountersRequestConfig> ecrcIdentifier,
            Optional<EgressElementCountersRequestConfig> eecrcOpt, String generatedUniqueId) {
        EgressElementCountersRequestConfig requestConfig = eecrcOpt.get();
        CounterRequestsBuilder crb = new CounterRequestsBuilder();
        crb.setRequestId(requestKey);
        crb.withKey(new CounterRequestsKey(requestKey));
        crb.setFilters(input.getIncomingTraffic().getFilters());
        crb.setPortId(input.getPortId());
        crb.setLportTag(getLportTag(input.getPortId()));
        crb.setDpn(getDpn(input.getPortId()));
        crb.setTrafficDirection(direcion.toString());
        crb.setGeneratedUniqueId(generatedUniqueId);
        List<CounterRequests> counterRequests = requestConfig.getCounterRequests();
        counterRequests.add(crb.build());

        EgressElementCountersRequestConfigBuilder ecrcb = new EgressElementCountersRequestConfigBuilder();
        ecrcb.setCounterRequests(counterRequests);
        requestConfig = ecrcb.build();
        transaction.put(LogicalDatastoreType.CONFIGURATION, ecrcIdentifier, requestConfig,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    private void creatIngressEelementCountersContainerInConfig(ReadWriteTransaction transaction,
            InstanceIdentifier<IngressElementCountersRequestConfig> ecrcIdentifier) {
        IngressElementCountersRequestConfigBuilder iecrcb = new IngressElementCountersRequestConfigBuilder();
        List<CounterRequests> counterRequests = new ArrayList<>();
        iecrcb.setCounterRequests(counterRequests);
        IngressElementCountersRequestConfig iecrc = iecrcb.build();
        transaction.put(LogicalDatastoreType.CONFIGURATION, ecrcIdentifier, iecrc,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    private void creatEgressEelementCountersContainerInConfig(ReadWriteTransaction transaction,
            InstanceIdentifier<EgressElementCountersRequestConfig> ecrcIdentifier) {
        EgressElementCountersRequestConfigBuilder eecrcb = new EgressElementCountersRequestConfigBuilder();
        List<CounterRequests> counterRequests = new ArrayList<>();
        eecrcb.setCounterRequests(counterRequests);
        EgressElementCountersRequestConfig eecrc = eecrcb.build();
        transaction.put(LogicalDatastoreType.CONFIGURATION, ecrcIdentifier, eecrc,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    private Integer allocateId(String idKey) {
        createIdPool();
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(CountersServiceUtils.COUNTERS_PULL_NAME)
                .setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManagerService.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id", e);
        }
        return null;
    }

    private void createIdPool() {
        if (checkPoolExists()) {
            return;
        }
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
                .setPoolName(CountersServiceUtils.COUNTERS_PULL_NAME).setLow(CountersServiceUtils.COUNTERS_PULL_START)
                .setHigh(CountersServiceUtils.COUNTERS_PULL_START + CountersServiceUtils.COUNTERS_PULL_END).build();
        ListenableFuture<RpcResult<CreateIdPoolOutput>> result = idManagerService.createIdPool(createPool);
        Futures.addCallback(result, new FutureCallback<RpcResult<CreateIdPoolOutput>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Failed to create idPool for Aliveness Monitor Service", error);
            }

            @Override
            public void onSuccess(@Nonnull RpcResult<CreateIdPoolOutput> rpcResult) {
                if (rpcResult.isSuccessful()) {
                    LOG.debug("Created IdPool for tap");
                } else {
                    LOG.error("RPC to create Idpool failed {}", rpcResult.getErrors());
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private void releaseId(String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(CountersServiceUtils.COUNTERS_PULL_NAME)
                .setIdKey(idKey).build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManagerService.releaseId(idInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to release Id with Key {} returned with Errors {}", idKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when releasing Id for key {}", idKey, e);
        }
    }

    private boolean checkPoolExists() {
        ReadOnlyTransaction roTransaction = db.newReadOnlyTransaction();
        InstanceIdentifier<IdPool> path = InstanceIdentifier.create(IdPools.class).child(IdPool.class,
                new IdPoolKey(CountersServiceUtils.COUNTERS_PULL_NAME));
        CheckedFuture<Optional<IdPool>, ReadFailedException> pool =
                roTransaction.read(LogicalDatastoreType.CONFIGURATION, path);
        try {
            Optional<IdPool> poolOpt = pool.get();
            if (poolOpt.isPresent()) {
                return true;
            }
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
        return false;
    }

    private boolean isIdenticalCounterRequestExist(String portId, String dirction, Filters filters,
            List<CounterRequests> counterRequests) {
        if (counterRequests.isEmpty()) {
            return false;
        }

        for (CounterRequests counterRequest : counterRequests) {
            if (portId.equals(counterRequest.getPortId()) && dirction.equals(counterRequest.getTrafficDirection())) {
                if (areFiltersEqual(filters, counterRequest.getFilters())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isIdenticalCounterRequestExist(String requesId, String portId, String dirction, Filters filters,
            List<CounterRequests> counterRequests) {
        if (counterRequests.isEmpty()) {
            return false;
        }

        for (CounterRequests counterRequest : counterRequests) {
            if (portId.equals(counterRequest.getPortId()) && dirction.equals(counterRequest.getTrafficDirection())
                    && !counterRequest.key().getRequestId().equals(requesId)) {
                if (areFiltersEqual(filters, counterRequest.getFilters())) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getLportTag(String interfaceId) {
        return interfaceManager.getInterfaceInfo(interfaceId).getInterfaceTag();
    }

    private BigInteger getDpn(String interfaceId) {
        return interfaceManager.getDpnForInterface(interfaceId);
    }

    private List<ElementCountersRequest> createElementCounterRequest(String portId, int lportTag, BigInteger dpn,
            ElementCountersDirection direcrtion, Filters filters) {
        List<ElementCountersRequest> ecrList = new ArrayList<>();
        LOG.debug("getting element counters for port {}", portId);
        addElementCounterRequest(ecrList, portId, lportTag, dpn, direcrtion, filters);

        logElementCounterRequests(ecrList);

        return ecrList;
    }
}
