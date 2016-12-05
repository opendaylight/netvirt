/*
 * Copyright (c) 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanDpnToTransportZoneListener
        extends AsyncDataTreeChangeListenerBase<DpnInterfaces, ElanDpnToTransportZoneListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnToTransportZoneListener.class);
    private final TransportZoneNotificationUtil transportZoneNotificationUtil;
    private final DataBroker dbx;
    private final Boolean useTransportZone;

    public ElanDpnToTransportZoneListener(final DataBroker dbx, final IInterfaceManager interfaceManager,
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
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModificationBefore,
            DpnInterfaces dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> key, DpnInterfaces dataObjectModification) {
        LOG.debug("Elan dpn {} add detected, updating transport zones", dataObjectModification);

        BigInteger dpId = dataObjectModification.getDpId();
        String elanInstanceName = key.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();

        if (!transportZoneNotificationUtil.checkIfVxlanNetwork(elanInstanceName)) {
            return;
        }

        transportZoneNotificationUtil.updateTransportZone(elanInstanceName, dpId);
    }

    @Override
    protected ElanDpnToTransportZoneListener getDataTreeChangeListener() {
        return ElanDpnToTransportZoneListener.this;
    }
}