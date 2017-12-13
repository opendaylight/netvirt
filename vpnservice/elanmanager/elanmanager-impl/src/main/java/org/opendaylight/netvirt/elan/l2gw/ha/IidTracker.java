/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAJobScheduler;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class IidTracker {

    private final Map<InstanceIdentifier, ListenableFuture<Void>> configInProgress = new ConcurrentHashMap<>();
    private final Map<InstanceIdentifier, ListenableFuture<Void>> opInProgress = new ConcurrentHashMap<>();

    @Inject
    public IidTracker() {
    }

    private void trackIid(InstanceIdentifier iid,
                          ListenableFuture<Void> ft,
                          Map<InstanceIdentifier, ListenableFuture<Void>> iidFutures) {
        iidFutures.put(iid, ft);
        Futures.addCallback(ft, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                iidFutures.remove(iid);
            }

            @Override
            public void onFailure(Throwable throwable) {
                iidFutures.remove(iid);
            }
        });
    }

    public synchronized void markUpdateInProgress(LogicalDatastoreType logicalDatastoreType,
                                                  InstanceIdentifier iid,
                                                  ListenableFuture<Void> ft) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            trackIid(iid, ft, configInProgress);
        } else {
            trackIid(iid, ft, opInProgress);
        }
    }

    public synchronized boolean isInProgress(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier iid) {

        ListenableFuture<Void> ft = getInprogressFt(logicalDatastoreType, iid);
        return ft != null && !ft.isDone() && !ft.isCancelled();
    }

    public synchronized boolean addCallbackIfInProgress(LogicalDatastoreType logicalDatastoreType,
                                                        InstanceIdentifier iid,
                                                        Runnable runnable) {

        ListenableFuture<Void> ft = getInprogressFt(logicalDatastoreType, iid);
        if (ft != null && !ft.isDone() && !ft.isCancelled()) {
            Futures.addCallback(ft, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    HAJobScheduler.getInstance().submitJob(runnable);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    HAJobScheduler.getInstance().submitJob(runnable);
                }
            });
            return true;
        }
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            configInProgress.remove(iid);
        } else {
            opInProgress.remove(iid);
        }
        return false;
    }

    public ListenableFuture<Void> getInprogressFt(LogicalDatastoreType logicalDatastoreType,
                                                  InstanceIdentifier iid) {
        if (logicalDatastoreType == LogicalDatastoreType.CONFIGURATION) {
            return configInProgress.get(iid);
        } else {
            return opInProgress.get(iid);
        }
    }
}
