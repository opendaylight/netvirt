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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add._interface.to.dpn.on.vpn.event.AddInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add.dpn.event.AddEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove.dpn.event.RemoveEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DpnInVpnChangeListener implements OdlL3vpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(DpnInVpnChangeListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IAclServiceUtil aclServiceUtil;

    public DpnInVpnChangeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                  IAclServiceUtil aclServiceUtil) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.aclServiceUtil = aclServiceUtil;
    }

    public void onAddDpnEvent(AddDpnEvent notification) {
        AddEventData addEventData = notification.getAddEventData();
        String vpnName = addEventData.getVpnName();
        BigInteger dpId = addEventData.getDpnId();
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
                boolean flushDpnsOnVpn = true;
                for (VpnToDpnList dpn : vpnToDpnList) {
                    if (dpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                        flushDpnsOnVpn = false;
                        break;
                    }
                }
                if (flushDpnsOnVpn) {
                    WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                    deleteDpn(vpnToDpnList, rd, writeTxn);
                    CheckedFuture<Void, TransactionCommitFailedException> futures = writeTxn.submit();
                    try {
                        futures.get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("Error removing dpnToVpnList for vpn {} ", vpnName);
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
        AddInterfaceEventData data = notification.getAddInterfaceEventData();
        LOG.trace("Ignoring vpn interface {} added on dpn {}", data.getInterfaceName(), data.getDpnId());
    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
        RemoveInterfaceEventData data = notification.getRemoveInterfaceEventData();
        LOG.trace("Ignoring vpn interface {} removed from dpn {}", data.getInterfaceName(), data.getDpnId());
    }
}

