/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.genius.utils.clustering.ClusteringUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.utils.SettableFutureCallback;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElanClusterUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanClusterUtils.class);

    private ElanClusterUtils() {
    }

    public static void runOnlyInLeaderNode(EntityOwnershipService entityOwnershipService, Runnable job) {
        runOnlyInLeaderNode(entityOwnershipService, job, "");
    }

    public static void runOnlyInLeaderNode(EntityOwnershipService entityOwnershipService, final Runnable job,
                                           final String jobDescription) {
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
            entityOwnershipService, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    job.run();
                } else {
                    LOG.trace("job is not run as i m not cluster owner desc :{} ", jobDescription);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Failed to identity cluster owner ", error);
            }
        }, MoreExecutors.directExecutor());
    }

    public static void runOnlyInLeaderNode(EntityOwnershipService entityOwnershipService, String jobKey,
                                           Callable<List<ListenableFuture<Void>>> dataStoreJob) {
        runOnlyInLeaderNode(entityOwnershipService, jobKey, "", dataStoreJob);
    }

    public static void runOnlyInLeaderNode(EntityOwnershipService entityOwnershipService, final String jobKey,
                                           final String jobDescription,
                                           final Callable<List<ListenableFuture<Void>>> dataStoreJob) {
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
            entityOwnershipService, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    LOG.trace("scheduling job {} ", jobDescription);
                    DataStoreJobCoordinator.getInstance().enqueueJob(jobKey, dataStoreJob,
                        SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                } else {
                    LOG.trace("job is not run as i m not cluster owner desc :{} ", jobDescription);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Failed to identity cluster owner for job " + jobDescription, error);
            }
        }, MoreExecutors.directExecutor());
    }

    public static <T extends DataObject> void asyncReadAndExecute(final DataBroker broker,
                                                                  final LogicalDatastoreType datastoreType,
                                                                  final InstanceIdentifier<T> iid,
                                                                  final String jobKey,
                                                                  final Function<Optional<T>, Void> function) {
        DataStoreJobCoordinator.getInstance().enqueueJob(jobKey, () -> {
            SettableFuture settableFuture = SettableFuture.create();
            List<ListenableFuture<Void>> futures = Collections.singletonList(settableFuture);

            ReadWriteTransaction tx = broker.newReadWriteTransaction();

            Futures.addCallback(tx.read(datastoreType, iid),
                                new SettableFutureCallback<Optional<T>>(settableFuture) {
                        public void onSuccess(Optional<T> data) {
                            function.apply(data);
                            super.onSuccess(data);
                        }
                    }, MoreExecutors.directExecutor());

            return futures;
        }, ElanConstants.JOB_MAX_RETRIES);
    }
}
