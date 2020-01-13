/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static java.util.Collections.emptyList;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlanNetworkOrVxlanSegment;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.elan.l2gw.jobs.HwvtepDeviceMcastMacUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.jobs.McastUpdateJob;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The utility class to handle ELAN L2 Gateway related to multicast.
 */
@Singleton
public class ElanL2GatewayMulticastUtils {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ElanL2GatewayMulticastUtils.class);

    /** The broker. */
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;

    private final ElanItmUtils elanItmUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanUtils elanUtils;
    private final IMdsalApiManager mdsalManager;
    private final IInterfaceManager interfaceManager;
    private final ElanRefUtil elanRefUtil;
    private final ElanClusterUtils elanClusterUtils;
    private final Scheduler scheduler;


    @Inject
    public ElanL2GatewayMulticastUtils(ElanItmUtils elanItmUtils, ElanUtils elanUtils, IMdsalApiManager mdsalManager,
                                       IInterfaceManager interfaceManager, ElanRefUtil elanRefUtil) {
        this.elanRefUtil = elanRefUtil;
        this.broker = elanRefUtil.getDataBroker();
        this.txRunner = new ManagedNewTransactionRunnerImpl(elanRefUtil.getDataBroker());
        this.elanItmUtils = elanItmUtils;
        this.jobCoordinator = elanRefUtil.getJobCoordinator();
        this.elanUtils = elanUtils;
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.elanClusterUtils = elanRefUtil.getElanClusterUtils();
        this.scheduler = elanRefUtil.getScheduler();
    }

    /**
     * Handle mcast for elan l2 gw device add.
     * @param elanName the elan name
     * @param device the device
     */
    public void handleMcastForElanL2GwDeviceAdd(String elanName, L2GatewayDevice device) {
        InstanceIdentifier<ExternalTeps> tepPath = buildExternalTepPath(elanName, device.getTunnelIp());
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> tx.put(tepPath, buildExternalTeps(device))), LOG, "Failed to write to config external tep {}",
            tepPath);
        updateMcastMacsForAllElanDevices(elanName, device, true/* updateThisDevice */);
    }

    public static InstanceIdentifier<ExternalTeps> buildExternalTepPath(String elan, IpAddress tepIp) {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, new ElanInstanceKey(elan))
                .child(ExternalTeps.class, new ExternalTepsKey(tepIp)).build();
    }

    protected ExternalTeps buildExternalTeps(L2GatewayDevice device) {
        return new ExternalTepsBuilder().setTepIp(device.getTunnelIp()).setNodeid(device.getHwvtepNodeId()).build();
    }

    /**
     * Updates the remote mcast mac table for all the devices in this elan
     * includes all the dpn tep ips and other devices tep ips in broadcast
     * locator set.
     *
     * @param elanName
     *            the elan to be updated
     */
    public void updateRemoteMcastMacOnElanL2GwDevices(String elanName) {
        for (L2GatewayDevice device : ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName)) {
            prepareRemoteMcastMacUpdateOnDevice(elanName, device, false, null);
        }
    }

    public void scheduleMcastMacUpdateJob(String elanName, L2GatewayDevice device) {
        HwvtepDeviceMcastMacUpdateJob job = new HwvtepDeviceMcastMacUpdateJob(this, elanName,device);
        jobCoordinator.enqueueJob(job.getJobKey(), job);
    }

    /**
     * Update remote mcast mac on elan l2 gw device.
     *
     * @param elanName
     *            the elan name
     * @param device
     *            the device
     */
    public void updateRemoteMcastMacOnElanL2GwDevice(String elanName, L2GatewayDevice device) {
        prepareRemoteMcastMacUpdateOnDevice(elanName, device, false, null);
    }

    public ListenableFuture<Void> prepareRemoteMcastMacUpdateOnDevice(String elanName, L2GatewayDevice device,
                                                                      boolean addCase, IpAddress removedDstTep) {
        NodeId dstNodeId = new NodeId(device.getHwvtepNodeId());
        RemoteMcastMacs existingMac = null;
        try {
            Optional<RemoteMcastMacs> mac  = elanRefUtil.getConfigMcastCache().get(getRemoteMcastIid(dstNodeId,
                    elanName));
            if (mac.isPresent()) {
                existingMac = mac.get();
            }
        } catch (ReadFailedException e) {
            LOG.error("Failed to read iid for elan {}", elanName, e);
        }

        if (!addCase && removedDstTep != null) {
            LOG.debug(" RemoteMcast update delete tep {} of elan {} ", removedDstTep.getIpv4Address().getValue(),
                    elanName);
            //incase of dpn flap immediately after cluster reboot just remove its tep alone
            if (existingMac != null) {
                return deleteLocatorFromMcast(elanName, dstNodeId, removedDstTep, existingMac);
            }
        }
        Collection<L2GatewayDevice> elanL2gwDevices = ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName);
        Collection<DpnInterfaces> dpns = elanRefUtil.getElanInstanceDpnsCache().get(elanName);
        List<IpAddress> dpnsTepIps = getAllTepIpsOfDpns(device, dpns);
        List<IpAddress> l2GwDevicesTepIps = getAllTepIpsOfL2GwDevices(elanL2gwDevices);
        return prepareRemoteMcastMacEntry(elanName, device, dpnsTepIps, l2GwDevicesTepIps, addCase);
    }

    private ListenableFuture<Void> deleteLocatorFromMcast(String elanName, NodeId dstNodeId,
                                                          IpAddress removedDstTep,
                                                          RemoteMcastMacs existingMac) {

        LocatorSet tobeDeleted = buildLocatorSet(dstNodeId, removedDstTep);
        RemoteMcastMacsBuilder newMacBuilder = new RemoteMcastMacsBuilder(existingMac);

        List<LocatorSet> locatorList = new ArrayList<>(existingMac.nonnullLocatorSet());
        locatorList.remove(tobeDeleted);
        newMacBuilder.setLocatorSet(locatorList);
        RemoteMcastMacs mac = newMacBuilder.build();
        //configMcastCache.add(macIid, mac);
        InstanceIdentifier<RemoteMcastMacs> macIid = HwvtepSouthboundUtils
                .createRemoteMcastMacsInstanceIdentifier(dstNodeId, existingMac.key());
        return ResourceBatchingManager.getInstance().put(
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, macIid, mac);
    }

    LocatorSet buildLocatorSet(NodeId nodeId, IpAddress tepIp) {
        HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                .createHwvtepPhysicalLocatorAugmentation(tepIp.stringValue());
        HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug));
        return new LocatorSetBuilder().setLocatorRef(phyLocRef).build();
    }

    /**
     * Update mcast macs for this elan.
     * for all dpns in this elan  recompute and update broadcast group
     * for all l2gw devices in this elan recompute and update remote mcast mac entry
     *
     * @param elanName
     *            the elan name
     * @param device
     *            the device
     * @param updateThisDevice
     *            the update this device
     */
    public void updateMcastMacsForAllElanDevices(String elanName, L2GatewayDevice device,
                                                                    boolean updateThisDevice) {
        if (updateThisDevice) {
            McastUpdateJob.updateAllMcastsForConnectionAdd(elanName, this, elanClusterUtils);
        } else {
            McastUpdateJob.updateAllMcastsForConnectionDelete(elanName, this, elanClusterUtils, device);
        }
    }

    public void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo, boolean createCase,
            TypedWriteTransaction<Datastore.Configuration> confTx) {
        List<DpnInterfaces> dpns = elanUtils.getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
        for (DpnInterfaces dpn : dpns) {
            setupStandardElanBroadcastGroups(elanInfo, null, dpn.getDpId(), createCase, confTx);
        }
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, Uint64 dpnId,
            TypedWriteTransaction<Datastore.Configuration> confTx) {
        setupElanBroadcastGroups(elanInfo, null, dpnId, confTx);
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces, Uint64 dpnId,
                                         TypedWriteTransaction<Datastore.Configuration> confTx) {
        setupStandardElanBroadcastGroups(elanInfo, dpnInterfaces, dpnId, confTx);
        setupLeavesEtreeBroadcastGroups(elanInfo, dpnInterfaces, dpnId, confTx);
    }

    public void setupStandardElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, Uint64 dpnId,
                                                 TypedWriteTransaction<Datastore.Configuration> confTx) {
        setupStandardElanBroadcastGroups(elanInfo, dpnInterfaces, dpnId, true, confTx);
    }

    public void setupStandardElanBroadcastGroups(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces,
            Uint64 dpnId, boolean createCase, TypedWriteTransaction<Datastore.Configuration> confTx) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag().toJava();
        List<Action> listAction = new ArrayList<>();
        listAction.add(new ActionGroup(ElanUtils.getElanLocalBCGId(elanTag)).buildAction(++actionKey));
        listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnInterfaces, dpnId, bucketId, elanTag);
        listBucket.addAll(listBucketInfoRemote);
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        LOG.trace("Installing the remote BroadCast Group:{}", group);
        if (createCase) {
            elanUtils.syncUpdateGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND, confTx);
        } else {
            mdsalManager.addGroup(confTx, dpnId, group);
        }
    }

    public void setupLeavesEtreeBroadcastGroups(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces,
            Uint64 dpnId, TypedWriteTransaction<Datastore.Configuration> confTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            long etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue().toJava();
            List<Bucket> listBucket = new ArrayList<>();
            int bucketId = 0;
            int actionKey = 0;
            List<Action> listAction = new ArrayList<>();
            listAction.add(new ActionGroup(ElanUtils.getEtreeLeafLocalBCGId(etreeLeafTag)).buildAction(++actionKey));
            listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                    MDSALUtil.WATCH_GROUP));
            bucketId++;
            List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnInterfaces, dpnId, bucketId,
                    etreeLeafTag);
            listBucket.addAll(listBucketInfoRemote);
            long groupId = ElanUtils.getEtreeLeafRemoteBCGId(etreeLeafTag);
            Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                    MDSALUtil.buildBucketLists(listBucket));
            LOG.trace("Installing the remote BroadCast Group:{}", group);
            mdsalManager.addGroup(confTx, dpnId, group);
        }
    }

    @Nullable
    private static DpnInterfaces getDpnInterfaces(ElanDpnInterfacesList elanDpns, Uint64 dpnId) {
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.nonnullDpnInterfaces()) {
                if (Objects.equals(dpnInterface.getDpId(), dpnId)) {
                    return dpnInterface;
                }
            }
        }
        return null;
    }

    private List<Bucket> getRemoteBCGroupExternalPortBuckets(ElanDpnInterfacesList elanDpns,
            DpnInterfaces dpnInterfaces, Uint64 dpnId, int bucketId) {
        DpnInterfaces currDpnInterfaces = dpnInterfaces != null ? dpnInterfaces : getDpnInterfaces(elanDpns, dpnId);
        if (currDpnInterfaces == null || !elanUtils.isDpnPresent(currDpnInterfaces.getDpId())
                || currDpnInterfaces.getInterfaces() == null || currDpnInterfaces.getInterfaces().isEmpty()) {
            return emptyList();
        }
        List<Bucket> listBucketInfo = new ArrayList<>();
        for (String interfaceName : currDpnInterfaces.getInterfaces()) {
            if (interfaceManager.isExternalInterface(interfaceName)) {
                List<Action> listActionInfo = elanItmUtils.getExternalPortItmEgressAction(interfaceName);
                if (!listActionInfo.isEmpty()) {
                    listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                            MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                    bucketId++;
                }
            }
        }
        return listBucketInfo;
    }

    @NonNull
    public List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces,
                                                Uint64 dpnId, int bucketId, long elanTag) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        ElanDpnInterfacesList elanDpns = elanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());

        if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
            // Adding 270000 to avoid collision between LPort and elan for broadcast group actions
            listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanDpns, dpnId, bucketId,
                    elanUtils.isOpenstackVniSemanticsEnforced()
                            ? ElanUtils.getVxlanSegmentationId(elanInfo).longValue() : elanTag
                            + ElanConstants.ELAN_TAG_ADDEND));
        }
        listBucketInfo.addAll(getRemoteBCGroupExternalPortBuckets(elanDpns, dpnInterfaces, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfElanExternalTeps(elanInfo, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBucketsOfElanL2GwDevices(ElanInstance elanInfo, Uint64 dpnId,
            int bucketId) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        for (L2GatewayDevice device : ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanInfo.getElanInstanceName())) {
            String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    device.getHwvtepNodeId());
            if (interfaceName == null) {
                continue;
            }
            List<Action> listActionInfo = elanItmUtils.buildTunnelItmEgressActions(interfaceName,
                    ElanUtils.getVxlanSegmentationId(elanInfo).longValue(), true);
            if (listActionInfo.isEmpty()) {
                LOG.debug("Retrieved empty egress action for interface {} for elan {} on DPN {}",
                        interfaceName, elanInfo.getElanInstanceName(), dpnId);
                continue;
            }
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        LOG.debug("Configured RemoteBCGroupBucketsOfElanL2GwDevices {} for DPN {} of ELAN {}",
                listBucketInfo, dpnId, elanInfo.getElanInstanceName());
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBucketsOfElanExternalTeps(ElanInstance elanInfo, Uint64 dpnId,
            int bucketId) {
        ElanInstance operElanInstance = null;
        try {
            operElanInstance = new SingleTransactionDataBroker(broker).syncReadOptional(
                LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, elanInfo.key())
                    .build()).orNull();
        } catch (ReadFailedException e) {
            LOG.error("Failed to read elan instance operational path {}", elanInfo, e);
            return emptyList();
        }
        if (operElanInstance == null) {
            return emptyList();
        }
        List<ExternalTeps> teps = operElanInstance.getExternalTeps();
        if (teps == null || teps.isEmpty()) {
            return emptyList();
        }
        List<Bucket> listBucketInfo = new ArrayList<>();
        for (ExternalTeps tep : teps) {
            String externalTep = tep.getNodeid() != null ? tep.getNodeid() : tep.getTepIp().toString();
            String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    externalTep);
            if (interfaceName == null) {
                LOG.error("Could not get interface name to ext tunnel {} {}", dpnId, tep.getTepIp());
                continue;
            }
            List<Action> listActionInfo = elanItmUtils.buildTunnelItmEgressActions(interfaceName,
                    ElanUtils.getVxlanSegmentationId(elanInfo).longValue(), false);
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

    private static int getNextAvailableBucketId(int bucketSize) {
        return bucketSize + 1;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<Bucket> getRemoteBCGroupTunnelBuckets(ElanDpnInterfacesList elanDpns, Uint64 dpnId, int bucketId,
            long elanTagOrVni) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.nonnullDpnInterfaces())  {
                if (!Objects.equals(dpnInterface.getDpId(), dpnId) && dpnInterface.getInterfaces() != null
                        && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = elanItmUtils.getInternalTunnelItmEgressAction(dpnId,
                                dpnInterface.getDpId(), elanTagOrVni);
                        LOG.trace("configuring broadcast group for elan {} for source DPN {} and destination DPN {} "
                                + "with actions {}", elanTagOrVni, dpnId, dpnInterface.getDpId(), listActionInfo);
                        if (listActionInfo.isEmpty()) {
                            continue;
                        }
                        listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                                MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                        bucketId++;
                    } catch (Exception ex) {
                        LOG.error("Logical Group Interface not found between source Dpn - {}, destination Dpn - {} ",
                                dpnId, dpnInterface.getDpId(), ex);
                    }
                }
            }
        }
        return listBucketInfo;
    }

    /**
     * Update remote mcast mac.
     *
     * @param elanName
     *            the elan name
     * @param device
     *            the device
     * @param dpnsTepIps
     *            the dpns tep ips
     * @param l2GwDevicesTepIps
     *            the l2 gw devices tep ips
     * @return the write transaction
     */
    private ListenableFuture<Void> prepareRemoteMcastMacEntry(String elanName,
                                             L2GatewayDevice device, List<IpAddress> dpnsTepIps,
                                             List<IpAddress> l2GwDevicesTepIps, boolean addCase) {
        NodeId nodeId = new NodeId(device.getHwvtepNodeId());

        ArrayList<IpAddress> remoteTepIps = new ArrayList<>(l2GwDevicesTepIps);
        remoteTepIps.remove(device.getTunnelIp());
        remoteTepIps.addAll(dpnsTepIps);
        IpAddress dhcpDesignatedSwitchTepIp = getTepIpOfDesignatedSwitchForExternalTunnel(device, elanName);
        if (dpnsTepIps.isEmpty()) {
            // If no dpns in elan, configure dhcp designated switch Tep Ip as a
            // physical locator in l2 gw device
            if (dhcpDesignatedSwitchTepIp != null) {
                remoteTepIps.add(dhcpDesignatedSwitchTepIp);

                HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                        .createHwvtepPhysicalLocatorAugmentation(dhcpDesignatedSwitchTepIp);
                InstanceIdentifier<TerminationPoint> iid =
                        HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug);
                TerminationPoint terminationPoint = new TerminationPointBuilder()
                                .withKey(HwvtepSouthboundUtils.getTerminationPointKey(phyLocatorAug))
                                .addAugmentation(HwvtepPhysicalLocatorAugmentation.class, phyLocatorAug).build();
                ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                        iid, terminationPoint);
                LOG.info("Adding PhysicalLocator for node: {} with Dhcp designated switch Tep Ip {} "
                        + "as physical locator, elan {}", device.getHwvtepNodeId(),
                        dhcpDesignatedSwitchTepIp.stringValue(), elanName);
            } else {
                LOG.warn("Dhcp designated switch Tep Ip not found for l2 gw node {} and elan {}",
                        device.getHwvtepNodeId(), elanName);
            }
        }
        if (dhcpDesignatedSwitchTepIp != null && !remoteTepIps.contains(dhcpDesignatedSwitchTepIp)) {
            remoteTepIps.add(dhcpDesignatedSwitchTepIp);
        }
        String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName);
        LOG.info("Adding RemoteMcastMac for node: {} with physical locators: {}", device.getHwvtepNodeId(),
                remoteTepIps);
        return putRemoteMcastMac(nodeId, logicalSwitchName, remoteTepIps, addCase);
    }

    /**
     * Put remote mcast mac in config DS.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param tepIps
     *            the tep ips
     */
    private ListenableFuture<Void> putRemoteMcastMac(NodeId nodeId, String logicalSwitchName,
            ArrayList<IpAddress> tepIps, boolean addCase) {
        List<LocatorSet> locators = new ArrayList<>();
        for (IpAddress tepIp : tepIps) {
            HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(tepIp);
            HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                    HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug));
            locators.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());
        }

        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));
        RemoteMcastMacs newRemoteMcastMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef)
                .setLocatorSet(locators).build();
        InstanceIdentifier<RemoteMcastMacs> iid = HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                newRemoteMcastMac.key());
        RemoteMcastMacs existingRemoteMcastMac = null;
        try {
            Optional<RemoteMcastMacs> mac  = elanRefUtil.getConfigMcastCache().get(iid);
            if (mac.isPresent()) {
                existingRemoteMcastMac = mac.get();
            }
        } catch (ReadFailedException e) {
            LOG.error("Failed to read iid {}", iid, e);
        }

        if (addCase && areLocatorsAlreadyConfigured(existingRemoteMcastMac, newRemoteMcastMac)) {
            return Futures.immediateFuture(null);
        }

        return ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                iid, newRemoteMcastMac);

    }

    private boolean areLocatorsAlreadyConfigured(RemoteMcastMacs existingMac, RemoteMcastMacs newMac) {
        if (existingMac == null) {
            return false;
        }
        Set existingLocators = new HashSet<>(existingMac.getLocatorSet());
        List newLocators = newMac.getLocatorSet();
        return existingLocators.containsAll(newLocators);
    }

    private InstanceIdentifier<RemoteMcastMacs> getRemoteMcastIid(NodeId nodeId, String logicalSwitchName) {
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));
        RemoteMcastMacs remoteMcastMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef)
                .build();
        return HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                remoteMcastMac.key());
    }

    /**
     * Gets all the tep ips of dpns.
     *
     * @param l2GwDevice
     *            the device
     * @param dpns
     *            the dpns
     * @return the all tep ips of dpns and devices
     */
    private List<IpAddress> getAllTepIpsOfDpns(L2GatewayDevice l2GwDevice, Collection<DpnInterfaces> dpns) {
        List<IpAddress> tepIps = new ArrayList<>();
        for (DpnInterfaces dpn : dpns) {
            IpAddress internalTunnelIp = elanItmUtils.getSourceDpnTepIp(dpn.getDpId(),
                    new NodeId(l2GwDevice.getHwvtepNodeId()));
            if (internalTunnelIp != null) {
                tepIps.add(internalTunnelIp);
            }
        }
        return tepIps;
    }

    /**
     * Gets the all tep ips of l2 gw devices.
     *
     * @param devices
     *            the devices
     * @return the all tep ips of l2 gw devices
     */
    private static List<IpAddress> getAllTepIpsOfL2GwDevices(Collection<L2GatewayDevice> devices) {
        List<IpAddress> tepIps = new ArrayList<>();
        for (L2GatewayDevice otherDevice : devices) {
            // There is no need to add the same tep ip to the list.
            if (!tepIps.contains(otherDevice.getTunnelIp())) {
                tepIps.add(otherDevice.getTunnelIp());
            }
        }
        return tepIps;
    }

    /**
     * Handle mcast for elan l2 gw device delete.
     *
     * @param elanName
     *            the elan instance name
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @return the listenable future
     */
    public List<ListenableFuture<Void>> handleMcastForElanL2GwDeviceDelete(String elanName,
                                                                           L2GatewayDevice l2GatewayDevice) {
        ListenableFuture<Void> deleteTepFuture =
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> tx.delete(buildExternalTepPath(elanName, l2GatewayDevice.getTunnelIp())));
        updateMcastMacsForAllElanDevices(elanName, l2GatewayDevice, false/* updateThisDevice */);
        ListenableFuture<Void> deleteRemoteMcastMacFuture = deleteRemoteMcastMac(
                new NodeId(l2GatewayDevice.getHwvtepNodeId()), elanName);
        return Arrays.asList(deleteRemoteMcastMacFuture, deleteTepFuture);
    }

    /**
     * Delete remote mcast mac from Hwvtep node.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @return the listenable future
     */
    public ListenableFuture<Void> deleteRemoteMcastMac(NodeId nodeId, String logicalSwitchName) {
        InstanceIdentifier<LogicalSwitches> logicalSwitch = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName));
        RemoteMcastMacsKey remoteMcastMacsKey = new RemoteMcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch),
                new MacAddress(ElanConstants.UNKNOWN_DMAC));

        LOG.info("Deleting RemoteMcastMacs entry on node: {} for logical switch: {}", nodeId.getValue(),
                logicalSwitchName);
        return HwvtepUtils.deleteRemoteMcastMac(broker, nodeId, remoteMcastMacsKey);
    }

    /**
     * Gets the tep ip of designated switch for external tunnel.
     *
     * @param l2GwDevice
     *            the l2 gw device
     * @param elanInstanceName
     *            the elan instance name
     * @return the tep ip of designated switch for external tunnel
     */
    public IpAddress getTepIpOfDesignatedSwitchForExternalTunnel(L2GatewayDevice l2GwDevice,
            String elanInstanceName) {
        IpAddress tepIp = null;
        if (l2GwDevice.getTunnelIp() == null) {
            LOG.warn("Tunnel IP not found for {}", l2GwDevice.getDeviceName());
            return tepIp;
        }
        DesignatedSwitchForTunnel desgSwitch = getDesignatedSwitchForExternalTunnel(l2GwDevice.getTunnelIp(),
                elanInstanceName);
        if (desgSwitch != null) {
            tepIp = elanItmUtils.getSourceDpnTepIp(Uint64.valueOf(desgSwitch.getDpId()),
                    new NodeId(l2GwDevice.getHwvtepNodeId()));
        }
        return tepIp;
    }

    /**
     * Gets the designated switch for external tunnel.
     *
     * @param tunnelIp
     *            the tunnel ip
     * @param elanInstanceName
     *            the elan instance name
     * @return the designated switch for external tunnel
     */
    public DesignatedSwitchForTunnel getDesignatedSwitchForExternalTunnel(IpAddress tunnelIp,
            String elanInstanceName) {
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier = InstanceIdentifier
                .builder(DesignatedSwitchesForExternalTunnels.class)
                .child(DesignatedSwitchForTunnel.class, new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp))
                .build();
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier).orNull();
    }

}
