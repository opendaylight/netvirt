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
import java.util.Optional;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePathsKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public class FibHelper {

    public static RoutePaths buildRoutePath(String nextHop, Long label) {
        return Optional.ofNullable(label).map(lbl -> {
            return new RoutePathsBuilder().setKey(new RoutePathsKey(nextHop)).setLabel(lbl)
                    .setNexthopAddress(nextHop).build();
        }).orElseGet(() -> {
            return new RoutePathsBuilder().setKey(new RoutePathsKey(nextHop))
                    .setNexthopAddress(nextHop).build();
        });
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue());
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, long label, String nextHop, RouteOrigin origin) {
        RoutePaths routePath = buildRoutePath(nextHop, label);
        return getVrfEntryBuilder(prefix, Arrays.asList(routePath), origin);
    }

    public static VrfEntryBuilder getVrfEntryBuilder(VrfEntry vrfEntry, long label,
            List<String> nextHopList, RouteOrigin origin) {
        List<RoutePaths> routePaths =
                nextHopList.stream().map(nextHop -> buildRoutePath(nextHop, label))
                        .collect(Collectors.toList());
        return getVrfEntryBuilder(vrfEntry.getDestPrefix(), routePaths, origin);
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
                .map(routePath -> routePath.getNexthopAddress())
                .collect(Collectors.toList());
    }

    public static VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = InstanceIdentifier.builder(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        com.google.common.base.Optional<VrfEntry> vrfEntry = read(broker,
                LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (vrfEntry.isPresent()) {
            return vrfEntry.get();
        }
        return null;
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