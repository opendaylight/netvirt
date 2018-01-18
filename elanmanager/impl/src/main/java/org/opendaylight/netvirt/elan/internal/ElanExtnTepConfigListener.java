/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepConfigListener
        extends AsyncDataTreeChangeListenerBase<ExternalTeps, ElanExtnTepConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepConfigListener.class);

    private final DataBroker broker;

    @Inject
    public ElanExtnTepConfigListener(DataBroker dataBroker) {
        super(ExternalTeps.class, ElanExtnTepConfigListener.class);
        this.broker = dataBroker;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    public InstanceIdentifier<ExternalTeps> getWildCardPath() {
        return InstanceIdentifier
                .builder(ElanInstances.class)
                .child(ElanInstance.class)
                .child(ExternalTeps.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, iid, tep, true);
        JdkFutures.addErrorLogging(tx.submit(), LOG, "Failed to update operational external teps {}", iid);
    }

    @Override
    protected void update(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    protected void remove(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, iid);
        JdkFutures.addErrorLogging(tx.submit(), LOG, "Failed to remove operational external teps {}", iid);
    }

    @Override
    protected ElanExtnTepConfigListener getDataTreeChangeListener() {
        return this;
    }
}
