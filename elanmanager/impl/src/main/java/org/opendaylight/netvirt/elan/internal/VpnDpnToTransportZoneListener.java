/*
 * Copyright (c) 2017, 2019 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpnOpElements;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.Vpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.Dpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnDpnToTransportZoneListener extends AbstractAsyncDataTreeChangeListener<Dpns> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnDpnToTransportZoneListener.class);
    private final TransportZoneNotificationUtil transportZoneNotificationUtil;
    private final DataBroker dbx;
    private final Boolean useTransportZone;

    @Inject
    public VpnDpnToTransportZoneListener(final DataBroker dbx,
            final ElanConfig elanConfig, final TransportZoneNotificationUtil tznu) {
        super(dbx, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(DpnOpElements.class)
                .child(Vpns.class).child(Dpns.class),
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
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<Dpns> identifier, Dpns del) {
        if (!useTransportZone) {
            return;
        }
        LOG.debug("Vpn dpn {} remove detected, SHOULD BE deleting transport zones", del.getDpnId());
    }

    @Override
    public void update(InstanceIdentifier<Dpns> identifier, Dpns original, Dpns update) {
        if (!useTransportZone) {
            return;
        }
        LOG.debug("Vpn dpn {} update detected, updating transport zones", update.getDpnId());

        if (update.getVpnInterfaces() == null || update.getVpnInterfaces().isEmpty()) {
            LOG.debug("Vpn dpn {} doesn't contain any vpn interfaces", update.getDpnId());
            return;
        }

        boolean shouldCreateVtep;
        if (original.getVpnInterfaces() != null && !original.getVpnInterfaces().isEmpty()) {
            shouldCreateVtep = transportZoneNotificationUtil
                    .shouldCreateVtep(update.nonnullVpnInterfaces().values().stream()
                    .filter(vi -> !original.nonnullVpnInterfaces().values().contains(vi)).collect(Collectors.toList()));
        } else {
            shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(
                    new ArrayList<>(update.nonnullVpnInterfaces().values()));
        }

        if (shouldCreateVtep) {
            String vrfId = identifier.firstKeyOf(VpnInstanceOpDataEntry.class).getVrfId();
            transportZoneNotificationUtil.updateTransportZone(vrfId, update.getDpnId());
        }
    }

    @Override
    public void add(InstanceIdentifier<Dpns> identifier, Dpns add) {
        if (!useTransportZone) {
            return;
        }
        LOG.debug("Vpn dpn {} add detected, updating transport zones", add.getDpnId());

        boolean shouldCreateVtep = transportZoneNotificationUtil.shouldCreateVtep(
                new ArrayList<>(add.nonnullVpnInterfaces().values()));
        if (shouldCreateVtep) {
            String vrfId = identifier.firstKeyOf(VpnInstanceOpDataEntry.class).getVrfId();
            transportZoneNotificationUtil.updateTransportZone(vrfId, add.getDpnId());
        }
    }
}
