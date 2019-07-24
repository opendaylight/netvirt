/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator coordinator;
    private final ConcurrentMap<BigInteger, ClearRouterDpnsJob> clearDsJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<BigInteger, ScheduledFuture> nodeDeletedTasks
        = new ConcurrentHashMap<>();
    private final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
        .setNameFormat("nat-cleands-%d").build();
    private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(
        1, namedThreadFactory);

    @Inject
    public SnatNodeEventListener(final DataBroker dataBroker,
            final NatSwitchCache centralizedSwitchCache,
            final NatserviceConfig config,
            final JobCoordinator coordinator) {

        super(dataBroker,new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier
                .create(Nodes.class).child(Node.class)),
                Executors.newSingleThreadExecutor());
        this.centralizedSwitchCache = centralizedSwitchCache;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.coordinator = coordinator;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatserviceConfig.NatMode.Controller;
        }
    }

    @Override
    public void remove(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.key();
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
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
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        if (natMode == NatserviceConfig.NatMode.Conntrack) {
            LOG.info("Dpn added {}", dpnId);
            centralizedSwitchCache.addSwitch(dpnId);
        } else {
            cancelDsCleanUpForNodeDeletion(dpnId);
        }
    }

    public void scheduleDsCleanUpForNodeDeletion(BigInteger dpnId) {
        LOG.debug("Scheduling CleanUp DS Job for DpnId : {}", dpnId);
        nodeDeletedTasks.computeIfAbsent(dpnId, (key) -> {
            return scheduleService.schedule(() -> {
                ClearRouterDpnsJob clearDs = new ClearRouterDpnsJob(getDataBroker(), dpnId);
                coordinator.enqueueJob(clearDs.getJobKey(), clearDs,
                    SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                clearDsJobs.put(dpnId, clearDs);
                nodeDeletedTasks.remove(dpnId);
            }, 120, TimeUnit.SECONDS);
        });
    }

    public void cancelDsCleanUpForNodeDeletion(BigInteger dpnId) {
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

    private class ClearRouterDpnsJob implements Callable<List<ListenableFuture<Void>>> {

        private DataBroker dataBroker;
        private BigInteger currentdpnId;
        private volatile boolean cancelled = false;

        ClearRouterDpnsJob(DataBroker dataBroker, BigInteger currentdpnId) {
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
            final BigInteger dpnid = currentdpnId;
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> {
                    Optional<NaptSwitches> naptSwitches = NatUtil
                        .getAllPrimaryNaptSwitches(dataBroker);
                    if (naptSwitches.isPresent()) {
                        for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.get()
                            .getRouterToNaptSwitch()) {
                            LOG.debug("NaptSwitch DPN: {}, DPN: {}",
                                routerToNaptSwitch.getPrimarySwitchId(), dpnid);
                            if (dpnid.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                                InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier =
                                    NatUtil.getRouterDpnId(routerToNaptSwitch.getRouterName(),
                                        dpnid);
                                Optional<DpnVpninterfacesList>
                                    optionalRouterDpnList = confTx.read(routerDpnListIdentifier)
                                    .get();
                                if (optionalRouterDpnList.isPresent()) {
                                    LOG.info("Deleting the stale NAPT switch entry for DPN {} "
                                        + "from neutron-router-dpns", dpnid);
                                    confTx.delete(routerDpnListIdentifier);
                                }
                            }
                        }
                    }
                }));
            return futures;
        }
    }
}
