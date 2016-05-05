/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsmanager.countermanager;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import java.io.IOException;

public class Activator implements BundleActivator {
    private final static org.slf4j.Logger LOG = LoggerFactory.getLogger(Activator.class);
    private Runnable listener;
    private Thread listenerThread;

    public void start(BundleContext context) throws InstanceNotFoundException, MalformedObjectNameException, MBeanException, ReflectionException, IOException {
        LOG.info("Starting countermanager bundle ");
        PMRegistrationListener notificationListeners = new PMRegistrationListener(context);
        try {
            listener = notificationListeners;
            listenerThread = new Thread(listener);
            listenerThread.start();
        } catch (Exception e) {
            LOG.error("Exception in counter thread {}", e);
        }
    }

    public void stop(BundleContext context) {
        LOG.info("Stopping countermanager bundle ");
    }
}