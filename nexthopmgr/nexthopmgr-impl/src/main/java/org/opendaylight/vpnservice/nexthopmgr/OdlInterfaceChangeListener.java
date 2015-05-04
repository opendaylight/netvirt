/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.L3tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL3tunnel;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.nexthopmgr.AbstractDataChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OdlInterfaceChangeListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(OdlInterfaceChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NexthopManager nexthopManager;
    private IInterfaceManager interfaceManager;


    public OdlInterfaceChangeListener(final DataBroker db, NexthopManager nhm, IInterfaceManager ifManager) {
        super(Interface.class);
        broker = db;
        nexthopManager = nhm;
        interfaceManager = ifManager;
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
        LOG.info("odlInterface listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), OdlInterfaceChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Nexthop Manager Interfaces DataChange listener registration fail!", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.info("key: " + identifier + ", value=" + intrf );

        if (intrf.getType().equals(L3tunnel.class)) {
            IfL3tunnel intfData = intrf.getAugmentation(IfL3tunnel.class);
            String gwIp = intfData.getGatewayIp().toString();
            String remoteIp = intfData.getRemoteIp().toString();
            if (gwIp != null) {
                remoteIp = gwIp;
            }
            NodeConnectorId ofPort = intrf.getAugmentation(BaseIds.class).getOfPortId();
            nexthopManager.createRemoteNextHop(intrf.getName(), ofPort.toString(), remoteIp);
        }
    }


    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier,
            Interface intrf) {
        if (intrf.getType().equals(L3tunnel.class)) {
            long dpnId = interfaceManager.getDpnForInterface(intrf.getName());
            IfL3tunnel intfData = intrf.getAugmentation(IfL3tunnel.class);
            String gwIp = intfData.getGatewayIp().toString();
            String remoteIp = intfData.getRemoteIp().toString();
            if (gwIp != null) {
                remoteIp = gwIp;
            }
            nexthopManager.removeRemoteNextHop(dpnId, remoteIp);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // TODO Auto-generated method stub

    }

}