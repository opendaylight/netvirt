/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.jobs.BcGroupUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepListener extends AbstractClusteredAsyncDataTreeChangeListener<ExternalTeps> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanRefUtil elanRefUtil;

    @Inject
    public ElanExtnTepListener(DataBroker dataBroker, ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
            JobCoordinator jobCoordinator, ElanInstanceCache elanInstanceCache, ElanRefUtil elanRefUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class).child(ExternalTeps.class),
                Executors.newListeningSingleThreadExecutor("ElanExtnTepListener", LOG));
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanRefUtil = elanRefUtil;
    }

    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps add received {}", instanceIdentifier);
        updateBcGroupOfElan(instanceIdentifier, tep, true);
    }

    @Override
    public void update(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    public void remove(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps remove received {}", instanceIdentifier);
        updateBcGroupOfElan(instanceIdentifier, tep, false);
    }

    protected void updateBcGroupOfElan(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep,
                                       boolean add) {
        String elanName = instanceIdentifier.firstKeyOf(ElanInstance.class).getElanInstanceName();
        BcGroupUpdateJob.updateAllBcGroups(elanName, elanRefUtil, elanL2GatewayMulticastUtils, broker, add);
    }
}
