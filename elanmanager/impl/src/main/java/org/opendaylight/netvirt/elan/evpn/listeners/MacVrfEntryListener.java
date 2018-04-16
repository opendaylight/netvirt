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
import org.opendaylight.netvirt.elan.evpn.utils.EvpnMacVrfUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * When RT2 route (advertise of withdraw) is received from peer side.
 * BGPManager will receive the RT2 msg.
 * It will check if the EVPN is configured and Network is attached to EVPN or not.
 * BGPManager will write in path (FibEntries.class).child(VrfTables.class).child(MacVrfEntry.class)
 * which MacVrfEntryListener is listening to.
 * When RT2 advertise route is received: add method of MacVrfEntryListener will install DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 * When RT2 withdraw route is received: remove method of MacVrfEntryListener will remove DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 */
@Singleton
public class MacVrfEntryListener extends AsyncDataTreeChangeListenerBase<MacVrfEntry, MacVrfEntryListener> {
    private static final Logger LOG = LoggerFactory.getLogger(MacVrfEntryListener.class);
    private final DataBroker broker;
    private final EvpnMacVrfUtils evpnMacVrfUtils;

    @Inject
    public MacVrfEntryListener(final DataBroker broker, final EvpnMacVrfUtils evpnMacVrfUtils) {
        this.broker = broker;
        this.evpnMacVrfUtils = evpnMacVrfUtils;

    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<MacVrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class)
                .child(VpnInstanceNames.class)
                .child(VrfTables.class).child(MacVrfEntry.class);
    }

    @Override
    protected MacVrfEntryListener getDataTreeChangeListener() {
        return MacVrfEntryListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        LOG.info("ADD: Adding DMAC Entry for MACVrfEntry {} ", macVrfEntry);
        evpnMacVrfUtils.addEvpnDmacFlow(instanceIdentifier, macVrfEntry);
    }

    @Override
    protected void update(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry, MacVrfEntry t1) {

    }

    @Override
    protected void remove(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        LOG.info("REMOVE: Removing DMAC Entry for MACVrfEntry {} ", macVrfEntry);
        evpnMacVrfUtils.removeEvpnDmacFlow(instanceIdentifier, macVrfEntry);
    }
}
