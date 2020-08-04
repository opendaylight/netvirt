/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Lists;

import java.math.BigInteger;
import java.util.Collections;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.elan.ElanEntityOwnerStatusMonitor;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacEntryListener extends AsyncClusteredDataTreeChangeListenerBase<MacEntry,
        ElanMacEntryListener> {

    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    private final DataBroker dataBroker;
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanInstanceCache elanInstanceCache;
    private final IdManagerService idManager;

    @Inject
    public ElanMacEntryListener(final DataBroker dataBroker,
                                ElanClusterUtils elanClusterUtils,
                                ElanInstanceCache elanInstanceCache,
                                ElanL2GatewayUtils elanL2GatewayUtils,
                                final IdManagerService idManager,
                                ElanEntityOwnerStatusMonitor elanEntityOwnerStatusMonitor) {
        super(MacEntry.class, ElanMacEntryListener.class);
        this.dataBroker = dataBroker;
        this.elanClusterUtils = elanClusterUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.idManager = idManager;
    }

    @PostConstruct
    public void init() {
        LOG.info("ElanMacEntryListener L2Gw init()");
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected void remove(final InstanceIdentifier<MacEntry> identifier, final MacEntry del) {
        LOG.trace("ElanMacEntryListener remove : {}", del);
        elanClusterUtils.runOnlyInOwnerNode(del.getMacAddress().getValue(),
            "Deleting dpn macs from remote ucast mac tables", () -> {
                String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
                ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
                elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInstance,
                        Lists.newArrayList(del.getMacAddress()));
                return Collections.EMPTY_LIST;
            });
    }

    @Override
    protected void update(InstanceIdentifier<MacEntry> identifier, MacEntry original, MacEntry update) {
    }

    //Using mac entry clustered listener instead of elan interface listener to avoid race conditions
    //always use clustered listener to programme l2gw device
    @Override
    protected void add(InstanceIdentifier<MacEntry> identifier, MacEntry add) {
        LOG.trace("ElanMacEntryListener add : {}", add);
        elanClusterUtils.runOnlyInOwnerNode("Adding dpn macs to remote ucast mac tables", () -> {
            String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
            ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
            if (ElanUtils.isVxlanNetworkOrVxlanSegment(elanInstance)) {
                BigInteger dpId = elanL2GatewayUtils.getDpidFromInterface(add.getInterface());
                elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanName, dpId,
                        Lists.newArrayList(add.getMacAddress()));
            }
        });
    }

    @Override
    protected ElanMacEntryListener getDataTreeChangeListener() {
        return ElanMacEntryListener.this;
    }

    @Override
    protected InstanceIdentifier<MacEntry> getWildCardPath() {
        return InstanceIdentifier.builder(
                ElanForwardingTables.class).child(MacTable.class).child(MacEntry.class).build();
    }
}