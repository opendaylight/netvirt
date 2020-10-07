/*
 * Copyright (c) 2020 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Lists;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacEntryListener extends AbstractClusteredAsyncDataTreeChangeListener<MacEntry> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanMacEntryListener.class);

    private final DataBroker dataBroker;
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanMacEntryListener(final DataBroker dataBroker,
                                ElanClusterUtils elanClusterUtils,
                                ElanInstanceCache elanInstanceCache,
                                ElanL2GatewayUtils elanL2GatewayUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanForwardingTables.class)
                .child(MacTable.class).child(MacEntry.class),
            Executors.newListeningSingleThreadExecutor("L2GatewayConnectionListener", LOG));
        this.dataBroker = dataBroker;
        this.elanClusterUtils = elanClusterUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        init();
    }

    public void init() {
        LOG.info("ElanMacEntryListener L2Gw init()");
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
    }

    @Override
    public void remove(final InstanceIdentifier<MacEntry> identifier, final MacEntry del) {
        LOG.trace("ElanMacEntryListener remove : {}", del);
        elanClusterUtils.runOnlyInOwnerNode(del.getMacAddress().getValue(),
            "Deleting dpn macs from remote ucast mac tables", () -> {
                String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
                ElanInstance elanInstance = elanInstanceCache.get(elanName).orElse(null);
                elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInstance,
                        Lists.newArrayList(del.getMacAddress()));
                return Collections.emptyList();
            });
    }

    @Override
    public void update(InstanceIdentifier<MacEntry> identifier, MacEntry original, MacEntry update) {
    }

    //Using mac entry clustered listener instead of elan interface listener to avoid race conditions
    //always use clustered listener to programme l2gw device
    @Override
    public void add(InstanceIdentifier<MacEntry> identifier, MacEntry add) {
        LOG.trace("ElanMacEntryListener add : {}", add);
        elanClusterUtils.runOnlyInOwnerNode("Adding dpn macs to remote ucast mac tables", () -> {
            String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
            ElanInstance elanInstance = elanInstanceCache.get(elanName).orElse(null);
            if (ElanUtils.isVxlanNetworkOrVxlanSegment(elanInstance)) {
                Uint64 dpId = elanL2GatewayUtils.getDpidFromInterface(add.getInterface());
                elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanName, dpId,
                        Lists.newArrayList(add.getMacAddress()));
            }
        });
    }
}
