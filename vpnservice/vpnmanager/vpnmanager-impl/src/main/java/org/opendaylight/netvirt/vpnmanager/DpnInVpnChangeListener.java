/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DpnInVpnChangeListener implements OdlL3vpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(DpnInVpnChangeListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;

    public DpnInVpnChangeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public void onAddDpnEvent(AddDpnEvent notification) {
        AddEventData addEventData = notification.getAddEventData();
        String vpnName = addEventData.getVpnName();
        String rd = addEventData.getRd();
        //Vpn Has FootPrint. Set state to FOOTPRINT_CREATED
        VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder().setVrfId(rd)
                .setVpnState(VpnInstanceOpDataEntry.VpnState.FootprintCreated);
        WriteTransaction txn = dataBroker.newWriteOnlyTransaction();
        txn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());
        txn.submit();
    }

    public void onRemoveDpnEvent(RemoveDpnEvent notification) {

        RemoveEventData eventData = notification.getRemoveEventData();
        final String rd = eventData.getRd();
        final String vpnName = eventData.getVpnName();
        BigInteger dpnId = eventData.getDpnId();

        LOG.trace("Remove Dpn Event notification received for rd {} VpnName {} DpnId {}", rd, vpnName, dpnId);

        synchronized (vpnName.intern()) {
            InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
            Optional<VpnInstanceOpDataEntry> vpnOpValue =
                VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);

            if (vpnOpValue.isPresent()) {
                VpnInstanceOpDataEntry vpnInstOpData = vpnOpValue.get();
                List<VpnToDpnList> vpnToDpnList = vpnInstOpData.getVpnToDpnList();
                boolean vpnFootprintCleared = true;
                for (VpnToDpnList dpn : vpnToDpnList) {
                    if (dpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        vpnFootprintCleared = false;
                        break;
                    }
                }
                if (vpnFootprintCleared) {
                    VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder().setVrfId(rd)
                            .setVpnState(VpnInstanceOpDataEntry.VpnState.FootprintCleared);
                    WriteTransaction txn = dataBroker.newWriteOnlyTransaction();
                    txn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());
                    txn.submit();
                }
            }
        }
    }

    @Override
    public void onAddInterfaceToDpnOnVpnEvent(AddInterfaceToDpnOnVpnEvent notification) {
    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
    }
}

