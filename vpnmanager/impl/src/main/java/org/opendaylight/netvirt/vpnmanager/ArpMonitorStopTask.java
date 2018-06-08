/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpMonitorStopTask implements Callable<List<ListenableFuture<Void>>> {
    private MacEntry macEntry;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private static final Logger LOG = LoggerFactory.getLogger(ArpMonitorStopTask.class);
    private boolean isRemoveMipAdjAndLearntIp;
    private final VpnUtil vpnUtil;

    public ArpMonitorStopTask(MacEntry macEntry, boolean removeMipAdjAndLearntIp, VpnUtil vpnUtil,
                              AlivenessMonitorUtils alivenessMonitorUtils) {
        this.macEntry = macEntry;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.isRemoveMipAdjAndLearntIp = removeMipAdjAndLearntIp;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        final List<ListenableFuture<Void>> futures = new ArrayList<>();
        java.util.Optional<Long> monitorIdOptional = AlivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
        monitorIdOptional.ifPresent(monitorId -> {
            alivenessMonitorUtils.stopArpMonitoring(monitorId);
        });

        if (this.isRemoveMipAdjAndLearntIp) {
            String vpnName =  macEntry.getVpnName();
            String learntIp = macEntry.getIpAddress().getHostAddress();
            LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, learntIp);
            if (vpnVipToPort != null && !vpnVipToPort.getCreationTime().equals(macEntry.getCreatedTime())) {
                LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                        + "Ignoring this remove event.", learntIp, vpnName);
                return futures;
            }

            vpnUtil.removeMipAdjAndLearntIp(macEntry.getVpnName(), macEntry.getInterfaceName(),
                    macEntry.getIpAddress().getHostAddress());
        }
        return futures;
    }


}
