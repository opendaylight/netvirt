/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.opendaylight.infrautils.counters.api.OccurenceCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class NodeConnectorStatisticsSupplier implements Supplier<NodeConnectorStatisticsSupplierOutput> {

    private final OpendaylightDirectStatisticsService odlDirectStatsService;
    private GetNodeConnectorStatisticsInput gncsi;
    private NodeConnectorId nodeConnectorId;

    public NodeConnectorStatisticsSupplier(OpendaylightDirectStatisticsService odlDirectStatsService,
            GetNodeConnectorStatisticsInput gncsi, NodeConnectorId nodeConnectorId) {
        this.odlDirectStatsService = odlDirectStatsService;
        this.gncsi = gncsi;
        this.nodeConnectorId = nodeConnectorId;
    }

    @Override
    public NodeConnectorStatisticsSupplierOutput get() {
        Future<RpcResult<GetNodeConnectorStatisticsOutput>> rpcResultFuture =
                odlDirectStatsService.getNodeConnectorStatistics(gncsi);
        RpcResult<GetNodeConnectorStatisticsOutput> rpcResult = null;
        try {
            rpcResult = rpcResultFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            RpcSupplierCounters.failed_getting_node_connector_counters.inc();
            return null;
        }
        return new NodeConnectorStatisticsSupplierOutput(rpcResult, nodeConnectorId);
    }

    enum RpcSupplierCounters {
        failed_getting_node_connector_counters, //
        ;
        private OccurenceCounter counter;

        RpcSupplierCounters() {
            counter = new OccurenceCounter(getClass().getEnclosingClass().getSimpleName(), name(), "");
        }

        public void inc() {
            counter.inc();
        }
    }

}
