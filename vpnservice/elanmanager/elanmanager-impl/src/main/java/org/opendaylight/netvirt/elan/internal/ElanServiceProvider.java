/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.elan.utils.ElanForwardingEntriesHandler;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.exceptions.MacNotFoundException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.internal.ElanL2GatewayProvider;
import org.opendaylight.netvirt.elan.statisitcs.ElanStatisticsImpl;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.genius.interfacemanager.exceptions.InterfaceAlreadyExistsException;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.api.IITMProvider;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.statistics.rev150824.ElanStatisticsService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;


public class ElanServiceProvider implements BindingAwareProvider, IElanService, AutoCloseable {

    private IdManagerService idManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private OdlInterfaceRpcService interfaceManagerRpcService;
    private ElanInstanceManager elanInstanceManager;
    private ElanForwardingEntriesHandler elanForwardingEntriesHandler;
    private ElanBridgeManager bridgeMgr;

    public IdManagerService getIdManager() {
        return idManager;
    }

    public ElanForwardingEntriesHandler getElanForwardingEntriesHandler() {
        return elanForwardingEntriesHandler;
    }

    public ElanPacketInHandler getElanPacketInHandler() {
        return elanPacketInHandler;
    }

    public ElanSmacFlowEventListener getElanSmacFlowEventListener() {
        return elanSmacFlowEventListener;
    }

    public ElanInterfaceStateChangeListener getElanInterfaceStateChangeListener() {
        return elanInterfaceStateChangeListener;
    }

    public ElanInterfaceStateClusteredListener getInfStateChangeClusteredListener() {
        return infStateChangeClusteredListener;
    }

    public ElanDpnInterfaceClusteredListener getElanDpnInterfaceClusteredListener() {
        return elanDpnInterfaceClusteredListener;
    }

    public ElanNodeListener getElanNodeListener() {
        return elanNodeListener;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public ElanL2GatewayProvider getElanL2GatewayProvider() {
        return elanL2GatewayProvider;
    }

    public static ElanStatusMonitor getElanstatusmonitor() {
        return elanStatusMonitor;
    }

    public ElanItmEventListener getElanItmEventListener() {
        return elanItmEventListener;
    }

    public static Logger getLogger() {
        return logger;
    }

    private ElanInterfaceManager elanInterfaceManager;
    private ElanPacketInHandler elanPacketInHandler;
    private ElanSmacFlowEventListener elanSmacFlowEventListener;
    private ElanInterfaceStateChangeListener elanInterfaceStateChangeListener;
    private ElanInterfaceStateClusteredListener infStateChangeClusteredListener;
    private ElanDpnInterfaceClusteredListener elanDpnInterfaceClusteredListener;
    private ElanNodeListener elanNodeListener;
    private NotificationService notificationService;
    private RpcProviderRegistry rpcProviderRegistry;
    private IITMProvider itmManager;
    private ItmRpcService itmRpcService;
    private DataBroker broker;
    private ElanL2GatewayProvider elanL2GatewayProvider;
    private ElanStatisticsService interfaceStatsService;
    private EntityOwnershipService entityOwnershipService;
    private static final ElanStatusMonitor elanStatusMonitor = ElanStatusMonitor.getInstance();
    static DataStoreJobCoordinator dataStoreJobCoordinator;
    private ElanOvsdbNodeListener elanOvsdbNodeListener;

    private boolean generateIntBridgeMac = true;

    public static void setDataStoreJobCoordinator(DataStoreJobCoordinator ds) {
        dataStoreJobCoordinator = ds;
    }

    public void setBroker(DataBroker broker) {
        this.broker = broker;
    }

    public static DataStoreJobCoordinator getDataStoreJobCoordinator() {
        if (dataStoreJobCoordinator == null) {
            dataStoreJobCoordinator = DataStoreJobCoordinator.getInstance();
        }
        return dataStoreJobCoordinator;
    }


    public ElanServiceProvider(RpcProviderRegistry rpcRegistry) {
        rpcProviderRegistry = rpcRegistry;
        elanStatusMonitor.registerMbean();
    }

    // private ElanInterfaceStateChangeListener elanInterfaceEventListener;
    private ElanItmEventListener elanItmEventListener;

    private static final Logger logger = LoggerFactory.getLogger(ElanServiceProvider.class);

    @Override
    public void onSessionInitiated(ProviderContext session) {
        elanStatusMonitor.reportStatus("STARTING");
        try {
            createIdPool();
            getDataStoreJobCoordinator();
            broker = session.getSALService(DataBroker.class);

            bridgeMgr = new ElanBridgeManager(broker);
            elanOvsdbNodeListener = new ElanOvsdbNodeListener(broker, generateIntBridgeMac, bridgeMgr, this);
            ElanUtils.setElanServiceProvider(this);
            elanForwardingEntriesHandler = ElanForwardingEntriesHandler.getElanForwardingEntriesHandler(this);
            elanInterfaceManager = ElanInterfaceManager.getElanInterfaceManager(this);
            elanInstanceManager = ElanInstanceManager.getElanInstanceManager(this);
            elanNodeListener  = ElanNodeListener.getElanNodeListener(this);
            elanPacketInHandler = ElanPacketInHandler.getElanPacketInHandler(this);
            elanSmacFlowEventListener = ElanSmacFlowEventListener.getElanSmacFlowEventListener(this);
            // Initialize statistics rpc provider for elan
            interfaceStatsService = ElanStatisticsImpl.getElanStatisticsService(this);
            rpcProviderRegistry.addRpcImplementation(ElanStatisticsService.class, interfaceStatsService);
            elanInterfaceStateChangeListener = ElanInterfaceStateChangeListener.getElanInterfaceStateChangeListener(this);
            infStateChangeClusteredListener = ElanInterfaceStateClusteredListener.getElanInterfaceStateClusteredListener(this);
            elanDpnInterfaceClusteredListener = ElanDpnInterfaceClusteredListener.getElanDpnInterfaceClusteredListener(this);
            ElanClusterUtils.setElanServiceProvider(this);
            this.elanL2GatewayProvider = new ElanL2GatewayProvider(this);
            elanInterfaceManager.registerListener(LogicalDatastoreType.CONFIGURATION,broker);
            elanInstanceManager.registerListener(LogicalDatastoreType.CONFIGURATION,broker);
            notificationService.registerNotificationListener(elanSmacFlowEventListener);
            notificationService.registerNotificationListener(elanPacketInHandler);
            elanStatusMonitor.reportStatus("OPERATIONAL");
        } catch (Exception e) {
            logger.error("Error initializing services", e);
            elanStatusMonitor.reportStatus("ERROR");
        }
    }



    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setEntityOwnershipService(EntityOwnershipService entityOwnershipService) {
        this.entityOwnershipService = entityOwnershipService;
    }

    public IInterfaceManager getInterfaceManager() {
        return this.interfaceManager;
    }

    public IMdsalApiManager getMdsalManager() {
        return mdsalManager;
    }

    public IITMProvider getItmManager() {
        return itmManager;
    }

    public DataBroker getBroker() {
        return broker;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setInterfaceManagerRpcService(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManagerRpcService = interfaceManager;
    }

    public OdlInterfaceRpcService getInterfaceManagerRpcService() {
        return interfaceManagerRpcService;
    }

    public void setItmManager(IITMProvider itmManager) {
        this.itmManager = itmManager;
    }

    public void setItmRpcService(ItmRpcService itmRpcService) {
        this.itmRpcService = itmRpcService;
    }

    public ItmRpcService getItmRpcService() {
        return itmRpcService;
    }

    public ElanInstanceManager getElanInstanceManager() {
        return elanInstanceManager;
    }

    public ElanInterfaceManager getElanInterfaceManager() {
        return elanInterfaceManager;
    }

    public EntityOwnershipService getEntityOwnershipService() {
        return entityOwnershipService;
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(ElanConstants.ELAN_ID_POOL_NAME)
            .setLow(ElanConstants.ELAN_ID_LOW_VALUE).setHigh(ElanConstants.ELAN_ID_HIGH_VALUE).build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if ((result != null) && (result.get().isSuccessful())) {
                logger.debug("ELAN Id Pool is created successfully");
            }
        } catch (Exception e) {
            logger.error("Failed to create ELAN Id pool {}", e);
        }
    }

    @Override
    public boolean createElanInstance(String elanInstanceName, long macTimeout, String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        boolean isSuccess = true;
        if (existingElanInstance != null) {
            if (compareWithExistingElanInstance(existingElanInstance, macTimeout, description)) {
                logger.debug("Elan Instance is already present in the Operational DS {}", existingElanInstance);
                return true;
            } else {
                ElanInstance updateElanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                    .setDescription(description).setMacTimeout(macTimeout)
                    .setKey(new ElanInstanceKey(elanInstanceName)).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName), updateElanInstance);
                logger.debug("Updating the Elan Instance {} with MAC TIME-OUT %l and Description %s ",
                    updateElanInstance, macTimeout, description);
            }
        } else {
            ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                .setMacTimeout(macTimeout).setDescription(description).setKey(new ElanInstanceKey(elanInstanceName))
                .build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName), elanInstance);
            logger.debug("Creating the new Elan Instance {}", elanInstance);
        }
        return isSuccess;
    }

    public static boolean compareWithExistingElanInstance(ElanInstance existingElanInstance, long macTimeOut,
                                                          String description) {
        boolean isEqual = false;
        if (existingElanInstance.getMacTimeout() == macTimeOut
            && existingElanInstance.getDescription().equals(description)) {
            isEqual = true;
        }
        return isEqual;
    }

    @Override
    public void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription) {
        createElanInstance(elanInstanceName, newMacTimout, newDescription);
    }

    @Override
    public boolean deleteElanInstance(String elanInstanceName) {
        boolean isSuccess = false;
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        if (existingElanInstance == null) {
            logger.debug("Elan Instance is not present {}", existingElanInstance);
            return isSuccess;
        }
        logger.debug("Deletion of the existing Elan Instance {}", existingElanInstance);
        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
            ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName));
        isSuccess = true;
        return isSuccess;
    }

    @Override
    public void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses,
                                 String description) {
        ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
        if (existingElanInstance != null) {
            ElanInterface elanInterface;
            if (staticMacAddresses == null) {
                elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                    .setDescription(description).setName(interfaceName).setKey(new ElanInterfaceKey(interfaceName))
                    .build();
            } else {
                elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                    .setDescription(description).setName(interfaceName)
                    .setStaticMacEntries(getPhysAddress(staticMacAddresses))
                    .setKey(new ElanInterfaceKey(interfaceName)).build();
            }
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
            logger.debug("Creating the new ELan Interface {}", elanInterface);
        }

    }

    @Override
    public void updateElanInterface(String elanInstanceName, String interfaceName,
                                    List<String> updatedStaticMacAddresses, String newDescription) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if (existingElanInterface == null) {
            return;
        }
        List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
        List<PhysAddress> updatedMacAddresses = getPhysAddress(updatedStaticMacAddresses);
        List<PhysAddress> updatedPhysAddress = getUpdatedPhyAddress(existingMacAddress, updatedMacAddresses);
        if (updatedPhysAddress.size() > 0) {
            logger.debug("updating the ElanInterface with new Mac Entries {}", updatedStaticMacAddresses);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(interfaceName).setDescription(newDescription).setStaticMacEntries(updatedPhysAddress)
                .setKey(new ElanInterfaceKey(interfaceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    @Override
    public void deleteElanInterface(String elanInstanceName, String interfaceName) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if (existingElanInterface != null) {
            ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
            logger.debug("deleting the Elan Interface {}", existingElanInterface);
        }
    }

    @Override
    public void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        PhysAddress updateStaticMacAddress = new PhysAddress(macAddress);
        if (existingElanInterface != null) {
            List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
            if (existingMacAddress.contains(updateStaticMacAddress)) {
                return;
            }
            existingMacAddress.add(updateStaticMacAddress);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(interfaceName).setStaticMacEntries(existingMacAddress)
                .setDescription(existingElanInterface.getDescription()).setKey(new ElanInterfaceKey(interfaceName))
                .build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    @Override
    public void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress)
        throws MacNotFoundException {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        PhysAddress physAddress = new PhysAddress(macAddress);
        if (existingElanInterface == null) {
            return;
        }
        List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
        if (existingMacAddress.contains(physAddress)) {
            existingMacAddress.remove(physAddress);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(interfaceName).setStaticMacEntries(existingMacAddress)
                .setDescription(existingElanInterface.getDescription()).setKey(new ElanInterfaceKey(interfaceName))
                .build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        } else {
            throw new MacNotFoundException("Mac Not Found Exception");
        }
    }

    @Override
    public Collection<MacEntry> getElanMacTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        List<MacEntry> macAddress = new ArrayList<>();
        if (elanInfo == null) {
            return macAddress;
        }
        List<String> elanInterfaces = elanInfo.getElanInterfaces();
        if (elanInterfaces != null && elanInterfaces.size() > 0) {
            for (String elanInterface : elanInterfaces) {
                ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null
                    && elanInterfaceMac.getMacEntry().size() > 0) {
                    macAddress.addAll(elanInterfaceMac.getMacEntry());
                }
            }
        }
        return macAddress;
    }

    @Override
    public void flushMACTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        if (elanInfo == null) {
            return;
        }
        List<String> elanInterfaces = elanInfo.getElanInterfaces();
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        for (String elanInterface : elanInterfaces) {
            ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
            if (elanInterfaceMac.getMacEntry() != null && elanInterfaceMac.getMacEntry().size() > 0) {
                List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
                for (MacEntry macEntry : macEntries) {
                    try {
                        deleteStaticMacAddress(elanInstanceName, elanInterface, macEntry.getMacAddress().getValue());
                    } catch (MacNotFoundException e) {
                        logger.error("Mac Not Found Exception {}", e);
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    @Override
    public void close() throws Exception {
        this.elanInstanceManager.close();
        this.elanL2GatewayProvider.close();
    }

    public static List<PhysAddress> getPhysAddress(List<String> macAddress) {
        List<PhysAddress> physAddresses = new ArrayList<>();
        for (String mac : macAddress) {
            physAddresses.add(new PhysAddress(mac));
        }
        return physAddresses;
    }

    public List<PhysAddress> getUpdatedPhyAddress(List<PhysAddress> originalAddresses,
                                                  List<PhysAddress> updatePhyAddresses) {
        if (updatePhyAddresses != null && !updatePhyAddresses.isEmpty()) {
            List<PhysAddress> existingClonedPhyAddress = new ArrayList<>();
            if (originalAddresses != null && !originalAddresses.isEmpty()) {
                existingClonedPhyAddress.addAll(0, originalAddresses);
                originalAddresses.removeAll(updatePhyAddresses);
                updatePhyAddresses.removeAll(existingClonedPhyAddress);
            }
        }
        return updatePhyAddresses;
    }

    @Override
    public ElanInstance getElanInstance(String elanName) {
        return ElanUtils.getElanInstanceByName(elanName);
    }

    @Override
    public List<ElanInstance> getElanInstances() {
        List<ElanInstance> elanList = new ArrayList<>();
        InstanceIdentifier<ElanInstances> elanInstancesIdentifier = InstanceIdentifier.builder(ElanInstances.class)
            .build();
        Optional<ElanInstances> elansOptional = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
            elanInstancesIdentifier);
        if (elansOptional.isPresent()) {
            elanList.addAll(elansOptional.get().getElanInstance());
        }
        return elanList;
    }

    @Override
    public List<String> getElanInterfaces(String elanInstanceName) {
        List<String> elanInterfaces = new ArrayList<>();
        InstanceIdentifier<ElanInterfaces> elanInterfacesIdentifier = InstanceIdentifier.builder(ElanInterfaces.class)
            .build();
        Optional<ElanInterfaces> elanInterfacesOptional = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
            elanInterfacesIdentifier);
        if (!elanInterfacesOptional.isPresent()) {
            return elanInterfaces;
        }
        List<ElanInterface> elanInterfaceList = elanInterfacesOptional.get().getElanInterface();
        for (ElanInterface elanInterface : elanInterfaceList) {
            if (elanInterface.getElanInstanceName().equals(elanInstanceName)) {
                elanInterfaces.add(elanInterface.getName());
            }
        }
        return elanInterfaces;
    }

    public boolean getGenerateIntBridgeMac() {
        return generateIntBridgeMac;
    }

    public void setGenerateIntBridgeMac(boolean generateIntBridgeMac) {
        this.generateIntBridgeMac = generateIntBridgeMac;
    }

    @Override
    public void createExternalElanNetworks(Node node) {
        handleExternalElanNetworks(node, new BiFunction<ElanInstance, String, Void>() {

            @Override
            public Void apply(ElanInstance elanInstance, String interfaceName) {
                createExternalElanNetwork(elanInstance, interfaceName);
                return null;
            }
        });
    }

    @Override
    public void createExternalElanNetwork(ElanInstance elanInstance) {
        handleExternalElanNetwork(elanInstance, new BiFunction<ElanInstance, String, Void>() {

            @Override
            public Void apply(ElanInstance elanInstance, String interfaceName) {
                createExternalElanNetwork(elanInstance, interfaceName);
                return null;
            }

        });
    }

    @Override
    public void deleteExternalElanNetworks(Node node) {
        handleExternalElanNetworks(node, new BiFunction<ElanInstance, String, Void>() {

            @Override
            public Void apply(ElanInstance elanInstance, String interfaceName) {
                deleteExternalElanNetwork(elanInstance, interfaceName);
                return null;
            }
        });
    }

    @Override
    public void deleteExternalElanNetwork(ElanInstance elanInstance) {
        handleExternalElanNetwork(elanInstance, new BiFunction<ElanInstance, String, Void>() {

            @Override
            public Void apply(ElanInstance elanInstance, String interfaceName) {
                deleteExternalElanNetwork(elanInstance, interfaceName);
                return null;
            }

        });
    }

    @Override
    public void updateExternalElanNetworks(Node origNode, Node updatedNode) {
        if (!bridgeMgr.isIntegrationBridge(updatedNode)) {
            return;
        }

        List<ElanInstance> elanInstances = getElanInstances();
        if (elanInstances == null || elanInstances.isEmpty()) {
            logger.trace("No ELAN instances found");
            return;
        }

        Optional<Map<String, String>> origProviderMapOpt = bridgeMgr.getOpenvswitchOtherConfigMap(origNode,
                ElanBridgeManager.PROVIDER_MAPPINGS_KEY);
        Optional<Map<String, String>> updatedProviderMapOpt = bridgeMgr.getOpenvswitchOtherConfigMap(updatedNode,
                ElanBridgeManager.PROVIDER_MAPPINGS_KEY);
        Map<String, String> origProviderMappping = origProviderMapOpt.or(Collections.emptyMap());
        Map<String, String> updatedProviderMappping = updatedProviderMapOpt.or(Collections.emptyMap());

        for (ElanInstance elanInstance : elanInstances) {
            String physicalNetworkName = elanInstance.getPhysicalNetworkName();
            if (physicalNetworkName != null) {
                String origPortName = origProviderMappping.get(physicalNetworkName);
                String updatedPortName = updatedProviderMappping.get(physicalNetworkName);
                if (origPortName != null && !origPortName.equals(updatedPortName)) {
                    deleteExternalElanNetwork(elanInstance, getExtInterfaceName(origNode, physicalNetworkName));
                }
                if (updatedPortName != null && !updatedPortName.equals(origPortName)) {
                    createExternalElanNetwork(elanInstance, getExtInterfaceName(updatedNode, updatedPortName));
                }
            }
        }
    }

    private void createExternalElanNetwork(ElanInstance elanInstance, String interfaceName) {
        if (interfaceName == null) {
            logger.trace("No physial interface is attached to {}", elanInstance.getPhysicalNetworkName());
            return;
        }

        String elanInterfaceName = createIetfInterfaces(elanInstance, interfaceName);
        addElanInterface(elanInstance.getElanInstanceName(), elanInterfaceName, null, null);
    }

    private void deleteExternalElanNetwork(ElanInstance elanInstance, String interfaceName) {
        if (interfaceName == null) {
            logger.trace("No physial interface is attached to {}", elanInstance.getPhysicalNetworkName());
            return;
        }

        String elanInstanceName = elanInstance.getElanInstanceName();
        for (String elanInterface : getExternalElanInterfaces(elanInstanceName)) {
            if (elanInterface.startsWith(interfaceName)) {
                deleteIetfInterface(elanInterface);
                deleteElanInterface(elanInstanceName, elanInterface);
            }
        }
    }

    @Override
    public Collection<String> getExternalElanInterfaces(String elanInstanceName) {
        List<String> elanInterfaces = getElanInterfaces(elanInstanceName);
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            logger.trace("No ELAN interfaces defined for {}", elanInstanceName);
            return Collections.emptySet();
        }

        Set<String> externalElanInterfaces = new HashSet<>();
        for (String elanInterface : elanInterfaces) {
            if (ElanUtils.isExternal(elanInterface)) {
                externalElanInterfaces.add(elanInterface);
            }
        }

        return externalElanInterfaces;
    }

    /**
     * Create ietf-interfaces based on the ELAN segment type.<br>
     * For segment type flat - create transparent interface pointing to the
     * patch-port attached to the physnet port.<br>
     * For segment type vlan - create trunk interface pointing to the patch-port
     * attached to the physnet port + trunk-member interface pointing to the
     * trunk interface.
     *
     * @param elanInstance
     *            ELAN instance
     * @param parentRef
     *            parent interface name
     * @return the name of the interface to be added to the ELAN instance i.e.
     *         trunk-member name for vlan network and transparent for flat
     *         network or null otherwise
     */
    private String createIetfInterfaces(ElanInstance elanInstance, String parentRef) {
        String interfaceName = null;
        Long segmentationId = elanInstance.getSegmentationId();

        try {
            if (ElanUtils.isFlat(elanInstance)) {
                interfaceName = parentRef + ":flat";
                interfaceManager.createVLANInterface(interfaceName, parentRef, null, null, null,
                        IfL2vlan.L2vlanMode.Transparent);
            } else if (ElanUtils.isVlan(elanInstance)) {
                String trunkName = parentRef + ":trunk";
                interfaceManager.createVLANInterface(interfaceName, parentRef, null, null, null,
                        IfL2vlan.L2vlanMode.Trunk, true);
                interfaceName = parentRef + IfmConstants.OF_URI_SEPARATOR + segmentationId;
                interfaceManager.createVLANInterface(interfaceName, trunkName, null, segmentationId.intValue(), null,
                        IfL2vlan.L2vlanMode.TrunkMember, true);
            }
        } catch (InterfaceAlreadyExistsException e) {
            logger.trace("Interface {} was already created", interfaceName);
        }

        return interfaceName;
    }

    private void deleteIetfInterface(String interfaceName) {
        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        InstanceIdentifier<Interface> interfaceInstanceIdentifier = InstanceIdentifier
                .builder(Interfaces.class).child(Interface.class, interfaceKey).build();
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        logger.debug("Deleting IETF interface {}", interfaceName);
    }

    private String getExtInterfaceName(Node node, String physicalNetworkName) {
        if (physicalNetworkName == null) {
            return null;
        }

        String providerMappingValue = bridgeMgr.getProviderMappingValue(node, physicalNetworkName);
        if (providerMappingValue == null) {
            logger.trace("No provider mapping found for physicalNetworkName {} node {}", physicalNetworkName,
                    node.getNodeId().getValue());
            return null;
        }

        return bridgeMgr.southboundUtils.getDataPathId(node) + IfmConstants.OF_URI_SEPARATOR
                + bridgeMgr.getIntBridgePortNameFor(node, providerMappingValue);
    }

    private void handleExternalElanNetworks(Node node, BiFunction<ElanInstance, String, Void> function) {
        if (!bridgeMgr.isIntegrationBridge(node)) {
            return;
        }

        List<ElanInstance> elanInstances = getElanInstances();
        if (elanInstances == null || elanInstances.isEmpty()) {
            logger.trace("No ELAN instances found");
            return;
        }

        for (ElanInstance elanInstance : elanInstances) {
            String interfaceName = getExtInterfaceName(node, elanInstance.getPhysicalNetworkName());
            if (interfaceName != null) {
                function.apply(elanInstance, interfaceName);
            }
        }
    }

    private void handleExternalElanNetwork(ElanInstance elanInstance, BiFunction<ElanInstance, String, Void> function) {
        String elanInstanceName = elanInstance.getElanInstanceName();
        if (elanInstance.getPhysicalNetworkName() == null) {
            logger.trace("No physical network attached to {}", elanInstanceName);
            return;
        }

        List<Node> nodes = bridgeMgr.southboundUtils.getOvsNodes();
        if (nodes == null || nodes.isEmpty()) {
            logger.trace("No OVS nodes found while creating external network for ELAN {}",
                    elanInstance.getElanInstanceName());
            return;
        }

        for (Node node : nodes) {
            if (bridgeMgr.isIntegrationBridge(node)) {
                String interfaceName = getExtInterfaceName(node, elanInstance.getPhysicalNetworkName());
                function.apply(elanInstance, interfaceName);
            }
        }
    }

}
