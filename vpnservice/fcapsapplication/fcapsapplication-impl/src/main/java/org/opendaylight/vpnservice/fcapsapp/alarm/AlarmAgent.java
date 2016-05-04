/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.fcapsapp.alarm;

import org.opendaylight.vpnservice.fcapsappjmx.ControlPathFailureAlarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class AlarmAgent {
    static Logger s_logger = LoggerFactory.getLogger(AlarmAgent.class);
    private MBeanServer mbs = null;
    private ObjectName alarmName = null;
    private static final String BEANNAME = "SDNC.FM:name=ControlPathFailureAlarmBean";
    private static ControlPathFailureAlarm alarmBean = new ControlPathFailureAlarm();

    /**
     * constructor get the instance of platform MBeanServer
     */
    public AlarmAgent() {
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            alarmName = new ObjectName(BEANNAME);
        } catch (MalformedObjectNameException e) {
            s_logger.error("ObjectName instance creation failed for BEANAME {} : {}",BEANNAME, e);
        }
    }

    /**
     * Method registers alarm mbean in platform MbeanServer
     */
    public void registerAlarmMbean() {
        try {
            if (!mbs.isRegistered(alarmName)) {
                mbs.registerMBean(alarmBean, alarmName);
                s_logger.info("Registered Mbean {} successfully", alarmName);
            }
        } catch (Exception e) {
            s_logger.error("Registeration failed for Mbean {} :{}", alarmName,e);
        }
    }

    /**
     * Method invoke raise alarm JMX API in platform MbeanServer with alarm details
     * @param alarmId
     *          alarm to be raised
     * @param text
     *          Additional details describing about the alarm on which dpnId and hostname
     * @param src
     *         Source of the alarm ex: dpnId=openflow:1
     *            the source node that caused this alarm
     */
    public void invokeFMraisemethod(String alarmId,String text,String src) {
        try {
            mbs.invoke(alarmName, "raiseAlarm", new Object[]{alarmId, text, src},
                    new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
            s_logger.debug("Invoked raiseAlarm function for Mbean {} with source {}", BEANNAME, src);
        } catch (Exception e) {
            s_logger.error("Invoking raiseAlarm method failed for Mbean {} :{}", alarmName,e);
        }
    }

    /**
     * Method invoke clear alarm JMX API in platform MbeanServer with alarm details
     * @param alarmId
     *          alarm to be cleared
     * @param text
     *          Additional details describing about the alarm on which dpnId and hostname
     * @param src
     *         Source of the alarm ex: dpn=openflow:1
     *            the source node that caused this alarm
     */
    public void invokeFMclearmethod(String alarmId,String text,String src) {
        try {
            mbs.invoke(alarmName, "clearAlarm", new Object[]{alarmId, text, src},
                    new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
            s_logger.debug("Invoked clearAlarm function for Mbean {} with source {}",BEANNAME,src);
        } catch (Exception e) {
            s_logger.error("Invoking clearAlarm method failed for Mbean {} :{}", alarmName,e);
        }
    }

    /**
     * Method gets the alarm details to be raised and construct the alarm objects
     * @param nodeId
     *         Source of the alarm dpnId
     * @param host
     *         Controller hostname
     */
    public void raiseControlPathAlarm(String nodeId,String host) {
        StringBuilder alarmText = new StringBuilder();
        StringBuilder source = new StringBuilder();

        if (host != null) {
            try {
                alarmText.append("OF Switch ").append(nodeId).append(" lost heart beat communication with controller ")
                        .append(host);
                source.append("Dpn=").append(nodeId);

                s_logger.debug("Raising ControlPathConnectionFailure alarm... alarmText {} source {} ", alarmText, source);
                //Invokes JMX raiseAlarm method
                invokeFMraisemethod("ControlPathConnectionFailure", alarmText.toString(), source.toString());
            } catch (Exception e) {
                s_logger.error("Exception before invoking raise method in jmx {}", e);
            }
        } else {
            s_logger.error("Received hostname is null");
        }
    }

    /**
     * Method gets the alarm details to be cleared and construct the alarm objects
     * @param nodeId
     *         Source of the alarm dpnId
     */
    public void clearControlPathAlarm(String nodeId) {
        StringBuilder source = new StringBuilder();

        try {
            source.append("Dpn=").append(nodeId);
            s_logger.debug("Clearing ControlPathConnectionFailure alarm of source {} ", source);
            //Invokes JMX clearAlarm method
            invokeFMclearmethod("ControlPathConnectionFailure", "OF Switch gained communication with controller",
                    source.toString());
        } catch (Exception e) {
            s_logger.error("Exception before invoking clear method jmx {}", e);
        }
    }
}