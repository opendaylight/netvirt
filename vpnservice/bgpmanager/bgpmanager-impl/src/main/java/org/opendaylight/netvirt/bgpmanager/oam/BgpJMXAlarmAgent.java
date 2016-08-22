/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

/**
 * Created by ECHIAPT on 7/21/2016.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
public class BgpJMXAlarmAgent {
    static Logger s_logger = LoggerFactory.getLogger(BgpJMXAlarmAgent.class);
    private MBeanServer mbs = null;
    private ObjectName alarmName = null;
    private static final String BEANNAME = "SDNC.FM:name=BgpControlPathAlarmBean";
    private static BgpNbrControlPathAlarm alarmBean= new BgpNbrControlPathAlarm();

    public BgpJMXAlarmAgent() {
        // Get the platform MBeanServer
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            alarmName = new ObjectName(BEANNAME);
        } catch (MalformedObjectNameException e) {
            s_logger.error("ObjectName instance creation failed for BEANAME {} : {}",BEANNAME, e);
        }
    }

    public void registerMbean() {
        // Unique identification of MBeans
        try {
            // Uniquely identify the MBeans and register them with the platform MBeanServer
            if(!mbs.isRegistered(alarmName)) {
                mbs.registerMBean(alarmBean, alarmName);
                s_logger.debug("Registered Mbean {} successfully", alarmName);
            }
        } catch(Exception e) {
            s_logger.error("Registeration failed for Mbean {} :{}", alarmName,e);
        }
    }

    public void unregisterMbean() {
        try {
            if(mbs.isRegistered(alarmName)) {
                mbs.unregisterMBean(alarmName);
                s_logger.debug("Unregistered Mbean {} successfully", alarmName);
            }
        } catch (Exception e) {
            s_logger.error("UnRegisteration failed for Mbean {} :{}", alarmName,e);
        }
    }

    public void invokeFMraisemethod(String alarmId,String text,String src) {
        try {
            mbs.invoke(alarmName, "raiseAlarm", new Object[]{alarmId, text, src}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
            s_logger.trace("Invoked raiseAlarm function for Mbean {} with source {}", BEANNAME, src);
        } catch (Exception e) {
            s_logger.error("Invoking raiseAlarm method failed for Mbean {} :{}", alarmName,e);
        }
    }

    public void invokeFMclearmethod(String alarmId,String text,String src) {
        try {
            mbs.invoke(alarmName, "clearAlarm", new Object[]{alarmId, text, src}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
            s_logger.trace("Invoked clearAlarm function for Mbean {} with source {}",BEANNAME,src);
        } catch (Exception e) {
            s_logger.error("Invoking clearAlarm method failed for Mbean {} :{}", alarmName,e);
        }
    }
}
