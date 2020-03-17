/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacTableCache extends AbstractClusteredAsyncDataTreeChangeListener<MacTable> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanMacTableCache.class);
    private final DataBroker dataBroker;
    private final ConcurrentHashMap<String, MacTable> macsByElan = new ConcurrentHashMap<>();

    @Inject
    public ElanMacTableCache(final DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanForwardingTables.class)
                .child(MacTable.class),
                Executors.newListeningSingleThreadExecutor("ElanMacTableCache", LOG));
        this.dataBroker = dataBroker;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<MacTable> key, MacTable mac) {
        macsByElan.remove(mac.getElanInstanceName());
    }

    @Override
    public void update(InstanceIdentifier<MacTable> key, MacTable old, MacTable mac) {
        macsByElan.put(mac.getElanInstanceName(), mac);
    }

    @Override
    public void add(InstanceIdentifier<MacTable> key, MacTable mac) {
        macsByElan.put(mac.getElanInstanceName(), mac);
    }

    public MacTable getByElanName(String name) {
        return macsByElan.get(name);
    }
}