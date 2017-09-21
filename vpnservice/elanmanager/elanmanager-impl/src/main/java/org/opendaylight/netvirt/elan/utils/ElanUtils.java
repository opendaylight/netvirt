/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.mdsalutil.packet.ARP;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ElanUtils.class);

    private static Map<String, ElanInstance> elanInstanceLocalCache = new ConcurrentHashMap<>();
    private static Map<String, ElanInterface> elanInterfaceLocalCache = new ConcurrentHashMap<>();
    private static Map<String, Set<DpnInterfaces>> elanInstancToDpnsCache = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> elanInstanceToInterfacesCache = new ConcurrentHashMap<>();

    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;
    private final ElanInstanceManager elanInstanceManager;
    private final OdlInterfaceRpcService interfaceManagerRpcService;
    private final ItmRpcService itmRpcService;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private final IInterfaceManager interfaceManager;
    private final ElanConfig elanConfig;
    private final ElanItmUtils elanItmUtils;
    private final ElanEtreeUtils elanEtreeUtils;

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

    @Inject
    public ElanUtils(DataBroker dataBroker, IMdsalApiManager mdsalManager, ElanInstanceManager elanInstanceManager,
                     OdlInterfaceRpcService interfaceManagerRpcService, ItmRpcService itmRpcService,
                     ElanConfig elanConfig,
                     EntityOwnershipService entityOwnershipService, IInterfaceManager interfaceManager,
                     ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils, ElanEtreeUtils elanEtreeUtils,
                     ElanItmUtils elanItmUtils, ElanDmacUtils elanDmacUtils) {
        this.broker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.elanInstanceManager = elanInstanceManager;
        this.interfaceManagerRpcService = interfaceManagerRpcService;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.elanConfig = elanConfig;

        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanEtreeUtils = elanEtreeUtils;
        this.elanItmUtils = elanItmUtils;
        this.elanL2GatewayUtils =
                new ElanL2GatewayUtils(broker, this, elanDmacUtils, elanItmUtils, entityOwnershipService);
        this.l2GatewayConnectionUtils = new L2GatewayConnectionUtils(broker,
                elanInstanceManager, entityOwnershipService, this);
    }

    public void close() {
        elanL2GatewayUtils.close();
    }

    public ElanL2GatewayUtils getElanL2GatewayUtils() {
        return elanL2GatewayUtils;
    }

    public ElanL2GatewayMulticastUtils getElanL2GatewayMulticastUtils() {
        return elanL2GatewayMulticastUtils;
    }

    public L2GatewayConnectionUtils getL2GatewayConnectionUtils() {
        return l2GatewayConnectionUtils;
    }

    public final Boolean isOpenStackVniSemanticsEnforced() {
        return elanConfig.isOpenstackVniSemanticsEnforced();
    }

    public static void addElanInstanceIntoCache(String elanInstanceName, ElanInstance elanInstance) {
        elanInstanceLocalCache.put(elanInstanceName, elanInstance);
    }

    public static void removeElanInstanceFromCache(String elanInstanceName) {
        elanInstanceLocalCache.remove(elanInstanceName);
    }

    public static ElanInstance getElanInstanceFromCache(String elanInstanceName) {
        return elanInstanceLocalCache.get(elanInstanceName);
    }

    public static Set<String> getAllElanNames() {
        return elanInstanceLocalCache.keySet();
    }

    public static void addElanInterfaceIntoCache(String interfaceName, ElanInterface elanInterface) {
        elanInterfaceLocalCache.put(interfaceName, elanInterface);
    }

    public static void removeElanInterfaceFromCache(String interfaceName) {
        elanInterfaceLocalCache.remove(interfaceName);
    }

    public static ElanInterface getElanInterfaceFromCache(String interfaceName) {
        return elanInterfaceLocalCache.get(interfaceName);
    }

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
                return rpcResult.getResult().getIdValue();
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
        idManager.releaseId(releaseIdInput);
    }

    /**
     * Read utility.
     *
     * @deprecated Consider using {@link #read2(LogicalDatastoreType, InstanceIdentifier)} with proper exception
     *             handling instead
     */
    @Deprecated
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(@Nonnull DataBroker broker,
            LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends DataObject> Optional<T> read2(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path)
            throws ReadFailedException {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            CheckedFuture<Optional<T>, ReadFailedException> checkedFuture = tx.read(datastoreType, path);
            return checkedFuture.checkedGet();
        }
    }

    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceIdentifier() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class).build();
    }

    // elan-instances config container
    public static ElanInstance getElanInstanceByName(DataBroker broker, String elanInstanceName) {
        ElanInstance elanObj = getElanInstanceFromCache(elanInstanceName);
        if (elanObj != null) {
            return elanObj;
        }
        InstanceIdentifier<ElanInstance> elanIdentifierId =
                ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orNull();
    }

    // elan-interfaces Config Container
    public static ElanInterface getElanInterfaceByElanInterfaceName(DataBroker broker, String elanInterfaceName) {
        ElanInterface elanInterfaceObj = getElanInterfaceFromCache(elanInterfaceName);
        if (elanInterfaceObj != null) {
            return elanInterfaceObj;
        }
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, elanInterfaceId).orNull();
    }

    public static EtreeInterface getEtreeInterfaceByElanInterfaceName(DataBroker broker, String elanInterfaceName) {
        ElanInterface elanInterface = getElanInterfaceByElanInterfaceName(broker, elanInterfaceName);
        if (elanInterface == null) {
            return null;
        } else {
            return elanInterface.getAugmentation(EtreeInterface.class);
        }
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    // elan-state Operational container
    public static Elan getElanByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<Elan> elanIdentifier = getElanInstanceOperationalDataPath(elanInstanceName);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, elanIdentifier).orNull();
    }

    public static InstanceIdentifier<Elan> getElanInstanceOperationalDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class, new ElanKey(elanInstanceName)).build();
    }

    // grouping of forwarding-entries
    public MacEntry getInterfaceMacEntriesOperationalDataPath(String interfaceName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> existingMacEntryId = getInterfaceMacEntriesIdentifierOperationalDataPath(
                interfaceName, physAddress);
        return read(broker, LogicalDatastoreType.OPERATIONAL, existingMacEntryId).orNull();
    }

    public MacEntry getInterfaceMacEntriesOperationalDataPathFromId(InstanceIdentifier identifier) {
        Optional<MacEntry> existingInterfaceMacEntry = read(broker,
                LogicalDatastoreType.OPERATIONAL, identifier);
        return existingInterfaceMacEntry.orNull();
    }

    public static InstanceIdentifier<MacEntry> getInterfaceMacEntriesIdentifierOperationalDataPath(String interfaceName,
            PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class)
                .child(ElanInterfaceMac.class, new ElanInterfaceMacKey(interfaceName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();

    }

    // elan-forwarding-tables Operational container
    public Optional<MacEntry> getMacEntryForElanInstance(String elanName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanName, physAddress);
        return read(broker, LogicalDatastoreType.OPERATIONAL, macId);
    }

    public MacEntry getMacEntryFromElanMacId(InstanceIdentifier identifier) {
        Optional<MacEntry> existingInterfaceMacEntry = read(broker,
                LogicalDatastoreType.OPERATIONAL, identifier);
        return existingInterfaceMacEntry.orNull();
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
    public ElanInterfaceMac getElanInterfaceMacByInterfaceName(String interfaceName) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = getElanInterfaceMacEntriesOperationalDataPath(
                interfaceName);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId).orNull();
    }

    /**
     * Gets the elan interface mac addresses.
     *
     * @param interfaceName
     *            the interface name
     * @return the elan interface mac addresses
     */
    public List<PhysAddress> getElanInterfaceMacAddresses(String interfaceName) {
        List<PhysAddress> macAddresses = new ArrayList<>();
        ElanInterfaceMac elanInterfaceMac = getElanInterfaceMacByInterfaceName(interfaceName);
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
    public DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, BigInteger dpId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                dpId);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId).orNull();
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
    public ElanTagName getElanInfoByElanTag(long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = read(broker,
                LogicalDatastoreType.OPERATIONAL, elanId);
        return existingElanInfo.orNull();
    }

    public static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(long elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class, new ElanTagNameKey(elanTag))
                .build();
    }

    // interface-index-tag operational container
    public Optional<IfIndexInterface> getInterfaceInfoByInterfaceTag(long interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        return read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
    }

    public static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(long interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class)
                .child(IfIndexInterface.class, new IfIndexInterfaceKey((int) interfaceTag)).build();
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    public ElanDpnInterfacesList getElanDpnInterfacesList(String elanName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanName);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId).orNull();
    }

    public ElanDpnInterfaces getElanDpnInterfacesList() {
        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfaceId = InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .build();
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId).orNull();
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
    public List<BigInteger> getParticipatingDpnsInElanInstance(String elanInstanceName) {
        List<BigInteger> dpIds = new ArrayList<>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = read(broker,
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
    public boolean isDpnAlreadyPresentInElanInstance(BigInteger dpId, String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = read(broker,
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (!existingElanDpnInterfaces.isPresent()) {
            return false;
        }
        List<DpnInterfaces> dpnInterfaces = existingElanDpnInterfaces.get().getDpnInterfaces();
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            if (dpnInterface.getDpId().equals(dpId)) {
                return true;
            }
        }
        return false;
    }

    public ElanForwardingTables getElanForwardingList() {
        InstanceIdentifier<ElanForwardingTables> elanForwardingTableId = InstanceIdentifier
                .builder(ElanForwardingTables.class).build();
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanForwardingTableId).orNull();
    }

    public static long getElanRemoteBroadCastGroupID(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + elanTag % ElanConstants.ELAN_GID_MIN * 2;
    }

    /**
     * Gets the elan mac table.
     *
     * @param elanName
     *            the elan name
     * @return the elan mac table
     */
    public MacTable getElanMacTable(String elanName) {
        InstanceIdentifier<MacTable> elanMacTableId = getElanMacTableOperationalDataPath(elanName);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanMacTableId).orNull();
    }

    public static long getElanLocalBCGId(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + (elanTag % ElanConstants.ELAN_GID_MIN * 2 - 1);
    }

    public static long getElanRemoteBCGId(long elanTag) {
        return ElanConstants.ELAN_GID_MIN + elanTag % ElanConstants.ELAN_GID_MIN * 2;
    }

    public static long getEtreeLeafLocalBCGId(long etreeLeafTag) {
        return ElanConstants.ELAN_GID_MIN + (etreeLeafTag % ElanConstants.ELAN_GID_MIN * 2 - 1);
    }

    public static long getEtreeLeafRemoteBCGId(long etreeLeafTag) {
        return ElanConstants.ELAN_GID_MIN + etreeLeafTag % ElanConstants.ELAN_GID_MIN * 2;
    }

    public static BigInteger getElanMetadataLabel(long elanTag, boolean isSHFlagSet) {
        int shBit = isSHFlagSet ? 1 : 0;
        return BigInteger.valueOf(elanTag).shiftLeft(24).or(BigInteger.valueOf(shBit));
    }

    /**
     * Setting SMAC, DMAC, UDMAC in this DPN and optionally in other DPNs.
     *
     * @param elanInfo
     *            the elan info
     * @param interfaceInfo
     *            the interface info
     * @param macTimeout
     *            the mac timeout
     * @param macAddress
     *            the mac address
     * @param configureRemoteFlows
     *            true if remote dmac flows should be configured as well
     * @param writeFlowGroupTx
     *            the flow group tx
     * @throws ElanException in case of issues creating the flow objects
     */
    public void setupMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
                              long macTimeout, String macAddress, boolean configureRemoteFlows,
                              WriteTransaction writeFlowGroupTx) throws ElanException {
        synchronized (getElanMacDPNKey(elanInfo.getElanTag(), macAddress, interfaceInfo.getDpId())) {
            setupKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress, mdsalManager,
                writeFlowGroupTx);
            setupOrigDmacFlows(elanInfo, interfaceInfo, macAddress, configureRemoteFlows, mdsalManager,
                broker, writeFlowGroupTx);
        }
    }

    public void setupDMacFlowOnRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo, BigInteger dstDpId,
                                         String macAddress, WriteTransaction writeFlowTx) throws ElanException {
        String elanInstanceName = elanInfo.getElanInstanceName();
        setupRemoteDmacFlow(dstDpId, interfaceInfo.getDpId(), interfaceInfo.getInterfaceTag(), elanInfo.getElanTag(),
                macAddress, elanInstanceName, writeFlowTx, interfaceInfo.getInterfaceName(), elanInfo);
        LOG.info("Remote Dmac flow entry created for elan Name:{}, logical port Name:{} and"
                + " mac address {} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(),
                macAddress, dstDpId);
    }

    /**
     * Inserts a Flow in SMAC table to state that the MAC has already been
     * learnt.
     */
    private void setupKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress, IMdsalApiManager mdsalApiManager, WriteTransaction writeFlowGroupTx) {
        FlowEntity flowEntity = buildKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress);
        mdsalApiManager.addFlowToTx(flowEntity, writeFlowGroupTx);
        LOG.debug("Known Smac flow entry created for elan Name:{}, logical Interface port:{} and mac address:{}",
                elanInfo.getElanInstanceName(), elanInfo.getDescription(), macAddress);
    }

    public FlowEntity buildKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress) {
        int lportTag = interfaceInfo.getInterfaceTag();
        // Matching metadata and eth_src fields
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanInfo.getElanTag(), lportTag),
                ElanHelper.getElanMetadataMask()));
        mkMatches.add(new MatchEthernetSource(new MacAddress(macAddress)));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        BigInteger dpId = interfaceInfo.getDpId();
        long elanTag = getElanTag(broker, elanInfo, interfaceInfo);
        return new FlowEntityBuilder()
            .setDpnId(dpId)
            .setTableId(NwConstants.ELAN_SMAC_TABLE)
            .setFlowId(getKnownDynamicmacFlowRef(NwConstants.ELAN_SMAC_TABLE, dpId, lportTag, macAddress, elanTag))
            .setPriority(20)
            .setFlowName(elanInfo.getDescription())
            .setIdleTimeOut((int) macTimeout)
            .setHardTimeOut(0)
            .setCookie(ElanConstants.COOKIE_ELAN_KNOWN_SMAC.add(BigInteger.valueOf(elanTag)))
            .setMatchInfoList(mkMatches)
            .setInstructionInfoList(mkInstructions)
            .setStrictFlag(true)
            // If Mac timeout is 0, the flow won't be deleted automatically, so no need to get notified
            .setSendFlowRemFlag(macTimeout != 0)
            .build();
    }

    private static Long getElanTag(DataBroker broker, ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        EtreeInterface etreeInterface = getEtreeInterfaceByElanInterfaceName(broker, interfaceInfo.getInterfaceName());
        if (etreeInterface == null || etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            return elanInfo.getElanTag();
        } else { // Leaf
            EtreeInstance etreeInstance = elanInfo.getAugmentation(EtreeInstance.class);
            if (etreeInstance == null) {
                LOG.warn("EtreeInterface {} is connected to a non-Etree network: {}",
                         interfaceInfo.getInterfaceName(), elanInfo.getElanInstanceName());
                return elanInfo.getElanTag();
            } else {
                return etreeInstance.getEtreeLeafTagVal().getValue();
            }
        }
    }

    /**
     * Installs a Flow in INTERNAL_TUNNEL_TABLE of the affected DPN that sends
     * the packet through the specified interface if the tunnel_id matches the
     * interface's lportTag.
     *
     * @param interfaceInfo
     *            the interface info
     * @param mdsalApiManager
     *            the mdsal API manager
     * @param writeFlowGroupTx
     *            the writeFLowGroup tx
     */
    public void setupTermDmacFlows(InterfaceInfo interfaceInfo, IMdsalApiManager mdsalApiManager,
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
        LOG.debug("Terminating service table flow entry created on dpn:{} for logical Interface port:{}", dpId,
                interfaceInfo.getPortName());
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
     * specific lportTag.
     *
     * @param lportTag
     *            lportTag that must be checked against the tunnel_id field
     * @return the list of match Info
     */
    public static List<MatchInfo> getTunnelIdMatchForFilterEqualsLPortTag(int lportTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(lportTag)));
        return mkMatches;
    }

    /**
     * Constructs the Instructions that take the packet over a given interface.
     *
     * @param ifName
     *            Name of the interface where the packet must be sent over. It
     *            can be a local interface or a tunnel interface (internal or
     *            external)
     * @return the Instruction
     */
    public List<Instruction> getInstructionsInPortForOutGroup(String ifName) {
        List<Instruction> mkInstructions = new ArrayList<>();
        List<Action> actions = getEgressActionsForInterface(ifName, /* tunnelKey */ null);

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
    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<Action> getEgressActionsForInterface(String ifName, Long tunnelKey) {
        List<Action> listAction = new ArrayList<>();
        try {
            GetEgressActionsForInterfaceInput getEgressActionInput = new GetEgressActionsForInterfaceInputBuilder()
                    .setIntfName(ifName).setTunnelKey(tunnelKey).build();
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result = interfaceManagerRpcService
                    .getEgressActionsForInterface(getEgressActionInput);
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName,
                        rpcResult.getErrors());
            } else {
                List<Action> actions = rpcResult.getResult().getAction();
                listAction = actions;
            }
        } catch (Exception e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listAction;
    }

    private void setupOrigDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
                                    boolean configureRemoteFlows, IMdsalApiManager mdsalApiManager,
                                    DataBroker broker, WriteTransaction writeFlowGroupTx)
                                    throws ElanException {
        BigInteger dpId = interfaceInfo.getDpId();
        String ifName = interfaceInfo.getInterfaceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        String elanInstanceName = elanInfo.getElanInstanceName();

        Long elanTag = elanInfo.getElanTag();

        setupLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInfo, mdsalApiManager, ifTag,
                writeFlowGroupTx);
        LOG.debug("Dmac flow entry created for elan Name:{}, logical port Name:{} mand mac address:{} "
                                    + "on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, dpId);

        if (!configureRemoteFlows) {
            return;
        }

        List<DpnInterfaces> elanDpns = getInvolvedDpnsInElan(elanInstanceName);
        if (elanDpns == null) {
            return;
        }

        for (DpnInterfaces elanDpn : elanDpns) {

            if (elanDpn.getDpId().equals(dpId)) {
                continue;
            }

            // Check for the Remote DPN present in Inventory Manager
            if (!isDpnPresent(elanDpn.getDpId())) {
                continue;
            }

            // For remote DPNs a flow is needed to indicate that packets of this ELAN going to this MAC need to be
            // forwarded through the appropriate ITM tunnel
            setupRemoteDmacFlow(elanDpn.getDpId(), // srcDpn (the remote DPN in this case)
                    dpId, // dstDpn (the local DPN)
                    interfaceInfo.getInterfaceTag(), // lportTag of the local interface
                    elanTag,  // identifier of the Elan
                    macAddress, // MAC to be programmed in remote DPN
                    elanInstanceName, writeFlowGroupTx, ifName, elanInfo
            );
            LOG.debug("Remote Dmac flow entry created for elan Name:{}, logical port Name:{} and mac address:{} on"
                        + " dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, elanDpn.getDpId());
        }

        // TODO: Make sure that the same is performed against the ElanDevices.
    }

    public List<DpnInterfaces> getInvolvedDpnsInElan(String elanName) {
        return elanInstanceManager.getElanDPNByName(elanName);
    }

    private void setupLocalDmacFlow(long elanTag, BigInteger dpId, String ifName, String macAddress,
            ElanInstance elanInfo, IMdsalApiManager mdsalApiManager, long ifTag, WriteTransaction writeFlowGroupTx) {
        Flow flowEntity = buildLocalDmacFlowEntry(elanTag, dpId, ifName, macAddress, elanInfo, ifTag);
        mdsalApiManager.addFlowToTx(dpId, flowEntity, writeFlowGroupTx);
        installEtreeLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInfo,
                mdsalApiManager, ifTag, writeFlowGroupTx);
    }

    private void installEtreeLocalDmacFlow(long elanTag, BigInteger dpId, String ifName, String macAddress,
            ElanInstance elanInfo, IMdsalApiManager mdsalApiManager, long ifTag, WriteTransaction writeFlowGroupTx) {
        EtreeInterface etreeInterface = getEtreeInterfaceByElanInterfaceName(broker, ifName);
        if (etreeInterface != null) {
            if (etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
                EtreeLeafTagName etreeTagName = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
                if (etreeTagName == null) {
                    LOG.warn("Interface {} seems like it belongs to Etree but etreeTagName from elanTag {} is null",
                             ifName, elanTag);
                } else {
                    Flow flowEntity = buildLocalDmacFlowEntry(etreeTagName.getEtreeLeafTag().getValue(), dpId, ifName,
                            macAddress, elanInfo, ifTag);
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

    public static String getKnownDynamicmacFlowRef(short tableId, BigInteger dpId, String macAddress, long elanTag) {
        return new StringBuffer().append(tableId).append(elanTag).append(dpId).append(macAddress).toString();
    }

    public static String getKnownDynamicmacFlowRef(short elanDmacTable, BigInteger dpId, String extDeviceNodeId,
            String dstMacAddress, long elanTag, boolean shFlag) {
        return new StringBuffer().append(elanDmacTable).append(elanTag).append(dpId).append(extDeviceNodeId)
                .append(dstMacAddress).append(shFlag).toString();
    }

    /**
     * Builds the flow to be programmed in the DMAC table of the local DPN (that
     * is, where the MAC is attached to). This flow consists in:
     *
     * <p>Match: + elanTag in metadata + packet goes to a MAC locally attached
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
     * @param elanInfo
     *            the elan info
     * @param ifTag
     *            the if tag
     * @return the flow
     */
    public Flow buildLocalDmacFlowEntry(long elanTag, BigInteger dpId, String ifName, String macAddress,
            ElanInstance elanInfo, long ifTag) {

        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddress)));

        List<Instruction> mkInstructions = new ArrayList<>();
        List<Action> actions = getEgressActionsForInterface(ifName, /* tunnelKey */ null);
        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, ifTag, macAddress, elanTag), 20,
                elanInfo.getElanInstanceName(), 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(
                        BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    public void setupRemoteDmacFlow(BigInteger srcDpId, BigInteger destDpId, int lportTag, long elanTag, String
            macAddress, String displayName, WriteTransaction writeFlowGroupTx, String interfaceName, ElanInstance
            elanInstance) throws ElanException {
        if (interfaceManager.isExternalInterface(interfaceName)) {
            LOG.debug("Ignoring install remote DMAC {} flow on provider interface {} elan {}",
                    macAddress, interfaceName, elanInstance.getElanInstanceName());
            return;
        }
        Flow flowEntity;
        // if openstack-vni-semantics are enforced, segmentation ID is passed as network VNI for VxLAN based provider
        // networks, 0 otherwise
        long lportTagOrVni = !isOpenStackVniSemanticsEnforced() ? lportTag : isVxlan(elanInstance)
                ? elanInstance.getSegmentationId() : 0;
        flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTagOrVni, elanTag, macAddress, displayName,
                elanInstance);
        mdsalManager.addFlowToTx(srcDpId, flowEntity, writeFlowGroupTx);
        setupEtreeRemoteDmacFlow(srcDpId, destDpId, lportTagOrVni, elanTag, macAddress, displayName, interfaceName,
                writeFlowGroupTx, elanInstance);
    }

    private void setupEtreeRemoteDmacFlow(BigInteger srcDpId, BigInteger destDpId, long lportTagOrVni, long elanTag,
                                          String macAddress, String displayName, String interfaceName,
                                          WriteTransaction writeFlowGroupTx, ElanInstance elanInstance)
                                          throws ElanException {
        Flow flowEntity;
        EtreeInterface etreeInterface = getEtreeInterfaceByElanInterfaceName(broker, interfaceName);
        if (etreeInterface != null) {
            if (etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
                EtreeLeafTagName etreeTagName = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
                if (etreeTagName == null) {
                    LOG.warn("Interface " + interfaceName
                            + " seems like it belongs to Etree but etreeTagName from elanTag " + elanTag + " is null.");
                } else {
                    flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTagOrVni,
                            etreeTagName.getEtreeLeafTag().getValue(), macAddress, displayName, elanInstance);
                    mdsalManager.addFlowToTx(srcDpId, flowEntity, writeFlowGroupTx);
                }
            }
        }
    }

    /**
     * Builds a Flow to be programmed in a remote DPN's DMAC table. This flow
     * consists in: Match: + elanTag in packet's metadata + packet going to a
     * MAC known to be located in another DPN Actions: + set_tunnel_id
     * + output ITM internal tunnel interface with the other DPN
     *
     * @param srcDpId
     *            the src Dpn Id
     * @param destDpId
     *            dest Dp Id
     * @param lportTagOrVni
     *            lportTag or network VNI
     * @param elanTag
     *            elan Tag
     * @param macAddress
     *            macAddress
     * @param displayName
     *            display Name
     * @return the flow remote Dmac
     * @throws ElanException in case of issues creating the flow objects
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Flow buildRemoteDmacFlowEntry(BigInteger srcDpId, BigInteger destDpId, long lportTagOrVni, long elanTag,
            String macAddress, String displayName, ElanInstance elanInstance) throws ElanException {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddress)));

        List<Instruction> mkInstructions = new ArrayList<>();

        // List of Action for the provided Source and Destination DPIDs
        try {
            List<Action> actions = null;
            if (isVlan(elanInstance) || isFlat(elanInstance)) {
                String interfaceName = getExternalElanInterface(elanInstance.getElanInstanceName(), srcDpId);
                if (null == interfaceName) {
                    LOG.info("buildRemoteDmacFlowEntry: Could not find interfaceName for {} {}", srcDpId,
                        elanInstance);
                }
                actions = getEgressActionsForInterface(interfaceName, null);
            } else if (isVxlan(elanInstance)) {
                actions = elanItmUtils.getInternalTunnelItmEgressAction(srcDpId, destDpId, lportTagOrVni);
            }
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get egress actions to add to flow for srcDpId=" + srcDpId + ", destDpId=" + destDpId
                    + ", lportTag/VNI=" + lportTagOrVni, e);
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, srcDpId, destDpId, macAddress, elanTag),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;

    }

    public void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, MacEntry macEntry,
            WriteTransaction deleteFlowGroupTx) {
        if (elanInfo == null || interfaceInfo == null) {
            return;
        }
        String macAddress = macEntry.getMacAddress().getValue();
        synchronized (getElanMacDPNKey(elanInfo.getElanTag(), macAddress, interfaceInfo.getDpId())) {
            deleteMacFlows(elanInfo, interfaceInfo, macAddress, /* alsoDeleteSMAC */ true, deleteFlowGroupTx);
        }
    }

    public void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, WriteTransaction deleteFlowGroupTx) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        BigInteger srcdpId = interfaceInfo.getDpId();
        boolean isFlowsRemovedInSrcDpn = false;
        for (DpnInterfaces dpnInterface : remoteFEs) {
            Long elanTag = elanInfo.getElanTag();
            BigInteger dstDpId = dpnInterface.getDpId();
            if (executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    elanTag, dstDpId, deleteFlowGroupTx)) {
                isFlowsRemovedInSrcDpn = true;
            }
            executeEtreeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    elanTag, dstDpId, deleteFlowGroupTx);
        }
        if (!isFlowsRemovedInSrcDpn) {
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, deleteFlowGroupTx);
        }
    }

    private void executeEtreeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, String elanInstanceName, BigInteger srcdpId, Long elanTag, BigInteger dstDpId,
            WriteTransaction deleteFlowGroupTx) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), dstDpId, deleteFlowGroupTx);
        }
    }

    private boolean executeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, String elanInstanceName, BigInteger srcdpId, Long elanTag, BigInteger dstDpId,
            WriteTransaction deleteFlowGroupTx) {
        boolean isFlowsRemovedInSrcDpn = false;
        if (dstDpId.equals(srcdpId)) {
            isFlowsRemovedInSrcDpn = true;
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, deleteFlowGroupTx);
        } else if (isDpnPresent(dstDpId)) {
            mdsalManager
                    .removeFlowToTx(dstDpId,
                            MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE, getKnownDynamicmacFlowRef(
                                    NwConstants.ELAN_DMAC_TABLE, dstDpId, srcdpId, macAddress, elanTag)),
                            deleteFlowGroupTx);
            LOG.debug("Dmac flow entry deleted for elan:{}, logical interface port:{} and mac address:{} on dpn:{}",
                    elanInstanceName, interfaceInfo.getPortName(), macAddress, dstDpId);
        }
        return isFlowsRemovedInSrcDpn;
    }

    private void deleteSmacAndDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, WriteTransaction deleteFlowGroupTx) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        BigInteger srcdpId = interfaceInfo.getDpId();
        Long elanTag = elanInfo.getElanTag();
        if (deleteSmac) {
            mdsalManager
                    .removeFlowToTx(srcdpId,
                            MDSALUtil.buildFlow(NwConstants.ELAN_SMAC_TABLE, getKnownDynamicmacFlowRef(
                                    NwConstants.ELAN_SMAC_TABLE, srcdpId, ifTag, macAddress, elanTag)),
                            deleteFlowGroupTx);
        }
        mdsalManager.removeFlowToTx(srcdpId,
                MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                        getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, srcdpId, ifTag, macAddress, elanTag)),
                deleteFlowGroupTx);
        LOG.debug("All the required flows deleted for elan:{}, logical Interface port:{} and MAC address:{} on dpn:{}",
                elanInstanceName, interfaceInfo.getPortName(), macAddress, srcdpId);
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
     * @param elanInterfaces
     *            the elan interfaces
     * @param tx
     *            transaction
     */
    public static void updateOperationalDataStore(DataBroker broker, IdManagerService idManager,
            ElanInstance elanInstanceAdded, List<String> elanInterfaces, WriteTransaction tx) {
        String elanInstanceName = elanInstanceAdded.getElanInstanceName();
        Long elanTag = elanInstanceAdded.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = retrieveNewElanTag(idManager, elanInstanceName);
        }
        Elan elanInfo = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(elanInterfaces)
                .setKey(new ElanKey(elanInstanceName)).build();

        // Add the ElanState in the elan-state operational data-store
        tx.put(LogicalDatastoreType.OPERATIONAL, getElanInstanceOperationalDataPath(elanInstanceName),
                elanInfo, WriteTransaction.CREATE_MISSING_PARENTS);

        // Add the ElanMacTable in the elan-mac-table operational data-store
        MacTable elanMacTable = new MacTableBuilder().setKey(new MacTableKey(elanInstanceName)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL, getElanMacTableOperationalDataPath(elanInstanceName),
                elanMacTable, WriteTransaction.CREATE_MISSING_PARENTS);

        ElanTagNameBuilder elanTagNameBuilder = new ElanTagNameBuilder().setElanTag(elanTag)
                .setKey(new ElanTagNameKey(elanTag)).setName(elanInstanceName);
        long etreeLeafTag = -1;
        if (isEtreeInstance(elanInstanceAdded)) {
            etreeLeafTag = retrieveNewElanTag(idManager,elanInstanceName + ElanConstants
                    .LEAVES_POSTFIX);
            EtreeLeafTagName etreeLeafTagName = new EtreeLeafTagNameBuilder()
                    .setEtreeLeafTag(new EtreeLeafTag(etreeLeafTag)).build();
            elanTagNameBuilder.addAugmentation(EtreeLeafTagName.class, etreeLeafTagName);
            addTheLeafTagAsElanTag(broker, elanInstanceName, etreeLeafTag, tx);
        }
        ElanTagName elanTagName = elanTagNameBuilder.build();

        // Add the ElanTag to ElanName in the elan-tag-name Operational
        // data-store
        tx.put(LogicalDatastoreType.OPERATIONAL,
                getElanInfoEntriesOperationalDataPath(elanTag), elanTagName);

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
        tx.merge(LogicalDatastoreType.CONFIGURATION, ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName),
                elanInstanceWithTag, true);
    }

    private static void addTheLeafTagAsElanTag(DataBroker broker, String elanInstanceName, long etreeLeafTag,
            WriteTransaction tx) {
        ElanTagName etreeTagAsElanTag = new ElanTagNameBuilder().setElanTag(etreeLeafTag)
                .setKey(new ElanTagNameKey(etreeLeafTag)).setName(elanInstanceName).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                getElanInfoEntriesOperationalDataPath(etreeLeafTag), etreeTagAsElanTag);
    }

    private static boolean isEtreeInstance(ElanInstance elanInstanceAdded) {
        return elanInstanceAdded.getAugmentation(EtreeInstance.class) != null;
    }

    public boolean isDpnPresent(BigInteger dpnId) {
        String dpn = String.format("%s:%s", "openflow", dpnId);
        NodeId nodeId = new NodeId(dpn);

        InstanceIdentifier<Node> node = InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId))
                .build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, node).isPresent();
    }

    public static ServicesInfo getServiceInfo(String elanInstanceName, long elanTag, String interfaceName) {
        int priority = ElanConstants.ELAN_SERVICE_PRIORITY;
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanHelper.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.ELAN_SMAC_TABLE, ++instructionKey));

        short serviceIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
        ServicesInfo serviceInfo = InterfaceServiceUtil.buildServiceInfo(
                String.format("%s.%s", elanInstanceName, interfaceName), serviceIndex, priority,
                NwConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
        return serviceInfo;
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short serviceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }

    public static List<MatchInfo> getTunnelMatchesForServiceId(int elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(elanTag)));

        return mkMatches;
    }

    public void removeTerminatingServiceAction(BigInteger destDpId, int serviceId) {
        RemoveTerminatingServiceActionsInput input = new RemoveTerminatingServiceActionsInputBuilder()
                .setDpnId(destDpId).setServiceId(serviceId).build();
        Future<RpcResult<Void>> futureObject = itmRpcService.removeTerminatingServiceActions(input);
        try {
            RpcResult<Void> result = futureObject.get();
            if (result.isSuccessful()) {
                LOG.debug("Successfully completed removeTerminatingServiceActions for ELAN with serviceId {} on "
                                + "dpn {}", serviceId, destDpId);
            } else {
                LOG.debug("Failure in removeTerminatingServiceAction RPC call for ELAN with serviceId {} on "
                        + "dpn {}", serviceId, destDpId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call removeTerminatingServiceActions for ELAN with serviceId {} on "
                    + "dpn {}: {}", serviceId, destDpId, e);
        }
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
    public ExternalTunnel getExternalTunnel(String sourceDevice, String destinationDevice,
            LogicalDatastoreType datastoreType) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        InstanceIdentifier<ExternalTunnel> iid = InstanceIdentifier.builder(ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(destinationDevice, sourceDevice, tunType)).build();
        return read(broker, datastoreType, iid).orNull();
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
    public ExternalTunnel getExternalTunnel(String interfaceName, LogicalDatastoreType datastoreType) {
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
    public List<ExternalTunnel> getAllExternalTunnels(LogicalDatastoreType datastoreType) {
        InstanceIdentifier<ExternalTunnelList> iid = InstanceIdentifier.builder(ExternalTunnelList.class).build();
        // Don’t use Optional.transform() here, getExternalTunnel() can return null
        Optional<ExternalTunnelList> optionalExternalTunnelList = read(broker, datastoreType, iid);
        return optionalExternalTunnelList.isPresent() ? optionalExternalTunnelList.get().getExternalTunnel()
                : Collections.emptyList();
    }

    public static List<MatchInfo> buildMatchesForElanTagShFlagAndDstMac(long elanTag, boolean shFlag, String macAddr) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(
                new MatchMetadata(getElanMetadataLabel(elanTag, shFlag), MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddr)));
        return mkMatches;
    }

    /**
     * Gets the dpid from interface.
     *
     * @param interfaceName
     *            the interface name
     * @return the dpid from interface
     */
    public BigInteger getDpidFromInterface(String interfaceName) {
        BigInteger dpId = null;
        Future<RpcResult<GetDpidFromInterfaceOutput>> output = interfaceManagerRpcService
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
        return ifState.getOperStatus() == OperStatus.Up && ifState.getAdminStatus() == AdminStatus.Up;
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
        .ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceStateFromOperDS(
            String interfaceName, DataBroker dataBroker) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId = createInterfaceStateInstanceIdentifier(
                interfaceName);
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, ifStateId).orNull();
    }

    /**
     * Creates the interface state instance identifier.
     *
     * @param interfaceName
     *            the interface name
     * @return the instance identifier
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
        .ietf.interfaces.rev140508.interfaces.state.Interface> createInterfaceStateInstanceIdentifier(
            String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> idBuilder = InstanceIdentifier
                .builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                        .ietf.interfaces.rev140508.interfaces.state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                            .ietf.interfaces.rev140508.interfaces.state.InterfaceKey(
                                interfaceName));
        return idBuilder.build();
    }

    public static CheckedFuture<Void, TransactionCommitFailedException> waitForTransactionToComplete(
            WriteTransaction tx) {
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore {}", e);
        }
        return futures;
    }

    public static boolean isVxlanNetwork(DataBroker broker, String elanInstanceName) {
        ElanInstance elanInstance = getElanInstanceByName(broker, elanInstanceName);
        return elanInstance != null && isVxlan(elanInstance);
    }

    public static boolean isVxlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId() != 0;
    }

    private static boolean isVxlanSegment(ElanInstance elanInstance) {
        if (elanInstance != null) {
            List<ElanSegments> elanSegments = elanInstance.getElanSegments();
            if (elanSegments != null) {
                for (ElanSegments segment : elanSegments) {
                    if (segment != null && segment.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                            && segment.getSegmentationId() != null
                            && segment.getSegmentationId().longValue() != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isVxlanNetworkOrVxlanSegment(ElanInstance elanInstance) {
        return isVxlan(elanInstance) || isVxlanSegment(elanInstance);
    }

    public static Long getVxlanSegmentationId(ElanInstance elanInstance) {
        Long segmentationId = 0L;

        if (elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().longValue() != 0) {
            segmentationId = elanInstance.getSegmentationId();
        } else {
            for (ElanSegments segment: elanInstance.getElanSegments()) {
                if (segment != null && segment.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                    && segment.getSegmentationId() != null
                    && segment.getSegmentationId().longValue() != 0) {
                    segmentationId = segment.getSegmentationId();
                }
            }
        }
        return segmentationId;
    }

    public static boolean isVlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId() != 0;
    }

    public static boolean isFlat(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeFlat.class);
    }

    public void handleDmacRedirectToDispatcherFlows(Long elanTag, String displayName,
            String macAddress, int addOrRemove, List<BigInteger> dpnIds) {
        for (BigInteger dpId : dpnIds) {
            if (addOrRemove == NwConstants.ADD_FLOW) {
                WriteTransaction writeTx = broker.newWriteOnlyTransaction();
                mdsalManager.addFlowToTx(buildDmacRedirectToDispatcherFlow(dpId, macAddress, displayName, elanTag),
                        writeTx);
                writeTx.submit();
            } else {
                String flowId = getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, macAddress, elanTag);
                mdsalManager.removeFlow(dpId, MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE, flowId));
            }
        }
    }

    public static FlowEntity buildDmacRedirectToDispatcherFlow(BigInteger dpId, String dstMacAddress,
            String displayName, long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        matches.add(new MatchEthernetDestination(new MacAddress(dstMacAddress)));
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

        instructions.add(new InstructionApplyActions(actions));
        String flowId = getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, dstMacAddress, elanTag);
        FlowEntity flow  = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, flowId, 20, displayName, 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                matches, instructions);
        return flow;
    }

    /**
     * Add Mac Address to ElanInterfaceForwardingEntries and ElanForwardingTables
     * Install SMAC and DMAC flows.
     */
    public void addMacEntryToDsAndSetupFlows(IInterfaceManager interfaceManager, String interfaceName,
            String macAddress, String elanName, WriteTransaction tx, WriteTransaction flowWritetx, int macTimeOut)
            throws ElanException {
        LOG.trace("Adding mac address {} and interface name {} to ElanInterfaceForwardingEntries and "
            + "ElanForwardingTables DS", macAddress, interfaceName);
        BigInteger timeStamp = new BigInteger(String.valueOf(System.currentTimeMillis()));
        PhysAddress physAddress = new PhysAddress(macAddress);
        MacEntry macEntry = new MacEntryBuilder().setInterface(interfaceName).setMacAddress(physAddress)
                .setKey(new MacEntryKey(physAddress)).setControllerLearnedForwardingEntryTimestamp(timeStamp)
                .setIsStaticAddress(false).build();
        InstanceIdentifier<MacEntry> macEntryId = ElanUtils
                .getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress);
        tx.put(LogicalDatastoreType.OPERATIONAL, macEntryId, macEntry);
        InstanceIdentifier<MacEntry> elanMacEntryId = ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress);
        tx.put(LogicalDatastoreType.OPERATIONAL, elanMacEntryId, macEntry);
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
        setupMacFlows(elanInstance, interfaceManager.getInterfaceInfo(interfaceName), macTimeOut, macAddress, true,
                flowWritetx);
    }

    /**
     * Remove Mac Address from ElanInterfaceForwardingEntries and ElanForwardingTables
     * Remove SMAC and DMAC flows.
     */
    public void deleteMacEntryFromDsAndRemoveFlows(IInterfaceManager interfaceManager, String interfaceName,
            String macAddress, String elanName, WriteTransaction tx, WriteTransaction deleteFlowTx) {
        LOG.trace("Deleting mac address {} and interface name {} from ElanInterfaceForwardingEntries "
                + "and ElanForwardingTables DS", macAddress, interfaceName);
        PhysAddress physAddress = new PhysAddress(macAddress);
        MacEntry macEntry = getInterfaceMacEntriesOperationalDataPath(interfaceName, physAddress);
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (macEntry != null && interfaceInfo != null) {
            deleteMacFlows(ElanUtils.getElanInstanceByName(broker, elanName), interfaceInfo, macEntry, deleteFlowTx);
        }
        tx.delete(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getInterfaceMacEntriesIdentifierOperationalDataPath(interfaceName, physAddress));
        tx.delete(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getMacEntryOperationalDataPath(elanName, physAddress));
    }

    public String getExternalElanInterface(String elanInstanceName, BigInteger dpnId) {
        DpnInterfaces dpnInterfaces = getElanInterfaceInfoByElanDpn(elanInstanceName, dpnId);
        if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null) {
            LOG.trace("Elan {} does not have interfaces in DPN {}", elanInstanceName, dpnId);
            return null;
        }

        for (String dpnInterface : dpnInterfaces.getInterfaces()) {
            if (interfaceManager.isExternalInterface(dpnInterface)) {
                return dpnInterface;
            }
        }

        LOG.trace("Elan {} does not have any external interace attached to DPN {}", elanInstanceName, dpnId);
        return null;
    }

    public static String getElanMacDPNKey(long elanTag, String macAddress, BigInteger dpnId) {
        String elanMacDmacDpnKey = "MAC-" + macAddress + " ELAN_TAG-" + elanTag + "DPN_ID-" + dpnId;
        return elanMacDmacDpnKey.intern();
    }

    public static String getElanMacKey(long elanTag, String macAddress) {
        String elanMacKey = "MAC-" + macAddress + " ELAN_TAG-" + elanTag;
        return elanMacKey.intern();
    }


    public static void addToListenableFutureIfTxException(RuntimeException exception,
            List<ListenableFuture<Void>> futures) {
        Throwable cause = exception.getCause();
        if (cause != null && cause instanceof TransactionCommitFailedException) {
            futures.add(Futures.immediateFailedCheckedFuture((TransactionCommitFailedException) cause));
        }
    }

    public static List<PhysAddress> getPhysAddress(List<String> macAddress) {
        Preconditions.checkNotNull(macAddress, "macAddress cannot be null");
        List<PhysAddress> physAddresses = new ArrayList<>();
        for (String mac : macAddress) {
            physAddresses.add(new PhysAddress(mac));
        }
        return physAddresses;
    }

    public static List<StaticMacEntries> getStaticMacEntries(List<String> staticMacAddresses) {
        if (isEmpty(staticMacAddresses)) {
            return Collections.EMPTY_LIST;
        }
        StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
        List<StaticMacEntries> staticMacEntries = new ArrayList<>();
        List<PhysAddress> physAddressList = getPhysAddress(staticMacAddresses);
        for (PhysAddress physAddress : physAddressList) {
            staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress).build());
        }
        return staticMacEntries;
    }

    public static InstanceIdentifier<StaticMacEntries> getStaticMacEntriesCfgDataPathIdentifier(String interfaceName,
                                                                                                String macAddress) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).child(StaticMacEntries.class,
                        new StaticMacEntriesKey(new PhysAddress(macAddress))).build();
    }

    public static List<StaticMacEntries> getDeletedEntries(List<StaticMacEntries> originalStaticMacEntries,
                                                           List<StaticMacEntries> updatedStaticMacEntries) {
        if (isEmpty(originalStaticMacEntries)) {
            return Collections.EMPTY_LIST;
        }
        List<StaticMacEntries> deleted = Lists.newArrayList(originalStaticMacEntries);
        if (isNotEmpty(updatedStaticMacEntries)) {
            deleted.removeAll(updatedStaticMacEntries);
        }
        return deleted;
    }

    public static <T> List<T> diffOf(List<T> orig, List<T> updated) {
        if (isEmpty(orig)) {
            return Collections.EMPTY_LIST;
        }
        List<T> diff = Lists.newArrayList(orig);
        if (isNotEmpty(updated)) {
            diff.removeAll(updated);
        }
        return diff;
    }

    public static void segregateToBeDeletedAndAddEntries(List<StaticMacEntries> originalStaticMacEntries,
                                                             List<StaticMacEntries> updatedStaticMacEntries) {
        if (isNotEmpty(updatedStaticMacEntries)) {
            List<StaticMacEntries> existingClonedStaticMacEntries = new ArrayList<>();
            if (isNotEmpty(originalStaticMacEntries)) {
                existingClonedStaticMacEntries.addAll(0, originalStaticMacEntries);
                originalStaticMacEntries.removeAll(updatedStaticMacEntries);
                updatedStaticMacEntries.removeAll(existingClonedStaticMacEntries);
            }
        }
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection collection) {
        return !isEmpty(collection);
    }

    public static void setElanInstancToDpnsCache(Map<String, Set<DpnInterfaces>> elanInstancToDpnsCache) {
        ElanUtils.elanInstancToDpnsCache = elanInstancToDpnsCache;
    }

    public static Set<DpnInterfaces> getElanInvolvedDPNsFromCache(String elanName) {
        return elanInstancToDpnsCache.get(elanName);
    }

    public static void addDPNInterfaceToElanInCache(String elanName, DpnInterfaces dpnInterfaces) {
        elanInstancToDpnsCache.computeIfAbsent(elanName, key -> new HashSet<>()).add(dpnInterfaces);
    }

    public static void removeDPNInterfaceFromElanInCache(String elanName, DpnInterfaces dpnInterfaces) {
        elanInstancToDpnsCache.computeIfAbsent(elanName, key -> new HashSet<>()).remove(dpnInterfaces);
    }

    public Optional<IpAddress> getSourceIpAddress(Ethernet ethernet) {
        Optional<IpAddress> srcIpAddress = Optional.absent();
        if (ethernet.getPayload() == null) {
            return srcIpAddress;
        }
        byte[] ipAddrBytes = null;
        if (ethernet.getPayload() instanceof IPv4) {
            IPv4 ipv4 = (IPv4) ethernet.getPayload();
            ipAddrBytes = Ints.toByteArray(ipv4.getSourceAddress());
        } else if (ethernet.getPayload() instanceof ARP) {
            ipAddrBytes = ((ARP) ethernet.getPayload()).getSenderProtocolAddress();
        }
        if (ipAddrBytes != null) {
            String ipAddr = NWUtil.toStringIpAddress(ipAddrBytes);
            return Optional.of(IpAddressBuilder.getDefaultInstance(ipAddr));
        }
        return srcIpAddress;
    }

    public List<MacEntry> getElanMacEntries(String elanName) {
        MacTable macTable = getElanMacTable(elanName);
        if (macTable == null) {
            return Collections.emptyList();
        }
        return macTable.getMacEntry();
    }

    public boolean isTunnelInLogicalGroup(String interfaceName, DataBroker broker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.Interface configIface =
            interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        IfTunnel ifTunnel = configIface.getAugmentation(IfTunnel.class);
        if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            ParentRefs refs = configIface.getAugmentation(ParentRefs.class);
            if (refs != null && !Strings.isNullOrEmpty(refs.getParentInterface())) {
                return true; //multiple VxLAN tunnels enabled, i.e. only logical tunnel should be treated
            }
        }
        return false;
    }

    public static void addElanInterfaceToElanInstanceCache(String elanInstanceName, String elanInterfaceName) {
        elanInstanceToInterfacesCache.computeIfAbsent(elanInstanceName, key -> new HashSet<>()).add(elanInterfaceName);
    }

    public static void removeElanInterfaceToElanInstanceCache(String elanInstanceName, String interfaceName) {
        Set<String> elanInterfaces = elanInstanceToInterfacesCache.get(elanInstanceName);
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return;
        }
        elanInterfaces.remove(interfaceName);
    }

    @Nonnull public static Set<String> removeAndGetElanInterfaces(String elanInstanceName) {
        Set<String> removed = elanInstanceToInterfacesCache.remove(elanInstanceName);
        return removed != null ? removed : Collections.emptySet();
    }

    public static InstanceIdentifier<Flow> getFlowIid(Flow flow, BigInteger dpnId) {
        FlowKey flowKey = new FlowKey(new FlowId(flow.getId()));
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("openflow:" + dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();
        return InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                        nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();
    }

    public static String getElanInterfaceJobKey(String interfaceName) {
        return "elaninterface-" + interfaceName;
    }

    public void addArpResponderFlow(BigInteger dpnId, String ingressInterfaceName, String ipAddress, String macAddress,
            int lportTag, List<Instruction> instructions) {
        LOG.info("Installing the ARP responder flow on DPN {} for Interface {} with MAC {} & IP {}", dpnId,
                ingressInterfaceName, macAddress, ipAddress);
        ElanInterface elanIface = getElanInterfaceByElanInterfaceName(broker, ingressInterfaceName);
        ElanInstance elanInstance = getElanInstanceByName(broker, elanIface.getElanInstanceName());
        if (elanInstance == null) {
            LOG.debug("addArpResponderFlow: elanInstance is null, Failed to install arp responder flow for Interface {}"
                      + " with MAC {} & IP {}", dpnId,
                ingressInterfaceName, macAddress, ipAddress);
            return;
        }
        String flowId = ArpResponderUtil.getFlowId(lportTag, ipAddress);
        ArpResponderUtil.installFlow(mdsalManager, dpnId, flowId, flowId, NwConstants.DEFAULT_ARP_FLOW_PRIORITY,
                ArpResponderUtil.generateCookie(lportTag, ipAddress),
                ArpResponderUtil.getMatchCriteria(lportTag, elanInstance, ipAddress), instructions);
        LOG.info("Installed the ARP Responder flow for Interface {}", ingressInterfaceName);
    }

    public void removeArpResponderFlow(BigInteger dpnId, String ingressInterfaceName, String ipAddress,
            int lportTag) {
        LOG.info("Removing the ARP responder flow on DPN {} of Interface {} with IP {}", dpnId, ingressInterfaceName,
                ipAddress);
        ArpResponderUtil.removeFlow(mdsalManager, dpnId, ArpResponderUtil.getFlowId(lportTag, ipAddress));
    }
}


