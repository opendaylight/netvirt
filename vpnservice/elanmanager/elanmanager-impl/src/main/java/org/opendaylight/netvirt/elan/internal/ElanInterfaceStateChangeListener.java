/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;


import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnel.list.InternalTunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        InterfaceInfo interfaceInfo = new InterfaceInfo(dpId, nodeConnectorId.getValue());
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceType(InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        interfaceInfo.setInterfaceTag(delIf.getIfIndex());
        String elanInstanceName = elanInterface.getElanInstanceName();
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        ElanInterfaceRemoveWorker removeWorker = new ElanInterfaceRemoveWorker(elanInstanceName, elanInstance, 
                interfaceName, interfaceInfo, elanInterfaceManager);
        coordinator.enqueueJob(elanInstanceName, removeWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        logger.trace("Operation Interface update event - Old: {}, New: {}", original, update);
        String interfaceName = update.getName();
        if (update.getType() == null) {
            logger.trace("Interface type for interface {} is null", interfaceName);
            return;
        }
        if(update.getType().equals(Tunnel.class)) {
            if (update.getOperStatus().equals(Interface.OperStatus.Up)) {
                InternalTunnel internalTunnel = getTunnelState(interfaceName);
                if (internalTunnel != null) {
                    elanInterfaceManager.handleInternalTunnelStateEvent(internalTunnel.getSourceDPN(), internalTunnel.getDestinationDPN());
                }
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
                        elanInterfaceManager.handleInternalTunnelStateEvent(internalTunnel.getSourceDPN(),
                                internalTunnel.getDestinationDPN());
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
        if (tunnelList != null && tunnelList.getInternalTunnel() != null) {
            List<InternalTunnel> internalTunnels = tunnelList.getInternalTunnel();
            for (InternalTunnel tunnel : internalTunnels) {
                if (tunnel.getTunnelInterfaceName().equalsIgnoreCase(interfaceName)) {
                    internalTunnel = tunnel;
                    break;
                }
            }
        }
        return internalTunnel;
    }
}
