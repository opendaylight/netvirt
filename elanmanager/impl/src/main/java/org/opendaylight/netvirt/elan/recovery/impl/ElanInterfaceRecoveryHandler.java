/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.recovery.impl;

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev170711.NetvirtElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInterfaceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceRecoveryHandler.class);

    private final ManagedNewTransactionRunner txRunner;
    private final ElanServiceProvider elanServiceProvider;
    private final EntityOwnershipUtils entityOwnershipUtils;

    @Inject
    public ElanInterfaceRecoveryHandler(DataBroker dataBroker,
                                        ElanServiceProvider elanServiceProvider,
                                        ServiceRecoveryRegistry serviceRecoveryRegistry,
                                        EntityOwnershipUtils entityOwnershipUtils) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanServiceProvider = elanServiceProvider;
        this.entityOwnershipUtils = entityOwnershipUtils;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(String entityId) {
        if (!entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
            return;
        }
        LOG.info("recover elan interface {}", entityId);
        // Fetch the elan interface from elan interface config DS first.
        ElanInterface elanInterface = elanServiceProvider.getElanInterfaceByElanInterfaceName(entityId);
        if (elanInterface != null) {
            // Do a delete and recreate of the elan interface configuration.
            InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                    .getElanInterfaceConfigurationDataPathId(entityId);
            try {
                LOG.trace("deleting elan interface {}", entityId);
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, elanInterfaceId)).get();
                LOG.trace("recreating elan interface {}, {}", entityId, elanInterface);
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> tx.put(LogicalDatastoreType.CONFIGURATION, elanInterfaceId, elanInterface)).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Service recovery failed for elan interface {}", entityId, e);
            }
        }
    }

    private String buildServiceRegistryKey() {
        return NetvirtElanInterface.class.toString();
    }
}
