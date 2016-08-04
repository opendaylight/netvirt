/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MDSALUtil.MdsalOp;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.CreateTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.CreateTerminatingServiceActionsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface.EtreeInterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTag;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaceForwardingEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanTagNameMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMacKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanUtils {

    private static ElanServiceProvider elanServiceProvider;
    private static final Logger LOG = LoggerFactory.getLogger(ElanUtils.class);

    public static void setElanServiceProvider(ElanServiceProvider serviceProvider) {
        elanServiceProvider = serviceProvider;
    }

    public static ElanServiceProvider getElanServiceProvider() {
        return elanServiceProvider;
    }

    public static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore operation", error);
        }
    };

    /**
     * Uses the IdManager to retrieve a brand new ElanTag.
     *
     * @param idManager
     *            the id manager
     * @param idKey
     *            the id key
     * @return the integer
     */
    public static Long retrieveNewElanTag(IdManagerService idManager, String idKey) {

        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(ElanConstants.ELAN_ID_POOL_NAME)
                .setIdKey(idKey).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().longValue();
            } else {
                LOG.warn("RPC Call to Allocate Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when Allocating Id", e);
        }
        return 0L;
    }

    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput releaseIdInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        Future<RpcResult<Void>> result = idManager.releaseId(releaseIdInput);
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        ReadOnlyTransaction tx = (broker != null) ? broker.newReadOnlyTransaction()
                : elanServiceProvider.getBroker().newReadOnlyTransaction();
        Optional<T> result = Optional.absent();
        try {
            CheckedFuture<Optional<T>, ReadFailedException> checkedFuture = tx.read(datastoreType, path);
            result = checkedFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceIdentifier() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class).build();
    }

    // elan-instances config container
    public static ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        Optional<ElanInstance> elanInstance = read(elanServiceProvider.getBroker(), LogicalDatastoreType.CONFIGURATION,
                elanIdentifierId);
        if (elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    // elan-interfaces Config Container
    public static ElanInterface getElanInterfaceByElanInterfaceName(String elanInterfaceName) {
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        Optional<ElanInterface> existingElanInterface = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.CONFIGURATION, elanInterfaceId);
        if (existingElanInterface.isPresent()) {
            return existingElanInterface.get();
        }
        return null;
    }

    public static EtreeInterface getEtreeInterfaceByElanInterfaceName(String elanInterfaceName) {
        ElanInterface elanInterface = ElanUtils.getElanInterfaceByElanInterfaceName(elanInterfaceName);
        return elanInterface.getAugmentation(EtreeInterface.class);
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    // elan-state Operational container
    public static Elan getElanByName(String elanInstanceName) {
        InstanceIdentifier<Elan> elanIdentifier = getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInstance = read(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL,
                elanIdentifier);
        if (elanInstance.isPresent()) {
            return elanInstance.get();
        }
        return null;
    }

    public static InstanceIdentifier<Elan> getElanInstanceOperationalDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class, new ElanKey(elanInstanceName)).build();
    }

    // grouping of forwarding-entries
    public static MacEntry getInterfaceMacEntriesOperationalDataPath(String interfaceName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> existingMacEntryId = getInterfaceMacEntriesIdentifierOperationalDataPath(
                interfaceName, physAddress);
        Optional<MacEntry> existingInterfaceMacEntry = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, existingMacEntryId);
        if (existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static MacEntry getInterfaceMacEntriesOperationalDataPathFromId(InstanceIdentifier identifier) {
        Optional<MacEntry> existingInterfaceMacEntry = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, identifier);
        if (existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static InstanceIdentifier<MacEntry> getInterfaceMacEntriesIdentifierOperationalDataPath(String interfaceName,
            PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class)
                .child(ElanInterfaceMac.class, new ElanInterfaceMacKey(interfaceName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();

    }

    // elan-forwarding-tables Operational container
    public static MacEntry getMacTableByElanName(String elanName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanName, physAddress);
        Optional<MacEntry> existingElanMacEntry = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, macId);
        if (existingElanMacEntry.isPresent()) {
            return existingElanMacEntry.get();
        }
        return null;
    }

    public static MacEntry getMacEntryFromElanMacId(InstanceIdentifier identifier) {
        Optional<MacEntry> existingInterfaceMacEntry = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, identifier);
        if (existingInterfaceMacEntry.isPresent()) {
            return existingInterfaceMacEntry.get();
        }
        return null;
    }

    public static InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName,
            PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    public static InstanceIdentifier<MacTable> getElanMacTableOperationalDataPath(String elanName) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName))
                .build();
    }

    // elan-interface-forwarding-entries Operational container
    public static ElanInterfaceMac getElanInterfaceMacByInterfaceName(String interfaceName) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = getElanInterfaceMacEntriesOperationalDataPath(
                interfaceName);
        Optional<ElanInterfaceMac> existingElanInterface = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
        if (existingElanInterface.isPresent()) {
            return existingElanInterface.get();
        }
        return null;
    }

    /**
     * Gets the elan interface mac addresses.
     *
     * @param interfaceName
     *            the interface name
     * @return the elan interface mac addresses
     */
    public static List<PhysAddress> getElanInterfaceMacAddresses(String interfaceName) {
        List<PhysAddress> macAddresses = new ArrayList<PhysAddress>();
        ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
            for (MacEntry macEntry : macEntries) {
                macAddresses.add(macEntry.getMacAddress());
            }
        }
        return macAddresses;
    }

    public static InstanceIdentifier<ElanInterfaceMac> getElanInterfaceMacEntriesOperationalDataPath(
            String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class)
                .child(ElanInterfaceMac.class, new ElanInterfaceMacKey(interfaceName)).build();
    }

    /**
     * Returns the list of Interfaces that belong to an Elan on an specific DPN.
     * Data retrieved from Elan's operational DS: elan-dpn-interfaces container
     *
     * @param elanInstanceName
     *            name of the Elan to which the interfaces must belong to
     * @param dpId
     *            Id of the DPN where the interfaces are located
     * @return the elan interface Info
     */
    public static DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, BigInteger dpId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                dpId);
        Optional<DpnInterfaces> elanDpnInterfaces = read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId);
        if (elanDpnInterfaces.isPresent()) {
            return elanDpnInterfaces.get();
        }
        return null;
    }

    /**
     * Returns the InstanceIdentifier that points to the Interfaces of an Elan
     * in a given DPN in the Operational DS. Data retrieved from Elans's
     * operational DS: dpn-interfaces list
     *
     * @param elanInstanceName
     *            name of the Elan to which the interfaces must belong to
     * @param dpId
     *            Id of the DPN where the interfaces are located
     * @return the elan dpn interface
     */
    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName,
            BigInteger dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
    }

    // elan-tag-name-map Operational Container
    public static ElanTagName getElanInfoByElanTag(long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanId);
        if (existingElanInfo.isPresent()) {
            return existingElanInfo.get();
        }
        return null;
    }

    public static EtreeLeafTagName getEtreeLeafTagByElanTag(long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanId);
        if (existingElanInfo.isPresent()) {
            ElanTagName elanTagName = existingElanInfo.get();
            EtreeLeafTagName etreeAugmentation = elanTagName.getAugmentation(EtreeLeafTagName.class);
            return etreeAugmentation;
        }
        return null;
    }

    public static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(long elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class, new ElanTagNameKey(elanTag))
                .build();
    }

    // interface-index-tag operational container
    public static Optional<IfIndexInterface> getInterfaceInfoByInterfaceTag(long interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        return ElanUtils.read(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, interfaceId);
    }

    public static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(long interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class)
                .child(IfIndexInterface.class, new IfIndexInterfaceKey((int) interfaceTag)).build();
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    public static ElanDpnInterfacesList getElanDpnInterfacesList(String elanName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (existingElanDpnInterfaces.isPresent()) {
            return existingElanDpnInterfaces.get();
        }
        return null;
    }

    /**
     * This method is useful get all ELAN participating CSS dpIds to install
     * program remote dmac entries and updating remote bc groups for tor
     * integration.
     *
     * @param elanInstanceName
     *            the elan instance name
     * @return list of dpIds
     */
    public static List<BigInteger> getParticipatingDPNsInElanInstance(String elanInstanceName) {
        List<BigInteger> dpIds = new ArrayList<BigInteger>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (!existingElanDpnInterfaces.isPresent()) {
            return dpIds;
        }
        List<DpnInterfaces> dpnInterfaces = existingElanDpnInterfaces.get().getDpnInterfaces();
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            dpIds.add(dpnInterface.getDpId());
        }
        return dpIds;
    }

    /**
     * To check given dpId is already present in Elan instance. This can be used
     * to program flow entry in external tunnel table when a new access port
     * added for first time into the ELAN instance
     *
     * @param dpId
     *            the dp id
     * @param elanInstanceName
     *            the elan instance name
     * @return true if dpId is already present, otherwise return false
     */
    public static boolean isDpnAlreadyPresentInElanInstance(BigInteger dpId, String elanInstanceName) {
        boolean isDpIdPresent = false;
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (!existingElanDpnInterfaces.isPresent()) {
            return isDpIdPresent;
        }
        List<DpnInterfaces> dpnInterfaces = existingElanDpnInterfaces.get().getDpnInterfaces();
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            if (dpnInterface.getDpId().equals(dpId)) {
                isDpIdPresent = true;
                break;
            }
        }
        return isDpIdPresent;
    }

    public static ElanDpnInterfaces getElanDpnInterfacesList() {
        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfaceId = InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .build();
        Optional<ElanDpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (existingElanDpnInterfaces.isPresent()) {
            return existingElanDpnInterfaces.get();
        }
        return null;
    }

    public static ElanForwardingTables getElanForwardingList() {
        InstanceIdentifier<ElanForwardingTables> elanForwardingTableId = InstanceIdentifier
                .builder(ElanForwardingTables.class).build();
        Optional<ElanForwardingTables> existingElanForwardingList = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanForwardingTableId);
        if (existingElanForwardingList.isPresent()) {
            return existingElanForwardingList.get();
        }
        return null;
    }

    /**
     * Gets the elan mac table.
     *
     * @param elanName
     *            the elan name
     * @return the elan mac table
     */
    public static MacTable getElanMacTable(String elanName) {
        InstanceIdentifier<MacTable> elanMacTableId = getElanMacTableOperationalDataPath(elanName);
        Optional<MacTable> existingElanMacTable = ElanUtils.read(elanServiceProvider.getBroker(),
                LogicalDatastoreType.OPERATIONAL, elanMacTableId);
        if (existingElanMacTable.isPresent()) {
            return existingElanMacTable.get();
        }
        return null;
    }

    public static long getElanLocalBCGID(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + (((elanTag % ElanConstants.ELAN_GID_MIN) * 2) - 1);
    }

    public static long getElanRemoteBCGID(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + (((elanTag % ElanConstants.ELAN_GID_MIN) * 2));
    }

    public static long getEtreeLeafLocalBCGID(long etreeLeafTag) {
        return ElanConstants.ELAN_GID_MIN + (((etreeLeafTag % ElanConstants.ELAN_GID_MIN) * 2) - 1);
    }

    public static long getEtreeLeafRemoteBCGID(long etreeLeafTag) {
        return ElanConstants.ELAN_GID_MIN + (((etreeLeafTag % ElanConstants.ELAN_GID_MIN) * 2));
    }

    public static BigInteger getElanMetadataLabel(long elanTag) {
        return (BigInteger.valueOf(elanTag)).shiftLeft(24);
    }

    public static BigInteger getElanMetadataLabel(long elanTag, boolean isSHFlagSet) {
        int shBit = (isSHFlagSet) ? 1 : 0;
        return (BigInteger.valueOf(elanTag)).shiftLeft(24).or(BigInteger.valueOf(shBit));
    }

    public static BigInteger getElanMetadataLabel(long elanTag, int lportTag) {
        return getElanMetadataLabel(elanTag).or(MetaDataUtil.getLportTagMetaData(lportTag));
    }

    public static BigInteger getElanMetadataMask() {
        return MetaDataUtil.METADATA_MASK_SERVICE.or(MetaDataUtil.METADATA_MASK_LPORT_TAG);
    }

    /**
     * Setting INTERNAL_TUNNEL_TABLE, SMAC, DMAC, UDMAC in this DPN and also in
     * other DPNs.
     *
     * @param elanInfo
     *            the elan info
     * @param interfaceInfo
     *            the interface info
     * @param macTimeout
     *            the mac timeout
     * @param macAddress
     *            the mac address
     * @param writeFlowGroupTx
     *            the flow group tx
     */
    public static void setupMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress, WriteTransaction writeFlowGroupTx) {
        synchronized (macAddress) {
            LOG.info("Acquired lock for mac : " + macAddress + ". Proceeding with install operation.");
            setupKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress, elanServiceProvider.getMdsalManager(),
                    writeFlowGroupTx);
            setupOrigDmacFlows(elanInfo, interfaceInfo, macAddress, elanServiceProvider.getMdsalManager(),
                    elanServiceProvider.getBroker(), writeFlowGroupTx);
        }
    }

    public static void setupDMacFlowonRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId,
            String macAddress, WriteTransaction writeFlowTx) {
        synchronized (macAddress) {
            LOG.info("Acquired lock for mac : " + macAddress + "Proceeding with install operation.");
            setupOrigDmacFlowsonRemoteDpn(elanInfo, interfaceInfo, dstDpId, macAddress, writeFlowTx);
        }
    }

    /**
     * Inserts a Flow in SMAC table to state that the MAC has already been
     * learnt.
     *
     * @param elanInfo
     * @param interfaceInfo
     * @param macTimeout
     * @param macAddress
     * @param mdsalApiManager
     */
    private static void setupKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress, IMdsalApiManager mdsalApiManager, WriteTransaction writeFlowGroupTx) {
        FlowEntity flowEntity = buildKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress);
        mdsalApiManager.addFlowToTx(flowEntity, writeFlowGroupTx);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Known Smac flow entry created for elan Name:{}, logical Interface port:{} and mac address:{}",
                    elanInfo.getElanInstanceName(), elanInfo.getDescription(), macAddress);
        }
    }

    public static FlowEntity buildKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress) {
        BigInteger dpId = interfaceInfo.getDpId();
        int lportTag = interfaceInfo.getInterfaceTag();
        long elanTag = getElanTag(elanInfo, interfaceInfo);
        // Matching metadata and eth_src fields
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanInfo.getElanTag(), lportTag), ElanUtils.getElanMetadataMask() }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_src, new String[] { macAddress }));
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_DMAC_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_SMAC_TABLE, dpId, lportTag, macAddress, elanTag), 20,
                elanInfo.getDescription(), (int) macTimeout, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_SMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);
        flowEntity.setStrictFlag(true);
        flowEntity.setSendFlowRemFlag(macTimeout != 0); // If Mac timeout is 0,
                                                        // the flow wont be
                                                        // deleted
                                                        // automatically, so no
                                                        // need to get notified
        return flowEntity;
    }

    private static Long getElanTag(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        EtreeInterface etreeInterface = ElanUtils
                .getEtreeInterfaceByElanInterfaceName(interfaceInfo.getInterfaceName());
        if (etreeInterface == null || etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            return elanInfo.getElanTag();
        } else { // Leaf
            EtreeInstance etreeInstance = elanInfo.getAugmentation(EtreeInstance.class);
            if (etreeInstance == null) {
                LOG.warn("EtreeInterface " + interfaceInfo.getInterfaceName() + " is connected to a non-Etree network: "
                        + elanInfo.getElanInstanceName());
                return elanInfo.getElanTag();
            } else {
                return etreeInstance.getEtreeLeafTagVal().getValue();
            }
        }
    }

    /**
     * Installs a Flow in INTERNAL_TUNNEL_TABLE of the affected DPN that sends
     * the packet through the specified interface if the tunnel_id matches the
     * interface's lportTag
     *
     * @param interfaceInfo
     *            the interface info
     * @param mdsalApiManager
     *            the mdsal API manager
     * @param writeFlowGroupTx
     *            the writeFLowGroup tx
     */
    public static void setupTermDmacFlows(InterfaceInfo interfaceInfo, IMdsalApiManager mdsalApiManager,
            WriteTransaction writeFlowGroupTx) {
        BigInteger dpId = interfaceInfo.getDpId();
        int lportTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getIntTunnelTableFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, lportTag), 5,
                String.format("%s:%d", "ITM Flow Entry ", lportTag), 0, 0,
                ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(lportTag)),
                getTunnelIdMatchForFilterEqualsLPortTag(lportTag),
                getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));
        mdsalApiManager.addFlowToTx(dpId, flow, writeFlowGroupTx);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Terminating service table flow entry created on dpn:{} for logical Interface port:{}", dpId,
                    interfaceInfo.getPortName());
        }
    }

    /**
     * Constructs the FlowName for flows installed in the Internal Tunnel Table,
     * consisting in tableId + elanTag.
     *
     * @param tableId
     *            table Id
     * @param elanTag
     *            elan Tag
     * @return the Internal tunnel
     */
    public static String getIntTunnelTableFlowRef(short tableId, int elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).toString();
    }

    /**
     * Constructs the Matches that checks that the tunnel_id field contains a
     * specific lportTag
     *
     * @param lportTag
     *            lportTag that must be checked against the tunnel_id field
     * @return the list of match Info
     */
    public static List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int lportTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] { BigInteger.valueOf(lportTag) }));
        return mkMatches;
    }

    /**
     * Constructs the Instructions that take the packet over a given interface
     *
     * @param ifName
     *            Name of the interface where the packet must be sent over. It
     *            can be a local interface or a tunnel interface (internal or
     *            external)
     * @return the Instruction
     */
    public static List<Instruction> getInstructionsInPortForOutGroup(String ifName) {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List<Action> actions = ElanUtils.getEgressActionsForInterface(ifName, /* tunnelKey */ null);

        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        return mkInstructions;
    }

    /**
     * Returns the list of Actions to be taken when sending the packet through
     * an Elan interface. Note that this interface can refer to an ElanInterface
     * where the Elan VM is attached to a DPN or an ITM tunnel interface where
     * Elan traffic can be sent through. In this latter case, the tunnelKey is
     * mandatory and it can hold serviceId for internal tunnels or the VNI for
     * external tunnels.
     *
     * @param ifName
     *            the if name
     * @param tunnelKey
     *            the tunnel key
     * @return the egress actions for interface
     */
    public static List<Action> getEgressActionsForInterface(String ifName, Long tunnelKey) {
        List<Action> listAction = new ArrayList<Action>();
        try {
            GetEgressActionsForInterfaceInput getEgressActionInput = new GetEgressActionsForInterfaceInputBuilder()
                    .setIntfName(ifName).setTunnelKey(tunnelKey).build();
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result = elanServiceProvider
                    .getInterfaceManagerRpcService().getEgressActionsForInterface(getEgressActionInput);
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName,
                        rpcResult.getErrors());
            } else {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actions = rpcResult
                        .getResult().getAction();
                listAction = actions;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listAction;
    }

    private static void setupOrigDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            IMdsalApiManager mdsalApiManager, DataBroker broker, WriteTransaction writeFlowGroupTx) {
        BigInteger dpId = interfaceInfo.getDpId();
        String ifName = interfaceInfo.getInterfaceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> elanDpns = getInvolvedDpnsInElan(elanInstanceName);
        if (elanDpns != null) {
            Long elanTag = getElanTag(elanInfo, interfaceInfo); // TODO might be
                                                                // bug and
                                                                // should call
                                                                // here just
                                                                // elanInfo.getElanTag()
            for (DpnInterfaces elanDpn : elanDpns) {
                if (elanDpn.getDpId().equals(dpId)) {
                    // On the local DPN set up a direct output flow
                    setupLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInstanceName, mdsalApiManager, ifTag,
                            writeFlowGroupTx);
                    LOG.debug(
                            "Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address:{} on dpn:{}",
                            elanInstanceName, interfaceInfo.getPortName(), macAddress, dpId);
                } else {
                    // Check for the Remote DPN present in Inventory Manager
                    if (isDpnPresent(elanDpn.getDpId())) {
                        // For remote DPNs a flow is needed to indicate that
                        // packets of this ELAN going to this MAC
                        // need to be forwarded through the appropiated ITM
                        // tunnel
                        setupRemoteDmacFlow(elanDpn.getDpId(), // srcDpn (the
                                                               // remote DPN in
                                                               // this case)
                                dpId, // dstDpn (the local DPN)
                                interfaceInfo.getInterfaceTag(), // lportTag of
                                                                 // the local
                                                                 // interface
                                elanTag, // identifier of the Elan
                                macAddress, // MAC to be programmed in remote
                                            // DPN
                                elanInstanceName, writeFlowGroupTx, ifName);
                        LOG.debug(
                                "Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address:{} on dpn:{}",
                                elanInstanceName, interfaceInfo.getPortName(), macAddress, elanDpn.getDpId());
                    }
                }
            }

            // TODO: Make sure that the same is performed against the
            // ElanDevices.
        }
    }

    private static void setupOrigDmacFlowsonRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            BigInteger dstDpId, String macAddress, WriteTransaction writeFlowTx) {
        BigInteger dpId = interfaceInfo.getDpId();
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        for (DpnInterfaces remoteFE : remoteFEs) {
            Long elanTag = elanInfo.getElanTag();
            if (remoteFE.getDpId().equals(dstDpId)) {
                // Check for the Remote DPN present in Inventory Manager
                setupRemoteDmacFlow(dstDpId, dpId, interfaceInfo.getInterfaceTag(), elanTag, macAddress,
                        elanInstanceName, writeFlowTx, interfaceInfo.getInterfaceName());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address {} on dpn:{}",
                            elanInstanceName, interfaceInfo.getPortName(), macAddress, remoteFE.getDpId());
                }
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<DpnInterfaces> getInvolvedDpnsInElan(String elanName) {
        List<DpnInterfaces> dpns = ElanInstanceManager.getElanInstanceManager(elanServiceProvider)
                .getElanDPNByName(elanName);
        if (dpns == null) {
            return Collections.emptyList();
        }
        return dpns;
    }

    private static void setupLocalDmacFlow(long elanTag, BigInteger dpId, String ifName, String macAddress,
            String displayName, IMdsalApiManager mdsalApiManager, long ifTag, WriteTransaction writeFlowGroupTx) {
        Flow flowEntity = buildLocalDmacFlowEntry(elanTag, dpId, ifName, macAddress, displayName, ifTag);
        mdsalApiManager.addFlowToTx(dpId, flowEntity, writeFlowGroupTx);
        installEtreeLocalDmacFlow(elanTag, dpId, ifName, macAddress, displayName, mdsalApiManager, ifTag,
                writeFlowGroupTx);
    }

    private static void installEtreeLocalDmacFlow(long elanTag, BigInteger dpId, String ifName, String macAddress,
            String displayName, IMdsalApiManager mdsalApiManager, long ifTag, WriteTransaction writeFlowGroupTx) {
        EtreeInterface etreeInterface = ElanUtils.getEtreeInterfaceByElanInterfaceName(ifName);
        if (etreeInterface != null) {
            if (etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
                EtreeLeafTagName etreeTagName = ElanUtils.getEtreeLeafTagByElanTag(elanTag);
                if (etreeTagName == null) {
                    LOG.warn("Interface " + ifName + " seems like it belongs to Etree but etreeTagName from elanTag "
                            + elanTag + " is null.");
                } else {
                    Flow flowEntity = buildLocalDmacFlowEntry(etreeTagName.getEtreeLeafTag().getValue(), dpId, ifName,
                            macAddress, displayName, ifTag);
                    mdsalApiManager.addFlowToTx(dpId, flowEntity, writeFlowGroupTx);
                }
            }
        }
    }

    public static String getKnownDynamicmacFlowRef(short tableId, BigInteger dpId, long lporTag, String macAddress,
            long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).append(dpId).append(lporTag).append(macAddress)
                .toString();
    }

    public static String getKnownDynamicmacFlowRef(short tableId, BigInteger dpId, BigInteger remoteDpId,
            String macAddress, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).append(dpId).append(remoteDpId).append(macAddress)
                .toString();
    }

    private static String getKnownDynamicmacFlowRef(short elanDmacTable, BigInteger dpId, String extDeviceNodeId,
            String dstMacAddress, long elanTag, boolean shFlag) {
        return new StringBuffer().append(elanDmacTable).append(elanTag).append(dpId).append(extDeviceNodeId)
                .append(dstMacAddress).append(shFlag).toString();
    }

    /**
     * Builds the flow to be programmed in the DMAC table of the local DPN (that
     * is, where the MAC is attached to). This flow consists in:
     *
     * Match: + elanTag in metadata + packet goes to a MAC locally attached
     * Actions: + optionally, pop-vlan + set-vlan-id + output to ifName's
     * portNumber
     *
     * @param elanTag
     *            the elan tag
     * @param dpId
     *            the dp id
     * @param ifName
     *            the if name
     * @param macAddress
     *            the mac address
     * @param displayName
     *            the display name
     * @param ifTag
     *            the if tag
     * @return the flow
     */
    public static Flow buildLocalDmacFlowEntry(long elanTag, BigInteger dpId, String ifName, String macAddress,
            String displayName, long ifTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { ElanUtils.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { macAddress }));

        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        List<Action> actions = getEgressActionsForInterface(ifName, /* tunnelKey */ null);
        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, ifTag, macAddress, elanTag), 20,
                displayName, 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches,
                mkInstructions);

        return flow;
    }

    public static void setupRemoteDmacFlow(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag,
            String macAddress, String displayName, WriteTransaction writeFlowGroupTx, String interfaceName) {
        Flow flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTag, elanTag, macAddress, displayName);
        elanServiceProvider.getMdsalManager().addFlowToTx(srcDpId, flowEntity, writeFlowGroupTx);
        setupEtreeRemoteDmacFlow(srcDpId, destDpId, lportTag, elanTag, macAddress, displayName, interfaceName,
                writeFlowGroupTx);
    }

    private static void setupEtreeRemoteDmacFlow(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag,
            String macAddress, String displayName, String interfaceName, WriteTransaction writeFlowGroupTx) {
        Flow flowEntity;
        EtreeInterface etreeInterface = ElanUtils.getEtreeInterfaceByElanInterfaceName(interfaceName);
        if (etreeInterface != null) {
            if (etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
                EtreeLeafTagName etreeTagName = ElanUtils.getEtreeLeafTagByElanTag(elanTag);
                if (etreeTagName == null) {
                    LOG.warn("Interface " + interfaceName
                            + " seems like it belongs to Etree but etreeTagName from elanTag " + elanTag + " is null.");
                } else {
                    flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTag,
                            etreeTagName.getEtreeLeafTag().getValue(), macAddress, displayName);
                    elanServiceProvider.getMdsalManager().addFlowToTx(srcDpId, flowEntity, writeFlowGroupTx);
                }
            }
        }
    }

    /**
     * Builds a Flow to be programmed in a remote DPN's DMAC table. This flow
     * consists in: Match: + elanTag in packet's metadata + packet going to a
     * MAC known to be located in another DPN Actions: + set_tunnel_id(lportTag)
     * + output ITM internal tunnel interface with the other DPN
     *
     * @param srcDpId
     *            the src Dpn Id
     * @param destDpId
     *            dest Dp Id
     * @param lportTag
     *            lport Tag
     * @param elanTag
     *            elan Tag
     * @param macAddress
     *            macAddress
     * @param displayName
     *            display Name
     * @return the flow remote Dmac
     */
    public static Flow buildRemoteDmacFlowEntry(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag,
            String macAddress, String displayName) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] { ElanUtils.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { macAddress }));

        List<Instruction> mkInstructions = new ArrayList<Instruction>();

        // List of Action for the provided Source and Destination DPIDs
        try {
            List<Action> actions = getInternalTunnelItmEgressAction(srcDpId, destDpId, lportTag);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Interface Not Found exception");
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, srcDpId, destDpId, macAddress, elanTag),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;

    }

    public static void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, MacEntry macEntry,
            WriteTransaction deleteFlowGroupTx) {
        if (elanInfo == null || interfaceInfo == null) {
            return;
        }
        String macAddress = macEntry.getMacAddress().getValue();
        synchronized (macAddress) {
            LOG.info("Acquired lock for mac : " + macAddress + "Proceeding with remove operation.");
            deleteMacFlows(elanInfo, interfaceInfo, macAddress, /* alsoDeleteSMAC */ true, deleteFlowGroupTx);
        }
    }

    public static void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, WriteTransaction deleteFlowGroupTx) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        BigInteger srcdpId = interfaceInfo.getDpId();
        boolean isFlowsRemovedInSrcDpn = false;
        for (DpnInterfaces dpnInterface : remoteFEs) {
            Long elanTag = elanInfo.getElanTag();
            BigInteger dstDpId = dpnInterface.getDpId();
            if (executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId, elanTag,
                    dstDpId, deleteFlowGroupTx)) {
                isFlowsRemovedInSrcDpn = true;
            }
            executeEtreeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId, elanTag,
                    dstDpId, deleteFlowGroupTx);
        }
        if (!isFlowsRemovedInSrcDpn) {
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, deleteFlowGroupTx);
        }
    }

    private static void executeEtreeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress, boolean deleteSmac,
            String elanInstanceName, BigInteger srcdpId, Long elanTag, BigInteger dstDpId,
            WriteTransaction deleteFlowGroupTx) {
        EtreeLeafTagName etreeLeafTag = ElanUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), dstDpId, deleteFlowGroupTx);
        }
    }

    private static boolean executeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress, boolean deleteSmac,
            String elanInstanceName, BigInteger srcdpId, Long elanTag, BigInteger dstDpId,
            WriteTransaction deleteFlowGroupTx) {
        boolean isFlowsRemovedInSrcDpn = false;
        if (dstDpId.equals(srcdpId)) {
            isFlowsRemovedInSrcDpn = true;
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, deleteFlowGroupTx);
        } else if (isDpnPresent(dstDpId)) {
            elanServiceProvider.getMdsalManager()
                    .removeFlowToTx(dstDpId,
                            MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE, getKnownDynamicmacFlowRef(
                                    NwConstants.ELAN_DMAC_TABLE, dstDpId, srcdpId, macAddress, elanTag)),
                            deleteFlowGroupTx);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Dmac flow entry deleted for elan:{}, logical interface port:{} and mac address:{} on dpn:{}",
                        elanInstanceName, interfaceInfo.getPortName(), macAddress, dstDpId);
            }
        }
        return isFlowsRemovedInSrcDpn;
    }

    private static void deleteSmacAndDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, WriteTransaction deleteFlowGroupTx) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        BigInteger srcdpId = interfaceInfo.getDpId();
        Long elanTag = elanInfo.getElanTag();
        if (deleteSmac) {
            elanServiceProvider.getMdsalManager()
                    .removeFlowToTx(srcdpId,
                            MDSALUtil.buildFlow(NwConstants.ELAN_SMAC_TABLE, getKnownDynamicmacFlowRef(
                                    NwConstants.ELAN_SMAC_TABLE, srcdpId, ifTag, macAddress, elanTag)),
                            deleteFlowGroupTx);
        }
        elanServiceProvider.getMdsalManager().removeFlowToTx(srcdpId,
                MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                        getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, srcdpId, ifTag, macAddress, elanTag)),
                deleteFlowGroupTx);
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "All the required flows deleted for elan:{}, logical Interface port:{} and mac address:{} on dpn:{}",
                    elanInstanceName, interfaceInfo.getPortName(), macAddress, srcdpId);
        }
    }

    /**
     * Updates the Elan information in the Operational DS. It also updates the
     * ElanInstance in the Config DS by setting the adquired elanTag.
     *
     * @param broker
     *            the broker
     * @param idManager
     *            the id manager
     * @param elanInstanceAdded
     *            the elan instance added
     * @param tx
     *            transaction
     */
    public static void updateOperationalDataStore(DataBroker broker, IdManagerService idManager,
            ElanInstance elanInstanceAdded, List<String> elanInterfaces, WriteTransaction tx) {
        String elanInstanceName = elanInstanceAdded.getElanInstanceName();
        Long elanTag = elanInstanceAdded.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = ElanUtils.retrieveNewElanTag(idManager, elanInstanceName);
        }
        Elan elanInfo = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(elanInterfaces)
                .setKey(new ElanKey(elanInstanceName)).build();

        // Add the ElanState in the elan-state operational data-store
        tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName),
                elanInfo, true);

        // Add the ElanMacTable in the elan-mac-table operational data-store
        MacTable elanMacTable = new MacTableBuilder().setKey(new MacTableKey(elanInstanceName)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanInstanceName),
                elanMacTable, true);

        ElanTagNameBuilder elanTagNameBuilder = new ElanTagNameBuilder().setElanTag(elanTag)
                .setKey(new ElanTagNameKey(elanTag)).setName(elanInstanceName);
        long etreeLeafTag = -1;
        if (isEtreeInstance(elanInstanceAdded)) {
            etreeLeafTag = ElanUtils.retrieveNewElanTag(idManager, elanInstanceName + ElanConstants.LEAVES_POSTFIX);
            EtreeLeafTagName etreeLeafTagName = new EtreeLeafTagNameBuilder()
                    .setEtreeLeafTag(new EtreeLeafTag(etreeLeafTag)).build();
            elanTagNameBuilder.addAugmentation(EtreeLeafTagName.class, etreeLeafTagName);
            addTheLeafTagAsElanTag(broker, elanInstanceName, etreeLeafTag, tx);
        }
        ElanTagName elanTagName = elanTagNameBuilder.build();

        // Add the ElanTag to ElanName in the elan-tag-name Operational
        // data-store
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag), elanTagName);

        // Updates the ElanInstance Config DS by setting the just acquired
        // elanTag
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                .setDescription(elanInstanceAdded.getDescription())
                .setMacTimeout(elanInstanceAdded.getMacTimeout() == null ? ElanConstants.DEFAULT_MAC_TIME_OUT
                        : elanInstanceAdded.getMacTimeout())
                .setKey(elanInstanceAdded.getKey()).setElanTag(elanTag);
        if (isEtreeInstance(elanInstanceAdded)) {
            EtreeInstance etreeInstance = new EtreeInstanceBuilder().setEtreeLeafTagVal(new EtreeLeafTag(etreeLeafTag))
                    .build();
            elanInstanceBuilder.addAugmentation(EtreeInstance.class, etreeInstance);
        }
        ElanInstance elanInstanceWithTag = elanInstanceBuilder.build();
        tx.merge(LogicalDatastoreType.CONFIGURATION, getElanInstanceConfigurationDataPath(elanInstanceName),
                elanInstanceWithTag, true);
    }

    private static void addTheLeafTagAsElanTag(DataBroker broker, String elanInstanceName, long etreeLeafTag,
            WriteTransaction tx) {
        ElanTagName etreeTagAsElanTag = new ElanTagNameBuilder().setElanTag(etreeLeafTag)
                .setKey(new ElanTagNameKey(etreeLeafTag)).setName(elanInstanceName).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanInfoEntriesOperationalDataPath(etreeLeafTag), etreeTagAsElanTag);
    }

    private static boolean isEtreeInstance(ElanInstance elanInstanceAdded) {
        return elanInstanceAdded.getAugmentation(EtreeInstance.class) != null;
    }

    public static boolean isDpnPresent(BigInteger dpnId) {
        String dpn = String.format("%s:%s", "openflow", dpnId);
        NodeId nodeId = new NodeId(dpn);

        InstanceIdentifier<Node> node = InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId))
                .build();
        Optional<Node> nodePresent = read(elanServiceProvider.getBroker(), LogicalDatastoreType.OPERATIONAL, node);
        return (nodePresent.isPresent());
    }

    public static ServicesInfo getServiceInfo(String elanInstanceName, long elanTag, String interfaceName) {
        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanUtils.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.ELAN_SMAC_TABLE, ++instructionKey));

        short serviceIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
        ServicesInfo serviceInfo = InterfaceServiceUtil.buildServiceInfo(
                String.format("%s.%s", elanInstanceName, interfaceName), serviceIndex, priority,
                NwConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
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
            LOG.error("Error writing to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static InstanceIdentifier<BoundServices> buildServiceId(String vpnInterfaceName, short serviceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(vpnInterfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }

    /**
     * Builds the list of actions to be taken when sending the packet over a
     * VxLan Tunnel Interface, such as setting the tunnel_id field, the vlanId
     * if proceeds and output the packet over the right port.
     *
     * @param tunnelIfaceName
     *            the tunnel iface name
     * @param tunnelKey
     *            the tunnel key
     * @return the list
     */
    public static List<Action> buildTunnelItmEgressActions(String tunnelIfaceName, Long tunnelKey) {
        if (tunnelIfaceName != null && !tunnelIfaceName.isEmpty()) {
            return buildItmEgressActions(tunnelIfaceName, tunnelKey);
        }

        return Collections.emptyList();
    }

    /**
     * Builds the list of actions to be taken when sending the packet over
     * external port such as tunnel, physical port etc.
     *
     * @param interfaceName
     *            the interface name
     * @param tunnelKey
     *            can be VNI for VxLAN tunnel interfaces, Gre Key for GRE
     *            tunnels, etc.
     * @return the list
     */
    public static List<Action> buildItmEgressActions(String interfaceName, Long tunnelKey) {
        List<Action> result = Collections.emptyList();
        GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                .setIntfName(interfaceName).setTunnelKey(tunnelKey).build();

        Future<RpcResult<GetEgressActionsForInterfaceOutput>> egressActionsOutputFuture = elanServiceProvider
                .getInterfaceManagerRpcService().getEgressActionsForInterface(getEgressActInput);
        try {
            if (egressActionsOutputFuture.get().isSuccessful()) {
                GetEgressActionsForInterfaceOutput egressActionsOutput = egressActionsOutputFuture.get().getResult();
                result = egressActionsOutput.getAction();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getEgressActionsForInterface {}", e);
        }

        if (result == null || result.size() == 0) {
            LOG.warn("Could not build Egress actions for interface {} and tunnelId {}", interfaceName, tunnelKey);
        }
        return result;
    }

    /**
     * Builds the list of actions to be taken when sending the packet over an
     * external VxLan tunnel interface, such as stamping the VNI on the VxLAN
     * header, setting the vlanId if it proceeds and output the packet over the
     * right port.
     *
     * @param srcDpnId
     *            Dpn where the tunnelInterface is located
     * @param torNode
     *            NodeId of the ExternalDevice where the packet must be sent to.
     * @param vni
     *            Vni to be stamped on the VxLAN Header.
     * @return the external itm egress action
     */
    public static List<Action> getExternalTunnelItmEgressAction(BigInteger srcDpnId, NodeId torNode, long vni) {
        List<Action> result = Collections.emptyList();

        GetExternalTunnelInterfaceNameInput input = new GetExternalTunnelInterfaceNameInputBuilder()
                .setDestinationNode(torNode.getValue()).setSourceNode(srcDpnId.toString())
                .setTunnelType(TunnelTypeVxlan.class).build();
        Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = elanServiceProvider.getItmRpcService()
                .getExternalTunnelInterfaceName(input);
        try {
            if (output.get().isSuccessful()) {
                GetExternalTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
                String tunnelIfaceName = tunnelInterfaceNameOutput.getInterfaceName();
                if (LOG.isDebugEnabled())
                    LOG.debug("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelIfaceName);

                result = buildTunnelItmEgressActions(tunnelIfaceName, vni);
            }

        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {}", e);
        }

        return result;
    }

    /**
     * Builds the list of actions to be taken when sending the packet over an
     * internal VxLan tunnel interface, such as setting the serviceTag on the
     * VNI field of the VxLAN header, setting the vlanId if it proceeds and
     * output the packet over the right port.
     *
     * @param sourceDpnId
     *            Dpn where the tunnelInterface is located
     * @param destinationDpnId
     *            Dpn where the packet must be sent to. It is used here in order
     *            to select the right tunnel interface.
     * @param serviceTag
     *            serviceId to be sent on the VxLAN header.
     * @return the internal itm egress action
     */
    public static List<Action> getInternalTunnelItmEgressAction(BigInteger sourceDpnId, BigInteger destinationDpnId,
            long serviceTag) {
        List<Action> result = Collections.emptyList();

        LOG.debug("In getInternalItmEgressAction Action source {}, destination {}, elanTag {}", sourceDpnId,
                destinationDpnId, serviceTag);
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        GetTunnelInterfaceNameInput input = new GetTunnelInterfaceNameInputBuilder()
                .setDestinationDpid(destinationDpnId).setSourceDpid(sourceDpnId).setTunnelType(tunType).build();
        Future<RpcResult<GetTunnelInterfaceNameOutput>> output = elanServiceProvider.getItmRpcService()
                .getTunnelInterfaceName(input);
        try {
            if (output.get().isSuccessful()) {
                GetTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
                String tunnelIfaceName = tunnelInterfaceNameOutput.getInterfaceName();
                LOG.debug("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelIfaceName);

                result = buildTunnelItmEgressActions(tunnelIfaceName, serviceTag);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {}", e);
        }

        return result;
    }

    /**
     * Build the list of actions to be taken when sending the packet to external
     * (physical) port
     *
     * @param interfaceName
     *            Interface name
     * @return the external port itm egress actions
     */
    public static List<Action> getExternalPortItmEgressAction(String interfaceName) {
        return buildItmEgressActions(interfaceName, null);
    }

    public static List<MatchInfo> getTunnelMatchesForServiceId(int elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] { BigInteger.valueOf(elanTag) }));

        return mkMatches;
    }

    public static void removeTerminatingServiceAction(BigInteger destDpId, int serviceId) {
        RemoveTerminatingServiceActionsInput input = new RemoveTerminatingServiceActionsInputBuilder()
                .setDpnId(destDpId).setServiceId(serviceId).build();
        Future<RpcResult<Void>> futureObject = elanServiceProvider.getItmRpcService()
                .removeTerminatingServiceActions(input);
        try {
            RpcResult<Void> result = futureObject.get();
            if (result.isSuccessful()) {
                LOG.debug("Successfully completed removeTerminatingServiceActions");
            } else {
                LOG.debug("Failure in removeTerminatingServiceAction RPC call");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call removeTerminatingServiceActions {}", e);
        }
    }

    public static void createTerminatingServiceActions(BigInteger destDpId, int serviceId, List<Action> actions) {
        List<Instruction> mkInstructions = new ArrayList<Instruction>();
        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        CreateTerminatingServiceActionsInput input = new CreateTerminatingServiceActionsInputBuilder()
                .setDpnId(destDpId).setServiceId(serviceId).setInstruction(mkInstructions).build();

        elanServiceProvider.getItmRpcService().createTerminatingServiceActions(input);
    }

    public static TunnelList buildInternalTunnel(DataBroker broker) {
        InstanceIdentifier<TunnelList> tunnelListInstanceIdentifier = InstanceIdentifier.builder(TunnelList.class)
                .build();
        Optional<TunnelList> tunnelList = read(broker, LogicalDatastoreType.CONFIGURATION,
                tunnelListInstanceIdentifier);
        if (tunnelList.isPresent()) {
            return tunnelList.get();
        }
        return null;
    }

    /**
     * Gets the external tunnel.
     *
     * @param sourceDevice
     *            the source device
     * @param destinationDevice
     *            the destination device
     * @param datastoreType
     *            the datastore type
     * @return the external tunnel
     */
    public static ExternalTunnel getExternalTunnel(String sourceDevice, String destinationDevice,
            LogicalDatastoreType datastoreType) {
        ExternalTunnel externalTunnel = null;
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        InstanceIdentifier<ExternalTunnel> iid = InstanceIdentifier.builder(ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(destinationDevice, sourceDevice, tunType)).build();
        Optional<ExternalTunnel> tunnelList = read(elanServiceProvider.getBroker(), datastoreType, iid);
        if (tunnelList.isPresent()) {
            externalTunnel = tunnelList.get();
        }
        return externalTunnel;
    }

    /**
     * Gets the external tunnel.
     *
     * @param interfaceName
     *            the interface name
     * @param datastoreType
     *            the datastore type
     * @return the external tunnel
     */
    public static ExternalTunnel getExternalTunnel(String interfaceName, LogicalDatastoreType datastoreType) {
        ExternalTunnel externalTunnel = null;
        List<ExternalTunnel> externalTunnels = getAllExternalTunnels(datastoreType);
        for (ExternalTunnel tunnel : externalTunnels) {
            if (StringUtils.equalsIgnoreCase(interfaceName, tunnel.getTunnelInterfaceName())) {
                externalTunnel = tunnel;
                break;
            }
        }
        return externalTunnel;
    }

    /**
     * Gets the all external tunnels.
     *
     * @param datastoreType
     *            the data store type
     * @return the all external tunnels
     */
    public static List<ExternalTunnel> getAllExternalTunnels(LogicalDatastoreType datastoreType) {
        List<ExternalTunnel> result = null;
        InstanceIdentifier<ExternalTunnelList> iid = InstanceIdentifier.builder(ExternalTunnelList.class).build();
        Optional<ExternalTunnelList> tunnelList = read(elanServiceProvider.getBroker(), datastoreType, iid);
        if (tunnelList.isPresent()) {
            result = tunnelList.get().getExternalTunnel();
        }
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    /**
     * Installs a Flow in a DPN's DMAC table. The Flow is for a MAC that is
     * connected remotely in another CSS and accessible through an internal
     * tunnel. It also installs the flow for dropping the packet if it came over
     * an ITM tunnel (that is, if the Split-Horizon flag is set)
     *
     * @param localDpId
     *            Id of the DPN where the MAC Addr is accessible locally
     * @param remoteDpId
     *            Id of the DPN where the flow must be installed
     * @param lportTag
     *            lportTag of the interface where the mac is connected to.
     * @param elanTag
     *            Identifier of the ELAN
     * @param macAddress
     *            MAC to be installed in remoteDpId's DMAC table
     * @param displayName
     *            the display name
     */
    public static void installDmacFlowsToInternalRemoteMac(BigInteger localDpId, BigInteger remoteDpId, long lportTag,
            long elanTag, String macAddress, String displayName) {
        Flow flow = buildDmacFlowForInternalRemoteMac(localDpId, remoteDpId, lportTag, elanTag, macAddress,
                displayName);
        elanServiceProvider.getMdsalManager().installFlow(remoteDpId, flow);
    }

    /**
     * Installs a Flow in the specified DPN's DMAC table. The flow is for a MAC
     * that is connected remotely in an External Device (TOR) and that is
     * accessible through an external tunnel. It also installs the flow for
     * dropping the packet if it came over an ITM tunnel (that is, if the
     * Split-Horizon flag is set)
     *
     * @param dpnId
     *            Id of the DPN where the flow must be installed
     * @param extDeviceNodeId
     *            the ext device node id
     * @param elanTag
     *            the elan tag
     * @param vni
     *            the vni
     * @param macAddress
     *            the mac address
     * @param displayName
     *            the display name
     *
     * @return the dmac flows
     */
    public static List<ListenableFuture<Void>> installDmacFlowsToExternalRemoteMac(BigInteger dpnId,
            String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (macAddress) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                    displayName);
            futures.add(elanServiceProvider.getMdsalManager().installFlow(dpnId, flow));

            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress);
            futures.add(elanServiceProvider.getMdsalManager().installFlow(dpnId, dropFlow));
            installEtreeDmacFlowsToExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress, displayName,
                    interfaceName, futures);
        }
        return futures;
    }

    private static void installEtreeDmacFlowsToExternalRemoteMac(BigInteger dpnId, String extDeviceNodeId, Long elanTag,
            Long vni, String macAddress, String displayName, String interfaceName,
            List<ListenableFuture<Void>> futures) {
        EtreeLeafTagName etreeLeafTag = ElanUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            buildEtreeDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress, futures,
                    etreeLeafTag);
            buildEtreeDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, vni, macAddress, displayName, interfaceName,
                    futures, etreeLeafTag);
        }
    }

    private static void buildEtreeDmacFlowForExternalRemoteMac(BigInteger dpnId, String extDeviceNodeId, Long vni,
            String macAddress, String displayName, String interfaceName, List<ListenableFuture<Void>> futures,
            EtreeLeafTagName etreeLeafTag) {
        boolean isRoot = false;
        if (interfaceName == null) {
            isRoot = true;
        } else {
            EtreeInterface etreeInterface = ElanUtils.getEtreeInterfaceByElanInterfaceName(interfaceName);
            if (etreeInterface != null) {
                if (etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
                    isRoot = true;
                }
            }
        }
        if (isRoot) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), vni, macAddress, displayName);
            futures.add(elanServiceProvider.getMdsalManager().installFlow(dpnId, flow));
        }
    }

    private static void buildEtreeDmacFlowDropIfPacketComingFromTunnel(BigInteger dpnId, String extDeviceNodeId,
            Long elanTag, String macAddress, List<ListenableFuture<Void>> futures, EtreeLeafTagName etreeLeafTag) {
        if (etreeLeafTag != null) {
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), macAddress);
            futures.add(elanServiceProvider.getMdsalManager().installFlow(dpnId, dropFlow));
        }
    }

    public static List<MatchInfo> buildMatchesForElanTagShFlagAndDstMac(long elanTag, boolean shFlag, String macAddr) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                ElanUtils.getElanMetadataLabel(elanTag, shFlag), MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG }));
        mkMatches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { macAddr }));

        return mkMatches;
    }

    /**
     * Builds a Flow to be programmed in a DPN's DMAC table. This method must be
     * used when the MAC is located in an External Device (TOR). The flow
     * matches on the specified MAC and 1) sends the packet over the CSS-TOR
     * tunnel if SHFlag is not set, or 2) drops it if SHFlag is set (what means
     * the packet came from an external tunnel)
     *
     * @param dpId
     *            DPN whose DMAC table is going to be modified
     * @param extDeviceNodeId
     *            Hwvtep node where the mac is attached to
     * @param elanTag
     *            ElanId to which the MAC is being added to
     * @param vni
     *            the vni
     * @param dstMacAddress
     *            The mac address to be programmed
     * @param displayName
     *            the display name
     * @return the flow
     */
    public static Flow buildDmacFlowForExternalRemoteMac(BigInteger dpId, String extDeviceNodeId, long elanTag,
            Long vni, String dstMacAddress, String displayName) {
        List<MatchInfo> mkMatches = buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ false, dstMacAddress);
        List<Instruction> mkInstructions = new ArrayList<>();
        try {
            List<Action> actions = getExternalTunnelItmEgressAction(dpId, new NodeId(extDeviceNodeId), vni);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get Egress Actions for DpId={}  externalNode={}", dpId, extDeviceNodeId);
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, dstMacAddress, elanTag,
                        false),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    /**
     * Builds the flow that drops the packet if it came through an external
     * tunnel, that is, if the Split-Horizon flag is set.
     *
     * @param dpnId
     *            DPN whose DMAC table is going to be modified
     * @param extDeviceNodeId
     *            Hwvtep node where the mac is attached to
     * @param elanTag
     *            ElanId to which the MAC is being added to
     * @param dstMacAddress
     *            The mac address to be programmed
     * @param displayName
     * @return
     */
    private static Flow buildDmacFlowDropIfPacketComingFromTunnel(BigInteger dpnId, String extDeviceNodeId,
            Long elanTag, String dstMacAddress) {
        List<MatchInfo> mkMatches = buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ true, dstMacAddress);
        List<Instruction> mkInstructions = MDSALUtil.buildInstructionsDrop();
        String flowId = getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpnId, extDeviceNodeId, dstMacAddress,
                elanTag, true);
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE, flowId, 20, /* prio */
                "Drop", 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    private static String getDmacDropFlowId(Long elanTag, String dstMacAddress) {
        return new StringBuilder(NwConstants.ELAN_DMAC_TABLE).append(elanTag).append(dstMacAddress).append("Drop")
                .toString();
    }

    /**
     * Builds a Flow to be programmed in a remote DPN's DMAC table. This method
     * must be used when the MAC is located in another CSS.
     *
     * This flow consists in: Match: + elanTag in packet's metadata + packet
     * going to a MAC known to be located in another DPN Actions: +
     * set_tunnel_id(lportTag) + output on ITM internal tunnel interface with
     * the other DPN
     *
     * @param localDpId
     *            the local dp id
     * @param remoteDpId
     *            the remote dp id
     * @param lportTag
     *            the lport tag
     * @param elanTag
     *            the elan tag
     * @param macAddress
     *            the mac address
     * @param displayName
     *            the display name
     * @return the flow
     */
    public static Flow buildDmacFlowForInternalRemoteMac(BigInteger localDpId, BigInteger remoteDpId, long lportTag,
            long elanTag, String macAddress, String displayName) {
        List<MatchInfo> mkMatches = buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ false, macAddress);

        List<Instruction> mkInstructions = new ArrayList<Instruction>();

        try {
            // List of Action for the provided Source and Destination DPIDs
            List<Action> actions = getInternalTunnelItmEgressAction(localDpId, remoteDpId, lportTag);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get Egress Actions for localDpId={}  remoteDpId={}   lportTag={}", localDpId,
                    remoteDpId, lportTag);
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, localDpId, remoteDpId, macAddress, elanTag),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;

    }

    /**
     * Installs or removes flows in DMAC table for MACs that are/were located in
     * an external Elan Device.
     *
     * @param dpId
     *            Id of the DPN where the DMAC table is going to be modified
     * @param extNodeId
     *            Id of the External Device where the MAC is located
     * @param elanTag
     *            Id of the ELAN
     * @param vni
     *            VNI of the LogicalSwitch to which the MAC belongs to, and that
     *            is associated with the ELAN
     * @param macAddress
     *            the mac address
     * @param elanInstanceName
     *            the elan instance name
     * @param addOrRemove
     *            Indicates if flows must be installed or removed.
     * @see org.opendaylight.genius.mdsalutil.MDSALUtil.MdsalOp
     */
    public static void setupDmacFlowsToExternalRemoteMac(BigInteger dpId, String extNodeId, Long elanTag, Long vni,
            String macAddress, String elanInstanceName, MdsalOp addOrRemove, String interfaceName) {
        if (addOrRemove == MdsalOp.CREATION_OP) {
            ElanUtils.installDmacFlowsToExternalRemoteMac(dpId, extNodeId, elanTag, vni, macAddress, elanInstanceName,
                    interfaceName);
        } else if (addOrRemove == MdsalOp.REMOVAL_OP) {
            ElanUtils.deleteDmacFlowsToExternalMac(elanTag, dpId, extNodeId, macAddress);
        }
    }

    /**
     * Delete dmac flows to external mac.
     *
     * @param elanTag
     *            the elan tag
     * @param dpId
     *            the dp id
     * @param extDeviceNodeId
     *            the ext device node id
     * @param macToRemove
     *            the mac to remove
     * @return dmac flow
     */
    public static List<ListenableFuture<Void>> deleteDmacFlowsToExternalMac(long elanTag, BigInteger dpId,
            String extDeviceNodeId, String macToRemove) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (macToRemove) {
            // Removing the flows that sends the packet on an external tunnel
            removeFlowThatSendsThePacketOnAnExternalTunnel(elanTag, dpId, extDeviceNodeId, macToRemove, futures);

            // And now removing the drop flow
            removeTheDropFlow(elanTag, dpId, extDeviceNodeId, macToRemove, futures);

            deleteEtreeDmacFlowsToExternalMac(elanTag, dpId, extDeviceNodeId, macToRemove, futures);
        }
        return futures;
    }

    private static void deleteEtreeDmacFlowsToExternalMac(long elanTag, BigInteger dpId, String extDeviceNodeId,
            String macToRemove, List<ListenableFuture<Void>> futures) {
        EtreeLeafTagName etreeLeafTag = ElanUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            removeFlowThatSendsThePacketOnAnExternalTunnel(etreeLeafTag.getEtreeLeafTag().getValue(), dpId,
                    extDeviceNodeId, macToRemove, futures);
            removeTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue(), dpId, extDeviceNodeId, macToRemove, futures);
        }
    }

    private static void removeTheDropFlow(long elanTag, BigInteger dpId, String extDeviceNodeId, String macToRemove,
            List<ListenableFuture<Void>> futures) {
        String flowId = getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(elanServiceProvider.getMdsalManager().removeFlow(dpId, flowToRemove));
    }

    private static void removeFlowThatSendsThePacketOnAnExternalTunnel(long elanTag, BigInteger dpId,
            String extDeviceNodeId, String macToRemove, List<ListenableFuture<Void>> futures) {
        String flowId = getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(elanServiceProvider.getMdsalManager().removeFlow(dpId, flowToRemove));
    }

    /**
     * Gets the dpid from interface.
     *
     * @param interfaceName
     *            the interface name
     * @return the dpid from interface
     */
    public static BigInteger getDpidFromInterface(String interfaceName) {
        BigInteger dpId = null;
        Future<RpcResult<GetDpidFromInterfaceOutput>> output = elanServiceProvider.getInterfaceManagerRpcService()
                .getDpidFromInterface(new GetDpidFromInterfaceInputBuilder().setIntfName(interfaceName).build());
        try {
            RpcResult<GetDpidFromInterfaceOutput> rpcResult = output.get();
            if (rpcResult.isSuccessful()) {
                dpId = rpcResult.getResult().getDpid();
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Failed to get the DPN ID: {} for interface {}: {} ", dpId, interfaceName, e);
        }
        return dpId;
    }

    /**
     * Checks if is interface operational.
     *
     * @param interfaceName
     *            the interface name
     * @param dataBroker
     *            the data broker
     * @return true, if is interface operational
     */
    public static boolean isInterfaceOperational(String interfaceName, DataBroker dataBroker) {
        if (StringUtils.isBlank(interfaceName)) {
            return false;
        }
        Interface ifState = getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null) {
            return false;
        }
        return ((ifState.getOperStatus() == OperStatus.Up) && (ifState.getAdminStatus() == AdminStatus.Up));
    }

    /**
     * Gets the interface state from operational ds.
     *
     * @param interfaceName
     *            the interface name
     * @param dataBroker
     *            the data broker
     * @return the interface state from oper ds
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(
            String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId = createInterfaceStateInstanceIdentifier(
                interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateOptional = MDSALUtil
                .read(dataBroker, LogicalDatastoreType.OPERATIONAL, ifStateId);
        if (ifStateOptional.isPresent()) {
            return ifStateOptional.get();
        }
        return null;
    }

    /**
     * Gets the interface from config ds.
     *
     * @param interfaceName
     *            the interface name
     * @param dataBroker
     *            the data broker
     * @return the interface from config ds
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface getInterfaceFromConfigDS(
            String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> ifaceId = createInterfaceInstanceIdentifier(
                interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> iface = MDSALUtil
                .read(dataBroker, LogicalDatastoreType.CONFIGURATION, ifaceId);
        if (iface.isPresent()) {
            return iface.get();
        }
        return null;
    }

    /**
     * Creates the interface state instance identifier.
     *
     * @param interfaceName
     *            the interface name
     * @return the instance identifier
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> createInterfaceStateInstanceIdentifier(
            String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> idBuilder = InstanceIdentifier
                .builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(
                                interfaceName));
        return idBuilder.build();
    }

    /**
     * Creates the interface instance identifier.
     *
     * @param interfaceName
     *            the interface name
     * @return the instance identifier
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> createInterfaceInstanceIdentifier(
            String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> idBuilder = InstanceIdentifier
                .builder(Interfaces.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey(
                                interfaceName));
        return idBuilder.build();
    }

    public static void waitForTransactionToComplete(WriteTransaction tx) {
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore {}", e);
        }
    }

    public static boolean isVxlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().longValue() != 0;
    }

    public static boolean isVlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().longValue() != 0;
    }

    public static boolean isFlat(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeFlat.class);
    }

    public static boolean isExternal(String interfaceName) {
        return isExternal(getInterfaceFromConfigDS(interfaceName, elanServiceProvider.getBroker()));
    }

    public static boolean isExternal(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface iface) {
        if (iface == null) {
            return false;
        }

        IfExternal ifExternal = iface.getAugmentation(IfExternal.class);
        return ifExternal != null && Boolean.TRUE.equals(ifExternal.isExternal());
    }

    public static boolean isEtreeRootInterfaceByInterfaceName(String interfaceName) {
        EtreeInterface etreeInterface = getEtreeInterfaceByElanInterfaceName(interfaceName);
        if (etreeInterface != null && etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            return true;
        }
        return false;
    }
}
