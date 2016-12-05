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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceStateToTransportZoneListener
    extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateToTransportZoneListener>
    implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateToTransportZoneListener.class);
    private TransportZoneNotificationUtil ism;
    private DataBroker dbx;
    private IInterfaceManager interfaceManager;

    public InterfaceStateToTransportZoneListener(DataBroker dbx, IInterfaceManager interfaceManager,
            ElanConfig elanConfig) {
        super(Interface.class, InterfaceStateToTransportZoneListener.class);
        ism = new TransportZoneNotificationUtil(dbx, interfaceManager, elanConfig);
        this.dbx = dbx;
        this.interfaceManager = interfaceManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        if (ism.isAutoTunnelConfigEnabled()) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        // once the TZ is declared it will stay forever
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.debug("Interface state {} update detected, updating transport zones", update);
        ism.updateTransportZone(interfaceManager.getInterfaceInfoFromOperationalDataStore(update.getName()));
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        LOG.debug("Interface state {} add detected, updating transport zones", add);
        ism.updateTransportZone(interfaceManager.getInterfaceInfoFromOperationalDataStore(add.getName()));
    }

    @Override
    protected InterfaceStateToTransportZoneListener getDataTreeChangeListener() {
        return InterfaceStateToTransportZoneListener.this;
    }
}
