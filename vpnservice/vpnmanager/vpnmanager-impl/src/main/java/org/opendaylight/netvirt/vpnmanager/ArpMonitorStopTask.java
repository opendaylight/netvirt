/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpMonitorStopTask implements Callable<List<ListenableFuture<Void>>> {
    private MacEntry macEntry;
    private AlivenessMonitorService alivenessManager;
    private DataBroker dataBroker;
    private static final Logger LOG = LoggerFactory.getLogger(ArpMonitorStopTask.class);

    public ArpMonitorStopTask(MacEntry macEntry, DataBroker dataBroker,
        AlivenessMonitorService alivenessManager) {
        this.macEntry = macEntry;
        this.dataBroker = dataBroker;
        this.alivenessManager = alivenessManager;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        final List<ListenableFuture<Void>> futures = new ArrayList<>();
        java.util.Optional<Long> monitorIdOptional = AlivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
        monitorIdOptional.ifPresent(monitorId -> {
            AlivenessMonitorUtils.stopArpMonitoring(alivenessManager, monitorId);
            removeMipAdjacency(macEntry.getIpAddress().getHostAddress(),
                    macEntry.getVpnName(), macEntry.getInterfaceName());
            VpnUtil.removeLearntVpnVipToPort(dataBroker, macEntry.getVpnName(),
                    macEntry.getIpAddress().getHostAddress());
        });
        return futures;
    }

    private void removeMipAdjacency(String fixedip, String vpnName, String interfaceName) {
        synchronized (interfaceName.intern()) {
            InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
            InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
            Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
            if (adjacencies.isPresent()) {
                InstanceIdentifier<Adjacency> adid = vpnIfId.augmentation(Adjacencies.class).child(Adjacency.class,
                    new AdjacencyKey(ipToPrefix(fixedip)));
                try {
                    SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, adid);
                } catch (TransactionCommitFailedException e) {
                    LOG.error("Failed to delete the learned-ip-adjacency for vpn {} interface {} prefix {}",
                            vpnName, interfaceName, ipToPrefix(fixedip), e);
                    return;
                }
                LOG.info("Successfully deleted the learned-ip-adjacency prefix {} on vpn {} for interface {}",
                        ipToPrefix(fixedip), vpnName, interfaceName);
            }
        }
    }

    private String ipToPrefix(String ip) {
        return new StringBuilder(ip).append(ArpConstants.PREFIX).toString();
    }

}
