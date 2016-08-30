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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.config.rev160806.NeutronvpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class NeutronRouterDpnsToTransportZoneListener extends AsyncDataTreeChangeListenerBase<RouterDpnList, NeutronRouterDpnsToTransportZoneListener> implements ClusteredDataTreeChangeListener<RouterDpnList>, AutoCloseable{

    private ToTransportZoneManagerManager ism;

    public NeutronRouterDpnsToTransportZoneListener(DataBroker dbx, NeutronvpnManager nvManager) {
        super(RouterDpnList.class, NeutronRouterDpnsToTransportZoneListener.class);
        ism = new ToTransportZoneManagerManager(dbx, nvManager);

        if (ism.isAutoTunnelConfigEnabled()) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }

    }

    @Override
    protected InstanceIdentifier<RouterDpnList> getWildCardPath() {
        return InstanceIdentifier.create(NeutronRouterDpns.class).child(RouterDpnList.class);
    }


    @Override
    protected void remove(InstanceIdentifier<RouterDpnList> identifier, RouterDpnList del) {
        // FIXME: once the TZ is declared it will stay forever

    }

    @Override
    protected void update(InstanceIdentifier<RouterDpnList> identifier, RouterDpnList original, RouterDpnList update) {
        ism.updateTrasportZone(update);
    }


    @Override
    protected void add(InstanceIdentifier<RouterDpnList> identifier, RouterDpnList add) {
        ism.updateTrasportZone(add);
    }

    @Override
    protected NeutronRouterDpnsToTransportZoneListener getDataTreeChangeListener() {
        return NeutronRouterDpnsToTransportZoneListener.this;
    }

}