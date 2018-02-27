/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.recovery.impl;

import com.google.common.base.Optional;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.srm.ServiceRecoveryInterface;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtElan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceRecoveryHandler.class);
    private final ElanInstanceCache elanInstanceCache;
    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner txRunner;
    private final EntityOwnershipUtils entityOwnershipUtils;

    @Inject
    public ElanInstanceRecoveryHandler(DataBroker dataBroker,
                                       ElanInstanceCache elanInstanceCache,
                                       JobCoordinator jobCoordinator,
                                       ServiceRecoveryRegistry serviceRecoveryRegistry,
                                       EntityOwnershipUtils entityOwnershipUtils) {
        this.elanInstanceCache = elanInstanceCache;
        this.jobCoordinator = jobCoordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.entityOwnershipUtils = entityOwnershipUtils;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(String entityId) {
        if (!entityOwnershipUtils.isEntityOwner(ElanConstants.ELAN_SERVICE_NAME,
                ElanConstants.ELAN_SERVICE_NAME)) {
            return;
        }
        LOG.info("recover elan instance {}", entityId);
        // Fetch the elan instance from elan instance config DS first.
        Optional<ElanInstance> elanInstanceOptional = elanInstanceCache.get(entityId);
        if (elanInstanceOptional.isPresent()) {
            ElanInstance elanInstance = elanInstanceOptional.get();
            // Do a delete and recreate the elan instance configuration.
            InstanceIdentifier<ElanInstance> elanInstanceId = ElanHelper.getElanInstanceConfigurationDataPath(
                    entityId);
            LOG.trace("deleting elan instance {}", entityId);
            jobCoordinator.enqueueJob(entityId, () -> Collections.singletonList(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, elanInstanceId))),
                    ElanConstants.JOB_MAX_RETRIES);
            LOG.trace("recreating elan instance {}, {}", entityId, elanInstance);
            jobCoordinator.enqueueJob(entityId, () -> Collections.singletonList(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.put(LogicalDatastoreType.CONFIGURATION, elanInstanceId, elanInstance))),
                    ElanConstants.JOB_MAX_RETRIES);
        }
    }

    private String buildServiceRegistryKey() {
        return NetvirtElan.class.toString();
    }
}

