/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclInterfaceEventListener extends AsyncDataTreeChangeListenerBase<Interface, AclInterfaceEventListener>
    implements ClusteredDataTreeChangeListener<Interface>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceEventListener.class);

    /** Our registration. */
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    public static final String EXTERNAL_ID_INTERFACE_ID = "iface-id";
    public final AclServiceManager aclServiceManger;
    private final DataBroker broker;

    /**
     * Initialize the member variables.
     * @param aclServiceManger the AclServiceManager instance.
     * @param broker the data broker instance.
     */
    public AclInterfaceEventListener(AclServiceManager aclServiceManger, DataBroker broker) {
        super(Interface.class, AclInterfaceEventListener.class);
        this.aclServiceManger = aclServiceManger;
        this.broker = broker;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        Optional<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
            > optPort = AclServiceUtils.getInterface(broker, dataObjectModification.getName());
        if (optPort.isPresent()) {
            LOG.info("Port removed {}", optPort.get());
            aclServiceManger.notify(optPort.get(), Action.REMOVE);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
                          Interface dataObjectModificationAfter) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        Optional<
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface
            > optPort = AclServiceUtils.getInterface(broker, dataObjectModification.getName());
        if (optPort.isPresent()) {
            LOG.info("Port added {}", optPort.get());
            aclServiceManger.notify(optPort.get(), Action.ADD);
        }
    }

    @Override
    protected AclInterfaceEventListener getDataTreeChangeListener() {
        return AclInterfaceEventListener.this;
    }
}
