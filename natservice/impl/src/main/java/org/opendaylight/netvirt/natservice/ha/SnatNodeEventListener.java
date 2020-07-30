/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CentralizedSwitchChangeListener adds/removes the switches to scheduler pool when a switch is
 * added/removed.
 */
@Singleton
public class SnatNodeEventListener  extends AbstractClusteredAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(SnatNodeEventListener.class);
    private final NatSwitchCache  centralizedSwitchCache;
    private final NatserviceConfig.NatMode natMode;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator coordinator;
    private final ConcurrentMap<Uint64, ClearRouterDpnsJob> clearDsJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uint64, ScheduledFuture> nodeDeletedTasks
            = new ConcurrentHashMap<>();
    private final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("nat-cleands-%d").build();
    private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(
            1, namedThreadFactory);

    @Inject
    public SnatNodeEventListener(final DataBroker dataBroker,
            final NatSwitchCache centralizedSwitchCache,
            final NatserviceConfig config, final JobCoordinator coordinator) {

        super(dataBroker, DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier
                .create(Nodes.class).child(Node.class)),
                Executors.newSingleThreadExecutor());
        this.centralizedSwitchCache = centralizedSwitchCache;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatserviceConfig.NatMode.Controller;
        }
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.coordinator = coordinator;
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        org.opendaylight.infrautils.utils.concurrent.Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.key();
        Uint64 dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        if (natMode == NatserviceConfig.NatMode.Conntrack) {
            LOG.info("Dpn removed {}", dpnId);
            centralizedSwitchCache.removeSwitch(dpnId);
        } else {
            scheduleDsCleanUpForNodeDeletion(dpnId);
        }
    }

    @Override
    public void update(Node dataObjectModificationBefore,
            Node dataObjectModificationAfter) {
        /*Do Nothing */
    }

    @Override
    public void add(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.key();
        Uint64 dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        if (natMode == NatserviceConfig.NatMode.Conntrack) {
            LOG.info("Dpn added {}", dpnId);
            centralizedSwitchCache.addSwitch(dpnId);
        } else {
            cancelDsCleanUpForNodeDeletion(dpnId);
        }
    }

    public void scheduleDsCleanUpForNodeDeletion(Uint64 dpnId) {
        LOG.debug("Scheduling CleanUp DS Job for DpnId : {}", dpnId);
        nodeDeletedTasks.computeIfAbsent(dpnId, (key) -> {
            return scheduleService.schedule(() -> {
                ClearRouterDpnsJob clearDs = new ClearRouterDpnsJob(dataBroker, dpnId);
                coordinator.enqueueJob(clearDs.getJobKey(), clearDs,
                        SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                clearDsJobs.put(dpnId, clearDs);
                nodeDeletedTasks.remove(dpnId);
            }, 120, TimeUnit.SECONDS);
        });
    }

    public void cancelDsCleanUpForNodeDeletion(Uint64 dpnId) {
        ScheduledFuture nodeDeletedTask = nodeDeletedTasks.remove(dpnId);
        if (nodeDeletedTask != null) {
            LOG.debug("Cleaning up DS for Node {} cancelled", dpnId);
            nodeDeletedTask.cancel(true);
            ClearRouterDpnsJob cleanDsJob = clearDsJobs.remove(dpnId);
            if (cleanDsJob != null) {
                cleanDsJob.cancel();
            }
        }

    }

    private class ClearRouterDpnsJob implements Callable<List<? extends ListenableFuture<?>>> {
        private DataBroker dataBroker;
        private Uint64 currentdpnId;
        private volatile boolean cancelled = false;

        ClearRouterDpnsJob(DataBroker dataBroker, Uint64 currentdpnId) {
            this.dataBroker = dataBroker;
            this.currentdpnId = currentdpnId;
        }

        public void cancel() {
            this.cancelled = true;
        }

        public String getJobKey() {
            return NatConstants.NAT_DJC_PREFIX + currentdpnId.toString().intern();
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            if (cancelled) {
                LOG.info("Cancelling Clearing Router Dpn DS Job for Dpn {}", currentdpnId);
                return Collections.emptyList();
            }
            final Uint64 dpnid = currentdpnId;
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(NatUtil.waitForTransactionToComplete(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, writeOperTxn -> {
                        Optional<NaptSwitches> naptSwitches = NatUtil.getAllPrimaryNaptSwitches(dataBroker);
                        if (naptSwitches.isPresent()) {
                            Collection<RouterToNaptSwitch> routerToNaptSwCollection = naptSwitches.get()
                                    .getRouterToNaptSwitch().values();
                            List<RouterToNaptSwitch> routerToNaptSwitchList = new ArrayList<RouterToNaptSwitch>(
                                    routerToNaptSwCollection != null ? routerToNaptSwCollection
                                            : Collections.emptyList());
                            for (RouterToNaptSwitch routerToNaptSwitch : routerToNaptSwitchList) {
                                LOG.debug("NaptSwitch DPN: {}, DPN: {}",routerToNaptSwitch.getPrimarySwitchId(), dpnid);
                                if (dpnid.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                                    InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier =
                                            NatUtil.getRouterDpnId(routerToNaptSwitch.getRouterName(),
                                                    dpnid);
                                    Optional<DpnVpninterfacesList>
                                            optionalRouterDpnList = SingleTransactionDataBroker
                                            .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                                    routerDpnListIdentifier);
                                    if (optionalRouterDpnList.isPresent()) {
                                        LOG.info("Deleting the stale NAPT switch entry for DPN {} "
                                                + "from neutron-router-dpns", dpnid);
                                        writeOperTxn.delete(routerDpnListIdentifier);
                                    }
                                }
                            }
                        }
                    })));
            return futures;
        }
    }
}
