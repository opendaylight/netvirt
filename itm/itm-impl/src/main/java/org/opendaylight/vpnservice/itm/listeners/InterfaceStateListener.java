/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TepTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.StateTunnelListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.StateTunnelListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.state.tunnel.list.DstInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnels_state.state.tunnel.list.SrcInfoBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.net.InetAddresses;

public class InterfaceStateListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;

    public InterfaceStateListener(final DataBroker db) {
        super(Interface.class);
        broker = db;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up interface state listener", e);
            }
            listenerRegistration = null;
        }
        LOG.info("Interface state listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), InterfaceStateListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("ITM Interfaces State listener registration fail!", e);
            throw new IllegalStateException("ITM Interfaces State listener registration failed.", e);
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface iface) {
        LOG.trace("Interface added: {}", iface);
        if(ItmUtils.isItmIfType(iface.getType())) {
            LOG.debug("Interface of type Tunnel added: {}", iface.getName());
            updateItmState(iface);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier,
            Interface iface) {
        LOG.trace("Interface deleted: {}", iface);
        if(ItmUtils.isItmIfType(iface.getType())) {
            LOG.debug("Tunnel interface deleted: {}", iface.getName());
            StateTunnelListKey tlKey = null;
            tlKey = ItmUtils.getTunnelStateKey(iface);
            InstanceIdentifier<StateTunnelList> stListId = buildStateTunnelListId(tlKey);
            LOG.trace("Deleting tunnel_state for Id: {}", stListId);
            ItmUtils.asyncDelete(LogicalDatastoreType.OPERATIONAL, stListId, broker, ItmUtils.DEFAULT_CALLBACK);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        /*
         * update contains only delta, may not include iftype
         * Note: This assumes type can't be edited on the fly
         */
        if(ItmUtils.isItmIfType(original.getType())) {
        LOG.trace("Interface updated. Old: {} New: {}", original, update);
            OperStatus operStatus = update.getOperStatus();
            if( operStatus != null ) {
                LOG.debug("Tunnel Interface {} changed state to {}", original.getName(), operStatus);
                updateItmState(update);
            }
        }
    }

    private void updateItmState(Interface iface) {
        StateTunnelListKey tlKey = null;
        tlKey = ItmUtils.getTunnelStateKey(iface);
        LOG.trace("TunnelStateKey: {} for interface: {}", tlKey, iface.getName());
        InstanceIdentifier<StateTunnelList> stListId = buildStateTunnelListId(tlKey);
        Optional<StateTunnelList> tunnelsState = ItmUtils.read(LogicalDatastoreType.OPERATIONAL, stListId, broker);
        StateTunnelList tunnelStateList;
        StateTunnelListBuilder stlBuilder;
        boolean tunnelState = (iface.getOperStatus().equals(OperStatus.Up)) ? (true):(false);
        if(tunnelsState.isPresent()) {
            tunnelStateList = tunnelsState.get();
            stlBuilder = new StateTunnelListBuilder(tunnelStateList);
            stlBuilder.setTunnelState(tunnelState);
            StateTunnelList stList = stlBuilder.build();
            LOG.trace("Updating tunnel_state: {} for Id: {}",stList, stListId);
            ItmUtils.asyncUpdate(LogicalDatastoreType.OPERATIONAL, stListId, stList, broker, ItmUtils.DEFAULT_CALLBACK);
        } else {
            // Create new Tunnel State
            try {
                /*FIXME:
                 * A defensive try-catch to find issues without disrupting existing behavior.
                 */
                tunnelStateList = buildStateTunnelList(tlKey, iface.getName(), tunnelState);
                LOG.trace("Creating tunnel_state: {} for Id: {}", tunnelStateList, stListId);
                ItmUtils.asyncUpdate(LogicalDatastoreType.OPERATIONAL, stListId, tunnelStateList, broker,
                                ItmUtils.DEFAULT_CALLBACK);
            } catch (Exception e) {
                LOG.warn("Exception trying to create tunnel state for {}", iface.getName(), e);
            }
        }
    }

    private StateTunnelList buildStateTunnelList(StateTunnelListKey tlKey, String name, boolean state) {
        StateTunnelListBuilder stlBuilder = new StateTunnelListBuilder();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface =
                        ItmUtils.getInterface(name, broker);
        IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
        ParentRefs parentRefs = iface.getAugmentation(ParentRefs.class);
        if(ifTunnel == null && parentRefs == null) {
            return null;
        }
        DstInfoBuilder dstInfoBuilder = new DstInfoBuilder();
        SrcInfoBuilder srcInfoBuilder = new SrcInfoBuilder();
        dstInfoBuilder.setTepIp(ifTunnel.getTunnelDestination());
        srcInfoBuilder.setTepIp(ifTunnel.getTunnelSource());
        //TODO: Add/Improve logic for device type
        InternalTunnel internalTunnel = ItmUtils.itmCache.getInternalTunnel(name);
        ExternalTunnel externalTunnel = ItmUtils.itmCache.getExternalTunnel(name);
        if(internalTunnel == null && externalTunnel == null) {
            // both not present in cache. let us update and try again.
            ItmUtils.updateTunnelsCache(broker);
            internalTunnel = ItmUtils.itmCache.getInternalTunnel(name);
            externalTunnel = ItmUtils.itmCache.getExternalTunnel(name);
        }
        if(internalTunnel != null) {
            srcInfoBuilder.setTepDeviceId(internalTunnel.getSourceDPN().toString()).setTepDeviceType(TepTypeInternal.class);
            dstInfoBuilder.setTepDeviceId(internalTunnel.getDestinationDPN().toString())
                .setTepDeviceType(TepTypeInternal.class);
            stlBuilder.setTransportType(internalTunnel.getTransportType());
        } else if(externalTunnel != null) {
            ExternalTunnel tunnel = ItmUtils.itmCache.getExternalTunnel(name);
            srcInfoBuilder.setTepDeviceId(tunnel.getSourceDevice())
                .setTepDeviceType(getDeviceType(tunnel.getSourceDevice()));
            dstInfoBuilder.setTepDeviceId(tunnel.getDestinationDevice())
                .setTepDeviceType(getDeviceType(tunnel.getSourceDevice()))
                .setTepIp(ifTunnel.getTunnelDestination());
            stlBuilder.setTransportType(tunnel.getTransportType());
        }
        stlBuilder.setKey(tlKey).setTunnelInterfaceName(name).setTunnelState(state)
            .setDstInfo(dstInfoBuilder.build()).setSrcInfo(srcInfoBuilder.build());
        return stlBuilder.build();
    }

    private Class<? extends TepTypeBase> getDeviceType(String device) {
        if(device.startsWith("hwvtep")) {
            return TepTypeHwvtep.class;
        } else if(InetAddresses.isInetAddress(device)) {
            return TepTypeExternal.class;
        } else {
            return TepTypeInternal.class;
        }
    }

    private InstanceIdentifier<StateTunnelList> buildStateTunnelListId(StateTunnelListKey tlKey) {
        InstanceIdentifier<StateTunnelList> stListId =
                        InstanceIdentifier.builder(TunnelsState.class).child(StateTunnelList.class, tlKey).build();
        return stListId;
    }

}
