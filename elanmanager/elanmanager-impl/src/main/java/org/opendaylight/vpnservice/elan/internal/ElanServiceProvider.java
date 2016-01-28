/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.internal;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.elanmanager.api.IElanService;
import org.opendaylight.vpnservice.elan.statisitcs.ElanStatisticsImpl;
import org.opendaylight.vpnservice.elan.utils.ElanConstants;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.statistics.rev150824.ElanStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.elanmanager.exceptions.MacNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

public class ElanServiceProvider implements BindingAwareProvider, IElanService, AutoCloseable {

    private IdManagerService idManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private OdlInterfaceRpcService interfaceManagerRpcService;
    private ElanInstanceManager elanInstanceManager;
    private ElanForwardingEntriesHandler elanForwardingEntriesHandler;
    private ElanInterfaceManager elanInterfaceManager;
    private ElanPacketInHandler elanPacketInHandler;
    private ElanSmacFlowEventListener elanSmacFlowEventListener;
    private ElanNodeListener elanNodeListener;
    private NotificationService notificationService;
    private RpcProviderRegistry rpcProviderRegistry;

    public ElanServiceProvider(RpcProviderRegistry rpcRegistry) {
        rpcProviderRegistry = rpcRegistry;
    }

    //private ElanInterfaceEventListener elanInterfaceEventListener;
    private ElanItmEventListener elanItmEventListener;

    public void setItmRpcService(ItmRpcService itmRpcService) {
        this.itmRpcService = itmRpcService;
    }

    public ItmRpcService getItmRpcService() {
        return itmRpcService;
    }

    private ItmRpcService itmRpcService;
    private DataBroker broker;

    private static final Logger logger = LoggerFactory.getLogger(ElanServiceProvider.class);

    @Override
    public void onSessionInitiated(ProviderContext session) {
        createIdPool();
        broker = session.getSALService(DataBroker.class);

        elanForwardingEntriesHandler = new ElanForwardingEntriesHandler(broker);

        elanInterfaceManager = ElanInterfaceManager.getElanInterfaceManager();
        elanInterfaceManager.setInterfaceManager(interfaceManager);
        elanInterfaceManager.setIdManager(idManager);
        elanInterfaceManager.setMdSalApiManager(mdsalManager);
        elanInterfaceManager.setDataBroker(broker);
        elanInterfaceManager.registerListener();
        elanInterfaceManager.setInterfaceManagerRpcService(interfaceManagerRpcService);
        elanInterfaceManager.setElanForwardingEntriesHandler(elanForwardingEntriesHandler);

        elanInstanceManager = ElanInstanceManager.getElanInstanceManager();
        elanInstanceManager.setDataBroker(broker);
        elanInstanceManager.setIdManager(idManager);
        elanInstanceManager.setElanInterfaceManager(elanInterfaceManager);
        elanInstanceManager.registerListener();

        elanNodeListener = new ElanNodeListener(broker, mdsalManager);

        elanPacketInHandler = new ElanPacketInHandler(broker);
        elanPacketInHandler.setInterfaceManager(interfaceManager);
        notificationService.registerNotificationListener(elanPacketInHandler);

        elanSmacFlowEventListener = new ElanSmacFlowEventListener(broker);
        elanSmacFlowEventListener.setMdSalApiManager(mdsalManager);
        elanSmacFlowEventListener.setInterfaceManager(interfaceManager);
        elanSmacFlowEventListener.setSalFlowService(session.getRpcService(SalFlowService.class));
        notificationService.registerNotificationListener(elanSmacFlowEventListener);

        // Initialize statistics rpc provider for elan
        ElanStatisticsService interfaceStatsService = new ElanStatisticsImpl(broker, interfaceManager, mdsalManager);
        rpcProviderRegistry.addRpcImplementation(ElanStatisticsService.class, interfaceStatsService);

        ElanUtils.setElanServiceProvider(this);
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

    public IMdsalApiManager getMdsalManager() {
        return mdsalManager;
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

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(ElanConstants.ELAN_ID_POOL_NAME).setLow(ElanConstants.ELAN_ID_LOW_VALUE).setHigh(ElanConstants.ELAN_ID_HIGH_VALUE)
            .build();
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
        if(existingElanInstance != null) {
           if(compareWithExistingElanInstance(existingElanInstance, macTimeout, description)) {
               logger.debug("Elan Instance is already present in the Operational DS {}", existingElanInstance);
               return true;
           } else {
               ElanInstance updateElanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setDescription(description).setMacTimeout(macTimeout).setKey(new ElanInstanceKey(elanInstanceName)).build();
               MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceConfigurationDataPath(elanInstanceName), updateElanInstance);
               logger.debug("Updating the Elan Instance {} with MAC TIME-OUT %l and Description %s ", updateElanInstance, macTimeout, description);
           }
        } else {
            ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setMacTimeout(macTimeout).setDescription(description).setKey(new ElanInstanceKey(elanInstanceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceIdentifier(elanInstanceName), elanInstance);
            logger.debug("Creating the new Elan Instance {}", elanInstance);
        }
        return isSuccess;
    }

    public static boolean compareWithExistingElanInstance(ElanInstance existingElanInstance, long macTimeOut, String description) {
        boolean isEqual = false;
        if(existingElanInstance.getMacTimeout() == macTimeOut && existingElanInstance.getDescription().equals(description)) {
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
        if(existingElanInstance == null) {
            logger.debug("Elan Instance is not present {}" , existingElanInstance);
            return isSuccess;
        }
        logger.debug("Deletion of the existing Elan Instance {}", existingElanInstance);
        ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInstanceIdentifier(elanInstanceName));
        isSuccess = true;
        return isSuccess;
    }

    @Override
    public void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses, String description) {
          ElanInstance existingElanInstance = elanInstanceManager.getElanInstanceByName(elanInstanceName);
          if(existingElanInstance != null) {
              ElanInterface elanInterface;
              if(staticMacAddresses == null) {
                  elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName).setDescription(description).setName(interfaceName).setKey(new ElanInterfaceKey(interfaceName)).build();
              } else {
                  elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName).setDescription(description).setName(interfaceName).setStaticMacEntries(getPhysAddress(staticMacAddresses)).setKey(new ElanInterfaceKey(interfaceName)).build();
              }
              MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
              logger.debug("Creating the new ELan Interface {}", elanInterface);
          }

    }

    @Override
    public void updateElanInterface(String elanInstanceName, String interfaceName, List<String> updatedStaticMacAddresses, String newDescription) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if (existingElanInterface == null) {
            return;
        }
        List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
        List<PhysAddress> updatedMacAddresses = getPhysAddress(updatedStaticMacAddresses);
        List<PhysAddress> updatedPhysAddress = getUpdatedPhyAddress(existingMacAddress, updatedMacAddresses);
        if(updatedPhysAddress.size() > 0) {
            logger.debug("updating the ElanInterface with new Mac Entries {}", updatedStaticMacAddresses);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName).setName(interfaceName).setDescription(newDescription).setStaticMacEntries(updatedPhysAddress).setKey(new ElanInterfaceKey(interfaceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    @Override
    public void deleteElanInterface(String elanInstanceName, String interfaceName) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        if(existingElanInterface != null) {
            ElanUtils.delete(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
            logger.debug("deleting the Elan Interface {}", existingElanInterface);
        }
    }

    @Override
    public void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) {
        ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        PhysAddress updateStaticMacAddress = new PhysAddress(macAddress);
        if (existingElanInterface != null) {
            List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
            if(existingMacAddress.contains(updateStaticMacAddress)) {
                return;
            }
            existingMacAddress.add(updateStaticMacAddress);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName).setName(interfaceName).setStaticMacEntries(existingMacAddress).setDescription(existingElanInterface.getDescription()).setKey(new ElanInterfaceKey(interfaceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        }
    }

    @Override
    public void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) throws MacNotFoundException {
           ElanInterface existingElanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(interfaceName);
        PhysAddress physAddress = new PhysAddress(macAddress);
        if(existingElanInterface == null) {
            return;
        }
        List<PhysAddress> existingMacAddress = existingElanInterface.getStaticMacEntries();
        if(existingMacAddress.contains(physAddress)) {
            existingMacAddress.remove(physAddress);
            ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName).setName(interfaceName).setStaticMacEntries(existingMacAddress).setDescription(existingElanInterface.getDescription()).setKey(new ElanInterfaceKey(interfaceName)).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName), elanInterface);
        } else {
            throw new MacNotFoundException("Mac Not Found Exception");
        }
    }

    @Override
    public Collection<MacEntry> getElanMacTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        List<MacEntry> macAddress = new ArrayList<>();
        if(elanInfo == null) {
            return macAddress;
        }
       List<String> elanInterfaces =  elanInfo.getElanInterfaces();
        if(elanInterfaces != null && elanInterfaces.size() > 0) {
            for(String elanInterface : elanInterfaces) {
                ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                if(elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null && elanInterfaceMac.getMacEntry().size() > 0){
                    macAddress.addAll(elanInterfaceMac.getMacEntry());
                }
            }
        }
       return macAddress;
    }

    @Override
    public void flushMACTable(String elanInstanceName) {
        Elan elanInfo = ElanUtils.getElanByName(elanInstanceName);
        if(elanInfo == null) {
            return;
        }
        List<String> elanInterfaces = elanInfo.getElanInterfaces();
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        for (String elanInterface : elanInterfaces) {
            ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
            if (elanInterfaceMac.getMacEntry() != null && elanInterfaceMac.getMacEntry().size() > 0) {
                List<MacEntry> macEntries =  elanInterfaceMac.getMacEntry();
                for(MacEntry macEntry : macEntries) {
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
        elanInstanceManager.close();
    }

    public static List<PhysAddress> getPhysAddress(List<String> macAddress) {
        List<PhysAddress> physAddresses = new ArrayList<>();
        for(String mac : macAddress) {
            physAddresses.add(new PhysAddress(mac));
        }
        return physAddresses;
    }


    public List<PhysAddress> getUpdatedPhyAddress(List<PhysAddress> originalAddresses, List<PhysAddress> updatePhyAddresses) {
        if(updatePhyAddresses != null && !updatePhyAddresses.isEmpty()) {
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
        List<ElanInstance> elanList = new ArrayList<ElanInstance>();
        InstanceIdentifier<ElanInstances> elanInstancesIdentifier =  InstanceIdentifier.builder(ElanInstances.class).build();
        Optional<ElanInstances> elansOptional  = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION, elanInstancesIdentifier);
        if(elansOptional.isPresent()) {
            elanList.addAll(elansOptional.get().getElanInstance());
        }
        return elanList;
    }

    @Override
    public List<String> getElanInterfaces(String elanInstanceName) {
        List<String> elanInterfaces = new ArrayList<>();
        InstanceIdentifier<ElanInterfaces> elanInterfacesIdentifier =  InstanceIdentifier.builder(ElanInterfaces.class).build();
        Optional<ElanInterfaces> elanInterfacesOptional  = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION, elanInterfacesIdentifier);
        if(!elanInterfacesOptional.isPresent()) {
             return elanInterfaces;
        }
        List<ElanInterface> elanInterfaceList = elanInterfacesOptional.get().getElanInterface();
        for(ElanInterface elanInterface : elanInterfaceList) {
            if(elanInterface.getElanInstanceName().equals(elanInstanceName)) {
                elanInterfaces.add(elanInterface.getName());
            }
        }
        return elanInterfaces;
    }
}
