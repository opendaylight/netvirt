/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class SubnetRouteInterfaceStateChangeListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private VpnInterfaceManager vpnInterfaceManager;

    public SubnetRouteInterfaceStateChangeListener(final DataBroker db, VpnInterfaceManager vpnInterfaceManager) {
        super(Interface.class);
        broker = db;
        this.vpnInterfaceManager = vpnInterfaceManager;
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
        LOG.info("Interface listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), SubnetRouteInterfaceStateChangeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Interface DataChange listener registration failed", e);
            throw new IllegalStateException("Nexthop Manager registration Listener failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} up event", intrf);
        try {
            String interfaceName = intrf.getName();
            LOG.info("Received port UP event for interface {} ", interfaceName);
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
                    configInterface = InterfaceUtils.getInterface(broker, interfaceName);
            if (configInterface != null) {
                if (!configInterface.getType().equals(Tunnel.class)) {
                    BigInteger dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                    vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceUp(dpnId, intrf.getName());
                }
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling addition for VPN Interface {}. ", intrf.getName(), e);
        }
    }


    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} down event", intrf);
        try {
            String interfaceName = intrf.getName();
            BigInteger dpnId = BigInteger.ZERO;
            LOG.info("Received port DOWN event for interface {} ", interfaceName);
            if (intrf != null && intrf.getType() != null && intrf.getType().equals(Tunnel.class)) {
                //withdraw all prefixes in all vpns for this dpn from bgp
                // FIXME: Blocked until tunnel event[vxlan/gre] support is available
                // vpnInterfaceManager.updatePrefixesForDPN(dpId, VpnInterfaceManager.UpdateRouteAction.WITHDRAW_ROUTE);
            } else {
                try {
                    dpnId = InterfaceUtils.getDpIdFromInterface(intrf);
                } catch (Exception e){
                    LOG.error("Unable to retrieve dpnId from interface operational data store for interface {}. Fetching from vpn interface op data store. ", intrf.getName(), e);
                }
                vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceDown(dpnId, intrf.getName());
            }
        } catch (Exception e) {
            LOG.error("Exception observed in handling deletion of VPN Interface {}. ", intrf.getName(), e);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
                          Interface original, Interface update) {
        LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
        String interfaceName = update.getName();
        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(update);
        if (update != null) {
            if (!update.getType().equals(Tunnel.class)) {
                if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                    vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceUp(dpId, update.getName());
                } else if (update.getOperStatus().equals(Interface.OperStatus.Down)) {
                    if (VpnUtil.isVpnInterfaceConfigured(broker, interfaceName)) {
                        vpnInterfaceManager.getVpnSubnetRouteHandler().onInterfaceDown(dpId, update.getName());
                    }
                }
            }
        }
    }
}
