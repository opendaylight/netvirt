/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInterfaceStateClusteredListener extends
    AsyncClusteredDataTreeChangeListenerBase<Interface, ElanInterfaceStateClusteredListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceStateClusteredListener.class);

    private final DataBroker broker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final ElanUtils elanUtils;
    private final EntityOwnershipUtils entityOwnershipUtils;

    /* FIXME:
     * Why do we have ElanInterfaceStateChangeListener and ElanInterfaceStateClusteredListener
     * both within same module? Refactor this code into single listener.
     */
    public ElanInterfaceStateClusteredListener(DataBroker broker, ElanInterfaceManager elanInterfaceManager,
                                               ElanUtils elanUtils, EntityOwnershipUtils entityOwnershipUtils) {
        this.broker = broker;
        this.elanInterfaceManager = elanInterfaceManager;
        this.elanUtils = elanUtils;
        this.entityOwnershipUtils = entityOwnershipUtils;
    }

    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    public InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
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

                ElanClusterUtils.runOnlyInOwnerNode(entityOwnershipUtils, "external tunnel update", () -> {
                    LOG.debug("running external tunnel update job for interface {} added", interfaceName);
                    try {
                        handleExternalTunnelUpdate(interfaceName, intrf);
                    } catch (ElanException e) {
                        LOG.error("Failed to add Interface: " + identifier.toString());
                    }
                });
            }
        }
    }

    private void handleExternalTunnelUpdate(String interfaceName, Interface update) throws ElanException {
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
    @Override
    protected ElanInterfaceStateClusteredListener getDataTreeChangeListener() {
        return this;
    }

}
