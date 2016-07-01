/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
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
     * Intialize the member variables.
     * @param aclServiceManger the AclServiceManager instance.
     * @param broker the data broker instance.
     */
    public AclInterfaceEventListener(AclServiceManager aclServiceManger, DataBroker broker) {
        super(Interface.class, AclInterfaceEventListener.class);
        this.aclServiceManger = aclServiceManger;
        this.broker = broker;

        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
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
                AclServiceUtils.getInterface(broker,dataObjectModification.getName());
        LOG.info("Port removed {}", port);
        aclServiceManger.notify(port, Action.REMOVE);

    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
                          Interface dataObjectModificationAfter) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface port =
                AclServiceUtils.getInterface(broker,dataObjectModification.getName());
        LOG.info("Port added {}", port);
        aclServiceManger.notify(port, Action.ADD);

    }

    @Override
    protected AclInterfaceEventListener getDataTreeChangeListener() {
        return AclInterfaceEventListener.this;
    }
}
