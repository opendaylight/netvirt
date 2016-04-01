/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils.clustering;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipState;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class ClusteringUtils {

    public static ListenableFuture<Boolean> checkNodeEntityOwner(EntityOwnershipService entityOwnershipService,
            String entityType, String nodeId) {
        return checkNodeEntityOwner(entityOwnershipService, new Entity(entityType, nodeId),
                SystemPropertyReader.Cluster.getSleepTimeBetweenRetries(), SystemPropertyReader.Cluster.getMaxRetries());
    }

    public static ListenableFuture<Boolean> checkNodeEntityOwner(EntityOwnershipService entityOwnershipService,
            String entityType, YangInstanceIdentifier nodeId) {
        return checkNodeEntityOwner(entityOwnershipService, new Entity(entityType, nodeId),
                SystemPropertyReader.Cluster.getSleepTimeBetweenRetries(), SystemPropertyReader.Cluster.getMaxRetries());
    }

    public static ListenableFuture<Boolean> checkNodeEntityOwner(EntityOwnershipService entityOwnershipService,
            Entity entity, long sleepBetweenRetries, int maxRetries) {
        SettableFuture<Boolean> checkNodeEntityfuture = SettableFuture.create();
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        CheckEntityOwnerTask checkEntityOwnerTask = new CheckEntityOwnerTask(entityOwnershipService, entity,
                checkNodeEntityfuture, sleepBetweenRetries, maxRetries);
        dataStoreCoordinator.enqueueJob(entityOwnershipService.toString(), checkEntityOwnerTask);
        return checkNodeEntityfuture;
    }

    private static class CheckEntityOwnerTask implements Callable<List<ListenableFuture<Void>>> {
        EntityOwnershipService entityOwnershipService;
        Entity entity;
        SettableFuture<Boolean> checkNodeEntityfuture;
        long sleepBetweenRetries;
        int retries;

        public CheckEntityOwnerTask(EntityOwnershipService entityOwnershipService, Entity entity,
                SettableFuture<Boolean> checkNodeEntityfuture, long sleepBetweenRetries, int retries) {
            this.entityOwnershipService = entityOwnershipService;
            this.entity = entity;
            this.checkNodeEntityfuture = checkNodeEntityfuture;
            this.sleepBetweenRetries = sleepBetweenRetries;
            this.retries = retries;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            while (retries > 0) {
                retries = retries - 1;
                Optional<EntityOwnershipState> entityState = entityOwnershipService.getOwnershipState(entity);
                if (entityState.isPresent()) {
                    EntityOwnershipState entityOwnershipState = entityState.get();
                    if (entityOwnershipState.hasOwner()) {
                        checkNodeEntityfuture.set(entityOwnershipState.isOwner());
                        return getResultFuture();
                    }
                }
                Thread.sleep(sleepBetweenRetries);
            }
            checkNodeEntityfuture.setException(new EntityOwnerNotPresentException("Entity Owner Not Present"));
            return getResultFuture();
        }

        private List<ListenableFuture<Void>> getResultFuture() {
            ListenableFuture<Void> future = Futures.immediateFuture(null);
            ArrayList<ListenableFuture<Void>> futureList = Lists.newArrayList();
            futureList.add(future);
            return futureList;
        }
    }
}
