/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static java.util.Collections.emptyList;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlanNetworkOrVxlanSegment;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.l2gw.jobs.McastUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.listeners.ConfigMcastCache;
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
    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger(ElanL2GatewayMulticastUtils.class);

    /** The broker. */
    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;

    private final ElanItmUtils elanItmUtils;
    private final ElanUtils elanUtils;
    private final IMdsalApiManager mdsalManager;
    private final IInterfaceManager interfaceManager;
    private final ConfigMcastCache configMcastCache;
    private final ElanInstanceDpnsCache elanInstanceDpnsCache;
    private final Scheduler scheduler;
    private final ElanRefUtil elanRefUtil;
    private final ElanClusterUtils elanClusterUtils;
    private final JobCoordinator jobCoordinator;

    private volatile boolean immediatelyAfterClusterReboot = true;


    @Inject
    public ElanL2GatewayMulticastUtils(DataBroker broker, ElanItmUtils elanItmUtils, ElanUtils elanUtils,
                                       IMdsalApiManager mdsalManager, IInterfaceManager interfaceManager,
                                       ConfigMcastCache configMcastCache,
                                       ElanInstanceDpnsCache elanInstanceDpnsCache,
                                       ElanRefUtil elanRefUtil,
                                       ElanClusterUtils elanClusterUtils,
                                       JobCoordinator jobCoordinator,
                                       Scheduler scheduler) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanItmUtils = elanItmUtils;
        this.elanUtils = elanUtils;
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.configMcastCache = configMcastCache;
        this.scheduler = scheduler;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanRefUtil = elanRefUtil;
        this.elanClusterUtils = elanClusterUtils;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void init() {
        scheduler.getScheduledExecutorService().schedule(() -> {
            immediatelyAfterClusterReboot = false;
        }, 60, TimeUnit.MINUTES);
    }

    public static InstanceIdentifier<ExternalTeps> buildExternalTepPath(String elan, IpAddress tepIp) {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, new ElanInstanceKey(elan))
                .child(ExternalTeps.class, new ExternalTepsKey(tepIp)).build();
    }

    protected ExternalTeps buildExternalTeps(L2GatewayDevice device) {
        return new ExternalTepsBuilder().setTepIp(device.getTunnelIp()).setNodeid(device.getHwvtepNodeId()).build();
    }

    public IInterfaceManager getInterfaceManager() {
        return interfaceManager;
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
        for (L2GatewayDevice device : ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).values()) {
            prepareRemoteMcastMacUpdateOnDevice(elanName, device, false, null);
        }
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

    public ListenableFuture<Void> prepareRemoteMcastMacUpdateOnDevice(String elanName,
                                                    L2GatewayDevice dstDevice,
                                                    boolean addCase,
                                                    IpAddress removedDstTep) {
        NodeId dstNodeId = new NodeId(dstDevice.getHwvtepNodeId());
        RemoteMcastMacs existingMac = configMcastCache.getMac(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(dstNodeId, new HwvtepNodeName(elanName)));
        if (!addCase && immediatelyAfterClusterReboot && removedDstTep != null) {
            LOG.debug(" RemoteMcast update delete tep {} of elan {} ", removedDstTep.getIpv4Address().getValue(),
                    elanName);
            //incase of dpn flap immediately after cluster reboot just remove its tep alone
            if (existingMac != null) {
                return deleteLocatorFromMcast(elanName, dstNodeId, removedDstTep, existingMac);
            }
        }
        ConcurrentMap<String, L2GatewayDevice> elanL2gwDevices = ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName);
        Collection<DpnInterfaces> elanDpns = elanInstanceDpnsCache.get(elanName);
        List<IpAddress> dpnsTepIps = getAllTepIpsOfDpns(dstDevice, new ArrayList<DpnInterfaces>(elanDpns));
        List<IpAddress> l2GwDevicesTepIps = getAllTepIpsOfL2GwDevices(elanL2gwDevices);

        return preapareRemoteMcastMacEntry(elanName, dstDevice, dpnsTepIps, l2GwDevicesTepIps, addCase);
    }

    private ListenableFuture<Void> deleteLocatorFromMcast(String elanName, NodeId dstNodeId,
                                                          IpAddress removedDstTep,
                                                          RemoteMcastMacs existingMac) {
        InstanceIdentifier<RemoteMcastMacs> macIid = HwvtepSouthboundUtils
                .createRemoteMcastMacsInstanceIdentifier(dstNodeId, existingMac.key());
        LocatorSet tobeDeleted = buildLocatorSet(dstNodeId, removedDstTep);
        RemoteMcastMacsBuilder newMacBuilder = new RemoteMcastMacsBuilder(existingMac);
        newMacBuilder.getLocatorSet().remove(tobeDeleted);
        RemoteMcastMacs mac = newMacBuilder.build();
        configMcastCache.added(macIid, mac);
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
     * @param createCase
     *            the update this device
     * @return the listenable future
     */
    public List<ListenableFuture<Void>> updateMcastMacsForAllElanDevices(String elanName, L2GatewayDevice device,
                                                                         boolean createCase) {
        /*BcGroupUpdateJob.updateAllBcGroups(elanName, createCase, null, device,
                elanRefUtil, this, mdsalManager,
                elanInstanceDpnsCache, elanItmUtils); */
        if (createCase) {
            McastUpdateJob.updateAllMcastsForConnectionAdd(elanName, this, elanClusterUtils, scheduler,
                    jobCoordinator);
        } else {
            McastUpdateJob.updateAllMcastsForConnectionDelete(elanName, this, elanClusterUtils, scheduler,
                    jobCoordinator, device);
        }
        return Collections.emptyList();
    }

    public void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo, boolean createCase,
                                                         Uint64 addedDpn) {
        //TODO cache this read
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx -> {
            //List<DpnInterfaces> dpns = elanUtils.getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
            Collection<DpnInterfaces> dpns = elanInstanceDpnsCache.get(elanInfo.getElanInstanceName());
            LOG.trace("Invoking method ELAN Broadcast Groups for ELAN {}",
                elanInfo.getElanInstanceName());
            if (createCase == true && addedDpn != null) {
                setupStandardElanBroadcastGroups(elanInfo, null, addedDpn, createCase,
                    confTx);
            }
            for (DpnInterfaces dpn : dpns) {
                if (!dpn.getDpId().equals(addedDpn)) {
                    setupStandardElanBroadcastGroups(elanInfo, null, dpn.getDpId(), createCase, confTx);
                }
            }
        });
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, Uint64 dpnId,
        TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("Setting up ELAN Broadcast Group for ELAN Instance {} for DPN {} ", elanInfo, dpnId);
        setupElanBroadcastGroups(elanInfo, null, dpnId, confTx);
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces,
        Uint64 dpnId ,TypedWriteTransaction<Datastore.Configuration> confTx) {
        setupStandardElanBroadcastGroups(elanInfo, dpnInterfaces, dpnId, confTx);
        setupLeavesEtreeBroadcastGroups(elanInfo, dpnInterfaces, dpnId, confTx);
    }

    public void setupStandardElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces,
        Uint64 dpnId, TypedWriteTransaction<Datastore.Configuration> confTx) {
        setupStandardElanBroadcastGroups(elanInfo, dpnInterfaces, dpnId, true, confTx);
    }

    public void setupStandardElanBroadcastGroups(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces,
        Uint64 dpnId, boolean createCase, TypedWriteTransaction<Datastore.Configuration> confTx) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag().longValue();
        List<Action> listAction = new ArrayList<>();
        listAction.add(new ActionGroup(ElanUtils.getElanLocalBCGId(elanTag)).buildAction(++actionKey));
        listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        LOG.debug("Configured ELAN Broadcast Group with Action {} ", listAction);
        bucketId++;
        LOG.info("Constructing RemoteBCGroupBuckets for {} on dpn {} ", elanInfo.getElanInstanceName(), dpnId);
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnInterfaces, dpnId, bucketId, elanTag);
        listBucket.addAll(listBucketInfoRemote);
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        LOG.info("Installing the remote BroadCast Group:{}", group);
        EVENT_LOGGER.debug("ELAN-RBG, ADD {} Elan Instance {} Dpn Id {}", group.getGroupId().getValue(),
                elanInfo.getElanInstanceName(), dpnId);
        if (createCase) {
            elanUtils.syncUpdateGroup(dpnId, group, ElanConstants.DELAY_TIME_IN_MILLISECOND, confTx);
        } else {
            mdsalManager.addGroup(confTx, dpnId, group);
        }
    }

    public void setupLeavesEtreeBroadcastGroups(ElanInstance elanInfo, @Nullable DpnInterfaces dpnInterfaces,
        Uint64 dpnId, TypedWriteTransaction<Configuration> confTx) {
        EtreeInstance etreeInstance = elanInfo.augmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            long etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue().longValue();
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

    private DpnInterfaces getDpnInterfaces(ElanDpnInterfacesList elanDpns, Uint64 dpnId) {
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.nonnullDpnInterfaces().values()) {
                LOG.trace("List of DpnInterfaces present in DS {} ", dpnInterface);
                if (dpnInterface.getDpId().equals(dpnId)) {
                    return dpnInterface;
                }
            }
        }
        LOG.debug("DPN {} missing in DpnInterfaces list {}", dpnId, elanDpns);
        return null;
    }

    private List<Bucket> getRemoteBCGroupExternalPortBuckets(ElanDpnInterfacesList elanDpns,
            DpnInterfaces dpnInterfaces, Uint64 dpnId, int bucketId) {
        DpnInterfaces currDpnInterfaces = dpnInterfaces != null ? dpnInterfaces : getDpnInterfaces(elanDpns, dpnId);
        if (currDpnInterfaces == null || !elanUtils.isDpnPresent(currDpnInterfaces.getDpId())
                || currDpnInterfaces.getInterfaces() == null || currDpnInterfaces.getInterfaces().isEmpty()) {
            LOG.debug("Returning empty Bucket list for DPN {}", dpnId);
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
        LOG.debug("Configured RemoteBCGroupExternalPortBuckets {} for DPN {}", listBucketInfo, dpnId);
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, Uint64 dpnId,
                                                int bucketId, long elanTag) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        ElanDpnInterfacesList elanDpns = elanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());

        if (isVxlanNetworkOrVxlanSegment(elanInfo)) {
            // Adding 270000 to avoid collision between LPort and elan for broadcast group actions
            listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanDpns, dpnId, bucketId,
                    elanUtils.isOpenstackVniSemanticsEnforced()
                            ? elanUtils.getVxlanSegmentationId(elanInfo).longValue()
                        : elanTag + ElanConstants.ELAN_TAG_ADDEND));
        }
        listBucketInfo.addAll(getRemoteBCGroupExternalPortBuckets(elanDpns, dpnInterfaces, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfElanExternalTeps(elanInfo, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfL2gwDevices(elanInfo, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
        LOG.debug("Configured ELAN Remote BC Group with Bucket Info {}", listBucketInfo);
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBucketsOfElanL2GwDevices(ElanInstance elanInfo, BigInteger dpnId,
            int bucketId) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        ConcurrentMap<String, L2GatewayDevice> map = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInfo.getElanInstanceName());
        for (L2GatewayDevice device : map.values()) {
            String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    device.getHwvtepNodeId());
            if (interfaceName == null) {
                LOG.debug("RPC returned with empty response for getExternalTunnelInterfaceName {}"
                        + " for DPN {}, bucketID {} ",elanInfo.getElanInstanceName() ,dpnId, bucketId);
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
                LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class, elanInfo.key())
                    .build()).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Failed to read elan instance operational path {}", elanInfo, e);
            return emptyList();
        }
        if (operElanInstance == null) {
            return emptyList();
        }
        List<ExternalTeps> teps = new ArrayList<>(operElanInstance.nonnullExternalTeps().values());
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
                    elanUtils.getVxlanSegmentationId(elanInfo).longValue(), false);
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBucketsOfL2gwDevices(ElanInstance elanInfo, Uint64 dpnId, int bucketId) {
        ConcurrentMap<String, L2GatewayDevice> elanL2gwDevices = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInfo.getElanInstanceName());
        if (elanL2gwDevices == null || elanL2gwDevices.isEmpty()) {
            return Collections.emptyList();
        }
        List<Bucket> listBucketInfo = new ArrayList<>();
        for (L2GatewayDevice l2GatewayDevice : elanL2gwDevices.values()) {
            if (l2GatewayDevice.getTunnelIp() == null) {
                continue;
            }
            String externalTep = l2GatewayDevice.getTunnelIp().toString();
            String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    externalTep);
            if (interfaceName == null) {
                LOG.error("Could not get interface name to ext tunnel {} {}", dpnId, externalTep);
                continue;
            }
            List<Action> listActionInfo = elanItmUtils.buildTunnelItmEgressActions(interfaceName,
                    elanUtils.getVxlanSegmentationId(elanInfo).longValue(), false);
            if (!listActionInfo.isEmpty()) {
                LOG.debug("Adding Remote BC Group Bucket of tor - tunnel {} tun_id {}", interfaceName,
                        elanUtils.getVxlanSegmentationId(elanInfo));
            }
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

    private int getNextAvailableBucketId(int bucketSize) {
        return bucketSize + 1;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<Bucket> getRemoteBCGroupTunnelBuckets(ElanDpnInterfacesList elanDpns, Uint64 dpnId, int bucketId,
            long elanTagOrVni) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns. nonnullDpnInterfaces().values()) {
                if (!Objects.equals(dpnInterface.getDpId(), dpnId) && dpnInterface.getInterfaces() != null
                        && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = elanItmUtils.getInternalTunnelItmEgressAction(dpnId,
                                dpnInterface.getDpId(), elanTagOrVni);
                        LOG.trace("configuring broadcast group for elan {} for source DPN {} and destination DPN {} "
                                + "with actions {}", elanTagOrVni, dpnId, dpnInterface.getDpId(), listActionInfo);
                        if (listActionInfo.isEmpty()) {
                            LOG.debug("getInternalTunnelItmEgressAction for src DPN {} "
                                            + "and dest DPN {} for ELAN Tag or VNI {} returned empty",
                                    dpnId, dpnInterface.getDpId(), elanTagOrVni);
                            continue;
                        }
                        listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                                MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
                        bucketId++;
                    } catch (Exception ex) {
                        LOG.error("Logical Group Interface not found between source Dpn - {}, "
                                        + "destination Dpn - {} with exception", dpnId, dpnInterface.getDpId(), ex);
                    }
                }
            }
        }
        LOG.debug("Configured RemoteBCGroupTunnelBuckets Info {} for DPN {} for ELAN Tag or VNI{}",
                listBucketInfo, dpnId, elanTagOrVni);
        return listBucketInfo;
    }

    /**
     * Update remote mcast mac.
     *
     * @param elanName
     *            the elan name
     * @param    device
     *            the device
     * @param dpnsTepIps
     *            the dpns tep ips
     * @param l2GwDevicesTepIps
     *            the l2 gw devices tep ips
     * @return the write transaction
     */
    private ListenableFuture<Void> preapareRemoteMcastMacEntry(String elanName,
                                                               L2GatewayDevice device, List<IpAddress> dpnsTepIps,
                                                               List<IpAddress> l2GwDevicesTepIps, boolean addCase) {
        ArrayList<IpAddress> remoteTepIps = new ArrayList<>(l2GwDevicesTepIps);
        remoteTepIps.remove(device.getTunnelIp());
        remoteTepIps.addAll(dpnsTepIps);
        IpAddress dhcpDesignatedSwitchTepIp = getTepIpOfDesignatedSwitchForExternalTunnel(device, elanName);
        if (dhcpDesignatedSwitchTepIp != null && !remoteTepIps.contains(dhcpDesignatedSwitchTepIp)) {
            remoteTepIps.add(dhcpDesignatedSwitchTepIp);
        }
        return putRemoteMcastMac(new NodeId(device.getHwvtepNodeId()), elanName, remoteTepIps, addCase);
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
            locators.add(buildLocatorSet(nodeId, tepIp));
        }
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));
        RemoteMcastMacs newMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef)
                .setLocatorSet(locators).build();
        InstanceIdentifier<RemoteMcastMacs> iid = HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                newMac.key());
        RemoteMcastMacs existingMac = configMcastCache.getMac(newMac.getLogicalSwitchRef().getValue());

        if (!addCase) {
            //proactively update the cache for delete cases do not wait for batch manager to delete from cache
            //while the delete is in progress from the batch manager the below skip may trigger
            //by updating the cache upfront the skip wont be triggered
            configMcastCache.added(iid, newMac);
        }

        if (addCase && existingMac != null && existingMac.getLocatorSet() != null) {
            Set existingLocators = new HashSet<>(existingMac.getLocatorSet());
            List newLocators = newMac.getLocatorSet();
            if (existingLocators.containsAll(newLocators)) {
                return Futures.immediateFuture(null);
            }
        }

        return ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                iid, newMac);
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
    private List<IpAddress> getAllTepIpsOfDpns(L2GatewayDevice l2GwDevice, List<DpnInterfaces> dpns) {
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
    private static List<IpAddress> getAllTepIpsOfL2GwDevices(ConcurrentMap<String, L2GatewayDevice> devices) {
        List<IpAddress> tepIps = new ArrayList<>();
        for (L2GatewayDevice otherDevice : devices.values()) {
            // There is no need to add the same tep ip to the list.
            if (!tepIps.contains(otherDevice.getTunnelIp())) {
                tepIps.add(otherDevice.getTunnelIp());
            }
        }
        return tepIps;
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
    public ListenableFuture<?> deleteRemoteMcastMac(NodeId nodeId, String logicalSwitchName) {
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
        try {
            InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier = InstanceIdentifier
                .builder(DesignatedSwitchesForExternalTunnels.class)
                .child(DesignatedSwitchForTunnel.class,new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp))
                .build();
            return new SingleTransactionDataBroker(broker).syncReadOptional(broker,
                LogicalDatastoreType.CONFIGURATION, instanceIdentifier).orElse(null);
        } catch (ExecutionException e) {
            LOG.error("Exception while retriving DesignatedSwitch for elan {} and tunnel {}",
                elanInstanceName, tunnelIp, e);
        } catch (InterruptedException e) {
            LOG.error("Exception while retriving DesignatedSwitch for elan {} and tunnel {}",
                elanInstanceName, tunnelIp, e);
        }
        return null;
    }

}
