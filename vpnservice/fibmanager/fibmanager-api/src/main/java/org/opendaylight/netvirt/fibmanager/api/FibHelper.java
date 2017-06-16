/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePathsKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public class FibHelper {

    public static RoutePaths buildRoutePath(String nextHop, Long label) {
        RoutePathsBuilder builder = new RoutePathsBuilder()
                .setKey(new RoutePathsKey(nextHop))
                .setNexthopAddress(nextHop);
        if (label != null) {
            builder.setLabel(label);
        }
        return builder.build();
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, RouteOrigin origin, String parentVpnRd) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setOrigin(origin.getValue()).setParentVpnRd(parentVpnRd);
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin, String parentVpnRd) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).setParentVpnRd(parentVpnRd);
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, long label, String nextHop, RouteOrigin origin,
            String parentVpnRd) {
        if (nextHop != null) {
            RoutePaths routePath = buildRoutePath(nextHop, label);
            return getVrfEntryBuilder(prefix, Arrays.asList(routePath), origin, parentVpnRd);
        } else {
            return getVrfEntryBuilder(prefix, origin, parentVpnRd);
        }
    }

    public static VrfEntryBuilder getVrfEntryBuilder(VrfEntry vrfEntry, long label,
            List<String> nextHopList, RouteOrigin origin, String parentvpnRd) {
        List<RoutePaths> routePaths =
                nextHopList.stream().map(nextHop -> buildRoutePath(nextHop, label))
                        .collect(toList());
        return getVrfEntryBuilder(vrfEntry.getDestPrefix(), routePaths, origin, parentvpnRd);
    }

    public static InstanceIdentifier<RoutePaths> buildRoutePathId(String rd, String prefix, String nextHop) {
        InstanceIdentifierBuilder<RoutePaths> idBuilder =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix))
                        .child(RoutePaths.class, new RoutePathsKey(nextHop));
        return idBuilder.build();
    }

    public static boolean isControllerManagedRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.CONNECTED
                || routeOrigin == RouteOrigin.LOCAL
                || routeOrigin == RouteOrigin.INTERVPN;
    }

    public static boolean isControllerManagedNonInterVpnLinkRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.CONNECTED
                || routeOrigin == RouteOrigin.LOCAL;
    }

    public static boolean isControllerManagedVpnInterfaceRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.LOCAL;
    }

    public static boolean isControllerManagedNonSelfImportedRoute(RouteOrigin routeOrigin) {
        return routeOrigin != RouteOrigin.SELF_IMPORTED;
    }

    public static void sortIpAddress(List<RoutePaths> routePathList) {
        if (routePathList != null) {
            routePathList.sort(comparing(RoutePaths::getNexthopAddress));
        }
    }

    public static InstanceIdentifier<RoutePaths> getRoutePathsIdentifier(String rd, String prefix, String nh) {
        return InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class,new VrfTablesKey(rd)).child(VrfEntry.class,new VrfEntryKey(prefix))
                .child(RoutePaths.class, new RoutePathsKey(nh)).build();
    }

    public static List<String> getNextHopListFromRoutePaths(final VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return routePaths.stream()
                .map(RoutePaths::getNexthopAddress)
                .collect(Collectors.toList());
    }

    public static com.google.common.base.Optional<VrfEntry> getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = InstanceIdentifier.builder(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
    }

    private static <T extends DataObject> com.google.common.base.Optional<T> read(DataBroker broker,
            LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
