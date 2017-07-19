/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.statistics.api.ICountersInterfaceChangeHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@SuppressWarnings("deprecation")
public class CountersServiceInterfaceListener
        extends AsyncDataTreeChangeListenerBase<Interface, CountersServiceInterfaceListener>
        implements ClusteredDataTreeChangeListener<Interface> {

    private final ICountersInterfaceChangeHandler cich;
    private final DataBroker db;

    public CountersServiceInterfaceListener(final DataBroker db, final ICountersInterfaceChangeHandler cich) {
        super(Interface.class, CountersServiceInterfaceListener.class);
        this.db = db;
        this.cich = cich;
        registerListener(LogicalDatastoreType.CONFIGURATION, db);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface port) {
        cich.handleInterfaceRemoval(port.getName());
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface portBefore, Interface portAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface port) {
    }

    @Override
    protected CountersServiceInterfaceListener getDataTreeChangeListener() {
        return this;
    }

}
