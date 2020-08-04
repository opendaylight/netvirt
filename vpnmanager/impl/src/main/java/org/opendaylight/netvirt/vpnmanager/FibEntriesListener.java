/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FibEntriesListener extends AbstractAsyncDataTreeChangeListener<VrfEntry> {
    private static final Logger LOG = LoggerFactory.getLogger(FibEntriesListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInstanceListener vpnInstanceListener;

    @Inject
    public FibEntriesListener(final DataBroker dataBroker, final VpnInstanceListener vpnInstanceListener) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(VrfEntry.class),
                Executors.newListeningSingleThreadExecutor("FibEntriesListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInstanceListener = vpnInstanceListener;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }


    @Override
    public void remove(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry del) {
        LOG.trace("Remove Fib event - Key : {}, value : {} ", identifier, del);
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class);
        String rd = key.getRouteDistinguisher();
        List<RoutePaths> routePaths = new ArrayList<RoutePaths>(del.nonnullRoutePaths().values());
        removeLabelFromVpnInstance(rd, routePaths);
    }

    @Override
    public void update(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry original, VrfEntry update) {
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class);
        String rd = key.getRouteDistinguisher();
        List<RoutePaths> originalRoutePaths = new ArrayList<RoutePaths>(original.nonnullRoutePaths().values());
        List<RoutePaths> updateRoutePaths = new ArrayList<RoutePaths>(update.nonnullRoutePaths().values());
        if (originalRoutePaths.size() < updateRoutePaths.size()) {
            updateRoutePaths.removeAll(originalRoutePaths);
            addLabelToVpnInstance(rd, updateRoutePaths);
        } else if (originalRoutePaths.size() > updateRoutePaths.size()) {
            originalRoutePaths.removeAll(updateRoutePaths);
            removeLabelFromVpnInstance(rd, originalRoutePaths);
        }
    }

    @Override
    public void add(InstanceIdentifier<VrfEntry> identifier,
        VrfEntry add) {
        LOG.trace("Add Vrf Entry event - Key : {}, value : {}", identifier, add);
        final VrfTablesKey key = identifier.firstKeyOf(VrfTables.class);
        String rd = key.getRouteDistinguisher();
        addLabelToVpnInstance(rd, new ArrayList<RoutePaths>(add.nonnullRoutePaths().values()));
    }

    private void addLabelToVpnInstance(String rd, List<RoutePaths> routePaths) {
        List<Uint32> labels = routePaths.stream().map(RoutePaths::getLabel).distinct()
                            .collect(Collectors.toList());
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnInstanceListener.getVpnInstanceOpData(rd);
        if (vpnInstanceOpData != null) {
            List<Uint32> routeIds = vpnInstanceOpData.getRouteEntryId() == null ? new ArrayList<>()
                    : vpnInstanceOpData.getRouteEntryId();
            labels.forEach(label -> {
                LOG.debug("Adding label to vpn info - {}", label);
                if (!routeIds.contains(label)) {
                    routeIds.add(label);
                }
            });
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx ->
                            tx.put(VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                                    new VpnInstanceOpDataEntryBuilder(vpnInstanceOpData).setRouteEntryId(routeIds)
                                            .build())),
                    LOG, "Error adding label to VPN instance");
        } else {
            LOG.warn("No VPN Instance found for RD: {}", rd);
        }
    }

    private void removeLabelFromVpnInstance(String rd, List<RoutePaths> routePaths) {
        List<Uint32> labels = routePaths.stream().map(RoutePaths::getLabel).distinct()
                .collect(Collectors.toList());
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnInstanceListener.getVpnInstanceOpData(rd);
        if (vpnInstanceOpData != null) {
            List<Uint32> routeIds = vpnInstanceOpData.getRouteEntryId();
            if (routeIds == null) {
                LOG.debug("Fib Route entry is empty.");
            } else {
                LOG.debug("Removing label from vpn info - {}", labels);
                routeIds.removeAll(labels);
                LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx ->
                                tx.put(VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                                        new VpnInstanceOpDataEntryBuilder(vpnInstanceOpData).setRouteEntryId(routeIds)
                                                .build())),
                        LOG, "Error removing label from VPN instance");
            }
        } else {
            LOG.warn("No VPN Instance found for RD: {}", rd);
        }
    }
}
