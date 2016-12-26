/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetTunnelTypeOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.ExtrarouteRoutedistinguishersMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.ExtrarouteRoutedistinguishers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.ExtrarouteRoutedistinguishersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.extraroute.routedistinguishers.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.routedistinguishers.map.extraroute.routedistinguishers.DestPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnExtraroutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.ExtraRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.ExtraRoutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.extra.routes.RoutesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extraroutes.extra.routes.RoutesKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VpnHelper {
    private static final Logger LOG = LoggerFactory.getLogger(VpnHelper.class);


    public static void sortIpAddress(List<RoutePaths> routePathsList) {
        Collections.sort(routePathsList, (route1, route2) -> route1.getNexthopAddressList().get(0)
                .compareTo(route2.getNexthopAddressList().get(0)));
    }

    public static RoutePaths buildRoutePath(RoutePaths routePaths, List<String> nextHops, long label) {
        if (routePaths == null) {
            return buildRoutePath(nextHops, label);
        }
        return new RoutePathsBuilder(routePaths).setKey(new RoutePathsKey(label)).setLabel(label)
                .setNexthopAddressList(nextHops).build();
    }

    public static RoutePaths buildRoutePath(List<String> nextHops, long label) {
        return new RoutePathsBuilder().setKey(new RoutePathsKey(label)).setLabel(label)
                .setNexthopAddressList(nextHops).build();
    }

    public static VrfEntry buildVrfEntry(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).build();
    }

    public static VrfEntry buildVrfEntry(String prefix, long label, List<String> nextHops, RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHops, label);
        return buildVrfEntry(prefix, Arrays.asList(routePath), origin);
    }

    public static VrfEntry buildVrfEntry(String prefix, long label, List<String> nextHops, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHops, label);
        routePaths.add(routePath);
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).build();
    }

    public static InstanceIdentifier<RoutePaths> buildRoutePathId(String rd, String prefix, Long label) {
        InstanceIdentifierBuilder<RoutePaths> idBuilder =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix))
                        .child(RoutePaths.class, new RoutePathsKey(label));
        return idBuilder.build();
    }

    public static VrfEntry buildVrfEntry(String prefix, RoutePaths routePaths, RouteOrigin origin) {
        return buildVrfEntry(prefix, Arrays.asList(routePaths), origin);
    }

    public static  InstanceIdentifier<Routes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    public static  InstanceIdentifier<ExtraRoutes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).build();
    }

    public static  InstanceIdentifier<VpnExtraroutes> getVpnToExtrarouteIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(VpnExtraroutes.class, new VpnExtraroutesKey(vpnName)).build();
    }

    public static  List<Routes> getVpnExtraroutes(DataBroker broker, String vpnName, String vpnRd) {
        InstanceIdentifier<ExtraRoutes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd);
        Optional<ExtraRoutes> vpnOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
        return vpnOpc.isPresent() ? vpnOpc.get().getRoutes() : new ArrayList<Routes>();
    }

    public static  Optional<Routes> getVpnExtraroutes(DataBroker broker, String vpnName, String vpnRd, String destPrefix) {
        InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd, destPrefix);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
    }

    public static  Routes getVpnToExtraroute(String ipPrefix, List<String> nextHopList) {
        return new RoutesBuilder().setPrefix(ipPrefix).setNexthopIpList(nextHopList).build();
    }

    public static List<Routes> getAllVpnExtraRoutes(DataBroker dataBroker, String vpnName, List<String> usedRds, String destPrefix) {
        List <Routes> routes = new ArrayList<>();
        for (String rd : usedRds) {
            Optional<Routes> extraRouteInfo =
                    MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, VpnHelper.getVpnToExtrarouteIdentifier(vpnName, rd, destPrefix));
            if(extraRouteInfo.isPresent()) {
                routes.add(extraRouteInfo.get());
            }
        }
        return routes;
    }

    public static  List<String> getUsedRds(DataBroker broker, long vpnId, String destPrefix) {
        InstanceIdentifier<DestPrefixes> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix);
        Optional<DestPrefixes> vpnOpc = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, usedRdsId);
        return vpnOpc.isPresent() ? vpnOpc.get().getRouteDistinguishers() : new ArrayList<String>();
    }

    public static  InstanceIdentifier<DestPrefixes> getUsedRdsIdentifier(long vpnId, String destPrefix) {
        return InstanceIdentifier.builder(ExtrarouteRoutedistinguishersMap.class)
        .child(ExtrarouteRoutedistinguishers.class, new ExtrarouteRoutedistinguishersKey(vpnId)).child(DestPrefixes.class,
                new DestPrefixesKey(destPrefix)).build();
    }

    public static Class<? extends TunnelTypeBase> getTunnelType(OdlInterfaceRpcService interfaceManager, String ifName) {
        try {
            Future<RpcResult<GetTunnelTypeOutput>> result = interfaceManager.getTunnelType(
                    new GetTunnelTypeInputBuilder().setIntfName(ifName).build());
            RpcResult<GetTunnelTypeOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getTunnelType();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel type", e);
        }
        return null;
    }

    public static RoutePaths getNonBgpRoutePath(VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            throw new NoSuchElementException("RoutePath does not exists for the vrfEntry " + vrfEntry);
        }
        return vrfEntry.getRoutePaths().get(0);
    }
}
