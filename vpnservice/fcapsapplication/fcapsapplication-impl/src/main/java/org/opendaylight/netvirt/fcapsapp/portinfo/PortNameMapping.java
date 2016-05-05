/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.portinfo;

import java.lang.String;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.management.*;
import java.lang.management.ManagementFactory;

public class PortNameMapping implements PortNameMappingMBean {

    private static final Logger LOG = LoggerFactory.getLogger(PortNameMapping.class);

    private static Map<String,String> portNameToPortIdMap = new HashMap<String,String>();

    @Override
    public Map<String,String> getPortIdtoPortNameMap() {
        return portNameToPortIdMap;
    }

    @Override
    public String getPortName(String portId){
        return portNameToPortIdMap.get(portId);
    }

    public static void updatePortMap(String portName,String portId,String status) {
        if (status.equals("ADD")){
            portNameToPortIdMap.put(portId,portName);
            LOG.debug("PortId {} : portName {} added",portId,portName);
        } else if(status.equals("DELETE")){
            portNameToPortIdMap.remove(portId);
            LOG.debug("PortId {} : portName {} removed",portId,portName);
        }
    }

    public static void registerPortMappingBean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName mbeanName = null;
        String BEANNAME = "Ports:type=PortNameMapping";

        try {
            mbeanName = new ObjectName(BEANNAME);
        } catch (MalformedObjectNameException e) {
            LOG.error("ObjectName instance creation failed for BEANAME {} : {}", BEANNAME, e);

        }
        try {
            if (!mbs.isRegistered(mbeanName)) {
                mbs.registerMBean(new PortNameMapping(), mbeanName);
                LOG.debug("Registered Mbean {} successfully", mbeanName);
            }

        } catch (Exception e) {
            LOG.error("Registeration failed for Mbean {} :{}", mbeanName, e);
        }
    }
}