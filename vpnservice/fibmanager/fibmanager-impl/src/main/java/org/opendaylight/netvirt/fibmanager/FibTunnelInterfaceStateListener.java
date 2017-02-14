/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.PendingFibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.pending.fib.entries.TunnelEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.pending.fib.entries.TunnelEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.pending.fib.entries.tunnel.entry.PendingFibEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FibTunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
        FibTunnelInterfaceStateListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VrfEntryListener.class);

    private final DataBroker dataBroker;
    private final VrfEntryListener vrfEntryListener;

    public FibTunnelInterfaceStateListener(final DataBroker dataBroker,
            final VrfEntryListener vrfEntryListener) {

        super(StateTunnelList.class, FibTunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.vrfEntryListener = vrfEntryListener;
    }

    @Override
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected FibTunnelInterfaceStateListener getDataTreeChangeListener() {
        return FibTunnelInterfaceStateListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> key, StateTunnelList toDelete) {
        LOG.trace("FibTunnelInterfaceStateListener: remove tunnel event {}", toDelete);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> key, StateTunnelList original, StateTunnelList update) {
        LOG.trace("FibTunnelInterfaceStateListener: update tunnel event {}", update);
        final BigInteger srcDpnId = new BigInteger(update.getSrcInfo().getTepDeviceId());
        final String destTepIp = String.valueOf(update.getDstInfo().getTepIp().getValue());
        InstanceIdentifier<TunnelEntry> tunnelEntryId =
                InstanceIdentifier.builder(PendingFibEntries.class)
                    .child(TunnelEntry.class, new TunnelEntryKey(srcDpnId, destTepIp)).build();
        Optional<TunnelEntry> tunnelEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, tunnelEntryId);
        if (tunnelEntry.isPresent()) {
            List<PendingFibEntry> fibEntryList = tunnelEntry.get().getPendingFibEntry();
            if (fibEntryList == null || fibEntryList.isEmpty()) {
                LOG.trace("PENDING_FIB_ENTRY: delete the empty pending tunnel entry {} from OperDS", tunnelEntry.get());
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, tunnelEntryId);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> key, StateTunnelList stateTunnelList) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final BigInteger destDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        final String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        LOG.trace("PENDING_FIB_ENTRY: add tunnel event {} srcDpnId {} destDpnId {} srcTepIp {} destTepIp {}",
                stateTunnelList.getTunnelInterfaceName(), srcDpnId, destDpnId, srcTepIp, destTepIp);
        InstanceIdentifier<TunnelEntry> tunnelEntryId =
                InstanceIdentifier.builder(PendingFibEntries.class)
                    .child(TunnelEntry.class, new TunnelEntryKey(srcDpnId, destTepIp)).build();
        Optional<TunnelEntry> tunnelEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, tunnelEntryId);
        if (!tunnelEntry.isPresent()) {
            return;
        }
        LOG.trace("PENDING_FIB_ENTRY: the pending tunnel found {}", tunnelEntry.get());
        List<PendingFibEntry> fibEntryList = tunnelEntry.get().getPendingFibEntry();
        for (PendingFibEntry fibEntry : fibEntryList) {

            String rd = fibEntry.getRouteDistinguisher();
            VrfTablesKey vrfTableKey = new VrfTablesKey(rd);
            long vpnId = FibUtil.getVpnId(dataBroker, rd);

            List<String> destPrefixList = fibEntry.getDestPrefix();
            for (String destPrefix : destPrefixList) {
                VrfEntry vrfEntry = vrfEntryListener.getVrfEntry(dataBroker, rd, destPrefix);
                if (vrfEntry == null) {
                    continue;
                }
                LOG.trace("PENDING_FIB_ENTRY: create missed FIB entry for rd {} destPrefix {} vrfEntry {}",
                        rd, destPrefix, vrfEntry);
                vrfEntryListener.createRemoteFibEntry(srcDpnId, vpnId, vrfTableKey, vrfEntry, null);
            }
        }
    }

}
