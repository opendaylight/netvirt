/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.elan.utils.ElanConstants;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo.InterfaceType;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;

import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


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
            new ConcurrentHashMap<String, ConcurrentLinkedQueue<ElanInterface>> ();

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
        removeElanInterface(elanInfo, interfaceName);
    }

    public void removeElanService(ElanInterface del, InterfaceInfo interfaceInfo) {
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(del.getElanInstanceName());
        String interfaceName = del.getName();
        removeElanInterface(elanInstance, interfaceInfo);
        unbindService(elanInstance, interfaceName);
    }

    public void removeElanInterface(ElanInstance elanInfo, String interfaceName) {
        String elanName = elanInfo.getElanInstanceName();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (interfaceInfo == null) {
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
                ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceConfigurationDataPath(elanName));
            } else {
                Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName).setKey(new ElanKey(elanName)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName), updateElanState);
            }
            return;
        }
        removeElanInterface(elanInfo, interfaceInfo);
        unbindService(elanInfo, interfaceName);
    }

    private void removeElanInterface(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {

        BigInteger dpId = interfaceInfo.getDpId();
        String elanName = elanInfo.getElanInstanceName();
        String interfaceName = interfaceInfo.getInterfaceName();
        Elan elanState = ElanUtils.getElanByName(elanName);
        logger.debug("Removing the Interface:{} from elan:{}", interfaceName, elanName);
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> existingElanInterface = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        if(existingElanInterface.isPresent()) {
            List<MacEntry> macEntries = existingElanInterface.get().getMacEntry();
            if(macEntries != null && !macEntries.isEmpty()) {
                for (MacEntry macEntry : macEntries) {
                    logger.debug("removing the  mac-entry:{} present on elanInterface:{}", macEntry.getMacAddress().getValue(), interfaceName);
                    elanForwardingEntriesHandler.deleteElanInterfaceForwardingEntries(elanInfo, interfaceInfo, macEntry);
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
                        removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId, interfaceName);
                        break;
                    }
                }
            }
        } else {
            removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId, interfaceName);
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

    private void removeElanDpnInterfaceFromOperationalDataStore(String elanName, BigInteger dpId, String interfaceName) {
        DpnInterfaces dpnInterfaces =  ElanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
        if(dpnInterfaces != null) {
            List<String> interfaceLists = dpnInterfaces.getInterfaces();
            interfaceLists.remove(interfaceName);
            updateElanDpnInterfacesList(elanName, dpId, interfaceLists);
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
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);

        if (elanInstance == null) {
            elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setDescription(elanInterfaceAdded.getDescription()).build();
            //Add the ElanInstance in the Configuration data-store
            ElanUtils.UpdateOperationalDataStore(broker, idManager, elanInstance);
            elanInstance = ElanUtils.getElanInstanceByName(elanInstanceName);
        }


        Long elanTag = elanInstance.getElanTag();
        // If elan tag is not updated, then put the elan interface into unprocessed entry map and entry. Let entries
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
        addElanInterface(elanInterfaceAdded, interfaceInfo, elanInstance);
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

    void addElanInterface(ElanInterface elanInterface, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
        String interfaceName = elanInterface.getName();
        String elanInstanceName = elanInterface.getElanInstanceName();
        List<PhysAddress> staticMacAddresses = elanInterface.getStaticMacEntries();
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        BigInteger dpId = null;
        if(elanInfo == null) {
            ElanUtils.UpdateOperationalDataStore(broker, idManager, elanInstance);
        }
        if(interfaceInfo != null) {
            dpId = interfaceInfo.getDpId();
        }
        if(dpId != null && !dpId.equals(ElanConstants.INVALID_DPN)) {
            InstanceIdentifier<DpnInterfaces> elanDpnInterfaces = ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
            Optional<DpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaces);
            if (!existingElanDpnInterfaces.isPresent()) {
                createElanInterfacesList(elanInstanceName, interfaceName, dpId);
            } else {
                List<String> elanInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                elanInterfaces.add(interfaceName);
                updateElanDpnInterfacesList(elanInstanceName, dpId, elanInterfaces);
            }
        }

        // add code to install Local/Remote BC group, unknow DMAC entry, terminating service table flow entry
        // call bindservice of interfacemanager to create ingress table flow enty.
        //Add interface to the ElanInterfaceForwardingEntires Container
        createElanInterfaceTablesList(interfaceName);
        createElanStateList(elanInstanceName, interfaceName);
        if(interfaceInfo != null) {
            installFlowsAndGroups(elanInstance, interfaceInfo);
        }
        // add the static mac addresses
        if(staticMacAddresses != null) {
            for (PhysAddress physAddress : staticMacAddresses) {
                InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName, physAddress);
                Optional<MacEntry> existingMacEntry = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macId);
                if (existingMacEntry.isPresent()) {
                    elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(elanInstanceName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get());
                } else {
                    elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(elanInstance, interfaceName, physAddress);
                }
                if(interfaceInfo != null && isOperational(interfaceInfo)) {
                    logger.debug("Installing Static Mac-Entry on the Elan Interface:{} with MacAddress:{}", interfaceInfo, physAddress.getValue());
                    ElanUtils.setupMacFlows(elanInstance, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT, physAddress.getValue());
                }
            }
        }
    }

    private Map<BigInteger, List<String>> readFePortsDbForElan(String elanName) {
        ElanDpnInterfacesList elanDpnInterfacesList = ElanUtils.getElanDpnInterfacesList(elanName);
        HashMap<BigInteger, List<String>> fePortsDb = Maps.newHashMap();
        if (elanDpnInterfacesList == null) {
            return fePortsDb;
        }
        List<DpnInterfaces> dpnInterfaces = elanDpnInterfacesList.getDpnInterfaces();
        if (dpnInterfaces == null) {
            return fePortsDb;
        }
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            fePortsDb.put(dpnInterface.getDpId(), dpnInterface.getInterfaces());
        }
        return fePortsDb;
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
            setupElanBroadcastGroups(elanInfo, interfaceInfo);

            setupLocalBroadcastGroups(elanInfo, interfaceInfo);
            //Terminating Service , UnknownDMAC Table.
            setupTerminateServiceTable(elanInfo, interfaceInfo);
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
                getInstructionsDrop());

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntry);
    }

    public void removeFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, ifTag),
                9, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getTunnelIdMatchForFilterEqualsLPortTag(ifTag), ElanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.removeFlow(interfaceInfo.getDpId(), flow);

        Flow flowEntity = MDSALUtil.buildFlowNew(ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, 1000+ifTag),
                10, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getMatchesForFilterEqualsLPortTag(ifTag),
                getInstructionsDrop());

        mdsalManager.removeFlow(interfaceInfo.getDpId(), flowEntity);
    }

    private List<Bucket> getRemoteBCGroupBucketInfos(ElanInstance elanInfo,
                                                     int bucketKeyStart, InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        int bucketId = bucketKeyStart;
        List<Bucket> listBuckets = new ArrayList<Bucket>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
               if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                   try {
                       List<Action> listAction = ElanUtils.getItmEgressAction(dpnId, dpnInterface.getDpId(), (int) elanTag);
                       listBuckets.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                       bucketId++;
                   } catch (Exception ex) {
                       logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnId, dpnInterface.getDpId() );
                   }
               }
            }
        }
        return listBuckets;
    }

    private List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo,
                                                         InterfaceInfo interfaceInfo, int bucketId) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        List<Bucket> listBucketInfo = new ArrayList<Bucket>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
                if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = ElanUtils.getItmEgressAction(dpnId, dpnInterface.getDpId(), (int) elanTag);
                        listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, 0, bucketId, 0xffffffffL, 0xffffffffL));
                        bucketId++;
                    } catch (Exception ex) {
                        logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnId, dpnInterface.getDpId() );
                    }
                }
            }
        }
        return listBucketInfo;
    }

    private void setElanBCGrouponOtherDpns(ElanInstance elanInfo,
                                           InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        List<Bucket> listBucket = new ArrayList<Bucket>();
        int bucketId = 0;
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
              List<Bucket> remoteListBucketInfo = new ArrayList<Bucket>();
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
                                List<Action> remoteListActionInfo = ElanUtils.getItmEgressAction(dpnInterface.getDpId(), otherFes.getDpId(), (int) elanTag);
                                remoteListBucketInfo.add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,MDSALUtil.WATCH_GROUP));
                                bucketId++;
                            } catch (Exception ex) {
                                logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnInterface.getDpId(), otherFes.getDpId() );
                                return;
                            }
                        }
                    }
                    if(remoteListBucketInfo.size() == 0) {
                        logger.debug( "No ITM is present on Dpn - {} " ,dpnInterface.getDpId());
                        continue;
                    }
                    Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(remoteListBucketInfo));
                    mdsalManager.syncInstallGroup(dpnInterface.getDpId(), group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
                }
            }
        }
    }

    private void updateRemoteBCGrouponDpnTunnelEvent(ElanInstance elanInfo,
                                               InterfaceInfo interfaceInfo, BigInteger dstDpId) {
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        List<DpnInterfaces> elanDpns = ElanUtils.getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            for(DpnInterfaces dpnInterface : elanDpns) {
                int bucketId = 0;
                List<Bucket> remoteListBucket = new ArrayList<Bucket>();
                if(ElanUtils.isDpnPresent(dstDpId) && dpnInterface.getDpId().equals(dstDpId) && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> remoteListActionInfo = ElanUtils.getItmEgressAction(interfaceInfo.getDpId(), dstDpId, (int) elanTag);
                        remoteListBucket.add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                        bucketId++;
                    } catch (Exception ex) {
                        logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnInterface.getDpId(), dstDpId);
                        return;
                    }
                    List<Action> remoteListActionInfo = new ArrayList<Action>();
                    remoteListActionInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(ElanUtils.getElanLocalBCGID(elanTag))}).buildAction());
                    remoteListBucket.add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                    Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(remoteListBucket));
                    mdsalManager.syncInstallGroup(interfaceInfo.getDpId(), group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
                    break;
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
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));
        return mkMatches;
    }

    private List<Instruction> getInstructionsForOutGroup(
            long groupId) {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List <Action> actions = new ArrayList <Action> ();
        actions.add(new ActionInfo(ActionType.group, new String[]{Long.toString(groupId)}).buildAction());
        mkInstructions.add(ElanUtils.getWriteActionInstruction(actions));
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
                ElanUtils.setupMacFlows(elanInfo, interfaceInfo, macEntry.isIsStaticAddress() ? ElanConstants.STATIC_MAC_TIMEOUT : elanInfo.getMacTimeout(), physAddress.getValue());
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
                            ElanUtils.setupRemoteDmacFlow(currentDpn, remoteInterface.getDpId(), remoteInterface.getInterfaceTag(), elanInfo.getElanTag(), physAddress.getValue(), elanInfo.getElanInstanceName());
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

    public void setupElanBroadcastGroups(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        List<Bucket> listBucket = new ArrayList<Bucket>();
        int bucketId = 0;
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGID(elanInfo.getElanTag());

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
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, interfaceInfo, bucketId);
        listBucket.addAll(listBucketInfoRemote);

        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, MDSALUtil.buildBucketLists(listBucket));
        logger.trace("installing the localBroadCast Group:{}", group);
        mdsalManager.syncInstallGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void setupLocalBroadcastGroups(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        List<Bucket> listBucket = new ArrayList<Bucket>();
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

    public void setupTerminateServiceTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE, getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, elanTag),
                5, String.format("%s:%d","ITM Flow Entry ",elanTag), 0,  0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(elanTag)), ElanUtils.getTunnelMatchesForServiceId((int)elanTag),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGID(elanTag)));

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntity);
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        Flow flowEntity = MDSALUtil.buildFlowNew(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)), getMatchesForElanTag(elanTag),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGID(elanTag)));

        mdsalManager.installFlow(interfaceInfo.getDpId(), flowEntity);
    }

    private void removeStaticELanFlows(final ElanInstance elanInfo, final InterfaceInfo interfaceInfo) {
        BigInteger dpId = interfaceInfo.getDpId();
        long elanTag = elanInfo.getElanTag();
        /*
         * If there are not elan ports, remove the unknown smac and default dmac
         * flows
         */
        DpnInterfaces dpnInterfaces = ElanUtils.getElanInterfaceInfoByElanDpn(elanInfo.getElanInstanceName(), dpId);
        if(dpnInterfaces == null) {
            return;
        }
        List <String> elanInterfaces = dpnInterfaces.getInterfaces();
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {

            logger.debug("deleting the elan: {} present on dpId: {}", elanInfo.getElanInstanceName(), dpId);
            removeDefaultTermFlow(dpId, elanInfo.getElanTag());
            removeUnknownDmacFlow(dpId, elanInfo);
            removeElanBroadcastGroup(elanInfo, interfaceInfo);
            removeLocalBroadcastGroup(elanInfo, interfaceInfo);
            removeFilterEqualsTable(elanInfo, interfaceInfo);
        } else {
            setupElanBroadcastGroups(elanInfo, interfaceInfo);
            setupLocalBroadcastGroups(elanInfo, interfaceInfo);
            removeFilterEqualsTable(elanInfo, interfaceInfo);
        }
    }

    private void removeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo) {
        Flow flow = getUnknownDmacFlowEntity(dpId, elanInfo);
        mdsalManager.removeFlow(dpId, flow);
    }

    private void removeDefaultTermFlow(BigInteger dpId, long elanTag) {
        ElanUtils.removeTerminatingServiceAction(dpId, (int) elanTag);
    }

    private void bindService(ElanInstance elanInfo, String interfaceName) {
       // interfaceManager.bindService(interfaceName, ElanUtils.getServiceInfo(elanInfo.getElanInstanceName(), elanInfo.getElanTag(), interfaceName));

        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<Instruction>();
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

    private Flow getUnknownDmacFlowEntity(BigInteger dpId, ElanInstance elanInfo) {
        long elanTag = elanInfo.getElanTag();
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));

        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List <Action> actionsInfos = new ArrayList <Action> ();
        actionsInfos.add(new ActionInfo(ActionType.group, new String[]{Long.toString(ElanUtils.getElanRemoteBCGID(elanTag))}, 0).buildAction());
        mkInstructions.add(ElanUtils.getWriteActionInstruction(actionsInfos));

        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                mkMatches, mkInstructions);
        return flow;
    }

    private String getFlowRef(long tableId, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    private List<Action> getInterfacePortActions(InterfaceInfo interfaceInfo) {
        List<Action> listAction = new ArrayList<Action>();
        int actionKey = 0;
        listAction.add((new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {BigInteger.valueOf(interfaceInfo.getInterfaceTag())}, actionKey)).buildAction());
        actionKey++;
        listAction.add((new ActionInfo(ActionType.nx_resubmit, new BigInteger[] {BigInteger.valueOf(55)}, actionKey)).buildAction());
        return listAction;
    }

    private void updateElanDpnInterfacesList(String elanInstanceName, BigInteger dpId, List<String> interfaceNames) {
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                .setInterfaces(interfaceNames).setKey(new DpnInterfacesKey(dpId)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId),
                dpnInterface);
    }

    private List<String> createElanInterfacesList(String elanInstanceName, String interfaceName, BigInteger dpId) {
        List<String> interfaceNames = new ArrayList<String>();
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
        return ((interfaceInfo.getOpState() == InterfaceInfo.InterfaceOpState.UP) && (interfaceInfo.getAdminState() == InterfaceInfo.InterfaceAdminState.ENABLED));
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

    public void handleTunnelStateEvent(BigInteger srcDpId, BigInteger dstDpId) {
        ElanDpnInterfaces dpnInterfaceLists =  ElanUtils.getElanDpnInterfacesList();
        Set<String> elanInstancesMap = new HashSet<>();
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
                DpnInterfaces dpnInterface = ElanUtils.getElanInterfaceInfoByElanDpn(elanName, srcDpId);
                Set<String> interfaceLists = new HashSet<>();
                ElanInstance elanInfo = ElanUtils.getElanInstanceByName(elanName);
                interfaceLists.addAll(dpnInterface.getInterfaces());
                for(String ifName : interfaceLists) {
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(ifName);
                    if (isOperational(interfaceInfo)) {
                        if (interfaceInfo.getDpId().equals(srcDpId) && !elanInstancesMap.contains(elanDpns.getElanInstanceName())) {
                            elanInstancesMap.add(elanDpns.getElanInstanceName());
                            elanInterfaceManager.updateRemoteBCGrouponDpnTunnelEvent(elanInfo, interfaceInfo, dstDpId);
                        }
                        elanInterfaceManager.installDMacAddressTables(elanInfo, interfaceInfo, dstDpId);
                    }
                }
            }

        }
    }

    public void handleInterfaceUpated(InterfaceInfo interfaceInfo, ElanInstance elanInstance, boolean isStateUp) {
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
        }
    }

    private List<MatchInfo> getMatchesForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getLportTagMetaData(LportTag),
                MetaDataUtil.METADATA_MASK_LPORT_TAG }));
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(LportTag)}));
        return mkMatches;
    }


    private List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                BigInteger.valueOf(LportTag)}));
        return mkMatches;


    }

    private List<Instruction> getInstructionsDrop() {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List <Action> actionsInfos = new ArrayList <Action> ();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[]{}).buildAction());
        mkInstructions.add(ElanUtils.getWriteActionInstruction(actionsInfos));
        return mkInstructions;
    }

}
