/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInterfaceStateClusteredListener extends
    AsyncClusteredDataChangeListenerBase<Interface, ElanInterfaceStateClusteredListener> implements AutoCloseable {
    public  ElanServiceProvider getElanServiceProvider() {
        return elanServiceProvider;
    }
    public void setElanServiceProvider(ElanServiceProvider elanServiceProvider) {
        this.elanServiceProvider = elanServiceProvider;
    }

    private  ElanServiceProvider elanServiceProvider = null;
    private static volatile ElanInterfaceStateClusteredListener elanInterfaceStateClusteredListener = null;
    private ListenerRegistration<DataChangeListener> listenerRegistration;

    private static final Logger logger = LoggerFactory.getLogger(ElanInterfaceStateClusteredListener.class);


    public ElanInterfaceStateClusteredListener(ElanServiceProvider elanServiceProvider) {
        super(Interface.class, ElanInterfaceStateClusteredListener.class);
        this.elanServiceProvider = elanServiceProvider;
        registerListener(this.elanServiceProvider.getBroker());
    }
    public static ElanInterfaceStateClusteredListener getElanInterfaceStateClusteredListener(
        ElanServiceProvider elanServiceProvider) {
        if (elanInterfaceStateClusteredListener == null)
            synchronized (ElanInterfaceStateClusteredListener.class) {
                if (elanInterfaceStateClusteredListener == null)
                {
                    ElanInterfaceStateClusteredListener elanInterfaceStateClusteredListener = new ElanInterfaceStateClusteredListener(elanServiceProvider);
                    return elanInterfaceStateClusteredListener;

                }
            }
        return elanInterfaceStateClusteredListener;
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = elanServiceProvider.getBroker().registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), ElanInterfaceStateClusteredListener.this, AsyncDataBroker.DataChangeScope.BASE);
        } catch (final Exception e) {
            logger.error("Elan Interfaces DataChange listener registration fail!", e);
            throw new IllegalStateException("ElanInterface registration Listener failed.", e);
        }
    }

    @Override
    public InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return ElanInterfaceStateClusteredListener.this;
    }

    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, final Interface update) {
        add(identifier, update);
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, final Interface intrf) {
        if (intrf.getType() != null && intrf.getType().equals(Tunnel.class)) {
            if (intrf.getOperStatus().equals(Interface.OperStatus.Up)) {
                final String interfaceName = intrf.getName();

                ElanClusterUtils.runOnlyInLeaderNode(new Runnable() {
                    @Override
                    public void run() {
                        logger.debug("running external tunnel update job for interface {} added", interfaceName);
                        handleExternalTunnelUpdate(interfaceName, intrf);
                    }
                });
            }
        }
    }

    private void handleExternalTunnelUpdate(String interfaceName, Interface update) {
        ExternalTunnel externalTunnel = ElanUtils.getExternalTunnel(interfaceName, LogicalDatastoreType.CONFIGURATION);
        if (externalTunnel != null) {
            logger.debug("handling external tunnel update event for ext device dst {}  src {} ",
                externalTunnel.getDestinationDevice(), externalTunnel.getSourceDevice());
            elanServiceProvider.getElanInterfaceManager().handleExternalTunnelStateEvent(externalTunnel, update);
        } else {
            logger.trace("External tunnel not found with interfaceName: {}", interfaceName);
        }
    }

}
