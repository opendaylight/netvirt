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
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.config.rev160806.NeutronvpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class InterfaceStateToTransportZoneListener extends AsyncDataTreeChangeListenerBase<Interface, InterfaceStateToTransportZoneListener> implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable{

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateToTransportZoneListener.class);
    private InterfaceStateManager ism;

    public InterfaceStateToTransportZoneListener(DataBroker dbx, NeutronvpnManager nvManager) {
        super(Interface.class, InterfaceStateToTransportZoneListener.class);
        ism = new InterfaceStateManager(dbx, nvManager);
        Optional<NeutronvpnConfig> nvsConfig = MDSALDataStoreUtils.read(dbx,
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(NeutronvpnConfig.class));
        Boolean useTZ = true;
        if (nvsConfig.isPresent()) {
            useTZ = nvsConfig.get().isUseTransportZone() == null ? true : nvsConfig.get().isUseTransportZone();
        }
        if (isAutoTunnelConfigEnabled(useTZ)) {
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
    
    private boolean isAutoTunnelConfigEnabled(Boolean useTZ) {
        if (useTZ) {
            LOG.info("using automatic tunnel configuration");
        } else {
            LOG.info("don't use automatic tunnel configuration");
        }
        return useTZ;
    }

}