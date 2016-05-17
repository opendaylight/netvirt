/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.performancecounter;


import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.opendaylight.netvirt.fcapsappjmx.NumberOfOFPorts;
import org.opendaylight.netvirt.fcapsappjmx.NumberOfOFSwitchCounter;
import org.opendaylight.netvirt.fcapsappjmx.PacketInCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMAgent {
    private static Logger s_logger = LoggerFactory.getLogger(PMAgent.class);
    private MBeanServer mbs = null;
    private ObjectName switchBeanName = null;
    private ObjectName portBeanName = null;
    private ObjectName pktInBeanName = null;
    private static final String SWITCH_BEANNAME = "SDNC.PM:type=NumberOfOFSwitchCounter";
    private static final String PORTS_BEANNAME = "SDNC.PM:type=NumberOfOFPortsCounter";
    private static final String PKTIN_BEANNAME = "SDNC.PM:type=InjectedPacketInCounter";

    private NumberOfOFSwitchCounter switchCounterBean = new NumberOfOFSwitchCounter();
    private NumberOfOFPorts PortcounterBean = new NumberOfOFPorts();
    private PacketInCounter packetInCounter = new PacketInCounter();

    public PMAgent() {
        mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            switchBeanName = new ObjectName(SWITCH_BEANNAME);
            portBeanName = new ObjectName(PORTS_BEANNAME);
            pktInBeanName = new ObjectName(PKTIN_BEANNAME);
        } catch (MalformedObjectNameException e) {
            s_logger.error("ObjectName instance creation failed for BEANAME {}", e);
        }
    }

    /**
     * Register MBeans.
     */
    public void registerMBean() {
        try {
            if (!mbs.isRegistered(switchBeanName)) {
                mbs.registerMBean(switchCounterBean, switchBeanName);
                s_logger.info("Registered Mbean {} successfully", switchBeanName);
            }
            if (!mbs.isRegistered(portBeanName)) {
                mbs.registerMBean(PortcounterBean, portBeanName);
                s_logger.info("Registered Mbean {} successfully", portBeanName);
            }
            if (!mbs.isRegistered(pktInBeanName)) {
                mbs.registerMBean(packetInCounter,pktInBeanName);
                s_logger.info("Registered Mbean {} successfully",pktInBeanName );
            }
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
            s_logger.error("Registeration failed for Mbean :{}", e);
        }
    }


    /**
     * Unregister MBeans.
     */
    public void unregisterMBean() {
        try {
            if (!mbs.isRegistered(switchBeanName)) {
                mbs.unregisterMBean(switchBeanName);
            }
            if (!mbs.isRegistered(portBeanName)) {
                mbs.unregisterMBean(portBeanName);
            }
            if (!mbs.isRegistered(pktInBeanName)) {
                mbs.unregisterMBean(pktInBeanName);
            }
        } catch (MBeanRegistrationException | InstanceNotFoundException e) {
            s_logger.error("Failed to unregister Mbean :{}", e);
        }
    }

    public void connectToPMAgent(Map<String, String> map) {
        switchCounterBean.updateCounter(map);
    }

    public void connectToPMAgentForNOOfPorts(Map<String, String> map) {
        PortcounterBean.updateCounter(map);
    }

    public void sendPacketInCounterUpdate(Map<String, String> map){
        packetInCounter.updateCounter(map);
    }
}
