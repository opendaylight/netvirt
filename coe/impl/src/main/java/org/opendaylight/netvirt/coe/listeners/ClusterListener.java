/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.coe.utils.CoeUtils;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.k8s.cluster.rev181127.K8sClustersInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.k8s.cluster.rev181127.k8s.clusters.info.K8sClusters;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ClusterListener extends AbstractSyncDataTreeChangeListener<K8sClusters> {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterListener.class);

    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner txRunner;
    private final CoeUtils coeUtils;

    @Inject
    public ClusterListener(final DataBroker dataBroker, JobCoordinator jobCoordinator, CoeUtils coeUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(K8sClustersInfo.class).child(K8sClusters.class));
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
        this.coeUtils = coeUtils;
    }

    @Override
    public void remove(@NonNull InstanceIdentifier<K8sClusters> instanceIdentifier, @NonNull K8sClusters clusters) {
        LOG.trace("K8 Cluster deleted {}", clusters);
        if (clusters.getClusterId() == null) {
            LOG.error("K8 cluster {} deleted with null cluster-id", clusters);
            return;
        }
        jobCoordinator.enqueueJob(clusters.getClusterId().getValue(),
                new K8ClusterRemoveWorker(txRunner, coeUtils, clusters));

    }

    @Override
    public void update(@NonNull InstanceIdentifier<K8sClusters> instanceIdentifier,
                       @NonNull K8sClusters originalClusters,
                       @NonNull final K8sClusters updatedClusters) {
        LOG.trace("K8 Cluster updated {} to {} . doing nothing", originalClusters, updatedClusters);
    }

    @Override
    public void add(@NonNull InstanceIdentifier<K8sClusters> instanceIdentifier, @NonNull K8sClusters clusters) {
        LOG.trace("K8 Cluster added {}", clusters);
        if (clusters.getClusterId() == null) {
            LOG.error("K8 cluster {} added with null cluster-id", clusters);
            return;
        }
        jobCoordinator.enqueueJob(clusters.getClusterId().getValue(),
                new K8ClusterAddWorker(txRunner, coeUtils, clusters));
    }

    private static class K8ClusterAddWorker implements Callable<List<ListenableFuture<Void>>> {
        private final K8sClusters clusters;
        private final ManagedNewTransactionRunner txRunner;
        private final CoeUtils coeUtils;

        K8ClusterAddWorker(ManagedNewTransactionRunner txRunner, CoeUtils coeUtils,
                           K8sClusters clusters) {
            this.clusters = clusters;
            this.txRunner = txRunner;
            this.coeUtils = coeUtils;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx ->  {
                LOG.debug("Creating VPN instance for k8cluster {}", clusters.getClusterId());
                coeUtils.createVpnInstance(clusters.getClusterId().getValue(), null, null, null,
                        false /*isL2Vpn*/, 0, IpVersionChoice.IPV4, tx);
            }));
            return futures;
        }
    }

    private static class K8ClusterRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private final K8sClusters clusters;
        private final ManagedNewTransactionRunner txRunner;
        private final CoeUtils coeUtils;

        K8ClusterRemoveWorker(ManagedNewTransactionRunner txRunner, CoeUtils coeUtils,
                              K8sClusters clusters) {
            this.clusters = clusters;
            this.txRunner = txRunner;
            this.coeUtils = coeUtils;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                LOG.debug("Deleting VPN instance for k8cluster {}", clusters.getClusterId());
                coeUtils.deleteVpnInstance(clusters.getClusterId().getValue(), tx);
            }));
            return futures;
        }
    }
}
