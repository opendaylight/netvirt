/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceInterfaceEventListener extends AsyncDataTreeChangeListenerBase<Interface,
    Ipv6ServiceInterfaceEventListener>
    implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceInterfaceEventListener.class);
    private final DataBroker broker;
    private Ipv6ServiceUtils ipv6ServiceUtils;

    /**
     * Intialize the member variables.
     * @param broker the data broker instance.
     */
    public Ipv6ServiceInterfaceEventListener(DataBroker broker) {
        super(Interface.class, Ipv6ServiceInterfaceEventListener.class);
        this.broker = broker;
        ipv6ServiceUtils = new Ipv6ServiceUtils();
    }

    @Override
    public void close() throws Exception {
        try {
            super.close();
        } catch (Exception e) {
            LOG.error("Error while closing {}", e.getMessage());

        }
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface port =
                Ipv6ServiceUtils.getInterface(broker, dataObjectModification.getName());
        LOG.info("Port removed {}", port);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
                          Interface dataObjectModificationAfter) {
        // TODO Auto-generated method stub
        LOG.info("Port updated...");
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface port =
                Ipv6ServiceUtils.getInterface(broker, dataObjectModification.getName());
        LOG.info("Port added {}", port);
    }

    @Override
    protected Ipv6ServiceInterfaceEventListener getDataTreeChangeListener() {
        return Ipv6ServiceInterfaceEventListener.this;
    }
}
