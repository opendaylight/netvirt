/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveStaticRouteInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveStaticRouteOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveStaticRouteOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

@Singleton
public class VpnRpcServiceImpl implements VpnRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(VpnRpcServiceImpl.class);
    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final IBgpManager bgpManager;
    private final IVpnManager vpnManager;
    private final InterVpnLinkCache interVpnLinkCache;
    private final VpnUtil vpnUtil;
    private final InterVpnLinkUtil interVpnLinkUtil;

    @Inject
    public VpnRpcServiceImpl(final DataBroker dataBroker, final IdManagerService idManager,
            final IFibManager fibManager, IBgpManager bgpManager, final IVpnManager vpnManager,
            final InterVpnLinkCache interVpnLinkCache, VpnUtil vpnUtil, InterVpnLinkUtil interVpnLinkUtil) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.bgpManager = bgpManager;
        this.vpnManager = vpnManager;
        this.interVpnLinkCache = interVpnLinkCache;
        this.vpnUtil = vpnUtil;
        this.interVpnLinkUtil = interVpnLinkUtil;
    }

    /**
     * Generate label for the given ip prefix from the associated VPN.
     */
    @Override
    public ListenableFuture<RpcResult<GenerateVpnLabelOutput>> generateVpnLabel(GenerateVpnLabelInput input) {
        String vpnName = input.getVpnName();
        String ipPrefix = input.getIpPrefix();
        SettableFuture<RpcResult<GenerateVpnLabelOutput>> futureResult = SettableFuture.create();
        String rd = vpnUtil.getVpnRd(vpnName);
        long label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
            VpnUtil.getNextHopLabelKey(rd != null ? rd : vpnName, ipPrefix));
        if (label == 0) {
            futureResult.set(RpcResultBuilder.<GenerateVpnLabelOutput>failed().withError(ErrorType.APPLICATION,
                    formatAndLog(LOG::error, "Could not retrieve the label for prefix {} in VPN {}", ipPrefix,
                            vpnName)).build());
        } else {
            GenerateVpnLabelOutput output = new GenerateVpnLabelOutputBuilder().setLabel(label).build();
            futureResult.set(RpcResultBuilder.success(output).build());
        }
        return futureResult;
    }

    /**
     * Remove label for the given ip prefix from the associated VPN.
     */
    @Override
    public ListenableFuture<RpcResult<RemoveVpnLabelOutput>> removeVpnLabel(RemoveVpnLabelInput input) {
        String vpnName = input.getVpnName();
        String ipPrefix = input.getIpPrefix();
        String rd = vpnUtil.getVpnRd(vpnName);
        vpnUtil.releaseId(VpnConstants.VPN_IDPOOL_NAME,
            VpnUtil.getNextHopLabelKey(rd != null ? rd : vpnName, ipPrefix));
        return RpcResultBuilder.success(new RemoveVpnLabelOutputBuilder().build()).buildFuture();
    }

    private Collection<RpcError> validateAddStaticRouteInput(AddStaticRouteInput input) {
        Collection<RpcError> rpcErrors = new ArrayList<>();
        String destination = input.getDestination();
        String vpnInstanceName = input.getVpnInstanceName();
        String nexthop = input.getNexthop();
        if (destination == null || destination.isEmpty()) {
            String message = "destination parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "addStaticRoute", message));
        }
        if (vpnInstanceName == null || vpnInstanceName.isEmpty()) {
            String message = "vpnInstanceName parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "addStaticRoute", message));
        }
        if (nexthop == null || nexthop.isEmpty()) {
            String message = "nexthop parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "addStaticRoute", message));
        }
        return rpcErrors;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public ListenableFuture<RpcResult<AddStaticRouteOutput>> addStaticRoute(AddStaticRouteInput input) {

        SettableFuture<RpcResult<AddStaticRouteOutput>> result = SettableFuture.create();
        String destination = input.getDestination();
        String vpnInstanceName = input.getVpnInstanceName();
        String nexthop = input.getNexthop();
        Long label = input.getLabel();
        LOG.info("Adding static route for Vpn {} with destination {}, nexthop {} and label {}",
            vpnInstanceName, destination, nexthop, label);

        Collection<RpcError> rpcErrors = validateAddStaticRouteInput(input);
        if (!rpcErrors.isEmpty()) {
            result.set(RpcResultBuilder.<AddStaticRouteOutput>failed().withRpcErrors(rpcErrors).build());
            return result;
        }

        if (label == null || label == 0) {
            label = (long) vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                VpnUtil.getNextHopLabelKey(vpnInstanceName, destination));
            if (label == 0) {
                String message = "Unable to retrieve a new Label for the new Route";
                result.set(RpcResultBuilder.<AddStaticRouteOutput>failed().withError(RpcError.ErrorType.APPLICATION,
                    message).build());
                return result;
            }
        }

        String vpnRd = vpnUtil.getVpnRd(input.getVpnInstanceName());
        VpnInstanceOpDataEntry vpnOpEntry = vpnUtil.getVpnInstanceOpData(vpnRd);
        Boolean isVxlan = VpnUtil.isL3VpnOverVxLan(vpnOpEntry.getL3vni());
        VrfEntry.EncapType encapType = VpnUtil.getEncapType(isVxlan);
        if (vpnRd == null) {
            String message = "Could not find Route-Distinguisher for VpnName " + vpnInstanceName;
            result.set(RpcResultBuilder.<AddStaticRouteOutput>failed().withError(RpcError.ErrorType.APPLICATION,
                message).build());
            return result;
        }

        Optional<InterVpnLinkDataComposite> optIVpnLink = interVpnLinkCache.getInterVpnLinkByEndpoint(nexthop);
        if (optIVpnLink.isPresent()) {
            try {
                interVpnLinkUtil.handleStaticRoute(optIVpnLink.get(), vpnInstanceName, destination, nexthop,
                                                   label.intValue());
            } catch (Exception e) {
                result.set(RpcResultBuilder.<AddStaticRouteOutput>failed().withError(ErrorType.APPLICATION,
                        formatAndLog(LOG::warn,
                                "Could not advertise route [vpn={}, prefix={}, label={}, nexthop={}] to BGP: {}", vpnRd,
                                destination, label, nexthop, e.getMessage(), e)).build());
                return result;
            }
        } else {
            vpnManager.addExtraRoute(vpnInstanceName, destination, nexthop, vpnRd, null /* routerId */,
                    vpnOpEntry.getL3vni(), RouteOrigin.STATIC, null /* intfName */,
                            null /*Adjacency*/, encapType, null);
        }

        AddStaticRouteOutput labelOutput = new AddStaticRouteOutputBuilder().setLabel(label).build();
        result.set(RpcResultBuilder.success(labelOutput).build());
        return result;
    }

    private Collection<RpcError> validateRemoveStaticRouteInput(RemoveStaticRouteInput input) {
        Collection<RpcError> rpcErrors = new ArrayList<>();
        String destination = input.getDestination();
        String vpnInstanceName = input.getVpnInstanceName();
        String nexthop = input.getNexthop();
        if (destination == null || destination.isEmpty()) {
            String message = "destination parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "removeStaticRoute", message));
        }
        if (vpnInstanceName == null || vpnInstanceName.isEmpty()) {
            String message = "vpnInstanceName parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "removeStaticRoute", message));
        }
        if (nexthop == null || nexthop.isEmpty()) {
            String message = "nexthop parameter is mandatory";
            rpcErrors.add(RpcResultBuilder.newError(RpcError.ErrorType.PROTOCOL, "removeStaticRoute", message));
        }
        return rpcErrors;
    }

    @Override
    public ListenableFuture<RpcResult<RemoveStaticRouteOutput>> removeStaticRoute(RemoveStaticRouteInput input) {

        SettableFuture<RpcResult<RemoveStaticRouteOutput>> result = SettableFuture.create();

        String destination = input.getDestination();
        String vpnInstanceName = input.getVpnInstanceName();
        String nexthop = input.getNexthop();
        LOG.info("Removing static route with destination={}, nexthop={} in VPN={}",
            destination, nexthop, vpnInstanceName);
        Collection<RpcError> rpcErrors = validateRemoveStaticRouteInput(input);
        if (!rpcErrors.isEmpty()) {
            result.set(RpcResultBuilder.<RemoveStaticRouteOutput>failed().withRpcErrors(rpcErrors).build());
            return result;
        }

        String vpnRd = vpnUtil.getVpnRd(input.getVpnInstanceName());
        if (vpnRd == null) {
            String message = "Could not find Route-Distinguisher for VpnName " + vpnInstanceName;
            result.set(RpcResultBuilder.<RemoveStaticRouteOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, message).build());
            return result;
        }

        Optional<InterVpnLinkDataComposite> optVpnLink = interVpnLinkCache.getInterVpnLinkByEndpoint(nexthop);
        if (optVpnLink.isPresent()) {
            fibManager.removeOrUpdateFibEntry(vpnRd, destination, nexthop, /*writeTx*/ null);
            bgpManager.withdrawPrefix(vpnRd, destination);
        } else {
            vpnManager.delExtraRoute(vpnInstanceName, destination,
                    nexthop, vpnRd, null /* routerId */, null /* intfName */, null);
        }
        result.set(RpcResultBuilder.success(new RemoveStaticRouteOutputBuilder().build()).build());

        return result;
    }

    private String formatAndLog(Consumer<String> logger, String template, Object arg1, Object arg2) {
        return logAndReturnMessage(logger, MessageFormatter.format(template, arg1, arg2));
    }

    private String formatAndLog(Consumer<String> logger, String template, Object... args) {
        return logAndReturnMessage(logger, MessageFormatter.arrayFormat(template, args));
    }

    private String logAndReturnMessage(Consumer<String> logger, FormattingTuple tuple) {
        String message = tuple.getMessage();
        logger.accept(message);
        return message;
    }
}
