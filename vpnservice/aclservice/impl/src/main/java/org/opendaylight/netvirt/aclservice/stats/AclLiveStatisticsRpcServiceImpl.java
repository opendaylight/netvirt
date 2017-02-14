/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.stats;

import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.AclLiveStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.AclPortStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class provides RPC service implementation for
 * {@link AclLiveStatisticsService}.
 */
public class AclLiveStatisticsRpcServiceImpl implements AclLiveStatisticsService {

    private static final Logger LOG = LoggerFactory.getLogger(AclLiveStatisticsRpcServiceImpl.class);

    private AclserviceConfig config;
    private DataBroker dataBroker;
    private OpendaylightDirectStatisticsService odlDirectStatsService;
    private SecurityGroupMode securityGroupMode;

    /**
     * Instantiates a new acl live statistics rpc service impl.
     *
     * @param config the config
     * @param dataBroker the data broker
     * @param odlDirectStatsService the odl direct stats service
     */
    @Inject
    public AclLiveStatisticsRpcServiceImpl(AclserviceConfig config, DataBroker dataBroker,
            OpendaylightDirectStatisticsService odlDirectStatsService) {
        this.config = config;
        this.dataBroker = dataBroker;
        this.odlDirectStatsService = odlDirectStatsService;
        this.securityGroupMode = (config == null ? SecurityGroupMode.Stateful : config.getSecurityGroupMode());

        LOG.info("AclLiveStatisticsRpcServiceImpl initialized");
    }

    @Override
    public Future<RpcResult<GetAclPortStatisticsOutput>> getAclPortStatistics(GetAclPortStatisticsInput input) {
        LOG.trace("Get ACL port statistics for input: {}", input);
        RpcResultBuilder<GetAclPortStatisticsOutput> rpcResultBuilder;

        if (this.securityGroupMode != SecurityGroupMode.Stateful) {
            rpcResultBuilder = RpcResultBuilder.failed();
            rpcResultBuilder.withError(ErrorType.APPLICATION, "operation-not-supported",
                    "Operation not supported for ACL " + this.securityGroupMode + " mode");
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        // Default direction is Both
        Direction direction = (input.getDirection() == null ? Direction.Both : input.getDirection());

        List<AclPortStats> lstAclInterfaceStats = AclLiveStatisticsHelper.getAclPortStats(direction,
                input.getInterfaceNames(), this.odlDirectStatsService, this.dataBroker);

        GetAclPortStatisticsOutputBuilder output =
                new GetAclPortStatisticsOutputBuilder().setAclPortStats(lstAclInterfaceStats);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

}
