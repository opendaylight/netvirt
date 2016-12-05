/*
 * Copyright (c) 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnToTransportZoneListener
        extends AsyncDataTreeChangeListenerBase<VpnInstanceOpDataEntry, VpnToTransportZoneListener>
        implements ClusteredDataTreeChangeListener<VpnInstanceOpDataEntry>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnToTransportZoneListener.class);
    private TransportZoneNotificationUtil ism;
    private DataBroker dbx;
    private Boolean useTransportZone;

    public VpnToTransportZoneListener(DataBroker dbx, IInterfaceManager interfaceManager, ElanConfig elanConfig) {
        super(VpnInstanceOpDataEntry.class, VpnToTransportZoneListener.class);

        useTransportZone = elanConfig.isUseTransportZone();
        ism = new TransportZoneNotificationUtil(dbx, interfaceManager, elanConfig);
        this.dbx = dbx;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());

        if (useTransportZone) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<VpnInstanceOpDataEntry> getWildCardPath() {
        return InstanceIdentifier.builder(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry del) {
        // once the TZ is declared it will stay forever
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry original,
            VpnInstanceOpDataEntry update) {
        LOG.debug("VPN {} update detected, updating transport zones", update);
        ism.updateTransportZone(update);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry add) {
        LOG.debug("VPN {} add detected, updating transport zones", add);
        ism.updateTransportZone(add);
    }

    @Override
    protected VpnToTransportZoneListener getDataTreeChangeListener() {
        return VpnToTransportZoneListener.this;
    }
}
