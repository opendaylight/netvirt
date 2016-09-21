/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventDataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Callback thats called whenever a Task performs certain actions because a DPN
 * has entered/exited a VPN footprint. For example, FIB flows being populated
 * when a new DPN becomes part of a VPN footprint.
 */
public class DpnEnterExitVpnCallback implements FutureCallback<List<Void>> {

    NotificationPublishService notificationPublishService;
    BigInteger dpnId;
    String vpnName;
    String rd;
    boolean entered;

    private static Logger LOG = LoggerFactory.getLogger(DpnEnterExitVpnCallback.class);

    public DpnEnterExitVpnCallback(NotificationPublishService notifPublishService, BigInteger dpnId, String vpnName,
                                 String rd, boolean entered) {
        this.notificationPublishService = notifPublishService;
        this.entered = entered;
        this.dpnId = dpnId;
        this.vpnName = vpnName;
        this.rd = rd;
    }

    /**
     * @param voids
     * This implies that all the future instances have returned success. -- TODO: Confirm this
     */
    @Override
    public void onSuccess(List<Void> voids) {
        if (entered) {
            publishAddNotification(dpnId, vpnName, rd);
        } else {
            publishRemoveNotification(dpnId, vpnName, rd);
        }
    }

    /**
     *
     * @param throwable
     * This method is used to handle failure callbacks.
     * If more retry needed, the retrycount is decremented and mainworker is executed again.
     * After retries completed, rollbackworker is executed.
     * If rollbackworker fails, this is a double-fault. Double fault is logged and ignored.
     */
    @Override
    public void onFailure(Throwable throwable) {
        LOG.warn("Job: failed with exception: {}", throwable.getStackTrace());
    }

    void publishAddNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("Sending notification for add dpn {} in vpn {} event ", dpnId, vpnName);
        AddEventData data = new AddEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        AddDpnEvent event = new AddDpnEventBuilder().setAddEventData(data).build();
        final ListenableFuture<? extends Object> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error in notifying listeners for add dpn {} in vpn {} event ", dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Successful in notifying listeners for add dpn {} in vpn {} event ", dpnId, vpnName);
            }
        });
    }

    void publishRemoveNotification(final BigInteger dpnId, final String vpnName, final String rd) {
        LOG.debug("Sending notification for remove dpn {} in vpn {} event ", dpnId, vpnName);
        RemoveEventData data = new RemoveEventDataBuilder().setVpnName(vpnName).setRd(rd).setDpnId(dpnId).build();
        RemoveDpnEvent event = new RemoveDpnEventBuilder().setRemoveEventData(data).build();
        final ListenableFuture<? extends Object> eventFuture = notificationPublishService.offerNotification(event);
        Futures.addCallback(eventFuture, new FutureCallback<Object>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.warn("Error in notifying listeners for remove dpn {} in vpn {} event ", dpnId, vpnName, error);
            }

            @Override
            public void onSuccess(Object arg) {
                LOG.trace("Successful in notifying listeners for remove dpn {} in vpn {} event ", dpnId, vpnName);
            }
        });
    }
}
