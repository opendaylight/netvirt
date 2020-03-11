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
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorListener;
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
    private final DataBroker dataBroker;
    private final JobCoordinator jobCoordinator;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private final VpnUtil vpnUtil;

    @Inject
    public IpMonitorEventListener(DataBroker dataBroker, JobCoordinator jobCoordinator,
                                   AlivenessMonitorUtils alivenessMonitorUtils, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public void onMonitorEvent(MonitorEvent notification) {
        Long monitorId = notification.getEventData().getMonitorId().toJava();
        MacEntry macEntry = AlivenessMonitorUtils.getMacEntryFromMonitorId(monitorId);
        if (macEntry == null) {
            LOG.debug("No MacEntry found associated with the monitor Id {}", monitorId);
            return;
        }
        LivenessState livenessState = notification.getEventData().getMonitorState();
        if (livenessState.equals(LivenessState.Down)) {
            String vpnName = macEntry.getVpnName();
            String learntIp = macEntry.getIpAddress().getHostAddress();
            LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, learntIp);
            if (vpnVipToPort != null) {
                if (macEntry.getCreatedTime().equals(vpnVipToPort.getCreationTime())) {
                    String jobKey = VpnUtil.buildIpMonitorJobKey(macEntry.getIpAddress().getHostAddress(),
                            macEntry.getVpnName());
                    jobCoordinator.enqueueJob(jobKey, new IpMonitorStopTask(macEntry, dataBroker, Boolean.TRUE, vpnUtil,
                            alivenessMonitorUtils));
                } else {
                    LOG.error("onMonitorEvent: VpnPortIpPort creationTime {} mis-match with macEntry creationTime {}"
                                    + "for VpnName {} learntIp {}", vpnVipToPort.getCreationTime(),
                            macEntry.getCreatedTime(), vpnName, learntIp);
                }
            } else {
                LOG.error("onMonitorEvent: VpnPortIpPort is missing for VpnName {} learntIp {} monitorId {}",
                        vpnName, learntIp, monitorId);
            }

        }
    }

}
