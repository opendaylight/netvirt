/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.api;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelTypeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelTypeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.ExtrarouteRdsMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.ExtrarouteRds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.ExtrarouteRdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.dest.prefixes.AllocatedRds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.dest.prefixes.AllocatedRdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.RoutesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VpnExtraRouteHelper {
    private static final Logger LOG = LoggerFactory.getLogger(VpnExtraRouteHelper.class);

    private VpnExtraRouteHelper() {

    }

    public static Optional<Routes> getVpnExtraroutes(DataBroker broker, String vpnName,
                                                     String vpnRd, String destPrefix) {
        InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteVrfIdIdentifier(vpnName, vpnRd, destPrefix);
        Optional<Routes> extraRouteOptional = Optional.empty();
        try {
            extraRouteOptional = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.OPERATIONAL,
                    vpnExtraRoutesId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVpnExtraroutes: failed to read VpnToExtraRoutes for vpn {} rd {} destprefix {} due "
                    + "to exception", vpnName, vpnRd, destPrefix, e);
        }
        return extraRouteOptional;
    }

    public static Optional<Routes> getVpnExtraroutes(TypedReadTransaction<Datastore.Operational> operTx,
                                                     String vpnName, String vpnRd, String destPrefix)
            throws ExecutionException, InterruptedException {
        return operTx.read(getVpnToExtrarouteVrfIdIdentifier(vpnName, vpnRd, destPrefix)).get();
    }

    public static  InstanceIdentifier<Routes> getVpnToExtrarouteVrfIdIdentifier(String vpnName, String vrfId,
            String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    public static  InstanceIdentifier<Vpn> getVpnToExtrarouteVpnIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).build();
    }

    public static List<Routes> getAllVpnExtraRoutes(DataBroker dataBroker, String vpnName,
            List<String> usedRds, String destPrefix) {
        List<Routes> routes = new ArrayList<>();
        for (String rd : usedRds) {
            try {
                Optional<Routes> extraRouteInfo = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                getVpnToExtrarouteVrfIdIdentifier(vpnName, rd, destPrefix));
                extraRouteInfo.ifPresent(routes::add);
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("getAllVpnExtraRoutes: failed to read VpnToExtraRouteVrf for vpn {} rd {} destprefix {} due "
                       + "to exception", vpnName, rd, destPrefix, e);
            }
        }
        return routes;
    }

    public static  List<String> getUsedRds(DataBroker broker, Uint32 vpnId, String destPrefix) {
        InstanceIdentifier<DestPrefixes> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix);
        Optional<DestPrefixes> usedRds = Optional.empty();
        try {
            usedRds = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, usedRdsId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getUsedRds: failed to read Used Rds for vpn {} destprefix {} due to exception", vpnId,
                    destPrefix, e);
        }
        return usedRds.isPresent() && usedRds.get().getAllocatedRds() != null
                ? usedRds.get().nonnullAllocatedRds().values().stream()
                .map(AllocatedRds::getRd).distinct().collect(toList()) : new ArrayList<>();
    }

    public static  List<String> getUsedRds(org.opendaylight.mdsal.binding.util
                                                   .TypedReadTransaction<Datastore.Configuration> confTx,
                                           Uint32 vpnId, String destPrefix)
            throws ExecutionException, InterruptedException {
        Optional<DestPrefixes> usedRds = confTx.read(getUsedRdsIdentifier(vpnId, destPrefix)).get();
        return usedRds.isPresent() && usedRds.get().getAllocatedRds() != null
                ? usedRds.get().nonnullAllocatedRds().values().stream()
            .map(AllocatedRds::getRd).distinct().collect(toList()) : new ArrayList<>();
    }

    public static  InstanceIdentifier<ExtrarouteRds> getUsedRdsIdentifier(Uint32 vpnId) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId)).build();
    }

    public static  InstanceIdentifier<DestPrefixes> getUsedRdsIdentifier(Uint32 vpnId, String destPrefix) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId))
                .child(DestPrefixes.class, new DestPrefixesKey(destPrefix)).build();
    }

    public static  InstanceIdentifier<AllocatedRds> getUsedRdsIdentifier(Uint32 vpnId, String destPrefix, String nh) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId))
                .child(DestPrefixes.class, new DestPrefixesKey(destPrefix))
                .child(AllocatedRds.class, new AllocatedRdsKey(nh)).build();
    }

    @Nullable
    public static Class<? extends TunnelTypeBase> getTunnelType(ItmRpcService itmRpcService, String ifName) {
        try {
            Future<RpcResult<GetTunnelTypeOutput>> result =
                    itmRpcService.getTunnelType(new GetTunnelTypeInputBuilder().setIntfName(ifName).build());
            RpcResult<GetTunnelTypeOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getTunnelType();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel type", e);
        }
        return null;
    }

    public static java.util.Optional<String> getRdAllocatedForExtraRoute(DataBroker broker,
            Uint32 vpnId, String destPrefix, String nextHop) {
        InstanceIdentifier<AllocatedRds> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix, nextHop);
        try {
            return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, usedRdsId)
                    .map(AllocatedRds::getRd);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getRdAllocatedForExtraRoute: failed to read Used Rds for vpn {} destprefix {} nexthop {} due "
                    + "to exception", vpnId, destPrefix, nextHop, e);
        }
        return Optional.empty();
    }

    public static List<DestPrefixes> getExtraRouteDestPrefixes(DataBroker broker, Uint32 vpnId) {
        try {
            Optional<ExtrarouteRds> optionalExtraRoutes = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                    getUsedRdsIdentifier(vpnId));
            Map<DestPrefixesKey, DestPrefixes> prefixesMap
                    = optionalExtraRoutes.map(ExtrarouteRds::getDestPrefixes).orElse(null);
            return prefixesMap == null ? Collections.emptyList() : new ArrayList<DestPrefixes>(prefixesMap.values());
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getExtraRouteDestPrefixes: failed to read ExRoutesRdsMap for vpn {} due to exception", vpnId, e);
        }
        return new ArrayList<>();
    }
}
