/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacEntryListener extends AsyncDataTreeChangeListenerBase<MacEntry, ElanMacEntryListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanMacEntryListener.class);
    private final DataBroker broker;
    private final EvpnUtils evpnUtils;

    @Inject
    public ElanMacEntryListener(final DataBroker broker, final EvpnUtils evpnUtils) {
        this.broker = broker;
        this.evpnUtils = evpnUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected InstanceIdentifier<MacEntry> getWildCardPath() {
        return InstanceIdentifier.builder(ElanForwardingTables.class)
                .child(MacTable.class).child(MacEntry.class).build();
    }

    @Override
    protected ElanMacEntryListener getDataTreeChangeListener() {
        return ElanMacEntryListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry) {
        LOG.info("ElanMacEntryListener : ADD macEntry {} ", instanceIdentifier);
        String elanName = instanceIdentifier.firstKeyOf(MacTable.class).getElanInstanceName();
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(broker, elanName);
        if (EvpnUtils.getEvpnNameFromElan(elanInfo) == null) {
            LOG.trace("ElanMacEntryListener : Add evpnName is null for elan {} ", elanInfo);
            return;
        }
        evpnUtils.advertisePrefix(elanInfo, macEntry);
    }

    @Override
    protected void remove(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry) {
        LOG.info("ElanMacEntryListener : remove macEntry {} ", instanceIdentifier);
        String elanName = instanceIdentifier.firstKeyOf(MacTable.class).getElanInstanceName();
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(broker, elanName);
        if (EvpnUtils.getEvpnNameFromElan(elanInfo) == null) {
            LOG.trace("ElanMacEntryListener : Remove evpnName is null for elan {} ", elanInfo);
            return;
        }
        evpnUtils.withdrawPrefix(elanInfo, macEntry);
    }

    @Override
    protected void update(InstanceIdentifier<MacEntry> instanceIdentifier, MacEntry macEntry, MacEntry t1) {
    }
}
