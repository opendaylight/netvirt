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
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvpnJMXAlarmAgent {

    private static final String BEANNAME = "SDNC.FM:name=NeutronvpnControlPathAlarmBean";

    public static final String OP_RAISEALARM = "raiseAlarm";
    public static final String OP_CLEARALARM = "clearAlarm";
    private static final Logger LOG = LoggerFactory.getLogger(NvpnJMXAlarmAgent.class);

    private MBeanServer mbs;
    private ObjectName alarmName = null;
    private final NvpnNbrControlPathAlarm alarmBean = new NvpnNbrControlPathAlarm();

    public NvpnJMXAlarmAgent() {
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            alarmName = new ObjectName(BEANNAME);
        } catch (MalformedObjectNameException e) {
            LOG.error("ObjectName instance creation failed for BEANAME {}", BEANNAME, e);
        }
    }

    public void invokeClearAlarmBean(String alarmId, String text, String src, String details) {
        try {
            mbs.invoke(alarmName, OP_CLEARALARM, new Object[] {alarmId, text, src, details}, new String[] {
                    String.class.getName(), String.class.getName(), String.class.getName(), String.class.getName()});
            LOG.debug("Invoked clearAlarm function for Mbean {} with source {} for details {}", BEANNAME, src, details);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
            LOG.error("Invoking clearAlarm method failed for Mbean {}", alarmName);
        }
    }

    public void invokeFMraisemethod(String alarmId, String text, String src, String details) {
        try {
            mbs.invoke(alarmName, OP_RAISEALARM, new Object[] {alarmId, text, src, details}, new String[] {
                    String.class.getName(), String.class.getName(), String.class.getName(), String.class.getName()});
            LOG.debug("Invoked raiseAlarm function for Mbean {} with source {} for details {}",BEANNAME, src , details);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
            LOG.error("Invoking raiseAlarm method failed for Mbean {}", alarmName);
        }
    }

    public void registerMbean() {
        try {
            if (!mbs.isRegistered(alarmName)) {
                mbs.registerMBean(alarmBean, alarmName);
                LOG.debug("Registered Mbean {} successfully", alarmName);
            }
        } catch (javax.management.InstanceAlreadyExistsException | javax.management.MBeanRegistrationException
                | javax.management.NotCompliantMBeanException e) {
            LOG.info("Registeration failed for Mbean {}", alarmName, e);
        }
        try {
            mbs.addNotificationListener(alarmName, alarmBean, null, null);
        } catch (InstanceNotFoundException e2) {
            LOG.error("Registeration of listener failed for Mbean {}", alarmName);
        }
    }

    public void unregisterMbean() {
        try {
            mbs.removeNotificationListener(alarmName, alarmBean);
        } catch (InstanceNotFoundException | ListenerNotFoundException e1) {
            LOG.error("unregisteration of listener failed for Mbean {}", alarmName);
        }
        try {
            if (mbs.isRegistered(alarmName)) {
                mbs.unregisterMBean(alarmName);
                LOG.debug("Unregistered Mbean {} successfully", alarmName);
            }
        } catch (InstanceNotFoundException | MBeanRegistrationException e2) {
            LOG.error("UnRegisteration failed for Mbean {}", alarmName);
        }
    }

    public NvpnNbrControlPathAlarm getAlarmBean() {
        return alarmBean;
    }


}
