/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.vpnservice.utils.clustering.ClusteringUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class ElanClusterUtils {
    private static final Logger logger = LoggerFactory.getLogger(ElanClusterUtils.class);

    private static EntityOwnershipService eos;

    static DataStoreJobCoordinator dataStoreJobCoordinator;

    public static void setDataStoreJobCoordinator(DataStoreJobCoordinator ds) {
        dataStoreJobCoordinator = ds;
    }

    public static void setEntityOwnershipService(EntityOwnershipService entityOwnershipService) {
        eos = entityOwnershipService;
    }

    public static void runOnlyInLeaderNode(Runnable job) {
        runOnlyInLeaderNode(job, "");
    }

    public static void runOnlyInLeaderNode(final Runnable job, final String jobDescription) {
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                eos, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    job.run();
                } else {
                    logger.trace("job is not run as i m not cluster owner desc :{} ", jobDescription);
                }
            }
            @Override
            public void onFailure(Throwable error) {
                logger.error("Failed to identity cluster owner ", error);
            }
        });
    }

    public static void runOnlyInLeaderNode(String jobKey, Callable<List<ListenableFuture<Void>>> dataStoreJob) {
        runOnlyInLeaderNode(jobKey, "", dataStoreJob);
    }

    public static void runOnlyInLeaderNode(final String jobKey, final String jobDescription,
                                           final Callable<List<ListenableFuture<Void>>> dataStoreJob) {
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                eos, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    logger.trace("scheduling job {} ", jobDescription);
                    dataStoreJobCoordinator.enqueueJob(jobKey, dataStoreJob,
                            SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                } else {
                    logger.trace("job is not run as i m not cluster owner desc :{} ", jobDescription);
                }
            }
            @Override
            public void onFailure(Throwable error) {
                logger.error("Failed to identity cluster owner for job "+jobDescription, error);
            }
        });
    }
}
