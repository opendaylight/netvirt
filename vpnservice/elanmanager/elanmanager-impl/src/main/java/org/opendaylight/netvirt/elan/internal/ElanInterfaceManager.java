/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo.InterfaceType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Class in charge of handling creations, modifications and removals of ElanInterfaces.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 *
 */
public class ElanInterfaceManager extends AbstractDataChangeListener<ElanInterface> implements AutoCloseable {

    private static ElanInterfaceManager elanInterfaceManager = new ElanInterfaceManager();
    private ListenerRegistration<DataChangeListener> elanInterfaceListenerRegistration;
    private ListenerRegistration<DataChangeListener> itmInterfaceListenerRegistration;
    private OdlInterfaceRpcService interfaceManagerRpcService;
    private DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private IdManagerService idManager;

    private ElanForwardingEntriesHandler elanForwardingEntriesHandler;
    private Map<String, ConcurrentLinkedQueue<ElanInterface>> unProcessedElanInterfaces =
            new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ElanInterfaceManager.class);

    public ElanInterfaceManager() {
        super(ElanInterface.class);
    }

    public static ElanInterfaceManager getElanInterfaceManager() {
        return elanInterfaceManager;
    }

    public void setMdSalApiManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setInterfaceManagerRpcService(OdlInterfaceRpcService ifManager) {
        this.interfaceManagerRpcService = ifManager;
    }

    public void setElanForwardingEntriesHandler(ElanForwardingEntriesHandler elanForwardingEntriesHandler) {
        this.elanForwardingEntriesHandler = elanForwardingEntriesHandler;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setDataBroker(DataBroker broker) {
        this.broker = broker;
    }

    @Override
    public void close() throws Exception {
        if (elanInterfaceListenerRegistration != null) {
            try {
                elanInterfaceListenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            elanInterfaceListenerRegistration = null;
        }
    }

    public void registerListener() {
        try {
            elanInterfaceListenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getElanInterfaceWildcardPath(), ElanInterfaceManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("ELAN Interface DataChange listener registration failed !", e);
            throw new IllegalStateException("ELAN Interface registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<?> getElanInterfaceWildcardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {
        String interfaceName =  del.getName();
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(del.getElanInstanceName());
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        String elanInstanceName = elanInfo.getElanInstanceName();
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        ElanInterfaceRemoveWorker configWorker = new ElanInterfaceRemoveWorker(elanInstanceName, elanInfo, interfaceName, interfaceInfo, this);
        coordinator.enqueueJob(elanInstanceName, configWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    public void removeElanInterface(ElanInstance elanInfo, String interfaceName, InterfaceInfo interfaceInfo) {
        String elanName = elanInfo.getElanInstanceName();
        if (interfaceInfo == null) {
            // Interface does not exist in ConfigDS, so lets remove everything about that interface related to Elan
            ElanInterfaceMac elanInterfaceMac =  ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
            if(elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
               List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
                for(MacEntry macEntry : macEntries) {
                    ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getMacEntryOperationalDataPath(elanName, macEntry.getMacAddress()));
                }
            }
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName));
            Elan elanState = ElanUtils.getElanByName(elanName);
            List<String> elanInterfaces = elanState.getElanInterfaces();
            elanInterfaces.remove(interfaceName);
            if(elanInterfaces.isEmpty()) {
                ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName));
                ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanName));
                ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInfoEntriesOperationalDataPath(elanInfo.getElanTag()));
            } else {
                Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName).setKey(new ElanKey(elanName)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName), updateElanState);
            }
        } else {
            removeElanInterface(elanInfo, interfaceInfo);
            unbindService(elanInfo, interfaceName);
        }
    }

    private void removeElanInterface(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {

        BigInteger dpId = interfaceInfo.getDpId();
        String elanName = elanInfo.getElanInstanceName();
        long elanTag = elanInfo.getElanTag();
        String interfaceName = interfaceInfo.getInterfaceName();
        Elan elanState = ElanUtils.getElanByName(elanName);
        logger.debug("Removing the Interface:{} from elan:{}", interfaceName, elanName);
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> existingElanInterface = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        if(existingElanInterface.isPresent()) {
            List<PhysAddress> macAddresses = new ArrayList<>();
            List<MacEntry> existingMacEntries = existingElanInterface.get().getMacEntry();
            List<MacEntry> macEntries = new ArrayList<>();
            if (existingMacEntries != null && !existingMacEntries.isEmpty()) {
                macEntries.addAll(existingMacEntries);
            }
            if(!macEntries.isEmpty()) {
                for (MacEntry macEntry : macEntries) {
                    logger.debug("removing the  mac-entry:{} present on elanInterface:{}", macEntry.getMacAddress().getValue(), interfaceName);
                    elanForwardingEntriesHandler.deleteElanInterfaceForwardingEntries(elanInfo, interfaceInfo, macEntry);
                    macAddresses.add(macEntry.getMacAddress());
                }

                // Removing all those MACs from External Devices belonging to this ELAN
                if (ElanUtils.isVxlan(elanInfo)) {
                    ElanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInfo, macAddresses);
                }
            }
        }
        /*
         *This condition check is mainly to get DPN-ID in pre-provision deletion scenario after stopping CSS
         */
        if(dpId.equals(ElanConstants.INVALID_DPN)) {
            ElanDpnInterfacesList elanDpnInterfacesList = ElanUtils.getElanDpnInterfacesList(elanName);
            if(elanDpnInterfacesList != null && !elanDpnInterfacesList.getDpnInterfaces().isEmpty()) {
                List<DpnInterfaces> dpnIfList = elanDpnInterfacesList.getDpnInterfaces();
                for (DpnInterfaces dpnInterface : dpnIfList) {
                    DpnInterfaces dpnIfLists = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpnInterface.getDpId());
                    if (dpnIfLists.getInterfaces().contains(interfaceName)) {
                        logger.debug("deleting the elanInterface from the ElanDpnInterface cache in pre-provision scenario of elan:{} dpn:{} interfaceName:{}", elanName, dpId, interfaceName);
                        removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId, interfaceName, elanTag);
                        break;
                    }
                }
            }
        } else {
            removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId, interfaceName, elanTag);
        }

        removeStaticELanFlows(elanInfo, interfaceInfo);
        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        List<String> elanInterfaces = elanState.getElanInterfaces();
        elanInterfaces.remove(interfaceName);

        if(elanInterfaces.isEmpty()) {
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName));
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnOperationDataPath(elanName));
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanName));
            ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInfoEntriesOperationalDataPath(elanInfo.getElanTag()));
            //ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceConfigurationDataPath(elanName));
        } else {
            Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName).setKey(new ElanKey(elanName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName), updateElanState);
        }
    }

    private void removeElanDpnInterfaceFromOperationalDataStore(String elanName, BigInteger dpId, String interfaceName, long elanTag) {
        DpnInterfaces dpnInterfaces =  ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
        if(dpnInterfaces != null) {
            List<String> interfaceLists = dpnInterfaces.getInterfaces();
            interfaceLists.remove(interfaceName);

            if (interfaceLists == null || interfaceLists.isEmpty()) {
                deleteAllRemoteMacsInADpn(elanName, dpId, elanTag);
                deleteElanDpnInterface(elanName, dpId);
            } else {
                updateElanDpnInterfacesList(elanName, dpId, interfaceLists);
            }
        }
    }

    private void deleteAllRemoteMacsInADpn(String elanName, BigInteger dpId, long elanTag) {
        List<DpnInterfaces> dpnInterfaces = ElanUtils.getInvolvedDpnsInElan(elanName);
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            BigInteger currentDpId = dpnInterface.getDpId();
            if (!currentDpId.equals(dpId)) {
                for (String elanInterface : dpnInterface.getInterfaces()) {
                    ElanInterfaceMac macs = ElanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                    if (macs == null) {
                        continue;
                    }
                    for (MacEntry mac : macs.getMacEntry())
                        mdsalManager.removeFlow(dpId, MDSALUtil.buildFlow(ElanConstants.ELAN_DMAC_TABLE,
                            ElanUtils.getKnownDynamicmacFlowRef(ElanConstants.ELAN_DMAC_TABLE, dpId, currentDpId, mac.getMacAddress().getValue(), elanTag)));
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {
        // updating the static-Mac Entries for the existing elanInterface
        String elanName = update.getElanInstanceName();
        String interfaceName = update.getName();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        List<PhysAddress> existingPhysAddress = original.getStaticMacEntries();
        List<PhysAddress> updatedPhysAddress = update.getStaticMacEntries();
        if(updatedPhysAddress != null && !updatedPhysAddress.isEmpty()) {
            List<PhysAddress> existingClonedPhyAddress = new ArrayList<>();
            if(existingPhysAddress != null && !existingPhysAddress.isEmpty()) {
                existingClonedPhyAddress.addAll(0, existingPhysAddress);
                existingPhysAddress.removeAll(updatedPhysAddress);
                updatedPhysAddress.removeAll(existingClonedPhyAddress);
                // removing the PhyAddress which are not presented in the updated List
                for(PhysAddress physAddress: existingPhysAddress) {
                    removeInterfaceStaticMacEntires(elanName, interfaceName, physAddress);
                }
            }
            // Adding the new PhysAddress which are presented in the updated List
            if(updatedPhysAddress.size() > 0) {
                for(PhysAddress physAddress: updatedPhysAddress) {
                    InstanceIdentifier<MacEntry> macId =  getMacEntryOperationalDataPath(elanName, physAddress);
                    Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macId);
                    if(existingMacEntry.isPresent()) {
                        elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(elanName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get());
                    } else {
                        elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(ElanUtils.getElanInstanceByName(elanName), interfaceName, physAddress);
                    }
                }
            }
        } else if(existingPhysAddress != null && !existingPhysAddress.isEmpty()) {
            for( PhysAddress physAddress : existingPhysAddress) {
                removeInterfaceStaticMacEntires(elanName, interfaceName, physAddress);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded) {
        String elanInstanceName = elanInterfaceAdded.getElanInstanceName();
        String interfaceName = elanInterfaceAdded.getName();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (interfaceInfo == null) {
            logger.warn("Interface {} is removed from Interface Oper DS due to port down ", interfaceName);
            return;
        }
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);

        if (elanInstance == null) {
            elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setDescription(elanInterfaceAdded.getDescription()).build();
            //Add the ElanInstance in the Configuration data-store
            ElanUtils.updateOperationalDataStore(broker, idManager, elanInstance);
            elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);
        }


        Long elanTag = elanInstance.getElanTag();
        // If elan tag is not updated, then put the elan interface into unprocessed entry map and entry. Let entries
        // in this map get processed during ELAN update DCN.
        if (elanTag == null) {
            ConcurrentLinkedQueue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstanceName);
            if (elanInterfaces == null) {
                elanInterfaces = new ConcurrentLinkedQueue<>();
            }
            elanInterfaces.add(elanInterfaceAdded);
            unProcessedElanInterfaces.put(elanInstanceName, elanInterfaces);
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        ElanInterfaceAddWorker addWorker = new ElanInterfaceAddWorker(elanInstanceName, elanInterfaceAdded,
                interfaceInfo, elanInstance, this);
        coordinator.enqueueJob(elanInstanceName, addWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    void handleunprocessedElanInterfaces(ElanInstance elanInstance) {
        Queue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstance.getElanInstanceName());
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        for (ElanInterface elanInterface: elanInterfaces) {
            String interfaceName = elanInterface.getName();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            addElanInterface(elanInterface, interfaceInfo, elanInstance);
        }
    }

    void programRemoteDmacFlow(ElanInstance elanInstance, InterfaceInfo interfaceInfo){
        ElanDpnInterfacesList elanDpnInterfacesList =  ElanUtils.getElanDpnInterfacesList(elanInstance.getElanInstanceName());
        List<DpnInterfaces> dpnInterfaceLists =  elanDpnInterfacesList.getDpnInterfaces();
        for(DpnInterfaces dpnInterfaces : dpnInterfaceLists){
            if(dpnInterfaces.getDpId().equals(interfaceInfo.getDpId())) {
                continue;
            }
            List<String> remoteElanInterfaces = dpnInterfaces.getInterfaces();
            for(String remoteIf : remoteElanInterfaces) {
                ElanInterfaceMac elanIfMac = ElanUtils.getElanInterfaceMacByInterfaceName(remoteIf);
                InterfaceInfo remoteInterface = interfaceManager.getInterfaceInfo(remoteIf);
                if(elanIfMac == null) {
                    continue;
                }
                List<MacEntry> remoteMacEntries = elanIfMac.getMacEntry();
                if(remoteMacEntries != null) {
                    for (MacEntry macEntry : remoteMacEntries) {
                        PhysAddress physAddress = macEntry.getMacAddress();
                        ElanUtils.setupRemoteDmacFlow(interfaceInfo.getDpId(), remoteInterface.getDpId(),
                                remoteInterface.getInterfaceTag(),
                                elanInstance.getElanTag(),
                                physAddress.getValue(),
                                elanInstance.getElanInstanceName());
                    }
                }
            }
        }
    }

    void addElanInterface(ElanInterface elanInterface, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
        Preconditions.checkNotNull(elanInstance, "elanInstance cannot be null");
        Preconditions.checkNotNull(interfaceInfo, "interfaceInfo cannot be null");
        Preconditions.checkNotNull(elanInterface, "elanInterface cannot be null");

        String interfaceName = elanInterface.getName();
        String elanInstanceName = elanInterface.getElanInstanceName();
        List<PhysAddress> staticMacAddresses = elanInterface.getStaticMacEntries();

        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        if(elanInfo == null) {
            ElanUtils.updateOperationalDataStore(broker, idManager, elanInstance);
        }

        // Specific actions to the DPN where the ElanInterface has been added, for example, programming the
        // External tunnel table if needed or adding the ElanInterface to the DpnInterfaces in the operational DS.
        BigInteger dpId = ( interfaceInfo != null ) ? dpId = interfaceInfo.getDpId() : null;
        if(dpId != null && !dpId.equals(ElanConstants.INVALID_DPN)) {
            InstanceIdentifier<DpnInterfaces> elanDpnInterfaces = ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
            Optional<DpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaces);
            if (!existingElanDpnInterfaces.isPresent()) {
                // ELAN's 1st ElanInterface added to this DPN
                createElanInterfacesList(elanInstanceName, interfaceName, dpId);
                /*
                 * Install remote DMAC flow.
                 * This is required since this DPN is added later to the elan instance
                 * and remote DMACs of other interfaces in this elan instance are not present in the current dpn.
                 */
                programRemoteDmacFlow(elanInstance, interfaceInfo);
                // The 1st ElanInterface in a DPN must program the Ext Tunnel table, but only if Elan has VNI
                if (ElanUtils.isVxlan(elanInstance)) {
                    setExternalTunnelTable(dpId, elanInstance);
                }
                ElanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance);
            } else {
                List<String> elanInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                elanInterfaces.add(interfaceName);
                if (elanInterfaces.size() == 1) {//1st dpn interface
                    ElanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance);
                }
                updateElanDpnInterfacesList(elanInstanceName, dpId, elanInterfaces);
            }
        }

        // add code to install Local/Remote BC group, unknow DMAC entry, terminating service table flow entry
        // call bindservice of interfacemanager to create ingress table flow enty.
        //Add interface to the ElanInterfaceForwardingEntires Container
        createElanInterfaceTablesList(interfaceName);
        createElanStateList(elanInstanceName, interfaceName);
        if (interfaceInfo != null) {
            installFlowsAndGroups(elanInstance, interfaceInfo);
        }
        // add the static mac addresses
        if (staticMacAddresses != null) {
            boolean isInterfaceOperational = isOperational(interfaceInfo);
            for (PhysAddress physAddress : staticMacAddresses) {
                InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName, physAddress);
                Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macId);
                if (existingMacEntry.isPresent()) {
                    elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(elanInstanceName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get());
                } else {
                    elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(elanInstance, interfaceName, physAddress);
                }

                if ( isInterfaceOperational ) {
                    // Setting SMAC, DMAC, UDMAC in this DPN and also in other DPNs
                    ElanUtils.setupMacFlows(elanInstance, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT, physAddress.getValue());
                }
            }

            if( isInterfaceOperational ) {
                // Add MAC in TOR's remote MACs via OVSDB. Outside of the loop on purpose.
                ElanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId, staticMacAddresses);
            }
        }
    }

    protected void removeInterfaceStaticMacEntires(String elanInstanceName, String interfaceName, PhysAddress physAddress) {
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        InstanceIdentifier<MacEntry> macId =  getMacEntryOperationalDataPath(elanInstanceName, physAddress);
        Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macId);

        if(!existingMacEntry.isPresent()) {
            return;
        }

        MacEntry macEntry = new MacEntryBuilder().setMacAddress(physAddress).setInterface(interfaceName).setKey(new MacEntryKey(physAddress)).build();
        elanForwardingEntriesHandler.deleteElanInterfaceForwardingEntries(ElanUtils.getElanInstanceByName(elanInstanceName), interfaceInfo, macEntry);
        elanForwardingEntriesHandler.deleteElanInterfaceMacForwardingEntries(interfaceName, physAddress);
    }


    private InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName, PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class,
                new MacTableKey(elanName)).child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    public void installFlowsAndGroups(final ElanInstance elanInfo, final InterfaceInfo interfaceInfo) {
        if (isOperational(interfaceInfo)) {

            // LocalBroadcast Group creation with elan-Interfaces
            setupElanBroadcastGroups(elanInfo, interfaceInfo.getDpId());

            setupLocalBroadcastGroups(elanInfo, interfaceInfo);
            //Terminating Service , UnknownDMAC Table.
            setupTerminateServiceTable(elanInfo, interfaceInfo);
            ElanUtils.setupTermDmacFlows(interfaceInfo, mdsalManager);
            setupUnknownDMacTable(elanInfo, interfaceInfo);
            setupFilterEqualsTable(elanInfo, interfaceInfo);
            // bind the Elan service to the Interface
            bindService(elanInfo, interfaceInfo.getInterfaceName());

            //update the remote-DPNs remoteBC group entry with Tunnels
            setElanBCGrouponOtherDpns(elanInfo, interfaceInfo);
        }
    }

    public void setupFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, ifTag),
                9, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getTunnelIdMatchForFilterEqualsLPortTag(ifTag), ElanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.installFlow(interfaceInfo.getDpId(), flow);

        Flow flowEntry = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, 1000+ifTag),
                10, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getMatchesForFilterEqualsLPortTag(ifTag),
                MDSALUtil.buildInstructionsDrop());

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntry);
    }

    public void removeFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, ifTag),
                9, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getTunnelIdMatchForFilterEqualsLPortTag(ifTag), ElanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.removeFlow(interfaceInfo.getDpId(), flow);

        Flow flowEntity = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, 1000+ifTag),
                10, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getMatchesForFilterEqualsLPortTag(ifTag),
                MDSALUtil.buildInstructionsDrop());

        mdsalManager.removeFlow(interfaceInfo.getDpId(), flowEntity);
    }

    private List<Bucket> getRemoteBCGroupBucketInfos(ElanInstance elanInfo,
                                                     int bucketKeyStart, InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        int bucketId = bucketKeyStart;
        List<Bucket> listBuckets = new ArrayList<>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
               if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                   try {
                       List<Action> listAction = ElanUtils.getInternalItmEgressAction(dpnId, dpnInterface.getDpId(), elanTag);
                       listBuckets.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                       bucketId++;
                   } catch (Exception ex) {
                       logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnId, dpnInterface.getDpId() );
                   }
               }
            }
            List<Bucket> elanL2GwDevicesBuckets = getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnId, bucketId);
            listBuckets.addAll(elanL2GwDevicesBuckets);
        }
        return listBuckets;
    }

    private List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, BigInteger dpnId, int bucketId) {
        int elanTag = elanInfo.getElanTag().intValue();
        List<Bucket> listBucketInfo = new ArrayList<>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
                if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = ElanUtils.getInternalItmEgressAction(dpnId, dpnInterface.getDpId(), elanTag);
                        listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, 0, bucketId, 0xffffffffL, 0xffffffffL));
                        bucketId++;
                    } catch (Exception ex) {
                        logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnId, dpnInterface.getDpId() );
                    }
                }
            }

            List<Bucket> elanL2GwDevicesBuckets = getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnId, bucketId);
            listBucketInfo.addAll(elanL2GwDevicesBuckets);
        }
        return listBucketInfo;
    }

    private void setElanBCGrouponOtherDpns(ElanInstance elanInfo,
                                           InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
              List<Bucket> remoteListBucketInfo = new ArrayList<>();
                if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && !dpnInterface.getDpId().equals(dpnId) && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    for(String ifName : dpnInterface.getInterfaces()) {
                        // In case if there is a InterfacePort in the cache which is not in
                        // operational state, skip processing it
                        InterfaceInfo ifInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName, interfaceInfo.getInterfaceType());
                        if (!isOperational(ifInfo)) {
                            continue;
                        }

                        listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                        bucketId++;
                    }
                    remoteListBucketInfo.addAll(listBucket);
                    for(DpnInterfaces otherFes : dpnInterfaceses) {
                        if (ElanUtils.isDpnPresent(otherFes.getDpId()) && otherFes.getDpId() != dpnInterface.getDpId()
                                && otherFes.getInterfaces() != null && ! otherFes.getInterfaces().isEmpty()) {
                            try {
                                List<Action> remoteListActionInfo = ElanUtils.getInternalItmEgressAction(dpnInterface.getDpId(), otherFes.getDpId(), elanTag);
                                remoteListBucketInfo.add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,MDSALUtil.WATCH_GROUP));
                                bucketId++;
                            } catch (Exception ex) {
                                logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnInterface.getDpId(), otherFes.getDpId() );
                                return;
                            }
                        }
                    }
                    List<Bucket> elanL2GwDevicesBuckets = getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnId,
                            bucketId);
                    remoteListBucketInfo.addAll(elanL2GwDevicesBuckets);

                    if (remoteListBucketInfo.size() == 0) {
                        logger.debug( "No ITM is present on Dpn - {} " ,dpnInterface.getDpId());
                        continue;
                    }
                    Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(remoteListBucketInfo));
                    mdsalManager.syncInstallGroup(dpnInterface.getDpId(), group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
                }
            }
        }
    }

    /**
     * Returns the bucket info with the given interface as the only bucket.
     */
    private Bucket getLocalBCGroupBucketInfo(InterfaceInfo interfaceInfo, int bucketIdStart) {
        return MDSALUtil.buildBucket(getInterfacePortActions(interfaceInfo), MDSALUtil.GROUP_WEIGHT, bucketIdStart, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP);
    }

    private List<MatchInfo> getMatchesForElanTag(Long elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));
        return mkMatches;
    }


    private List<MatchInfo> buildMatchesForVni(Long vni) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        MatchInfo match = new MatchInfo(MatchFieldType.tunnel_id,
                                        new BigInteger[]{BigInteger.valueOf(vni)} );
        mkMatches.add(match);
        return mkMatches;
    }

    private List<Instruction> getInstructionsForOutGroup(
            long groupId) {
        List<Instruction> mkInstructions = new ArrayList<>();
        List <Action> actions = new ArrayList<>();
        actions.add(new ActionInfo(ActionType.group, new String[]{Long.toString(groupId)}).buildAction());
        mkInstructions.add(MDSALUtil.getWriteActionsInstruction(actions, 0));
        return mkInstructions;
    }

    private List<MatchInfo> getMatchesForElanTag(long elanTag, boolean isSHFlagSet) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag, isSHFlagSet),
                MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG}));
        return mkMatches;
    }




    /**
     * Builds the list of instructions to be installed in the External Tunnel table (38), which so far
     * consists in writing the elanTag in metadata and send packet to the new DHCP table
     *
     * @param elanTag elanTag to be written in metadata when flow is selected
     * @return the instructions ready to be installed in a flow
     */
    private List<InstructionInfo> getInstructionsExtTunnelTable(Long elanTag) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionInfo(InstructionType.write_metadata,
                                               new BigInteger[] {
                                                       ElanUtils.getElanMetadataLabel(elanTag),
                                                       ElanUtils.getElanMetadataMask()
                                               } ) );
        // TODO (eperefr) We should point to SMAC or DMAC depending on a configuration property to enable
        // mac learning
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table,
                                               new long[] { ElanConstants.ELAN_DMAC_TABLE }));

        return mkInstructions;
    }

    public void removeFlowsAndGroups(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        removeStaticELanFlows(elanInfo, interfaceInfo);
        unbindService(elanInfo, interfaceInfo.getInterfaceName());
    }

    public void installMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {

        String interfaceName = interfaceInfo.getInterfaceName();
        BigInteger currentDpn = interfaceInfo.getDpId();
        ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if(elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            List<MacEntry> macEntries =  elanInterfaceMac.getMacEntry();
            for(MacEntry macEntry : macEntries) {
                PhysAddress physAddress = macEntry.getMacAddress();
                ElanUtils.setupMacFlows(elanInfo,
                                        interfaceInfo,
                                        macEntry.isIsStaticAddress()
                                          ? ElanConstants.STATIC_MAC_TIMEOUT
                                          : elanInfo.getMacTimeout(), physAddress.getValue());
            }
            //Programming the remoteDMACFlows
            ElanDpnInterfacesList elanDpnInterfacesList =  ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
            List<DpnInterfaces> dpnInterfaceLists =  elanDpnInterfacesList.getDpnInterfaces();
            for(DpnInterfaces dpnInterfaces : dpnInterfaceLists){
                if(dpnInterfaces.getDpId().equals(interfaceInfo.getDpId())) {
                    continue;
                }
                List<String> remoteElanInterfaces = dpnInterfaces.getInterfaces();
                for(String remoteIf : remoteElanInterfaces) {
                    ElanInterfaceMac elanIfMac = ElanUtils.getElanInterfaceMacByInterfaceName(remoteIf);
                    InterfaceInfo remoteInterface = interfaceManager.getInterfaceInfo(remoteIf);
                    if(elanIfMac == null) {
                        continue;
                    }
                    List<MacEntry> remoteMacEntries = elanIfMac.getMacEntry();
                    if(remoteMacEntries != null) {
                        for (MacEntry macEntry : remoteMacEntries) {
                            PhysAddress physAddress = macEntry.getMacAddress();
                            ElanUtils.installDmacFlowsToInternalRemoteMac(currentDpn, remoteInterface.getDpId(),
                                    remoteInterface.getInterfaceTag(),
                                    elanInfo.getElanTag(),
                                    physAddress.getValue(),
                                    elanInfo.getElanInstanceName());
                        }
                    }
                }
            }
        }
    }

    // Install DMAC entry on dst DPN
    public void installDMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId) {
        String interfaceName = interfaceInfo.getInterfaceName();
        ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if(elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            List<MacEntry> macEntries =  elanInterfaceMac.getMacEntry();
            for(MacEntry macEntry : macEntries) {
                PhysAddress physAddress = macEntry.getMacAddress();
                ElanUtils.setupDMacFlowonRemoteDpn(elanInfo, interfaceInfo, dstDpId, physAddress.getValue());
            }
        }
    }

    public void removeMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceInfo.getInterfaceName());
        if(elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            List<MacEntry> macEntries =  elanInterfaceMac.getMacEntry();
            for(MacEntry macEntry : macEntries) {
                ElanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry);
            }
        }
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        long groupId = ElanUtils.getElanRemoteBCGID(elanInfo.getElanTag());

        DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanInfo.getElanInstanceName(), dpnId);
        for(String ifName : dpnInterfaces.getInterfaces()) {
            // In case if there is a InterfacePort in the cache which is not in
            // operational state, skip processing it
            // FIXME: interfaceType to be obtained dynamically. It doesn't
            // affect the functionality here as it is nowhere used.
            InterfaceType interfaceType = InterfaceInfo.InterfaceType.VLAN_INTERFACE;
            InterfaceInfo ifInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName, interfaceType);
            if (!isOperational(ifInfo)) {
                continue;
            }

            listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnId, bucketId);
        listBucket.addAll(listBucketInfoRemote);

        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(listBucket));
        logger.trace("installing the remote BroadCast Group:{}", group);
        mdsalManager.syncInstallGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void setupLocalBroadcastGroups(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGID(elanInfo.getElanTag());

        DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanInfo.getElanInstanceName(), dpnId);
        for(String ifName : dpnInterfaces.getInterfaces()) {
            // In case if there is a InterfacePort in the cache which is not in
            // operational state, skip processing it
            InterfaceInfo ifInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(ifName, interfaceInfo.getInterfaceType());
            if (!isOperational(ifInfo)) {
                continue;
            }

            listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }

        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(listBucket));
        logger.trace("installing the localBroadCast Group:{}", group);
        mdsalManager.syncInstallGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void removeLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGID(elanInfo.getElanTag());
        List<Bucket> listBuckets = new ArrayList<>();
        int bucketId = 0;
        listBuckets.add(getLocalBCGroupBucketInfo(interfaceInfo, bucketId));
        //listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, 1, interfaceInfo));
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(listBuckets));
        logger.trace("deleted the localBroadCast Group:{}", group);
        mdsalManager.syncRemoveGroup(dpnId, group);
    }

    public void removeElanBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        int bucketId = 0;
        List<Bucket> listBuckets = new ArrayList<>();
        listBuckets.add(getLocalBCGroupBucketInfo(interfaceInfo, bucketId));
        bucketId++;
        listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, bucketId, interfaceInfo));
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGID(elanInfo.getElanTag());
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(listBuckets));
        logger.trace("deleting the remoteBroadCast group:{}", group);
        mdsalManager.syncRemoveGroup(dpnId, group);
    }

    /**
     * Installs a flow in the External Tunnel table consisting in translating
     * the VNI retrieved from the packet that came over a tunnel with a TOR into
     * elanTag that will be used later in the ELANs pipeline.
     *
     * @param dpnId
     *            the dpn id
     * @param elanInfo
     *            the elan info
     */
    public void setExternalTunnelTable(BigInteger dpnId, ElanInstance elanInfo) {
        long elanTag = elanInfo.getElanTag();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId,
                                                          NwConstants.EXTERNAL_TUNNEL_TABLE,
                                                          getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanTag),
                                                          5,  // prio
                                                          elanInfo.getElanInstanceName(),  // flowName
                                                          0,  // idleTimeout
                                                          0,  // hardTimeout
                                                          ITMConstants.COOKIE_ITM_EXTERNAL.add(BigInteger.valueOf(elanTag)),
                                                          buildMatchesForVni(elanInfo.getSegmentationId()),
                                                          getInstructionsExtTunnelTable(elanTag) );

        mdsalManager.installFlow(flowEntity);
    }

    /**
     * Removes, from External Tunnel table, the flow that translates from VNI to elanTag.
     * Important: ensure this method is only called whenever there is no other ElanInterface in the specified DPN
     *
     * @param dpnId DPN whose Ext Tunnel table is going to be modified
     * @param elanInfo holds the elanTag needed for selecting the flow to be removed
     */
    public void unsetExternalTunnelTable(BigInteger dpnId, ElanInstance elanInfo) {
        // TODO (eperefr): Use DataStoreJobCoordinator in order to avoid that removing the last ElanInstance plus
        // adding a new one does (almost at the same time) are executed in that exact order

        String flowId = getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanInfo.getElanTag());
        FlowEntity flowEntity = new FlowEntity(dpnId);
        flowEntity.setTableId(NwConstants.EXTERNAL_TUNNEL_TABLE);
        flowEntity.setFlowId(flowId);
        mdsalManager.removeFlow(flowEntity);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE, getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, elanTag),
                5, String.format("%s:%d","ITM Flow Entry ",elanTag), 0,  0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(elanTag)), ElanUtils.getTunnelMatchesForServiceId((int)elanTag),
                getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntity);
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getUnknownDmacFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag, /*SH flag*/false),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)), getMatchesForElanTag(elanTag, /*SH flag*/false),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGID(elanTag)));

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntity);

        // only if vni is present in elanInfo, perform the following
        if (ElanUtils.isVxlan(elanInfo)) {
           Flow flowEntity2 = MDSALUtil.buildFlowNew(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getUnknownDmacFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag, /*SH flag*/true),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)), getMatchesForElanTag(elanTag, /*SH flag*/true),
                getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));
           mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntity2);
        }

    }


    private void removeStaticELanFlows(final ElanInstance elanInfo, final InterfaceInfo interfaceInfo) {
        BigInteger dpId = interfaceInfo.getDpId();
        /*
         * If there are not elan ports, remove the unknown smac and default dmac
         * flows
         */
        DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanInfo.getElanInstanceName(), dpId);
        if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null || dpnInterfaces.getInterfaces().isEmpty()) {
            // No more Elan Interfaces in this DPN
            logger.debug("deleting the elan: {} present on dpId: {}", elanInfo.getElanInstanceName(), dpId);
            removeDefaultTermFlow(dpId, elanInfo.getElanTag());
            removeDefaultTermFlow(dpId, interfaceInfo.getInterfaceTag());
            removeUnknownDmacFlow(dpId, elanInfo);
            removeElanBroadcastGroup(elanInfo, interfaceInfo);
            removeLocalBroadcastGroup(elanInfo, interfaceInfo);
            if (ElanUtils.isVxlan(elanInfo)) {
                unsetExternalTunnelTable(dpId, elanInfo);
            }
            removeFilterEqualsTable(elanInfo, interfaceInfo);
        } else {
            setupElanBroadcastGroups(elanInfo, dpId);
            setupLocalBroadcastGroups(elanInfo, interfaceInfo);
            removeFilterEqualsTable(elanInfo, interfaceInfo);
        }
    }

    private void removeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo) {
//        Flow flow = getUnknownDmacFlowEntity(dpId, elanInfo);
//        mdsalManager.removeFlow(dpId, flow);
        Flow flow = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE,
                                                                             elanInfo.getElanTag(), /*SH flag*/ false)))
                                     .setTableId(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE)
                                     .build();
        mdsalManager.removeFlow(dpId, flow);

        if (ElanUtils.isVxlan(elanInfo)) {
           Flow flow2 = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE,
                                                                                elanInfo.getElanTag(), /*SH flag*/ true)))
                                        .setTableId(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE)
                                        .build();
           mdsalManager.removeFlow(dpId, flow2);
        }


    }

    private void removeDefaultTermFlow(BigInteger dpId, long elanTag) {
        ElanUtils.removeTerminatingServiceAction(dpId, (int) elanTag);
    }

    private void bindService(ElanInstance elanInfo, String interfaceName) {
       // interfaceManager.bindService(interfaceName, ElanUtils.getServiceInfo(elanInfo.getElanInstanceName(), elanInfo.getElanTag(), interfaceName));

        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanUtils.getElanMetadataLabel(elanInfo.getElanTag()), MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(ElanConstants.ELAN_SMAC_TABLE, ++instructionKey));
        BoundServices
                serviceInfo =
                ElanUtils.getBoundServices(String.format("%s.%s.%s", "vpn",elanInfo.getElanInstanceName(), interfaceName),
                        ElanConstants.ELAN_SERVICE_INDEX, priority,
                        ElanConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
        ElanUtils.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.buildServiceId(interfaceName, ElanConstants.ELAN_SERVICE_INDEX), serviceInfo);
    }

    private void unbindService(ElanInstance elanInfo, String interfaceName) {
        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.buildServiceId(interfaceName,ElanConstants.ELAN_SERVICE_INDEX),
                ElanUtils.DEFAULT_CALLBACK);
    }

    private void unbindService(ElanInstance elanInfo, String interfaceName, int vlanId) {
        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.buildServiceId(interfaceName,ElanConstants.ELAN_SERVICE_INDEX),
                ElanUtils.DEFAULT_CALLBACK);
    }

    private String getFlowRef(long tableId, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    private String getUnknownDmacFlowRef(long tableId, long elanTag, boolean shFlag) {
        return new StringBuffer().append(tableId).append(elanTag).append(shFlag).toString();
    }

    private List<Action> getInterfacePortActions(InterfaceInfo interfaceInfo) {
        List<Action> listAction = new ArrayList<>();
        int actionKey = 0;
        listAction.add((new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {BigInteger.valueOf(interfaceInfo.getInterfaceTag())}, actionKey)).buildAction());
        actionKey++;
        listAction.add((new ActionInfo(ActionType.nx_resubmit,
                new String[] {String.valueOf(ElanConstants.ELAN_FILTER_EQUALS_TABLE)}, actionKey)).buildAction());
        return listAction;
    }

    private void updateElanDpnInterfacesList(String elanInstanceName, BigInteger dpId, List<String> interfaceNames) {
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                .setInterfaces(interfaceNames).setKey(new DpnInterfacesKey(dpId)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId),
                dpnInterface);
    }

    /**
     * Delete elan dpn interface from operational DS.
     *
     * @param elanInstanceName
     *            the elan instance name
     * @param dpId
     *            the dp id
     */
    private void deleteElanDpnInterface(String elanInstanceName, BigInteger dpId) {
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId));
    }

    private List<String> createElanInterfacesList(String elanInstanceName, String interfaceName, BigInteger dpId) {
        List<String> interfaceNames = new ArrayList<>();
        interfaceNames.add(interfaceName);
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                .setInterfaces(interfaceNames).setKey(new DpnInterfacesKey(dpId)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId),
                dpnInterface);
        return interfaceNames;
    }

    private void createElanInterfaceTablesList(String interfaceName) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceMacTables = ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> interfaceMacTables = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceMacTables);
        // Adding new Elan Interface Port to the operational DataStore without Static-Mac Entries..
        if(!interfaceMacTables.isPresent()) {
            ElanInterfaceMac elanInterfaceMacTable = new ElanInterfaceMacBuilder().setElanInterface(interfaceName).setKey(new ElanInterfaceMacKey(interfaceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName),
                    elanInterfaceMacTable);
        }
    }

    private void createElanStateList(String elanInstanceName, String interfaceName) {
        InstanceIdentifier<Elan> elanInstance = ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInterfaceLists = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanInstance);
        // Adding new Elan Interface Port to the operational DataStore without Static-Mac Entries..
        if(elanInterfaceLists.isPresent()) {
            List<String> interfaceLists = elanInterfaceLists.get().getElanInterfaces();
            if(interfaceLists == null) {
                interfaceLists = new ArrayList<>();
            }
            interfaceLists.add(interfaceName);
            Elan elanState = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(interfaceLists).setKey(new ElanKey(elanInstanceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName), elanState);
        }
    }

    private boolean isOperational(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            return false;
        }
        return interfaceInfo.getAdminState() == InterfaceInfo.InterfaceAdminState.ENABLED;
    }

    protected void updatedIfPrimaryAttributeChanged(ElanInterface elanInterface, boolean isUpdated) {
        String interfaceName = elanInterface.getName();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        BigInteger dpId = interfaceInfo.getDpId();
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> existingElanInterface = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanInterface.getElanInstanceName());

        if(!existingElanInterface.isPresent()) {
            return;
        }

        List<MacEntry> macEntries = existingElanInterface.get().getMacEntry();
        if(macEntries != null && !macEntries.isEmpty()) {
            for (MacEntry macEntry : macEntries) {
                if(isUpdated) {
                    ElanUtils.setupMacFlows(elanInfo, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT, macEntry.getMacAddress().getValue());
                } else {
                    ElanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry);
                }
            }
        }

        InstanceIdentifier<DpnInterfaces> dpnInterfaceId = ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInterface.getElanInstanceName(), interfaceInfo.getDpId());
        Optional<DpnInterfaces> dpnInterfaces =  ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, dpnInterfaceId);
        List<String> interfaceLists = dpnInterfaces.get().getInterfaces();

        if(isUpdated) {
            interfaceLists.add(elanInterface.getName());
        } else {
            interfaceLists.remove(elanInterface.getName());
        }

        DpnInterfaces  updateDpnInterfaces = new DpnInterfacesBuilder().setInterfaces(interfaceLists).setDpId(dpId).setKey(new DpnInterfacesKey(dpId)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, dpnInterfaceId, updateDpnInterfaces);

        if(isUpdated) {
            installFlowsAndGroups(elanInfo, interfaceInfo);
        } else {
            removeStaticELanFlows(elanInfo, interfaceInfo);
            unbindService(elanInfo, interfaceName);
        }
    }

    public void handleInternalTunnelStateEvent(BigInteger srcDpId, BigInteger dstDpId) {
        ElanDpnInterfaces dpnInterfaceLists =  ElanUtils.getElanDpnInterfacesList();
        if(dpnInterfaceLists == null) {
            return;
        }
        List<ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists.getElanDpnInterfacesList();
        for(ElanDpnInterfacesList elanDpns: elanDpnIf) {
            int cnt = 0;
            String elanName = elanDpns.getElanInstanceName();
            List<DpnInterfaces> dpnInterfaces = elanDpns.getDpnInterfaces();
            if(dpnInterfaces == null) {
               continue;
            }
            for (DpnInterfaces dpnIf : dpnInterfaces) {
               if(dpnIf.getDpId().equals(srcDpId) || dpnIf.getDpId().equals(dstDpId)) {
                   cnt++;
                }
            }
            if(cnt == 2) {
                logger.debug("Elan instance:{} is present b/w srcDpn:{} and dstDpn:{}", elanName, srcDpId, dstDpId);
                ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanName);
                // update Remote BC Group
                setupElanBroadcastGroups(elanInfo, srcDpId);

                DpnInterfaces dpnInterface = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, srcDpId);
                Set<String> interfaceLists = new HashSet<>();
                interfaceLists.addAll(dpnInterface.getInterfaces());
                for(String ifName : interfaceLists) {
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(ifName);
                    if (isOperational(interfaceInfo)) {
                        elanInterfaceManager.installDMacAddressTables(elanInfo, interfaceInfo, dstDpId);
                    }
                }
            }

        }
    }

    /**
     * Handle external tunnel state event.
     *
     * @param externalTunnel
     *            the external tunnel
     * @param intrf
     *            the interface
     */
    public void handleExternalTunnelStateEvent(ExternalTunnel externalTunnel, Interface intrf) {
        if (!validateExternalTunnelStateEvent(externalTunnel, intrf)) {
            return;
        }
        // dpId/externalNodeId will be available either in source or destination
        // based on the tunnel end point
        BigInteger dpId = null;
        NodeId externalNodeId = null;
        if (StringUtils.isNumeric(externalTunnel.getSourceDevice())) {
            dpId = new BigInteger(externalTunnel.getSourceDevice());
            externalNodeId = new NodeId(externalTunnel.getDestinationDevice());
        } else if (StringUtils.isNumeric(externalTunnel.getDestinationDevice())) {
            dpId = new BigInteger(externalTunnel.getDestinationDevice());
            externalNodeId = new NodeId(externalTunnel.getSourceDevice());
        }
        if (dpId == null || externalNodeId == null) {
            logger.error("Dp ID / externalNodeId not found in external tunnel {}", externalTunnel);
            return;
        }

        ElanDpnInterfaces dpnInterfaceLists = ElanUtils.getElanDpnInterfacesList();
        if (dpnInterfaceLists == null) {
            return;
        }
        List<ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists.getElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf) {
            String elanName = elanDpns.getElanInstanceName();
            ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanName);

            DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                    || dpnInterfaces.getInterfaces().isEmpty()) {
                continue;
            }
            logger.debug("Elan instance:{} is present in Dpn:{} ", elanName, dpId);

            setupElanBroadcastGroups(elanInfo, dpId);
            // install L2gwDevices local macs in dpn.
            ElanL2GatewayUtils.installL2gwDeviceMacsInDpn(dpId, externalNodeId, elanInfo);
            // Install dpn macs on external device
            ElanL2GatewayUtils.installDpnMacsInL2gwDevice(elanName, new HashSet<>(dpnInterfaces.getInterfaces()), dpId,
                    externalNodeId);
        }
        logger.info("Handled ExternalTunnelStateEvent for {}", externalTunnel);
    }

    /**
     * Validate external tunnel state event.
     *
     * @param externalTunnel
     *            the external tunnel
     * @param intrf
     *            the intrf
     * @return true, if successful
     */
    private boolean validateExternalTunnelStateEvent(ExternalTunnel externalTunnel, Interface intrf) {
        if (intrf.getOperStatus() == Interface.OperStatus.Up) {
            String srcDevice = externalTunnel.getDestinationDevice();
            String destDevice = externalTunnel.getSourceDevice();
            ExternalTunnel otherEndPointExtTunnel = ElanUtils.getExternalTunnel(srcDevice, destDevice,
                    LogicalDatastoreType.CONFIGURATION);
            if (logger.isTraceEnabled()) {
                logger.trace("Validating external tunnel state: src tunnel {}, dest tunnel {}", externalTunnel,
                        otherEndPointExtTunnel);
            }
            if (otherEndPointExtTunnel != null) {
                boolean otherEndPointInterfaceOperational = ElanUtils
                        .isInterfaceOperational(otherEndPointExtTunnel.getTunnelInterfaceName(), broker);
                if (otherEndPointInterfaceOperational) {
                    return true;
                } else {
                    logger.debug("Other end [{}] of the external tunnel is not yet UP for {}",
                            otherEndPointExtTunnel.getTunnelInterfaceName(), externalTunnel);
                }
            }
        }
        return false;
    }

    public void handleInterfaceUpdated(InterfaceInfo interfaceInfo, ElanInstance elanInstance, boolean isStateUp) {
        BigInteger dpId = interfaceInfo.getDpId();
        String elanName = elanInstance.getElanInstanceName();
        String ifName = interfaceInfo.getInterfaceName();
        logger.trace("Handling interface update event for interface with info {} , state {}", interfaceInfo, isStateUp);
        if(isStateUp) {

            DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if(dpnInterfaces == null) {
                createElanInterfacesList(elanName, interfaceInfo.getInterfaceName(), dpId);
            } else {
              List<String> dpnElanInterfaces = dpnInterfaces.getInterfaces();
                dpnElanInterfaces.add(interfaceInfo.getInterfaceName());
                DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                        .setInterfaces(dpnElanInterfaces).setKey(new DpnInterfacesKey(dpId)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanName, interfaceInfo.getDpId()), dpnInterface);
            }

            logger.trace("ElanInterface Service is installed for interface:{}", ifName);
            elanInterfaceManager.installFlowsAndGroups(elanInstance, interfaceInfo);
            elanInterfaceManager.installMacAddressTables(elanInstance, interfaceInfo);

            if (ElanUtils.isVxlan(elanInstance)) {
                List<PhysAddress> macAddresses = ElanUtils
                        .getElanInterfaceMacAddresses(interfaceInfo.getInterfaceName());
                if (macAddresses != null && !macAddresses.isEmpty()) {
                    ElanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(),
                            dpId, macAddresses);
                }
            }
        } else {

            DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if(dpnInterfaces != null) {
                List<String> dpnElanInterfaces = dpnInterfaces.getInterfaces();
                dpnElanInterfaces.remove(interfaceInfo.getInterfaceName());
                DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                        .setInterfaces(dpnElanInterfaces).setKey(new DpnInterfacesKey(dpId)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanName, interfaceInfo.getDpId()), dpnInterface);
            }
            logger.trace("ElanInterface Service is removed for the interface:{}", ifName);
            elanInterfaceManager.removeMacAddressTables(elanInstance, interfaceInfo);
            elanInterfaceManager.removeFlowsAndGroups(elanInstance, interfaceInfo);

            // Removing MACs from External Devices belonging to this ELAN
            if (ElanUtils.isVxlan(elanInstance)) {
                List<PhysAddress> macAddresses = ElanUtils
                        .getElanInterfaceMacAddresses(interfaceInfo.getInterfaceName());
                if (macAddresses != null && !macAddresses.isEmpty()) {
                    ElanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInstance, macAddresses);
                }
            }
        }
    }

    private List<MatchInfo> getMatchesForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getLportTagMetaData(LportTag),
                MetaDataUtil.METADATA_MASK_LPORT_TAG }));
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(LportTag)}));
        return mkMatches;
    }


    private List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                BigInteger.valueOf(LportTag)}));
        return mkMatches;
    }

    public void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo) {
        List<DpnInterfaces> dpns = ElanUtils.getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
        if (dpns == null) {
            return;
        }
        for (DpnInterfaces dpn : dpns) {
            setupElanBroadcastGroups(elanInfo, dpn.getDpId());
        }
    }

    public static List<Bucket> getRemoteBCGroupBucketsOfElanL2GwDevices(ElanInstance elanInfo, BigInteger dpnId,
            int bucketId) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        ConcurrentMap<String, L2GatewayDevice> map = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInfo.getElanInstanceName());
        for (L2GatewayDevice device : map.values()) {
            String interfaceName = ElanL2GatewayUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    device.getHwvtepNodeId());
            if (interfaceName == null) {
                continue;
            }
            List<Action> listActionInfo = ElanUtils.buildItmEgressActions(interfaceName, elanInfo.getSegmentationId());
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

}


