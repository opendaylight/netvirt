/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayBcGroupUtils;
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
    private final ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanRefUtil elanRefUtil;

    @Inject
    public ElanExtnTepListener(DataBroker dataBroker, ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils,
            JobCoordinator jobCoordinator, ElanInstanceCache elanInstanceCache, ElanRefUtil elanRefUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class).child(ExternalTeps.class),
                Executors.newListeningSingleThreadExecutor("ElanExtnTepListener", LOG));
        this.broker = dataBroker;
        this.elanL2GatewayBcGroupUtils = elanL2GatewayBcGroupUtils;
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
        updateElanRemoteBroadCastGroup(instanceIdentifier);
    }

    @Override
    public void update(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    public void remove(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps remove received {}", instanceIdentifier);
        updateElanRemoteBroadCastGroup(instanceIdentifier);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void updateElanRemoteBroadCastGroup(final InstanceIdentifier<ExternalTeps> iid) {
        String elanName = iid.firstKeyOf(ElanInstance.class).getElanInstanceName();
        ElanInstance elanInfo = elanInstanceCache.get(elanName).orElseGet(null);
        if (elanInfo == null) {
            return;
        }

        jobCoordinator.enqueueJob(elanName, () -> {
            SettableFuture<Void> ft = SettableFuture.create();
            try {
                //TODO make the following method return ft
                elanL2GatewayBcGroupUtils.updateRemoteBroadcastGroupForAllElanDpns(elanInfo);
                ft.set(null);
            } catch (Exception e) {
                //since the above method does a sync write , if it fails there was no retry
                //by setting the above mdsal exception in ft, and returning the ft makes sures that job is retried
                ft.setException(e);
            }
            return Lists.newArrayList(ft);
        });
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }
}
