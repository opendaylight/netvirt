/*
 * Copyright (c) 2015 - 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeRefEntryToTransportZoneListener extends AsyncDataTreeChangeListenerBase<BridgeRefEntry,
    BridgeRefEntryToTransportZoneListener> implements ClusteredDataTreeChangeListener<BridgeRefEntry>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BridgeRefEntryToTransportZoneListener.class);
    private ToTransportZoneManagerManager ism;
    private DataBroker dbx;

    public BridgeRefEntryToTransportZoneListener(DataBroker dbx, NeutronvpnManager nvManager) {
        super(BridgeRefEntry.class, BridgeRefEntryToTransportZoneListener.class);
        this.dbx = dbx;
        ism = new ToTransportZoneManagerManager(dbx, nvManager);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        if (ism.isAutoTunnelConfigEnabled()) {
            registerListener(LogicalDatastoreType.OPERATIONAL, dbx);
        }
    }

    @Override
    protected InstanceIdentifier<BridgeRefEntry> getWildCardPath() {
        InstanceIdentifier.InstanceIdentifierBuilder<BridgeRefEntry> bridgeRefEntryInstanceIdentifierBuilder =
                InstanceIdentifier.builder(BridgeRefInfo.class)
                        .child(BridgeRefEntry.class);
        return bridgeRefEntryInstanceIdentifierBuilder.build();
    }


    @Override
    protected void remove(InstanceIdentifier<BridgeRefEntry> identifier, BridgeRefEntry del) {

    }

    @Override
    protected void update(InstanceIdentifier<BridgeRefEntry> identifier, BridgeRefEntry original,
            BridgeRefEntry update) {
        LOG.info("handle BridgeRefEntry update notification {}", update);
        updateTransportZoneOfEntry(update);
    }

    @Override
    protected void add(InstanceIdentifier<BridgeRefEntry> identifier, BridgeRefEntry add) {
        LOG.info("handle BridgeRefEntry add notification {}", add);
        updateTransportZoneOfEntry(add);
    }

    private void updateTransportZoneOfEntry(BridgeRefEntry entry) {
        BigInteger dpid = entry.getDpid();
        Set<RouterDpnList> allRouterDpnList = getAllRouterDpnList(dpid);
        for (RouterDpnList routerDpnList : allRouterDpnList) {
            ism.updateTrasportZone(routerDpnList);
        }
    }

    private Set<RouterDpnList> getAllRouterDpnList(BigInteger dpid) {
        Set<RouterDpnList> ret = new HashSet<>();
        InstanceIdentifier<NeutronRouterDpns> routerDpnId =
                InstanceIdentifier.create(NeutronRouterDpns.class);
        Optional<NeutronRouterDpns> routerDpnOpt = MDSALUtil.read(dbx, LogicalDatastoreType.OPERATIONAL, routerDpnId);
        if (routerDpnOpt.isPresent()) {
            NeutronRouterDpns neutronRouterDpns = routerDpnOpt.get();
            List<RouterDpnList> routerDpnLists = neutronRouterDpns.getRouterDpnList();
            for (RouterDpnList routerDpnList : routerDpnLists) {
                if (routerDpnList.getDpnVpninterfacesList() != null) {
                    for (DpnVpninterfacesList dpnInterfaceList : routerDpnList.getDpnVpninterfacesList()) {
                        if (dpnInterfaceList.getDpnId().equals(dpid)) {
                            ret.add(routerDpnList);
                        }
                    }
                }
            }
        }
        return ret;
    }

    @Override
    protected BridgeRefEntryToTransportZoneListener getDataTreeChangeListener() {
        return BridgeRefEntryToTransportZoneListener.this;
    }

}
