/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibDSWriter {
    private static final Logger LOG = LoggerFactory.getLogger(FibDSWriter.class);
    private final DataBroker dataBroker;

    public FibDSWriter(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public synchronized void addFibEntryToDS(String rd, String prefix, List<String> nextHopList,
            int label, RouteOrigin origin) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");
        List<RoutePaths> routePaths = new ArrayList<>();

        for (String nextHop : nextHopList) {
            if (nextHop == null || nextHop.isEmpty()) {
                LOG.error("nextHop list contains null element");
                return;
            }
            routePaths.add(VpnHelper.buildRoutePath(nextHop, label));
            LOG.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);

        }

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

        VrfEntry vrfEntry = VpnHelper.buildVrfEntry(prefix, routePaths, origin);

        BgpUtil.update(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);

    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifierBuilder<VrfEntry> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(
                        VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        BgpUtil.delete(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

    }

    public synchronized void removeVrfFromDS(String rd) {
        LOG.debug("Removing vrf table for  rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        BgpUtil.delete(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfTableId);

    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix, String nextHop) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}",
                prefix, rd);

        // TODO [KK] : If its the last route should we delete the entire prefix?
        InstanceIdentifier<RoutePaths> routePathId = VpnHelper.buildRoutePathId(rd, prefix, nextHop);
        BgpUtil.delete(dataBroker, LogicalDatastoreType.CONFIGURATION, routePathId);

    }
}
