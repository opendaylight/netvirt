/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

public class FibHelper {

    public static RoutePaths buildRoutePath(String nextHop, long label) {
        return new RoutePathsBuilder().setKey(new RoutePathsKey(nextHop)).setLabel(label)
                .setNexthopAddress(nextHop).build();
    }

    public static VrfEntryBuilder buildVrfEntry(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue());
    }

    public static VrfEntryBuilder buildVrfEntry(String prefix, long label, String nextHop, RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHop, label);
        return buildVrfEntry(prefix, Arrays.asList(routePath), origin);
    }

    public static VrfEntryBuilder buildVrfEntry(VrfEntry vrfEntry, long label, List<String> nextHopList, RouteOrigin origin) {
        List<RoutePaths> routePaths = Collections.EMPTY_LIST;
        routePaths = nextHopList.stream().map(nextHop -> buildRoutePath(nextHop, label)).collect(Collectors.toList());
        return buildVrfEntry(vrfEntry.getDestPrefix(), routePaths, origin);
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