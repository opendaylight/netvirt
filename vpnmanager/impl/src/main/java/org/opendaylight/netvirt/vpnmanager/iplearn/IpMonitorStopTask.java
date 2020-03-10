/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpMonitorStopTask implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitorStopTask.class);
    private MacEntry macEntry;
    private DataBroker dataBroker;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private boolean isRemoveMipAdjAndLearntIp;
    private final VpnUtil vpnUtil;

    public IpMonitorStopTask(MacEntry macEntry, DataBroker dataBroker, boolean removeMipAdjAndLearntIp, VpnUtil vpnUtil,
                             AlivenessMonitorUtils alivenessMonitorUtils) {
        this.macEntry = macEntry;
        this.dataBroker = dataBroker;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.isRemoveMipAdjAndLearntIp = removeMipAdjAndLearntIp;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        final List<ListenableFuture<Void>> futures = new ArrayList<>();
        java.util.Optional<Uint32> monitorIdOptional = alivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
        if (monitorIdOptional.isPresent()) {
            alivenessMonitorUtils.stopIpMonitoring(monitorIdOptional.get());
        } else {
            LOG.warn("MonitorId not available for IP {} interface {}. IpMonitoring not stopped",
                    macEntry.getIpAddress(), macEntry.getInterfaceName());
        }

        String learntIp = macEntry.getIpAddress().getHostAddress();
        if (this.isRemoveMipAdjAndLearntIp) {
            String vpnName =  macEntry.getVpnName();
            LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, learntIp);
            if (vpnVipToPort != null && !Objects.equals(vpnVipToPort.getCreationTime(), macEntry.getCreatedTime())) {
                LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                        + "Ignoring this remove event.", learntIp, vpnName);
                return futures;
            }
            vpnUtil.removeLearntVpnVipToPort(macEntry.getVpnName(),
                    macEntry.getIpAddress().getHostAddress(), null);
            vpnUtil.removeVpnPortFixedIpToPort(dataBroker, macEntry.getVpnName(),
                    macEntry.getIpAddress().getHostAddress(), null);
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            vpnUtil.removeMipAdjacency(macEntry.getVpnName(), macEntry.getInterfaceName(),
                    macEntry.getIpAddress().getHostAddress(), tx);
            CheckedFuture<Void, TransactionCommitFailedException> txFutures = tx.submit();
            try {
                txFutures.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("ArpMonitorStopTask: Error writing to datastore for Vpn {} IP  {}",
                        macEntry.getVpnName(), macEntry.getIpAddress().getHostAddress(), e);
            }
        } else {
            // Delete only MIP adjacency
            vpnUtil.removeMipAdjacency(macEntry.getInterfaceName(), learntIp);
        }
        return futures;
    }


}
