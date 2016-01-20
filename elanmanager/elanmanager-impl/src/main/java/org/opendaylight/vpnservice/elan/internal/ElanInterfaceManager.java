/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
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
import org.opendaylight.vpnservice.itm.api.IITMProvider;

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
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceOutput;
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
    private IITMProvider itmManager;
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

    public void setIITMManager(IITMProvider itmManager) {
        this.itmManager = itmManager;
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

    public void removeElanService(ElanInterface del, int vlanId) {
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(del.getElanInstanceName());
        String interfaceName = del.getName();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName, InterfaceType.VLAN_INTERFACE);
        removeElanInterface(elanInstance, interfaceInfo);
        unbindService(elanInstance, interfaceName, vlanId);
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

        ElanUtils.delete(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        List<String> elanInterfaces = elanState.getElanInterfaces();
        elanInterfaces.remove(interfaceName);
        removeStaticELanFlows(elanInfo, interfaceInfo);
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
            setupLocalBroadcastGroups(elanInfo, interfaceInfo);

            //Terminating Service , UnknownDMAC Table.
            setupTerminateServiceTable(elanInfo, interfaceInfo);
            setupUnknownDMacTable(elanInfo, interfaceInfo);
            setupFilterEqualsTable(elanInfo, interfaceInfo);
            // bind the Elan service to the Interface
            bindService(elanInfo, interfaceInfo.getInterfaceName());

            //update the remote-DPNs remoteBC group entry with Tunnels
            setRemoteBCGrouponOtherDpns(elanInfo, interfaceInfo);
        }
    }

    public void setupFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        int ifTag = interfaceInfo.getInterfaceTag();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(interfaceInfo.getDpId(), ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, ifTag),
                9, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.installFlow(flowEntity);

        FlowEntity flowEntity1 = MDSALUtil.buildFlowEntity(interfaceInfo.getDpId(), ElanConstants.ELAN_FILTER_EQUALS_TABLE, getFlowRef(ElanConstants.ELAN_FILTER_EQUALS_TABLE, 1000+ifTag),
                10, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)), getMatchesForFilterEqualsLPortTag(ifTag),
                getInstructionsDrop());

        mdsalManager.installFlow(flowEntity1);
    }

    private List<BucketInfo> getRemoteBCGroupBucketInfos(ElanInstance elanInfo,
            InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
               if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && dpnInterface.getDpId() != dpnId && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                   try {
                       //FIXME [ELANBE] Removing ITM API for now, will need this for multi dpn.
                       //List<ActionInfo> listActionInfo = itmManager.ITMIngressGetActions(dpnId, dpnInterface.getDpId(), (int) elanTag);
                       //listBucketInfo.add(new BucketInfo(listActionInfo));
                   } catch (Exception ex) {
                       logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnId, dpnInterface.getDpId() );
                   }
               }
            }
        }
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        listActionInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(ElanUtils.getElanLocalBCGID(elanInfo.getElanTag()))}));
        listBucketInfo.add(new BucketInfo(listActionInfo));
        return listBucketInfo;
    }

    public ActionInfo getTunnelIdActionInfo(int interfaceTag) {
         return new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[]{BigInteger.valueOf(interfaceTag)});
    }

    private void setRemoteBCGrouponOtherDpns(ElanInstance elanInfo,
                                                         InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGID(elanTag);
        ElanDpnInterfacesList elanDpns = ElanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if(elanDpns != null) {
            List<DpnInterfaces> dpnInterfaceses = elanDpns.getDpnInterfaces();
            for(DpnInterfaces dpnInterface : dpnInterfaceses) {
              List<BucketInfo> remoteListBucketInfo = new ArrayList<BucketInfo>();
                if(ElanUtils.isDpnPresent(dpnInterface.getDpId()) && !dpnInterface.getDpId().equals(dpnId) && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    for(DpnInterfaces otherFes : dpnInterfaceses) {
                        if (ElanUtils.isDpnPresent(otherFes.getDpId()) && otherFes.getDpId() != dpnInterface.getDpId()
                                && otherFes.getInterfaces() != null && ! otherFes.getInterfaces().isEmpty()) {
                            try {
                                //FIXME [ELANBE] Removing ITM API for now, will need this for multi dpn.
                                //List<ActionInfo> remoteListActionInfo = itmManager.ITMIngressGetActions(dpnInterface.getDpId(), otherFes.getDpId(), (int) elanTag);
                                //remoteListBucketInfo.add(new BucketInfo(remoteListActionInfo));
                            } catch (Exception ex) {
                                logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnInterface.getDpId(), otherFes.getDpId() );
                                return;
                            }
                        }
                    }
                    List<ActionInfo> remoteListActionInfo = new ArrayList<ActionInfo>();
                    remoteListActionInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(ElanUtils.getElanLocalBCGID(elanTag))}));
                    remoteListBucketInfo.add(new BucketInfo(remoteListActionInfo));
                    GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnInterface.getDpId(), groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, remoteListBucketInfo);
                    mdsalManager.installGroup(groupEntity);
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
                List<BucketInfo> remoteListBucketInfo = new ArrayList<BucketInfo>();
                if(ElanUtils.isDpnPresent(dstDpId) && dpnInterface.getDpId().equals(dstDpId) && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        //FIXME [ELANBE] Removing ITM API for now, will need this for multi dpn.
                        //List<ActionInfo> remoteListActionInfo = itmManager.ITMIngressGetActions(interfaceInfo.getDpId(), dstDpId, (int) elanTag);
                        //remoteListBucketInfo.add(new BucketInfo(remoteListActionInfo));
                    } catch (Exception ex) {
                        logger.error( "Logical Group Interface not found between source Dpn - {}, destination Dpn - {} " ,dpnInterface.getDpId(), dstDpId);
                        return;
                    }
                    List<ActionInfo> remoteListActionInfo = new ArrayList<ActionInfo>();
                    remoteListActionInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(ElanUtils.getElanLocalBCGID(elanTag))}));
                    remoteListBucketInfo.add(new BucketInfo(remoteListActionInfo));
                    GroupEntity groupEntity = MDSALUtil.buildGroupEntity(interfaceInfo.getDpId(), groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, remoteListBucketInfo);
                    mdsalManager.installGroup(groupEntity);
                    break;
                }
            }
        }
    }


    /**
     * Returns the bucket info with the given interface as the only bucket.
     */
    private List<BucketInfo> getLocalBCGroupBucketInfo(InterfaceInfo interfaceInfo) {
        return Lists.newArrayList(new BucketInfo(getInterfacePortActionInfos(interfaceInfo)));
    }

    private List<MatchInfo> getMatchesForElanTag(Long elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));
        return mkMatches;
    }

    private List<InstructionInfo> getInstructionsForOutGroup(
            long groupId) {
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
        actionsInfos.add(new ActionInfo(ActionType.group, new String[]{Long.toString(groupId)}));
        mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
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

    public void setupLocalBroadcastGroups(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
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

            listBucketInfo.add(new BucketInfo(getInterfacePortActionInfos(ifInfo)));
        }
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, listBucketInfo);
        logger.trace("installing the localBroadCast GroupEntity:{}", groupEntity);
        mdsalManager.syncInstallGroup(groupEntity, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    public void removeLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGID(elanInfo.getElanTag());

        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, getLocalBCGroupBucketInfo(interfaceInfo));
        logger.trace("deleted the localBroadCast GroupEntity:{}", groupEntity);
        mdsalManager.syncRemoveGroup(groupEntity);
    }

    public void removeRemoteBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        List<BucketInfo> listBucketInfo = getRemoteBCGroupBucketInfos(elanInfo, interfaceInfo);
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGID(elanInfo.getElanTag());
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll, listBucketInfo);
        logger.trace("deleting the remoteBroadCast GroupEntity:{}", groupEntity);
        mdsalManager.syncRemoveGroup(groupEntity);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        //FIXME [ELANBE] Removing ITM API for now, will need this for multi dpn.
//        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(interfaceInfo.getDpId(), ITMConstants.TERMINATING_SERVICE_TABLE, getFlowRef(ITMConstants.TERMINATING_SERVICE_TABLE, elanTag),
//                5, elanInfo.getElanInstanceName(), 0,  0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(elanTag)), itmManager.getTunnelMatchesForServiceId(elanTag),
//                getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));
//
//        mdsalManager.installFlow(flowEntity);
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        long elanTag = elanInfo.getElanTag();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(interfaceInfo.getDpId(), ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)), getMatchesForElanTag(elanTag),
                getInstructionsForOutGroup(ElanUtils.getElanLocalBCGID(elanTag)));

        mdsalManager.installFlow(flowEntity);
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
            removeRemoteBroadcastGroup(elanInfo, interfaceInfo);
            removeLocalBroadcastGroup(elanInfo, interfaceInfo);
        } else {
            setupLocalBroadcastGroups(elanInfo, interfaceInfo);
        }
    }

    private void removeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo) {
        FlowEntity flowEntity = getUnknownDmacFlowEntity(dpId, elanInfo);
        mdsalManager.syncRemoveFlow(flowEntity, ElanConstants.DELAY_TIME_IN_MILLISECOND);
    }

    private void removeDefaultTermFlow(BigInteger dpId, long elanTag) {
        //FIXME [ELANBE] Removing ITM API for now, will need this for multi dpn.
        //itmManager.removeTerminatingServiceAction(dpId, (int) elanTag);
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

    private FlowEntity getUnknownDmacFlowEntity(BigInteger dpId, ElanInstance elanInfo) {
        long elanTag = elanInfo.getElanTag();
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));

        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
        actionsInfos.add(new ActionInfo(ActionType.group, new String[]{Long.toString(ElanUtils.getElanRemoteBCGID(elanTag))}));
        mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, getFlowRef(ElanConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag),
                5, elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                mkMatches, mkInstructions);
        return flowEntity;
    }

    private String getFlowRef(long tableId, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    private List<ActionInfo> getInterfacePortActionInfos(InterfaceInfo interfaceInfo) {
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        listActionInfo.add(getTunnelIdActionInfo(interfaceInfo.getInterfaceTag()));
        listActionInfo.add(new ActionInfo(ActionType.nx_resubmit, new String[]{}));
        return listActionInfo;
    }

    private void updateElanDpnInterfacesList(String elanInstanceName, BigInteger dpId, List<String> interfaceNames) {
        if(!interfaceNames.isEmpty()) {
            DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId)
                    .setInterfaces(interfaceNames).setKey(new DpnInterfacesKey(dpId)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId),
                    dpnInterface);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId));
        }
        
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

    private List<InstructionInfo> getInstructionsInPortForOutGroup(
            String ifName) {
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
        actionsInfos.addAll(ElanUtils.getEgressActionsForInterface(ifName));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        return mkInstructions;
    }



    private List<InstructionInfo> getInstructionsDrop() {
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[]{}));
        mkInstructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
        return mkInstructions;
    }

}
