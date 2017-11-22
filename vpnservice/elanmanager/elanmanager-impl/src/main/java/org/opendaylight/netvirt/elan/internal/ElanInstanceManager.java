/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance, ElanInstanceManager>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceManager.class);

    private final DataBroker broker;
    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final ElanInterfaceManager elanInterfaceManager;

    @Inject
    public ElanInstanceManager(final DataBroker dataBroker, final IdManagerService managerService,
                               final ElanInterfaceManager elanInterfaceManager,
                               final IInterfaceManager interfaceManager) {
        super(ElanInstance.class, ElanInstanceManager.class);
        this.broker = dataBroker;
        this.idManager = managerService;
        this.elanInterfaceManager = elanInterfaceManager;
        this.interfaceManager = interfaceManager;
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
                for (String elanInterfaceName : elanInterfaces) {
                    InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                            .getElanInterfaceConfigurationDataPathId(elanInterfaceName);
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(elanInterfaceName);
                    elanInterfaceManager.removeElanInterface(deletedElan, elanInterfaceName, interfaceInfo, false);
                    ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                            elanInterfaceId);
                }
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
        DataStoreJobCoordinator dataStoreJobCoordinator = DataStoreJobCoordinator.getInstance();
        ElanUtils.removeAndGetElanInterfaces(elanName).forEach(elanInterfaceName -> {
            dataStoreJobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(elanInterfaceName), () -> {
                WriteTransaction writeConfigTxn = broker.newWriteOnlyTransaction();
                LOG.info("Deleting the elanInterface present under ConfigDS:{}", elanInterfaceName);
                ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                        ElanUtils.getElanInterfaceConfigurationDataPathId(elanInterfaceName));
                elanInterfaceManager.unbindService(elanInterfaceName, writeConfigTxn);
                ElanUtils.removeElanInterfaceToElanInstanceCache(elanName, elanInterfaceName);
                LOG.info("unbind the Interface:{} service bounded to Elan:{}", elanInterfaceName, elanName);
                return Collections.singletonList(writeConfigTxn.submit());
            }, ElanConstants.JOB_MAX_RETRIES);
        });
        // Release tag
        ElanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME, elanName);
        if (deletedElan.getAugmentation(EtreeInstance.class) != null) {
            removeEtreeInstance(deletedElan);
        }
    }

    private void removeEtreeInstance(ElanInstance deletedElan) {
        // Release leaves tag
        ElanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME,
                deletedElan.getElanInstanceName() + ElanConstants.LEAVES_POSTFIX);

        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanInfoEntriesOperationalDataPath(
                deletedElan.getAugmentation(EtreeInstance.class).getEtreeLeafTagVal().getValue()));
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        Long existingElanTag = original.getElanTag();
        String elanName = update.getElanInstanceName();
        if (existingElanTag != null && existingElanTag.equals(update.getElanTag())) {
            return;
        } else if (update.getElanTag() == null) {
            // update the elan-Instance with new properties
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            ElanUtils.updateOperationalDataStore(broker, idManager,
                    update, new ArrayList<>(), tx);
            ElanUtils.waitForTransactionToComplete(tx);
            return;
        }

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(elanName, () -> {
            try {
                elanInterfaceManager.handleunprocessedElanInterfaces(update);
            } catch (ElanException e) {
                LOG.error("update() failed for ElanInstance: " + identifier.toString(), e);
            }
            return null;
        }, ElanConstants.JOB_MAX_RETRIES);

    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        String elanInstanceName  = elanInstanceAdded.getElanInstanceName();
        Elan elanInfo = ElanUtils.getElanByName(broker, elanInstanceName);
        if (elanInfo == null) {
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            ElanUtils.updateOperationalDataStore(broker, idManager,
                elanInstanceAdded, new ArrayList<>(), tx);
            ElanUtils.waitForTransactionToComplete(tx);
        }
    }

    public ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orNull();
    }

    @Nonnull
    public List<DpnInterfaces> getElanDPNByName(String elanInstanceName) {
        return getElanDPNByName(broker, elanInstanceName);
    }

    @Nonnull
    public static List<DpnInterfaces> getElanDPNByName(DataBroker dataBroker, String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanIdentifier = getElanDpnOperationDataPath(elanInstanceName);
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, elanIdentifier).toJavaUtil().map(
                ElanDpnInterfacesList::getDpnInterfaces).orElse(Collections.emptyList());
    }

    private static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    private InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
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
