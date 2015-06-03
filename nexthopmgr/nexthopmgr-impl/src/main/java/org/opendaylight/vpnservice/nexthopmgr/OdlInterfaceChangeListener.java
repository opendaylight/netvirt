/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;


import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL3tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.L3tunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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
        LOG.trace("Adding Interface : key: " + identifier + ", value=" + intrf );

        if (intrf.getType().equals(L3tunnel.class)) {
            IfL3tunnel intfData = intrf.getAugmentation(IfL3tunnel.class);
            IpAddress gatewayIp = intfData.getGatewayIp();
            IpAddress remoteIp = (gatewayIp == null) ? intfData.getRemoteIp() : gatewayIp;
            NodeConnectorId ofPort = intrf.getAugmentation(BaseIds.class).getOfPortId();
            nexthopManager.createRemoteNextHop(intrf.getName(), ofPort.toString(), remoteIp.getIpv4Address().getValue());
        }
    }


    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier,
            Interface intrf) {
        LOG.trace("Removing interface : key: " + identifier + ", value=" + intrf );
        if (intrf.getType().equals(L3tunnel.class)) {
            BigInteger dpnId = interfaceManager.getDpnForInterface(intrf);
            IfL3tunnel intfData = intrf.getAugmentation(IfL3tunnel.class);
            IpAddress gatewayIp = intfData.getGatewayIp();
            IpAddress remoteIp = (gatewayIp == null) ? intfData.getRemoteIp() : gatewayIp;
            nexthopManager.removeRemoteNextHop(dpnId, intrf.getName(), remoteIp.getIpv4Address().getValue());
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // TODO Auto-generated method stub

    }

}