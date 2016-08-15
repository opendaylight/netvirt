/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.statusanddiag;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanStatusMonitorJMX implements ElanStatusMonitor, ElanStatusMonitorMBean {

    private String serviceStatus;
    private static final String JMX_ELAN_OBJ_NAME = "com.ericsson.sdncp.services.status:type=SvcElanService";
    private static final Logger LOG = LoggerFactory.getLogger(ElanStatusMonitorJMX.class);

    public void init() {
        registerMbean();
    }

    public void registerMbean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName objName = new ObjectName(JMX_ELAN_OBJ_NAME);
            mbs.registerMBean(this, objName);
            LOG.info("MXBean registration SUCCESSFUL!!! {}", JMX_ELAN_OBJ_NAME);
        } catch (InstanceAlreadyExistsException iaeEx) {
            LOG.error("MXBean registration FAILED with InstanceAlreadyExistsException", iaeEx);
        } catch (MBeanRegistrationException mbrEx) {
            LOG.error("MXBean registration FAILED with MBeanRegistrationException", mbrEx);
        } catch (NotCompliantMBeanException ncmbEx) {
            LOG.error("MXBean registration FAILED with NotCompliantMBeanException", ncmbEx);
        } catch (MalformedObjectNameException monEx) {
            LOG.error("MXBean registration failed with MalformedObjectNameException", monEx);
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
