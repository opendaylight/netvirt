/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.infrautils.utils.concurrent.LoggingFutures.addErrorLogging;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;
import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlanNetworkOrVxlanSegment;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.Acquired;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.recovery.impl.ElanServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanEtreeUtils;
import org.opendaylight.netvirt.elan.utils.ElanForwardingEntriesHandler;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface.EtreeInterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class in charge of handling creations, modifications and removals of
 * ElanInterfaces.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 */
@Singleton
public class ElanInterfaceManager extends AbstractAsyncDataTreeChangeListener<ElanInterface>
        implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceManager.class);
    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("NetvirtEventLogger");
    public static final long WAIT_TIME_FOR_SYNC_INSTALL = Long.getLong("wait.time.sync.install", 300L);
    private static final boolean SH_FLAG_SET = true;
    private static final boolean SH_FLAG_UNSET = false;

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final IInterfaceManager interfaceManager;
    private final IdManagerService idManager;
    private final ElanForwardingEntriesHandler elanForwardingEntriesHandler;
    private final INeutronVpnManager neutronVpnManager;
    private final ElanItmUtils elanItmUtils;
    private final ElanEtreeUtils elanEtreeUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanUtils elanUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInterfaceCache elanInterfaceCache;
    private final ElanGroupCache elanGroupCache;

    private final Map<String, ConcurrentLinkedQueue<ElanInterface>>
        unProcessedElanInterfaces = new ConcurrentHashMap<>();

    @Inject
    public ElanInterfaceManager(final DataBroker dataBroker, final IdManagerService managerService,
                                final IMdsalApiManager mdsalApiManager, IInterfaceManager interfaceManager,
                                final ElanForwardingEntriesHandler elanForwardingEntriesHandler,
                                final INeutronVpnManager neutronVpnManager, final ElanItmUtils elanItmUtils,
                                final ElanEtreeUtils elanEtreeUtils, final ElanL2GatewayUtils elanL2GatewayUtils,
                                final ElanUtils elanUtils, final JobCoordinator jobCoordinator,
                                final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                final ElanInstanceCache elanInstanceCache,
                                final ElanInterfaceCache elanInterfaceCache,
                                final ElanServiceRecoveryHandler elanServiceRecoveryHandler,
                                ElanGroupCache elanGroupCache,
                                final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ElanInterfaces.class)
                .child(ElanInterface.class),
                Executors.newListeningSingleThreadExecutor("ElanInterfaceManager", LOG));
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = managerService;
        this.mdsalManager = mdsalApiManager;
        this.interfaceManager = interfaceManager;
        this.elanForwardingEntriesHandler = elanForwardingEntriesHandler;
        this.neutronVpnManager = neutronVpnManager;
        this.elanItmUtils = elanItmUtils;
        this.elanEtreeUtils = elanEtreeUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInterfaceCache = elanInterfaceCache;
        this.elanGroupCache = elanGroupCache;
        serviceRecoveryRegistry.addRecoverableListener(elanServiceRecoveryHandler.buildServiceRegistryKey(), this);
    }

    public void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
        super.register();
        LOG.info("Registering ElanInterfaceManager");
    }

    @Override
    public void deregisterListener() {
        super.close();
        LOG.info("Deregistering ElanInterfaceManager");
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {
        String interfaceName = del.getName();
        String elanInstanceName = del.getElanInstanceName();
        EVENT_LOGGER.debug("ELAN-Interface, REMOVE {} Instance {}", interfaceName, elanInstanceName);
        Queue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstanceName);
        if (elanInterfaces != null && elanInterfaces.contains(del)) {
            elanInterfaces.remove(del);
            if (elanInterfaces.isEmpty()) {
                unProcessedElanInterfaces.remove(elanInstanceName);
            }
        }
        ElanInstance elanInfo = elanInstanceCache.get(elanInstanceName).orElse(null);
        /*
         * Handling in case the elan instance is deleted.If the Elan instance is
         * deleted, there is no need to explicitly delete the elan interfaces
         */
        if (elanInfo == null) {
            return;
        }
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        if (interfaceInfo == null && elanInfo.isExternal()) {
            // In deleting external network, the underlying ietf Inteface might have been removed
            // from the config DS prior to deleting the ELAN interface. We try to get the InterfaceInfo
            // from Operational DS instead
            interfaceInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName);
        }
        InterfaceRemoveWorkerOnElan configWorker = new InterfaceRemoveWorkerOnElan(elanInstanceName, elanInfo,
                interfaceName, interfaceInfo, this);
        jobCoordinator.enqueueJob(elanInstanceName, configWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    private static class RemoveElanInterfaceHolder {
        boolean isLastElanInterface = false;
        Uint64 dpId = null;
    }

    @SuppressWarnings("checkstyle:ForbidCertainMethod")
    public List<ListenableFuture<?>> removeElanInterface(ElanInstance elanInfo, String interfaceName,
            InterfaceInfo interfaceInfo) {
        String elanName = elanInfo.getElanInstanceName();
        EVENT_LOGGER.debug("ELAN-InterfaceState, REMOVE {} Instance {}", interfaceName, elanName);
        Uint32 elanTag = elanInfo.getElanTag();
        // We use two transaction so we don't suffer on multiple shards (interfaces and flows)
        List<ListenableFuture<?>> futures = new ArrayList<>();
        RemoveElanInterfaceHolder holder = new RemoveElanInterfaceHolder();
        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, interfaceTx -> {
            Elan elanState = removeElanStateForInterface(elanInfo, interfaceName, interfaceTx);
            if (elanState == null) {
                return;
            }
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, flowTx -> {
                List<String> elanInterfaces = elanState.getElanInterfaces();
                if (elanInterfaces == null || elanInterfaces.isEmpty()) {
                    holder.isLastElanInterface = true;
                }
                if (interfaceInfo != null) {
                    holder.dpId = interfaceInfo.getDpId();
                    DpnInterfaces dpnInterfaces = removeElanDpnInterfaceFromOperationalDataStore(elanName, holder.dpId,
                        interfaceName, elanTag, interfaceTx);
                    /*
                     * If there are not elan ports, remove the unknown dmac, terminating
                     * service table flows, remote/local bc group
                     */
                    if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                        || dpnInterfaces.getInterfaces().isEmpty()) {
                        // No more Elan Interfaces in this DPN
                        EVENT_LOGGER.debug("ELAN-Flows, REMOVE {} Instance {}", interfaceName, elanName);
                        LOG.debug("deleting the elan: {} present on dpId: {}", elanInfo.getElanInstanceName(),
                            holder.dpId);
                        if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
                            removeDefaultTermFlow(holder.dpId, elanInfo.getElanTag().toJava());
                        }
                        removeUnknownDmacFlow(holder.dpId, elanInfo, flowTx, elanInfo.getElanTag().toJava());
                        removeEtreeUnknownDmacFlow(holder.dpId, elanInfo, flowTx);
                        removeElanBroadcastGroup(elanInfo, interfaceInfo, flowTx);
                        removeLocalBroadcastGroup(elanInfo, interfaceInfo, flowTx);
                        removeEtreeBroadcastGrups(elanInfo, interfaceInfo, flowTx);
                        if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
                            if (elanUtils.isOpenstackVniSemanticsEnforced()) {
                                elanUtils.removeTerminatingServiceAction(holder.dpId,
                                    ElanUtils.getVxlanSegmentationId(elanInfo).intValue());
                            }
                            unsetExternalTunnelTable(holder.dpId, elanInfo, flowTx);
                        }
                    } else {
                        setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo, flowTx);
                    }
                }
            }));
        }));
        futures.forEach(ElanUtils::waitForTransactionToComplete);

        InterfaceRemoveWorkerOnElanInterface removeInterfaceWorker = new InterfaceRemoveWorkerOnElanInterface(
                interfaceName, elanInfo, interfaceInfo, this, holder.isLastElanInterface);
        jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(interfaceName), removeInterfaceWorker,
                ElanConstants.JOB_MAX_RETRIES);

        return futures;
    }

    private void removeEtreeUnknownDmacFlow(Uint64 dpId, ElanInstance elanInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanInfo.getElanTag().toJava());
        if (etreeLeafTag != null) {
            long leafTag = etreeLeafTag.getEtreeLeafTag().getValue().toJava();
            removeUnknownDmacFlow(dpId, elanInfo, deleteFlowGroupTx, leafTag);
        }
    }

    private void removeEtreeBroadcastGrups(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        removeLeavesEtreeBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
        removeLeavesLocalBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
    }

    private void removeLeavesLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            Uint64 dpnId = interfaceInfo.getDpId();
            long groupId = ElanUtils.getEtreeLeafLocalBCGId(etreeInstance.getEtreeLeafTagVal().getValue().toJava());
            LOG.trace("deleted the localBroadCast Group:{}", groupId);
            mdsalManager.removeGroup(deleteFlowGroupTx, dpnId, groupId);
        }
    }

    private void removeLeavesEtreeBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            long etreeTag = etreeInstance.getEtreeLeafTagVal().getValue().toJava();
            Uint64 dpnId = interfaceInfo.getDpId();
            long groupId = ElanUtils.getEtreeLeafRemoteBCGId(etreeTag);
            LOG.trace("deleting the remoteBroadCast group:{}", groupId);
            mdsalManager.removeGroup(deleteFlowGroupTx, dpnId, groupId);
        }
    }

    private static Elan removeElanStateForInterface(ElanInstance elanInfo, String interfaceName,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        String elanName = elanInfo.getElanInstanceName();
        Elan elanState = ElanUtils.getElanByName(tx, elanName);
        if (elanState == null) {
            return elanState;
        }
        List<String> existingElanInterfaces = elanState.getElanInterfaces();
        List<String> elanInterfaces = new ArrayList<>();
        if (existingElanInterfaces != null) {
            elanInterfaces.addAll(existingElanInterfaces);
        }
        boolean isRemoved = elanInterfaces.remove(interfaceName);
        if (!isRemoved) {
            return elanState;
        }

        if (elanInterfaces.isEmpty()) {
            tx.delete(ElanUtils.getElanInstanceOperationalDataPath(elanName));
            tx.delete(ElanUtils.getElanMacTableOperationalDataPath(elanName));
            tx.delete(ElanUtils.getElanInfoEntriesOperationalDataPath(elanInfo.getElanTag()));
            tx.delete(ElanUtils.getElanDpnOperationDataPath(elanName));
        } else {
            Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName)
                    .withKey(new ElanKey(elanName)).build();
            tx.put(ElanUtils.getElanInstanceOperationalDataPath(elanName), updateElanState);
        }
        return elanState;
    }

    private void deleteElanInterfaceFromConfigDS(String interfaceName, TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        // removing the ElanInterface from the config data_store if interface is
        // not present in Interface config DS
        InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                .getElanInterfaceConfigurationDataPathId(interfaceName);
        FluentFuture<Optional<ElanInterface>> interfaceOptional = tx.read(elanInterfaceId);
        if (!interfaceOptional.get().isPresent() && elanInterfaceCache.get(interfaceName).isPresent()) {
            tx.delete(ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
        }
    }

    List<ListenableFuture<?>> removeEntriesForElanInterface(ElanInstance elanInfo, InterfaceInfo
            interfaceInfo, String interfaceName, boolean isLastElanInterface) {
        String elanName = elanInfo.getElanInstanceName();
        EVENT_LOGGER.debug("ELAN-InterfaceEntries, REMOVE {} Instance {}", interfaceName, elanName);
        List<ListenableFuture<?>> futures = new ArrayList<>();
        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, flowTx -> {
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, interfaceTx -> {
                InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils
                        .getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
                Optional<ElanInterfaceMac> existingElanInterfaceMac = interfaceTx.read(elanInterfaceId).get();
                LOG.debug("Removing the Interface:{} from elan:{}", interfaceName, elanName);
                if (interfaceInfo != null) {
                    if (existingElanInterfaceMac.isPresent()) {
                        Map<MacEntryKey, MacEntry> existingMacEntries =
                            existingElanInterfaceMac.get().nonnullMacEntry();
                        if (existingMacEntries != null) {
                            List<PhysAddress> macAddresses = new ArrayList<>();
                            for (MacEntry macEntry : existingMacEntries.values()) {
                                PhysAddress macAddress = macEntry.getMacAddress();
                                LOG.debug("removing the  mac-entry:{} present on elanInterface:{}",
                                        macAddress.getValue(), interfaceName);
                                Optional<MacEntry> macEntryOptional =
                                        elanUtils.getMacEntryForElanInstance(interfaceTx, elanName, macAddress);
                                if (!isLastElanInterface && macEntryOptional.isPresent()) {
                                    interfaceTx.delete(ElanUtils.getMacEntryOperationalDataPath(elanName, macAddress));
                                }
                                EVENT_LOGGER.debug("ELAN-MacFlows, REMOVE {} Instance {} Mac {}",
                                        interfaceName, elanName, macAddress);
                                elanUtils.deleteMacFlows(elanInfo, interfaceInfo, macEntry, flowTx);
                                macAddresses.add(macAddress);
                            }

                            // Removing all those MACs from External Devices belonging
                            // to this ELAN
                            if (isVxlanNetworkOrVxlanSegment(elanInfo) && ! macAddresses.isEmpty()) {
                                elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanInfo, macAddresses);
                            }
                        }
                    }
                    removeDefaultTermFlow(interfaceInfo.getDpId(), interfaceInfo.getInterfaceTag());
                    removeFilterEqualsTable(elanInfo, interfaceInfo, flowTx);
                } else if (existingElanInterfaceMac.isPresent()) {
                    // Interface does not exist in ConfigDS, so lets remove everything
                    // about that interface related to Elan
                    Map<MacEntryKey, MacEntry> macEntries = existingElanInterfaceMac.get().nonnullMacEntry();
                    if (macEntries != null) {
                        for (MacEntry macEntry : macEntries.values()) {
                            PhysAddress macAddress = macEntry.getMacAddress();
                            if (elanUtils.getMacEntryForElanInstance(elanName, macAddress).isPresent()) {
                                interfaceTx.delete(ElanUtils.getMacEntryOperationalDataPath(elanName, macAddress));
                            }
                        }
                    }
                }
                if (existingElanInterfaceMac.isPresent()) {
                    interfaceTx.delete(elanInterfaceId);
                }
                unbindService(interfaceName, flowTx);
                deleteElanInterfaceFromConfigDS(interfaceName, flowTx);
            }));
        }));
        return futures;
    }

    private DpnInterfaces removeElanDpnInterfaceFromOperationalDataStore(String elanName, Uint64 dpId,
                                                                         String interfaceName, Uint32 elanTag,
                                                                         TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        // FIXME: pass in and use ElanInstanceKey instead?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanName);
        lock.lock();
        try {
            DpnInterfaces dpnInterfaces = elanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if (dpnInterfaces != null) {
                List<String> interfaceLists = null;
                if (dpnInterfaces.getInterfaces() != null) {
                    interfaceLists = new ArrayList<>(dpnInterfaces.getInterfaces());
                }
                if (interfaceLists != null) {
                    interfaceLists.remove(interfaceName);
                }

                if (interfaceLists == null || interfaceLists.isEmpty()) {
                    deleteAllRemoteMacsInADpn(elanName, dpId, elanTag);
                    deleteElanDpnInterface(elanName, dpId, tx);
                } else {
                    dpnInterfaces = updateElanDpnInterfacesList(elanName, dpId, interfaceLists, tx);
                }
            }
            return dpnInterfaces;
        } finally {
            lock.unlock();
        }
    }

    private void deleteAllRemoteMacsInADpn(String elanName, Uint64 dpId, Uint32 elanTag) {
        List<DpnInterfaces> dpnInterfaces = elanUtils.getInvolvedDpnsInElan(elanName);
        addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
            for (DpnInterfaces dpnInterface : dpnInterfaces) {
                Uint64 currentDpId = dpnInterface.getDpId();
                if (!currentDpId.equals(dpId) && dpnInterface.getInterfaces() != null) {
                    for (String elanInterface : dpnInterface.getInterfaces()) {
                        ElanInterfaceMac macs = elanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                        if (macs == null || macs.getMacEntry() == null) {
                            continue;
                        }
                        for (MacEntry mac : new ArrayList<MacEntry>(macs.nonnullMacEntry().values())) {
                            removeTheMacFlowInTheDPN(dpId, elanTag, mac, confTx);
                            removeEtreeMacFlowInTheDPN(dpId, elanTag, mac, confTx);
                        }
                    }
                }
            }
        }), LOG, "Error deleting remote MACs in DPN {}", dpId);
    }

    private void removeEtreeMacFlowInTheDPN(Uint64 dpId, Uint32 elanTag, MacEntry mac,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag.longValue());
        if (etreeLeafTag != null) {
            removeTheMacFlowInTheDPN(dpId, etreeLeafTag.getEtreeLeafTag().getValue(), mac, confTx);
        }
    }

    private void removeTheMacFlowInTheDPN(Uint64 dpId, Uint32 elanTag, MacEntry mac,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        mdsalManager
                .removeFlow(confTx, dpId,
                        MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                                ElanUtils.getKnownDynamicmacFlowRef(elanTag, mac.getMacAddress().getValue())));
    }

    /*
    * Possible Scenarios for update
    *   a. if orig={1,2,3,4}   and updated=null or updated={}
        then all {1,2,3,4} should be removed

        b. if orig=null or orig={}  and  updated ={1,2,3,4}
        then all {1,2,3,4} should be added

        c. if orig = {1,2,3,4} updated={2,3,4}
        then 1 should be removed

        d. basically if orig = { 1,2,3,4} and updated is {1,2,3,4,5}
        then we should just add 5

        e. if orig = {1,2,3,4} updated={2,3,4,5}
        then 1 should be removed , 5 should be added
    * */
    @SuppressWarnings("checkstyle:ForbidCertainMethod")
    @Override
    public void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {
        // updating the static-Mac Entries for the existing elanInterface
        String elanName = update.getElanInstanceName();
        String interfaceName = update.getName();
        LOG.info("Update static mac entries for elan interface {} in elan instance {}", interfaceName, elanName);
        EVENT_LOGGER.debug("ELAN-Interface, UPDATE {} Instance {}", original.getName(), elanName);

        List<StaticMacEntries> originalStaticMacEntries = new ArrayList<StaticMacEntries>(original
                .nonnullStaticMacEntries().values());
        List<StaticMacEntries> updatedStaticMacEntries = new ArrayList<StaticMacEntries>(update
                .nonnullStaticMacEntries().values());
        List<StaticMacEntries> deletedEntries = ElanUtils.diffOf(originalStaticMacEntries, updatedStaticMacEntries);
        List<StaticMacEntries> updatedEntries = ElanUtils.diffOf(updatedStaticMacEntries, originalStaticMacEntries);

        deletedEntries.forEach((deletedEntry) -> removeInterfaceStaticMacEntries(elanName, interfaceName,
                deletedEntry.getMacAddress()));

        /*if updatedStaticMacEntries is NOT NULL, which means as part of update call these entries were added.
        * Hence add the macentries for the same.*/
        for (StaticMacEntries staticMacEntry : updatedEntries) {
            InstanceIdentifier<MacEntry> macEntryIdentifier = getMacEntryOperationalDataPath(elanName,
                    staticMacEntry.getMacAddress());
            addErrorLogging(ElanUtils.waitForTransactionToComplete(
                txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                    Optional<MacEntry> existingMacEntry = tx.read(macEntryIdentifier).get();
                    if (existingMacEntry.isPresent()) {
                        LOG.debug("updating elan interface forwarding table for mac entry {} elan instance {}",
                                existingMacEntry.get(), elanName);
                        elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(
                            elanName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get(),
                            tx);
                    } else {
                        LOG.info("Adding elan interface forwarding table for mac entry {} elan interface"
                                + " {} elan instance {}.", staticMacEntry.getMacAddress(), interfaceName, elanName);
                        elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(
                            elanName, interfaceName, staticMacEntry, tx);
                    }
                })), LOG, "Error in update: identifier={}, original={}, update={}", identifier, original, update);
        }
    }

    @Override
    public void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded) {
        LOG.info("Init for ELAN interface Add {}", elanInterfaceAdded);
        addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, operTx -> {
            String elanInstanceName = elanInterfaceAdded.getElanInstanceName();
            String interfaceName = elanInterfaceAdded.getName();
            EVENT_LOGGER.debug("ELAN-Interface, ADD {} Instance {}", interfaceName, elanInstanceName);
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            if (interfaceInfo == null) {
                LOG.info("Interface {} is removed from Interface Oper DS due to port down ", interfaceName);
                return;
            }
            ElanInstance elanInstance = elanInstanceCache.get(elanInstanceName).orElse(null);

            if (elanInstance == null) {
                // Add the ElanInstance in the Configuration data-store
                List<String> elanInterfaces = new ArrayList<>();
                elanInterfaces.add(interfaceName);
                elanInstance = txRunner.applyWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    confTx -> ElanUtils.updateOperationalDataStore(idManager,
                        new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setDescription(
                            elanInterfaceAdded.getDescription()).build(), elanInterfaces, confTx, operTx)).get();
            }

            Uint32 elanTag = elanInstance.getElanTag();
            // If elan tag is not updated, then put the elan interface into
            // unprocessed entry map and entry. Let entries
            // in this map get processed during ELAN update DCN.
            if (elanTag == null || elanTag.longValue() == 0L) {
                ConcurrentLinkedQueue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstanceName);
                if (elanInterfaces == null) {
                    elanInterfaces = new ConcurrentLinkedQueue<>();
                }
                if (!elanInterfaces.contains(elanInterfaceAdded)) {
                    elanInterfaces.add(elanInterfaceAdded);
                }
                LOG.error("ELAN tag for instance {} is not created. Adding it to unprocessed list."
                        + " Recreate the network if this message is seen multiple times", elanInstanceName);
                unProcessedElanInterfaces.put(elanInstanceName, elanInterfaces);
                return;
            }
            InterfaceAddWorkerOnElan addWorker = new InterfaceAddWorkerOnElan(elanInstanceName, elanInterfaceAdded,
                    interfaceInfo, elanInstance, this);
            jobCoordinator.enqueueJob(elanInstanceName, addWorker, ElanConstants.JOB_MAX_RETRIES);
        }), LOG, "Error processing added ELAN interface");
    }

    List<ListenableFuture<?>> handleunprocessedElanInterfaces(ElanInstance elanInstance) {
        LOG.trace("Handling unprocessed elan interfaces for elan instance {}", elanInstance.getElanInstanceName());
        List<ListenableFuture<?>> futures = new ArrayList<>();
        Queue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstance.getElanInstanceName());
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return futures;
        }
        for (ElanInterface elanInterface : elanInterfaces) {
            String interfaceName = elanInterface.getName();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            futures.addAll(addElanInterface(elanInterface, interfaceInfo, elanInstance));
        }
        unProcessedElanInterfaces.remove(elanInstance.getElanInstanceName());
        LOG.info("Unprocessed elan interfaces for elan instance {} has been removed.",
                elanInstance.getElanInstanceName());
        return futures;
    }

    void programRemoteDmacFlow(ElanInstance elanInstance, InterfaceInfo interfaceInfo,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        ElanDpnInterfacesList elanDpnInterfacesList = elanUtils
                .getElanDpnInterfacesList(elanInstance.getElanInstanceName());
        List<DpnInterfaces> dpnInterfaceLists = null;
        if (elanDpnInterfacesList != null) {
            dpnInterfaceLists = new ArrayList<DpnInterfaces>(elanDpnInterfacesList.nonnullDpnInterfaces().values());
        }
        if (dpnInterfaceLists != null && !dpnInterfaceLists.isEmpty()) {
            Uint64 dstDpId = interfaceInfo.getDpId();
            processRemoteDmacFlowForInterface(dstDpId, elanInstance, dpnInterfaceLists, writeFlowGroupTx);
        }
    }

    private void processRemoteDmacFlowForInterface(Uint64 dstDpId, ElanInstance elanInstance,
             List<DpnInterfaces> dpnInterfaceLists, TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            if (Objects.equals(dpnInterfaces.getDpId(), dstDpId)) {
                continue;
            }
            List<String> remoteElanInterfaces = dpnInterfaces.getInterfaces();
            if (remoteElanInterfaces == null || remoteElanInterfaces.isEmpty()) {
                continue;
            }
            for (String remoteIf : remoteElanInterfaces) {
                ElanInterfaceMac elanIfMac = elanUtils.getElanInterfaceMacByInterfaceName(remoteIf);
                InterfaceInfo remoteInterface = interfaceManager.getInterfaceInfo(remoteIf);
                if (elanIfMac == null || remoteInterface == null) {
                    continue;
                }
                Map<MacEntryKey, MacEntry> remoteMacEntries = elanIfMac.nonnullMacEntry();
                for (MacEntry macEntry : remoteMacEntries.values()) {
                    String macAddress = macEntry.getMacAddress().getValue();
                    LOG.info("Programming remote dmac {} on the newly added DPN {} for elan {}", macAddress,
                            dstDpId, elanInstance.getElanInstanceName());
                    elanUtils.setupRemoteDmacFlow(dstDpId, remoteInterface.getDpId(),
                            remoteInterface.getInterfaceTag(), elanInstance.getElanTag(), macAddress,
                            elanInstance.getElanInstanceName(), writeFlowGroupTx, remoteIf, elanInstance);
                }
            }
        }
    }

    private static class AddElanInterfaceHolder {
        private DpnInterfaces dpnInterfaces = null;
        private boolean isFirstInterfaceInDpn = false;
        private Uint64 dpId;
    }

    @SuppressWarnings("checkstyle:ForbidCertainMethod")
    List<ListenableFuture<?>> addElanInterface(ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
        requireNonNull(elanInstance, "elanInstance cannot be null");
        requireNonNull(interfaceInfo, "interfaceInfo cannot be null");
        requireNonNull(elanInterface, "elanInterface cannot be null");

        String interfaceName = elanInterface.getName();
        String elanInstanceName = elanInterface.getElanInstanceName();
        LOG.trace("Adding elan interface: interface name {} , instance name {}", interfaceName, elanInstanceName);
        EVENT_LOGGER.debug("ELAN-InterfaceState, ADD {} Instance {}", interfaceName, elanInstanceName);

        List<ListenableFuture<?>> futures = new ArrayList<>();
        AddElanInterfaceHolder holder = new AddElanInterfaceHolder();
        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx -> {
            Elan elanInfo = ElanUtils.getElanByName(broker, elanInstanceName);
            if (elanInfo == null) {
                LOG.trace("elanInfo is null for elan instance: {}", elanInstanceName);
                List<String> elanInterfaces = new ArrayList<>();
                elanInterfaces.add(interfaceName);
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    confTx -> ElanUtils.updateOperationalDataStore(idManager, elanInstance, elanInterfaces, confTx,
                        operTx)));
            } else {
                createElanStateList(elanInstanceName, interfaceName, operTx);
            }
            // Specific actions to the DPN where the ElanInterface has been added,
            // for example, programming the
            // External tunnel table if needed or adding the ElanInterface to the
            // DpnInterfaces in the operational DS.
            holder.dpId = interfaceInfo.getDpId();
            if (holder.dpId != null && !holder.dpId.equals(ElanConstants.INVALID_DPN)) {
                // FIXME: use elanInstaince.key() instead?
                final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanInstanceName);
                lock.lock();
                try {
                    InstanceIdentifier<DpnInterfaces> elanDpnInterfaces = ElanUtils
                        .getElanDpnInterfaceOperationalDataPath(elanInstanceName, holder.dpId);
                    Optional<DpnInterfaces> existingElanDpnInterfaces = operTx.read(elanDpnInterfaces).get();
                    if (ElanUtils.isVlan(elanInstance)) {
                        holder.isFirstInterfaceInDpn =  checkIfFirstInterface(interfaceName,
                            elanInstanceName, existingElanDpnInterfaces);
                    } else {
                        holder.isFirstInterfaceInDpn = !existingElanDpnInterfaces.isPresent();
                    }
                    if (holder.isFirstInterfaceInDpn) {
                        // ELAN's 1st ElanInterface added to this DPN
                        if (!existingElanDpnInterfaces.isPresent()) {
                            holder.dpnInterfaces =
                                createElanInterfacesList(elanInstanceName, interfaceName, holder.dpId, operTx);
                        } else {
                            List<String> existingInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                            List<String> elanInterfaces =
                                existingInterfaces != null ? new ArrayList<>(existingInterfaces) : new ArrayList<>();
                            elanInterfaces.add(interfaceName);
                            holder.dpnInterfaces = updateElanDpnInterfacesList(elanInstanceName, holder.dpId,
                                elanInterfaces, operTx);
                        }
                        LOG.debug("1st interface {} for elan {} is added to dpn {}",
                                interfaceName, elanInstanceName, holder.dpId);
                        // The 1st ElanInterface in a DPN must program the Ext Tunnel
                        // table, but only if Elan has VNI
                        if (isVxlanNetworkOrVxlanSegment(elanInstance)) {
                            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                                confTx -> setExternalTunnelTable(holder.dpId, elanInstance, confTx)));
                        }
                        elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(holder.dpId, elanInstance,
                            interfaceName);
                    } else {
                        List<String> existingInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                        List<String> elanInterfaces =
                            existingInterfaces != null ? new ArrayList<>(existingInterfaces) : new ArrayList<>();
                        elanInterfaces.add(interfaceName);
                        if (elanInterfaces.size() == 1) { // 1st dpn interface
                            elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(holder.dpId, elanInstance,
                                interfaceName);
                        }
                        holder.dpnInterfaces =
                            updateElanDpnInterfacesList(elanInstanceName, holder.dpId, elanInterfaces, operTx);
                        LOG.debug("Interface {} for elan {} is added to dpn {}",
                                interfaceName, elanInstanceName, holder.dpId);
                    }
                } finally {
                    lock.unlock();
                }
            }

            // add code to install Local/Remote BC group, unknow DMAC entry,
            // terminating service table flow entry
            // call bindservice of interfacemanager to create ingress table flow
            // enty.
        }));
        futures.forEach(ElanUtils::waitForTransactionToComplete);
        futures.add(
            ElanUtils.waitForTransactionToComplete(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                confTx -> installEntriesForFirstInterfaceonDpn(elanInstance, interfaceInfo, holder.dpnInterfaces,
                    holder.isFirstInterfaceInDpn, confTx))));

        // add the vlan provider interface to remote BC group for the elan
        // for internal vlan networks
        if (ElanUtils.isVlan(elanInstance) && !elanInstance.isExternal()) {
            if (interfaceManager.isExternalInterface(interfaceName)) {
                LOG.debug("adding vlan prv intf {} to elan {} BC group", interfaceName, elanInstanceName);
                handleExternalInterfaceEvent(elanInstance, holder.dpnInterfaces, holder.dpId);
            }
        }
        if (holder.isFirstInterfaceInDpn) {
            // ELAN's 1st ElanInterface added to this DPN
            LOG.debug("Adding dpn into operational dpn list {}", holder.dpId);
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx -> {
                operTx.mergeParentStructurePut(ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                        holder.dpId), holder.dpnInterfaces);
            }));
        } else {
            LOG.debug("Updated dpn into operational dpn list {}", holder.dpId);
        }

        scheduleElanInterfaceWorkerAfterRemoteBcGroup(elanInstance, interfaceInfo, holder.dpnInterfaces,
                holder.isFirstInterfaceInDpn, elanInterface);
        return futures;
    }

    @SuppressWarnings("checkstyle:ForbidCertainMethod")
    List<ListenableFuture<?>> setupEntriesForElanInterface(ElanInstance elanInstance,
            ElanInterface elanInterface, InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn) {
        String elanInstanceName = elanInstance.getElanInstanceName();
        String interfaceName = elanInterface.getName();
        List<ListenableFuture<?>> futures = new ArrayList<>();
        Uint64 dpId = interfaceInfo.getDpId();
        boolean isInterfaceOperational = isOperational(interfaceInfo);
        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx -> {
                installEntriesForElanInterface(elanInstance, elanInterface, interfaceInfo,
                    isFirstInterfaceInDpn, confTx);
                Map<StaticMacEntriesKey, StaticMacEntries> staticMacEntriesMap =
                    elanInterface.nonnullStaticMacEntries();
                List<PhysAddress> staticMacAddresses = new ArrayList<>();
                if (ElanUtils.isNotEmpty(staticMacEntriesMap.values())) {
                    for (StaticMacEntries staticMacEntry : staticMacEntriesMap.values()) {
                        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName,
                            staticMacEntry.getMacAddress());
                        Optional<MacEntry> existingMacEntry = ElanUtils.read(broker,
                            LogicalDatastoreType.OPERATIONAL, macId);
                        if (existingMacEntry.isPresent()) {
                            elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(
                                elanInstanceName, interfaceName, existingMacEntry.get().getInterface(),
                                existingMacEntry.get(), operTx);
                        } else {
                            elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(elanInstanceName,
                                interfaceName, staticMacEntry, operTx);
                        }

                        if (isInterfaceOperational) {
                            // Setting SMAC, DMAC, UDMAC in this DPN and also in other
                            // DPNs
                            String macAddress = staticMacEntry.getMacAddress().getValue();
                            LOG.info(
                                "programming smac and dmacs for {} on source and other DPNs for elan {} and interface"
                                    + " {}",
                                macAddress, elanInstanceName, interfaceName);
                            EVENT_LOGGER.debug("ELAN-MacFlows, ADD {} Instance {} Mac {}",
                                    interfaceName, elanInstanceName, macAddress);
                            elanUtils.setupMacFlows(elanInstance, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT,
                                staticMacEntry.getMacAddress().getValue(), true, confTx);
                        }
                    }

                    if (isInterfaceOperational) {
                        // Add MAC in TOR's remote MACs via OVSDB. Outside of the loop
                        // on purpose.
                        for (StaticMacEntries staticMacEntry : staticMacEntriesMap.values()) {
                            staticMacAddresses.add(staticMacEntry.getMacAddress());
                        }
                        elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId,
                            staticMacAddresses);
                    }
                }
            }));
        }));
        futures.forEach(ElanUtils::waitForTransactionToComplete);
        if (isInterfaceOperational && !interfaceManager.isExternalInterface(interfaceName)) {
            //At this point, the interface is operational and D/SMAC flows have been configured, mark the port active
            try {
                Port neutronPort = neutronVpnManager.getNeutronPort(interfaceName);
                if (neutronPort != null) {
                    NeutronUtils.updatePortStatus(interfaceName, NeutronUtils.PORT_STATUS_ACTIVE, broker);
                }
            } catch (IllegalArgumentException ex) {
                LOG.trace("Interface: {} is not part of Neutron Network", interfaceName);
            }
        }
        return futures;
    }

    protected void removeInterfaceStaticMacEntries(String elanInstanceName, String interfaceName,
            PhysAddress physAddress) {
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
        InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName, physAddress);
        Optional<MacEntry> existingMacEntry = ElanUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, macId);

        if (!existingMacEntry.isPresent()) {
            return;
        }

        MacEntry macEntry = new MacEntryBuilder().setMacAddress(physAddress).setInterface(interfaceName)
                .withKey(new MacEntryKey(physAddress)).build();
        elanForwardingEntriesHandler.deleteElanInterfaceForwardingEntries(
                elanInstanceCache.get(elanInstanceName).orElse(null), interfaceInfo, macEntry);
    }

    private boolean checkIfFirstInterface(String elanInterface, String elanInstanceName,
            Optional<DpnInterfaces> existingElanDpnInterfaces) {
        String routerPortUuid = ElanUtils.getRouterPordIdFromElanInstance(broker, elanInstanceName);
        if (!existingElanDpnInterfaces.isPresent()) {
            return true;
        }
        if (elanInterface.equals(elanInstanceName) || elanInterface.equals(routerPortUuid)) {
            return false;
        }
        DpnInterfaces dpnInterfaces = existingElanDpnInterfaces.get();
        int dummyInterfaceCount =  0;
        List<String> interfaces = dpnInterfaces.getInterfaces();
        if (interfaces == null) {
            return true;
        }
        if (interfaces.contains(routerPortUuid)) {
            dummyInterfaceCount++;
        }
        if (interfaces.contains(elanInstanceName)) {
            dummyInterfaceCount++;
        }
        return interfaces.size() == dummyInterfaceCount;
    }

    private static InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName,
            PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    private void installEntriesForElanInterface(ElanInstance elanInstance, ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn, TypedWriteTransaction<Configuration> confTx) {
        if (!isOperational(interfaceInfo)) {
            LOG.warn("Interface {} is not operational", elanInterface.getName());
            EVENT_LOGGER.debug("ELAN-InterfaceEntries, ADD {} Instance {} Interface Status {}, returning",
                    elanInterface.getName(), elanInstance.getElanInstanceName(), interfaceInfo.getOpState());
            return;
        }
        Uint64 dpId = interfaceInfo.getDpId();
        if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
            elanUtils.setupTermDmacFlows(interfaceInfo, mdsalManager, confTx);
        }
        setupFilterEqualsTable(elanInstance, interfaceInfo, confTx);
        if (isFirstInterfaceInDpn) {
            // Terminating Service , UnknownDMAC Table.
            // The 1st ELAN Interface in a DPN must program the INTERNAL_TUNNEL_TABLE, but only if the network type
            // for ELAN Instance is VxLAN
            if (isVxlanNetworkOrVxlanSegment(elanInstance)) {
                setupTerminateServiceTable(elanInstance, dpId, confTx);
            }
            setupUnknownDMacTable(elanInstance, dpId, confTx);
            /*
             * Install remote DMAC flow. This is required since this DPN is
             * added later to the elan instance and remote DMACs of other
             * interfaces in this elan instance are not present in the current
             * dpn.
             */
            if (!interfaceManager.isExternalInterface(interfaceInfo.getInterfaceName())) {
                LOG.info("Programming remote dmac flows on the newly connected dpn {} for elan {} ", dpId,
                        elanInstance.getElanInstanceName());
                programRemoteDmacFlow(elanInstance, interfaceInfo, confTx);
            }
        }
        // bind the Elan service to the Interface
        bindService(elanInstance, elanInterface, interfaceInfo.getInterfaceTag(), confTx);
    }

    private void installEntriesForFirstInterfaceonDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            DpnInterfaces dpnInterfaces, boolean isFirstInterfaceInDpn, TypedWriteTransaction<Configuration> confTx) {
        if (!isOperational(interfaceInfo)) {
            LOG.warn("Entries for interface on dpn {} not installed since interface {} is not operational",
                    dpnInterfaces.getDpId(), interfaceInfo.getInterfaceName());
            EVENT_LOGGER.debug("ELAN-1stInterfaceEntries, ADD {} Instance {} Interface Status {}, returning",
                    interfaceInfo.getInterfaceName(), elanInfo.getElanInstanceName(), interfaceInfo.getOpState());
            return;
        }
        EVENT_LOGGER.debug("ELAN-1stInterfaceEntries, ADD {} Instance {}",
                interfaceInfo.getInterfaceName(), elanInfo.getElanInstanceName());
        // LocalBroadcast Group creation with elan-Interfaces
        LOG.info("Installing entries for interface {} on dpn {}", interfaceInfo.getInterfaceName(),
                dpnInterfaces.getDpId());
        setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo, confTx);
        if (isFirstInterfaceInDpn) {
            LOG.trace("waitTimeForSyncInstall is {}", WAIT_TIME_FOR_SYNC_INSTALL);
            try {
                Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
            } catch (InterruptedException e1) {
                LOG.warn("Error while waiting for local BC group for ELAN {} to install", elanInfo);
            }
        }
    }

    public InstanceIdentifier<Group> getGroupIid(ElanInstance elanInfo, Uint64 dpnId) {
        long remoteBcGroupId = ElanUtils.getElanRemoteBCGId(elanInfo.getElanTag().toJava());
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight
                        .inventory.rev130819.NodeId("openflow:" + dpnId.toString())))
                .augmentation(FlowCapableNode.class)
                .child(Group.class, new GroupKey(new GroupId(remoteBcGroupId))).build();
    }

    public void scheduleElanInterfaceWorkerAfterRemoteBcGroup(ElanInstance elanInfo,
                                                              InterfaceInfo interfaceInfo,
                                                              DpnInterfaces dpnInterfaces,
                                                              boolean isFirstInterfaceInDpn,
                                                              ElanInterface elanInterface) {
        if (!isOperational(interfaceInfo)) {
            LOG.debug("Interface {} is not operational", elanInterface.getName());
            return;
        }
        String elanInterfaceJobKey = ElanUtils.getElanInterfaceJobKey(interfaceInfo.getInterfaceName());
        InterfaceAddWorkerOnElanInterface addWorker = new InterfaceAddWorkerOnElanInterface(elanInterfaceJobKey,
                elanInterface, interfaceInfo, elanInfo, isFirstInterfaceInDpn, this);
        InstanceIdentifier<Group> groupInstanceId = getGroupIid(elanInfo, dpnInterfaces.getDpId());
        elanGroupCache.addJobToWaitList(groupInstanceId, () -> {
            jobCoordinator.enqueueJob(elanInterfaceJobKey, addWorker, ElanConstants.JOB_MAX_RETRIES);
        });
    }

    public void setupFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "group"), 11, elanInfo.getElanInstanceName(),
                0, 0, Uint64.valueOf(ElanConstants.COOKIE_ELAN_FILTER_EQUALS.toJava().add(BigInteger.valueOf(ifTag))),
                ElanUtils.getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                elanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.addFlow(writeFlowGroupTx, interfaceInfo.getDpId(), flow);
        LOG.trace("Filter equals table(55) flow entry created on dpn: {} for interface port: {}",
                interfaceInfo.getDpId(), interfaceInfo.getPortName());
        Map<InstructionKey, Instruction> customInstructionsMap = new HashMap<InstructionKey, Instruction>();
        int instructionKey = 0;
        for (Instruction instructionObj : MDSALUtil.buildInstructionsDrop()) {
            customInstructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }

        Flow flowEntry = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "drop"), 12, elanInfo.getElanInstanceName(), 0,
                0, Uint64.valueOf(ElanConstants.COOKIE_ELAN_FILTER_EQUALS.toJava().add(BigInteger.valueOf(ifTag))),
                getMatchesForFilterEqualsLPortTag(ifTag), customInstructionsMap);

        mdsalManager.addFlow(writeFlowGroupTx, interfaceInfo.getDpId(), flowEntry);
        LOG.trace("Filter equals table(55) drop flow entry created on dpn: {} for interface port: {}",
                interfaceInfo.getDpId(), interfaceInfo.getPortName());
    }

    public void removeFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> flowTx) throws ExecutionException, InterruptedException {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlow(NwConstants.ELAN_FILTER_EQUALS_TABLE,
            getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "group"));

        mdsalManager.removeFlow(flowTx, interfaceInfo.getDpId(), flow);

        Flow flowEntity = MDSALUtil.buildFlow(NwConstants.ELAN_FILTER_EQUALS_TABLE,
            getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "drop"));

        mdsalManager.removeFlow(flowTx, interfaceInfo.getDpId(), flowEntity);
    }

    private static List<MatchInfo> buildMatchesForVni(Uint64 vni) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        MatchInfo match = new MatchTunnelId(vni);
        mkMatches.add(match);
        return mkMatches;
    }

    private static List<InstructionInfo> getInstructionsForOutGroup(long groupId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionWriteActions(Collections.singletonList(new ActionGroup(groupId))));
        return mkInstructions;
    }

    private static List<MatchInfo> getMatchesForElanTag(long elanTag, boolean isSHFlagSet) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchMetadata(
                ElanUtils.getElanMetadataLabel(elanTag, isSHFlagSet), MetaDataUtil.METADATA_MASK_SERVICE_SH_FLAG));
        return mkMatches;
    }

    /**
     * Builds the list of instructions to be installed in the INTERNAL_TUNNEL_TABLE (36) / EXTERNAL_TUNNEL_TABLE (38)
     * which so far consists of writing the elanTag in metadata and send the packet to ELAN_DMAC_TABLE.
     *
     * @param elanTag
     *            elanTag to be written in metadata when flow is selected
     * @return the instructions ready to be installed in a flow
     */
    private static List<InstructionInfo> getInstructionsIntOrExtTunnelTable(Uint32 elanTag) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionWriteMetadata(ElanHelper.getElanMetadataLabel(elanTag.longValue()),
                ElanHelper.getElanMetadataMask()));
        /* applicable for EXTERNAL_TUNNEL_TABLE only
        * TODO: We should point to SMAC or DMAC depending on a configuration property to enable mac learning
        */
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        return mkInstructions;
    }

    // Install DMAC entry on dst DPN
    public List<ListenableFuture<?>> installDMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            Uint64 dstDpId) {
        String interfaceName = interfaceInfo.getInterfaceName();
        ElanInterfaceMac elanInterfaceMac = elanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            Map<MacEntryKey, MacEntry> macEntries = elanInterfaceMac.nonnullMacEntry();
            return Collections.singletonList(ElanUtils.waitForTransactionToComplete(
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    for (MacEntry macEntry : macEntries.values()) {
                        String macAddress = macEntry.getMacAddress().getValue();
                        LOG.info("Installing remote dmac for mac address {} and interface {}", macAddress,
                            interfaceName);
                        try (Acquired lock = ElanUtils.lockElanMacDPN(elanInfo.getElanTag().toJava(), macAddress,
                            interfaceInfo.getDpId())) {
                            LOG.info("Acquired lock for mac : {}, proceeding with remote dmac install operation",
                                macAddress);
                            elanUtils.setupDMacFlowOnRemoteDpn(elanInfo, interfaceInfo, dstDpId, macAddress, tx);
                        }
                    }
                })));
        }
        return emptyList();
    }

    private static void createDropBucket(List<Bucket> listBucket) {
        List<Action> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionDrop().buildAction());
        Bucket dropBucket = MDSALUtil.buildBucket(actionsInfos, MDSALUtil.GROUP_WEIGHT, 0, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP);
        listBucket.add(dropBucket);
    }

    private void setupLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo, TypedWriteTransaction<Configuration> confTx) {
        if (!isOperational(interfaceInfo)) {
            EVENT_LOGGER.debug("ELAN-LBG, ADD {} Instance {} Interface Status {}, returning",
                    interfaceInfo.getInterfaceName(), elanInfo.getElanInstanceName(), interfaceInfo.getOpState());
            return;
        }
        setupStandardLocalBroadcastGroups(elanInfo, newDpnInterface, interfaceInfo, confTx);
        setupLeavesLocalBroadcastGroups(elanInfo, newDpnInterface, interfaceInfo, confTx);
    }

    private void setupStandardLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo, TypedWriteTransaction<Configuration> confTx) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        long groupId = ElanUtils.getElanLocalBCGId(elanInfo.getElanTag().toJava());

        List<String> interfaces = new ArrayList<>();
        if (newDpnInterface != null && newDpnInterface.getInterfaces() != null) {
            interfaces = newDpnInterface.getInterfaces();
        }
        for (String ifName : interfaces) {
            // In case if there is a InterfacePort in the cache which is not in
            // operational state, skip processing it
            InterfaceInfo ifInfo = interfaceManager
                    .getInterfaceInfoFromOperationalDataStore(ifName, interfaceInfo.getInterfaceType());
            if (!isOperational(ifInfo)) {
                continue;
            }

            if (!interfaceManager.isExternalInterface(ifName)) {
                listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId,
                        MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                bucketId++;
            }
        }

        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        LOG.trace("installing the localBroadCast Group:{}", group);
        mdsalManager.addGroup(confTx, interfaceInfo.getDpId(), group);
    }

    private void setupLeavesLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo, TypedWriteTransaction<Configuration> confTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            List<Bucket> listBucket = new ArrayList<>();
            int bucketId = 0;

            List<String> interfaces = new ArrayList<>();
            if (newDpnInterface != null && newDpnInterface.getInterfaces() != null) {
                interfaces = newDpnInterface.getInterfaces();
            }
            for (String ifName : interfaces) {
                // In case if there is a InterfacePort in the cache which is not
                // in
                // operational state, skip processing it
                InterfaceInfo ifInfo = interfaceManager
                        .getInterfaceInfoFromOperationalDataStore(ifName, interfaceInfo.getInterfaceType());
                if (!isOperational(ifInfo)) {
                    continue;
                }

                if (!interfaceManager.isExternalInterface(ifName)) {
                    // only add root interfaces
                    bucketId = addInterfaceIfRootInterface(bucketId, ifName, listBucket, ifInfo);
                }
            }

            if (listBucket.isEmpty()) { // No Buckets
                createDropBucket(listBucket);
            }

            long etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue().toJava();
            long groupId = ElanUtils.getEtreeLeafLocalBCGId(etreeLeafTag);
            Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                    MDSALUtil.buildBucketLists(listBucket));
            LOG.trace("installing the localBroadCast Group:{}", group);
            mdsalManager.addGroup(confTx, interfaceInfo.getDpId(), group);
        }
    }

    private int addInterfaceIfRootInterface(int bucketId, String ifName, List<Bucket> listBucket,
            InterfaceInfo ifInfo) {
        Optional<EtreeInterface> etreeInterface = elanInterfaceCache.getEtreeInterface(ifName);
        if (etreeInterface.isPresent() && etreeInterface.get().getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            listBucket.add(MDSALUtil.buildBucket(getInterfacePortActions(ifInfo), MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return bucketId;
    }

    public void removeLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        Uint64 dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGId(elanInfo.getElanTag().toJava());
        LOG.trace("deleted the localBroadCast Group:{}", groupId);
        mdsalManager.removeGroup(deleteFlowGroupTx, dpnId, groupId);
    }

    public void removeElanBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx)
            throws ExecutionException, InterruptedException {
        Uint64 dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGId(elanInfo.getElanTag().toJava());
        LOG.trace("deleting the remoteBroadCast group:{}", groupId);
        mdsalManager.removeGroup(deleteFlowGroupTx, dpnId, groupId);
    }

    /**
     * Installs a flow in the External Tunnel table consisting in translating
     * the VNI retrieved from the packet that came over a tunnel with a TOR into
     * elanTag that will be used later in the ELANs pipeline.
     * @param dpnId the dpn id
     */
    private void setExternalTunnelTable(Uint64 dpnId, ElanInstance elanInfo,
            TypedWriteTransaction<Configuration> confTx) {
        Uint32 elanTag = elanInfo.getElanTag();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.EXTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanTag.longValue()), 5, // prio
                elanInfo.getElanInstanceName(), // flowName
                0, // idleTimeout
                0, // hardTimeout
                Uint64.valueOf(ITMConstants.COOKIE_ITM_EXTERNAL.toJava().add(BigInteger.valueOf(elanTag.longValue()))),
                buildMatchesForVni(Uint64.valueOf(ElanUtils.getVxlanSegmentationId(elanInfo).longValue())),
                getInstructionsIntOrExtTunnelTable(elanTag));

        mdsalManager.addFlow(confTx, flowEntity);
    }

    /**
     * Removes, from External Tunnel table, the flow that translates from VNI to
     * elanTag. Important: ensure this method is only called whenever there is
     * no other ElanInterface in the specified DPN
     *
     * @param dpnId DPN whose Ext Tunnel table is going to be modified
     */
    private void unsetExternalTunnelTable(Uint64 dpnId, ElanInstance elanInfo,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        // TODO: Use DataStoreJobCoordinator in order to avoid that removing the
        // last ElanInstance plus
        // adding a new one does (almost at the same time) are executed in that
        // exact order

        String flowId = getFlowRef(NwConstants.EXTERNAL_TUNNEL_TABLE, elanInfo.getElanTag().toJava());
        FlowEntity flowEntity = new FlowEntityBuilder()
            .setDpnId(dpnId)
            .setTableId(NwConstants.EXTERNAL_TUNNEL_TABLE)
            .setFlowId(flowId)
            .build();
        mdsalManager.removeFlow(confTx, flowEntity);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, Uint64 dpId,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        setupTerminateServiceTable(elanInfo, dpId, elanInfo.getElanTag(), writeFlowGroupTx);
        setupEtreeTerminateServiceTable(elanInfo, dpId, writeFlowGroupTx);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, Uint64 dpId, Uint32 elanTag,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        List<? extends MatchInfoBase> listMatchInfoBase;
        List<InstructionInfo> instructionInfos;
        Uint32 serviceId;
        if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
            serviceId = elanTag;
            listMatchInfoBase = ElanUtils.getTunnelMatchesForServiceId(elanTag);
            instructionInfos = getInstructionsForOutGroup(ElanUtils.getElanLocalBCGId(elanTag.longValue()));
        } else {
            serviceId = ElanUtils.getVxlanSegmentationId(elanInfo);
            listMatchInfoBase = buildMatchesForVni(Uint64.valueOf(serviceId));
            instructionInfos = getInstructionsIntOrExtTunnelTable(elanTag);
        }
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, serviceId.longValue()), 5,
                String.format("%s:%s", "ITM Flow Entry ", elanTag.toString()), 0, 0,
                Uint64.valueOf(ITMConstants.COOKIE_ITM.toJava().add(BigInteger.valueOf(elanTag.longValue()))),
                listMatchInfoBase, instructionInfos);
        mdsalManager.addFlow(writeFlowGroupTx, flowEntity);
        LOG.info("Installed internal tunnel table (36) flow entry on dpn: {} for elan: {}, tag: {}", dpId,
                elanInfo.getElanInstanceName(), elanTag);
    }

    private void setupEtreeTerminateServiceTable(ElanInstance elanInfo, Uint64 dpId,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            setupTerminateServiceTable(elanInfo, dpId,
                etreeInstance.getEtreeLeafTagVal().getValue(), writeFlowGroupTx);
        }
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, Uint64 dpId,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        long elanTag = elanInfo.getElanTag().toJava();
        installLocalUnknownFlow(elanInfo, dpId, elanTag, writeFlowGroupTx);
        installRemoteUnknownFlow(elanInfo, dpId, elanTag, writeFlowGroupTx);
        setupEtreeUnknownDMacTable(elanInfo, dpId, elanTag, writeFlowGroupTx);
    }

    private void setupEtreeUnknownDMacTable(ElanInstance elanInfo, Uint64 dpId, long elanTag,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            long leafTag = etreeLeafTag.getEtreeLeafTag().getValue().toJava();
            installRemoteUnknownFlow(elanInfo, dpId, leafTag, writeFlowGroupTx);
            installLocalUnknownFlow(elanInfo, dpId, leafTag, writeFlowGroupTx);
        }
    }

    private void installLocalUnknownFlow(ElanInstance elanInfo, Uint64 dpId, long elanTag,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,/* SH flag */false),
                5, elanInfo.getElanInstanceName(), 0, 0,
                Uint64.valueOf(ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.toJava().add(BigInteger.valueOf(elanTag))),
                getMatchesForElanTag(elanTag, /* SH flag */false),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGId(elanTag)));

        mdsalManager.addFlow(writeFlowGroupTx, flowEntity);
        LOG.trace("Installed unknown dmac table (53) flow entry on dpn: {} for elan: {}, tag: {}",
                dpId, elanInfo.getElanInstanceName(), elanTag);
    }

    private void installRemoteUnknownFlow(ElanInstance elanInfo, Uint64 dpId, long elanTag,
            TypedWriteTransaction<Configuration> writeFlowGroupTx) {
        // only if ELAN can connect to external network, perform the following

        if (isVxlanNetworkOrVxlanSegment(elanInfo) || ElanUtils.isVlan(elanInfo) || ElanUtils.isFlat(elanInfo)) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,/* SH flag */true),
                    5, elanInfo.getElanInstanceName(), 0, 0,
                    Uint64.valueOf(ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.toJava().add(BigInteger.valueOf(elanTag))),
                    getMatchesForElanTag(elanTag, /* SH flag */true),
                    getInstructionsForOutGroup(ElanUtils.getElanLocalBCGId(elanTag)));
            mdsalManager.addFlow(writeFlowGroupTx, flowEntity);
            LOG.trace("Installed unknown dmac table (53) flow entry on dpn: {} for elan connected to "
                    + "external network: {}, tag: {}", dpId, elanInfo.getElanInstanceName(), elanTag);
        }
    }


    private void removeUnknownDmacFlow(Uint64 dpId, ElanInstance elanInfo,
            TypedReadWriteTransaction<Configuration> deleteFlowGroupTx, long elanTag)
            throws ExecutionException, InterruptedException {
        Flow flow = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                elanTag, SH_FLAG_UNSET))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE).build();
        mdsalManager.removeFlow(deleteFlowGroupTx, dpId, flow);

        if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
            Flow flow2 = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    elanTag, SH_FLAG_SET))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE)
                    .build();
            mdsalManager.removeFlow(deleteFlowGroupTx, dpId, flow2);
        }
    }

    private void removeDefaultTermFlow(Uint64 dpId, long elanTag) {
        elanUtils.removeTerminatingServiceAction(dpId, (int) elanTag);
    }

    private void bindService(ElanInstance elanInfo, ElanInterface elanInterface, int lportTag,
            TypedWriteTransaction<Configuration> tx) {
        if (isStandardElanService(elanInterface)) {
            bindElanService(elanInfo.getElanTag().toJava(), elanInfo.getElanInstanceName(),
                    elanInterface.getName(), lportTag, tx);
        } else { // Etree service
            bindEtreeService(elanInfo, elanInterface, lportTag, tx);
        }
    }

    private void bindElanService(long elanTag, String elanInstanceName, String interfaceName, int lportTag,
            TypedWriteTransaction<Configuration> tx) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(ElanHelper.getElanMetadataLabel(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));

        List<Action> actions = new ArrayList<>();
        actions.add(new ActionRegLoad(0, NxmNxReg1.class, 0, ElanConstants.INTERFACE_TAG_LENGTH - 1,
                lportTag).buildAction());
        actions.add(new ActionRegLoad(1, ElanConstants.ELAN_REG_ID, 0, ElanConstants.ELAN_TAG_LENGTH - 1,
                elanTag).buildAction());
        instructions.add(MDSALUtil.buildApplyActionsInstruction(actions, ++instructionKey));

        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.ARP_CHECK_TABLE,
                ++instructionKey));

        short elanServiceIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
        BoundServices serviceInfo = ElanUtils.getBoundServices(
                String.format("%s.%s.%s", "elan", elanInstanceName, interfaceName), elanServiceIndex,
                NwConstants.ELAN_SERVICE_INDEX, NwConstants.COOKIE_ELAN_INGRESS_TABLE, instructions);
        InstanceIdentifier<BoundServices> bindServiceId = ElanUtils.buildServiceId(interfaceName, elanServiceIndex);
        Optional<BoundServices> existingElanService = ElanUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                bindServiceId);
        if (!existingElanService.isPresent()) {
            tx.mergeParentStructurePut(bindServiceId, serviceInfo);
            LOG.trace("Done binding elan service for elan: {} for interface: {}", elanInstanceName, interfaceName);
        }
    }

    private void bindEtreeService(ElanInstance elanInfo, ElanInterface elanInterface, int lportTag,
            TypedWriteTransaction<Configuration> tx) {
        if (elanInterface.augmentation(EtreeInterface.class).getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            bindElanService(elanInfo.getElanTag().toJava(), elanInfo.getElanInstanceName(), elanInterface.getName(),
                    lportTag, tx);
        } else {
            EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
            if (etreeInstance == null) {
                LOG.error("EtreeInterface {} is associated with a non EtreeInstance: {}",
                        elanInterface.getName(), elanInfo.getElanInstanceName());
            } else {
                bindElanService(etreeInstance.getEtreeLeafTagVal().getValue().toJava(), elanInfo.getElanInstanceName(),
                        elanInterface.getName(), lportTag, tx);
            }
        }
    }

    private static boolean isStandardElanService(ElanInterface elanInterface) {
        return elanInterface.augmentation(EtreeInterface.class) == null;
    }

    protected void unbindService(String interfaceName, TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        short elanServiceIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
        InstanceIdentifier<BoundServices> bindServiceId = ElanUtils.buildServiceId(interfaceName, elanServiceIndex);
        if (tx.read(bindServiceId).get().isPresent()) {
            tx.delete(bindServiceId);
        }
    }

    private static String getFlowRef(long tableId, long elanTag) {
        return String.valueOf(tableId) + elanTag;
    }

    private static String getFlowRef(long tableId, long elanTag, String flowName) {
        return new StringBuilder().append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(elanTag)
                .append(NwConstants.FLOWID_SEPARATOR).append(flowName).toString();
    }

    private static String getUnknownDmacFlowRef(long tableId, long elanTag, boolean shFlag) {
        return String.valueOf(tableId) + elanTag + shFlag;
    }

    private static List<Action> getInterfacePortActions(InterfaceInfo interfaceInfo) {
        List<Action> listAction = new ArrayList<>();
        int actionKey = 0;
        listAction.add(
            new ActionSetFieldTunnelId(Uint64.valueOf(interfaceInfo.getInterfaceTag())).buildAction(actionKey));
        actionKey++;
        listAction.add(new ActionNxResubmit(NwConstants.ELAN_FILTER_EQUALS_TABLE).buildAction(actionKey));
        return listAction;
    }

    private static DpnInterfaces updateElanDpnInterfacesList(String elanInstanceName, Uint64 dpId,
            List<String> interfaceNames, TypedWriteTransaction<Operational> tx) {
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .withKey(new DpnInterfacesKey(dpId)).build();
        tx.mergeParentStructurePut(ElanUtils
                .getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId), dpnInterface);
        LOG.trace("Updated operational dpn interfaces for elan: {} with interfaces: {}", elanInstanceName,
                interfaceNames);
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
    private static void deleteElanDpnInterface(String elanInstanceName, Uint64 dpId,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        InstanceIdentifier<DpnInterfaces> dpnInterfacesId = ElanUtils
                .getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
        Optional<DpnInterfaces> dpnInterfaces = tx.read(dpnInterfacesId).get();
        if (dpnInterfaces.isPresent()) {
            tx.delete(dpnInterfacesId);
        }
    }

    private static DpnInterfaces createElanInterfacesList(String elanInstanceName, String interfaceName,
            Uint64 dpId, TypedWriteTransaction<Operational> tx) {
        List<String> interfaceNames = new ArrayList<>();
        interfaceNames.add(interfaceName);
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .withKey(new DpnInterfacesKey(dpId)).build();
        return dpnInterface;
    }

    private static void createElanStateList(String elanInstanceName, String interfaceName,
            TypedReadWriteTransaction<Operational> tx) throws ExecutionException, InterruptedException {
        InstanceIdentifier<Elan> elanInstance = ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInterfaceLists = tx.read(elanInstance).get();
        // Adding new Elan Interface Port to the operational DataStore without
        // Static-Mac Entries..
        if (elanInterfaceLists.isPresent()) {
            List<String> interfaceLists = new ArrayList<>();
            interfaceLists.add(interfaceName);
            List<String> existingInterfaceLists = elanInterfaceLists.get().getElanInterfaces();
            if (existingInterfaceLists != null && !existingInterfaceLists.isEmpty()) {
                existingInterfaceLists.forEach(iface -> interfaceLists.add(iface));
            }

            Elan elanState = new ElanBuilder().setName(elanInstanceName).setElanInterfaces(interfaceLists)
                    .withKey(new ElanKey(elanInstanceName)).build();
            tx.mergeParentStructurePut(ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName), elanState);
            LOG.trace("Updated operational elan state for elan: {} with interfaces: {}", elanInstanceName,
                    interfaceLists);
        }
    }

    private static boolean isOperational(InterfaceInfo interfaceInfo) {
        return interfaceInfo != null && interfaceInfo.getAdminState() == InterfaceInfo.InterfaceAdminState.ENABLED;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleInternalTunnelStateEvent(Uint64 srcDpId, Uint64 dstDpId) {
        ElanDpnInterfaces dpnInterfaceLists = elanUtils.getElanDpnInterfacesList();
        LOG.trace("processing tunnel state event for srcDpId {} dstDpId {}"
                + " and dpnInterfaceList {}", srcDpId, dstDpId, dpnInterfaceLists);
        if (dpnInterfaceLists == null) {
            return;
        }
        Map<ElanDpnInterfacesListKey, ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists
                .nonnullElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf.values()) {
            int cnt = 0;
            String elanName = elanDpns.getElanInstanceName();
            ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);
            if (elanInfo == null) {
                LOG.warn("ELAN Info is null for elanName {} that does exist in elanDpnInterfaceList, "
                        + "skipping this ELAN for tunnel handling", elanName);
                continue;
            }
            if (!isVxlanNetworkOrVxlanSegment(elanInfo)) {
                LOG.debug("Ignoring internal tunnel state event for Flat/Vlan elan {}", elanName);
                continue;
            }
            Map<DpnInterfacesKey, DpnInterfaces> dpnInterfaces = elanDpns.nonnullDpnInterfaces();
            if (dpnInterfaces == null) {
                continue;
            }
            DpnInterfaces dstDpnIf = null;
            for (DpnInterfaces dpnIf : dpnInterfaces.values()) {
                Uint64 dpnIfDpId = dpnIf.getDpId();
                if (Objects.equals(dpnIfDpId, srcDpId)) {
                    cnt++;
                } else if (Objects.equals(dpnIfDpId, dstDpId)) {
                    cnt++;
                    dstDpnIf = dpnIf;
                }
            }
            if (cnt == 2) {
                LOG.info("Elan instance:{} is present b/w srcDpn:{} and dstDpn:{}", elanName, srcDpId, dstDpId);
                final DpnInterfaces finalDstDpnIf = dstDpnIf; // var needs to be final so it can be accessed in lambda
                jobCoordinator.enqueueJob(elanName, () -> {
                    // update Remote BC Group
                    LOG.trace("procesing elan remote bc group for tunnel event {}", elanInfo);
                    try {
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                            confTx -> elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, srcDpId,
                                confTx)).get();
                    } catch (RuntimeException e) {
                        LOG.error("Error while adding remote bc group for {} on dpId {} ", elanName, srcDpId);
                    }
                    Set<String> interfaceLists = new HashSet<>();
                    interfaceLists.addAll(finalDstDpnIf.getInterfaces());
                    for (String ifName : interfaceLists) {
                        jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(ifName), () -> {
                            LOG.info("Processing tunnel up event for elan {} and interface {}", elanName, ifName);
                            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(ifName);
                            if (isOperational(interfaceInfo)) {
                                return installDMacAddressTables(elanInfo, interfaceInfo, srcDpId);
                            }
                            return emptyList();
                        }, ElanConstants.JOB_MAX_RETRIES);
                    }
                    return emptyList();
                }, ElanConstants.JOB_MAX_RETRIES);
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
    void handleExternalTunnelStateEvent(ExternalTunnel externalTunnel, Interface intrf) {
        if (!validateExternalTunnelStateEvent(externalTunnel, intrf)) {
            return;
        }
        // dpId/externalNodeId will be available either in source or destination
        // based on the tunnel end point
        Uint64 dpId = null;
        NodeId externalNodeId = null;
        if (StringUtils.isNumeric(externalTunnel.getSourceDevice())) {
            dpId = Uint64.valueOf(externalTunnel.getSourceDevice());
            externalNodeId = new NodeId(externalTunnel.getDestinationDevice());
        } else if (StringUtils.isNumeric(externalTunnel.getDestinationDevice())) {
            dpId = Uint64.valueOf(externalTunnel.getDestinationDevice());
            externalNodeId = new NodeId(externalTunnel.getSourceDevice());
        }
        if (dpId == null || externalNodeId == null) {
            LOG.error("Dp ID / externalNodeId not found in external tunnel {}", externalTunnel);
            return;
        }

        ElanDpnInterfaces dpnInterfaceLists = elanUtils.getElanDpnInterfacesList();
        if (dpnInterfaceLists == null) {
            return;
        }
        Map<ElanDpnInterfacesListKey, ElanDpnInterfacesList> elanDpnIf
                = dpnInterfaceLists.nonnullElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf.values()) {
            String elanName = elanDpns.getElanInstanceName();
            ElanInstance elanInfo = elanInstanceCache.get(elanName).orElse(null);

            DpnInterfaces dpnInterfaces = elanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if (elanInfo == null || dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                    || dpnInterfaces.getInterfaces().isEmpty()) {
                continue;
            }
            LOG.debug("Elan instance:{} is present in Dpn:{} ", elanName, dpId);

            final Uint64 finalDpId = dpId;
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                confTx -> elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, finalDpId, confTx)), LOG,
                "Error setting up ELAN BGs");
            // install L2gwDevices local macs in dpn.
            elanL2GatewayUtils.installL2gwDeviceMacsInDpn(dpId, externalNodeId, elanInfo, intrf.getName());
            // Install dpn macs on external device
            installDpnMacsInL2gwDevice(elanName, new HashSet<>(dpnInterfaces.getInterfaces()), dpId,
                    externalNodeId);
        }
        LOG.info("Handled ExternalTunnelStateEvent for {}", externalTunnel);
    }

    /**
     * Installs dpn macs in external device. first it checks if the physical
     * locator towards this dpn tep is present or not if the physical locator is
     * present go ahead and add the ucast macs otherwise update the mcast mac
     * entry to include this dpn tep ip and schedule the job to put ucast macs
     * once the physical locator is programmed in device
     *
     * @param elanName
     *            the elan name
     * @param lstElanInterfaceNames
     *            the lst Elan interface names
     * @param dpnId
     *            the dpn id
     * @param externalNodeId
     *            the external node id
     */
    private void installDpnMacsInL2gwDevice(String elanName, Set<String> lstElanInterfaceNames, Uint64 dpnId,
            NodeId externalNodeId) {
        L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName,
                externalNodeId.getValue());
        if (elanL2GwDevice == null) {
            LOG.debug("L2 gw device not found in elan cache for device name {}", externalNodeId);
            return;
        }
        IpAddress dpnTepIp = elanItmUtils.getSourceDpnTepIp(dpnId, externalNodeId);
        if (dpnTepIp == null) {
            LOG.warn("Could not install dpn macs in l2gw device , dpnTepIp not found dpn : {} , nodeid : {}", dpnId,
                    externalNodeId);
            return;
        }

        String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName);
        RemoteMcastMacs remoteMcastMac = elanL2GatewayUtils.readRemoteMcastMac(externalNodeId, logicalSwitchName,
                LogicalDatastoreType.OPERATIONAL);
        boolean phyLocAlreadyExists =
                ElanL2GatewayUtils.checkIfPhyLocatorAlreadyExistsInRemoteMcastEntry(externalNodeId, remoteMcastMac,
                dpnTepIp);
        LOG.debug("phyLocAlreadyExists = {} for locator [{}] in remote mcast entry for elan [{}], nodeId [{}]",
                phyLocAlreadyExists, dpnTepIp.stringValue(), elanName, externalNodeId.getValue());
        /*
        List<PhysAddress> staticMacs = elanL2GatewayUtils.getElanDpnMacsFromInterfaces(lstElanInterfaceNames);

        if (phyLocAlreadyExists) {
            elanL2GatewayUtils.scheduleAddDpnMacsInExtDevice(elanName, dpnId, staticMacs, elanL2GwDevice);
            return;
        }
        elanL2GatewayMulticastUtils.scheduleMcastMacUpdateJob(elanName, elanL2GwDevice);
        elanL2GatewayUtils.scheduleAddDpnMacsInExtDevice(elanName, dpnId, staticMacs, elanL2GwDevice);
        */
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
            ExternalTunnel otherEndPointExtTunnel = elanUtils.getExternalTunnel(srcDevice, destDevice,
                    LogicalDatastoreType.CONFIGURATION);
            LOG.trace("Validating external tunnel state: src tunnel {}, dest tunnel {}", externalTunnel,
                    otherEndPointExtTunnel);
            if (otherEndPointExtTunnel != null) {
                boolean otherEndPointInterfaceOperational = ElanUtils.isInterfaceOperational(
                        otherEndPointExtTunnel.getTunnelInterfaceName(), broker);
                if (otherEndPointInterfaceOperational) {
                    return true;
                } else {
                    LOG.debug("Other end [{}] of the external tunnel is not yet UP for {}",
                            otherEndPointExtTunnel.getTunnelInterfaceName(), externalTunnel);
                }
            }
        }
        return false;
    }

    private static List<MatchInfo> getMatchesForFilterEqualsLPortTag(int lportTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(
                new MatchMetadata(MetaDataUtil.getLportTagMetaData(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG));
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(lportTag)));
        return mkMatches;
    }

    public void handleExternalInterfaceEvent(ElanInstance elanInstance, DpnInterfaces dpnInterfaces,
                                             Uint64 dpId) {
        LOG.debug("setting up remote BC group for elan {}", elanInstance.getPhysicalNetworkName());
        addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            confTx -> elanL2GatewayMulticastUtils.setupStandardElanBroadcastGroups(elanInstance, dpnInterfaces, dpId,
                confTx)), LOG, "Error setting up remote BC group for ELAN {}", elanInstance.getPhysicalNetworkName());
        try {
            Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
        } catch (InterruptedException e) {
            LOG.warn("Error while waiting for local BC group for ELAN {} to install", elanInstance);
        }
    }
}
