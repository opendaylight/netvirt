/*
 * Copyright (c) 2015 - 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class InterfaceStateToTransportZoneListener extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateToTransportZoneListener> implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable{

    private InterfaceStateManager ism;

    public InterfaceStateToTransportZoneListener(DataBroker dbx, NeutronvpnManager nvManager, String autoTunnelConfig) {
        super(Interface.class, InterfaceStateToTransportZoneListener.class);
        ism = new InterfaceStateManager(dbx, nvManager);
        if (isAutoTunnelConfigEnabled(autoTunnelConfig)) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }       

    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }


    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        // FIXME: once the TZ is declared it will stay forever

    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        ism.updateTrasportZone(update);
    }


    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        ism.updateTrasportZone(add);
    }
    
    @Override
    protected InterfaceStateToTransportZoneListener getDataTreeChangeListener() {
        return InterfaceStateToTransportZoneListener.this;
    }
    
    private boolean isAutoTunnelConfigEnabled(String autoTunnelConfig) {
        return !"no".equals(autoTunnelConfig);
    }

}