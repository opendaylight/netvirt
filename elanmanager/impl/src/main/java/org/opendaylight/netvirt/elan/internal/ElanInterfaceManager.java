/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlanNetworkOrVxlanSegment;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
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
import org.opendaylight.genius.srm.RecoverableListener;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.ElanException;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface.EtreeInterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class in charge of handling creations, modifications and removals of
 * ElanInterfaces.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 */
@Singleton
public class ElanInterfaceManager extends AsyncDataTreeChangeListenerBase<ElanInterface, ElanInterfaceManager>
        implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceManager.class);
    private static final long WAIT_TIME_FOR_SYNC_INSTALL = Long.getLong("wait.time.sync.install", 300L);
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
                                final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(ElanInterface.class, ElanInterfaceManager.class);
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
        serviceRecoveryRegistry.addRecoverableListener(elanServiceRecoveryHandler.buildServiceRegistryKey(), this);
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener();
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInterface> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> identifier, ElanInterface del) {
        String interfaceName = del.getName();
        ElanInstance elanInfo = elanInstanceCache.get(del.getElanInstanceName()).orNull();
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
        String elanInstanceName = elanInfo.getElanInstanceName();
        InterfaceRemoveWorkerOnElan configWorker = new InterfaceRemoveWorkerOnElan(elanInstanceName, elanInfo,
                interfaceName, interfaceInfo, this);
        jobCoordinator.enqueueJob(elanInstanceName, configWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    @SuppressWarnings("checkstyle:ForbiddenMethod")
    public List<ListenableFuture<Void>> removeElanInterface(ElanInstance elanInfo, String interfaceName,
            InterfaceInfo interfaceInfo) {
        String elanName = elanInfo.getElanInstanceName();
        boolean isLastElanInterface = false;
        boolean isLastInterfaceOnDpn = false;
        BigInteger dpId = null;
        long elanTag = elanInfo.getElanTag();
        // We use two transaction so we don't suffer on multiple shards (interfaces and flows)
        WriteTransaction interfaceTx = broker.newWriteOnlyTransaction();
        Elan elanState = removeElanStateForInterface(elanInfo, interfaceName, interfaceTx);
        if (elanState == null) {
            interfaceTx.cancel();
            return Collections.emptyList();
        }
        WriteTransaction flowTx = broker.newWriteOnlyTransaction();
        List<String> elanInterfaces = elanState.getElanInterfaces();
        if (elanInterfaces.isEmpty()) {
            isLastElanInterface = true;
        }
        if (interfaceInfo != null) {
            dpId = interfaceInfo.getDpId();
            DpnInterfaces dpnInterfaces = removeElanDpnInterfaceFromOperationalDataStore(elanName, dpId,
                    interfaceName, elanTag, interfaceTx);
            /*
             * If there are not elan ports, remove the unknown dmac, terminating
             * service table flows, remote/local bc group
             */
            if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                    || dpnInterfaces.getInterfaces().isEmpty()) {
                // No more Elan Interfaces in this DPN
                LOG.debug("deleting the elan: {} present on dpId: {}", elanInfo.getElanInstanceName(), dpId);
                if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
                    removeDefaultTermFlow(dpId, elanInfo.getElanTag());
                }
                removeUnknownDmacFlow(dpId, elanInfo, flowTx, elanInfo.getElanTag());
                removeEtreeUnknownDmacFlow(dpId, elanInfo, flowTx);
                removeElanBroadcastGroup(elanInfo, interfaceInfo, flowTx);
                removeLocalBroadcastGroup(elanInfo, interfaceInfo, flowTx);
                removeEtreeBroadcastGrups(elanInfo, interfaceInfo, flowTx);
                if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
                    if (elanUtils.isOpenstackVniSemanticsEnforced()) {
                        elanUtils.removeTerminatingServiceAction(dpId,
                                elanUtils.getVxlanSegmentationId(elanInfo).intValue());
                    }
                    unsetExternalTunnelTable(dpId, elanInfo);
                }
                isLastInterfaceOnDpn = true;
            } else {
                setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo);
            }
        }

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(ElanUtils.waitForTransactionToComplete(interfaceTx));
        futures.add(ElanUtils.waitForTransactionToComplete(flowTx));

        if (isLastInterfaceOnDpn && dpId != null && isVxlanNetworkOrVxlanSegment(elanInfo)) {
            setElanAndEtreeBCGrouponOtherDpns(elanInfo, dpId);
        }
        InterfaceRemoveWorkerOnElanInterface removeInterfaceWorker = new InterfaceRemoveWorkerOnElanInterface(
                interfaceName, elanInfo, interfaceInfo, this, isLastElanInterface);
        jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(interfaceName), removeInterfaceWorker,
                ElanConstants.JOB_MAX_RETRIES);

        return futures;
    }

    private void removeEtreeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo,
            WriteTransaction deleteFlowGroupTx) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanInfo.getElanTag());
        if (etreeLeafTag != null) {
            long leafTag = etreeLeafTag.getEtreeLeafTag().getValue();
            removeUnknownDmacFlow(dpId, elanInfo, deleteFlowGroupTx, leafTag);
        }
    }

    private void removeEtreeBroadcastGrups(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        removeLeavesEtreeBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
        removeLeavesLocalBroadcastGroup(elanInfo, interfaceInfo, deleteFlowGroupTx);
    }

    private void removeLeavesLocalBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            BigInteger dpnId = interfaceInfo.getDpId();
            long groupId = ElanUtils.getEtreeLeafLocalBCGId(etreeInstance.getEtreeLeafTagVal().getValue());
            List<Bucket> listBuckets = new ArrayList<>();
            int bucketId = 0;
            listBuckets.add(getLocalBCGroupBucketInfo(interfaceInfo, bucketId));
            Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                    MDSALUtil.buildBucketLists(listBuckets));
            LOG.trace("deleted the localBroadCast Group:{}", group);
            mdsalManager.removeGroupToTx(dpnId, group, deleteFlowGroupTx);
        }
    }

    private void removeLeavesEtreeBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            long etreeTag = etreeInstance.getEtreeLeafTagVal().getValue();
            int bucketId = 0;
            int actionKey = 0;
            List<Bucket> listBuckets = new ArrayList<>();
            List<Action> listAction = new ArrayList<>();
            listAction.add(new ActionGroup(ElanUtils.getEtreeLeafLocalBCGId(etreeTag)).buildAction(++actionKey));
            listBuckets.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                    MDSALUtil.WATCH_GROUP));
            bucketId++;
            listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, bucketId, interfaceInfo, etreeTag));
            BigInteger dpnId = interfaceInfo.getDpId();
            long groupId = ElanUtils.getEtreeLeafRemoteBCGId(etreeTag);
            Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                    MDSALUtil.buildBucketLists(listBuckets));
            LOG.trace("deleting the remoteBroadCast group:{}", group);
            mdsalManager.removeGroupToTx(dpnId, group, deleteFlowGroupTx);
        }
    }

    private Elan removeElanStateForInterface(ElanInstance elanInfo, String interfaceName, WriteTransaction tx) {
        String elanName = elanInfo.getElanInstanceName();
        Elan elanState = ElanUtils.getElanByName(broker, elanName);
        if (elanState == null) {
            return elanState;
        }
        List<String> elanInterfaces = elanState.getElanInterfaces();
        boolean isRemoved = elanInterfaces.remove(interfaceName);
        if (!isRemoved) {
            return elanState;
        }

        if (elanInterfaces.isEmpty()) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName));
            tx.delete(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanMacTableOperationalDataPath(elanName));
            tx.delete(LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInfoEntriesOperationalDataPath(elanInfo.getElanTag()));
        } else {
            Elan updateElanState = new ElanBuilder().setElanInterfaces(elanInterfaces).setName(elanName)
                    .withKey(new ElanKey(elanName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanName),
                    updateElanState);
        }
        return elanState;
    }

    private void deleteElanInterfaceFromConfigDS(String interfaceName, WriteTransaction tx) {
        // removing the ElanInterface from the config data_store if interface is
        // not present in Interface config DS
        if (interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName) == null
                && elanInterfaceCache.get(interfaceName).isPresent()) {
            tx.delete(LogicalDatastoreType.CONFIGURATION,
                    ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
        }
    }

    List<ListenableFuture<Void>> removeEntriesForElanInterface(ElanInstance elanInfo, InterfaceInfo
            interfaceInfo, String interfaceName, boolean isLastElanInterface) {
        String elanName = elanInfo.getElanInstanceName();
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(flowTx -> {
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(interfaceTx -> {
                InstanceIdentifier<ElanInterfaceMac> elanInterfaceId = ElanUtils
                        .getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
                Optional<ElanInterfaceMac> existingElanInterfaceMac =
                        interfaceTx.read(LogicalDatastoreType.OPERATIONAL, elanInterfaceId).checkedGet();
                LOG.debug("Removing the Interface:{} from elan:{}", interfaceName, elanName);
                if (interfaceInfo != null) {
                    if (existingElanInterfaceMac.isPresent()) {
                        List<MacEntry> existingMacEntries = existingElanInterfaceMac.get().getMacEntry();
                        if (existingMacEntries != null) {
                            List<PhysAddress> macAddresses = new ArrayList<>();
                            for (MacEntry macEntry : existingMacEntries) {
                                PhysAddress macAddress = macEntry.getMacAddress();
                                LOG.debug("removing the  mac-entry:{} present on elanInterface:{}",
                                        macAddress.getValue(), interfaceName);
                                Optional<MacEntry> macEntryOptional =
                                        elanUtils.getMacEntryForElanInstance(interfaceTx, elanName, macAddress);
                                if (!isLastElanInterface && macEntryOptional.isPresent()) {
                                    interfaceTx.delete(LogicalDatastoreType.OPERATIONAL,
                                            ElanUtils.getMacEntryOperationalDataPath(elanName, macAddress));
                                }
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
                    List<MacEntry> macEntries = existingElanInterfaceMac.get().getMacEntry();
                    if (macEntries != null) {
                        for (MacEntry macEntry : macEntries) {
                            PhysAddress macAddress = macEntry.getMacAddress();
                            if (elanUtils.getMacEntryForElanInstance(elanName, macAddress).isPresent()) {
                                interfaceTx.delete(LogicalDatastoreType.OPERATIONAL,
                                        ElanUtils.getMacEntryOperationalDataPath(elanName, macAddress));
                            }
                        }
                    }
                }
                if (existingElanInterfaceMac.isPresent()) {
                    interfaceTx.delete(LogicalDatastoreType.OPERATIONAL, elanInterfaceId);
                }
                unbindService(interfaceName, interfaceTx);
                deleteElanInterfaceFromConfigDS(interfaceName, interfaceTx);
            }));
        }));
        return futures;
    }

    private DpnInterfaces removeElanDpnInterfaceFromOperationalDataStore(String elanName, BigInteger dpId,
                                                                         String interfaceName, long elanTag,
                                                                         WriteTransaction tx) {
        synchronized (elanName.intern()) {

            DpnInterfaces dpnInterfaces = elanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if (dpnInterfaces != null) {
                List<String> interfaceLists = dpnInterfaces.getInterfaces();
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
        }
    }

    private void deleteAllRemoteMacsInADpn(String elanName, BigInteger dpId, long elanTag) {
        List<DpnInterfaces> dpnInterfaces = elanUtils.getInvolvedDpnsInElan(elanName);
        for (DpnInterfaces dpnInterface : dpnInterfaces) {
            BigInteger currentDpId = dpnInterface.getDpId();
            if (!currentDpId.equals(dpId)) {
                for (String elanInterface : dpnInterface.getInterfaces()) {
                    ElanInterfaceMac macs = elanUtils.getElanInterfaceMacByInterfaceName(elanInterface);
                    if (macs == null || macs.getMacEntry() == null) {
                        continue;
                    }
                    for (MacEntry mac : macs.getMacEntry()) {
                        removeTheMacFlowInTheDPN(dpId, elanTag, currentDpId, mac);
                        removeEtreeMacFlowInTheDPN(dpId, elanTag, currentDpId, mac);
                    }
                }
            }
        }
    }

    private void removeEtreeMacFlowInTheDPN(BigInteger dpId, long elanTag, BigInteger currentDpId, MacEntry mac) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            removeTheMacFlowInTheDPN(dpId, etreeLeafTag.getEtreeLeafTag().getValue(), currentDpId, mac);
        }
    }

    private void removeTheMacFlowInTheDPN(BigInteger dpId, long elanTag, BigInteger currentDpId, MacEntry mac) {
        mdsalManager
                .removeFlow(dpId,
                        MDSALUtil.buildFlow(NwConstants.ELAN_DMAC_TABLE,
                                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, currentDpId,
                                        mac.getMacAddress().getValue(), elanTag)));
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
    @SuppressWarnings("checkstyle:ForbiddenMethod")
    @Override
    protected void update(InstanceIdentifier<ElanInterface> identifier, ElanInterface original, ElanInterface update) {
        // updating the static-Mac Entries for the existing elanInterface
        String elanName = update.getElanInstanceName();
        String interfaceName = update.getName();

        List<StaticMacEntries> originalStaticMacEntries = original.getStaticMacEntries();
        List<StaticMacEntries> updatedStaticMacEntries = update.getStaticMacEntries();
        List<StaticMacEntries> deletedEntries = ElanUtils.diffOf(originalStaticMacEntries, updatedStaticMacEntries);
        List<StaticMacEntries> updatedEntries = ElanUtils.diffOf(updatedStaticMacEntries, originalStaticMacEntries);

        deletedEntries.forEach((deletedEntry) -> removeInterfaceStaticMacEntries(elanName, interfaceName,
                deletedEntry.getMacAddress()));

        /*if updatedStaticMacEntries is NOT NULL, which means as part of update call these entries were added.
        * Hence add the macentries for the same.*/
        for (StaticMacEntries staticMacEntry : updatedEntries) {
            InstanceIdentifier<MacEntry> macEntryIdentifier = getMacEntryOperationalDataPath(elanName,
                    staticMacEntry.getMacAddress());
            Optional<MacEntry> existingMacEntry = ElanUtils.read(broker,
                    LogicalDatastoreType.OPERATIONAL, macEntryIdentifier);
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            if (existingMacEntry.isPresent()) {
                elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(
                        elanName, interfaceName, existingMacEntry.get().getInterface(), existingMacEntry.get(),
                        tx);
            } else {
                elanForwardingEntriesHandler.addElanInterfaceForwardingTableList(
                        elanName, interfaceName, staticMacEntry, tx);
            }
            ListenableFutures.addErrorLogging(ElanUtils.waitForTransactionToComplete(tx), LOG,
                    "Error in update: identifier={}, original={}, update={}", identifier, original, update);
        }
    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded) {
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            String elanInstanceName = elanInterfaceAdded.getElanInstanceName();
            String interfaceName = elanInterfaceAdded.getName();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            if (interfaceInfo == null) {
                LOG.info("Interface {} is removed from Interface Oper DS due to port down ", interfaceName);
                return;
            }
            ElanInstance elanInstance = elanInstanceCache.get(elanInstanceName).orNull();

            if (elanInstance == null) {
                elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName)
                        .setDescription(elanInterfaceAdded.getDescription()).build();
                // Add the ElanInstance in the Configuration data-store
                List<String> elanInterfaces = new ArrayList<>();
                elanInterfaces.add(interfaceName);
                elanInstance = ElanUtils.updateOperationalDataStore(idManager, elanInstance, elanInterfaces, tx);
            }

            Long elanTag = elanInstance.getElanTag();
            // If elan tag is not updated, then put the elan interface into
            // unprocessed entry map and entry. Let entries
            // in this map get processed during ELAN update DCN.
            if (elanTag == null) {
                ConcurrentLinkedQueue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstanceName);
                if (elanInterfaces == null) {
                    elanInterfaces = new ConcurrentLinkedQueue<>();
                }
                elanInterfaces.add(elanInterfaceAdded);
                unProcessedElanInterfaces.put(elanInstanceName, elanInterfaces);
                return;
            }
            InterfaceAddWorkerOnElan addWorker = new InterfaceAddWorkerOnElan(elanInstanceName, elanInterfaceAdded,
                    interfaceInfo, elanInstance, this);
            jobCoordinator.enqueueJob(elanInstanceName, addWorker, ElanConstants.JOB_MAX_RETRIES);
        }), LOG, "Error procedding added ELAN interface");
    }

    List<ListenableFuture<Void>> handleunprocessedElanInterfaces(ElanInstance elanInstance) throws ElanException {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        Queue<ElanInterface> elanInterfaces = unProcessedElanInterfaces.get(elanInstance.getElanInstanceName());
        if (elanInterfaces == null || elanInterfaces.isEmpty()) {
            return futures;
        }
        for (ElanInterface elanInterface : elanInterfaces) {
            String interfaceName = elanInterface.getName();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            futures.addAll(addElanInterface(elanInterface, interfaceInfo, elanInstance));
        }
        return futures;
    }

    void programRemoteDmacFlow(ElanInstance elanInstance, InterfaceInfo interfaceInfo,
            WriteTransaction writeFlowGroupTx) throws ElanException {
        ElanDpnInterfacesList elanDpnInterfacesList = elanUtils
                .getElanDpnInterfacesList(elanInstance.getElanInstanceName());
        List<DpnInterfaces> dpnInterfaceLists = null;
        if (elanDpnInterfacesList != null) {
            dpnInterfaceLists = elanDpnInterfacesList.getDpnInterfaces();
        }
        if (dpnInterfaceLists == null) {
            dpnInterfaceLists = new ArrayList<>();
        }
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            BigInteger dstDpId = interfaceInfo.getDpId();
            if (dpnInterfaces.getDpId().equals(dstDpId)) {
                continue;
            }
            List<String> remoteElanInterfaces = dpnInterfaces.getInterfaces();
            for (String remoteIf : remoteElanInterfaces) {
                ElanInterfaceMac elanIfMac = elanUtils.getElanInterfaceMacByInterfaceName(remoteIf);
                InterfaceInfo remoteInterface = interfaceManager.getInterfaceInfo(remoteIf);
                if (elanIfMac == null || remoteInterface == null) {
                    continue;
                }
                List<MacEntry> remoteMacEntries = elanIfMac.getMacEntry();
                if (remoteMacEntries != null) {
                    for (MacEntry macEntry : remoteMacEntries) {
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
    }

    @SuppressWarnings("checkstyle:ForbiddenMethod")
    List<ListenableFuture<Void>> addElanInterface(ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, ElanInstance elanInstance) throws ElanException {
        Preconditions.checkNotNull(elanInstance, "elanInstance cannot be null");
        Preconditions.checkNotNull(interfaceInfo, "interfaceInfo cannot be null");
        Preconditions.checkNotNull(elanInterface, "elanInterface cannot be null");

        String interfaceName = elanInterface.getName();
        String elanInstanceName = elanInterface.getElanInstanceName();

        Elan elanInfo = ElanUtils.getElanByName(broker, elanInstanceName);
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        if (elanInfo == null) {
            List<String> elanInterfaces = new ArrayList<>();
            elanInterfaces.add(interfaceName);
            ElanUtils.updateOperationalDataStore(idManager, elanInstance, elanInterfaces, tx);
        } else {
            createElanStateList(elanInstanceName, interfaceName, tx);
        }
        boolean isFirstInterfaceInDpn = false;
        // Specific actions to the DPN where the ElanInterface has been added,
        // for example, programming the
        // External tunnel table if needed or adding the ElanInterface to the
        // DpnInterfaces in the operational DS.
        BigInteger dpId = interfaceInfo.getDpId();
        DpnInterfaces dpnInterfaces = null;
        if (dpId != null && !dpId.equals(ElanConstants.INVALID_DPN)) {
            synchronized (elanInstanceName.intern()) {
                InstanceIdentifier<DpnInterfaces> elanDpnInterfaces = ElanUtils
                        .getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
                Optional<DpnInterfaces> existingElanDpnInterfaces = ElanUtils.read(broker,
                        LogicalDatastoreType.OPERATIONAL, elanDpnInterfaces);
                if (ElanUtils.isVlan(elanInstance)) {
                    isFirstInterfaceInDpn =  checkIfFirstInterface(interfaceName,
                            elanInstanceName, existingElanDpnInterfaces);
                } else {
                    isFirstInterfaceInDpn = !existingElanDpnInterfaces.isPresent();
                }
                if (isFirstInterfaceInDpn) {
                    // ELAN's 1st ElanInterface added to this DPN
                    if (!existingElanDpnInterfaces.isPresent()) {
                        dpnInterfaces = createElanInterfacesList(elanInstanceName, interfaceName, dpId, tx);
                    } else {
                        List<String> elanInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                        elanInterfaces.add(interfaceName);
                        dpnInterfaces = updateElanDpnInterfacesList(elanInstanceName, dpId,
                                elanInterfaces, tx);
                    }
                    // The 1st ElanInterface in a DPN must program the Ext Tunnel
                    // table, but only if Elan has VNI
                    if (isVxlanNetworkOrVxlanSegment(elanInstance)) {
                        setExternalTunnelTable(dpId, elanInstance);
                    }
                    elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance, interfaceName);
                } else {
                    List<String> elanInterfaces = existingElanDpnInterfaces.get().getInterfaces();
                    elanInterfaces.add(interfaceName);
                    if (elanInterfaces.size() == 1) { // 1st dpn interface
                        elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(dpId, elanInstance, interfaceName);
                    }
                    dpnInterfaces = updateElanDpnInterfacesList(elanInstanceName, dpId, elanInterfaces, tx);
                }
            }
        }

        // add code to install Local/Remote BC group, unknow DMAC entry,
        // terminating service table flow entry
        // call bindservice of interfacemanager to create ingress table flow
        // enty.
        // Add interface to the ElanInterfaceForwardingEntires Container
        createElanInterfaceTablesList(interfaceName, tx);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(ElanUtils.waitForTransactionToComplete(tx));
        installEntriesForFirstInterfaceonDpn(elanInstance, interfaceInfo, dpnInterfaces, isFirstInterfaceInDpn);

        // add the vlan provider interface to remote BC group for the elan
        // for internal vlan networks
        if (ElanUtils.isVlan(elanInstance) && !elanInstance.isExternal()) {
            if (interfaceManager.isExternalInterface(interfaceName)) {
                LOG.debug("adding vlan prv intf {} to elan {} BC group", interfaceName, elanInstanceName);
                handleExternalInterfaceEvent(elanInstance, dpnInterfaces, dpId);
            }
        }

        if (isFirstInterfaceInDpn && isVxlanNetworkOrVxlanSegment(elanInstance)) {
            //update the remote-DPNs remoteBC group entry with Tunnels
            LOG.trace("update remote bc group for elan {} on other DPNs for newly added dpn {}", elanInstance, dpId);
            setElanAndEtreeBCGrouponOtherDpns(elanInstance, dpId);
        }

        String jobKey = ElanUtils.getElanInterfaceJobKey(interfaceName);
        InterfaceAddWorkerOnElanInterface addWorker = new InterfaceAddWorkerOnElanInterface(jobKey,
                elanInterface, interfaceInfo, elanInstance, isFirstInterfaceInDpn, this);
        jobCoordinator.enqueueJob(jobKey, addWorker, ElanConstants.JOB_MAX_RETRIES);
        return futures;
    }

    @SuppressWarnings("checkstyle:ForbiddenMethod")
    List<ListenableFuture<Void>> setupEntriesForElanInterface(ElanInstance elanInstance,
            ElanInterface elanInterface, InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn)
            throws ElanException {
        String elanInstanceName = elanInstance.getElanInstanceName();
        String interfaceName = elanInterface.getName();
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        BigInteger dpId = interfaceInfo.getDpId();
        WriteTransaction writeFlowGroupTx = broker.newWriteOnlyTransaction();
        installEntriesForElanInterface(elanInstance, elanInterface, interfaceInfo,
                isFirstInterfaceInDpn, tx, writeFlowGroupTx);

        List<StaticMacEntries> staticMacEntriesList = elanInterface.getStaticMacEntries();
        List<PhysAddress> staticMacAddresses = Lists.newArrayList();

        boolean isInterfaceOperational = isOperational(interfaceInfo);
        if (ElanUtils.isNotEmpty(staticMacEntriesList)) {
            for (StaticMacEntries staticMacEntry : staticMacEntriesList) {
                InstanceIdentifier<MacEntry> macId = getMacEntryOperationalDataPath(elanInstanceName,
                        staticMacEntry.getMacAddress());
                Optional<MacEntry> existingMacEntry = ElanUtils.read(broker,
                        LogicalDatastoreType.OPERATIONAL, macId);
                if (existingMacEntry.isPresent()) {
                    elanForwardingEntriesHandler.updateElanInterfaceForwardingTablesList(
                            elanInstanceName, interfaceName, existingMacEntry.get().getInterface(),
                            existingMacEntry.get(), tx);
                } else {
                    elanForwardingEntriesHandler
                            .addElanInterfaceForwardingTableList(elanInstanceName, interfaceName, staticMacEntry, tx);
                }

                if (isInterfaceOperational) {
                    // Setting SMAC, DMAC, UDMAC in this DPN and also in other
                    // DPNs
                    String macAddress = staticMacEntry.getMacAddress().getValue();
                    LOG.info("programming smac and dmacs for {} on source and other DPNs for elan {} and interface {}",
                            macAddress, elanInstanceName, interfaceName);
                    elanUtils.setupMacFlows(elanInstance, interfaceInfo, ElanConstants.STATIC_MAC_TIMEOUT,
                            staticMacEntry.getMacAddress().getValue(), true, writeFlowGroupTx);
                }
            }

            if (isInterfaceOperational) {
                // Add MAC in TOR's remote MACs via OVSDB. Outside of the loop
                // on purpose.
                for (StaticMacEntries staticMacEntry : staticMacEntriesList) {
                    staticMacAddresses.add(staticMacEntry.getMacAddress());
                }
                elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanInstance.getElanInstanceName(), dpId,
                        staticMacAddresses);
            }
        }
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(ElanUtils.waitForTransactionToComplete(tx));
        futures.add(ElanUtils.waitForTransactionToComplete(writeFlowGroupTx));
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
                elanInstanceCache.get(elanInstanceName).orNull(), interfaceInfo, macEntry);
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
        int dummyInterfaeceCount =  0;
        if (dpnInterfaces.getInterfaces().contains(routerPortUuid)) {
            dummyInterfaeceCount++;
        }
        if (dpnInterfaces.getInterfaces().contains(elanInstanceName)) {
            dummyInterfaeceCount++;
        }
        if (dpnInterfaces.getInterfaces().size() - dummyInterfaeceCount == 0) {
            return true;
        }
        return false;
    }

    private InstanceIdentifier<MacEntry> getMacEntryOperationalDataPath(String elanName, PhysAddress physAddress) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName))
                .child(MacEntry.class, new MacEntryKey(physAddress)).build();
    }

    private void installEntriesForElanInterface(ElanInstance elanInstance, ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn, WriteTransaction tx,
            WriteTransaction writeFlowGroupTx) throws ElanException {
        if (!isOperational(interfaceInfo)) {
            return;
        }
        BigInteger dpId = interfaceInfo.getDpId();
        if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
            elanUtils.setupTermDmacFlows(interfaceInfo, mdsalManager, writeFlowGroupTx);
        }
        setupFilterEqualsTable(elanInstance, interfaceInfo, writeFlowGroupTx);
        if (isFirstInterfaceInDpn) {
            // Terminating Service , UnknownDMAC Table.
            // The 1st ELAN Interface in a DPN must program the INTERNAL_TUNNEL_TABLE, but only if the network type
            // for ELAN Instance is VxLAN
            if (isVxlanNetworkOrVxlanSegment(elanInstance)) {
                setupTerminateServiceTable(elanInstance, dpId, writeFlowGroupTx);
            }
            setupUnknownDMacTable(elanInstance, dpId, writeFlowGroupTx);
            /*
             * Install remote DMAC flow. This is required since this DPN is
             * added later to the elan instance and remote DMACs of other
             * interfaces in this elan instance are not present in the current
             * dpn.
             */
            if (!interfaceManager.isExternalInterface(interfaceInfo.getInterfaceName())) {
                LOG.info("Programming remote dmac flows on the newly connected dpn {} for elan {} ", dpId,
                        elanInstance.getElanInstanceName());
                programRemoteDmacFlow(elanInstance, interfaceInfo, writeFlowGroupTx);
            }
        }
        // bind the Elan service to the Interface
        bindService(elanInstance, elanInterface, interfaceInfo.getInterfaceTag(), tx);
    }

    public void installEntriesForFirstInterfaceonDpn(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            DpnInterfaces dpnInterfaces, boolean isFirstInterfaceInDpn) {
        if (!isOperational(interfaceInfo)) {
            return;
        }
        // LocalBroadcast Group creation with elan-Interfaces
        setupLocalBroadcastGroups(elanInfo, dpnInterfaces, interfaceInfo);
        if (isFirstInterfaceInDpn) {
            LOG.trace("waitTimeForSyncInstall is {}", WAIT_TIME_FOR_SYNC_INSTALL);
            BigInteger dpId = interfaceInfo.getDpId();
            // RemoteBroadcast Group creation
            try {
                Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
            } catch (InterruptedException e1) {
                LOG.warn("Error while waiting for local BC group for ELAN {} to install", elanInfo);
            }
            elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, dpnInterfaces, dpId);
            try {
                Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
            } catch (InterruptedException e1) {
                LOG.warn("Error while waiting for local BC group for ELAN {} to install", elanInfo);
            }
        }
    }

    public void setupFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction writeFlowGroupTx) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "group"), 9, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                ElanUtils.getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                elanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.addFlowToTx(interfaceInfo.getDpId(), flow, writeFlowGroupTx);

        Flow flowEntry = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "drop"), 10, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getMatchesForFilterEqualsLPortTag(ifTag), MDSALUtil.buildInstructionsDrop());

        mdsalManager.addFlowToTx(interfaceInfo.getDpId(), flowEntry, writeFlowGroupTx);
    }

    public void removeFilterEqualsTable(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        int ifTag = interfaceInfo.getInterfaceTag();
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "group"), 9, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                ElanUtils.getTunnelIdMatchForFilterEqualsLPortTag(ifTag),
                elanUtils.getInstructionsInPortForOutGroup(interfaceInfo.getInterfaceName()));

        mdsalManager.removeFlowToTx(interfaceInfo.getDpId(), flow, deleteFlowGroupTx);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.ELAN_FILTER_EQUALS_TABLE,
                getFlowRef(NwConstants.ELAN_FILTER_EQUALS_TABLE, ifTag, "drop"), 10, elanInfo.getElanInstanceName(), 0,
                0, ElanConstants.COOKIE_ELAN_FILTER_EQUALS.add(BigInteger.valueOf(ifTag)),
                getMatchesForFilterEqualsLPortTag(ifTag), MDSALUtil.buildInstructionsDrop());

        mdsalManager.removeFlowToTx(interfaceInfo.getDpId(), flowEntity, deleteFlowGroupTx);
    }

    private List<Bucket> getRemoteBCGroupBucketInfos(ElanInstance elanInfo, int bucketKeyStart,
                                                     InterfaceInfo interfaceInfo, long elanTag) {
        return elanL2GatewayMulticastUtils.getRemoteBCGroupBuckets(elanInfo, null, interfaceInfo.getDpId(),
                bucketKeyStart, elanTag);
    }

    private void setElanAndEtreeBCGrouponOtherDpns(ElanInstance elanInfo, BigInteger dpId) {
        int elanTag = elanInfo.getElanTag().intValue();
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        setBCGrouponOtherDpns(elanInfo, dpId, elanTag, groupId);
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            int etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue().intValue();
            long etreeLeafGroupId = ElanUtils.getEtreeLeafRemoteBCGId(etreeLeafTag);
            setBCGrouponOtherDpns(elanInfo, dpId, etreeLeafTag, etreeLeafGroupId);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void setBCGrouponOtherDpns(ElanInstance elanInfo, BigInteger dpId, int elanTag, long groupId) {
        int bucketId = 0;
        ElanDpnInterfacesList elanDpns = elanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());
        if (elanDpns != null) {
            List<DpnInterfaces> dpnInterfaces = elanDpns.getDpnInterfaces();
            for (DpnInterfaces dpnInterface : dpnInterfaces) {
                List<Bucket> remoteListBucketInfo = new ArrayList<>();
                if (elanUtils.isDpnPresent(dpnInterface.getDpId()) && !Objects.equals(dpnInterface.getDpId(), dpId)
                        && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    List<Action> listAction = new ArrayList<>();
                    int actionKey = 0;
                    listAction.add(new ActionGroup(ElanUtils.getElanLocalBCGId(elanTag)).buildAction(++actionKey));
                    remoteListBucketInfo.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                    bucketId++;
                    for (DpnInterfaces otherFes : dpnInterfaces) {
                        if (elanUtils.isDpnPresent(otherFes.getDpId()) && !Objects.equals(otherFes.getDpId(),
                            dpnInterface.getDpId()) && otherFes.getInterfaces() != null
                            && !otherFes.getInterfaces().isEmpty()) {
                            try {
                                List<Action> remoteListActionInfo = elanItmUtils.getInternalTunnelItmEgressAction(
                                        dpnInterface.getDpId(), otherFes.getDpId(),
                                        elanUtils.isOpenstackVniSemanticsEnforced()
                                                ? elanUtils.getVxlanSegmentationId(elanInfo) : elanTag);
                                if (!remoteListActionInfo.isEmpty()) {
                                    remoteListBucketInfo.add(MDSALUtil.buildBucket(remoteListActionInfo, MDSALUtil
                                            .GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                                    bucketId++;
                                }
                            } catch (Exception ex) {
                                LOG.error("setElanBCGrouponOtherDpns failed due to Exception caught; "
                                        + "Logical Group Interface not found between source Dpn - {}, "
                                        + "destination Dpn - {} ", dpnInterface.getDpId(), otherFes.getDpId(), ex);
                                return;
                            }
                        }
                    }
                    List<Bucket> elanL2GwDevicesBuckets = elanL2GatewayMulticastUtils
                            .getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnInterface.getDpId(), bucketId);
                    remoteListBucketInfo.addAll(elanL2GwDevicesBuckets);

                    if (remoteListBucketInfo.isEmpty()) {
                        LOG.debug("No ITM is present on Dpn - {} ", dpnInterface.getDpId());
                        continue;
                    }
                    Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                            MDSALUtil.buildBucketLists(remoteListBucketInfo));
                    LOG.trace("Installing remote bc group {} on dpnId {}", group, dpnInterface.getDpId());
                    mdsalManager.syncInstallGroup(dpnInterface.getDpId(), group);
                }
            }
            try {
                Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
            } catch (InterruptedException e1) {
                LOG.warn("Error while waiting for remote BC group on other DPNs for ELAN {} to install", elanInfo);
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

    private List<MatchInfo> buildMatchesForVni(Long vni) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        MatchInfo match = new MatchTunnelId(BigInteger.valueOf(vni));
        mkMatches.add(match);
        return mkMatches;
    }

    private List<InstructionInfo> getInstructionsForOutGroup(long groupId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionWriteActions(Collections.singletonList(new ActionGroup(groupId))));
        return mkInstructions;
    }

    private List<MatchInfo> getMatchesForElanTag(long elanTag, boolean isSHFlagSet) {
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
    private List<InstructionInfo> getInstructionsIntOrExtTunnelTable(Long elanTag) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionWriteMetadata(ElanHelper.getElanMetadataLabel(elanTag), ElanHelper
                .getElanMetadataMask()));
        /* applicable for EXTERNAL_TUNNEL_TABLE only
        * TODO: We should point to SMAC or DMAC depending on a configuration property to enable mac learning
        */
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        return mkInstructions;
    }

    // Install DMAC entry on dst DPN
    @SuppressWarnings("checkstyle:ForbiddenMethod")
    public List<ListenableFuture<Void>> installDMacAddressTables(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            BigInteger dstDpId) throws ElanException {
        String interfaceName = interfaceInfo.getInterfaceName();
        ElanInterfaceMac elanInterfaceMac = elanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
        if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
            List<MacEntry> macEntries = elanInterfaceMac.getMacEntry();
            WriteTransaction writeFlowTx = broker.newWriteOnlyTransaction();
            for (MacEntry macEntry : macEntries) {
                String macAddress = macEntry.getMacAddress().getValue();
                LOG.info("Installing remote dmac for mac address {} and interface {}", macAddress, interfaceName);
                synchronized (ElanUtils.getElanMacDPNKey(elanInfo.getElanTag(), macAddress,
                        interfaceInfo.getDpId())) {
                    LOG.info("Acquired lock for mac : {}, proceeding with remote dmac install operation", macAddress);
                    elanUtils.setupDMacFlowOnRemoteDpn(elanInfo, interfaceInfo, dstDpId, macAddress,
                            writeFlowTx);
                }
            }
            return Collections.singletonList(ElanUtils.waitForTransactionToComplete(writeFlowTx));
        }
        return Collections.emptyList();
    }

    private void createDropBucket(List<Bucket> listBucket) {
        List<Action> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionDrop().buildAction());
        Bucket dropBucket = MDSALUtil.buildBucket(actionsInfos, MDSALUtil.GROUP_WEIGHT, 0, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP);
        listBucket.add(dropBucket);
    }

    public void setupLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo) {
        setupStandardLocalBroadcastGroups(elanInfo, newDpnInterface, interfaceInfo);
        setupLeavesLocalBroadcastGroups(elanInfo, newDpnInterface, interfaceInfo);
    }

    public void setupStandardLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        long groupId = ElanUtils.getElanLocalBCGId(elanInfo.getElanTag());

        List<String> interfaces = new ArrayList<>();
        if (newDpnInterface != null) {
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
        mdsalManager.syncInstallGroup(interfaceInfo.getDpId(), group);
    }

    private void setupLeavesLocalBroadcastGroups(ElanInstance elanInfo, DpnInterfaces newDpnInterface,
            InterfaceInfo interfaceInfo) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            List<Bucket> listBucket = new ArrayList<>();
            int bucketId = 0;

            List<String> interfaces = new ArrayList<>();
            if (newDpnInterface != null) {
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

            long etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue();
            long groupId = ElanUtils.getEtreeLeafLocalBCGId(etreeLeafTag);
            Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                    MDSALUtil.buildBucketLists(listBucket));
            LOG.trace("installing the localBroadCast Group:{}", group);
            mdsalManager.syncInstallGroup(interfaceInfo.getDpId(), group);
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
            WriteTransaction deleteFlowGroupTx) {
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanLocalBCGId(elanInfo.getElanTag());
        List<Bucket> listBuckets = new ArrayList<>();
        int bucketId = 0;
        listBuckets.add(getLocalBCGroupBucketInfo(interfaceInfo, bucketId));
        // listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, 1,
        // interfaceInfo));
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBuckets));
        LOG.trace("deleted the localBroadCast Group:{}", group);
        mdsalManager.removeGroupToTx(dpnId, group, deleteFlowGroupTx);
    }

    public void removeElanBroadcastGroup(ElanInstance elanInfo, InterfaceInfo interfaceInfo,
            WriteTransaction deleteFlowGroupTx) {
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
        List<Bucket> listBuckets = new ArrayList<>();
        List<Action> listAction = new ArrayList<>();
        listAction.add(new ActionGroup(++actionKey, ElanUtils.getElanLocalBCGId(elanTag)).buildAction());
        listBuckets.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        listBuckets.addAll(getRemoteBCGroupBucketInfos(elanInfo, bucketId, interfaceInfo, elanTag));
        BigInteger dpnId = interfaceInfo.getDpId();
        long groupId = ElanUtils.getElanRemoteBCGId(elanInfo.getElanTag());
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBuckets));
        LOG.trace("deleting the remoteBroadCast group:{}", group);
        mdsalManager.removeGroupToTx(dpnId, group, deleteFlowGroupTx);
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
                buildMatchesForVni(ElanUtils.getVxlanSegmentationId(elanInfo)),
                getInstructionsIntOrExtTunnelTable(elanTag));

        mdsalManager.installFlow(flowEntity);
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
        FlowEntity flowEntity = new FlowEntityBuilder()
            .setDpnId(dpnId)
            .setTableId(NwConstants.EXTERNAL_TUNNEL_TABLE)
            .setFlowId(flowId)
            .build();
        mdsalManager.removeFlow(flowEntity);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, BigInteger dpId, WriteTransaction writeFlowGroupTx) {
        setupTerminateServiceTable(elanInfo, dpId, elanInfo.getElanTag(), writeFlowGroupTx);
        setupEtreeTerminateServiceTable(elanInfo, dpId, writeFlowGroupTx);
    }

    public void setupTerminateServiceTable(ElanInstance elanInfo, BigInteger dpId, long elanTag,
            WriteTransaction writeFlowGroupTx) {
        List<? extends MatchInfoBase> listMatchInfoBase;
        List<InstructionInfo> instructionInfos;
        long serviceId;
        if (!elanUtils.isOpenstackVniSemanticsEnforced()) {
            serviceId = elanTag;
            listMatchInfoBase = ElanUtils.getTunnelMatchesForServiceId((int) elanTag);
            instructionInfos = getInstructionsForOutGroup(ElanUtils.getElanLocalBCGId(elanTag));
        } else {
            serviceId = elanUtils.getVxlanSegmentationId(elanInfo);
            listMatchInfoBase = buildMatchesForVni(serviceId);
            instructionInfos = getInstructionsIntOrExtTunnelTable(elanTag);
        }
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, serviceId), 5, String.format("%s:%d", "ITM Flow Entry ",
                elanTag), 0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(elanTag)), listMatchInfoBase,
                instructionInfos);
        mdsalManager.addFlowToTx(flowEntity, writeFlowGroupTx);
    }

    private void setupEtreeTerminateServiceTable(ElanInstance elanInfo, BigInteger dpId,
            WriteTransaction writeFlowGroupTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            setupTerminateServiceTable(elanInfo, dpId, etreeInstance.getEtreeLeafTagVal().getValue(), writeFlowGroupTx);
        }
    }

    public void setupUnknownDMacTable(ElanInstance elanInfo, BigInteger dpId, WriteTransaction writeFlowGroupTx) {
        long elanTag = elanInfo.getElanTag();
        installLocalUnknownFlow(elanInfo, dpId, elanTag, writeFlowGroupTx);
        installRemoteUnknownFlow(elanInfo, dpId, elanTag, writeFlowGroupTx);
        setupEtreeUnknownDMacTable(elanInfo, dpId, elanTag, writeFlowGroupTx);
    }

    private void setupEtreeUnknownDMacTable(ElanInstance elanInfo, BigInteger dpId, long elanTag,
            WriteTransaction writeFlowGroupTx) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            long leafTag = etreeLeafTag.getEtreeLeafTag().getValue();
            installRemoteUnknownFlow(elanInfo, dpId, leafTag, writeFlowGroupTx);
            installLocalUnknownFlow(elanInfo, dpId, leafTag, writeFlowGroupTx);
        }
    }

    private void installLocalUnknownFlow(ElanInstance elanInfo, BigInteger dpId, long elanTag,
            WriteTransaction writeFlowGroupTx) {
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,/* SH flag */false),
                5, elanInfo.getElanInstanceName(), 0, 0,
                ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                getMatchesForElanTag(elanTag, /* SH flag */false),
                getInstructionsForOutGroup(ElanUtils.getElanRemoteBCGId(elanTag)));

        mdsalManager.addFlowToTx(flowEntity, writeFlowGroupTx);
    }

    private void installRemoteUnknownFlow(ElanInstance elanInfo, BigInteger dpId, long elanTag,
            WriteTransaction writeFlowGroupTx) {
        // only if ELAN can connect to external network, perform the following

        if (isVxlanNetworkOrVxlanSegment(elanInfo) || ElanUtils.isVlan(elanInfo) || ElanUtils.isFlat(elanInfo)) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE, elanTag,/* SH flag */true),
                    5, elanInfo.getElanInstanceName(), 0, 0,
                    ElanConstants.COOKIE_ELAN_UNKNOWN_DMAC.add(BigInteger.valueOf(elanTag)),
                    getMatchesForElanTag(elanTag, /* SH flag */true),
                    getInstructionsForOutGroup(ElanUtils.getElanLocalBCGId(elanTag)));
            mdsalManager.addFlowToTx(flowEntity, writeFlowGroupTx);
        }
    }


    private void removeUnknownDmacFlow(BigInteger dpId, ElanInstance elanInfo, WriteTransaction deleteFlowGroupTx,
            long elanTag) {
        Flow flow = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                elanTag, SH_FLAG_UNSET))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE).build();
        mdsalManager.removeFlowToTx(dpId, flow, deleteFlowGroupTx);

        if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
            Flow flow2 = new FlowBuilder().setId(new FlowId(getUnknownDmacFlowRef(NwConstants.ELAN_UNKNOWN_DMAC_TABLE,
                    elanTag, SH_FLAG_SET))).setTableId(NwConstants.ELAN_UNKNOWN_DMAC_TABLE)
                    .build();
            mdsalManager.removeFlowToTx(dpId, flow2, deleteFlowGroupTx);
        }
    }

    private void removeDefaultTermFlow(BigInteger dpId, long elanTag) {
        elanUtils.removeTerminatingServiceAction(dpId, (int) elanTag);
    }

    private void bindService(ElanInstance elanInfo, ElanInterface elanInterface, int lportTag, WriteTransaction tx) {
        if (isStandardElanService(elanInterface)) {
            bindElanService(elanInfo.getElanTag(), elanInfo.getElanInstanceName(),
                    elanInterface.getName(), lportTag, tx);
        } else { // Etree service
            bindEtreeService(elanInfo, elanInterface, lportTag, tx);
        }
    }

    private void bindElanService(long elanTag, String elanInstanceName, String interfaceName, int lportTag,
            WriteTransaction tx) {
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
            tx.put(LogicalDatastoreType.CONFIGURATION, bindServiceId, serviceInfo,
                    WriteTransaction.CREATE_MISSING_PARENTS);
        }
    }

    private void bindEtreeService(ElanInstance elanInfo, ElanInterface elanInterface, int lportTag,
            WriteTransaction tx) {
        if (elanInterface.augmentation(EtreeInterface.class).getEtreeInterfaceType() == EtreeInterfaceType.Root) {
            bindElanService(elanInfo.getElanTag(), elanInfo.getElanInstanceName(), elanInterface.getName(),
                    lportTag, tx);
        } else {
            EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
            if (etreeInstance == null) {
                LOG.error("EtreeInterface {} is associated with a non EtreeInstance: {}",
                        elanInterface.getName(), elanInfo.getElanInstanceName());
            } else {
                bindElanService(etreeInstance.getEtreeLeafTagVal().getValue(), elanInfo.getElanInstanceName(),
                        elanInterface.getName(), lportTag, tx);
            }
        }
    }

    private boolean isStandardElanService(ElanInterface elanInterface) {
        return elanInterface.augmentation(EtreeInterface.class) == null;
    }

    protected void unbindService(String interfaceName, ReadWriteTransaction tx) throws ReadFailedException {
        short elanServiceIndex = ServiceIndex.getIndex(NwConstants.ELAN_SERVICE_NAME, NwConstants.ELAN_SERVICE_INDEX);
        InstanceIdentifier<BoundServices> bindServiceId = ElanUtils.buildServiceId(interfaceName, elanServiceIndex);
        if (tx.read(LogicalDatastoreType.CONFIGURATION, bindServiceId).checkedGet().isPresent()) {
            tx.delete(LogicalDatastoreType.CONFIGURATION, bindServiceId);
        }
    }

    private String getFlowRef(long tableId, long elanTag) {
        return String.valueOf(tableId) + elanTag;
    }

    private String getFlowRef(long tableId, long elanTag, String flowName) {
        return new StringBuffer().append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(elanTag)
                .append(NwConstants.FLOWID_SEPARATOR).append(flowName).toString();
    }

    private String getUnknownDmacFlowRef(long tableId, long elanTag, boolean shFlag) {
        return String.valueOf(tableId) + elanTag + shFlag;
    }

    private List<Action> getInterfacePortActions(InterfaceInfo interfaceInfo) {
        List<Action> listAction = new ArrayList<>();
        int actionKey = 0;
        listAction.add(
            new ActionSetFieldTunnelId(BigInteger.valueOf(interfaceInfo.getInterfaceTag())).buildAction(actionKey));
        actionKey++;
        listAction.add(new ActionNxResubmit(NwConstants.ELAN_FILTER_EQUALS_TABLE).buildAction(actionKey));
        return listAction;
    }

    private DpnInterfaces updateElanDpnInterfacesList(String elanInstanceName, BigInteger dpId,
            List<String> interfaceNames, WriteTransaction tx) {
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .withKey(new DpnInterfacesKey(dpId)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId), dpnInterface,
                WriteTransaction.CREATE_MISSING_PARENTS);
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
        InstanceIdentifier<DpnInterfaces> dpnInterfacesId = ElanUtils
                .getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
        Optional<DpnInterfaces> dpnInterfaces = ElanUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, dpnInterfacesId);
        if (dpnInterfaces.isPresent()) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, dpnInterfacesId);
        }
    }

    private DpnInterfaces createElanInterfacesList(String elanInstanceName, String interfaceName, BigInteger dpId,
            WriteTransaction tx) {
        List<String> interfaceNames = new ArrayList<>();
        interfaceNames.add(interfaceName);
        DpnInterfaces dpnInterface = new DpnInterfacesBuilder().setDpId(dpId).setInterfaces(interfaceNames)
                .withKey(new DpnInterfacesKey(dpId)).build();
        tx.put(LogicalDatastoreType.OPERATIONAL,
                ElanUtils.getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId), dpnInterface,
                WriteTransaction.CREATE_MISSING_PARENTS);
        return dpnInterface;
    }

    private void createElanInterfaceTablesList(String interfaceName, WriteTransaction tx) {
        InstanceIdentifier<ElanInterfaceMac> elanInterfaceMacTables = ElanUtils
                .getElanInterfaceMacEntriesOperationalDataPath(interfaceName);
        Optional<ElanInterfaceMac> interfaceMacTables = ElanUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, elanInterfaceMacTables);
        // Adding new Elan Interface Port to the operational DataStore without
        // Static-Mac Entries..
        if (!interfaceMacTables.isPresent()) {
            ElanInterfaceMac elanInterfaceMacTable = new ElanInterfaceMacBuilder().setElanInterface(interfaceName)
                    .withKey(new ElanInterfaceMacKey(interfaceName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL,
                    ElanUtils.getElanInterfaceMacEntriesOperationalDataPath(interfaceName), elanInterfaceMacTable,
                    WriteTransaction.CREATE_MISSING_PARENTS);
        }
    }

    private void createElanStateList(String elanInstanceName, String interfaceName, WriteTransaction tx) {
        InstanceIdentifier<Elan> elanInstance = ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName);
        Optional<Elan> elanInterfaceLists = ElanUtils.read(broker,
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
                    .withKey(new ElanKey(elanInstanceName)).build();
            tx.put(LogicalDatastoreType.OPERATIONAL, ElanUtils.getElanInstanceOperationalDataPath(elanInstanceName),
                    elanState, WriteTransaction.CREATE_MISSING_PARENTS);
        }
    }

    private boolean isOperational(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            return false;
        }
        return interfaceInfo.getAdminState() == InterfaceInfo.InterfaceAdminState.ENABLED;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleInternalTunnelStateEvent(BigInteger srcDpId, BigInteger dstDpId) {
        ElanDpnInterfaces dpnInterfaceLists = elanUtils.getElanDpnInterfacesList();
        LOG.trace("processing tunnel state event for srcDpId {} dstDpId {}"
                + " and dpnInterfaceList {}", srcDpId, dstDpId, dpnInterfaceLists);
        if (dpnInterfaceLists == null) {
            return;
        }
        List<ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists.getElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf) {
            int cnt = 0;
            String elanName = elanDpns.getElanInstanceName();
            ElanInstance elanInfo = elanInstanceCache.get(elanName).orNull();
            if (elanInfo == null) {
                LOG.warn("ELAN Info is null for elanName {} that does exist in elanDpnInterfaceList, "
                        + "skipping this ELAN for tunnel handling", elanName);
                continue;
            }
            if (!isVxlanNetworkOrVxlanSegment(elanInfo)) {
                LOG.debug("Ignoring internal tunnel state event for Flat/Vlan elan {}", elanName);
                continue;
            }
            List<DpnInterfaces> dpnInterfaces = elanDpns.getDpnInterfaces();
            if (dpnInterfaces == null) {
                continue;
            }
            DpnInterfaces dstDpnIf = null;
            for (DpnInterfaces dpnIf : dpnInterfaces) {
                BigInteger dpnIfDpId = dpnIf.getDpId();
                if (dpnIfDpId.equals(srcDpId)) {
                    cnt++;
                } else if (dpnIfDpId.equals(dstDpId)) {
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
                        elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, srcDpId);
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
                            return Collections.emptyList();
                        }, ElanConstants.JOB_MAX_RETRIES);
                    }
                    return Collections.emptyList();
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
     * @throws ElanException in case of issues creating the flow objects
     */
    public void handleExternalTunnelStateEvent(ExternalTunnel externalTunnel, Interface intrf) throws ElanException {
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
            LOG.error("Dp ID / externalNodeId not found in external tunnel {}", externalTunnel);
            return;
        }

        ElanDpnInterfaces dpnInterfaceLists = elanUtils.getElanDpnInterfacesList();
        if (dpnInterfaceLists == null) {
            return;
        }
        List<ElanDpnInterfacesList> elanDpnIf = dpnInterfaceLists.getElanDpnInterfacesList();
        for (ElanDpnInterfacesList elanDpns : elanDpnIf) {
            String elanName = elanDpns.getElanInstanceName();
            ElanInstance elanInfo = elanInstanceCache.get(elanName).orNull();

            DpnInterfaces dpnInterfaces = elanUtils.getElanInterfaceInfoByElanDpn(elanName, dpId);
            if (elanInfo == null || dpnInterfaces == null || dpnInterfaces.getInterfaces() == null
                    || dpnInterfaces.getInterfaces().isEmpty()) {
                continue;
            }
            LOG.debug("Elan instance:{} is present in Dpn:{} ", elanName, dpId);

            elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInfo, dpId);
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
    private void installDpnMacsInL2gwDevice(String elanName, Set<String> lstElanInterfaceNames, BigInteger dpnId,
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
                phyLocAlreadyExists, String.valueOf(dpnTepIp.getValue()), elanName, externalNodeId.getValue());
        List<PhysAddress> staticMacs = elanL2GatewayUtils.getElanDpnMacsFromInterfaces(lstElanInterfaceNames);

        if (phyLocAlreadyExists) {
            elanL2GatewayUtils.scheduleAddDpnMacsInExtDevice(elanName, dpnId, staticMacs, elanL2GwDevice);
            return;
        }
        elanL2GatewayMulticastUtils.scheduleMcastMacUpdateJob(elanName, elanL2GwDevice);
        elanL2GatewayUtils.scheduleAddDpnMacsInExtDevice(elanName, dpnId, staticMacs, elanL2GwDevice);
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

    private List<MatchInfo> getMatchesForFilterEqualsLPortTag(int lportTag) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(
                new MatchMetadata(MetaDataUtil.getLportTagMetaData(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG));
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(lportTag)));
        return mkMatches;
    }

    @Override
    protected ElanInterfaceManager getDataTreeChangeListener() {
        return this;
    }

    public void handleExternalInterfaceEvent(ElanInstance elanInstance, DpnInterfaces dpnInterfaces,
                                             BigInteger dpId) {
        LOG.debug("setting up remote BC group for elan {}", elanInstance.getPhysicalNetworkName());
        elanL2GatewayMulticastUtils.setupStandardElanBroadcastGroups(elanInstance, dpnInterfaces, dpId);
        try {
            Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
        } catch (InterruptedException e) {
            LOG.warn("Error while waiting for local BC group for ELAN {} to install", elanInstance);
        }
    }
}
