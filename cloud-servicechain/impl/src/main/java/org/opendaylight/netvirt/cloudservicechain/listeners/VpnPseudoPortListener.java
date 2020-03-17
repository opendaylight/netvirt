/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnPseudoPortCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.VpnToPseudoPortList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to changes in the Vpn to VpnPseudoPort relationship with the only
 * purpose of updating the VpnPseudoPorts caches in all blades.
 *
 */
@Singleton
public class VpnPseudoPortListener
        extends AsyncClusteredDataTreeChangeListenerBase<VpnToPseudoPortData, VpnPseudoPortListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnPseudoPortListener.class);
    private final DataBroker dataBroker;
    private final VpnPseudoPortCache vpnPseudoPortCache;

    @Inject
    public VpnPseudoPortListener(final DataBroker dataBroker, final VpnPseudoPortCache vpnPseudoPortCache) {
        this.dataBroker = dataBroker;
        this.vpnPseudoPortCache = vpnPseudoPortCache;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnToPseudoPortData> identifier, VpnToPseudoPortData del) {
        LOG.trace("Reacting to VpnToPseudoPortData removal: iid={}", identifier);
        vpnPseudoPortCache.remove(del.getVrfId());
    }

    @Override
    protected void update(InstanceIdentifier<VpnToPseudoPortData> identifier, VpnToPseudoPortData original,
                          VpnToPseudoPortData update) {
        vpnPseudoPortCache.add(update.getVrfId(), update.getVpnLportTag());
    }

    @Override
    protected void add(InstanceIdentifier<VpnToPseudoPortData> identifier, VpnToPseudoPortData add) {
        LOG.trace("Reacting to VpnToPseudoPortData creation:  vrf={}  vpnPseudoLportTag={}  scfTag={}  scfTable={}.",
                  add.getVrfId(), add.getVpnLportTag(), add.getScfTag(), add.getScfTableId());
        vpnPseudoPortCache.add(add.getVrfId(), add.getVpnLportTag());
    }

    @Override
    protected InstanceIdentifier<VpnToPseudoPortData> getWildCardPath() {
        return InstanceIdentifier.builder(VpnToPseudoPortList.class).child(VpnToPseudoPortData.class).build();
    }

    @Override
    protected VpnPseudoPortListener getDataTreeChangeListener() {
        return this;
    }



}
