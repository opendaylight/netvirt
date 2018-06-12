/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import static java.util.Collections.emptyList;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance, ElanInstanceManager> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceManager.class);

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
        super(ElanInstance.class, ElanInstanceManager.class);
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = managerService;
        this.elanInterfaceManager = elanInterfaceManager;
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
        LOG.trace("Remove ElanInstance - Key: {}, value: {}", identifier, deletedElan);
        String elanName = deletedElan.getElanInstanceName();
        // check the elan Instance present in the Operational DataStore
        Elan existingElan = ElanUtils.getElanByName(broker, elanName);
        long elanTag = deletedElan.getElanTag();
        // Cleaning up the existing Elan Instance
        if (existingElan != null) {
            List<String> elanInterfaces = existingElan.getElanInterfaces();
            if (elanInterfaces != null && !elanInterfaces.isEmpty()) {
                List<ListenableFuture<Void>> futureList = new ArrayList<>();
                elanInterfaces.forEach(elanInterfaceName -> {
                    jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(elanInterfaceName), () -> {
                        InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                                .getElanInterfaceConfigurationDataPathId(elanInterfaceName);
                        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(elanInterfaceName);
                        futureList.addAll(elanInterfaceManager.removeElanInterface(deletedElan, elanInterfaceName,
                                interfaceInfo));
                        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                                elanInterfaceId);
                        return futureList;
                    },ElanConstants.JOB_MAX_RETRIES);
                });
            }
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInstanceOperationalDataPath(elanName));
            Optional<ElanDpnInterfacesList> elanDpnInterfaceList = MDSALUtil.read(broker,
                    LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanDpnOperationDataPath(elanName));
            if (elanDpnInterfaceList.isPresent()) {
                ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                        getElanDpnOperationDataPath(elanName));
            }
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag));
        }
        elanInterfaceCache.getInterfaceNames(elanName).forEach(
            elanInterfaceName -> jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(elanInterfaceName),
                () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                    LOG.info("Deleting the elanInterface present under ConfigDS:{}", elanInterfaceName);
                    ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                            ElanUtils.getElanInterfaceConfigurationDataPathId(elanInterfaceName));
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
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        Long existingElanTag = original.getElanTag();
        String elanName = update.getElanInstanceName();
        if (existingElanTag == null || !existingElanTag.equals(update.getElanTag())) {
            if (update.getElanTag() == null) {
                // update the elan-Instance with new properties
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(configTx -> {
                    ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(operTx -> {
                        ElanUtils.updateOperationalDataStore(idManager, update, new ArrayList<>(), configTx, operTx);
                    });
                    ListenableFutures.addErrorLogging(future, LOG, "Error updating ELAN tag in ELAN instance");
                    futures.add(future);
                }));
            } else {
                jobCoordinator.enqueueJob(elanName, () -> {
                    try {
                        return elanInterfaceManager.handleunprocessedElanInterfaces(update);
                    } catch (ElanException e) {
                        LOG.error("update() failed for ElanInstance: {}", identifier.toString(), e);
                        return emptyList();
                    }
                }, ElanConstants.JOB_MAX_RETRIES);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(configTx -> {
            ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(operTx -> {
                String elanInstanceName  = elanInstanceAdded.getElanInstanceName();
                Elan elanInfo = ElanUtils.getElanByName(operTx, elanInstanceName);
                if (elanInfo == null) {
                    ElanUtils.updateOperationalDataStore(idManager, elanInstanceAdded, new ArrayList<>(),
                            configTx, operTx);
                }
            });
            ListenableFutures.addErrorLogging(future, LOG, "Error adding an ELAN instance");
            futures.add(future);
        }));
    }

    private static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

    @Override
    protected ElanInstanceManager getDataTreeChangeListener() {
        return this;
    }
}
