/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.statisitcs;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius._interface.statistics.rev150824.ResultCode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.ElanStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.GetElanInterfaceStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.GetElanInterfaceStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.GetElanInterfaceStatisticsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.get.elan._interface.statistics.output.StatResultBuilder;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanStatisticsImpl implements ElanStatisticsService {
    private static final Logger LOG = LoggerFactory.getLogger(ElanStatisticsImpl.class);

    private final IInterfaceManager interfaceManager;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanStatisticsImpl(IInterfaceManager interfaceManager,
            ElanInterfaceCache elanInterfaceCache) {
        this.interfaceManager = interfaceManager;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    @Override
    public ListenableFuture<RpcResult<GetElanInterfaceStatisticsOutput>> getElanInterfaceStatistics(
        GetElanInterfaceStatisticsInput input) {
        String interfaceName = input.getInterfaceName();
        LOG.debug("getElanInterfaceStatistics is called for elan interface {}", interfaceName);
        RpcResultBuilder<GetElanInterfaceStatisticsOutput> rpcResultBuilder = null;
        if (interfaceName == null) {
            rpcResultBuilder = RpcResultBuilder.failed();
            return getFutureWithAppErrorMessage(rpcResultBuilder, "Interface name is not provided");
        }
        Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
        if (!elanInterface.isPresent()) {
            rpcResultBuilder = RpcResultBuilder.failed();
            return getFutureWithAppErrorMessage(rpcResultBuilder,
                    String.format("Interface %s is not a ELAN interface", interfaceName));
        }
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (!interfaceInfo.isOperational()) {
            LOG.debug("interface {} is down and returning with no statistics", interfaceName);
            rpcResultBuilder = RpcResultBuilder.success();
            return rpcResultBuilder.withResult(new GetElanInterfaceStatisticsOutputBuilder()
                            .setStatResult(
                                    new StatResultBuilder().setStatResultCode(ResultCode.NotFound).setByteRxCount(0L)
                                            .setByteTxCount(0L).setPacketRxCount(0L).setPacketTxCount(0L).build())
                            .build()).buildFuture();
        }
        rpcResultBuilder = RpcResultBuilder.success();
        return Futures.immediateFuture(rpcResultBuilder.withResult(queryforElanInterfaceStatistics()).build());
    }

    @Nullable
    private static GetElanInterfaceStatisticsOutput queryforElanInterfaceStatistics() {
        return null;
    }

    private static ListenableFuture<RpcResult<GetElanInterfaceStatisticsOutput>> getFutureWithAppErrorMessage(
        RpcResultBuilder<GetElanInterfaceStatisticsOutput> rpcResultBuilder, String message) {
        return rpcResultBuilder.withError(ErrorType.APPLICATION, message).buildFuture();
    }

}
