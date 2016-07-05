/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
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

import com.google.common.base.Optional;

public class ElanInstanceManager extends AsyncDataTreeChangeListenerBase<ElanInstance,ElanInstanceManager> implements AutoCloseable {

    private  ElanServiceProvider elanServiceProvider = null;
    private static volatile ElanInstanceManager elanInstanceManager = null;

    private static final Logger logger = LoggerFactory.getLogger(ElanInstanceManager.class);

    private ElanInstanceManager(ElanServiceProvider elanServiceProvider) {
        super(ElanInstance.class,ElanInstanceManager.class);
        this.elanServiceProvider = elanServiceProvider;
    }

    public static ElanInstanceManager getElanInstanceManager(ElanServiceProvider elanServiceProvider) {
        if (elanInstanceManager == null) {
            synchronized (ElanInstanceManager.class) {
                if (elanInstanceManager == null) {
                    elanInstanceManager = new ElanInstanceManager(elanServiceProvider);
                }
            }
        }
        return elanInstanceManager;
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
        logger.trace("Remove ElanInstance - Key: {}, value: {}", identifier, deletedElan);
        String elanName = deletedElan.getElanInstanceName();
        //check the elan Instance present in the Operational DataStore
        Elan existingElan = ElanUtils.getElanByName(elanName);
        long elanTag = deletedElan.getElanTag();
        //Cleaning up the existing Elan Instance
        if (existingElan != null) {
            List<String> elanInterfaces =  existingElan.getElanInterfaces();
            if (elanInterfaces != null && !elanInterfaces.isEmpty()) {
                for (String elanInterfaceName : elanInterfaces) {
                    InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils.getElanInterfaceConfigurationDataPathId(elanInterfaceName);
                    InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(elanInterfaceName);
                    elanServiceProvider.getElanInterfaceManager().removeElanInterface(deletedElan, elanInterfaceName, interfaceInfo, false);
                    ElanUtils.delete(elanServiceProvider.getBroker(), LogicalDatastoreType.CONFIGURATION, elanInterfaceId);
                }
            }
            ElanUtils.delete(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName));
            ElanUtils.delete(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, getElanDpnOperationDataPath(elanName));
            ElanUtils.delete(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag));
        }
        // Release tag
        ElanUtils.releaseId( elanServiceProvider.getIdManager(), ElanConstants.ELAN_ID_POOL_NAME, elanName);

    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        Long existingElanTag = original.getElanTag();
        if (existingElanTag != null && existingElanTag == update.getElanTag()) {
            return;
        } else if (update.getElanTag() == null) {
            // update the elan-Instance with new properties
            WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
            ElanUtils.updateOperationalDataStore(elanServiceProvider.getBroker(), elanServiceProvider.getIdManager(),
                update, new ArrayList<String>(), tx);
            ElanUtils.waitForTransactionToComplete(tx);
            return;
        }
        elanServiceProvider.getElanInterfaceManager().handleunprocessedElanInterfaces(update);
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceAdded.getElanInstanceName());
        if (elanInfo == null) {
            WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
            ElanUtils.updateOperationalDataStore(elanServiceProvider.getBroker(), elanServiceProvider.getIdManager(), elanInstanceAdded,
                new ArrayList<String>(), tx);
            ElanUtils.waitForTransactionToComplete(tx);
        }
    }

    public ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        Optional<ElanInstance> elanInstance = ElanUtils.read(elanServiceProvider.getBroker(), LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
        if (elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public List<DpnInterfaces> getElanDPNByName(String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanIdentifier = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> elanInstance = ElanUtils.read(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, elanIdentifier);
        if (elanInstance.isPresent()) {
            ElanDpnInterfacesList elanDPNs =  elanInstance.get();
            return elanDPNs.getDpnInterfaces();
        }
        return null;
    }

    private InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    private InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
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
