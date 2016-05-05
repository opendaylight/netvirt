/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsmanager.countermanager;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

public class PMRegistrationListener implements Runnable {
    private final static org.slf4j.Logger LOG = LoggerFactory.getLogger(PMRegistrationListener.class);
    static MBeanServer mbs = null;

    private static String DOMAIN = "SDNC.PM";
    public static HashSet<ObjectName> beanNames = new HashSet<ObjectName>();
    private BundleContext context = null;

    public PMRegistrationListener(BundleContext context){
        this.context=context;
    }

    /**
     * Gets register notification when a mbean is registered in platform Mbeanserver and checks if it is counter mbean and add it to the map.
     */
    public static class DelegateListener implements NotificationListener {
        public void handleNotification(Notification notification, Object obj) {
            if (notification instanceof MBeanServerNotification) {
                MBeanServerNotification msnotification =
                        (MBeanServerNotification) notification;
                String nType = msnotification.getType();
                ObjectName mbn = msnotification.getMBeanName();
                if (nType.equals("JMX.mbean.registered")) {
                    String mbean = mbn.toString();
                    if(mbean.contains(DOMAIN)) {
                        beanNames.add(mbn);
                        LOG.debug("Beans are " +beanNames);
                    }
                }
                if (nType.equals("JMX.mbean.unregistered")) {
                    if(mbn.toString().contains(DOMAIN)) {
                        beanNames.remove(mbn);
                        LOG.debug(mbn +" MBean has been unregistered");
                    }
                }
            }
        }
    }

    @Override
    public void run(){
        mbs = ManagementFactory.getPlatformMBeanServer();
        queryMbeans();
        DelegateListener delegateListener = new DelegateListener();
        ObjectName delegate = null;
        try {
            delegate = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        }
        NotificationFilterSupport filter = new NotificationFilterSupport();

        filter.enableType("JMX.mbean.registered");
        filter.enableType("JMX.mbean.unregistered");

        LOG.debug("Add PM Registeration Notification Listener");
        try {
            mbs.addNotificationListener(delegate, delegateListener, filter,null);
        }catch (InstanceNotFoundException e) {
            e.printStackTrace();
        }
        Poller poller = new Poller(this.context);
        poller.polling();
        waitforNotification();
    }

    /**
     * Prepovising case to handle all counter mbeans which are registered before the installation of framework bundle
     * Queries the platform Mbeanserver to retrieve registered counter mbean and add it to the map
     */
    public void queryMbeans() {
        Set<ObjectName> names =
                new TreeSet<ObjectName>(mbs.queryNames(null, null));
        LOG.debug("\nQueried MBeanServer for MBeans:");
        for (ObjectName name : names) {
            if(name.toString().contains(DOMAIN)){
                beanNames.add(name);
            }
        }
    }

    private void waitforNotification() {
        while(true){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}