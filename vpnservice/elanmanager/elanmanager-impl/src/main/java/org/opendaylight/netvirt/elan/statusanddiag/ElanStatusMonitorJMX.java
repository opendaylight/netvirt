/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.statusanddiag;

import java.lang.management.ManagementFactory;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanStatusMonitorJMX implements ElanStatusMonitor, ElanStatusMonitorMBean {

    private String serviceStatus;
    private static final String JMX_ELAN_OBJ_NAME = "org.opendaylight.netvirt.elan:type=SvcElanService";
    private static final Logger LOG = LoggerFactory.getLogger(ElanStatusMonitorJMX.class);
    private static final MBeanServer MBS = ManagementFactory.getPlatformMBeanServer();

    public void init() throws Exception {
        registerMbean();
    }

    public void registerMbean() throws JMException {
        ObjectName objName = new ObjectName(JMX_ELAN_OBJ_NAME);
        MBS.registerMBean(this, objName);
        LOG.info("ElanStatusMonitor MXBean successfully registered {}", JMX_ELAN_OBJ_NAME);
    }

    public void close() {
        try {
            ObjectName objName = new ObjectName(JMX_ELAN_OBJ_NAME);
            if (MBS.isRegistered(objName)) {
                MBS.unregisterMBean(objName);
                LOG.info("Successfully unregistered MXBean {}", JMX_ELAN_OBJ_NAME);
            }
        } catch (JMException e) {
            LOG.error("Error unregistering MXBean " + JMX_ELAN_OBJ_NAME, e);
        }
    }

    @Override
    public String acquireServiceStatus() {
        return serviceStatus;
    }

    @Override
    @SuppressWarnings("hiding")
    public void reportStatus(String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }
}
