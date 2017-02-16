/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.cache.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.cache.DataStoreCache;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to changes in the Vpn instance Operational data so that this data can be updated if needed.
 */
public class VpnOpInstanceCacheManager
    extends AsyncClusteredDataTreeChangeListenerBase<VpnInstanceOpDataEntry, VpnOpInstanceCacheManager>
    implements AutoCloseable {

    private final DataBroker dataBroker;

    private static final Logger LOG = LoggerFactory.getLogger(VpnOpInstanceCacheManager.class);

    public VpnOpInstanceCacheManager(final DataBroker broker) {
        this.dataBroker = broker;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        DataStoreCache.create(VpnConstants.VPN_OP_INSTANCE_CACHE_NAME);
        registerListener(LogicalDatastoreType.OPERATIONAL, this.dataBroker);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry del) {
        DataStoreCache.remove(VpnConstants.VPN_OP_INSTANCE_CACHE_NAME, del.getVrfId());
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry original,
        VpnInstanceOpDataEntry update) {
        DataStoreCache.add(VpnConstants.VPN_OP_INSTANCE_CACHE_NAME, update.getVrfId(), update);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry add) {
        DataStoreCache.add(VpnConstants.VPN_OP_INSTANCE_CACHE_NAME, add.getVrfId(), add);
    }

    @Override
    protected InstanceIdentifier<VpnInstanceOpDataEntry> getWildCardPath() {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class).build();
    }

    @Override
    protected VpnOpInstanceCacheManager getDataTreeChangeListener() {
        return this;
    }


}
