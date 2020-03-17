/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInterfaceStateClusteredListener extends
        AbstractClusteredAsyncDataTreeChangeListener<Interface> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceStateClusteredListener.class);

    private final DataBroker broker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final ElanUtils elanUtils;
    private final ElanClusterUtils elanClusterUtils;

    /* FIXME:
     * Why do we have ElanInterfaceStateChangeListener and ElanInterfaceStateClusteredListener
     * both within same module? Refactor this code into single listener.
     */
    @Inject
    public ElanInterfaceStateClusteredListener(DataBroker broker, ElanInterfaceManager elanInterfaceManager,
                                               ElanUtils elanUtils, ElanClusterUtils elanClusterUtils) {
        super(broker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class)
                .child(Interface.class),
                Executors.newListeningSingleThreadExecutor("ElanInterfaceStateClusteredListener", LOG));
        this.broker = broker;
        this.elanInterfaceManager = elanInterfaceManager;
        this.elanUtils = elanUtils;
        this.elanClusterUtils = elanClusterUtils;
    }

    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
    }

    @Override
    public void update(InstanceIdentifier<Interface> identifier, Interface original, final Interface update) {
        add(identifier, update);
    }

    @Override
    public void add(InstanceIdentifier<Interface> identifier, final Interface intrf) {
        if (intrf.getType() != null && intrf.getType().equals(Tunnel.class)) {
            if (Interface.OperStatus.Up.equals(intrf.getOperStatus())) {
                final String interfaceName = intrf.getName();

                elanClusterUtils.runOnlyInOwnerNode("external tunnel update", () -> {
                    LOG.debug("running external tunnel update job for interface {} added", interfaceName);
                    handleExternalTunnelUpdate(interfaceName, intrf);
                });
            }
        }
    }

    private void handleExternalTunnelUpdate(String interfaceName, Interface update) {
        ExternalTunnel externalTunnel = elanUtils.getExternalTunnel(interfaceName, LogicalDatastoreType.CONFIGURATION);
        if (externalTunnel != null) {
            LOG.debug("handling external tunnel update event for ext device dst {}  src {} ",
                externalTunnel.getDestinationDevice(), externalTunnel.getSourceDevice());
            elanInterfaceManager.handleExternalTunnelStateEvent(externalTunnel, update);
        } else {
            LOG.trace("External tunnel not found with interfaceName: {}", interfaceName);
        }
    }

    /* (non-Javadoc)
     * @see org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
}
