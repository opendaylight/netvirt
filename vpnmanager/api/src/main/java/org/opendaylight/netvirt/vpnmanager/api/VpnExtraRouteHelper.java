/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VpnExtraRouteHelper {
    private static final Logger LOG = LoggerFactory.getLogger(VpnExtraRouteHelper.class);

    private VpnExtraRouteHelper() { }

    public static  List<Routes> getVpnExtraroutes(DataBroker broker, String vpnName, String vpnRd) {
        InstanceIdentifier<ExtraRoutes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd);
        Optional<ExtraRoutes> vpnOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
        return vpnOpc.isPresent() ? vpnOpc.get().getRoutes() : new ArrayList<>();
    }

    public static Optional<Routes> getVpnExtraroutes(DataBroker broker, String vpnName,
                                                     String vpnRd, String destPrefix) {
        InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteVrfIdIdentifier(vpnName, vpnRd, destPrefix);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
    }

    public static  InstanceIdentifier<Routes> getVpnToExtrarouteVrfIdIdentifier(String vpnName, String vrfId,
            String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    public static  InstanceIdentifier<ExtraRoutes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).build();
    }

    public static  InstanceIdentifier<Vpn> getVpnToExtrarouteVpnIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).build();
    }

    public static List<Routes> getAllVpnExtraRoutes(DataBroker dataBroker, String vpnName,
            List<String> usedRds, String destPrefix) {
        List<Routes> routes = new ArrayList<>();
        for (String rd : usedRds) {
            Optional<Routes> extraRouteInfo =
                    MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            getVpnToExtrarouteVrfIdIdentifier(vpnName, rd, destPrefix));
            if (extraRouteInfo.isPresent()) {
                routes.add(extraRouteInfo.get());
            }
        }
        return routes;
    }

    public static  List<String> getUsedRds(DataBroker broker, long vpnId, String destPrefix) {
        InstanceIdentifier<DestPrefixes> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix);
        Optional<DestPrefixes> usedRds = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, usedRdsId);
        return usedRds.isPresent() ? usedRds.get().getAllocatedRds().stream()
                .map(AllocatedRds::getRd).distinct().collect(toList()) : new ArrayList<>();
    }

    public static  InstanceIdentifier<ExtrarouteRds> getUsedRdsIdentifier(long vpnId) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId)).build();
    }

    public static  InstanceIdentifier<DestPrefixes> getUsedRdsIdentifier(long vpnId, String destPrefix) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId))
                .child(DestPrefixes.class, new DestPrefixesKey(destPrefix)).build();
    }

    public static  InstanceIdentifier<AllocatedRds> getUsedRdsIdentifier(long vpnId, String destPrefix, String nh) {
        return InstanceIdentifier.builder(ExtrarouteRdsMap.class)
                .child(ExtrarouteRds.class, new ExtrarouteRdsKey(vpnId))
                .child(DestPrefixes.class, new DestPrefixesKey(destPrefix))
                .child(AllocatedRds.class, new AllocatedRdsKey(nh)).build();
    }

    public static List<Routes> getAllExtraRoutes(DataBroker broker, String vpnName, String vrfId) {
        Optional<ExtraRoutes> extraRoutes = MDSALUtil.read(broker,LogicalDatastoreType.OPERATIONAL,
                getVpnToExtrarouteIdentifier(vpnName, vrfId));
        List<Routes> extraRoutesList = new ArrayList<>();
        if (extraRoutes.isPresent()) {
            extraRoutesList = extraRoutes.get().getRoutes();
        }
        return extraRoutesList;
    }

    public static Class<? extends TunnelTypeBase> getTunnelType(OdlInterfaceRpcService interfaceManager,
            String ifName) {
        try {
            Future<RpcResult<GetTunnelTypeOutput>> result = interfaceManager.getTunnelType(
                    new GetTunnelTypeInputBuilder().setIntfName(ifName).build());
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
            long vpnId, String destPrefix, String nextHop) {
        InstanceIdentifier<AllocatedRds> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix, nextHop);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, usedRdsId)
                .toJavaUtil().map(AllocatedRds::getRd);
    }

    public static List<DestPrefixes> getExtraRouteDestPrefixes(DataBroker broker, Long vpnId) {
        Optional<ExtrarouteRds> extraRoutes = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                getUsedRdsIdentifier(vpnId));
        return extraRoutes.isPresent() ? extraRoutes.get().getDestPrefixes() : new ArrayList<>();
    }
}
