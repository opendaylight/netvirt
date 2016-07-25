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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;

public class ArpremovechacheTask implements Callable<List<ListenableFuture<Void>>> {
    DataBroker dataBroker;
    String fixedip;
    String vpnName;
    String interfaceName;
    String rd;
    InstanceIdentifier<VpnPortipToPort> id;
    private static final Logger LOG = LoggerFactory.getLogger(ArpremovechacheTask.class);

    public ArpremovechacheTask(DataBroker dataBroker, String fixedip, String vpnName, String interfaceName, String rd,
                               InstanceIdentifier<VpnPortipToPort> id) {
        super();
        this.fixedip = fixedip;
        this.vpnName = vpnName;
        this.interfaceName = interfaceName;
        this.rd = rd;
        this.dataBroker = dataBroker;
        this.id = id;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        List<ListenableFuture<Void>> result = new ArrayList<ListenableFuture<Void>>();
        deleteVrfEntries(rd,fixedip,tx);
        deleteAdjacencies(fixedip,vpnName,interfaceName,tx);
        tx.delete(LogicalDatastoreType.CONFIGURATION, id);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore {}", e);
        }
        result.add(futures);
        return result;

    }

    private void deleteVrfEntries(String rd, String fixedip, WriteTransaction tx) {
        InstanceIdentifier<VrfEntry> vrfid= InstanceIdentifier.builder(FibEntries.class).
                child(VrfTables.class, new VrfTablesKey(rd)).
                child(VrfEntry.class,new VrfEntryKey(iptoprefix(fixedip))).
                build();

        tx.delete(LogicalDatastoreType.CONFIGURATION, vrfid);
    }


    public void deleteAdjacencies(String fixedip, String vpnName, String interfaceName, WriteTransaction tx) {
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
        if (adjacencies.isPresent()) {
            InstanceIdentifier <Adjacency> adid = vpnIfId.augmentation(Adjacencies.class).child(Adjacency.class, new AdjacencyKey(iptoprefix(fixedip)));
            tx.delete(LogicalDatastoreType.CONFIGURATION, adid);
            LOG.info("deleting the adjacencies ");
        }
    }

    private String iptoprefix(String ip){
        return new StringBuilder(ip).append(ArpConstants.PREFIX).toString();

    }
}
