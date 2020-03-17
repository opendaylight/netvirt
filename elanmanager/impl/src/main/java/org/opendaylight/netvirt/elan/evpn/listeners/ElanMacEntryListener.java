/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacEntryListener extends AbstractAsyncDataTreeChangeListener<MacEntry> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanMacEntryListener.class);
    private final DataBroker broker;
    private final EvpnUtils evpnUtils;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanMacEntryListener(final DataBroker broker, final EvpnUtils evpnUtils,
            final ElanInstanceCache elanInstanceCache) {
        super(broker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanForwardingTables.class)
                .child(MacTable.class).child(MacEntry.class),
                Executors.newListeningSingleThreadExecutor("ElanMacEntryListener", LOG));
        this.broker = broker;
        this.evpnUtils = evpnUtils;
        this.elanInstanceCache = elanInstanceCache;
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry) {
        LOG.info("ElanMacEntryListener : ADD macEntry {} ", instanceIdentifier);
        String elanName = instanceIdentifier.firstKeyOf(MacTable.class).getElanInstanceName();
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
        if (EvpnUtils.getEvpnNameFromElan(elanInfo) == null) {
            LOG.trace("ElanMacEntryListener : Add evpnName is null for elan {} ", elanInfo);
            return;
        }
        evpnUtils.advertisePrefix(elanInfo, macEntry);
    }

    @Override
    public void remove(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry) {
        LOG.info("ElanMacEntryListener : remove macEntry {} ", instanceIdentifier);
        String elanName = instanceIdentifier.firstKeyOf(MacTable.class).getElanInstanceName();
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
        if (EvpnUtils.getEvpnNameFromElan(elanInfo) == null) {
            LOG.trace("ElanMacEntryListener : Remove evpnName is null for elan {} ", elanInfo);
            return;
        }
        evpnUtils.withdrawPrefix(elanInfo, macEntry);
    }

    @Override
    public void update(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry, MacEntry t1) {
    }
}
