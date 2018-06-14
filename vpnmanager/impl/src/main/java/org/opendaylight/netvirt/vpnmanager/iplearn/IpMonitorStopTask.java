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
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpMonitorStopTask implements Callable<List<ListenableFuture<Void>>> {
    private MacEntry macEntry;
    private AlivenessMonitorService alivenessManager;
    private DataBroker dataBroker;
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitorStopTask.class);
    private boolean isRemoveMipAdjAndLearntIp;

    public IpMonitorStopTask(MacEntry macEntry, DataBroker dataBroker,
        AlivenessMonitorService alivenessManager, boolean removeMipAdjAndLearntIp) {
        this.macEntry = macEntry;
        this.dataBroker = dataBroker;
        this.alivenessManager = alivenessManager;
        this.isRemoveMipAdjAndLearntIp = removeMipAdjAndLearntIp;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        final List<ListenableFuture<Void>> futures = new ArrayList<>();
        java.util.Optional<Long> monitorIdOptional = AlivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
        monitorIdOptional.ifPresent(monitorId -> {
            AlivenessMonitorUtils.stopIpMonitoring(alivenessManager, monitorId);
        });

        if (this.isRemoveMipAdjAndLearntIp) {
            String vpnName =  macEntry.getVpnName();
            String learntIp = macEntry.getIpAddress().getHostAddress();
            LearntVpnVipToPort vpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker, vpnName, learntIp);
            if (vpnVipToPort != null && !vpnVipToPort.getCreationTime().equals(macEntry.getCreatedTime())) {
                LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                        + "Ignoring this remove event.", learntIp, vpnName);
                return futures;
            }

            VpnUtil.removeMipAdjAndLearntIp(dataBroker, macEntry.getVpnName(), macEntry.getInterfaceName(),
                    macEntry.getIpAddress().getHostAddress());
        }
        return futures;
    }


}
