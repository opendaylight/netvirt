/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Optional;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.DpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnInstancesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DpnInVpnChangeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DpnInVpnChangeHandler.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public DpnInVpnChangeHandler(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    public void onVpnAddedToDpn(String vpnName, String primaryRd, Uint64 dpnId) {
        LOG.trace("onVpnAddedToDpn: Add Dpn Event notification received for rd {} VpnName {} DpnId {}", primaryRd,
                vpnName, dpnId);
        synchronized (dpnId.toString().intern()) {
            /*DpnToVpnList contains list of VPNs having footprint on a given DPN*/
            InstanceIdentifier<VpnInstances> id = VpnUtil.getDpnToVpnListVpnInstanceIdentifier(dpnId, vpnName);
            VpnInstancesBuilder vpnInstancesBuilder =
                    new VpnInstancesBuilder().setVpnInstanceName(vpnName).setVrfId(primaryRd);
            try {
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                    tx -> tx.put(id, vpnInstancesBuilder.build(), true)).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("onVpnAddedToDpn: Error updating dpnToVpnList for dpn {} vpn {} ", dpnId, vpnName, e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public void onVpnRemovedFromDpn(String vpnName, String primaryRd, Uint64 dpnId)  {
        LOG.trace("onVpnRemovedFromDpn: Remove Dpn Event notification received for rd {} VpnName {} DpnId {}",
                primaryRd, vpnName, dpnId);
        // FIXME: separate out to somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(primaryRd);
            Optional<VpnInstanceOpDataEntry> vpnOpValue =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            if (vpnOpValue.isPresent()) {
                VpnInstanceOpDataEntry vpnInstOpData = vpnOpValue.get();
                List<VpnToDpnList> vpnToDpnList = vpnInstOpData.nonnullVpnToDpnList();
                boolean flushDpnsOnVpn = true;
                for (VpnToDpnList dpn : vpnToDpnList) {
                    if (dpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        flushDpnsOnVpn = false;
                        break;
                    }
                }
                if (flushDpnsOnVpn) {
                    try {
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                            tx -> deleteDpn(vpnToDpnList, primaryRd, tx)).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("Error removing dpnToVpnList for vpn {} ", vpnName);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            }
        } catch (ReadFailedException e) {
            LOG.error("onVpnRemovedFromDpn: Failed to read VpnInstanceOpDataEntry DS for rd {} vpn {} dpn {}",
                    primaryRd, vpnName, dpnId);
        }

        try {
            synchronized (dpnId.toString().intern()) {
                /*Remove VpnName from DpnToVpnList for a given DPN*/
                InstanceIdentifier<DpnList> id = VpnUtil.getDpnToVpnListIdentifier(dpnId);
                Optional<DpnList> dpnListOptional =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
                VpnInstances vpnInstance = new VpnInstancesBuilder().setVpnInstanceName(vpnName).setVrfId(primaryRd)
                        .build();

                if (dpnListOptional.isPresent()) {
                    List<VpnInstances> vpnInstanceList = dpnListOptional.get().getVpnInstances();
                    try {
                        if (vpnInstanceList.remove(vpnInstance)) {

                            if (vpnInstanceList.isEmpty()) {
                                txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                                    tx -> tx.delete(id)).get();
                            } else {
                                txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                                    tx -> tx.delete(id.child(VpnInstances.class, new VpnInstancesKey(vpnName))))
                                        .get();
                            }
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("onVpnRemovedFromDpn: Error deleting entry in dpnToVpnList for dpn {} ", dpnId, e);
                    }

                }
            }
        } catch (ReadFailedException e) {
            LOG.error("onVpnRemovedFromDpn: Failed to read DpnList DS for rd {} vpn {} dpn {}", primaryRd, vpnName,
                    dpnId, e);
        }
    }

    protected void deleteDpn(Collection<VpnToDpnList> vpnToDpnList, String primaryRd,
                             TypedWriteTransaction<Operational> tx) {
        for (final VpnToDpnList curDpn : vpnToDpnList) {
            InstanceIdentifier<VpnToDpnList> vpnToDpnId = VpnHelper.getVpnToDpnListIdentifier(primaryRd,
                                                                        curDpn.getDpnId());
            tx.delete(vpnToDpnId);
        }
    }
}
