/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.LivenessState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class listens for interface creation/removal/update in Configuration DS.
 * This is used to handle interfaces for base of-ports.
 */
public class ArpCacheListener implements AlivenessMonitorListener {
    private static final Logger LOG = LoggerFactory.getLogger(ArpCacheListener.class);
    private AlivenessMonitorService alivenessManager;
    private DataBroker dataBroker;
    public ArpCacheListener(DataBroker dataBroker, AlivenessMonitorService alivenessManager) {
        this.alivenessManager = alivenessManager;
        this.dataBroker = dataBroker;
    }

    @Override
    public void onMonitorEvent(MonitorEvent notification) {
        Long monitorId = notification.getEventData().getMonitorId();
        MacEntry macEntry = AlivenessMonitorUtils.getInterfaceFromMonitorId(monitorId);
        LivenessState livenessState = notification.getEventData().getMonitorState();
        if(livenessState.equals(LivenessState.Down)) {
            AlivenessMonitorUtils.stopArpMonitoring(alivenessManager, monitorId);
            DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
            coordinator.enqueueJob(ArpScheduler.buildJobKey(macEntry.getIpAddress().getHostAddress(),macEntry.getVpnName()),
                    new ArpRemoveCacheTask(dataBroker,macEntry.getIpAddress().getHostAddress(), macEntry.getVpnName(),macEntry.getInterfaceName()));
        }
    }

}