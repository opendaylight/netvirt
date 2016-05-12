/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;

public class ElanDpnInterfaceClusteredListener
        extends AsyncClusteredDataChangeListenerBase<DpnInterfaces, ElanDpnInterfaceClusteredListener>
        implements AutoCloseable {
    private DataBroker broker;
    private ElanInterfaceManager elanInterfaceManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnInterfaceClusteredListener.class);

    public ElanDpnInterfaceClusteredListener(final DataBroker db, final ElanInterfaceManager ifManager) {
        super(DpnInterfaces.class, ElanDpnInterfaceClusteredListener.class);
        broker = db;
        elanInterfaceManager = ifManager;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), ElanDpnInterfaceClusteredListener.this, AsyncDataBroker.DataChangeScope.BASE);
        } catch (final Exception e) {
            LOG.error("DpnInterfaces DataChange listener registration fail!", e);
        }
    }

    @Override
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return ElanDpnInterfaceClusteredListener.this;
    }

    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    void handleUpdate(InstanceIdentifier<DpnInterfaces> id, DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(id);
        if (ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).isEmpty()) {
            LOG.debug("dpnInterface updation, no external l2 devices to update for elan {} with Dp Id:", elanName,
                    dpnInterfaces.getDpId());
            return;
        }
        ElanClusterUtils.runOnlyInLeaderNode(elanName, "updating mcast mac upon tunnel event",
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        return Lists.newArrayList(
                                ElanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName));
                    }
                });
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        // this is the last dpn interface on this elan
        final String elanName = getElanName(identifier);
        LOG.debug("Received ElanDpnInterface removed for for elan {} with Dp Id ", elanName,
                dpnInterfaces.getDpId());

        if (ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).isEmpty()) {
            LOG.debug("dpnInterface removed, no external l2 devices to update for elan {} with Dp Id:", elanName,
                    dpnInterfaces.getDpId());
            return;
        }
        ElanClusterUtils.runOnlyInLeaderNode(elanName, "handling ElanDpnInterface removed",
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        // deleting Elan L2Gw Devices UcastLocalMacs From Dpn
                        ElanL2GatewayUtils.deleteElanL2GwDevicesUcastLocalMacsFromDpn(elanName,
                                dpnInterfaces.getDpId());
                        // updating remote mcast mac on l2gw devices
                        return Lists.newArrayList(
                                ElanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName));
                    }
                });
    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
            final DpnInterfaces dpnInterfaces) {
        LOG.debug("dpninterfaces update fired new size {}", dpnInterfaces.getInterfaces().size());
        if (dpnInterfaces.getInterfaces().size() == 0) {
            LOG.debug("dpninterfaces last dpn interface on this elan {} ", dpnInterfaces.getKey());
            // this is the last dpn interface on this elan
            handleUpdate(identifier, dpnInterfaces);
        }
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        if (dpnInterfaces.getInterfaces().size() == 1) {
            LOG.debug("dpninterfaces first dpn interface on this elan {} {} ", dpnInterfaces.getKey(),
                    dpnInterfaces.getInterfaces().get(0));
            // this is the first dpn interface on this elan
            handleUpdate(identifier, dpnInterfaces);
        }
    }

    /**
     * @param identifier
     * @return
     */
    private String getElanName(InstanceIdentifier<DpnInterfaces> identifier) {
        return identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
    }
}
