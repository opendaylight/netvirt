/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
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
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public DpnInVpnChangeListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public void onAddDpnEvent(AddDpnEvent notification) {

    }

    @Override
    public void onRemoveDpnEvent(RemoveDpnEvent notification) {

        RemoveEventData eventData = notification.getRemoveEventData();
        final String rd = eventData.getRd();
        final String vpnName = eventData.getVpnName();
        BigInteger dpnId = eventData.getDpnId();

        LOG.trace("Remove Dpn Event notification received for rd {} VpnName {} DpnId {}", rd, vpnName, dpnId);
        try {
            synchronized (vpnName.intern()) {
                InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
                Optional<VpnInstanceOpDataEntry> vpnOpValue =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);

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
                        try {
                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> deleteDpn(vpnToDpnList, rd, tx))
                                    .get();
                        } catch (InterruptedException | ExecutionException e) {
                            LOG.error("Error removing dpnToVpnList for vpn {} ", vpnName);
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (ReadFailedException e) {
            LOG.error("onRemoveDpnEvent: Failed to read data store for rd {} vpn {} dpn {}", rd, vpnName, dpnId);
        }
    }

    protected void deleteDpn(Collection<VpnToDpnList> vpnToDpnList, String rd, WriteTransaction writeTxn) {
        for (final VpnToDpnList curDpn : vpnToDpnList) {
            InstanceIdentifier<VpnToDpnList> vpnToDpnId = VpnHelper.getVpnToDpnListIdentifier(rd, curDpn.getDpnId());
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

