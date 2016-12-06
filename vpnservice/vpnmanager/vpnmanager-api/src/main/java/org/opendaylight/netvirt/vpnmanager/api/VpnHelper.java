/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public class VpnHelper {

    public static void sortIpAddress(List<RoutePaths> routePathsList) {
        Collections.sort(routePathsList, (route1, route2) -> route1.getNexthopAddress()
                .compareTo(route2.getNexthopAddress()));
    }

    public static RoutePaths buildRoutePath(RoutePaths routePaths, String nextHop, long label) {
        if (routePaths == null) {
            return buildRoutePath(nextHop, label);
        }
        return new RoutePathsBuilder(routePaths).setKey(new RoutePathsKey(nextHop)).setLabel(label)
                .setNexthopAddress(nextHop).build();
    }

    public static RoutePaths buildRoutePath(String nextHop, long label) {
        return new RoutePathsBuilder().setKey(new RoutePathsKey(nextHop)).setLabel(label)
                .setNexthopAddress(nextHop).build();
    }

    public static VrfEntry buildVrfEntry(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).build();
    }

    public static VrfEntry buildVrfEntry(String prefix, long label, String nextHop, RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHop, label);
        return buildVrfEntry(prefix, Arrays.asList(routePath), origin);
    }

    public static VrfEntry buildVrfEntry(String prefix, long label, List<String> nextHopList, RouteOrigin origin) {
        List<RoutePaths> routePaths = Collections.EMPTY_LIST;
        routePaths = nextHopList.stream().map(nextHop -> buildRoutePath(nextHop, label)).collect(Collectors.toList());
        return buildVrfEntry(prefix, routePaths, origin);
    }

    public static VrfEntry buildVrfEntry(String prefix, long label, String nextHop, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHop, label);
        routePaths.add(routePath);
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).build();
    }

    public static VrfEntry buildVrfEntry(VrfEntry vrfEntry, String prefix, long label, String nextHop, RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHop, label);
        return buildVrfEntry(prefix, Arrays.asList(routePath), origin);
    }

    public static InstanceIdentifier<RoutePaths> buildRoutePathId(String rd, String prefix, String nextHop) {
        InstanceIdentifierBuilder<RoutePaths> idBuilder =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix))
                        .child(RoutePaths.class, new RoutePathsKey(nextHop));
        return idBuilder.build();
    }
}
