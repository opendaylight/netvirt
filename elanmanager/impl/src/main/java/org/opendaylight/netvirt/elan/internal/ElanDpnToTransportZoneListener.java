/*
 * Copyright (c) 2017, 2019 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanDpnToTransportZoneListener
        extends AbstractAsyncDataTreeChangeListener<DpnInterfaces> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnToTransportZoneListener.class);
    private final TransportZoneNotificationUtil transportZoneNotificationUtil;
    private final DataBroker dbx;
    private final Boolean useTransportZone;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanDpnToTransportZoneListener(final DataBroker dbx,
            final ElanConfig elanConfig, final TransportZoneNotificationUtil tznu,
            final ElanInstanceCache elanInstanceCache) {
        super(dbx, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class).child(DpnInterfaces.class),
                Executors.newListeningSingleThreadExecutor("ElanDpnToTransportZoneListener", LOG));
        useTransportZone = elanConfig.isAutoConfigTransportZones();
        transportZoneNotificationUtil = tznu;
        this.dbx = dbx;
        this.elanInstanceCache = elanInstanceCache;
    }

    public void start() {
        if (useTransportZone) {
            LOG.info("{} registered", getClass().getSimpleName());
        }
    }

    @Override
    public void remove(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
        LOG.debug("Elan dpn {} delete detected, deleting transport zones", dataObjectModification.getDpId());
        Uint64 dpId = dataObjectModification.getDpId();
        String elanInstanceName = key.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();

        if (!ElanUtils.isVxlanNetworkOrVxlanSegment(elanInstanceCache.get(elanInstanceName).orElse(null))) {
            LOG.debug("ElanInstance {} is not vxlan network, nothing to do", elanInstanceName);
            return;
        }
        LOG.debug("Deleting tz for elanInstance {} dpId {}", elanInstanceName, dpId);
        transportZoneNotificationUtil.deleteTransportZone(elanInstanceName, dpId);

    }

    @Override
    public void update(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModificationBefore,
            DpnInterfaces dataObjectModificationAfter) {
    }

    @Override
    public void add(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
        LOG.debug("Elan dpn {} add detected, updating transport zones", dataObjectModification.getDpId());

        Uint64 dpId = dataObjectModification.getDpId();
        String elanInstanceName = key.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();

        if (!ElanUtils.isVxlanNetworkOrVxlanSegment(elanInstanceCache.get(elanInstanceName).orElse(null))) {
            return;
        }

        transportZoneNotificationUtil.updateTransportZone(elanInstanceName, dpId);
    }
}
