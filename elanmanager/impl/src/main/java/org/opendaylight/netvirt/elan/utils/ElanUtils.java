/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.api.IITMProvider;
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
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.Acquired;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.internal.ElanGroupCache;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.BucketId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.Buckets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ElanSegmentsKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionRegLoadNodesNodeGroupBucketsBucketActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanUtils {
    private static final class ElanLockName {
        private final String macAddress;
        private final Uint64 dpnId;
        private final long elanTag;

        ElanLockName(long elanTag, String macAddress, Uint64 dpnId) {
            this.elanTag = elanTag;
            this.macAddress = macAddress;
            this.dpnId = dpnId;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(elanTag) + Objects.hash(macAddress, dpnId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ElanLockName)) {
                return false;
            }
            final ElanLockName other = (ElanLockName) obj;
            return elanTag == other.elanTag && Objects.equals(macAddress, other.macAddress)
                    && Objects.equals(dpnId, other.dpnId);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ElanUtils.class);
    private static final NamedLocks<ElanLockName> ELAN_LOCKS = new NamedLocks<>();

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final OdlInterfaceRpcService interfaceManagerRpcService;
    private final ItmRpcService itmRpcService;
    private final IInterfaceManager interfaceManager;
    private final ElanConfig elanConfig;
    private final ElanItmUtils elanItmUtils;
    private final ElanEtreeUtils elanEtreeUtils;
    private final ElanInterfaceCache elanInterfaceCache;
    private final IITMProvider iitmProvider;
    private final ElanGroupCache elanGroupCache;

    public static final FutureCallback<CommitInfo> DEFAULT_CALLBACK = new FutureCallback<>() {
        @Override
        public void onSuccess(CommitInfo result) {
            LOG.debug("Success in Datastore operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore operation", error);
        }
    };

    @Inject
    public ElanUtils(DataBroker dataBroker, IMdsalApiManager mdsalManager,
            OdlInterfaceRpcService interfaceManagerRpcService, ItmRpcService itmRpcService, ElanConfig elanConfig,
            IInterfaceManager interfaceManager, ElanEtreeUtils elanEtreeUtils, ElanItmUtils elanItmUtils,
            ElanInterfaceCache elanInterfaceCache, IITMProvider iitmProvider, ElanGroupCache elanGroupCache) {
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.interfaceManagerRpcService = interfaceManagerRpcService;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.elanConfig = elanConfig;
        this.elanEtreeUtils = elanEtreeUtils;
        this.elanItmUtils = elanItmUtils;
        this.elanInterfaceCache = elanInterfaceCache;
        this.iitmProvider = iitmProvider;
        this.elanGroupCache = elanGroupCache;
    }

    public final Boolean isOpenstackVniSemanticsEnforced() {
        return elanConfig.isOpenstackVniSemanticsEnforced() != null
                ? elanConfig.isOpenstackVniSemanticsEnforced() : false;
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
    public static Uint32 retrieveNewElanTag(IdManagerService idManager, String idKey) {

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
        return Uint32.valueOf(0L);
    }

    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput releaseIdInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        LoggingFutures.addErrorLogging(idManager.releaseId(releaseIdInput), LOG, "Release Id");
    }

    /**
     * Read utility.
     *
     * @deprecated Consider using {@link #read2(LogicalDatastoreType, InstanceIdentifier)} with proper exception
     *             handling instead
     * @param broker dataBroker
     * @param datastoreType Logical DataStore type
     * @param path IID to read
     * @param <T> T extends DataObject
     * @return the read value T
     */
    @Deprecated
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(@NonNull DataBroker broker,
            LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (ExecutionException | InterruptedException  e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends DataObject> Optional<T> read2(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path)
            throws InterruptedException, ExecutionException {
        try (ReadTransaction tx = broker.newReadOnlyTransaction()) {
            FluentFuture<Optional<T>> checkedFuture = tx.read(datastoreType, path);
            return checkedFuture.get();
        }
    }

    @SuppressWarnings("checkstyle:ForbidCertainMethod")
    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
                                                            InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        FluentFuture<? extends @NonNull CommitInfo> future = tx.commit();
        future.addCallback(DEFAULT_CALLBACK, MoreExecutors.directExecutor());
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    // elan-state Operational container
    @Nullable
    public static Elan getElanByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<Elan> elanIdentifier = getElanInstanceOperationalDataPath(elanInstanceName);
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.OPERATIONAL,
                    elanIdentifier).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getElanByName: Exception while reading elan-instance DS for the elan instance {}",
                    elanInstanceName, e);
            return null;
        }
    }

    @Nullable
    public static Elan getElanByName(TypedReadTransaction<Operational> tx, String elanInstanceName)
            throws ExecutionException, InterruptedException {
        return tx.read(getElanInstanceOperationalDataPath(elanInstanceName)).get().orElse(null);
    }

    public static InstanceIdentifier<Elan> getElanInstanceOperationalDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class, new ElanKey(elanInstanceName)).build();
    }

    // grouping of forwarding-entries
    @Nullable
    public MacEntry getInterfaceMacEntriesOperationalDataPath(String interfaceName, PhysAddress physAddress) {
        InstanceIdentifier<MacEntry> existingMacEntryId = getInterfaceMacEntriesIdentifierOperationalDataPath(
                interfaceName, physAddress);
        return read(broker, LogicalDatastoreType.OPERATIONAL, existingMacEntryId).orElse(null);
    }

    @Nullable
    public MacEntry getInterfaceMacEntriesOperationalDataPathFromId(
        TypedReadTransaction<Operational> tx,
            InstanceIdentifier<MacEntry> identifier) throws ExecutionException, InterruptedException {
        return tx.read(identifier).get().orElse(null);
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

    public Optional<MacEntry> getMacEntryForElanInstance(TypedReadTransaction<Operational> tx, String elanName,
            PhysAddress physAddress) throws ExecutionException, InterruptedException {
        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanName, physAddress);
        return tx.read(macId).get();
    }

    @Nullable
    public MacEntry getMacEntryFromElanMacId(TypedReadTransaction<Operational> tx,
            InstanceIdentifier<MacEntry> identifier) throws ExecutionException, InterruptedException {
        return tx.read(identifier).get().orElse(null);
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
        return getElanInterfaceMacByInterfaceName(broker, interfaceName);
    }

    @Nullable
    public static ElanInterfaceMac getElanInterfaceMacByInterfaceName(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = getElanInterfaceMacEntriesOperationalDataPath(
                interfaceName);
        return read(dataBroker, LogicalDatastoreType.OPERATIONAL, elanInterfaceId).orElse(null);
    }

    public static InstanceIdentifier<ElanInterfaceMac> getElanInterfaceMacEntriesOperationalDataPath(
            String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaceForwardingEntries.class)
                .child(ElanInterfaceMac.class, new ElanInterfaceMacKey(interfaceName)).build();
    }

    /**
     * Returns the list of Interfaces that belong to an Elan on an specific DPN.
     * Data retrieved from Elan's operational DS: elan-dpn-interfaces container.
     *
     * @param elanInstanceName
     *            name of the Elan to which the interfaces must belong to
     * @param dpId
     *            Id of the DPN where the interfaces are located
     * @return the elan interface Info
     */
    @Nullable
    public DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, Uint64 dpId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                dpId);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId).orElse(null);
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
            Uint64 dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
    }

    // elan-tag-name-map Operational Container
    @Nullable
    public ElanTagName getElanInfoByElanTag(Uint32 elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = read(broker,
                LogicalDatastoreType.OPERATIONAL, elanId);
        return existingElanInfo.orElse(null);
    }

    public static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(Uint32 elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class, new ElanTagNameKey(elanTag))
                .build();
    }

    // interface-index-tag operational container
    public Optional<IfIndexInterface> getInterfaceInfoByInterfaceTag(Uint32 interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        return read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
    }

    public static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(Uint32 interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class)
                .child(IfIndexInterface.class,
                        new IfIndexInterfaceKey(Integer.valueOf(interfaceTag.intValue()))).build();
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
    }

    @Nullable
    public ElanDpnInterfacesList getElanDpnInterfacesList(String elanName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanName);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId).orElse(null);
    }

    @Nullable
    public ElanDpnInterfaces getElanDpnInterfacesList() {
        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfaceId = InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .build();
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId).orElse(null);
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
    @NonNull
    public List<Uint64> getParticipatingDpnsInElanInstance(String elanInstanceName) {
        List<Uint64> dpIds = new ArrayList<>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        Optional<ElanDpnInterfacesList> existingElanDpnInterfaces = read(broker,
                LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
        if (!existingElanDpnInterfaces.isPresent()) {
            return dpIds;
        }
        Map<DpnInterfacesKey, DpnInterfaces> dpnInterfaces = existingElanDpnInterfaces.get().nonnullDpnInterfaces();
        for (DpnInterfaces dpnInterface : dpnInterfaces.values()) {
            dpIds.add(dpnInterface.getDpId());
        }
        return dpIds;
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
    @Nullable
    public MacTable getElanMacTable(String elanName) {
        return getElanMacTable(broker, elanName);
    }

    @Nullable
    public static MacTable getElanMacTable(DataBroker dataBroker, String elanName) {
        InstanceIdentifier<MacTable> elanMacTableId = getElanMacTableOperationalDataPath(elanName);
        return read(dataBroker, LogicalDatastoreType.OPERATIONAL, elanMacTableId).orElse(null);
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

    public static Uint64 getElanMetadataLabel(long elanTag, boolean isSHFlagSet) {
        int shBit = isSHFlagSet ? 1 : 0;
        return Uint64.valueOf(BigInteger.valueOf(elanTag).shiftLeft(24).or(BigInteger.valueOf(shBit)));
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
     */
    public void setupMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
                              long macTimeout, String macAddress, boolean configureRemoteFlows,
                              TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        try (Acquired lock = lockElanMacDPN(elanInfo.getElanTag().toJava(), macAddress, interfaceInfo.getDpId())) {
            setupKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress, mdsalManager,
                writeFlowGroupTx);
            setupOrigDmacFlows(elanInfo, interfaceInfo, macAddress, configureRemoteFlows, mdsalManager,
                    writeFlowGroupTx);
        }
    }

    public void setupDMacFlowOnRemoteDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo, Uint64 dstDpId,
                                         String macAddress, TypedWriteTransaction<Configuration> writeFlowTx) {
        String elanInstanceName = elanInfo.getElanInstanceName();
        setupRemoteDmacFlow(dstDpId, interfaceInfo.getDpId(), interfaceInfo.getInterfaceTag(),
                elanInfo.getElanTag(), macAddress, elanInstanceName, writeFlowTx,
                interfaceInfo.getInterfaceName(), elanInfo);
        LOG.info("Remote Dmac flow entry created for elan Name:{}, logical port Name:{} and"
                + " mac address {} on dpn:{}", elanInstanceName, interfaceInfo.getPortName(),
                macAddress, dstDpId);
    }

    /**
     * Inserts a Flow in SMAC table to state that the MAC has already been
     * learnt.
     */
    private void setupKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress, IMdsalApiManager mdsalApiManager,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        FlowEntity flowEntity = buildKnownSmacFlow(elanInfo, interfaceInfo, macTimeout, macAddress);
        mdsalApiManager.addFlow(writeFlowGroupTx, flowEntity);
        LOG.debug("Known Smac flow entry created for elan Name:{}, logical Interface port:{} and mac address:{}",
                elanInfo.getElanInstanceName(), elanInfo.getDescription(), macAddress);
    }

    public FlowEntity buildKnownSmacFlow(ElanInstance elanInfo, InterfaceInfo interfaceInfo, long macTimeout,
            String macAddress) {
        int lportTag = interfaceInfo.getInterfaceTag();
        // Matching metadata and eth_src fields
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanInfo.getElanTag().toJava(), lportTag),
                ElanHelper.getElanMetadataMask()));
        mkMatches.add(new MatchEthernetSource(new MacAddress(macAddress)));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        Uint64 dpId = interfaceInfo.getDpId();
        Uint32 elanTag = getElanTag(elanInfo, interfaceInfo);
        return new FlowEntityBuilder()
            .setDpnId(dpId)
            .setTableId(NwConstants.ELAN_SMAC_TABLE)
            .setFlowId(getKnownDynamicmacFlowRef(elanTag, macAddress))
            .setPriority(20)
            .setFlowName(elanInfo.getDescription())
            .setIdleTimeOut((int) macTimeout)
            .setHardTimeOut(0)
            .setCookie(Uint64.valueOf(ElanConstants.COOKIE_ELAN_KNOWN_SMAC.longValue() + elanTag.longValue()))
            .setMatchInfoList(mkMatches)
            .setInstructionInfoList(mkInstructions)
            .setStrictFlag(true)
            // If Mac timeout is 0, the flow won't be deleted automatically, so no need to get notified
            .setSendFlowRemFlag(macTimeout != 0)
            .build();
    }

    private Uint32 getElanTag(ElanInstance elanInfo, InterfaceInfo interfaceInfo) {
        EtreeInterface etreeInterface = elanInterfaceCache
            .getEtreeInterface(interfaceInfo.getInterfaceName()).orElse(null);
        if (etreeInterface == null || etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            return elanInfo.getElanTag();
        } else { // Leaf
            EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
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
                                   TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        Uint64 dpId = interfaceInfo.getDpId();
        int lportTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getIntTunnelTableFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, lportTag), 5,
                String.format("%s:%d", "ITM Flow Entry ", lportTag), 0, 0,
                Uint64.valueOf(ITMConstants.COOKIE_ITM.longValue() + lportTag),
                getTunnelIdMatchForFilterEqualsLPortTag(lportTag),
                getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));
        mdsalApiManager.addFlow(writeFlowGroupTx, dpId, flow);
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
        return new StringBuilder().append(tableId).append(elanTag).toString();
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
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(lportTag)));
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
    public Map<InstructionKey, Instruction> getInstructionsInPortForOutGroup(String ifName) {
        int instructionsKey = 0;
        Map<InstructionKey, Instruction> mkInstructions = new HashMap<>();
        List<Action> actions = getEgressActionsForInterface(ifName, /* tunnelKey */ null);

        mkInstructions.put(new InstructionKey(++instructionsKey), MDSALUtil.buildApplyActionsInstruction(actions));
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
    @NonNull
    public List<Action> getEgressActionsForInterface(String ifName, @Nullable Long tunnelKey) {
        List<Action> listAction = new ArrayList<>();
        try {
            GetEgressActionsForInterfaceInput getEgressActionInput = new GetEgressActionsForInterfaceInputBuilder()
                    .setIntfName(ifName).setTunnelKey(tunnelKey).build();
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result = interfaceManagerRpcService
                    .getEgressActionsForInterface(getEgressActionInput);
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.debug("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName,
                        rpcResult.getErrors());
            } else {
                listAction = new ArrayList<>(rpcResult.getResult().nonnullAction().values());
            }
        } catch (Exception e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listAction;
    }

    private void setupOrigDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
                                    boolean configureRemoteFlows, IMdsalApiManager mdsalApiManager,
                                    TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        Uint64 dpId = interfaceInfo.getDpId();
        String ifName = interfaceInfo.getInterfaceName();
        long ifTag = interfaceInfo.getInterfaceTag();
        String elanInstanceName = elanInfo.getElanInstanceName();

        Uint32 elanTag = elanInfo.getElanTag();

        setupLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInfo, mdsalApiManager, ifTag,
                writeFlowGroupTx);
        LOG.debug("Dmac flow entry created for elan Name:{}, logical port Name:{} mand mac address:{} "
                                    + "on dpn:{}", elanInstanceName, interfaceInfo.getPortName(), macAddress, dpId);

        if (!configureRemoteFlows) {
            return;
        }

        List<DpnInterfaces> elanDpns = getInvolvedDpnsInElan(elanInstanceName);
        for (DpnInterfaces elanDpn : elanDpns) {

            if (Objects.equals(elanDpn.getDpId(), dpId)) {
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

    @NonNull
    public List<DpnInterfaces> getInvolvedDpnsInElan(String elanName) {
        return getElanDPNByName(elanName);
    }

    @NonNull
    public List<DpnInterfaces> getElanDPNByName(String elanInstanceName) {
        InstanceIdentifier<ElanDpnInterfacesList> elanIdentifier = getElanDpnOperationDataPath(elanInstanceName);
        try {
            return new ArrayList<>(SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.OPERATIONAL, elanIdentifier).map(ElanDpnInterfacesList::nonnullDpnInterfaces)
                    .orElse(emptyMap()).values());
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getElanDPNByName: Exception while reading elanDpnInterfaceList DS for the elan "
                    + "instance {}", elanInstanceName, e);
            return emptyList();
        }
    }

    private void setupLocalDmacFlow(Uint32 elanTag, Uint64 dpId, String ifName, String macAddress,
            ElanInstance elanInfo, IMdsalApiManager mdsalApiManager, long ifTag,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        Flow flowEntity = buildLocalDmacFlowEntry(elanTag, dpId, ifName, macAddress, elanInfo, ifTag);
        mdsalApiManager.addFlow(writeFlowGroupTx, dpId, flowEntity);
        installEtreeLocalDmacFlow(elanTag, dpId, ifName, macAddress, elanInfo, ifTag, writeFlowGroupTx);
    }

    private void installEtreeLocalDmacFlow(Uint32 elanTag, Uint64 dpId, String ifName, String macAddress,
            ElanInstance elanInfo, long ifTag, TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        EtreeInterface etreeInterface = elanInterfaceCache.getEtreeInterface(ifName).orElse(null);
        if (etreeInterface != null && etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            EtreeLeafTagName etreeTagName = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag.longValue());
            if (etreeTagName == null) {
                LOG.warn("Interface {} seems like it belongs to Etree but etreeTagName from elanTag {} is null",
                        ifName, elanTag);
            } else {
                Flow flowEntity = buildLocalDmacFlowEntry(etreeTagName.getEtreeLeafTag().getValue(), dpId,
                        ifName, macAddress, elanInfo, ifTag);
                mdsalManager.addFlow(writeFlowGroupTx, dpId, flowEntity);
            }
        }
    }

    public static String getKnownDynamicmacFlowRef(Uint32 elanTag, String macAddress) {
        return new StringBuilder().append(elanTag).append(macAddress.toLowerCase(Locale.getDefault())).toString();
    }

    public static String getKnownDynamicmacFlowRef(short elanDmacTable, Uint64 dpId, String extDeviceNodeId,
            String dstMacAddress, long elanTag, boolean shFlag) {
        return String.valueOf(elanDmacTable) + elanTag + dpId + extDeviceNodeId + dstMacAddress + shFlag;
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
    public Flow buildLocalDmacFlowEntry(Uint32 elanTag, Uint64 dpId, String ifName, String macAddress,
            ElanInstance elanInfo, long ifTag) {

        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag.longValue()),
            MetaDataUtil.METADATA_MASK_SERVICE));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddress)));

        Map<InstructionKey, Instruction> mkInstructions = new HashMap<>();
        List<Action> actions = getEgressActionsForInterface(ifName, /* tunnelKey */ null);
        mkInstructions.put(new InstructionKey(0), MDSALUtil.buildApplyActionsInstruction(actions));
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(elanTag, macAddress), 20,
                elanInfo.getElanInstanceName(), 0, 0,
                Uint64.valueOf(ElanConstants.COOKIE_ELAN_KNOWN_DMAC.longValue() + elanTag.longValue()),
                mkMatches, mkInstructions);

        return flow;
    }

    public void setupRemoteDmacFlow(Uint64 srcDpId, Uint64 destDpId, int lportTag, Uint32 elanTag,
            String macAddress, String displayName, TypedWriteTransaction<Configuration> writeFlowGroupTx,
            String interfaceName, ElanInstance elanInstance) {
        if (interfaceManager.isExternalInterface(interfaceName)) {
            LOG.debug("Ignoring install remote DMAC {} flow on provider interface {} elan {}",
                    macAddress, interfaceName, elanInstance.getElanInstanceName());
            return;
        }
        // if openstack-vni-semantics are enforced, segmentation ID is passed as network VNI for VxLAN based provider
        // networks, 0 otherwise
        long lportTagOrVni = !isOpenstackVniSemanticsEnforced() ? lportTag : isVxlanNetworkOrVxlanSegment(elanInstance)
                ? getVxlanSegmentationId(elanInstance).longValue() : 0;
        Flow flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTagOrVni, elanTag, macAddress, displayName,
                elanInstance);
        mdsalManager.addFlow(writeFlowGroupTx, srcDpId, flowEntity);
        setupEtreeRemoteDmacFlow(srcDpId, destDpId, lportTagOrVni, elanTag, macAddress, displayName, interfaceName,
                writeFlowGroupTx, elanInstance);
    }

    private void setupEtreeRemoteDmacFlow(Uint64 srcDpId, Uint64 destDpId, long lportTagOrVni, Uint32 elanTag,
                                          String macAddress, String displayName, String interfaceName,
                                          TypedWriteTransaction<Configuration> writeFlowGroupTx,
                                          ElanInstance elanInstance) {
        Flow flowEntity;
        EtreeInterface etreeInterface = elanInterfaceCache.getEtreeInterface(interfaceName).orElse(null);
        if (etreeInterface != null && etreeInterface.getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            EtreeLeafTagName etreeTagName = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag.longValue());
            if (etreeTagName == null) {
                LOG.warn("Interface {} seems like it belongs to Etree but etreeTagName from elanTag {} is null.",
                        interfaceName, elanTag);
            } else {
                flowEntity = buildRemoteDmacFlowEntry(srcDpId, destDpId, lportTagOrVni,
                        etreeTagName.getEtreeLeafTag().getValue(), macAddress, displayName, elanInstance);
                mdsalManager.addFlow(writeFlowGroupTx, srcDpId, flowEntity);
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
     * @param elanInstance
     *            elanInstance
     * @return the flow remote Dmac
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Flow buildRemoteDmacFlowEntry(Uint64 srcDpId, Uint64 destDpId, long lportTagOrVni, Uint32 elanTag,
            String macAddress, String displayName, ElanInstance elanInstance) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag.longValue()),
            MetaDataUtil.METADATA_MASK_SERVICE));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddress)));

        Map<InstructionKey, Instruction> mkInstructions = new HashMap<>();

        // List of Action for the provided Source and Destination DPIDs
        try {
            List<Action> actions = null;
            if (isVxlanNetworkOrVxlanSegment(elanInstance)) {
                actions = elanItmUtils.getInternalTunnelItmEgressAction(srcDpId, destDpId, lportTagOrVni);
            } else if (isVlan(elanInstance) || isFlat(elanInstance)) {
                String interfaceName = getExternalElanInterface(elanInstance.getElanInstanceName(), srcDpId);
                if (null == interfaceName) {
                    LOG.info("buildRemoteDmacFlowEntry: Could not find interfaceName for {} {}", srcDpId,
                            elanInstance);
                }
                actions = getEgressActionsForInterface(interfaceName, null);
            }
            mkInstructions.put(new InstructionKey(0), MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get egress actions to add to flow for srcDpId {}, destDpId {}, lportTag/VNI {}",
                    srcDpId,  destDpId, lportTagOrVni, e);
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(elanTag, macAddress),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                Uint64.valueOf(ElanConstants.COOKIE_ELAN_KNOWN_DMAC.longValue() + elanTag.longValue()),
                mkMatches, mkInstructions);

        return flow;

    }

    public void deleteMacFlows(@Nullable ElanInstance elanInfo, @Nullable InterfaceInfo interfaceInfo,
            MacEntry macEntry, TypedReadWriteTransaction<Configuration> flowTx)
            throws ExecutionException, InterruptedException {
        if (elanInfo == null || interfaceInfo == null) {
            return;
        }
        String macAddress = macEntry.getMacAddress().getValue();
        try (Acquired lock = lockElanMacDPN(elanInfo.getElanTag().toJava(),
                macAddress, interfaceInfo.getDpId())) {
            deleteMacFlows(elanInfo, interfaceInfo, macAddress, /* alsoDeleteSMAC */ true, flowTx);
        }
    }

    public void deleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, TypedReadWriteTransaction<Configuration> flowTx)
            throws ExecutionException, InterruptedException {
        String elanInstanceName = elanInfo.getElanInstanceName();
        List<DpnInterfaces> remoteFEs = getInvolvedDpnsInElan(elanInstanceName);
        Uint64 srcdpId = interfaceInfo.getDpId();
        boolean isFlowsRemovedInSrcDpn = false;
        for (DpnInterfaces dpnInterface : remoteFEs) {
            Uint32 elanTag = elanInfo.getElanTag();
            Uint64 dstDpId = dpnInterface.getDpId();
            if (executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    elanTag, dstDpId, flowTx)) {
                isFlowsRemovedInSrcDpn = true;
            }
            executeEtreeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    elanTag, dstDpId, flowTx);
        }
        if (!isFlowsRemovedInSrcDpn) {
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, flowTx);
        }
    }

    private void executeEtreeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, String elanInstanceName, Uint64 srcdpId, Uint32 elanTag, Uint64 dstDpId,
            TypedReadWriteTransaction<Configuration> flowTx) throws ExecutionException, InterruptedException {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag.longValue());
        if (etreeLeafTag != null) {
            executeDeleteMacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, elanInstanceName, srcdpId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), dstDpId, flowTx);
        }
    }

    private boolean executeDeleteMacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, String elanInstanceName, Uint64 srcdpId, Uint32 elanTag, Uint64 dstDpId,
            TypedReadWriteTransaction<Configuration> flowTx) throws ExecutionException, InterruptedException {
        boolean isFlowsRemovedInSrcDpn = false;
        if (dstDpId.equals(srcdpId)) {
            isFlowsRemovedInSrcDpn = true;
            deleteSmacAndDmacFlows(elanInfo, interfaceInfo, macAddress, deleteSmac, flowTx);
        } else if (isDpnPresent(dstDpId)) {
            mdsalManager
                .removeFlow(flowTx, dstDpId,
                    MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE, getKnownDynamicmacFlowRef(elanTag, macAddress)));
            LOG.debug("Dmac flow entry deleted for elan:{}, logical interface port:{} and mac address:{} on dpn:{}",
                    elanInstanceName, interfaceInfo.getPortName(), macAddress, dstDpId);
        }
        return isFlowsRemovedInSrcDpn;
    }

    public void deleteSmacFlowOnly(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
                                   TypedReadWriteTransaction<Configuration> flowTx) throws ExecutionException,
            InterruptedException {
        Uint64 srcdpId = interfaceInfo.getDpId();
        Uint32 elanTag = elanInfo.getElanTag();
        LOG.debug("Deleting SMAC flow with id {}", getKnownDynamicmacFlowRef(elanTag, macAddress));
        mdsalManager.removeFlow(flowTx, srcdpId,
                MDSALUtil.buildFlow(NwConstants.ELAN_SMAC_TABLE, getKnownDynamicmacFlowRef(elanTag, macAddress)));
    }

    private void deleteSmacAndDmacFlows(ElanInstance elanInfo, InterfaceInfo interfaceInfo, String macAddress,
            boolean deleteSmac, TypedReadWriteTransaction<Configuration> flowTx)
            throws ExecutionException, InterruptedException {
        String elanInstanceName = elanInfo.getElanInstanceName();
        Uint64 srcdpId = interfaceInfo.getDpId();
        Uint32 elanTag = elanInfo.getElanTag();
        if (deleteSmac) {
            LOG.debug("Deleting SMAC flow with id {}", getKnownDynamicmacFlowRef(elanTag, macAddress));
            mdsalManager
                    .removeFlow(flowTx, srcdpId,
                            MDSALUtil.buildFlow(NwConstants.ELAN_SMAC_TABLE,
                                    getKnownDynamicmacFlowRef(elanTag, macAddress)));
        }
        mdsalManager.removeFlow(flowTx, srcdpId,
            MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                getKnownDynamicmacFlowRef(elanTag, macAddress)));
        LOG.debug("All the required flows deleted for elan:{}, logical Interface port:{} and MAC address:{} on dpn:{}",
                elanInstanceName, interfaceInfo.getPortName(), macAddress, srcdpId);
    }

    /**
     * Updates the Elan information in the Operational DS. It also updates the
     * ElanInstance in the Config DS by setting the adquired elanTag.
     *
     * @param idManager
     *            the id manager
     * @param elanInstanceAdded
     *            the elan instance added
     * @param elanInterfaces
     *            the elan interfaces
     * @param operTx
     *            transaction
     *
     * @return the updated ELAN instance.
     */
    public static ElanInstance updateOperationalDataStore(IdManagerService idManager,
            ElanInstance elanInstanceAdded, List<String> elanInterfaces, TypedWriteTransaction<Configuration> confTx,
            TypedWriteTransaction<Operational> operTx) {
        String elanInstanceName = elanInstanceAdded.getElanInstanceName();
        Uint32 elanTag = elanInstanceAdded.getElanTag();
        if (elanTag == null || elanTag.longValue() == 0L) {
            elanTag = retrieveNewElanTag(idManager, elanInstanceName);
        }
        if (elanTag.longValue() == 0L) {
            LOG.error("ELAN tag creation failed for elan instance {}. Not updating the ELAN DS. "
                    + "Recreate network for recovery", elanInstanceName);
            return null;
        }
        Elan elanInfo = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(elanInterfaces)
                .withKey(new ElanKey(elanInstanceName)).build();

        // Add the ElanState in the elan-state operational data-store
        operTx.mergeParentStructurePut(getElanInstanceOperationalDataPath(elanInstanceName), elanInfo);

        // Add the ElanMacTable in the elan-mac-table operational data-store
        MacTable elanMacTable = new MacTableBuilder().withKey(new MacTableKey(elanInstanceName)).build();
        operTx.mergeParentStructurePut(getElanMacTableOperationalDataPath(elanInstanceName), elanMacTable);

        ElanTagNameBuilder elanTagNameBuilder = new ElanTagNameBuilder().setElanTag(elanTag)
                .withKey(new ElanTagNameKey(elanTag)).setName(elanInstanceName);
        Uint32 etreeLeafTag = Uint32.valueOf(0L);
        if (isEtreeInstance(elanInstanceAdded)) {
            etreeLeafTag = retrieveNewElanTag(idManager,elanInstanceName + ElanConstants
                    .LEAVES_POSTFIX);
            EtreeLeafTagName etreeLeafTagName = new EtreeLeafTagNameBuilder()
                    .setEtreeLeafTag(new EtreeLeafTag(etreeLeafTag)).build();
            elanTagNameBuilder.addAugmentation(etreeLeafTagName);
            addTheLeafTagAsElanTag(elanInstanceName, etreeLeafTag, operTx);
        }
        ElanTagName elanTagName = elanTagNameBuilder.build();

        // Add the ElanTag to ElanName in the elan-tag-name Operational
        // data-store
        operTx.put(getElanInfoEntriesOperationalDataPath(elanTag), elanTagName);

        // Updates the ElanInstance Config DS by setting the just acquired
        // elanTag
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstanceAdded)
                .setElanInstanceName(elanInstanceName)
                .setDescription(elanInstanceAdded.getDescription())
                .setMacTimeout(elanInstanceAdded.getMacTimeout() == null
                        ? Uint32.valueOf(ElanConstants.DEFAULT_MAC_TIME_OUT) : elanInstanceAdded.getMacTimeout())
                .withKey(elanInstanceAdded.key()).setElanTag(elanTag);
        if (isEtreeInstance(elanInstanceAdded)) {
            EtreeInstance etreeInstance = new EtreeInstanceBuilder().setEtreeLeafTagVal(new EtreeLeafTag(etreeLeafTag))
                    .build();
            elanInstanceBuilder.addAugmentation(etreeInstance);
        }
        ElanInstance elanInstanceWithTag = elanInstanceBuilder.build();
        LOG.trace("Updated elan Operational DS for elan: {} with elanTag: {} and interfaces: {}", elanInstanceName,
                elanTag, elanInterfaces);
        confTx.mergeParentStructureMerge(ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName),
                elanInstanceWithTag);
        return elanInstanceWithTag;
    }

    private static void addTheLeafTagAsElanTag(String elanInstanceName,Uint32 etreeLeafTag,
            TypedWriteTransaction<Operational> tx) {
        ElanTagName etreeTagAsElanTag = new ElanTagNameBuilder().setElanTag(etreeLeafTag)
                .withKey(new ElanTagNameKey(etreeLeafTag)).setName(elanInstanceName).build();
        tx.put(getElanInfoEntriesOperationalDataPath(etreeLeafTag), etreeTagAsElanTag);
    }

    private static boolean isEtreeInstance(ElanInstance elanInstanceAdded) {
        return elanInstanceAdded.augmentation(EtreeInstance.class) != null;
    }

    public boolean isDpnPresent(Uint64 dpnId) {
        String dpn = String.format("%s:%s", "openflow", dpnId);
        NodeId nodeId = new NodeId(dpn);

        InstanceIdentifier<Node> node = InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId))
                .build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, node).isPresent();
    }

    public static String getElanServiceName(String elanName, String interfaceName) {
        return "elan." + elanName + interfaceName;
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            Uint64 cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().withKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(augBuilder.build()).build();
    }

    public static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short serviceIndex) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }

    public static List<MatchInfo> getTunnelMatchesForServiceId(Uint32 elanTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        // Adding 270000 to avoid collision between LPort and elan tag for broadcast
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(elanTag.longValue() + ElanConstants.ELAN_TAG_ADDEND)));

        return mkMatches;
    }

    public void removeTerminatingServiceAction(Uint64 destDpId, int serviceId) {
        RemoveTerminatingServiceActionsInput input = new RemoveTerminatingServiceActionsInputBuilder()
                .setDpnId(destDpId).setServiceId(serviceId).build();
        Future<RpcResult<RemoveTerminatingServiceActionsOutput>> futureObject =
                itmRpcService.removeTerminatingServiceActions(input);
        try {
            RpcResult<RemoveTerminatingServiceActionsOutput> result = futureObject.get();
            if (result.isSuccessful()) {
                LOG.debug("Successfully completed removeTerminatingServiceActions for ELAN with serviceId {} on "
                                + "dpn {}", serviceId, destDpId);
            } else {
                LOG.debug("Failure in removeTerminatingServiceAction RPC call for ELAN with serviceId {} on "
                        + "dpn {}", serviceId, destDpId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call removeTerminatingServiceActions for ELAN with serviceId {} on "
                    + "dpn {}", serviceId, destDpId, e);
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
    @Nullable
    public ExternalTunnel getExternalTunnel(String sourceDevice, String destinationDevice,
            LogicalDatastoreType datastoreType) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        InstanceIdentifier<ExternalTunnel> iid = InstanceIdentifier.builder(ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(destinationDevice, sourceDevice, tunType)).build();
        return read(broker, datastoreType, iid).orElse(null);
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
    @Nullable
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
        return new ArrayList<>(read(broker, datastoreType, iid).map(ExternalTunnelList
                ::nonnullExternalTunnel).orElse(Collections.emptyMap()).values());
    }

    public static List<MatchInfo> buildMatchesForElanTagShFlagAndDstMac(long elanTag, boolean shFlag, String macAddr) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(
                new MatchMetadata(getElanMetadataLabel(elanTag, shFlag), MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(macAddr)));
        return mkMatches;
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .@Nullable Interface getInterfaceStateFromOperDS(String interfaceName, DataBroker dataBroker) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    createInterfaceStateInstanceIdentifier(interfaceName)).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getInterfaceStateFromOperDS: Exception while reading interface DS for the interface {}",
                    interfaceName, e);
            return null;
        }
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

    @CheckReturnValue
    public static ListenableFuture<?> waitForTransactionToComplete(ListenableFuture<?> future) {
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            // NETVIRT-1215: Do not log.error() here, only debug(); but callers *MUST* @CheckReturnValue
            LOG.debug("Error writing to datastore", e);
        }
        return future;
    }

    public static boolean isVxlan(@Nullable ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().toJava() != 0;
    }

    private static boolean isVxlanSegment(@Nullable ElanInstance elanInstance) {
        if (elanInstance != null) {
            Map<ElanSegmentsKey, ElanSegments> elanSegments = elanInstance.nonnullElanSegments();
            if (elanSegments != null) {
                for (ElanSegments segment : elanSegments.values()) {
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

    public static boolean isVxlanNetworkOrVxlanSegment(@Nullable ElanInstance elanInstance) {
        return isVxlan(elanInstance) || isVxlanSegment(elanInstance);
    }

    public static Uint32 getVxlanSegmentationId(ElanInstance elanInstance) {
        Uint32 segmentationId = Uint32.ZERO;
        if (elanInstance == null) {
            return segmentationId;
        }

        if (elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVxlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().longValue() != 0) {
            segmentationId = elanInstance.getSegmentationId();
        } else {
            for (ElanSegments segment: elanInstance.nonnullElanSegments().values()) {
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
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().toJava() != 0;
    }

    public static boolean isFlat(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeFlat.class);
    }

    public void addDmacRedirectToDispatcherFlows(Uint32 elanTag, String displayName,
            String macAddress, List<Uint64> dpnIds) {
        for (Uint64 dpId : dpnIds) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> mdsalManager.addFlow(tx, buildDmacRedirectToDispatcherFlow(dpId, macAddress, displayName,
                        elanTag))), LOG,
                "Error adding DMAC redirect to dispatcher flows");
        }
    }

    public void removeDmacRedirectToDispatcherFlows(Uint32 elanTag, String macAddress, List<Uint64> dpnIds) {
        LoggingFutures.addErrorLogging(
            txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                for (Uint64 dpId : dpnIds) {
                    String flowId = getKnownDynamicmacFlowRef(elanTag, macAddress);
                    mdsalManager.removeFlow(tx, dpId, MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE, flowId));
                }
            }), LOG, "Error removing DMAC redirect to dispatcher flows");
    }

    public static FlowEntity buildDmacRedirectToDispatcherFlow(Uint64 dpId, String dstMacAddress,
            String displayName, Uint32 elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag.longValue()),
                MetaDataUtil.METADATA_MASK_SERVICE));
        matches.add(new MatchEthernetDestination(new MacAddress(dstMacAddress)));
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

        instructions.add(new InstructionApplyActions(actions));
        String flowId = getKnownDynamicmacFlowRef(elanTag, dstMacAddress);
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, flowId, 20, displayName, 0, 0,
                Uint64.valueOf(ElanConstants.COOKIE_ELAN_KNOWN_DMAC.longValue() + elanTag.longValue()),
                matches, instructions);
    }

    @Nullable
    public String getExternalElanInterface(String elanInstanceName, Uint64 dpnId) {
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

    public static Acquired lockElanMacDPN(long elanTag, String macAddress, Uint64 dpnId) {
        return ELAN_LOCKS.acquire(new ElanLockName(elanTag, macAddress, dpnId));
    }

    public static List<ListenableFuture<?>>
        returnFailedListenableFutureIfTransactionCommitFailedExceptionCauseOrElseThrow(RuntimeException exception) {

        Throwable cause = exception.getCause();
        if (cause != null && cause instanceof TransactionCommitFailedException) {
            return Collections.singletonList(Futures.immediateFailedFuture(cause));
        } else {
            throw exception;
        }
    }

    public static List<PhysAddress> getPhysAddress(List<String> macAddress) {
        requireNonNull(macAddress, "macAddress cannot be null");
        List<PhysAddress> physAddresses = new ArrayList<>();
        for (String mac : macAddress) {
            physAddresses.add(new PhysAddress(mac));
        }
        return physAddresses;
    }

    public static List<StaticMacEntries> getStaticMacEntries(List<String> staticMacAddresses) {
        if (isEmpty(staticMacAddresses)) {
            return emptyList();
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

    public static <T> List<T> diffOf(List<T> orig, List<T> updated) {
        if (isEmpty(orig)) {
            return emptyList();
        }
        List<T> diff = Lists.newArrayList(orig);
        if (isNotEmpty(updated)) {
            diff.removeAll(updated);
        }
        return diff;
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection collection) {
        return !isEmpty(collection);
    }

    public Optional<IpAddress> getSourceIpAddress(Ethernet ethernet) {
        Optional<IpAddress> srcIpAddress = Optional.empty();
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
            return emptyList();
        }
        return new ArrayList<>(macTable.nonnullMacEntry().values());
    }

    public boolean isTunnelInLogicalGroup(String interfaceName) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.Interface configIface =
            interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        if (configIface == null) {
            configIface = iitmProvider.getInterface(interfaceName);
        }
        if (configIface == null) {
            return  false;
        }
        IfTunnel ifTunnel = configIface.augmentation(IfTunnel.class);
        if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            ParentRefs refs = configIface.augmentation(ParentRefs.class);
            return refs != null && !Strings.isNullOrEmpty(refs.getParentInterface());
        }
        return false;
    }

    public static InstanceIdentifier<Flow> getFlowIid(Flow flow, Uint64 dpnId) {
        FlowKey flowKey = new FlowKey(new FlowId(flow.getId()));
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("openflow:" + dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new NodeBuilder().setId(nodeId).withKey(new NodeKey(nodeId)).build();
        return InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                        nodeDpn.key()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();
    }

    public static String getElanInterfaceJobKey(String interfaceName) {
        return "elaninterface-" + interfaceName;
    }

    public void removeArpResponderFlow(Uint64 dpnId, String ingressInterfaceName, String ipAddress,
            int lportTag) {
        LOG.info("Removing the ARP responder flow on DPN {} of Interface {} with IP {}", dpnId, ingressInterfaceName,
                ipAddress);
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION,
            tx -> mdsalManager.removeFlow(tx, dpnId, ArpResponderUtil.getFlowId(lportTag, ipAddress),
                NwConstants.ARP_RESPONDER_TABLE)), LOG, "Error removing ARP responder flow");
    }

    @Nullable
    public static String getRouterPordIdFromElanInstance(DataBroker dataBroker, String elanInstanceName) {
        Optional<Subnetmaps> subnetMapsData =
                read(dataBroker, LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = new ArrayList<>(subnetMapsData.get().nonnullSubnetmap().values());
            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
                    if (subnet.getNetworkId().getValue().equals(elanInstanceName)) {
                        if (subnet.getRouterInterfacePortId() != null) {
                            return subnet.getRouterInterfacePortId().getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    protected  Node buildDpnNode(Uint64 dpnId) {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819
                        .NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).withKey(new NodeKey(nodeId)).build();
        return nodeDpn;
    }

    public void syncUpdateGroup(Uint64 dpnId, Group newGroup, long delayTime,
                                TypedWriteTransaction<Datastore.Configuration> confTx) {
        Node nodeDpn = buildDpnNode(dpnId);
        long groupIdInfo = newGroup.getGroupId().getValue().longValue();
        GroupKey groupKey = new GroupKey(new GroupId(groupIdInfo));
        InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
                .child(Group.class, groupKey).build();
        LOG.trace("Performing merge operation for remote BC group for node {} with group {}", nodeDpn, newGroup);
        Optional<Group> existingGroupOpt = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION, groupInstanceId);
        if (!existingGroupOpt.isPresent()) {
            LOG.debug("Group {} doesn't exist. Performing syncInstall", groupIdInfo);
            mdsalManager.addGroup(confTx, dpnId, newGroup);
            return;
        }
        Buckets existingGroup = existingGroupOpt.get().getBuckets();
        if (existingGroup == null) {
            LOG.debug("Bucket doesn't exist for group {}. Performing syncInstall", groupIdInfo);
            mdsalManager.addGroup(confTx, dpnId, newGroup);
            return;
        }
        if (newGroup.getBuckets() == null) {
            LOG.debug("Buckets are not sent for group {}. Skipping merge operation", groupIdInfo);
            return;
        }
        List<Bucket> newBuckets = new ArrayList<>(newGroup.getBuckets().nonnullBucket().values());
        List<Bucket> existingBuckets = new ArrayList<>(existingGroup.nonnullBucket().values());
        LOG.debug("New Buckets {} and Existing Buckets {}", newBuckets, existingBuckets);
        List<Bucket> combinedBuckets = new ArrayList<>(existingBuckets);

        Map<String,Bucket> ncBucketMap = new HashMap<>();
        Map<String,Bucket> reg6ActionBucketMap = new HashMap<>();
        // Add all buckets in the new group to a map with node connector/reg6 value as key
        newBuckets.forEach(bucket -> {
            List<Action> actionList = new ArrayList<>(bucket.getAction().values());
            if (actionList != null && !actionList.isEmpty()) {
                actionList.forEach(action -> {
                    if (action.getAction() instanceof OutputActionCase) {
                        OutputActionCase outputAction = (OutputActionCase)action.getAction();
                        String nc = outputAction.getOutputAction().getOutputNodeConnector().getValue();
                        ncBucketMap.put(nc, bucket);
                    }
                    if (action.getAction() instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase)action.getAction();
                        String regValue = regLoad.getNxRegLoad().getValue().toString();
                        reg6ActionBucketMap.put(regValue, bucket);
                    }
                });
            }
        });
        //Replace existing bucket if action has same node connector/reg6 value as stored in map
        //First, remove buckets with same nc id/reg6 value as in hashmap from combinedBuckets
        //Next, add all the buckets in hashmap to combined buckets
        existingBuckets.forEach(existingBucket -> {
            List<Action> actionList = new ArrayList<>(existingBucket.getAction().values());
            if (actionList != null && !actionList.isEmpty()) {
                actionList.forEach(action -> {
                    if (action.getAction() instanceof OutputActionCase) {
                        OutputActionCase outputAction = (OutputActionCase)action.getAction();
                        String nc = outputAction.getOutputAction().getOutputNodeConnector().getValue();
                        Bucket storedBucket = ncBucketMap.get(nc);
                        if (storedBucket != null) {
                            combinedBuckets.remove(existingBucket);
                        }
                    }
                    if (action.getAction() instanceof NxActionRegLoadNodesNodeGroupBucketsBucketActionsCase) {
                        NxActionRegLoadNodesNodeGroupBucketsBucketActionsCase regLoad = (
                                NxActionRegLoadNodesNodeGroupBucketsBucketActionsCase)action.getAction();
                        String regValue = regLoad.getNxRegLoad().getValue().toString();
                        Bucket storedBucket = reg6ActionBucketMap.get(regValue);
                        if (storedBucket != null) {
                            combinedBuckets.remove(existingBucket);
                        }
                    }
                });
            }
        });
        AtomicLong bucketIdValue = new AtomicLong(-1);
        //Change the bucket id of existing buckets
        List<Bucket> bucketsToBeAdded = new ArrayList<>();
        bucketsToBeAdded.addAll(combinedBuckets.stream().map(bucket -> {
            BucketId bucketId = new BucketId(bucketIdValue.incrementAndGet());
            return new BucketBuilder(bucket).withKey(new BucketKey(bucketId)).setBucketId(bucketId).build();
        }).collect(Collectors.toList()));
        //Change the bucket id of remaining to be added to the combined buckets
        bucketsToBeAdded.addAll(ncBucketMap.values().stream().map(bucket -> {
            BucketId bucketId = new BucketId(bucketIdValue.incrementAndGet());
            return new BucketBuilder(bucket).withKey(new BucketKey(bucketId)).setBucketId(bucketId).build();
        }).collect(Collectors.toList()));
        bucketsToBeAdded.addAll(reg6ActionBucketMap.values().stream().map(bucket -> {
            BucketId bucketId = new BucketId(bucketIdValue.incrementAndGet());
            return new BucketBuilder(bucket).withKey(new BucketKey(bucketId)).setBucketId(bucketId).build();
        }).collect(Collectors.toList()));
        Group group = MDSALUtil.buildGroup(newGroup.getGroupId().getValue().longValue(), newGroup.getGroupName(),
                GroupTypes.GroupAll, MDSALUtil.buildBucketLists(bucketsToBeAdded));
        mdsalManager.addGroup(confTx, dpnId, group);
        LOG.trace("Installed remote BC group for node {} with group {}", nodeDpn.key().getId().getValue(), group);
    }

    static InstanceIdentifier<Subnetmaps> buildSubnetMapsWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class);
    }

    public static InstanceIdentifier<Group> getGroupInstanceid(Uint64 dpnId, long groupId) {
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(new NodeId("openflow:" + dpnId)))
                .augmentation(FlowCapableNode.class).child(Group.class, new GroupKey(new GroupId(groupId))).build();
    }

    public static String getBcGroupUpdateKey(String elanName) {
        return "bc.group.update." + elanName;
    }
}
