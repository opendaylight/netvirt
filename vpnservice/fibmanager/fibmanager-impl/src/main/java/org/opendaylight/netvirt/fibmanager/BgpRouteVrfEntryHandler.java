/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpRouteVrfEntryHandler implements AutoCloseable, ResourceHandler, VrfEntryHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BgpRouteVrfEntryHandler.class);
    private final DataBroker dataBroker;
    private static final int BATCH_INTERVAL = 500;
    private static final int BATCH_SIZE = 1000;
    private final BlockingQueue<ActionableResource> vrfEntryBufferQ = new LinkedBlockingQueue<>();
    private final ResourceBatchingManager resourceBatchingManager;
    private final VrfEntryListener vrfEntryListener;

    public BgpRouteVrfEntryHandler(final DataBroker dataBroker,
                                   final VrfEntryListener vrfEntryListener) {
        this.dataBroker = dataBroker;
        this.vrfEntryListener = vrfEntryListener;

        resourceBatchingManager = ResourceBatchingManager.getInstance();
        resourceBatchingManager.registerBatchableResource("FIB-VRFENTRY",vrfEntryBufferQ, this);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        subscribeWithVrfListener();
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
        unSubscribeWithVrfListener();
    }

    @Override
    public void subscribeWithVrfListener() {
        vrfEntryListener.registerVrfEntryHandler(this);
    }

    @Override
    public void unSubscribeWithVrfListener() {
        vrfEntryListener.unRegisterVrfEntryHandler(this);
    }

    public boolean wantToProcessVrfEntry(VrfEntry vrfEntry) {
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            return true;
        }
        return false;
    }

    public String getHandlerType() {
        return "BgpRouteVrfEntryHandler";
    }

    public short createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + vrfEntry.getDestPrefix());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(vrfEntry);
        vrfEntryBufferQ.add(actResource);
        return VRF_PROCESSING_CONTINUE;
    }

    public short removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + vrfEntry.getDestPrefix());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(vrfEntry);
        vrfEntryBufferQ.add(actResource);
        return VRF_PROCESSING_CONTINUE;
    }

    public short updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + update.getDestPrefix());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(update);
        actResource.setOldInstance(original);
        vrfEntryBufferQ.add(actResource);
        return VRF_PROCESSING_COMPLETED;
    }

    @Override
    public DataBroker getResourceBroker() {
        return dataBroker;
    }

    @Override
    public void create(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object vrfEntry, List<SubTransaction> transactionObjects) {
        if (vrfEntry instanceof VrfEntry) {
            createFibEntries(tx, identifier, (VrfEntry)vrfEntry, transactionObjects);
        }
    }

    @Override
    public void delete(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object vrfEntry, List<SubTransaction> transactionObjects) {
        if (vrfEntry instanceof VrfEntry) {
            deleteFibEntries(tx, identifier, (VrfEntry) vrfEntry, transactionObjects);
        }
    }

    @Override
    public void update(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object original, Object update, List<SubTransaction> transactionObjects) {
        if ((original instanceof VrfEntry) && (update instanceof VrfEntry)) {
            createFibEntries(tx, identifier, (VrfEntry)update, transactionObjects);
        }
    }

    @Override
    public int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    public int getBatchInterval() {
        return BATCH_INTERVAL;
    }

    @Override
    public LogicalDatastoreType getDatastoreType() {
        return LogicalDatastoreType.CONFIGURATION;
    }

    /*
      The invocation of the following method is via create() callback from the MDSAL Batching
      Infrastructure provided by ResourceBatchingManager
     */
    private void createFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> vrfEntryIid,
                                  final VrfEntry vrfEntry, List<SubTransaction> txnObjects) {
        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);

        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId()
                                    + " has null vpnId!");

        final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            for (VpnToDpnList vpnDpn : vpnToDpnList) {
                if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                    vrfEntryListener.createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey,
                            vrfEntry, writeTx, txnObjects);
                }
            }
        }
    }

    /*
      The invocation of the following method is via delete() callback from the MDSAL Batching
      Infrastructure provided by ResourceBatchingManager
     */
    private void deleteFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> identifier,
                                  final VrfEntry vrfEntry, List<SubTransaction> txnObjects) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);

        final String rd  = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        if (vpnInstance == null) {
            LOG.debug("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            for (VpnToDpnList curDpn : vpnToDpnList) {
                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                    vrfEntryListener.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(), vpnInstance.getVpnId(),
                            vrfTableKey, vrfEntry, writeTx, txnObjects);
                }
            }
        }
    }


}
