/*
 * Copyright (c) 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceToTransportZoneListener
    extends AsyncDataTreeChangeListenerBase<Interface, InterfaceToTransportZoneListener>
    implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceToTransportZoneListener.class);
    private TransportZoneNotificationUtil ism;
    private DataBroker dbx;

    public InterfaceToTransportZoneListener(DataBroker dbx, NeutronvpnManager nvManager) {
        super(Interface.class, InterfaceToTransportZoneListener.class);
        ism = new TransportZoneNotificationUtil(dbx, nvManager);
        this.dbx = dbx;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        if (ism.isAutoTunnelConfigEnabled()) {
            registerListener(LogicalDatastoreType.CONFIGURATION, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }


    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        // once the TZ is declared it will stay forever
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        ism.updateTransportZone(update);
    }


    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        ism.updateTransportZone(add);
    }

    @Override
    protected InterfaceToTransportZoneListener getDataTreeChangeListener() {
        return InterfaceToTransportZoneListener.this;
    }

}
