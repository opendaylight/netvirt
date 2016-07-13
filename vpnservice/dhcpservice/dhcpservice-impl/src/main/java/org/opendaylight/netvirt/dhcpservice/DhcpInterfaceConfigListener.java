/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceConfigListener extends AbstractDataChangeListener<Interface> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpInterfaceConfigListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DataBroker dataBroker;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;

    public DhcpInterfaceConfigListener(DataBroker dataBroker, DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(Interface.class);
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        registerListener();
    }

    private void registerListener() {
        try {
            listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), DhcpInterfaceConfigListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("DhcpInterfaceEventListener DataChange listener registration fail!", e);
            throw new IllegalStateException("DhcpInterfaceEventListener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        logger.info("DhcpInterfaceConfigListener Closed");
    }
    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        IfTunnel tunnelInterface = del.getAugmentation(IfTunnel.class);
        if (tunnelInterface != null && !tunnelInterface.isInternal()) {
            IpAddress tunnelIp = tunnelInterface.getTunnelDestination();
            ParentRefs interfce = del.getAugmentation(ParentRefs.class);
            if (interfce != null) {
                dhcpExternalTunnelManager.handleTunnelStateDown(tunnelIp, interfce.getDatapathNodeIdentifier());
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        // Handled in update () DhcpInterfaceEventListener
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
        // Handled in add() DhcpInterfaceEventListener
    }
}
