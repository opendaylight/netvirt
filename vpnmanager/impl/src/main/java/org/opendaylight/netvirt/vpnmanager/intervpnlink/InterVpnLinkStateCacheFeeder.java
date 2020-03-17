/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clustered listener whose only purpose is to keep global (well, per cluster)
 * caches updated. Same as InterVpnLinkCacheFeeder but this listens on
 * InterVpnLinkState changes.
 */
@Singleton
public class InterVpnLinkStateCacheFeeder extends AbstractClusteredAsyncDataTreeChangeListener<InterVpnLinkState> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkStateCacheFeeder.class);

    private final InterVpnLinkCache interVpnLinkCache;
    private final DataBroker dataBroker;

    @Inject
    public InterVpnLinkStateCacheFeeder(final DataBroker dataBroker, final InterVpnLinkCache interVpnLinkCache) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(InterVpnLinkStates.class)
                .child(InterVpnLinkState.class),
                Executors.newListeningSingleThreadExecutor("InterVpnLinkStateCacheFeeder", LOG));
        this.dataBroker = dataBroker;
        this.interVpnLinkCache = interVpnLinkCache;
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState del) {
        LOG.debug("InterVpnLinkState {} has been removed", del.getInterVpnLinkName());
        interVpnLinkCache.removeInterVpnLinkStateFromCache(del);
    }

    @Override
    public void update(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState original,
        InterVpnLinkState update) {
        LOG.debug("InterVpnLinkState {} has been updated", update.getInterVpnLinkName());
        interVpnLinkCache.addInterVpnLinkStateToCaches(update);
    }

    @Override
    public void add(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState add) {
        LOG.debug("InterVpnLinkState {} has been added", add.getInterVpnLinkName());
        interVpnLinkCache.addInterVpnLinkStateToCaches(add);
    }

}
