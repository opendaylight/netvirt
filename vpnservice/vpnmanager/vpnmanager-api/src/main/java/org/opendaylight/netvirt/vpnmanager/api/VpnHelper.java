/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsKey;

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

    public static VrfEntry buildVrfEntry(String prefix, RoutePaths routePaths, RouteOrigin origin) {
        return buildVrfEntry(prefix, Arrays.asList(routePaths), origin);
    }
}
