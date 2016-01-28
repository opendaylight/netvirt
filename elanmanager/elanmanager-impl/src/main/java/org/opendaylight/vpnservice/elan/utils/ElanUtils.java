/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.elan.internal.ElanInstanceManager;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.vpnservice.elan.internal.ElanServiceProvider;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceServiceUtil;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.TunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.tag.name.map.ElanTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.CreateTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.CreateTerminatingServiceActionsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveTerminatingServiceActionsInputBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesKey;


import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ElanUtils {

    private static ElanServiceProvider elanServiceProvider;
    private static final Logger logger = LoggerFactory.getLogger(ElanUtils.class);

    public static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    logger.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    logger.error("Error in Datastore operation", error);
                };
            };

    public static Integer getUniqueId(IdManagerService idManager, String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                                           .setPoolName(poolName)
                                           .setIdKey(idKey).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                logger.warn("RPC Call to Allocate Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Exception when Allocating Id",e);
        }
        return 0;
    }

    public static void setElanServiceProvider(ElanServiceProvider elanService) {
        elanServiceProvider = elanService;
    }
    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput releaseIdInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        Future<RpcResult<Void>> result = idManager.releaseId(releaseIdInput);
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }


    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceIdentifier(String elanName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanName)).build();
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceIdentifier() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class).build();
    }

    //elan-instances config container
    public static ElanInstance getElanInstanceByName(String elanInstanceName) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        Optional<ElanInstance> elanInstance = read(broker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
        if(elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    //elan-interfaces Config Container
    public static ElanInterface getElanInterfaceByElanInterfaceName(String elanInterfaceName) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        Optional<ElanInterface> existingElanInterface = read(broker, LogicalDatastoreType.CONFIGURATION, elanInterfaceId);
        if(existingElanInterface.isPresent()) {
            return existingElanInterface.get();
        }
        return null;
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface.class,
                new ElanInterfaceKey(interfaceName)).build();
    }

    //elan-state Operational container
    public static Elan getElanByName(String elanInstanceName) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<Elan> elanIdentifier = getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInstance = read(broker, LogicalDatastoreType.OPERATIONAL, elanIdentifier);
        if(elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public static InstanceIdentifier<Elan> getElanInstanceOperationalDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class, new ElanKey(elanInstanceName)).build();
    }

    // grouping of forwarding-entries
    public static MacEntry getInterfaceMacEntriesOperationalDataPath(String interfaceName, PhysAddress physAddress) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<MacEntry> existingMacEntryId = getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
        Optional<MacEntry> existingInterfaceMacEntry = read(broker, LogicalDatastoreType.OPERATIONAL, existingMacEntryId);
        if(existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static MacEntry getInterfaceMacEntriesOperationalDataPathFromId(InstanceIdentifier identifier) {
        DataBroker broker = elanServiceProvider.getBroker();
        Optional<MacEntry> existingInterfaceMacEntry = read(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        if(existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static InstanceIdentifier<MacEntry> getInterfaceMacEntriesIdentifierOperationalDataPath(String interfaceName, PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class).child(ElanInterfaceMac.class,
                new ElanInterfaceMacKey(interfaceName)).child(MacEntry.class, new MacEntryKey(physAddress)).build();

    }

    //elan-forwarding-tables Operational container
    public static MacEntry getMacTableByElanName(String elanName, PhysAddress physAddress) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<MacEntry> macId =  getMacEntryOperationalDataPath(elanName, physAddress);
        Optional<MacEntry> existingElanMacEntry = read(broker, LogicalDatastoreType.OPERATIONAL, macId);
        if(existingElanMacEntry.isPresent()) {
            return existingElanMacEntry.get();
        }
        return null;
    }


    public static MacEntry getMacEntryFromElanMacId(InstanceIdentifier identifier) {
        DataBroker broker = elanServiceProvider.getBroker();
        Optional<MacEntry> existingInterfaceMacEntry = read(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        if(existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName, PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class,
                new MacTableKey(elanName)).child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    public static InstanceIdentifier<MacTable> getElanMacTableOperationalDataPath(String elanName) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class,
                new MacTableKey(elanName)).build();
    }

    //elan-interface-forwarding-entries Operational container
    public static ElanInterfaceMac getElanInterfaceMacByInterfaceName(String interfaceName) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> existingElanInterface = read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        if(existingElanInterface.isPresent()) {
            return existingElanInterface.get();
        }
        return null;
    }

    public static InstanceIdentifier<ElanInterfaceMac> getElanInterfaceMacEntriesOperationalDataPath(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class).child(ElanInterfaceMac.class,
                new ElanInterfaceMacKey(interfaceName)).build();
    }

    //elan-dpn-interfaces Operational Container
    public static DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, BigInteger dpId) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
        Optional<DpnInterfaces> elanDpnInterfaces = read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId);
        if(elanDpnInterfaces.isPresent()) {
            return elanDpnInterfaces.get();
        }
        return null;
    }

    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName, BigInteger dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class,
                new ElanDpnInterfacesListKey(elanInstanceName)).child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
    }

    //elan-tag-name-map Operational Container
    public static ElanTagName getElanInfoByElanTag(long elanTag) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanId);
        if(existingElanInfo.isPresent()) {
            return existingElanInfo.get();
        }
        return null;
    }

    public static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(long elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class,
                new ElanTagNameKey(elanTag)).build();
    }

    // interface-index-tag operational container
    public static IfIndexInterface getInterfaceInfoByInterfaceTag(long interfaceTag) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        Optional<IfIndexInterface> existingInterfaceInfo = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
        if(existingInterfaceInfo.isPresent()) {
            return existingInterfaceInfo.get();
        }
        return null;
    }

    public static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(long interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class,
                new IfIndexInterfaceKey((int) interfaceTag)).build();
    }



    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    public static  ElanDpnInterfacesList getElanDpnInterfacesList(String elanName) {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if(existingElanDpnInterfaces.isPresent()) {
            return existingElanDpnInterfaces.get();
        }
        return null;
    }

    public static  ElanDpnInterfaces getElanDpnInterfacesList() {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfaceId = InstanceIdentifier.builder(ElanDpnInterfaces.class).build();
        Optional<ElanDpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if(existingElanDpnInterfaces.isPresent()) {
            return existingElanDpnInterfaces.get();
        }
        return null;
    }

    public static ElanForwardingTables getElanForwardingList() {
        DataBroker broker = elanServiceProvider.getBroker();
        InstanceIdentifier<ElanForwardingTables> elanForwardingTableId = InstanceIdentifier.builder(ElanForwardingTables.class).build();
        Optional<ElanForwardingTables> existingElanForwardingList = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, elanForwardingTableId);
        if(existingElanForwardingList.isPresent()) {
            return existingElanForwardingList.get();
        }
        return null;
    }


    public static long getElanLocalBCGID(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + (((elanTag % ElanConstants.ELAN_GID_MIN) *2) - 1);
    }

    public static long getElanRemoteBCGID(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + (((elanTag % ElanConstants.ELAN_GID_MIN) *2));
    }

    public static BigInteger getElanMetadataLabel(long elanTag) {
        return (BigInteger.valueOf(elanTag)).shiftLeft(24);
    }

    public static BigInteger getElanMetadataLabel(long elanTag, int lportTag) {
        return getElanMetadataLabel(elanTag).or(MetaDataUtil.getLportTagMetaData(lportTag));
    }

    public static BigInteger getElanMetadataMask() {
        return MetaDataUtil.METADATA_MASK_SERVICE.or(MetaDataUtil.METADATA_MASK_LPORT_TAG);
    }

    public static void setupMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
                                     String macAddress) {
        IMdsalApiManager mdsalApiManager = elanServiceProvider.getMdsalManager();
        DataBroker broker = elanServiceProvider.getBroker();
        ItmRpcService itmRpcService = elanServiceProvider.getItmRpcService();
        synchronized (macAddress) {
            logger.info("Acquired lock for mac : " + macAddress + "Proceeding with install operation.");
            setupKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress, mdsalApiManager);
            setupTermDmacFlows(interfaceInfo, mdsalApiManager);
            setupOrigDmacFlows(elanInfo, interfaceInfo, macAddress, mdsalApiManager, broker);
        }
    }

    public static void setupDMacFlowonRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId,
                                     String macAddress) {
        synchronized (macAddress) {
            logger.info("Acquired lock for mac : " + macAddress + "Proceeding with install operation.");
            setupOrigDmacFlowsonRemoteDpn(elanInfo, interfaceInfo, dstDpId, macAddress);
        }
    }


    private static void setupKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
                                           String macAddress, IMdsalApiManager mdsalApiManager) {
        FlowEntity flowEntity = getKnownSmacFlowEntity(elanInfo, interfaceInfo, macTimeout, macAddress);
        mdsalApiManager.installFlow(flowEntity);
        if (logger.isDebugEnabled()) {
            logger.debug("Known Smac flow entry created for elan Name:{}, logical Interface port:{} and mac address:{}", elanInfo.getElanInstanceName(), elanInfo.getDescription(), macAddress);
        }
    }

    private static FlowEntity getKnownSmacFlowEntity(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout, String macAddress) {
        BigInteger dpId = interfaceInfo.getDpId();
        int lportTag = interfaceInfo.getInterfaceTag();
        long elanTag = elanInfo.getElanTag();
        // Matching metadata and eth_src fields
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanInfo.getElanTag(), lportTag),
                ElanUtils.getElanMetadataMask() }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_src, new String[] { macAddress }));
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { ElanConstants.ELAN_DMAC_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, ElanConstants.ELAN_SMAC_TABLE,
                getKnownDynamicmacFlowRef(ElanConstants.ELAN_SMAC_TABLE, dpId, lportTag, macAddress, elanTag),
                20, elanInfo.getDescription(), (int)macTimeout, 0, ElanConstants.COOKIE_ELAN_KNOWN_SMAC.add(BigInteger.valueOf(elanTag)),
                mkMatches, mkInstructions);
        flowEntity.setStrictFlag(true);
        flowEntity.setSendFlowRemFlag(macTimeout != 0); //If Mac timeout is 0, the flow wont be deleted automatically, so no need to get notified
        return flowEntity;
    }

    private static void setupTermDmacFlows(InterfaceInfo interfaceInfo, IMdsalApiManager mdsalApiManager) {
        BigInteger dpId = interfaceInfo.getDpId();
        int lportTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE, getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE,lportTag), 5, String.format("%s:%d","ITM Flow Entry ",lportTag), 0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(lportTag)), getTunnelIdMatchForFilterEqualsLPortTag(lportTag),
                getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));
        mdsalApiManager.installFlow(dpId, flow);
        if (logger.isDebugEnabled()) {
            logger.debug("Terminating service table flow entry created on dpn:{} for logical Interface port:{}", dpId, interfaceInfo.getPortName());
        }
    }

    private static String getFlowRef(short tableId, int elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    private static List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int LportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                BigInteger.valueOf(LportTag)}));
        return mkMatches;


    }

    public static List<Instruction> getInstructionsInPortForOutGroup(
            String ifName) {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List <Action> actionsInfos = new ArrayList <Action> ();
        actionsInfos.addAll(ElanUtils.getEgressActionsForInterface(ifName));
        mkInstructions.add(new InstructionBuilder().setInstruction(new ApplyActionsCaseBuilder().setApplyActions(new ApplyActionsBuilder().setAction(actionsInfos).build()).build())
                .setKey(new InstructionKey(0)).build());
             return mkInstructions;
    }

    public static Instruction getWriteActionInstruction(List<Action> actions) {
        return new InstructionBuilder().setInstruction(new WriteActionsCaseBuilder().setWriteActions(new WriteActionsBuilder().setAction(actions).build()).build()).setKey(new InstructionKey(0)).build();
    }

    public static Instruction getApplyActionInstruction(List<Action> actions) {
        return new InstructionBuilder().setInstruction(new ApplyActionsCaseBuilder().setApplyActions(new ApplyActionsBuilder().setAction(actions).build()).build()).setKey(new InstructionKey(0)).build();
    }


    public static List<Action> getEgressActionsForInterface(String ifName) {
        List<Action> listAction = new ArrayList<Action>();
        try {
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    elanServiceProvider.getInterfaceManagerRpcService().getEgressActionsForInterface(
                            new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                logger.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName, rpcResult.getErrors());
            } else {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actions =
                        rpcResult.getResult().getAction();
                listAction = actions;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listAction;
    }

    private static void setupOrigDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
                                           IMdsalApiManager mdsalApiManager, DataBroker broker) {
        BigInteger dpId = interfaceInfo.getDpId();
        String ifName = interfaceInfo.getInterfaceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        long groupId = interfaceInfo.getGroupId();
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        if(remoteFEs != null) {
            for (DpnInterfaces remoteFE : remoteFEs) {
                Long elanTag = elanInfo.getElanTag();
                if (remoteFE.getDpId().equals(dpId)) {
                    // On the local FE set up a direct output flow
                    setupLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInstanceName, mdsalApiManager, ifTag);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address:{} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, dpId);
                    }
                } else {
                    if (isDpnPresent(remoteFE.getDpId())) {
                        // Check for the Remote DPN present in Inventory Manager
                        setupRemoteDmacFlow(remoteFE.getDpId(), dpId, interfaceInfo.getInterfaceTag(), elanTag, macAddress, elanInstanceName);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address:{} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, remoteFE.getDpId());
                        }
                    }
                }
            }
        }
    }

    private static void setupOrigDmacFlowsonRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId, String macAddress) {
        BigInteger dpId = interfaceInfo.getDpId();
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        for(DpnInterfaces remoteFE: remoteFEs) {
            Long elanTag = elanInfo.getElanTag();
            if (remoteFE.getDpId().equals(dstDpId)) {
                // Check for the Remote DPN present in Inventory Manager
                setupRemoteDmacFlow(dstDpId, dpId, interfaceInfo.getInterfaceTag(), elanTag, macAddress, elanInstanceName);
                if (logger.isDebugEnabled()) {
                    logger.debug("Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address {} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, remoteFE.getDpId());
                }
                break;
            }
        }
    }


    @SuppressWarnings("unchecked")
    public static List<DpnInterfaces> getInvolvedDpnsInElan(String elanName) {
        List<DpnInterfaces> dpns = ElanInstanceManager.getElanInstanceManager().getElanDPNByName(elanName);
        return dpns;
    }

    private static void setupLocalDmacFlow(long elanTag, BigInteger dpId, String ifName, String macAddress,
                                           String displayName, IMdsalApiManager mdsalApiManager, long ifTag) {
        Flow flowEntity = getLocalDmacFlowEntry(elanTag, dpId, ifName, macAddress, displayName, ifTag);
        mdsalApiManager.installFlow(dpId, flowEntity);

    }

    public static String getKnownDynamicmacFlowRef(short tableId, BigInteger dpId, long lporTag, String macAddress, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).append(dpId).append(lporTag).append(macAddress).toString();
    }

    public static String getKnownDynamicmacFlowRef(short tableId, BigInteger dpId, BigInteger remoteDpId, String macAddress, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).append(dpId).append(remoteDpId).append(macAddress).toString();
    }

    public static Flow getLocalDmacFlowEntry(long elanTag, BigInteger dpId, String ifName, String macAddress,
                                                   String displayName, long ifTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { macAddress }));

        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List <Action> actionsInfos = new ArrayList <Action> ();
        actionsInfos.addAll(getEgressActionsForInterface(ifName));
        mkInstructions.add(new InstructionBuilder().setInstruction(new ApplyActionsCaseBuilder().setApplyActions(new ApplyActionsBuilder().setAction(actionsInfos).build()).build())
                .setKey(new InstructionKey(0)).build());
        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_DMAC_TABLE, getKnownDynamicmacFlowRef(ElanConstants.ELAN_DMAC_TABLE, dpId, ifTag, macAddress, elanTag),
                20, displayName, 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    public static void setupRemoteDmacFlow(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag, String macAddress,
                                           String displayName) {
        IMdsalApiManager mdsalApiManager = elanServiceProvider.getMdsalManager();
        Flow flowEntity = getRemoteDmacFlowEntry(srcDpId, destDpId, lportTag, elanTag, macAddress, displayName);
        mdsalApiManager.installFlow(srcDpId, flowEntity);
    }

    public static Flow getRemoteDmacFlowEntry(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag,
                                              String macAddress, String displayName) {
        ItmRpcService itmRpcService = elanServiceProvider.getItmRpcService();
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[]{
                ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { macAddress }));

        List<Instruction> mkInstructions = new ArrayList<Instruction>();

        //List of ActionInfo for the provided Source and Destination DPIDs
        try {
            List<Action> actionsInfos = getItmEgressAction(srcDpId, destDpId, lportTag);
            Instruction instruction = new InstructionBuilder().setInstruction(new ApplyActionsCaseBuilder().setApplyActions(new ApplyActionsBuilder().setAction(actionsInfos).build()).build())
                    .setKey(new InstructionKey(0)).build();
            mkInstructions.add(instruction);
        } catch (Exception e) {
            logger.error("Interface Not Found exception");
        }


        Flow flow = MDSALUtil.buildFlowNew(ElanConstants.ELAN_DMAC_TABLE, getKnownDynamicmacFlowRef(ElanConstants.ELAN_DMAC_TABLE, srcDpId, destDpId, macAddress, elanTag), 20
                , displayName, 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;

    }

    public static void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, MacEntry macEntry) {
        if (elanInfo == null || interfaceInfo == null) {
            return;
        }
        String macAddress = macEntry.getMacAddress().getValue();
        synchronized (macAddress) {
            logger.info("Acquired lock for mac : " + macAddress + "Proceeding with remove operation.");
            deleteMacFlows(elanInfo, interfaceInfo,  macAddress, true);
        }
    }

    public static void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress, boolean deleteSmac) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        String ifName = interfaceInfo.getInterfaceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        IMdsalApiManager mdsalApiManager = elanServiceProvider.getMdsalManager();
        ItmRpcService itmRpcService = elanServiceProvider.getItmRpcService();
        BigInteger srcdpId = interfaceInfo.getDpId();
        String displayName = elanInstanceName;
        long groupId = interfaceInfo.getGroupId();
        for (DpnInterfaces dpnInterface: remoteFEs) {
            Long elanTag = elanInfo.getElanTag();
            if (dpnInterface.getDpId().equals(srcdpId)) {
                if(deleteSmac) {
                    mdsalApiManager.removeFlow(getKnownSmacFlowEntity(elanInfo, interfaceInfo, 0, macAddress));
                }
                mdsalApiManager.removeFlow(dpnInterface.getDpId(), getLocalDmacFlowEntry(elanTag, dpnInterface.getDpId(), ifName, macAddress, displayName, ifTag));
                RemoveTerminatingServiceActionsInput removeTerminatingServiceActionsInput = new RemoveTerminatingServiceActionsInputBuilder().setServiceId(interfaceInfo.getInterfaceTag()).setDpnId(dpnInterface.getDpId()).build();
                itmRpcService.removeTerminatingServiceActions(removeTerminatingServiceActionsInput);
                  if (logger.isDebugEnabled()) {
                    logger.debug("All the required flows deleted for elan:{}, logical Interface port:{} and mac address:{} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, dpnInterface.getDpId());
                }
            } else if (isDpnPresent(dpnInterface.getDpId())) {
                mdsalApiManager.removeFlow(dpnInterface.getDpId(),
                        getRemoteDmacFlowEntry(dpnInterface.getDpId(), srcdpId, interfaceInfo.getInterfaceTag(), elanTag, macAddress,
                                displayName));
                if (logger.isDebugEnabled()) {
                    logger.debug("Dmac flow entry deleted for elan:{}, logical interface port:{} and mac address:{} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, dpnInterface.getDpId());
                }
            }
        }
    }

    public static void UpdateOperationalDataStore(DataBroker broker, IdManagerService idManager, ElanInstance elanInstanceAdded) {
        String elanInstanceName = elanInstanceAdded.getElanInstanceName();
        long elanTag = ElanUtils.getUniqueId(idManager, ElanConstants.ELAN_ID_POOL_NAME, elanInstanceName);
        Elan elanInfo = new ElanBuilder().setName(elanInstanceName).setKey(new ElanKey(elanInstanceName)).build();
        //Add the ElanState in the elan-state operational data-store
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName), elanInfo);
        //Add the ElanMacTable in the elan-mac-table operational data-store
        MacTable elanMacTable = new MacTableBuilder().setKey(new MacTableKey(elanInstanceName)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanInstanceName), elanMacTable);
        ElanTagName elanTagName = new ElanTagNameBuilder().setElanTag(elanTag).setKey(new ElanTagNameKey(elanTag)).setName(elanInstanceName).build();
        //Add the ElanTag to ElanName in the elan-tag-name Operational data-store
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag), elanTagName);
        ElanInstance elanInstanceWithTag = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setDescription(elanInstanceAdded.getDescription()).setMacTimeout(elanInstanceAdded
                .getMacTimeout() == null ? ElanConstants.DEFAULT_MAC_TIME_OUT : elanInstanceAdded.getMacTimeout()).setKey(elanInstanceAdded.getKey()).setElanTag(elanTag).build();
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, getElanInstanceIdentifier(elanInstanceName), elanInstanceWithTag);
    }

    public static boolean isDpnPresent(BigInteger dpnId) {
        DataBroker broker = elanServiceProvider.getBroker();
        boolean isPresent = false;
        String dpn = String.format("%s:%s", "openflow",dpnId);
        NodeId nodeId = new NodeId(dpn);
        InstanceIdentifier<Node> node = InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId)).build();
        Optional<Node> nodePresent = read(broker, LogicalDatastoreType.OPERATIONAL, node);
        if(nodePresent.isPresent()) {
            isPresent = true;
        }
        return isPresent;
    }

    public static ServicesInfo getServiceInfo(String elanInstanceName, long elanTag, String interfaceName) {
        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanUtils.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(ElanConstants.ELAN_SMAC_TABLE, ++instructionKey));

        ServicesInfo serviceInfo = InterfaceServiceUtil.buildServiceInfo(String.format("%s.%s", elanInstanceName, interfaceName), ElanConstants.ELAN_SERVICE_INDEX,
                priority, ElanConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
        return serviceInfo;
    }

    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
                                              InstanceIdentifier<T> path, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void syncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                        InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error writing to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }


    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                                 BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class).addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static InstanceIdentifier<BoundServices> buildServiceId(String vpnInterfaceName, short serviceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, new ServicesInfoKey(vpnInterfaceName))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }


    public static List<Action> getItmEgressAction(BigInteger sourceDpnId,
                                                      BigInteger destinationDpnId, int serviceTag) {
        ItmRpcService itmManager = elanServiceProvider.getItmRpcService();
        OdlInterfaceRpcService interfaceManagerRpcService = elanServiceProvider.getInterfaceManagerRpcService();
        logger.debug("In getItmIngress Action source {}, destination {}, elanTag {}", sourceDpnId, destinationDpnId, serviceTag);
        List<Action> actions = new ArrayList<>();
        String tunnelInterfaceName;
        GetTunnelInterfaceNameInput input = new GetTunnelInterfaceNameInputBuilder().setDestinationDpid(destinationDpnId).setSourceDpid(sourceDpnId).build();
        Future<RpcResult<GetTunnelInterfaceNameOutput>> output = itmManager.getTunnelInterfaceName(input);
        try {
            GetTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
            tunnelInterfaceName = tunnelInterfaceNameOutput.getInterfaceName();
            logger.debug("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelInterfaceName);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error in RPC call getTunnelInterfaceName {}", e);
            return actions;
        }
        if (tunnelInterfaceName != null && !tunnelInterfaceName.isEmpty()) {
            GetEgressActionsInput getEgressActionsForInterfaceInput = new GetEgressActionsInputBuilder().setServiceTag(Long.valueOf(serviceTag)).setIntfName(tunnelInterfaceName).build();
            Future<RpcResult<GetEgressActionsOutput>> egressActionsOutputFuture = interfaceManagerRpcService.getEgressActions(getEgressActionsForInterfaceInput);
            try {
                GetEgressActionsOutput egressActionsOutput = egressActionsOutputFuture.get().getResult();
                List<Action> outputAction = egressActionsOutput.getAction();
                return outputAction;
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error in RPC call getEgressActionsForInterface {}", e);
                return actions;
            }
        }
        return actions;
    }

    public static List<MatchInfo> getTunnelMatchesForServiceId(int elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[]{
                BigInteger.valueOf(elanTag)}));

        return mkMatches;
    }

    public static void removeTerminatingServiceAction(BigInteger destDpId, int serviceId) {
        ItmRpcService itmRpcService = elanServiceProvider.getItmRpcService();
        RemoveTerminatingServiceActionsInput input = new RemoveTerminatingServiceActionsInputBuilder().setDpnId(destDpId).setServiceId(serviceId).build();
        Future<RpcResult<Void>> futureObject = itmRpcService.removeTerminatingServiceActions(input);
        try {
            RpcResult<Void> result = futureObject.get();
            if (result.isSuccessful()) {
                logger.debug("Successfully completed removeTerminatingServiceActions");
            } else {
                logger.debug("Failure in removeTerminatingServiceAction RPC call");
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error in RPC call removeTerminatingServiceActions {}", e);
        }
    }

    public static void createTerminatingServiceActions(BigInteger destDpId, int serviceId, List<Action> actions) {
        ItmRpcService itmRpcService = elanServiceProvider.getItmRpcService();
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        mkInstructions.add(getApplyActionInstruction(actions));
        CreateTerminatingServiceActionsInput input = new CreateTerminatingServiceActionsInputBuilder().setDpnId(destDpId).setServiceId(serviceId).setInstruction(mkInstructions).build();

        itmRpcService.createTerminatingServiceActions(input);
    }
}
