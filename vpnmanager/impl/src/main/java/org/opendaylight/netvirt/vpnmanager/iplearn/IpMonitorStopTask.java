/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpMonitorStopTask implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitorStopTask.class);
    private MacEntry macEntry;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private boolean isRemoveMipAdjAndLearntIp;
    private final VpnUtil vpnUtil;

    public IpMonitorStopTask(MacEntry macEntry, boolean removeMipAdjAndLearntIp, VpnUtil vpnUtil,
                              AlivenessMonitorUtils alivenessMonitorUtils) {
        this.macEntry = macEntry;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.isRemoveMipAdjAndLearntIp = removeMipAdjAndLearntIp;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        final List<ListenableFuture<Void>> futures = new ArrayList<>();
        java.util.Optional<Long> monitorIdOptional = alivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
        if (monitorIdOptional.isPresent()) {
            alivenessMonitorUtils.stopIpMonitoring(monitorIdOptional.get());
        } else {
            LOG.warn("MonitorId not available for IP {} interface {}. IpMonitoring not stopped",
                    macEntry.getIpAddress(), macEntry.getInterfaceName());
        }

        if (this.isRemoveMipAdjAndLearntIp) {
            String learntIp = macEntry.getIpAddress().getHostAddress();
            String vpnName =  macEntry.getVpnName();
            LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, learntIp);
            if (vpnVipToPort != null && !Objects.equals(vpnVipToPort.getCreationTime(), macEntry.getCreatedTime())) {
                LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                        + "Ignoring this remove event.", learntIp, vpnName);
                return futures;
            }
            vpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, learntIp, null);
            vpnUtil.removePortNameLearntIpMap(dataBroker, vpnName, macEntry.getInterfaceName(), null);
            vpnUtil.removeMipAdjAndLearntIp(vpnName, macEntry.getInterfaceName(), learntIp);
        }
        return futures;
    }


}
