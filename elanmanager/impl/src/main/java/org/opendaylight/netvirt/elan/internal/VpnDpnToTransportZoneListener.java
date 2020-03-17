/*
 * Copyright (c) 2017, 2019 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnDpnToTransportZoneListener
        extends AbstractAsyncDataTreeChangeListener<VpnToDpnList> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnDpnToTransportZoneListener.class);
    private final TransportZoneNotificationUtil transportZoneNotificationUtil;
    private final DataBroker dbx;
    private final Boolean useTransportZone;

    @Inject
    public VpnDpnToTransportZoneListener(final DataBroker dbx,
            final ElanConfig elanConfig, final TransportZoneNotificationUtil tznu) {
        super(dbx, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class).child(VpnToDpnList.class),
                Executors.newListeningSingleThreadExecutor("VpnDpnToTransportZoneListener", LOG));
        useTransportZone = elanConfig.isAutoConfigTransportZones();
        transportZoneNotificationUtil = tznu;
        this.dbx = dbx;
        start();
    }

    public void start() {
        if (useTransportZone) {
            LOG.info("{} registered", getClass().getSimpleName());
        }
    }

    @Override
    public void remove(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList del) {
        LOG.debug("Vpn dpn {} remove detected, SHOULD BE deleting transport zones", del.getDpnId());
    }

    @Override
    public void update(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList original, VpnToDpnList update) {
        LOG.debug("Vpn dpn {} update detected, updating transport zones", update.getDpnId());

        if (update.getVpnInterfaces() == null || update.getVpnInterfaces().isEmpty()) {
            LOG.debug("Vpn dpn {} doesn't contain any vpn interfaces", update.getDpnId());
            return;
        }

        boolean shouldCreateVtep;
        if (original.getVpnInterfaces() != null && !original.getVpnInterfaces().isEmpty()) {
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
    public void add(InstanceIdentifier<VpnToDpnList> identifier, VpnToDpnList add) {
        LOG.debug("Vpn dpn {} add detected, updating transport zones", add.getDpnId());

        boolean shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(add.getVpnInterfaces());
        if (shouldCreateVtep) {
            String vrfId = identifier.firstKeyOf(VpnInstanceOpDataEntry.class).getVrfId();
            transportZoneNotificationUtil.updateTransportZone(vrfId, add.getDpnId());
        }
    }
}
