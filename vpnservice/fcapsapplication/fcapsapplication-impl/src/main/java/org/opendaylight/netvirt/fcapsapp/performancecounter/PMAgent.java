/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.performancecounter;


import org.opendaylight.netvirt.fcapsappjmx.NumberOfOFPorts;
import org.opendaylight.netvirt.fcapsappjmx.NumberOfOFSwitchCounter;
import org.opendaylight.netvirt.fcapsappjmx.PacketInCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.String;
import java.lang.management.ManagementFactory;
import java.util.Map;

public class PMAgent {
    private static Logger s_logger = LoggerFactory.getLogger(PMAgent.class);
    private MBeanServer mbs = null;
    private ObjectName switch_mbeanName = null;
    private ObjectName port_mbeanName = null;
    private ObjectName pktIn_mbeanName = null;
    private static final String SWITCH_BEANNAME = "SDNC.PM:type=NumberOfOFSwitchCounter";
    private static final String PORTS_BEANNAME = "SDNC.PM:type=NumberOfOFPortsCounter";
    private static final String PKTIN_BEANNAME = "SDNC.PM:type=InjectedPacketInCounter";

    private static NumberOfOFSwitchCounter switchCounterBean = new NumberOfOFSwitchCounter();
    private static NumberOfOFPorts PortcounterBean = new NumberOfOFPorts();
    private static PacketInCounter packetInCounter = new PacketInCounter();

    public PMAgent() {
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            switch_mbeanName = new ObjectName(SWITCH_BEANNAME);
            port_mbeanName = new ObjectName(PORTS_BEANNAME);
            pktIn_mbeanName = new ObjectName(PKTIN_BEANNAME);
        } catch (MalformedObjectNameException e) {
            s_logger.error("ObjectName instance creation failed for BEANAME {}", e);

        }
    }

    public void registerMbeanForEFS() {
        try {
            if (!mbs.isRegistered(switch_mbeanName)) {
                mbs.registerMBean(switchCounterBean, switch_mbeanName);
                s_logger.info("Registered Mbean {} successfully", switch_mbeanName);
            }

        } catch (Exception e) {
            s_logger.error("Registeration failed for Mbean {} :{}", switch_mbeanName, e);
        }
    }

    public void registerMbeanForPorts() {
        try {
            if (!mbs.isRegistered(port_mbeanName)) {
                mbs.registerMBean(PortcounterBean, port_mbeanName);
                s_logger.info("Registered Mbean {} successfully", port_mbeanName);
            }
        } catch (Exception e) {
            s_logger.error("Registeration failed for Mbean {} :{}", port_mbeanName, e);
        }
    }

    public void registerMbeanForPacketIn() {
        try {
            if (!mbs.isRegistered(pktIn_mbeanName)) {
                mbs.registerMBean(packetInCounter,pktIn_mbeanName);
                s_logger.info("Registered Mbean {} successfully",pktIn_mbeanName );
            }
        } catch (Exception e) {
            s_logger.error("Registeration failed for Mbean {} :{}",pktIn_mbeanName , e);
        }
    }

    public void connectToPMAgent(Map map) {
        switchCounterBean.updateCounter(map);
    }

    public void connectToPMAgentForNOOfPorts(Map map) {
        PortcounterBean.updateCounter(map);
    }

    public void sendPacketInCounterUpdate(Map map){
        packetInCounter.updateCounter(map);
    }
}
