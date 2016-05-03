/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsmanager.alarmmanager;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.vpnservice.fcapsmanager.AlarmServiceFacade;

public class AlarmNotificationListeners implements Runnable {
    static Logger s_logger = LoggerFactory.getLogger(AlarmNotificationListeners.class);
    private MBeanServer mbs = null;
    private static String DOMAIN = "SDNC.FM";

    private final DelegateListener delegateListener = new DelegateListener();
    private BundleContext context = null;

    public AlarmNotificationListeners(BundleContext context) {
        this.context=context;
    }

    /**
     * Platform dependent bundle injects its handle and it is retrieved in the method
     */
    private AlarmServiceFacade getAlarmServiceSPI (){
        AlarmServiceFacade service =null;
        if(context != null) {
            try {
                ServiceReference<?> serviceReference = context.
                        getServiceReference(AlarmServiceFacade.class.getName());
                service = (AlarmServiceFacade) context.
                        getService(serviceReference);
            }catch (NullPointerException ex){
                service = null;
            }catch (Exception e){
                s_logger.error("Exception {} occurred in getting AlarmServiceSPI",e);
            }
        }
        return service;
    }

    /**
     * Gets register notification when a mbean is registered in platform Mbeanserver and checks if it is alarm mbean and add attribute notification listener to it.
     * Gets attribute notification when alarm mbean is updated by the application.
     */
    public class DelegateListener implements NotificationListener {
        public void handleNotification(Notification notification, Object obj) {
            if (notification instanceof MBeanServerNotification) {
                MBeanServerNotification msnotification =
                        (MBeanServerNotification) notification;
                String nType = msnotification.getType();
                ObjectName mbn = msnotification.getMBeanName();

                if (nType.equals("JMX.mbean.registered")) {
                    if (mbn.toString().contains(DOMAIN)) {
                        s_logger.debug("Received registeration of Mbean "+mbn);
                        try {
                            mbs.addNotificationListener(mbn,delegateListener, null, null);
                            s_logger.debug("Added attribute notification listener for Mbean "+ mbn);
                        } catch (InstanceNotFoundException e) {
                            s_logger.error("Exception while adding attribute notification of mbean {}", e);
                        }
                    }
                }

                if (nType.equals("JMX.mbean.unregistered")) {
                    if (mbn.toString().contains(DOMAIN)) {
                        s_logger.debug("Time: " + msnotification.getTimeStamp() + "MBean " + msnotification.getMBeanName()+" unregistered successfully");
                    }
                }
            }
            else if (notification instanceof AttributeChangeNotification) {
                AttributeChangeNotification acn =
                        (AttributeChangeNotification) notification;

                s_logger.debug("Received attribute notification of Mbean: "
                        + notification.getSource()
                        + " for attribute:" + acn.getAttributeName() );

                if(acn.getAttributeName().toString().equals("raiseAlarmObject")){

                    String value=acn.getNewValue().toString();
                    value = value.replace(value.charAt(0), ' ');
                    value = value.replace(value.charAt(value.lastIndexOf("]")), ' ');

                    String[] args =value.split(",");
                    s_logger.debug("Receive attribute value :"+args[0].trim()+args[1].trim()+args[2].trim());
                    if(getAlarmServiceSPI() != null ) {
                        getAlarmServiceSPI().raiseAlarm(args[0].trim(),args[1].trim(),args[2].trim());
                    } else {
                        s_logger.debug("Alarm service not available");
                    }

                } else if(acn.getAttributeName().toString().equals("clearAlarmObject")){

                    String value=acn.getNewValue().toString();
                    value = value.replace(value.charAt(0), ' ');
                    value = value.replace(value.charAt(value.lastIndexOf("]")), ' ');

                    String[] args =value.split(",");
                    s_logger.debug("Receive attribute value :"+args[0].trim()+args[1].trim()+args[2].trim());
                    if(getAlarmServiceSPI() != null )
                        getAlarmServiceSPI().clearAlarm(args[0].trim(), args[1].trim(), args[2].trim());
                    else
                        s_logger.debug("Alarm service not available");
                }
            }
        }
    }

    /**
     * Gets the platform MBeanServer instance and registers to get notification whenever alarm mbean is registered in the mbeanserver
     */
    @Override
    public void run() {
        mbs = ManagementFactory.getPlatformMBeanServer();

        queryMbeans();

        ObjectName delegate = null;
        try {
            delegate = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        }
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType("JMX.mbean.registered");
        filter.enableType("JMX.mbean.unregistered");

        try {
            mbs.addNotificationListener(delegate, delegateListener, filter, null);
            s_logger.debug("Added registeration listener for Mbean {}",delegate);
        } catch (InstanceNotFoundException e) {
            s_logger.error("Failed to add registeration listener {}", e);
        }

        waitForNotification();
    }

    /**
     *  Pre-provisioning case to handle all alarm mbeans which are registered before installation of framework bundle
     *  Queries the platform Mbeanserver to retrieve registered alarm mbean and add attribute notification listener to it
     */
    public void queryMbeans() {

        Set<ObjectName> names =
                new TreeSet<ObjectName>(mbs.queryNames(null, null));
        s_logger.debug("Queried MBeanServer for MBeans:");
        for (ObjectName beanName : names) {
            if(beanName.toString().contains(DOMAIN)){
                try {
                    mbs.addNotificationListener(beanName,delegateListener, null, null);
                    s_logger.debug("Added attribute notification listener for Mbean "+ beanName);
                } catch (InstanceNotFoundException e) {
                    s_logger.error("Failed to add attribute notification for Mbean {}", e);
                }
            }
        }
    }
    public void waitForNotification() {
        while(true){
            try {
                Thread.sleep(50);
            }
            catch(Exception ex){
                ex.printStackTrace();
            }
        }
    }
}