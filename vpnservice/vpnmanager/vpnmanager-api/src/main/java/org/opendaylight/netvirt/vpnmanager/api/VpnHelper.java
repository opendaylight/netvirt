/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import java.util.Collections;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsKey;

public class VpnHelper {

    public static void sortIpAddress(List<RoutePaths> routePathsList) {
        Collections.sort(routePathsList, (route1, route2) -> route1.getNextHopAddressList().get(0)
                .compareTo(route2.getNextHopAddressList().get(0)));
    }

    public static RoutePaths buildRoutePath(RoutePaths routePaths, List<String> nextHops, long label) {
        if (routePaths == null) {
            return new RoutePathsBuilder().setKey(new RoutePathsKey(label)).setLabel(label)
                    .setNextHopAddressList(nextHops).build();
        }
        return new RoutePathsBuilder(routePaths).setKey(new RoutePathsKey(label)).setLabel(label)
                .setNextHopAddressList(nextHops).build();
    }
}
