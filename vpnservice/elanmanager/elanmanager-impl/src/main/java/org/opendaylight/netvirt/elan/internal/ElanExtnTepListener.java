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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepListener extends AsyncDataTreeChangeListenerBase<ExternalTeps, ElanExtnTepListener>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepListener.class);

    private final DataBroker broker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final EntityOwnershipService entityOwnershipService;

    @Inject
    public ElanExtnTepListener(DataBroker dataBroker,
                               ElanInterfaceManager elanInterfaceManager,
                               EntityOwnershipService entityOwnershipService) {
        super(ExternalTeps.class, ElanExtnTepListener.class);
        this.broker = dataBroker;
        this.elanInterfaceManager = elanInterfaceManager;
        this.entityOwnershipService = entityOwnershipService;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ExternalTeps> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class).child(ExternalTeps.class);
    }

    @Override
    protected void add(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps add received {}", instanceIdentifier);
        updateElanRemoteBroadCastGroup(instanceIdentifier);
    }

    @Override
    protected void update(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep, ExternalTeps t1) {
    }

    @Override
    protected void remove(InstanceIdentifier<ExternalTeps> instanceIdentifier, ExternalTeps tep) {
        LOG.trace("ExternalTeps remove received {}", instanceIdentifier);
        updateElanRemoteBroadCastGroup(instanceIdentifier);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void updateElanRemoteBroadCastGroup(final InstanceIdentifier<ExternalTeps> iid) {
        String elanName = iid.firstKeyOf(ElanInstance.class).getElanInstanceName();
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(broker, elanName);

        DataStoreJobCoordinator dataStoreJobCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreJobCoordinator.enqueueJob(elanName, () -> {
            SettableFuture<Void> ft = SettableFuture.create();
            try {
                //TODO make the following method return ft
                elanInterfaceManager.updateRemoteBroadcastGroupForAllElanDpns(elanInfo);
                ft.set(null);
            } catch (Exception e) {
                //since the above method does a sync write , if it fails there was no retry
                //by setting the above mdsal exception in ft, and returning the ft makes sures that job is retried
                ft.setException(e);
            }
            return Lists.newArrayList(ft);
        }, ElanConstants.JOB_MAX_RETRIES);
    }

    @Override
    protected ElanExtnTepListener getDataTreeChangeListener() {
        return this;
    }
}