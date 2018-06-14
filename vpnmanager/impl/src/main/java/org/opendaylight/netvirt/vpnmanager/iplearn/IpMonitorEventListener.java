/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.iplearn;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.LivenessState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class listens for interface creation/removal/update in Configuration DS.
 * This is used to handle interfaces for base of-ports.
 */
@Singleton
public class IpMonitorEventListener implements AlivenessMonitorListener {
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitorEventListener.class);
    private final AlivenessMonitorService alivenessManager;
    private final DataBroker dataBroker;
    private final JobCoordinator jobCoordinator;

    @Inject
    public IpMonitorEventListener(DataBroker dataBroker, AlivenessMonitorService alivenessManager,
            JobCoordinator jobCoordinator) {
        this.alivenessManager = alivenessManager;
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
    }

    @Override
    public void onMonitorEvent(MonitorEvent notification) {
        Long monitorId = notification.getEventData().getMonitorId();
        MacEntry macEntry = AlivenessMonitorUtils.getMacEntryFromMonitorId(monitorId);
        if (macEntry == null) {
            LOG.debug("No MacEntry found associated with the monitor Id {}", monitorId);
            return;
        }
        LivenessState livenessState = notification.getEventData().getMonitorState();
        if (livenessState.equals(LivenessState.Down)) {
            String vpnName = macEntry.getVpnName();
            String learntIp = macEntry.getIpAddress().getHostAddress();
            LearntVpnVipToPort vpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker, vpnName, learntIp);
            if (vpnVipToPort != null && macEntry.getCreatedTime().equals(vpnVipToPort.getCreationTime())) {
                String jobKey = IpMonitoringHandler.buildIpMonitorJobKey(macEntry.getIpAddress().getHostAddress(),
                        macEntry.getVpnName());
                jobCoordinator.enqueueJob(jobKey,
                        new IpMonitorStopTask(macEntry, dataBroker, alivenessManager, Boolean.TRUE));
            }

        }
    }

}
