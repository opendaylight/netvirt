/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.stats;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Counter64;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetFlowStatisticsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetGroupStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetGroupStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetMeterStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetMeterStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetNodeConnectorStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetQueueStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.GetQueueStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.flow.and.statistics.map.list.FlowAndStatisticsMapListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TestOdlDirectStatisticsService implements OpendaylightDirectStatisticsService {

    private static final Logger LOG = LoggerFactory.getLogger(TestOdlDirectStatisticsService.class);

    protected FlowCookie aclDropFlowCookieMask = new FlowCookie(AclLiveStatisticsHelper.COOKIE_ACL_DROP_FLOW_MASK);

    @Override
    public ListenableFuture<RpcResult<GetNodeConnectorStatisticsOutput>> getNodeConnectorStatistics(
            GetNodeConnectorStatisticsInput input) {
        return RpcResultBuilder.<GetNodeConnectorStatisticsOutput>success().buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<GetQueueStatisticsOutput>> getQueueStatistics(GetQueueStatisticsInput input) {
        return RpcResultBuilder.<GetQueueStatisticsOutput>success().buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<GetGroupStatisticsOutput>> getGroupStatistics(GetGroupStatisticsInput input) {
        return RpcResultBuilder.<GetGroupStatisticsOutput>success().buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<GetFlowStatisticsOutput>> getFlowStatistics(GetFlowStatisticsInput input) {
        LOG.info("getFlowStatistics rpc input = {}", input);

        List<FlowAndStatisticsMapList> flowStatsList = new ArrayList<>();
        FlowAndStatisticsMapList portIngressFlowStats1 =
                buildFlowStats(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                        AclConstants.ACL_PORT_SPECIFIC_DROP_PRIORITY, 1, 5, 5);
        FlowAndStatisticsMapList portIngressFlowStats2 =
                buildFlowStats(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                        AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, 1, 10, 10);

        FlowAndStatisticsMapList portEgressFlowStats1 =
                buildFlowStats(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                        AclConstants.ACL_PORT_SPECIFIC_DROP_PRIORITY, 1, 15, 15);
        FlowAndStatisticsMapList portEgressFlowStats2 =
                buildFlowStats(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                        AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, 1, 20, 20);

        if (input.getTableId() == null
                || input.getTableId().shortValue() == NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE) {
            flowStatsList.add(portIngressFlowStats1);
            flowStatsList.add(portIngressFlowStats2);
        }
        if (input.getTableId() == null
                || input.getTableId().shortValue() == NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE) {
            flowStatsList.add(portEgressFlowStats1);
            flowStatsList.add(portEgressFlowStats2);
        }

        GetFlowStatisticsOutput output =
                new GetFlowStatisticsOutputBuilder().setFlowAndStatisticsMapList(flowStatsList).build();

        RpcResultBuilder<GetFlowStatisticsOutput> rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output);
        return rpcResultBuilder.buildFuture();
    }

    private FlowAndStatisticsMapList buildFlowStats(short tableId, Integer priority, Integer lportTag, long byteCount,
            long packetCount) {
        Match metadataMatch = AclLiveStatisticsHelper.buildMetadataMatch(lportTag);
        FlowCookie aclDropFlowCookie = new FlowCookie(AclServiceUtils.getDropFlowCookie(lportTag));

        return new FlowAndStatisticsMapListBuilder().setTableId(tableId).setCookie(aclDropFlowCookie)
                .setCookieMask(aclDropFlowCookieMask).setMatch(metadataMatch).setPriority(priority)
                .setByteCount(new Counter64(BigInteger.valueOf(byteCount)))
                .setPacketCount(new Counter64(BigInteger.valueOf(packetCount))).build();
    }

    @Override
    public ListenableFuture<RpcResult<GetMeterStatisticsOutput>> getMeterStatistics(GetMeterStatisticsInput input) {
        return RpcResultBuilder.<GetMeterStatisticsOutput>success().buildFuture();
    }

}
