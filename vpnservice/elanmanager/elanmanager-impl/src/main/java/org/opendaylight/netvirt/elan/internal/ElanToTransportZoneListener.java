/*
 * Copyright (c) 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.List;
import java.util.stream.Collectors;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanToTransportZoneListener extends AsyncDataTreeChangeListenerBase<Elan, ElanToTransportZoneListener>
        implements ClusteredDataTreeChangeListener<Elan>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanToTransportZoneListener.class);
    private TransportZoneNotificationUtil ism;
    private DataBroker dbx;
    private Boolean useTransportZone;
    private MdsalUtils mdsalUtils;

    public ElanToTransportZoneListener(DataBroker dbx, IInterfaceManager interfaceManager, ElanConfig elanConfig) {
        super(Elan.class, ElanToTransportZoneListener.class);

        useTransportZone = elanConfig.isUseTransportZone();
        ism = new TransportZoneNotificationUtil(dbx, interfaceManager, elanConfig);
        this.dbx = dbx;
        this.mdsalUtils = new MdsalUtils(dbx);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());

        if (useTransportZone) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<Elan> getWildCardPath() {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<Elan> identifier, Elan del) {
        // once the TZ is declared it will stay forever
    }

    @Override
    protected void update(InstanceIdentifier<Elan> identifier, Elan original, Elan update) {
        LOG.debug("ELAN {} update detected, updating transport zones", update);

        if (!checkIfVxlanNetwork(update.getName())) {
            return;
        }

        List<String> newElanInterfaces = update.getElanInterfaces().stream()
                .filter(ei -> !original.getElanInterfaces().contains(ei)).collect(Collectors.toList());

        ism.updateTransportZone(newElanInterfaces);
    }

    @Override
    protected void add(InstanceIdentifier<Elan> identifier, Elan add) {
        LOG.debug("ELAN {} add detected, updating transport zones", add);

        if (!checkIfVxlanNetwork(add.getName())) {
            return;
        }

        ism.updateTransportZone(add.getElanInterfaces());
    }

    @Override
    protected ElanToTransportZoneListener getDataTreeChangeListener() {
        return ElanToTransportZoneListener.this;
    }

    private boolean checkIfVxlanNetwork(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanInstancePath = InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName));
        ElanInstance elanInstance = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, elanInstancePath);
        if (elanInstance == null || !ElanUtils.isVxlan(elanInstance)) {
            LOG.debug("Non-VXLAN elanInstance: " + elanInstanceName);
            return false;
        }

        return true;
    }
}