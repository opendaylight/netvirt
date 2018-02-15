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
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanExtnTepListener extends AsyncDataTreeChangeListenerBase<ExternalTeps, ElanExtnTepListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanExtnTepListener.class);

    private final DataBroker broker;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public ElanExtnTepListener(DataBroker dataBroker, ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
            JobCoordinator jobCoordinator) {
        super(ExternalTeps.class, ElanExtnTepListener.class);
        this.broker = dataBroker;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.jobCoordinator = jobCoordinator;
    }

    @Override
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

        jobCoordinator.enqueueJob(elanName, () -> {
            SettableFuture<Void> ft = SettableFuture.create();
            try {
                //TODO make the following method return ft
                elanL2GatewayMulticastUtils.updateRemoteBroadcastGroupForAllElanDpns(elanInfo);
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
