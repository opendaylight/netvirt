/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.nexthopmgr;


import java.math.BigInteger;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
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
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), OdlInterfaceChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Nexthop Manager Interfaces DataChange listener registration fail!", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface interfaceInfo) {
        LOG.trace("Adding Interface : key: " + identifier + ", value=" + interfaceInfo );
        // READ interface config information
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface intrf =
                getInterfaceFromConfigDS(nexthopManager, new InterfaceKey(interfaceInfo.getName()));

        if(intrf != null && intrf.getType().equals(Tunnel.class)){
            IfTunnel intfData = intrf.getAugmentation(IfTunnel.class);
            IpAddress gatewayIp = intfData.getTunnelGateway();
            IpAddress remoteIp = (gatewayIp == null) ? intfData.getTunnelDestination() : gatewayIp;
            nexthopManager.createRemoteNextHop(intrf.getName(), remoteIp.getIpv4Address().getValue());
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier,
            Interface interfaceInfo) {
        LOG.trace("Removing interface : key: " + identifier + ", value=" + interfaceInfo );
        // READ interface config information
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface intrf =
                getInterfaceFromConfigDS(nexthopManager, new InterfaceKey(interfaceInfo.getName()));

        if (intrf != null && intrf.getType().equals(Tunnel.class)) {
            BigInteger dpnId = interfaceManager.getDpnForInterface(intrf);
            IfTunnel intfData = intrf.getAugmentation(IfTunnel.class);
            IpAddress gatewayIp = intfData.getTunnelGateway();
            IpAddress remoteIp = (gatewayIp == null) ? intfData.getTunnelDestination() : gatewayIp;
            nexthopManager.removeRemoteNextHop(dpnId, intrf.getName(), remoteIp.getIpv4Address().getValue());
        }
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface getInterfaceFromConfigDS(NexthopManager nexthopManager, InterfaceKey interfaceKey) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> interfaceId =
                getInterfaceIdentifier(interfaceKey);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> interfaceOptional =
                nexthopManager.read(LogicalDatastoreType.CONFIGURATION, interfaceId);
        if (!interfaceOptional.isPresent()) {
            return null;
        }

        return interfaceOptional.get();
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> interfaceInstanceIdentifierBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // TODO Auto-generated method stub

    }

}