/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.ArrayList;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceManager extends AbstractAsyncDataTreeChangeListener<ElanInstance> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceManager.class);
    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("NetvirtEventLogger");

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final ElanInterfaceManager elanInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanInstanceManager(final DataBroker dataBroker, final IdManagerService managerService,
                               final ElanInterfaceManager elanInterfaceManager,
                               final IInterfaceManager interfaceManager, final JobCoordinator jobCoordinator,
                               final ElanInterfaceCache elanInterfaceCache) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class),
                Executors.newListeningSingleThreadExecutor("ElanInstanceManager", LOG));
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = managerService;
        this.elanInterfaceManager = elanInterfaceManager;
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
        LOG.trace("Remove ElanInstance - Key: {}, value: {}", identifier, deletedElan);
        String elanName = deletedElan.getElanInstanceName();
        EVENT_LOGGER.debug("ELAN-Instance, REMOVE {}",elanName);
        elanInterfaceCache.getInterfaceNames(elanName).forEach(
            elanInterfaceName -> jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(elanInterfaceName),
                () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                    LOG.info("Deleting the elanInterface present under ConfigDS:{}", elanInterfaceName);
                    tx.delete(ElanUtils.getElanInterfaceConfigurationDataPathId(elanInterfaceName));
                    elanInterfaceManager.unbindService(elanInterfaceName, tx);
                    LOG.info("unbind the Interface:{} service bounded to Elan:{}", elanInterfaceName, elanName);
                })), ElanConstants.JOB_MAX_RETRIES));
        // Release tag
        ElanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME, elanName);
        if (deletedElan.augmentation(EtreeInstance.class) != null) {
            removeEtreeInstance(deletedElan);
        }
    }

    private void removeEtreeInstance(ElanInstance deletedElan) {
        // Release leaves tag
        ElanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME,
                deletedElan.getElanInstanceName() + ElanConstants.LEAVES_POSTFIX);

        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanInfoEntriesOperationalDataPath(
                        deletedElan.augmentation(EtreeInstance.class).getEtreeLeafTagVal().getValue()));
    }

    @Override
    public void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        EVENT_LOGGER.debug("ELAN-Instance, UPDATE {}", original.getElanInstanceName());
        Uint32 existingElanTag = original.getElanTag();
        String elanName = update.getElanInstanceName();
        if (existingElanTag == null || !existingElanTag.equals(update.getElanTag())) {
            if (update.getElanTag() == null  || update.getElanTag().longValue() == 0L) {
                // update the elan-Instance with new properties
                LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                    operTx -> LoggingFutures.addErrorLogging(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                            confTx -> ElanUtils.updateOperationalDataStore(idManager, update, new ArrayList<>(), confTx,
                                operTx)), LOG, "Error updating ELAN tag in ELAN instance for config "
                                    + "transaction")), LOG,
                    "Error updating ELAN tag in ELAN instance for operational transaction");
            } else {
                jobCoordinator.enqueueJob(elanName, () -> elanInterfaceManager.handleunprocessedElanInterfaces(update),
                    ElanConstants.JOB_MAX_RETRIES);
            }
        }
    }

    @Override
    public void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx -> {
            String elanInstanceName  = elanInstanceAdded.getElanInstanceName();
            EVENT_LOGGER.debug("ELAN-Instance, ADD {}", elanInstanceName);
            Elan elanInfo = ElanUtils.getElanByName(operTx, elanInstanceName);
            if (elanInfo == null) {
                LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    confTx -> ElanUtils.updateOperationalDataStore(idManager, elanInstanceAdded, new ArrayList<>(),
                        confTx, operTx)), LOG, "Error adding an ELAN instance for config transaction");
            }
        }), LOG, "Error adding an ELAN instance for operational transaction");
    }
}
