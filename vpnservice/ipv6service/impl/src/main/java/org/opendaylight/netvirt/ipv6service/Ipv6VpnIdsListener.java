/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6VpnIdsListener
        extends AsyncDataTreeChangeListenerBase<VpnIds, Ipv6VpnIdsListener>
        implements ClusteredDataTreeChangeListener<VpnIds>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6VpnIdsListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    /**
     * Intialize the member variables.
     * @param broker the data broker instance.
     */
    @Inject
    public Ipv6VpnIdsListener(DataBroker broker) {
        super(VpnIds.class, Ipv6VpnIdsListener.class);
        this.dataBroker = broker;
        ifMgr = IfMgr.getIfMgrInstance();
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnIds> getWildCardPath() {
        return InstanceIdentifier.create(VpnIdToVpnInstance.class).child(VpnIds.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnIds> key, VpnIds del) {
        LOG.debug("Notification received for vpnId delete event {}, {}", key, del);
        ifMgr.removeVpnId(del);
    }

    @Override
    protected void update(InstanceIdentifier<VpnIds> key, VpnIds before, VpnIds after) {
        //do nothing
    }

    @Override
    protected void add(InstanceIdentifier<VpnIds> key, VpnIds add) {
        LOG.debug("Notification received for new vpnId add event {}, {}", key, add);
        ifMgr.addVpnId(add);
    }

    @Override
    protected Ipv6VpnIdsListener getDataTreeChangeListener() {
        return Ipv6VpnIdsListener.this;
    }
}
