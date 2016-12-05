/*
 * Copyright (c) 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnDpnToTransportZoneListener
        extends AsyncDataTreeChangeListenerBase<VpnToDpnList, VpnDpnToTransportZoneListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnDpnToTransportZoneListener.class);
    private TransportZoneNotificationUtil transportZoneNotificationUtil;
    private DataBroker dbx;
    private Boolean useTransportZone;

    public VpnDpnToTransportZoneListener(final DataBroker dbx, final IInterfaceManager interfaceManager,
            final ElanConfig elanConfig, final TransportZoneNotificationUtil tznu) {
        useTransportZone = elanConfig.isUseTransportZone();
        transportZoneNotificationUtil = tznu;
        this.dbx = dbx;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());

        if (useTransportZone) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<VpnToDpnList> getWildCardPath() {
        return InstanceIdentifier.builder(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class)
                .child(VpnToDpnList.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList del) {
    }

    @Override
    protected void update(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList original, VpnToDpnList update) {
        LOG.debug("Vpn dpn {} update detected, updating transport zones", update);

        if (update.getVpnInterfaces() == null || update.getVpnInterfaces().isEmpty()) {
            LOG.debug("Vpn dpn {} doesn't contain any vpn interfaces", update);
            return;
        }

        boolean shouldCreateVtep;
        if (original.getVpnInterfaces() != null || original.getVpnInterfaces().isEmpty()) {
            shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(update.getVpnInterfaces().stream()
                    .filter(vi -> !original.getVpnInterfaces().contains(vi)).collect(Collectors.toList()));
        } else {
            shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(update.getVpnInterfaces());
        }

        if (shouldCreateVtep) {
            String vrfId = identifier.firstKeyOf(VpnInstanceOpDataEntry.class).getVrfId();
            transportZoneNotificationUtil.updateTransportZone(vrfId, update.getDpnId());
        }
    }

    @Override
    protected void add(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList add) {
        LOG.debug("Vpn dpn {} add detected, updating transport zones", add);

        boolean shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(add.getVpnInterfaces());
        if (shouldCreateVtep) {
            String vrfId = identifier.firstKeyOf(VpnInstanceOpDataEntry.class).getVrfId();
            transportZoneNotificationUtil.updateTransportZone(vrfId, add.getDpnId());
        }
    }

    @Override
    protected VpnDpnToTransportZoneListener getDataTreeChangeListener() {
        return VpnDpnToTransportZoneListener.this;
    }
}
