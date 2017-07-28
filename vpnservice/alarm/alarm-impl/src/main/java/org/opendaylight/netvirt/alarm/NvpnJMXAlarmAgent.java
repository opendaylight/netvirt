/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.alarm;

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import jline.internal.Log;

public class NvpnJMXAlarmAgent {

    private static final String BEANNAME = "SDNC.FM:name=NeutronvpnControlPathAlarmBean";

    private static final String OP_RAISEALARM = "raiseAlarm";
    private static final String OP_CLEARALARM = "clearAlarm";

    public MBeanServer mbs = null;
    private ObjectName alarmName = null;
    public NvpnNbrControlPathAlarm alarmBean = new NvpnNbrControlPathAlarm();

    public NvpnJMXAlarmAgent() {
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            alarmName = new ObjectName(BEANNAME);
        } catch (MalformedObjectNameException e) {
            Log.info("ObjectName instance creation failed for BEANAME " + BEANNAME + " ," + e);
        }
    }

    public void invokeFMclearmethod(String alarmId, String text, String src, String details) {
        try {
            mbs.invoke(alarmName, OP_CLEARALARM, new Object[] {alarmId, text, src, details}, new String[] {
                    String.class.getName(), String.class.getName(), String.class.getName(), String.class.getName()});
            Log.debug("Invoked clearAlarm function for Mbean {} with source {}", BEANNAME, src, details);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
            Log.info("Invoking clearAlarm method failed for Mbean {} :{}", alarmName, e);
        }
    }

    public void invokeFMraisemethod(String alarmId, String text, String src, String details) {
        try {
            mbs.invoke(alarmName, OP_RAISEALARM, new Object[] {alarmId, text, src, details}, new String[] {
                    String.class.getName(), String.class.getName(), String.class.getName(), String.class.getName()});
            Log.debug("Invoked raiseAlarm function for Mbean {} with source {} for details {}",BEANNAME, src , details);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
            Log.info("Invoking raiseAlarm method failed for Mbean {} :{}", alarmName, e);
        }
    }

    public void registerMbean() {
        try {
            if (!mbs.isRegistered(alarmName)) {
                mbs.registerMBean(alarmBean, alarmName);
                Log.debug("Registered Mbean {} successfully", alarmName);
            }
        } catch (javax.management.InstanceAlreadyExistsException | javax.management.MBeanRegistrationException
                | javax.management.NotCompliantMBeanException e) {
            Log.info("Registeration failed for Mbean " + alarmName + " : " + e);
        }
    }

    public void unregisterMbean() {
        try {
            if (mbs.isRegistered(alarmName)) {
                mbs.unregisterMBean(alarmName);
                Log.debug("Unregistered Mbean {} successfully", alarmName);
            }
        } catch (InstanceNotFoundException | MBeanRegistrationException e) {
            Log.info("UnRegisteration failed for Mbean {} :{}", alarmName, e);
        }
    }


}
