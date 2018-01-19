/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.DpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnNamesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DpnInVpnChangeListener implements OdlL3vpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(DpnInVpnChangeListener.class);
    private final DataBroker dataBroker;

    @Inject
    public DpnInVpnChangeListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onAddDpnEvent(AddDpnEvent notification) {
        AddEventData event = notification.getAddEventData();
        final String rd = event.getRd();
        final String vpnName = event.getVpnName();
        BigInteger dpnId = event.getDpnId();
        WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();

        LOG.trace("Add Dpn Event notification received for rd {} VpnName {} DpnId {}", rd , vpnName, dpnId);
        synchronized (dpnId.toString().intern()) {
            /*DpnToVpnList contains list of VPNs having footprint on a given DPN*/
            InstanceIdentifier<VpnNames> id = VpnUtil.getDpnToVpnListVpnInstanceIdentifier(dpnId, vpnName);
            VpnNamesBuilder vpnNamesBuilder =
                    new VpnNamesBuilder().setVpnInstanceName(vpnName).setVrfId(rd);
            writeTxn.put(LogicalDatastoreType.OPERATIONAL, id, vpnNamesBuilder.build(), true);

            CheckedFuture<Void, TransactionCommitFailedException> futures = writeTxn.submit();
            try {
                futures.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("onAddDpnEvent: Error updating dpnToVpnList for dpn {} vpn {} ", dpnId, vpnName);
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public void onRemoveDpnEvent(RemoveDpnEvent notification) {

        RemoveEventData eventData = notification.getRemoveEventData();
        final String rd = eventData.getRd();
        final String vpnName = eventData.getVpnName();
        BigInteger dpnId = eventData.getDpnId();

        LOG.trace("Remove Dpn Event notification received for rd {} VpnName {} DpnId {}", rd, vpnName, dpnId);

        synchronized (vpnName.intern()) {
            WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
            InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
            Optional<VpnInstanceOpDataEntry> vpnOpValue =
                VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);

            if (vpnOpValue.isPresent()) {
                VpnInstanceOpDataEntry vpnInstOpData = vpnOpValue.get();
                List<VpnToDpnList> vpnToDpnList = vpnInstOpData.getVpnToDpnList();
                boolean flushDpnsOnVpn = true;
                for (VpnToDpnList dpn : vpnToDpnList) {
                    if (dpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        flushDpnsOnVpn = false;
                        break;
                    }
                }
                if (flushDpnsOnVpn) {
                    deleteDpn(vpnToDpnList, rd, writeTxn);
                    try {
                        writeTxn.submit().get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("Error removing dpnToVpnList for vpn {} ", vpnName);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            }
        }

        synchronized (dpnId.toString().intern()) {
            WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
            /*Remove VpnName from DpnToVpnList for a given DPN*/
            InstanceIdentifier<DpnList> id = VpnUtil.getDpnToVpnListIdentifier(dpnId);
            Optional<DpnList> dpnListOptional = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            VpnNames vpnNames = new VpnNamesBuilder().setVpnInstanceName(vpnName).setVrfId(rd).build();

            if (dpnListOptional.isPresent()) {
                List<VpnNames> vpnInstanceList = dpnListOptional.get().getVpnNames();

                if (vpnInstanceList.remove(vpnNames)) {
                    if (vpnInstanceList.isEmpty()) {
                        writeTxn.delete(LogicalDatastoreType.OPERATIONAL, id);
                    } else {
                        writeTxn.delete(LogicalDatastoreType.OPERATIONAL, id.child(VpnNames.class,
                                new VpnNamesKey(vpnName)));
                    }
                    CheckedFuture<Void, TransactionCommitFailedException> futures = writeTxn.submit();
                    try {
                        futures.get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("onRemoveDpnEvent: Error deleting entry in dpnToVpnList for dpn {} ", dpnId);
                        throw new RuntimeException(e.getMessage());
                    }
                }
            }
        }
    }

    protected void deleteDpn(Collection<VpnToDpnList> vpnToDpnList, String rd, WriteTransaction writeTxn) {
        for (final VpnToDpnList curDpn : vpnToDpnList) {
            InstanceIdentifier<VpnToDpnList> vpnToDpnId = VpnUtil.getVpnToDpnListIdentifier(rd, curDpn.getDpnId());
            writeTxn.delete(LogicalDatastoreType.OPERATIONAL, vpnToDpnId);
        }
    }

    @Override
    public void onAddInterfaceToDpnOnVpnEvent(AddInterfaceToDpnOnVpnEvent notification) {
    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
    }
}

