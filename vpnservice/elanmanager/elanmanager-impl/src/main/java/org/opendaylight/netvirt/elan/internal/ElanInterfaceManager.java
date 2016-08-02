/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.itm.globals.ITMConstants;
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
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
//import org.opendaylight.genius.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class in charge of handling creations, modifications and removals of
 * ElanInterfaces.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 *
 */
@SuppressWarnings("deprecation")
public class ElanInterfaceManager extends AsyncDataTreeChangeListenerBase<ElanInterface, ElanInterfaceManager>
        implements AutoCloseable {

    private ElanServiceProvider elanServiceProvider = null;
    private static volatile ElanInterfaceManager elanInterfaceManager = null;
    private static long waitTimeForSyncInstall;

    private Map<String, ConcurrentLinkedQueue<ElanInterface>> unProcessedElanInterfaces = new ConcurrentHashMap<String, ConcurrentLinkedQueue<ElanInterface>>();

    private static final Logger logger = LoggerFactory.getLogger(ElanInterfaceManager.class);

    public ElanInterfaceManager(ElanServiceProvider elanServiceProvider) {
        super(ElanInterface.class, ElanInterfaceManager.class);
        this.elanServiceProvider = elanServiceProvider;
    }

    public static ElanInterfaceManager getElanInterfaceManager(ElanServiceProvider elanServiceProvider) {
        if (elanInterfaceManager == null) {
            synchronized (ElanInterfaceManager.class) {
                if (elanInterfaceManager == null) {
                    elanInterfaceManager = new ElanInterfaceManager(elanServiceProvider);
                    Long waitTime = Long.getLong("wait.time.sync.install");
                    if (waitTime == null) {
                        waitTime = 300L;
                    }
                    waitTimeForSyncInstall = waitTime;
                }
            }
        }
        return elanInterfaceManager;
    }

    @Override
    protected InstanceIdentifier<ElanInterface> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {
        String interfaceName = del.getName();
        ElanInstance elanInfo = ElanUtils.getElanInstanceByName(del.getElanInstanceName());
        /*
         * Handling in case the elan instance is deleted.If the Elan instance is
         * deleted, there is no need to explicitly delete the elan interfaces
         */
        if (elanInfo == null) {
            return;
        }
        InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(interfaceName);
        String elanInstanceName = elanInfo.getElanInstanceName();
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceRemoveWorkerOnElan configWorker = new InterfaceRemoveWorkerOnElan(elanInstanceName, elanInfo,
                interfaceName, interfaceInfo, false, this);
        coordinator.enqueueJob(elanInstanceName, configWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    public void removeElanInterface(ElanInstance elanInfo, String interfaceName, InterfaceInfo interfaceInfo,
            boolean isInterfaceStateRemoved) {
        String elanName = elanInfo.getElanInstanceName();
        boolean isLastElanInterface = false;
        long elanTag = elanInfo.getElanTag();
        WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        WriteTransaction deleteFlowGroupTx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        Elan elanState = removeElanStateForInterface(elanInfo, interfaceName, tx);
        if (elanState == null) {
            return;
        }
        List<String> elanInterfaces = elanState.getElanInterfaces();
        if (elanInterfaces.size() == 0) {
            isLastElanInterface = true;
        }
        if (interfaceInfo != null) {
            BigInteger dpId = interfaceInfo.getDpId();
            DpnInterfaces dpnInterfaces = removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId, interfaceName,
                    elanTag, tx);
            /*
             * If there are not elan ports, remove the unknown dmac, terminating
             * service table flows, remote/local bc group
             */
            if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                    || dpnInterfaces.getInterfaces().isEmpty()) {
                // No more Elan Interfaces in this DPN
                logger.debug("deleting the elan: {} present on dpId: {}", elanInfo.getElanInstanceName(), dpId);
                removeDefaultTermFlow(dpId, elanInfo.getElanTag());
                removeUnknownDmacFlow(dpId, elanInfo, deleteFlowGroupTx);
                removeElanBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
                removeLocalBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
                if (ElanUtils.isVxlan(elanInfo)) {
                    unsetExternalTunnelTable(dpId, elanInfo);
                }
            } else {
                setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo);
            }
        }
        ElanUtils.waitForTransactionToComplete(tx);
        ElanUtils.waitForTransactionToComplete(deleteFlowGroupTx);
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceRemoveWorkerOnElanInterface removeInterfaceWorker = new InterfaceRemoveWorkerOnElanInterface(
                interfaceName, elanInfo, interfaceInfo, isInterfaceStateRemoved, this, isLastElanInterface);
        coordinator.enqueueJob(interfaceName, removeInterfaceWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    private Elan removeElanStateForInterface(ElanInstance elanInfo, String interfaceName, WriteTransaction tx) {
        String elanName = elanInfo.getElanInstanceName();
        Elan elanState = ElanUtils.getElanByName(elanName);
        if (elanState == null) {
            return elanState;
        }
        List<String> elanInterfaces = elanState.getElanInterfaces();
        elanInterfaces.remove(interfaceName);
        if (elanInterfaces.isEmpty()) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName));
            tx.delete(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanName));
            tx.delete(LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInfoEntriesOperationalDataPath(elanInfo.getElanTag()));
        } else {
            Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName)
                    .setKey(new ElanKey(elanName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName),
                    updateElanState);
        }
        return elanState;
    }

    private void deleteElanInterfaceFromConfigDS(String interfaceName, WriteTransaction tx) {
        // removing the ElanInterface from the config data_store if interface is
        // not present in Interface config DS
        if (elanServiceProvider.getInterfaceManager().getInterfaceInfoFromConfigDataStore(interfaceName) == null) {
            tx.delete(LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
        }
    }

    void removeEntriesForElanInterface(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String interfaceName,
            boolean isInterfaceStateRemoved, boolean isLastElanInterface) {
        String elanName = elanInfo.getElanInstanceName();
        WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        WriteTransaction deleteFlowGroupTx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils
                .getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        logger.debug("Removing the Interface:{} from elan:{}", interfaceName, elanName);
        if (interfaceInfo != null) {
            Optional<ElanInterfaceMac> existingElanInterfaceMac = ElanUtils.read(elanServiceProvider.getBroker(),
                    LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
            if (existingElanInterfaceMac.isPresent()) {
                List<PhysAddress> macAddresses = new ArrayList<PhysAddress>();
                List<MacEntry> existingMacEntries = existingElanInterfaceMac.get().getMacEntry();
                List<MacEntry> macEntries = new ArrayList<>();
                if (existingMacEntries != null && !existingMacEntries.isEmpty()) {
                    macEntries.addAll(existingMacEntries);
                }
                if (!macEntries.isEmpty()) {
                    for (MacEntry macEntry : macEntries) {
                        logger.debug("removing the  mac-entry:{} present on elanInterface:{}",
                                macEntry.getMacAddress().getValue(), interfaceName);
                        if (!isLastElanInterface) {
                            tx.delete(LogicalDatastoreType.OPERATIONAL,
                                    ElanUtils.getMacEntryOperationalDataPath(elanName, macEntry.getMacAddress()));
                        }
                        ElanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry, deleteFlowGroupTx);
                        macAddresses.add(macEntry.getMacAddress());
                    }

                    // Removing all those MACs from External Devices belonging
                    // to this ELAN
                    if (ElanUtils.isVxlan(elanInfo)) {
                        ElanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInfo, macAddresses);
                    }
                }
            }
            removeDefaultTermFlow(interfaceInfo.getDpId(), interfaceInfo.getInterfaceTag());
            removeFilterEqualsTable(elanInfo, interfaceInfo, deleteFlowGroupTx);
        } else {
            // Interface does not exist in ConfigDS, so lets remove everything
            // about that interface related to Elan
            ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
            if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
                List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
                for (MacEntry macEntry : macEntries) {
                    tx.delete(LogicalDatastoreType.OPERATIONAL,
                            ElanUtils.getMacEntryOperationalDataPath(elanName, macEntry.getMacAddress()));
                }
            }
        }
        tx.delete(LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        if (!isInterfaceStateRemoved) {
            unbindService(elanInfo, interfaceName, tx);
        }
        deleteElanInterfaceFromConfigDS(interfaceName, tx);
        ElanUtils.waitForTransactionToComplete(tx);
        ElanUtils.waitForTransactionToComplete(deleteFlowGroupTx);
    }

    private DpnInterfaces removeElanDpnInterfaceFromOperationalDataStore(String elanName, BigInteger dpId,
            String interfaceName, long elanTag, WriteTransaction tx) {
        DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
        if (dpnInterfaces != null) {
            List<String> interfaceLists = dpnInterfaces.getInterfaces();
            interfaceLists.remove(interfaceName);

            if (interfaceLists == null || interfaceLists.isEmpty()) {
                deleteAllRemoteMacsInADpn(elanName, dpId, elanTag);
                deleteElanDpnInterface(elanName, dpId, tx);
            } else {
                dpnInterfaces = updateElanDpnInterfacesList(elanName, dpId, interfaceLists, tx);
            }
        }
        return dpnInterfaces;
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
                        elanServiceProvider.getMdsalManager().removeFlow(dpId,
                                MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                                        ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId,
                                                currentDpId, mac.getMacAddress().getValue(), elanTag)));
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {
        // updating the static-Mac Entries for the existing elanInterface
        String elanName = update.getElanInstanceName();
        String interfaceName = update.getName();
        InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(interfaceName);
        List<PhysAddress> existingPhysAddress = original.getStaticMacEntries();
        List<PhysAddress> updatedPhysAddress = update.getStaticMacEntries();
        if (updatedPhysAddress != null && !updatedPhysAddress.isEmpty()) {
            List<PhysAddress> existingClonedPhyAddress = new ArrayList<>();
            if (existingPhysAddress != null && !existingPhysAddress.isEmpty()) {
                existingClonedPhyAddress.addAll(0, existingPhysAddress);
                existingPhysAddress.removeAll(updatedPhysAddress);
                updatedPhysAddress.removeAll(existingClonedPhyAddress);
                // removing the PhyAddress which are not presented in the
                // updated List
                for (PhysAddress physAddress : existingPhysAddress) {
                    removeInterfaceStaticMacEntires(elanName, interfaceName, physAddress);
                }
            }
            // Adding the new PhysAddress which are presented in the updated
            // List
            if (updatedPhysAddress.size() > 0) {
                for (PhysAddress physAddress : updatedPhysAddress) {
                    InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanName, physAddress);
                    Optional<MacEntry> existingMacEntry = ElanUtils.read(elanServiceProvider.getBroker(),
                            LogicalDatastoreType.OPERATIONAL, macId);
                    WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
                    if (existingMacEntry.isPresent()) {
                        elanServiceProvider.getElanForwardingEntriesHandler().updateElanInterfaceForwardingTablesList(
                                elanName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get(),
                                tx);
                    } else {
                        elanServiceProvider.getElanForwardingEntriesHandler().addElanInterfaceForwardingTableList(
                                ElanUtils.getElanInstanceByName(elanName), interfaceName, physAddress, tx);
                    }
                    ElanUtils.waitForTransactionToComplete(tx);
                }
            }
        } else if (existingPhysAddress != null && !existingPhysAddress.isEmpty()) {
            for (PhysAddress physAddress : existingPhysAddress) {
                removeInterfaceStaticMacEntires(elanName, interfaceName, physAddress);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded) {
        String elanInstanceName = elanInterfaceAdded.getElanInstanceName();
        String interfaceName = elanInterfaceAdded.getName();
        InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(interfaceName);
        if (interfaceInfo == null) {
            logger.warn("Interface {} is removed from Interface Oper DS due to port down ", interfaceName);
            return;
        }
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);

        if (elanInstance == null) {
            elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                    .setDescription(elanInterfaceAdded.getDescription()).build();
            // Add the ElanInstance in the Configuration data-store
            WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
            List<String> elanInterfaces = new ArrayList<String>();
            elanInterfaces.add(interfaceName);
            ElanUtils.updateOperationalDataStore(elanServiceProvider.getBroker(), elanServiceProvider.getIdManager(),
                    elanInstance, elanInterfaces, tx);
            ElanUtils.waitForTransactionToComplete(tx);
            elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);
        }

        Long elanTag = elanInstance.getElanTag();
        // If elan tag is not updated, then put the elan interface into
        // unprocessed entry map and entry. Let entries
        // in this map get processed during ELAN update DCN.
        if (elanTag == null) {
            ConcurrentLinkedQueue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstanceName);
            if (elanInterfaces == null) {
                elanInterfaces = new ConcurrentLinkedQueue<ElanInterface>();
            }
            elanInterfaces.add(elanInterfaceAdded);
            unProcessedElanInterfaces.put(elanInstanceName, elanInterfaces);
            return;
        }
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceAddWorkerOnElan addWorker = new InterfaceAddWorkerOnElan(elanInstanceName, elanInterfaceAdded,
                interfaceInfo, elanInstance, this);
        coordinator.enqueueJob(elanInstanceName, addWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    void handleunprocessedElanInterfaces(ElanInstance elanInstance) {
        Queue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstance.getElanInstanceName());
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        for (ElanInterface elanInterface : elanInterfaces) {
            String interfaceName = elanInterface.getName();
            InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(interfaceName);
            addElanInterface(elanInterface, interfaceInfo, elanInstance);
        }
    }

    void programRemoteDmacFlow(ElanInstance elanInstance, InterfaceInfo interfaceInfo,
            WriteTransaction writeFlowGroupTx) {
        ElanDpnInterfacesList elanDpnInterfacesList = ElanUtils
                .getElanDpnInterfacesList(elanInstance.getElanInstanceName());
        List<DpnInterfaces> dpnInterfaceLists = null;
        if (elanDpnInterfacesList != null) {
            dpnInterfaceLists = elanDpnInterfacesList.getDpnInterfaces();
        }
        if (dpnInterfaceLists == null) {
            dpnInterfaceLists = new ArrayList<DpnInterfaces>();
        }
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            if (dpnInterfaces.getDpId().equals(interfaceInfo.getDpId())) {
                continue;
            }
            List<String> remoteElanInterfaces = dpnInterfaces.getInterfaces();
            for (String remoteIf : remoteElanInterfaces) {
                ElanInterfaceMac elanIfMac = ElanUtils.getElanInterfaceMacByInterfaceName(remoteIf);
                InterfaceInfo remoteInterface = elanServiceProvider.getInterfaceManager().getInterfaceInfo(remoteIf);
                if (elanIfMac == null) {
                    continue;
                }
                List<MacEntry> remoteMacEntries = elanIfMac.getMacEntry();
                if (remoteMacEntries != null) {
                    for (MacEntry macEntry : remoteMacEntries) {
                        PhysAddress physAddress = macEntry.getMacAddress();
                        ElanUtils.setupRemoteDmacFlow(interfaceInfo.getDpId(), remoteInterface.getDpId(),
                                remoteInterface.getInterfaceTag(), elanInstance.getElanTag(), physAddress.getValue(),
                                elanInstance.getElanInstanceName(), writeFlowGroupTx);
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

        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        if (elanInfo == null) {
            List<String> elanInterfaces = new ArrayList<String>();
            elanInterfaces.add(interfaceName);
            ElanUtils.updateOperationalDataStore(elanServiceProvider.getBroker(), elanServiceProvider.getIdManager(),
                    elanInstance, elanInterfaces, tx);
        } else {
            createElanStateList(elanInstanceName, interfaceName, tx);
        }
        boolean isFirstInterfaceInDpn = false;
        // Specific actions to the DPN where the ElanInterface has been added,
        // for example, programming the
        // External tunnel table if needed or adding the ElanInterface to the
        // DpnInterfaces in the operational DS.
        BigInteger dpId = (interfaceInfo != null) ? dpId = interfaceInfo.getDpId() : null;
        DpnInterfaces dpnInterfaces = null;
        if (dpId != null && !dpId.equals(ElanConstants.INVALID_DPN)) {
            InstanceIdentifier<DpnInterfaces> elanDpnInterfaces = ElanUtils
                    .getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
            Optional<DpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(elanServiceProvider.getBroker(),
                    LogicalDatastoreType.OPERATIONAL, elanDpnInterfaces);
            if (!existingElanDpnInterfaces.isPresent()) {
                isFirstInterfaceInDpn = true;
                // ELAN's 1st ElanInterface added to this DPN
                dpnInterfaces = createElanInterfacesList(elanInstanceName, interfaceName, dpId, tx);
                // The 1st ElanInterface in a DPN must program the Ext Tunnel
                // table, but only if Elan has VNI
                if (ElanUtils.isVxlan(elanInstance)) {
                    setExternalTunnelTable(dpId, elanInstance);
                }
                ElanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance);
            } else {
                List<String> elanInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                elanInterfaces.add(interfaceName);
                if (elanInterfaces.size() == 1) {// 1st dpn interface
                    ElanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance);
                }
                dpnInterfaces = updateElanDpnInterfacesList(elanInstanceName, dpId, elanInterfaces, tx);
            }
        }

        // add code to install Local/Remote BC group, unknow DMAC entry,
        // terminating service table flow entry
        // call bindservice of interfacemanager to create ingress table flow
        // enty.
        // Add interface to the ElanInterfaceForwardingEntires Container
        createElanInterfaceTablesList(interfaceName, tx);
        if (interfaceInfo != null) {
            installEntriesForFirstInterfaceonDpn(elanInstance, interfaceInfo, dpnInterfaces, isFirstInterfaceInDpn, tx);
        }
        ElanUtils.waitForTransactionToComplete(tx);

        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        InterfaceAddWorkerOnElanInterface addWorker = new InterfaceAddWorkerOnElanInterface(interfaceName,
                elanInterface, interfaceInfo, elanInstance, isFirstInterfaceInDpn, this);
        coordinator.enqueueJob(interfaceName, addWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    void setupEntriesForElanInterface(ElanInstance elanInstance, ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn) {
        String elanInstanceName = elanInstance.getElanInstanceName();
        String interfaceName = elanInterface.getName();
        WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        BigInteger dpId = interfaceInfo.getDpId();
        WriteTransaction writeFlowGroupTx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        installEntriesForElanInterface(elanInstance, interfaceInfo, isFirstInterfaceInDpn, tx, writeFlowGroupTx);
        List<PhysAddress> staticMacAddresses = elanInterface.getStaticMacEntries();
        if (staticMacAddresses != null) {
            boolean isInterfaceOperational = isOperational(interfaceInfo);
            for (PhysAddress physAddress : staticMacAddresses) {
                InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName, physAddress);
                Optional<MacEntry> existingMacEntry = ElanUtils.read(elanServiceProvider.getBroker(),
                        LogicalDatastoreType.OPERATIONAL, macId);
                if (existingMacEntry.isPresent()) {
                    elanServiceProvider.getElanForwardingEntriesHandler().updateElanInterfaceForwardingTablesList(
                            elanInstanceName, interfaceName, existingMacEntry.get().getInterface(),
                            existingMacEntry.get(), tx);
                } else {
                    elanServiceProvider.getElanForwardingEntriesHandler()
                            .addElanInterfaceForwardingTableList(elanInstance, interfaceName, physAddress, tx);
                }

                if (isInterfaceOperational) {
                    // Setting SMAC, DMAC, UDMAC in this DPN and also in other
                    // DPNs
                    ElanUtils.setupMacFlows(elanInstance, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT,
                            physAddress.getValue(), writeFlowGroupTx);
                }
            }

            if (isInterfaceOperational) {
                // Add MAC in TOR's remote MACs via OVSDB. Outside of the loop
                // on purpose.
                ElanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId,
                        staticMacAddresses);
            }
        }
        ElanUtils.waitForTransactionToComplete(tx);
        ElanUtils.waitForTransactionToComplete(writeFlowGroupTx);
    }

    protected void removeInterfaceStaticMacEntires(String elanInstanceName, String interfaceName,
            PhysAddress physAddress) {
        InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(interfaceName);
        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName, physAddress);
        Optional<MacEntry> existingMacEntry = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, macId);

        if (!existingMacEntry.isPresent()) {
            return;
        }

        MacEntry macEntry = new MacEntryBuilder().setMacAddress(physAddress).setInterface(interfaceName)
                .setKey(new MacEntryKey(physAddress)).build();
        WriteTransaction tx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
        elanServiceProvider.getElanForwardingEntriesHandler().deleteElanInterfaceForwardingEntries(
                ElanUtils.getElanInstanceByName(elanInstanceName), interfaceInfo, macEntry, tx);
        elanServiceProvider.getElanForwardingEntriesHandler().deleteElanInterfaceMacForwardingEntries(interfaceName,
                physAddress, tx);
        ElanUtils.waitForTransactionToComplete(tx);
    }

    private InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName, PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    private void installEntriesForElanInterface(ElanInstance elanInstance, InterfaceInfo interfaceInfo,
            boolean isFirstInterfaceInDpn, WriteTransaction tx, WriteTransaction writeFlowGroupTx) {
        if (!isOperational(interfaceInfo)) {
            return;
        }
        BigInteger dpId = interfaceInfo.getDpId();
        ElanUtils.setupTermDmacFlows(interfaceInfo, elanServiceProvider.getMdsalManager(), writeFlowGroupTx);
        setupFilterEqualsTable(elanInstance, interfaceInfo, writeFlowGroupTx);
        if (isFirstInterfaceInDpn) {
            // Terminating Service , UnknownDMAC Table.
            setupTerminateServiceTable(elanInstance, dpId, writeFlowGroupTx);
            setupUnknownDMacTable(elanInstance, dpId, writeFlowGroupTx);
            // update the remote-DPNs remoteBC group entry with Tunnels
            setElanBCGrouponOtherDpns(elanInstance, dpId, writeFlowGroupTx);
            /*
             * Install remote DMAC flow. This is required since this DPN is
             * added later to the elan instance and remote DMACs of other
             * interfaces in this elan instance are not present in the current
             * dpn.
             */
            programRemoteDmacFlow(elanInstance, interfaceInfo, writeFlowGroupTx);
        }
        // bind the Elan service to the Interface
        bindService(elanInstance, interfaceInfo.getInterfaceName(), tx);
    }

    public void installEntriesForFirstInterfaceonDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            DpnInterfaces dpnInterfaces, boolean isFirstInterfaceInDpn, WriteTransaction tx) {
        if (!isOperational(interfaceInfo)) {
            return;
        }
        // LocalBroadcast Group creation with elan-Interfaces
        setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo);
        if (isFirstInterfaceInDpn) {
            logger.trace("waitTimeForSyncInstall is {}", waitTimeForSyncInstall);
            BigInteger dpId = interfaceInfo.getDpId();
            // RemoteBroadcast Group creation
            try {
                Thread.sleep(waitTimeForSyncInstall);
            } catch (InterruptedException e1) {
                logger.warn("Error while waiting for local BC group for ELAN {} to install", elanInfo);
            }
            setupElanBroadcastGroups(elanInfo, dpnInterfaces, dpId);
            try {
                Thread.sleep(waitTimeForSyncInstall);
            } catch (InterruptedException e1) {
                logger.warn("Error while waiting for local BC group for ELAN {} to install", elanInfo);
            }
        }
    }

    public void setupFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction writeFlowGroupTx) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag), 9, elanInfo.getElanInstanceName(), 0, 0,
                ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                ElanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        elanServiceProvider.getMdsalManager().addFlowToTx(interfaceInfo.getDpId(), flow, writeFlowGroupTx);

        Flow flowEntry = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, 1000 + ifTag), 10, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getMatchesForFilterEqualsLPortTag(ifTag), MDSALUtil.buildInstructionsDrop());

        elanServiceProvider.getMdsalManager().addFlowToTx(interfaceInfo.getDpId(), flowEntry, writeFlowGroupTx);
    }

    public void removeFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag), 9, elanInfo.getElanInstanceName(), 0, 0,
                ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                ElanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        elanServiceProvider.getMdsalManager().removeFlowToTx(interfaceInfo.getDpId(), flow, deleteFlowGroupTx);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, 1000 + ifTag), 10, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getMatchesForFilterEqualsLPortTag(ifTag), MDSALUtil.buildInstructionsDrop());

        elanServiceProvider.getMdsalManager().removeFlowToTx(interfaceInfo.getDpId(), flowEntity, deleteFlowGroupTx);
    }

    private List<Bucket> getRemoteBCGroupBucketInfos(ElanInstance elanInfo, int bucketKeyStart,
            InterfaceInfo interfaceInfo) {
        return getRemoteBCGroupBuckets(elanInfo, null, interfaceInfo.getDpId(), bucketKeyStart);
    }

    private List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId,
            int bucketId) {
        int elanTag = elanInfo.getElanTag().intValue();
        List<Bucket> listBucketInfo = new ArrayList<Bucket>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanDpns, dpnId, bucketId, elanTag));
        listBucketInfo.addAll(getRemoteBCGroupExternalPortBuckets(elanDpns, dpnInterfaces, dpnId, bucketId));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnId, bucketId));
        return listBucketInfo;
    }

    private List<Bucket> getRemoteBCGroupTunnelBuckets(ElanDpnInterfacesList elanDpns, BigInteger dpnId, int bucketId,
            int elanTag) {
        List<Bucket> listBucketInfo = new ArrayList<Bucket>();
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.getDpnInterfaces()) {
                if (ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId
                        && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = ElanUtils.getInternalTunnelItmEgressAction(dpnId,
                                dpnInterface.getDpId(), elanTag);
                        if (listActionInfo.isEmpty()) {
                            continue;
                        }
                        listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                                MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                        bucketId++;
                    } catch (Exception ex) {
                        logger.error("Logical Group Interface not found between source Dpn - {}, destination Dpn - {} ",
                                dpnId, dpnInterface.getDpId());
                    }
                }
            }
        }
        return listBucketInfo;
    }

    private List<Bucket> getRemoteBCGroupExternalPortBuckets(ElanDpnInterfacesList elanDpns,
            DpnInterfaces dpnInterfaces, BigInteger dpnId, int bucketId) {
        DpnInterfaces currDpnInterfaces = dpnInterfaces != null ? dpnInterfaces : getDpnInterfaces(elanDpns, dpnId);
        if (currDpnInterfaces == null || !ElanUtils.isDpnPresent(currDpnInterfaces.getDpId())
                || currDpnInterfaces.getInterfaces() == null || currDpnInterfaces.getInterfaces().isEmpty()) {
            return Collections.emptyList();
        }

        List<Bucket> listBucketInfo = new ArrayList<Bucket>();
        for (String interfaceName : currDpnInterfaces.getInterfaces()) {
            if (ElanUtils.isExternal(interfaceName)) {
                List<Action> listActionInfo = ElanUtils.getExternalPortItmEgressAction(interfaceName);
                if (!listActionInfo.isEmpty()) {
                    listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                    bucketId++;
                }
            }
        }
        return listBucketInfo;
    }

    private DpnInterfaces getDpnInterfaces(ElanDpnInterfacesList elanDpns, BigInteger dpnId) {
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.getDpnInterfaces()) {
                if (dpnInterface.getDpId() == dpnId) {
                    return dpnInterface;
                }
            }
        }
        return null;
    }

    private void setElanBCGrouponOtherDpns(ElanInstance elanInfo, BigInteger dpId, WriteTransaction tx) {
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        List<Bucket> listBucket = new ArrayList<Bucket>();
        int bucketId = 0;
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if (elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for (DpnInterfaces dpnInterface : dpnInterfaceses) {
                List<Bucket> remoteListBucketInfo = new ArrayList<Bucket>();
                if (ElanUtils.isDpnPresent(dpnInterface.getDpId()) && !dpnInterface.getDpId().equals(dpId)
                        && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    List<Action> listAction = new ArrayList<Action>();
                    int actionKey = 0;
                    listAction.add((new ActionInfo(ActionType.group,
                            new String[] { String.valueOf(ElanUtils.getElanLocalBCGID(elanTag)) }, ++actionKey))
                                    .buildAction());
                    listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                    bucketId++;
                    remoteListBucketInfo.addAll(listBucket);
                    for (DpnInterfaces otherFes : dpnInterfaceses) {
                        if (ElanUtils.isDpnPresent(otherFes.getDpId()) && otherFes.getDpId() != dpnInterface.getDpId()
                                && otherFes.getInterfaces() != null && !otherFes.getInterfaces().isEmpty()) {
                            try {
                                List<Action> remoteListActionInfo = ElanUtils.getInternalTunnelItmEgressAction(
                                        dpnInterface.getDpId(), otherFes.getDpId(), elanTag);
                                if (!remoteListActionInfo.isEmpty()) {
                                    remoteListBucketInfo
                                            .add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil.GROUP_WEIGHT,
                                                    bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                                    bucketId++;
                                }
                            } catch (Exception ex) {
                                logger.error(
                                        "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} ",
                                        dpnInterface.getDpId(), otherFes.getDpId());
                                return;
                            }
                        }
                    }
                    List<Bucket> elanL2GwDevicesBuckets = getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpId,
                            bucketId);
                    remoteListBucketInfo.addAll(elanL2GwDevicesBuckets);

                    if (remoteListBucketInfo.size() == 0) {
                        logger.debug("No ITM is present on Dpn - {} ", dpnInterface.getDpId());
                        continue;
                    }
                    Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                            MDSALUtil.buildBucketLists(remoteListBucketInfo));
                    elanServiceProvider.getMdsalManager().addGroupToTx(dpnInterface.getDpId(), group, tx);
                }
            }
        }
    }

    /**
     * Returns the bucket info with the given interface as the only bucket.
     */
    private Bucket getLocalBCGroupBucketInfo(InterfaceInfo interfaceInfo, int bucketIdStart) {
        return MDSALUtil.buildBucket(getInterfacePortActions(interfaceInfo), MDSALUtil.GROUP_WEIGHT, bucketIdStart,
                MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP);
    }

    private List<MatchInfo> getMatchesForElanTag(Long elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { ElanUtils.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE }));
        return mkMatches;
    }

    private List<MatchInfo> buildMatchesForVni(Long vni) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        MatchInfo match = new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] { BigInteger.valueOf(vni) });
        mkMatches.add(match);
        return mkMatches;
    }

    private List<Instruction> getInstructionsForOutGroup(long groupId) {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List<Action> actions = new ArrayList<Action>();
        actions.add(new ActionInfo(ActionType.group, new String[] { Long.toString(groupId) }).buildAction());
        mkInstructions.add(MDSALUtil.getWriteActionsInstruction(actions, 0));
        return mkInstructions;
    }

    private List<MatchInfo> getMatchesForElanTag(long elanTag, boolean isSHFlagSet) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag, isSHFlagSet), MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG }));
        return mkMatches;
    }

    /**
     * Builds the list of instructions to be installed in the External Tunnel
     * table (38), which so far consists in writing the elanTag in metadata and
     * send packet to the new DHCP table
     *
     * @param elanTag
     *            elanTag to be written in metadata when flow is selected
     * @return the instructions ready to be installed in a flow
     */
    private List<InstructionInfo> getInstructionsExtTunnelTable(Long elanTag) {
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.write_metadata,
                new BigInteger[] { ElanUtils.getElanMetadataLabel(elanTag), ElanUtils.getElanMetadataMask() }));
        // TODO: We should point to SMAC or DMAC depending on a configuration
        // property to enable
        // mac learning
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_DMAC_TABLE }));

        return mkInstructions;
    }

    // Install DMAC entry on dst DPN
    public void installDMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId) {
        String interfaceName = interfaceInfo.getInterfaceName();
        ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            WriteTransaction writeFlowTx = elanServiceProvider.getBroker().newWriteOnlyTransaction();
            List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
            for (MacEntry macEntry : macEntries) {
                PhysAddress physAddress = macEntry.getMacAddress();
                ElanUtils.setupDMacFlowonRemoteDpn(elanInfo, interfaceInfo, dstDpId, physAddress.getValue(),
                        writeFlowTx);
            }
            writeFlowTx.submit();
        }
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId) {
        setupElanBroadcastGroups(elanInfo, null, dpnId);
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId) {
        List<Bucket> listBucket = new ArrayList<Bucket>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        List<Action> listAction = new ArrayList<Action>();
        listAction.add((new ActionInfo(ActionType.group,
                new String[] { String.valueOf(ElanUtils.getElanLocalBCGID(elanTag)) }, ++actionKey)).buildAction());
        listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnInterfaces, dpnId, bucketId);
        listBucket.addAll(listBucketInfoRemote);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        logger.trace("Installing the remote BroadCast Group:{}", group);
        elanServiceProvider.getMdsalManager().syncInstallGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void setupLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo) {
        List<Bucket> listBucket = new ArrayList<Bucket>();
        int bucketId = 0;
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGID(elanInfo.getElanTag());

        List<String> interfaces = new ArrayList<String>();
        if (newDpnInterface != null) {
            interfaces = newDpnInterface.getInterfaces();
        }
        for (String ifName : interfaces) {
            // In case if there is a InterfacePort in the cache which is not in
            // operational state, skip processing it
            InterfaceInfo ifInfo = elanServiceProvider.getInterfaceManager()
                    .getInterfaceInfoFromOperationalDataStore(ifName, interfaceInfo.getInterfaceType());
            if (!isOperational(ifInfo)) {
                continue;
            }

            if (!ElanUtils.isExternal(ifName)) {
                listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId,
                        MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                bucketId++;
            }
        }

        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        logger.trace("installing the localBroadCast Group:{}", group);
        elanServiceProvider.getMdsalManager().syncInstallGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void removeLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGID(elanInfo.getElanTag());
        List<Bucket> listBuckets = new ArrayList<>();
        int bucketId = 0;
        listBuckets.add(getLocalBCGroupBucketInfo(interfaceInfo, bucketId));
        // listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, 1,
        // interfaceInfo));
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBuckets));
        logger.trace("deleted the localBroadCast Group:{}", group);
        elanServiceProvider.getMdsalManager().removeGroupToTx(dpnId, group, deleteFlowGroupTx);
    }

    public void removeElanBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
        List<Bucket> listBuckets = new ArrayList<>();
        List<Action> listAction = new ArrayList<Action>();
        listAction.add((new ActionInfo(ActionType.group,
                new String[] { String.valueOf(ElanUtils.getElanLocalBCGID(elanTag)) }, ++actionKey)).buildAction());
        listBuckets.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, bucketId, interfaceInfo));
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGID(elanInfo.getElanTag());
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBuckets));
        logger.trace("deleting the remoteBroadCast group:{}", group);
        elanServiceProvider.getMdsalManager().removeGroupToTx(dpnId, group, deleteFlowGroupTx);
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
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.EXTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanTag), 5, // prio
                elanInfo.getElanInstanceName(), // flowName
                0, // idleTimeout
                0, // hardTimeout
                ITMConstants.COOKIE_ITM_EXTERNAL.add(BigInteger.valueOf(elanTag)),
                buildMatchesForVni(elanInfo.getSegmentationId()), getInstructionsExtTunnelTable(elanTag));

        elanServiceProvider.getMdsalManager().installFlow(flowEntity);
    }

    /**
     * Removes, from External Tunnel table, the flow that translates from VNI to
     * elanTag. Important: ensure this method is only called whenever there is
     * no other ElanInterface in the specified DPN
     *
     * @param dpnId
     *            DPN whose Ext Tunnel table is going to be modified
     * @param elanInfo
     *            holds the elanTag needed for selecting the flow to be removed
     */
    public void unsetExternalTunnelTable(BigInteger dpnId, ElanInstance elanInfo) {
        // TODO: Use DataStoreJobCoordinator in order to avoid that removing the
        // last ElanInstance plus
        // adding a new one does (almost at the same time) are executed in that
        // exact order

        String flowId = getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanInfo.getElanTag());
        FlowEntity flowEntity = new FlowEntity(dpnId);
        flowEntity.setTableId(NwConstants.EXTERNAL_TUNNEL_TABLE);
        flowEntity.setFlowId(flowId);
        elanServiceProvider.getMdsalManager().removeFlow(flowEntity);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, BigInteger dpId, WriteTransaction writeFlowGroupTx) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, elanTag), 5,
                String.format("%s:%d", "ITM Flow Entry ", elanTag), 0, 0,
                ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(elanTag)),
                ElanUtils.getTunnelMatchesForServiceId((int) elanTag),
                getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));

        elanServiceProvider.getMdsalManager().addFlowToTx(dpId, flowEntity, writeFlowGroupTx);
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, BigInteger dpId, WriteTransaction writeFlowGroupTx) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,
                        /* SH flag */false),
                5, elanInfo.getElanInstanceName(), 0, 0,
                ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                getMatchesForElanTag(elanTag, /* SH flag */false),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGID(elanTag)));

        elanServiceProvider.getMdsalManager().addFlowToTx(dpId, flowEntity, writeFlowGroupTx);

        // only if ELAN can connect to external network, perform the following
        if (ElanUtils.isVxlan(elanInfo) || ElanUtils.isVlan(elanInfo) || ElanUtils.isFlat(elanInfo)) {
            Flow flowEntity2 = MDSALUtil.buildFlowNew(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,
                            /* SH flag */true),
                    5, elanInfo.getElanInstanceName(), 0, 0,
                    ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                    getMatchesForElanTag(elanTag, /* SH flag */true),
                    getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));
            elanServiceProvider.getMdsalManager().addFlowToTx(dpId, flowEntity2, writeFlowGroupTx);
        }

    }

    private void removeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo, WriteTransaction deleteFlowGroupTx) {
        // Flow flow = getUnknownDmacFlowEntity(dpId, elanInfo);
        // mdsalManager.removeFlow(dpId, flow);
        Flow flow = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                elanInfo.getElanTag(), /* SH flag */ false))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE).build();
        elanServiceProvider.getMdsalManager().removeFlowToTx(dpId, flow, deleteFlowGroupTx);

        if (ElanUtils.isVxlan(elanInfo)) {
            Flow flow2 = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    elanInfo.getElanTag(), /* SH flag */ true))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE)
                    .build();
            elanServiceProvider.getMdsalManager().removeFlowToTx(dpId, flow2, deleteFlowGroupTx);
        }

    }

    private void removeDefaultTermFlow(BigInteger dpId, long elanTag) {
        ElanUtils.removeTerminatingServiceAction(dpId, (int) elanTag);
    }

    private void bindService(ElanInstance elanInfo, String interfaceName, WriteTransaction tx) {
        // interfaceManager.bindService(interfaceName,
        // ElanUtils.getServiceInfo(elanInfo.getElanInstanceName(),
        // elanInfo.getElanTag(), interfaceName));

        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions
                .add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanUtils.getElanMetadataLabel(elanInfo.getElanTag()),
                        MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.ELAN_SMAC_TABLE, ++instructionKey));
        BoundServices serviceInfo = ElanUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", elanInfo.getElanInstanceName(), interfaceName),
                NwConstants.ELAN_SERVICE_INDEX, priority, NwConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
        tx.put(LogicalDatastoreType.CONFIGURATION,
                ElanUtils.buildServiceId(interfaceName, NwConstants.ELAN_SERVICE_INDEX), serviceInfo, true);
    }

    private void unbindService(ElanInstance elanInfo, String interfaceName, WriteTransaction tx) {
        tx.delete(LogicalDatastoreType.CONFIGURATION,
                ElanUtils.buildServiceId(interfaceName, NwConstants.ELAN_SERVICE_INDEX));
    }

    private String getFlowRef(long tableId, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    private String getUnknownDmacFlowRef(long tableId, long elanTag, boolean shFlag) {
        return new StringBuffer().append(tableId).append(elanTag).append(shFlag).toString();
    }

    private List<Action> getInterfacePortActions(InterfaceInfo interfaceInfo) {
        List<Action> listAction = new ArrayList<Action>();
        int actionKey = 0;
        listAction.add((new ActionInfo(ActionType.set_field_tunnel_id,
                new BigInteger[] { BigInteger.valueOf(interfaceInfo.getInterfaceTag()) }, actionKey)).buildAction());
        actionKey++;
        listAction.add((new ActionInfo(ActionType.nx_resubmit,
                new String[] { String.valueOf(NwConstants.ELAN_FILTER_EQUALS_TABLE) }, actionKey)).buildAction());
        return listAction;
    }

    private DpnInterfaces updateElanDpnInterfacesList(String elanInstanceName, BigInteger dpId,
            List<String> interfaceNames, WriteTransaction tx) {
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .setKey(new DpnInterfacesKey(dpId)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId), dpnInterface, true);
        return dpnInterface;
    }

    /**
     * Delete elan dpn interface from operational DS.
     *
     * @param elanInstanceName
     *            the elan instance name
     * @param dpId
     *            the dp id
     */
    private void deleteElanDpnInterface(String elanInstanceName, BigInteger dpId, WriteTransaction tx) {
        tx.delete(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId));
    }

    private DpnInterfaces createElanInterfacesList(String elanInstanceName, String interfaceName, BigInteger dpId,
            WriteTransaction tx) {
        List<String> interfaceNames = new ArrayList<String>();
        interfaceNames.add(interfaceName);
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .setKey(new DpnInterfacesKey(dpId)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId), dpnInterface, true);
        return dpnInterface;
    }

    private void createElanInterfaceTablesList(String interfaceName, WriteTransaction tx) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceMacTables = ElanUtils
                .getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> interfaceMacTables = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanInterfaceMacTables);
        // Adding new Elan Interface Port to the operational DataStore without
        // Static-Mac Entries..
        if (!interfaceMacTables.isPresent()) {
            ElanInterfaceMac elanInterfaceMacTable = new ElanInterfaceMacBuilder().setElanInterface(interfaceName)
                    .setKey(new ElanInterfaceMacKey(interfaceName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName), elanInterfaceMacTable,
                    true);
        }
    }

    private void createElanStateList(String elanInstanceName, String interfaceName, WriteTransaction tx) {
        InstanceIdentifier<Elan> elanInstance = ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInterfaceLists = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanInstance);
        // Adding new Elan Interface Port to the operational DataStore without
        // Static-Mac Entries..
        if (elanInterfaceLists.isPresent()) {
            List<String> interfaceLists = elanInterfaceLists.get().getElanInterfaces();
            if (interfaceLists == null) {
                interfaceLists = new ArrayList<>();
            }
            interfaceLists.add(interfaceName);
            Elan elanState = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(interfaceLists)
                    .setKey(new ElanKey(elanInstanceName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName),
                    elanState, true);
        }
    }

    private boolean isOperational(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            return false;
        }
        return interfaceInfo.getAdminState() == InterfaceInfo.InterfaceAdminState.ENABLED;
    }

    public void handleInternalTunnelStateEvent(BigInteger srcDpId, BigInteger dstDpId) {
        ElanDpnInterfaces dpnInterfaceLists = ElanUtils.getElanDpnInterfacesList();
        if (dpnInterfaceLists == null) {
            return;
        }
        List<ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists.getElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf) {
            int cnt = 0;
            String elanName = elanDpns.getElanInstanceName();
            List<DpnInterfaces> dpnInterfaces = elanDpns.getDpnInterfaces();
            if (dpnInterfaces == null) {
                continue;
            }
            for (DpnInterfaces dpnIf : dpnInterfaces) {
                if (dpnIf.getDpId().equals(srcDpId) || dpnIf.getDpId().equals(dstDpId)) {
                    cnt++;
                }
            }
            if (cnt == 2) {
                logger.debug("Elan instance:{} is present b/w srcDpn:{} and dstDpn:{}", elanName, srcDpId, dstDpId);
                ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanName);
                // update Remote BC Group
                setupElanBroadcastGroups(elanInfo, srcDpId);

                DpnInterfaces dpnInterface = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dstDpId);
                Set<String> interfaceLists = new HashSet<>();
                interfaceLists.addAll(dpnInterface.getInterfaces());
                for (String ifName : interfaceLists) {
                    InterfaceInfo interfaceInfo = elanServiceProvider.getInterfaceManager().getInterfaceInfo(ifName);
                    if (isOperational(interfaceInfo)) {
                        installDMacAddressTables(elanInfo, interfaceInfo, srcDpId);
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
                boolean otherEndPointInterfaceOperational = ElanUtils.isInterfaceOperational(
                        otherEndPointExtTunnel.getTunnelInterfaceName(), elanServiceProvider.getBroker());
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

    private List<MatchInfo> getMatchesForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { MetaDataUtil.getLportTagMetaData(LportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG }));
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] { BigInteger.valueOf(LportTag) }));
        return mkMatches;
    }

    private List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] { BigInteger.valueOf(LportTag) }));
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
        List<Bucket> listBucketInfo = new ArrayList<Bucket>();
        ConcurrentMap<String, L2GatewayDevice> map = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInfo.getElanInstanceName());
        for (L2GatewayDevice device : map.values()) {
            String interfaceName = ElanL2GatewayUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    device.getHwvtepNodeId());
            if (interfaceName == null) {
                continue;
            }
            List<Action> listActionInfo = ElanUtils.buildTunnelItmEgressActions(interfaceName,
                    elanInfo.getSegmentationId());
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

    public ElanServiceProvider getElanServiceProvider() {
        return elanServiceProvider;
    }

    public void setElanServiceProvider(ElanServiceProvider elanServiceProvider) {
        this.elanServiceProvider = elanServiceProvider;
    }

    @Override
    protected ElanInterfaceManager getDataTreeChangeListener() {
        return this;
    }

}
