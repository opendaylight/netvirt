/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;

import java.util.List;
import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.vpnservice.nexthopmgr.AbstractDataChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VpnInterfaceChangeListener extends AbstractDataChangeListener<Adjacencies> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NexthopManager nexthopManager;

    public VpnInterfaceChangeListener(final DataBroker db, NexthopManager nhm) {
        super(Adjacencies.class);
        broker = db;
        nexthopManager = nhm;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("VPN Interface Manager Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), VpnInterfaceChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Nexthop Manager DataChange listener registration fail!", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Adjacencies> identifier,
            Adjacencies adjs) {
        LOG.trace("Adding adjacencies interface : key: " + identifier + ", value=" + adjs );
        InstanceIdentifier<VpnInterface> vpnIfId = identifier.firstIdentifierOf(VpnInterface.class);
        Optional<VpnInterface> vpnIf = read(LogicalDatastoreType.OPERATIONAL, vpnIfId);
        VpnInterface vpnIfData = vpnIf.get();

        List<Adjacency> adjList = adjs.getAdjacency();
        for (Adjacency adjacency : adjList) {
            nexthopManager.createLocalNextHop(
                    vpnIfData.getName(),
                    vpnIfData.getVpnInstanceName(),
                    adjacency.getIpAddress(),
                    adjacency.getMacAddress());
        }
    }


    @Override
    protected void remove(InstanceIdentifier<Adjacencies> identifier,
            Adjacencies adjs) {
        // nexthop group will be removed after fib entry deletion
    }

    @Override
    protected void update(InstanceIdentifier<Adjacencies> identifier,
            Adjacencies original, Adjacencies update) {
        // TODO Auto-generated method stub
    }


    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private InstanceIdentifier<Adjacencies> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class).augmentation(Adjacencies.class);
    }


}