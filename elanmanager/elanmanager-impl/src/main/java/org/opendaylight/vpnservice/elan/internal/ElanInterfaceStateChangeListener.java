/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;


import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;

public class ElanInterfaceStateChangeListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {
    private DataBroker broker;
    private IInterfaceManager interfaceManager;
    private ElanInterfaceManager elanInterfaceManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private static final Logger logger = LoggerFactory.getLogger(ElanInterfaceStateChangeListener.class);

    public ElanInterfaceStateChangeListener(final DataBroker db, final ElanInterfaceManager ifManager) {
        super(Interface.class);
        broker = db;
        elanInterfaceManager = ifManager;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), ElanInterfaceStateChangeListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("Elan Interfaces DataChange listener registration fail!", e);
            throw new IllegalStateException("ElanInterface registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    private void handleVlanInterfaceOperationalStateChange(String interfaceName, boolean isStateUp) {
        //fetching the elanInstanceName from elan-interface config data-store
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if (elanInterface == null) {
            return;
        }
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanInterface.getElanInstanceName());
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName, 
                InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        if (interfaceInfo == null) {
            logger.warn("Interface {} doesn't exist in operational datastore", interfaceName);
            return;
        }

        logger.trace("ElanService Interface Operational state has changes for Interface:{}", interfaceName);
        elanInterfaceManager.handleInterfaceUpated(interfaceInfo, elanInfo , isStateUp);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
        logger.trace("Received interface {} Down event", delIf);
        String interfaceName =  delIf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if(elanInterface == null) {
            logger.debug("No Elan Interface is created for the interface:{} ", interfaceName);
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(delIf.getLowerLayerIf().get(0));
        BigInteger dpId = MDSALUtil.getDpnIdFromNodeName(nodeConnectorId.getValue());
        InterfaceInfo interfaceInfo = new InterfaceInfo(dpId, nodeConnectorId.getValue());
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceType(InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        interfaceInfo.setInterfaceTag(delIf.getIfIndex());
        elanInterfaceManager.removeElanService(elanInterface, interfaceInfo);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        logger.trace("Operation Interface update event - Old: {}, New: {}", original, update);
        String interfaceName = update.getName();
        if(update.getType().equals(Tunnel.class)) {
            if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                InternalTunnel internalTunnel = getTunnelState(interfaceName);
                if (internalTunnel != null) {
                    elanInterfaceManager.handleTunnelStateEvent(internalTunnel.getSourceDPN(), internalTunnel.getDestinationDPN());
                }
            }
        } else if(update.getType().equals(L2vlan.class)) {
            ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
            if(elanInterface == null) {
                logger.debug("No Elan Interface is created for the interface:{} ", interfaceName);
                return;
            }
            if (update.getOperStatus().equals(Interface.OperStatus.Up) && update.getAdminStatus() == Interface.AdminStatus.Up) {
                logger.trace("Operation Status for Interface:{}  event state UP ", interfaceName);
                handleVlanInterfaceOperationalStateChange(interfaceName, true);
            } else if (update.getOperStatus().equals(Interface.OperStatus.Down)) {
                logger.trace("Operation Status for Interface:{}  event state DOWN ", interfaceName);
                handleVlanInterfaceOperationalStateChange(interfaceName, false);
            }

        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        logger.trace("Received interface {} up event", intrf);
        String interfaceName =  intrf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if(elanInterface == null) {
            if (intrf.getType() != null && intrf.getType().equals(Tunnel.class)) {
                if(intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
                    InternalTunnel internalTunnel = getTunnelState(interfaceName);
                    if (internalTunnel != null) {
                        elanInterfaceManager.handleTunnelStateEvent(internalTunnel.getSourceDPN(), internalTunnel.getDestinationDPN());
                    }
                }
            }
            return;
        }
        InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName);
        elanInterfaceManager.add(elanInterfaceId, elanInterface);
    }

    @Override
    public void close() throws Exception {

    }

    public  InternalTunnel getTunnelState(String interfaceName) {
        InternalTunnel internalTunnel = null;
        TunnelList tunnelList = ElanUtils.buildInternalTunnel(broker);
        if (tunnelList.getInternalTunnel() != null) {
            List<InternalTunnel> internalTunnels = tunnelList.getInternalTunnel();
            for (InternalTunnel tunnel : internalTunnels) {
                if (internalTunnel.getTunnelInterfaceName().equalsIgnoreCase(interfaceName)) {
                    internalTunnel = tunnel;
                    break;
                }
            }
        }
        return internalTunnel;
    }
}
