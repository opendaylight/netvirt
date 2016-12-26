/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.ExtrarouteRoutedistinguishersMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.ExtrarouteRoutedistingueshers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.ExtrarouteRoutedistingueshersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.extraroute.routedistingueshers.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.extraroute.routedistingueshers.DestPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnExtraroutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.ExtraRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.ExtraRoutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.extra.routes.RoutesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnHelper {
    private static final Logger LOG = LoggerFactory.getLogger(VpnHelper.class);

    public static  List<Routes> getVpnExtraroutes(DataBroker broker, String vpnName, String vpnRd) {
        InstanceIdentifier<ExtraRoutes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd);
        Optional<ExtraRoutes> vpnOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
        return vpnOpc.isPresent() ? vpnOpc.get().getRoutes() : new ArrayList<Routes>();
    }

    public static Optional<Routes> getVpnExtraroutes(DataBroker broker, String vpnName,
                                                     String vpnRd, String destPrefix) {
        InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd, destPrefix);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
    }

    public static  InstanceIdentifier<Routes> getVpnToExtrarouteIdentifier(String vpnName,
            String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    static  InstanceIdentifier<ExtraRoutes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).build();
    }

    public static  InstanceIdentifier<VpnExtraroutes> getVpnToExtrarouteIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).build();
    }

    public static List<Routes> getAllVpnExtraRoutes(DataBroker dataBroker, String vpnName,
            List<String> usedRds, String destPrefix) {
        List<Routes> routes = new ArrayList<>();
        for (String rd : usedRds) {
            Optional<Routes> extraRouteInfo =
                    MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            VpnHelper.getVpnToExtrarouteIdentifier(vpnName, rd, destPrefix));
            if (extraRouteInfo.isPresent()) {
                routes.add(extraRouteInfo.get());
            }
        }
        return routes;
    }

    public static List<String> getUsedRds(DataBroker broker, long vpnId, String destPrefix) {
        InstanceIdentifier<DestPrefixes> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix);
        Optional<DestPrefixes> vpnOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, usedRdsId);
        return vpnOpc.isPresent() ? vpnOpc.get().getRouteDistinguishers() : new ArrayList<String>();
    }

    public static  InstanceIdentifier<DestPrefixes> getUsedRdsIdentifier(long vpnId, String destPrefix) {
        return InstanceIdentifier.builder(ExtrarouteRoutedistinguishersMap.class)
                .child(ExtrarouteRoutedistingueshers.class, new ExtrarouteRoutedistingueshersKey(vpnId))
                .child(DestPrefixes.class, new DestPrefixesKey(destPrefix)).build();
    }

    static List<Routes> getAllExtraRoutes(DataBroker broker, String vpnName, String vrfId) {
        Optional<ExtraRoutes> extraRoutes = MDSALUtil.read(broker,LogicalDatastoreType.OPERATIONAL,
                getVpnToExtrarouteIdentifier(vpnName, vrfId));
        if (extraRoutes.isPresent()) {
            return extraRoutes.get().getRoutes();
        }
        return new ArrayList<>();
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

}
