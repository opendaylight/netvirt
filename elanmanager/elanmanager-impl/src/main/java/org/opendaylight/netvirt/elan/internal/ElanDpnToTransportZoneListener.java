/*
 * Copyright (c) 2017 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanDpnToTransportZoneListener
        extends AsyncDataTreeChangeListenerBase<DpnInterfaces, ElanDpnToTransportZoneListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnToTransportZoneListener.class);
    private final TransportZoneNotificationUtil transportZoneNotificationUtil;
    private final DataBroker dbx;
    private final Boolean useTransportZone;

    @Inject
    public ElanDpnToTransportZoneListener(final DataBroker dbx, final IInterfaceManager interfaceManager,
            final ElanConfig elanConfig, final TransportZoneNotificationUtil tznu) {
        useTransportZone = elanConfig.isAutoConfigTransportZones();
        transportZoneNotificationUtil = tznu;
        this.dbx = dbx;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());

        if (useTransportZone) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
        LOG.debug("Elan dpn {} delete detected, deleting transport zones", dataObjectModification.getDpId());
        BigInteger dpId = dataObjectModification.getDpId();
        String elanInstanceName = key.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();

        if (!ElanUtils.isVxlanNetwork(dbx, elanInstanceName)) {
            LOG.debug("ElanInstance {} is not vxlan network, nothing to do", elanInstanceName);
            return;
        }
        LOG.debug("Deleting tz for elanInstance {} dpId {}", elanInstanceName, dpId);
        transportZoneNotificationUtil.deleteTransportZone(elanInstanceName, dpId);

    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModificationBefore,
            DpnInterfaces dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
        LOG.debug("Elan dpn {} add detected, updating transport zones", dataObjectModification.getDpId());

        BigInteger dpId = dataObjectModification.getDpId();
        String elanInstanceName = key.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();

        if (!ElanUtils.isVxlanNetwork(dbx, elanInstanceName)) {
            return;
        }

        transportZoneNotificationUtil.updateTransportZone(elanInstanceName, dpId);
    }

    @Override
    protected ElanDpnToTransportZoneListener getDataTreeChangeListener() {
        return ElanDpnToTransportZoneListener.this;
    }
}
