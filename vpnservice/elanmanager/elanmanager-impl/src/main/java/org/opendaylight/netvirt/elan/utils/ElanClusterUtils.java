/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
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
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
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

    public static void runOnlyInOwnerNode(EntityOwnershipUtils entityOwnershipUtils, String jobDesc, Runnable job) {
        entityOwnershipUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME, DataStoreJobCoordinator.getInstance(), jobDesc, job);
    }

    public static void runOnlyInOwnerNode(EntityOwnershipUtils entityOwnershipUtils, String jobKey, String jobDesc,
            Callable<List<ListenableFuture<Void>>> job) {
        entityOwnershipUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME, DataStoreJobCoordinator.getInstance(), jobKey, jobDesc, job);
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
                        @Override
                        public void onSuccess(Optional<T> data) {
                            function.apply(data);
                            super.onSuccess(data);
                        }
                    }, MoreExecutors.directExecutor());

            return futures;
        }, ElanConstants.JOB_MAX_RETRIES);
    }
}
