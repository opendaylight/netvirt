/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInterfaceConfigVppListener
        extends AsyncDataTreeChangeListenerBase<Interface, ElanInterfaceConfigVppListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceConfigVppListener.class);

    private final DataBroker broker;
    private final ElanInterfaceVppRenderer elanInterfaceVppRenderer;

    public ElanInterfaceConfigVppListener(final DataBroker db, final ElanInterfaceVppRenderer elanVppRenderer) {
        super(Interface.class, ElanInterfaceConfigVppListener.class);
        broker = db;
        elanInterfaceVppRenderer = elanVppRenderer;
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
        LOG.trace("Received interface {} Delete event", delIf);
        String interfaceName = delIf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (elanInterface != null) {
            elanInterfaceVppRenderer.handleInterfaceDeleted(elanInterface, delIf);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("Operation Interface update event - Old: {}, New: {}", original, update);
        String interfaceName = update.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (elanInterface != null) {
            elanInterfaceVppRenderer.handleInterfaceAdded(elanInterface);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("Received interface {} add event", intrf);
        String interfaceName =  intrf.getName();
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(broker, interfaceName);
        if (elanInterface != null) {
            elanInterfaceVppRenderer.handleInterfaceAdded(elanInterface);
        }
        return;
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }


    @Override
    protected ElanInterfaceConfigVppListener getDataTreeChangeListener() {
        return this;
    }

}
