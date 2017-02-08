/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.vrfentry.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibEntriesListener extends AsyncDataTreeChangeListenerBase<VrfEntry, FibEntriesListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FibEntriesListener.class);
    private final DataBroker dataBroker;
    private final VpnInstanceListener vpnInstanceListener;

    public FibEntriesListener(final DataBroker dataBroker, final VpnInstanceListener vpnInstanceListener) {
        super(VrfEntry.class, FibEntriesListener.class);
        this.dataBroker = dataBroker;
        this.vpnInstanceListener = vpnInstanceListener;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(VrfEntry.class);
    }

    @Override
    protected FibEntriesListener getDataTreeChangeListener() {
        return FibEntriesListener.this;
    }


    @Override
    protected void remove(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry del) {
        LOG.trace("Remove Fib event - Key : {}, value : {} ", identifier, del);
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
        String rd = key.getRouteDistinguisher();
        List<RoutePaths> routePaths = del.getRoutePaths();
        removeLabelFromVpnInstance(rd, routePaths);
    }

    @Override
    protected void update(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry original, VrfEntry update) {
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
        String rd = key.getRouteDistinguisher();
        List<RoutePaths> originalRoutePaths = new ArrayList<>(original.getRoutePaths());
        List<RoutePaths> updateRoutePaths = new ArrayList<>(update.getRoutePaths());
        if (originalRoutePaths.size() < updateRoutePaths.size()) {
            updateRoutePaths.removeAll(originalRoutePaths);
            addLabelToVpnInstance(rd, updateRoutePaths);
        } else if (originalRoutePaths.size() > updateRoutePaths.size()) {
            originalRoutePaths.removeAll(updateRoutePaths);
            removeLabelFromVpnInstance(rd, originalRoutePaths);
        }
    }

    @Override
    protected void add(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry add) {
        LOG.trace("Add Vrf Entry event - Key : {}, value : {}", identifier, add);
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class, VrfTablesKey.class);
        String rd = key.getRouteDistinguisher();
        addLabelToVpnInstance(rd, add.getRoutePaths());
    }

    private void addLabelToVpnInstance(String rd, List<RoutePaths> routePaths) {
        List<Long> labels = routePaths.stream().map(routePath -> routePath.getLabel()).distinct()
                            .collect(Collectors.toList());
        VpnInstanceOpDataEntry vpn = vpnInstanceListener.getVpnInstanceOpData(rd);
        if (vpn != null) {
            for (Long label : labels) {
                List<Long> routeIds = vpn.getRouteEntryId();
                if (routeIds == null) {
                    routeIds = new ArrayList<>();
                }
                LOG.debug("Adding label to vpn info - {}", label);
                routeIds.add(label);
                TransactionUtil.asyncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                        new VpnInstanceOpDataEntryBuilder(vpn).setRouteEntryId(routeIds).build(),
                        TransactionUtil.DEFAULT_CALLBACK);
            }
        } else {
            LOG.warn("No VPN Instance found for RD: {}", rd);
        }
    }

    private void removeLabelFromVpnInstance(String rd, List<RoutePaths> routePaths) {
        List<Long> labels = routePaths.stream().map(routePath -> routePath.getLabel()).distinct()
                .collect(Collectors.toList());
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnInstanceListener.getVpnInstanceOpData(rd);
        if (vpnInstanceOpData != null) {
            for (Long label : labels) {
                List<Long> routeIds = vpnInstanceOpData.getRouteEntryId();
                if (routeIds == null) {
                    LOG.debug("Fib Route entry is empty.");
                    return;
                }
                LOG.debug("Removing label from vpn info - {}", label);
                routeIds.remove(label);
                TransactionUtil.asyncWrite(
                        dataBroker,
                        LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                        new VpnInstanceOpDataEntryBuilder(vpnInstanceOpData).setRouteEntryId(
                                routeIds).build(), TransactionUtil.DEFAULT_CALLBACK);
            }
        } else {
            LOG.warn("No VPN Instance found for RD: {}", rd);
        }
    }
}
