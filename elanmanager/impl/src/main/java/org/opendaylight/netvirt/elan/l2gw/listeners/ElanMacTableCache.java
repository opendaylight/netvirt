/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacTableCache extends AsyncClusteredDataTreeChangeListenerBase<MacTable, ElanMacTableCache> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanMacTableCache.class);
    private final DataBroker dataBroker;
    private final ConcurrentHashMap<String, MacTable> macsByElan = new ConcurrentHashMap<>();

    @Inject
    public ElanMacTableCache(final DataBroker dataBroker) {
        super(MacTable.class, ElanMacTableCache.class);
        this.dataBroker = dataBroker;
    }

    @PostConstruct
    public void init() {
        this.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<MacTable> getWildCardPath() {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class).build();
    }

    @Override
    protected ElanMacTableCache getDataTreeChangeListener() {
        return ElanMacTableCache.this;
    }

    @Override
    protected void remove(InstanceIdentifier<MacTable> key, MacTable mac) {
        macsByElan.remove(mac.getElanInstanceName());
    }

    @Override
    protected void update(InstanceIdentifier<MacTable> key, MacTable old, MacTable mac) {
        macsByElan.put(mac.getElanInstanceName(), mac);
    }

    @Override
    protected void add(InstanceIdentifier<MacTable> key, MacTable mac) {
        macsByElan.put(mac.getElanInstanceName(), mac);
    }

    public MacTable getByElanName(String name) {
        return macsByElan.get(name);
    }
}