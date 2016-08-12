/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
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

public class ElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance, ElanInstanceManager>
        implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ElanInstanceManager.class);

    private final DataBroker broker;
    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final ElanInterfaceManager elanInterfaceManager;
    private ElanUtils elanUtils;

    public ElanInstanceManager(final DataBroker dataBroker, final IdManagerService managerService,
                               final ElanInterfaceManager elanInterfaceManager,
                               final IInterfaceManager interfaceManager) {
        super(ElanInstance.class, ElanInstanceManager.class);
        this.broker = dataBroker;
        this.idManager = managerService;
        this.elanInterfaceManager = elanInterfaceManager;
        this.interfaceManager = interfaceManager;
    }

    public void setElanUtils(ElanUtils elanUtils) {
        this.elanUtils = elanUtils;
    }

    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
        logger.trace("Remove ElanInstance - Key: {}, value: {}", identifier, deletedElan);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        String elanName = deletedElan.getElanInstanceName();
        // check the elan Instance present in the Operational DataStore
        Elan existingElan = elanUtils.getElanByName(elanName);
        long elanTag = deletedElan.getElanTag();
        // Cleaning up the existing Elan Instance
        if (existingElan != null) {
            List<String> elanInterfaces = existingElan.getElanInterfaces();
            if (elanInterfaces != null && !elanInterfaces.isEmpty()) {
                for (String elanInterfaceName : elanInterfaces) {
                    InstanceIdentifier<ElanInterface> elanInterfaceId = elanUtils
                            .getElanInterfaceConfigurationDataPathId(elanInterfaceName);
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(elanInterfaceName);
                    elanInterfaceManager.removeElanInterface(futures, deletedElan, elanInterfaceName,
                            interfaceInfo, false);
                    elanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                            elanInterfaceId);
                }
            }
            elanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    elanUtils.getElanInstanceOperationalDataPath(elanName));
            elanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    getElanDpnOperationDataPath(elanName));
            elanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    elanUtils.getElanInfoEntriesOperationalDataPath(elanTag));
        }
        // Release tag
        elanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME, elanName);
        if (deletedElan.getAugmentation(EtreeInstance.class) != null) {
            removeEtreeInstance(deletedElan);
        }
    }

    private void removeEtreeInstance(ElanInstance deletedElan) {
        // Release leaves tag
        elanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME,
                deletedElan.getElanInstanceName() + ElanConstants.LEAVES_POSTFIX);

        elanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                elanUtils.getElanInfoEntriesOperationalDataPath(
                deletedElan.getAugmentation(EtreeInstance.class).getEtreeLeafTagVal().getValue()));
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        Long existingElanTag = original.getElanTag();
        if (existingElanTag != null && existingElanTag == update.getElanTag()) {
            return;
        } else if (update.getElanTag() == null) {
            // update the elan-Instance with new properties
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            elanUtils.updateOperationalDataStore(broker, idManager,
                    update, new ArrayList<String>(), tx);
            elanUtils.waitForTransactionToComplete(tx);
            return;
        }
        elanInterfaceManager.handleunprocessedElanInterfaces(update);
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        Elan elanInfo = elanUtils.getElanByName(elanInstanceAdded.getElanInstanceName());
        if (elanInfo == null) {
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            elanUtils.updateOperationalDataStore(broker, idManager,
                    elanInstanceAdded, new ArrayList<String>(), tx);
            elanUtils.waitForTransactionToComplete(tx);
        }
    }

    public ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        Optional<ElanInstance> elanInstance = elanUtils.read(broker,
                LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
        if (elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public List<DpnInterfaces> getElanDPNByName(String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanIdentifier = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> elanInstance = elanUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, elanIdentifier);
        if (elanInstance.isPresent()) {
            ElanDpnInterfacesList elanDPNs = elanInstance.get();
            return elanDPNs.getDpnInterfaces();
        }
        return null;
    }

    private InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
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
