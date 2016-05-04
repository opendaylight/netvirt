/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsmanager.alarmmanager;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {
    static Logger s_logger = LoggerFactory.getLogger(Activator.class);
    private Runnable listener;
    private Thread listenerThread;

    public void start(BundleContext context) {
        s_logger.info("Starting alarmmanager bundle");
        AlarmNotificationListeners notificationListeners = new AlarmNotificationListeners(context);
        try {
            listener = notificationListeners;
            listenerThread = new Thread(listener);
            listenerThread.start();
        } catch (Exception e) {
            s_logger.error("Exception in alarm thread {}", e);
        }
    }

    public void stop(BundleContext context) {
        s_logger.info("Stopping alarmmanager bundle");
    }
}