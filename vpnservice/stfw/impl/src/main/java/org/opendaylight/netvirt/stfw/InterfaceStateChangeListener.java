/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterfaceStateChangeListener extends
    AsyncDataTreeChangeListenerBase<Interface, InterfaceStateChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateChangeListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private OvsSimulator ovsSimulator;

    @Inject
    public InterfaceStateChangeListener(DataBroker dataBroker, OvsSimulator ovsSimulator) {
        super(Interface.class, InterfaceStateChangeListener.class);
        this.ovsSimulator = ovsSimulator;
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        LOG.info("InterfaceStateChangeListener registered");
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} add event", intrf.getName());
        try {
            if (intrf.getType() != null) {
                ovsSimulator.incrementInterfacesCount(getInterfaceType(intrf));
            }
        } catch (Exception e) {
            LOG.error("Exception caught in Interface Operational State Up event", e);
        }
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected InterfaceStateChangeListener getDataTreeChangeListener() {
        return InterfaceStateChangeListener.this;
    }

    public String getInterfaceType(Interface intrf) {
        return intrf.getType() == Tunnel.class ? "TUNNEL" : "L2VLAN";
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} removed event", intrf);
        if (intrf.getType() != null) {
            ovsSimulator.decrementInterfacesCount(getInterfaceType(intrf));
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
                          Interface original, Interface update) {
        LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
    }

}
