/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.snd;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ITMStatusMonitor implements ITMStatusMonitorMBean {


    private String serviceStatus;
    private static ITMStatusMonitor itmStatusMonitor = new ITMStatusMonitor();
    private static final String JMX_ITM_OBJ_NAME = "com.ericsson.sdncp.services.status:type=SvcItmService";
    private static final Logger log = LoggerFactory.getLogger(ITMStatusMonitor.class);

    private ITMStatusMonitor () {
    }

    public void registerMbean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName objName = new ObjectName(JMX_ITM_OBJ_NAME);
            mbs.registerMBean(itmStatusMonitor, objName);
            log.info("itm MXBean registration SUCCESSFUL!!! {}", JMX_ITM_OBJ_NAME);
        } catch (InstanceAlreadyExistsException iaeEx) {
            log.error("itm MXBean registration FAILED with InstanceAlreadyExistsException", iaeEx);
        } catch (MBeanRegistrationException mbrEx) {
            log.error("itm MXBean registration FAILED with MBeanRegistrationException", mbrEx);
        } catch (NotCompliantMBeanException ncmbEx) {
            log.error("itm MXBean registration FAILED with NotCompliantMBeanException", ncmbEx);
        } catch (MalformedObjectNameException monEx) {
            log.error("itm MXBean registration failed with MalformedObjectNameException", monEx);
        }
    }

    public static ITMStatusMonitor getInstance() {
        return itmStatusMonitor;
    }

    @Override
    public String acquireServiceStatus() {
        return serviceStatus;
    }

    public void reportStatus (String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }


}
