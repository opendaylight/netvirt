/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
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
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanInstanceManager extends AbstractDataChangeListener<ElanInstance> implements AutoCloseable {
    private DataBroker broker;
    private static ElanInstanceManager elanInstanceManager = new ElanInstanceManager();
    private ListenerRegistration<DataChangeListener> elanInstanceListenerRegistration;
    private IdManagerService idManager;
    private ElanInterfaceManager elanInterfaceManager;
    private IInterfaceManager interfaceManager;

    private static final Logger logger = LoggerFactory.getLogger(ElanInstanceManager.class);

    private ElanInstanceManager() {
        super(ElanInstance.class);

    }

    public static ElanInstanceManager getElanInstanceManager() {
        return elanInstanceManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setDataBroker(DataBroker broker) {
        this.broker = broker;
    }

    public void setElanInterfaceManager(ElanInterfaceManager elanInterfaceManager) {
        this.elanInterfaceManager = elanInterfaceManager;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    /**
     * Starts listening for changes in elan.yang:elan-instance container
     */
    public void registerListener() {
        try {
            elanInstanceListenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getElanInstanceWildcardPath(), ElanInstanceManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("ELAN Instance DataChange listener registration failed !", e);
            throw new IllegalStateException("ELAN Instance registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<?> getElanInstanceWildcardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

    @Override
    public void close() throws Exception {
        if (elanInstanceListenerRegistration != null) {
            elanInstanceListenerRegistration.close();
        }
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> identifier, ElanInstance deletedElan) {
        logger.trace("Remove ElanInstance - Key: {}, value: {}", identifier, deletedElan);
        String elanName = deletedElan.getElanInstanceName();
        // check the elan Instance present in the Operational DataStore
        Elan existingElan = ElanUtils.getElanByName(elanName);
        long elanTag = deletedElan.getElanTag();
        // Cleaning up the existing Elan Instance
        if (existingElan != null) {
            List<String> elanInterfaces = existingElan.getElanInterfaces();
            if (elanInterfaces != null && !elanInterfaces.isEmpty()) {
                for (String elanInterfaceName : elanInterfaces) {
                    InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                            .getElanInterfaceConfigurationDataPathId(elanInterfaceName);
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(elanInterfaceName);
                    elanInterfaceManager.removeElanInterface(deletedElan, elanInterfaceName, interfaceInfo);
                    ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION, elanInterfaceId);
                }
            }
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInstanceOperationalDataPath(elanName));
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, getElanDpnOperationDataPath(elanName));
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag));
        }
        // Release tag
        ElanUtils.releaseId(idManager, ElanConstants.ELAN_ID_POOL_NAME, elanName);

    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> identifier, ElanInstance original, ElanInstance update) {
        Long existingElanTag = original.getElanTag();
        if (existingElanTag != null && existingElanTag == update.getElanTag()) {
            return;
        } else if (update.getElanTag() == null) {
            // update the elan-Instance with new properties
            ElanUtils.updateOperationalDataStore(broker, idManager, update);
            return;
        }
        elanInterfaceManager.handleunprocessedElanInterfaces(update);
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> identifier, ElanInstance elanInstanceAdded) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceAdded.getElanInstanceName());
        if (elanInfo == null) {
            ElanUtils.updateOperationalDataStore(broker, idManager, elanInstanceAdded);
        }
    }

    public ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        Optional<ElanInstance> elanInstance = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                elanIdentifierId);
        if (elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public List<DpnInterfaces> getElanDPNByName(String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanIdentifier = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> elanInstance = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL,
                elanIdentifier);
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
}
