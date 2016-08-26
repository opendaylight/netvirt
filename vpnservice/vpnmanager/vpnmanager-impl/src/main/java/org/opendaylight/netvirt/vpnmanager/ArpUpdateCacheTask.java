/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;

public class ArpUpdateCacheTask implements Callable<List<ListenableFuture<Void>>> {
    private DataBroker dataBroker;
    private String fixedip;
    private String vpnName;
    private String interfaceName;
    private static final Logger LOG = LoggerFactory.getLogger(ArpUpdateCacheTask.class);

    public ArpUpdateCacheTask(DataBroker dataBroker, String fixedip, String vpnName, String interfaceName) {
        super();
        this.fixedip = fixedip;
        this.vpnName = vpnName;
        this.interfaceName = interfaceName;
        this.dataBroker = dataBroker;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        removeMipAdjacency(fixedip, vpnName, interfaceName, tx);
        VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, fixedip);
        CheckedFuture<Void, TransactionCommitFailedException> txFutures = tx.submit();
        try {
            txFutures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore {}", e);
        }
        futures.add(txFutures);
        return futures;
    }

 private void removeMipAdjacency(String fixedip, String vpnName, String interfaceName, WriteTransaction tx) {
     synchronized (interfaceName.intern()) {
         InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
         InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
         Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
         if (adjacencies.isPresent()) {
             InstanceIdentifier<Adjacency> adid = vpnIfId.augmentation(Adjacencies.class).child(Adjacency.class, new AdjacencyKey(iptoprefix(fixedip)));

             tx.delete(LogicalDatastoreType.CONFIGURATION, adid);
             LOG.info("deleting the adjacencies for vpn {} interface {}", vpnName, interfaceName);
         }
     }
 }

 private String iptoprefix(String ip) {
     return new StringBuilder(ip).append(ArpConstants.PREFIX).toString();
  }
}
