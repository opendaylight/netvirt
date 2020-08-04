/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepConfigListener
        extends AbstractAsyncDataTreeChangeListener<ExternalTeps> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepConfigListener.class);

    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public ElanExtnTepConfigListener(DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class).child(ExternalTeps.class),
                Executors.newListeningSingleThreadExecutor("ElanExtnTepConfigListener", LOG));
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
            tx.mergeParentStructurePut(iid, tep);
        }), LOG, "Failed to update operational external teps {}", iid);
    }

    @Override
    public void update(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    public void remove(InstanceIdentifier<ExternalTeps> iid, ExternalTeps tep) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
            tx.delete(iid);
        }), LOG, "Failed to update operational external teps {}", iid);
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }
}
