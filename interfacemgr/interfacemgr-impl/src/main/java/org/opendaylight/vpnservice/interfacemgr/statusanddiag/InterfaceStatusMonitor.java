/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.statusanddiag;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceStatusMonitor implements InterfaceStatusMonitorMBean{

    private String serviceStatus;
    private static InterfaceStatusMonitor interfaceStatusMonitor = new InterfaceStatusMonitor();
    private static final String JMX_INTERFACE_OBJ_NAME = "com.ericsson.sdncp.services.status:type=SvcInterfaceService";
    private static final Logger log = LoggerFactory.getLogger(InterfaceStatusMonitor.class);

    private InterfaceStatusMonitor () {
    }

    public void registerMbean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName objName = new ObjectName(JMX_INTERFACE_OBJ_NAME);
            log.debug("MXBean Object-Name framed");
            mbs.registerMBean(interfaceStatusMonitor, objName);
            log.info("MXBean registration SUCCESSFUL!!! {}", JMX_INTERFACE_OBJ_NAME);
        } catch (InstanceAlreadyExistsException iaeEx) {
            log.error("MXBean registration FAILED with InstanceAlreadyExistsException", iaeEx);
        } catch (MBeanRegistrationException mbrEx) {
            log.error("MXBean registration FAILED with MBeanRegistrationException", mbrEx);
        } catch (NotCompliantMBeanException ncmbEx) {
            log.error("MXBean registration FAILED with NotCompliantMBeanException", ncmbEx);
        } catch (MalformedObjectNameException monEx) {
            log.error("MXBean registration failed with MalformedObjectNameException", monEx);
        }
    }

    public static InterfaceStatusMonitor getInstance() {
        return interfaceStatusMonitor;
    }

    @Override
    public String acquireServiceStatus() {
        return serviceStatus;
    }

    public void reportStatus (String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }
}
