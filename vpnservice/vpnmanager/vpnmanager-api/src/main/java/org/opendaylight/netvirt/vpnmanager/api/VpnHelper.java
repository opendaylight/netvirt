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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
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

import com.google.common.base.Optional;

public class VpnHelper {

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

    public static  Optional<Routes> getVpnExtraroutes(DataBroker broker, String vpnName, String vpnRd, String destPrefix) {
        InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(vpnName, vpnRd, destPrefix);
        return read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
    }

    public static  Routes getVpnToExtraroute(String ipPrefix, List<String> nextHopList) {
        return new RoutesBuilder().setPrefix(ipPrefix).setNexthopIpList(nextHopList).build();
    }

    public static List<Routes> getAllVpnExtraRoutes(DataBroker dataBroker, String vpnName, List<String> usedRds, String destPrefix) {
        List <Routes> routes = new ArrayList<>();
        for (String rd : usedRds) {
            Optional<Routes> extraRouteInfo =
                    read(dataBroker, LogicalDatastoreType.OPERATIONAL, VpnHelper.getVpnToExtrarouteIdentifier(vpnName, rd, destPrefix));
            if(extraRouteInfo.isPresent()) {
                routes.add(extraRouteInfo.get());
            }
        }
        return routes;
    }

    public static  InstanceIdentifier<DestPrefixes> getUsedRdsIdentifier(long vpnId, String destPrefix) {
        return InstanceIdentifier.builder(ExtrarouteRoutedistinguishersMap.class)
                .child(ExtrarouteRoutedistinguishers.class, new ExtrarouteRoutedistinguishersKey(vpnId)).child(DestPrefixes.class,
                        new DestPrefixesKey(destPrefix)).build();
    }

    public static  List<String> getUsedRds(DataBroker broker, long vpnId, String destPrefix) {
        InstanceIdentifier<DestPrefixes> usedRdsId = getUsedRdsIdentifier(vpnId, destPrefix);
        Optional<DestPrefixes> vpnOpc = read(broker, LogicalDatastoreType.OPERATIONAL, usedRdsId);
        if(vpnOpc.isPresent()) {
            if(vpnOpc.get().getRouteDistinguishers() != null) {
                return vpnOpc.get().getRouteDistinguishers();
            } else {
                return new ArrayList<String>();
            }
        } else {
            return new ArrayList<String>();
        }
    }

    public static  <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();
        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
