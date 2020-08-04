/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.cache;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ItmExternalTunnelCache extends InstanceIdDataObjectCache<ExternalTunnel> {
    private static final Logger LOG = LoggerFactory.getLogger(ItmExternalTunnelCache.class);
    //private final DataBroker dataBroker;
    private final Map<String, ExternalTunnel> externalTunnelsByName = new ConcurrentHashMap<>();

    @Inject
    public ItmExternalTunnelCache(DataBroker dataBroker, CacheProvider cacheProvider) {
        super(ExternalTunnel.class, dataBroker, LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(ExternalTunnelList.class).child(ExternalTunnel.class), cacheProvider);
        //this.dataBroker = dataBroker;
    }

    /*@PostConstruct
    public void init() throws ReadFailedException {
        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        try {
            Optional<ExternalTunnelList> data = tx.read(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(ExternalTunnelList.class).build()).checkedGet();
            if (data.isPresent() && data.get().getExternalTunnel() != null) {
                data.get().getExternalTunnel().stream().forEach(tunnel -> add(null, tunnel));
            }
        } finally {
            tx.close();
        }
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }*/

    @Override
    protected void removed(InstanceIdentifier<ExternalTunnel> key, ExternalTunnel deleted) {
        externalTunnelsByName.remove(deleted.getTunnelInterfaceName());
    }

    /*@Override
    protected void updated(InstanceIdentifier<ExternalTunnel> key, ExternalTunnel old, ExternalTunnel updated) {
        externalTunnelsByName.put(updated.getTunnelInterfaceName(), updated);
    }*/

    @Override
    protected synchronized void added(InstanceIdentifier<ExternalTunnel> key, ExternalTunnel added) {
        externalTunnelsByName.put(added.getTunnelInterfaceName(), added);
    }

    public Optional<ExternalTunnel> getExternalTunnel(String key) {
        if (externalTunnelsByName.containsKey(key)) {
            return Optional.of(externalTunnelsByName.get(key));
        }
        return Optional.absent();
    }
}
