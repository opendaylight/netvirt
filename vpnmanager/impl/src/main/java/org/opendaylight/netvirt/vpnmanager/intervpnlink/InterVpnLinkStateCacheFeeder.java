/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
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
public class InterVpnLinkStateCacheFeeder
    extends AsyncClusteredDataTreeChangeListenerBase<InterVpnLinkState, InterVpnLinkStateCacheFeeder> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkStateCacheFeeder.class);

    private final InterVpnLinkCache interVpnLinkCache;
    private final DataBroker dataBroker;

    @Inject
    public InterVpnLinkStateCacheFeeder(final DataBroker dataBroker, final InterVpnLinkCache interVpnLinkCache) {
        this.dataBroker = dataBroker;
        this.interVpnLinkCache = interVpnLinkCache;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected void remove(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState del) {
        LOG.debug("InterVpnLinkState {} has been removed", del.getInterVpnLinkName());
        interVpnLinkCache.removeInterVpnLinkStateFromCache(del);
    }

    @Override
    protected void update(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState original,
        InterVpnLinkState update) {
        LOG.debug("InterVpnLinkState {} has been updated", update.getInterVpnLinkName());
        interVpnLinkCache.addInterVpnLinkStateToCaches(update);
    }

    @Override
    protected void add(InstanceIdentifier<InterVpnLinkState> identifier, InterVpnLinkState add) {
        LOG.debug("InterVpnLinkState {} has been added", add.getInterVpnLinkName());
        interVpnLinkCache.addInterVpnLinkStateToCaches(add);
    }

    @Override
    protected InstanceIdentifier<InterVpnLinkState> getWildCardPath() {
        return InstanceIdentifier.create(InterVpnLinkStates.class).child(InterVpnLinkState.class);
    }

    @Override
    protected InterVpnLinkStateCacheFeeder getDataTreeChangeListener() {
        return this;
    }

}
