/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnPseudoPortCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToPseudoPortTagData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.pseudo.port.tag.data.VpnToPseudoPortTag;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to changes in the Vpn to VpnPseudoPort relationship with the only
 * purpose of updating the VpnPseudoPorts caches in all blades
 *
 */
public class VpnPseudoPortListener
    extends AsyncClusteredDataChangeListenerBase<VpnToPseudoPortTag, VpnPseudoPortListener> {

    private static final Logger logger = LoggerFactory.getLogger(VpnPseudoPortListener.class);

    private final DataBroker broker;

    public VpnPseudoPortListener(final DataBroker broker) {
        super(VpnToPseudoPortTag.class, VpnPseudoPortListener.class);
        this.broker = broker;
    }

    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    public void close() throws Exception {
        super.close();
        logger.info("VpnPseudoPort listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<VpnToPseudoPortTag> identifier, VpnToPseudoPortTag del) {
        VpnPseudoPortCache.removeVpnPseudoPortFromCache(del.getVrfId());
    }

    @Override
    protected void update(InstanceIdentifier<VpnToPseudoPortTag> identifier, VpnToPseudoPortTag original,
                          VpnToPseudoPortTag update) {
        VpnPseudoPortCache.addVpnPseudoPortToCache(update.getVrfId(), update.getLportTag().intValue());
    }

    @Override
    protected void add(InstanceIdentifier<VpnToPseudoPortTag> identifier, VpnToPseudoPortTag add) {
        VpnPseudoPortCache.addVpnPseudoPortToCache(add.getVrfId(), add.getLportTag().intValue());
    }

    @Override
    protected InstanceIdentifier<VpnToPseudoPortTag> getWildCardPath() {
        return InstanceIdentifier.builder(VpnToPseudoPortTagData.class).child(VpnToPseudoPortTag.class).build();
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }



}
