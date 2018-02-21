/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlan;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.jobs.HwvtepDeviceMcastMacUpdateJob;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
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

    @Inject
    public ElanL2GatewayMulticastUtils(DataBroker broker, ElanItmUtils elanItmUtils, JobCoordinator jobCoordinator,
            ElanUtils elanUtils, IMdsalApiManager mdsalManager, IInterfaceManager interfaceManager) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanItmUtils = elanItmUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanUtils = elanUtils;
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
    }

    /**
     * Handle mcast for elan l2 gw device add.
     * @param elanName the elan name
     * @param elanInstance elanInstace
     * @param device the device
     * @return the listenable future
     */
    public ListenableFuture<Void> handleMcastForElanL2GwDeviceAdd(String elanName, ElanInstance elanInstance,
            L2GatewayDevice device) {
        return updateMcastMacsForAllElanDevices(elanName, elanInstance, device, true/* updateThisDevice */);
    }

    /**
     * Updates the remote mcast mac table for all the devices in this elan
     * includes all the dpn tep ips and other devices tep ips in broadcast
     * locator set.
     *
     * @param elanName
     *            the elan to be updated
     * @return the listenable future
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<Void> updateRemoteMcastMacOnElanL2GwDevices(String elanName) {
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            for (L2GatewayDevice device : ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).values()) {
                prepareRemoteMcastMacUpdateOnDevice(tx, elanName, device);
            }
        });
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
     * @return the listenable future
     */
    public ListenableFuture<Void> updateRemoteMcastMacOnElanL2GwDevice(String elanName, L2GatewayDevice device) {
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> prepareRemoteMcastMacUpdateOnDevice(tx, elanName, device));
    }

    public void prepareRemoteMcastMacUpdateOnDevice(WriteTransaction transaction,String elanName,
                                                           L2GatewayDevice device) {
        ConcurrentMap<String, L2GatewayDevice> elanL2gwDevices = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanName);
        List<DpnInterfaces> dpns = elanUtils.getElanDPNByName(elanName);
        List<IpAddress> dpnsTepIps = getAllTepIpsOfDpns(device, dpns);
        List<IpAddress> l2GwDevicesTepIps = getAllTepIpsOfL2GwDevices(elanL2gwDevices);
        preapareRemoteMcastMacEntry(transaction, elanName, device, dpnsTepIps, l2GwDevicesTepIps);
    }

    /**
     * Update mcast macs for this elan.
     * for all dpns in this elan  recompute and update broadcast group
     * for all l2gw devices in this elan recompute and update remote mcast mac entry
     *
     * @param elanName
     *            the elan name
     * @param elanInstance
     *            the ElanInstance
     * @param device
     *            the device
     * @param updateThisDevice
     *            the update this device
     * @return the listenable future
     */
    private ListenableFuture<Void> updateMcastMacsForAllElanDevices(String elanName, ElanInstance elanInstance,
            L2GatewayDevice device, boolean updateThisDevice) {

        SettableFuture<Void> ft = SettableFuture.create();
        ft.set(null);

        updateRemoteBroadcastGroupForAllElanDpns(elanInstance);

        List<DpnInterfaces> dpns = elanUtils.getElanDPNByName(elanName);

        ConcurrentMap<String, L2GatewayDevice> devices = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanName);

        List<IpAddress> dpnsTepIps = getAllTepIpsOfDpns(device, dpns);
        List<IpAddress> l2GwDevicesTepIps = getAllTepIpsOfL2GwDevices(devices);
        // if (allTepIps.size() < 2) {
        // LOG.debug("no other devices are found in the elan {}", elanName);
        // return ft;
        // }

        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            if (updateThisDevice) {
                preapareRemoteMcastMacEntry(tx, elanName, device, dpnsTepIps, l2GwDevicesTepIps);
            }

            // TODO: Need to revisit below logic as logical switches might not be
            // present to configure RemoteMcastMac entry
            for (L2GatewayDevice otherDevice : devices.values()) {
                if (!otherDevice.getDeviceName().equals(device.getDeviceName())) {
                    preapareRemoteMcastMacEntry(tx, elanName, otherDevice, dpnsTepIps, l2GwDevicesTepIps);
                }
            }
        });

    }

    public void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo) {
        List<DpnInterfaces> dpns = elanUtils.getInvolvedDpnsInElan(elanInfo.getElanInstanceName());
        for (DpnInterfaces dpn : dpns) {
            setupElanBroadcastGroups(elanInfo, dpn.getDpId());
        }
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId) {
        setupElanBroadcastGroups(elanInfo, null, dpnId);
    }

    public void setupElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId) {
        setupStandardElanBroadcastGroups(elanInfo, dpnInterfaces, dpnId);
        setupLeavesEtreeBroadcastGroups(elanInfo, dpnInterfaces, dpnId);
    }

    public void setupStandardElanBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId) {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
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
        mdsalManager.syncInstallGroup(dpnId, group);
    }

    public void setupLeavesEtreeBroadcastGroups(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId) {
        EtreeInstance etreeInstance = elanInfo.getAugmentation(EtreeInstance.class);
        if (etreeInstance != null) {
            long etreeLeafTag = etreeInstance.getEtreeLeafTagVal().getValue();
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
            mdsalManager.syncInstallGroup(dpnId, group);
        }
    }

    private DpnInterfaces getDpnInterfaces(ElanDpnInterfacesList elanDpns, BigInteger dpnId) {
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.getDpnInterfaces()) {
                if (dpnInterface.getDpId().equals(dpnId)) {
                    return dpnInterface;
                }
            }
        }
        return null;
    }

    private List<Bucket> getRemoteBCGroupExternalPortBuckets(ElanDpnInterfacesList elanDpns,
            DpnInterfaces dpnInterfaces, BigInteger dpnId, int bucketId) {
        DpnInterfaces currDpnInterfaces = dpnInterfaces != null ? dpnInterfaces : getDpnInterfaces(elanDpns, dpnId);
        if (currDpnInterfaces == null || !elanUtils.isDpnPresent(currDpnInterfaces.getDpId())
                || currDpnInterfaces.getInterfaces() == null || currDpnInterfaces.getInterfaces().isEmpty()) {
            return Collections.emptyList();
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

    @Nonnull
    public List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId,
                                                int bucketId, long elanTag) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        ElanDpnInterfacesList elanDpns = elanUtils.getElanDpnInterfacesList(elanInfo.getElanInstanceName());

        if (isVxlan(elanInfo)) {
            listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanDpns, dpnId, bucketId,
                    elanUtils.isOpenstackVniSemanticsEnforced() ? elanInfo.getSegmentationId() : elanTag));
        }
        listBucketInfo.addAll(getRemoteBCGroupExternalPortBuckets(elanDpns, dpnInterfaces, dpnId,
            getNextAvailableBucketId(listBucketInfo.size())));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfElanL2GwDevices(elanInfo, dpnId,
            getNextAvailableBucketId(listBucketInfo.size())));
        listBucketInfo.addAll(getRemoteBCGroupBucketsOfElanExternalTeps(elanInfo, dpnId,
                getNextAvailableBucketId(listBucketInfo.size())));
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
                continue;
            }
            List<Action> listActionInfo = elanItmUtils.buildTunnelItmEgressActions(interfaceName,
                    ElanUtils.getVxlanSegmentationId(elanInfo));
            listBucketInfo.add(MDSALUtil.buildBucket(listActionInfo, MDSALUtil.GROUP_WEIGHT, bucketId,
                    MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));
            bucketId++;
        }
        return listBucketInfo;
    }

    public List<Bucket> getRemoteBCGroupBucketsOfElanExternalTeps(ElanInstance elanInfo, BigInteger dpnId,
            int bucketId) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        List<ExternalTeps> teps = elanInfo.getExternalTeps();
        if (teps == null || teps.isEmpty()) {
            return listBucketInfo;
        }
        for (ExternalTeps tep : teps) {
            String interfaceName = elanItmUtils.getExternalTunnelInterfaceName(String.valueOf(dpnId),
                    tep.getTepIp().toString());
            if (interfaceName == null) {
                LOG.error("Could not get interface name to ext tunnel {} {}", dpnId, tep.getTepIp());
                continue;
            }
            List<Action> listActionInfo = elanItmUtils.buildTunnelItmEgressActions(interfaceName,
                    elanInfo.getSegmentationId());
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
    private List<Bucket> getRemoteBCGroupTunnelBuckets(ElanDpnInterfacesList elanDpns, BigInteger dpnId, int bucketId,
            long elanTagOrVni) {
        List<Bucket> listBucketInfo = new ArrayList<>();
        if (elanDpns != null) {
            for (DpnInterfaces dpnInterface : elanDpns.getDpnInterfaces()) {
                if (elanUtils.isDpnPresent(dpnInterface.getDpId()) && !Objects.equals(dpnInterface.getDpId(), dpnId)
                        && dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty()) {
                    try {
                        List<Action> listActionInfo = elanItmUtils.getInternalTunnelItmEgressAction(dpnId,
                                dpnInterface.getDpId(), elanTagOrVni);
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
     * @param transaction
     *            the transaction
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
    private void preapareRemoteMcastMacEntry(WriteTransaction transaction, String elanName,
                                                    L2GatewayDevice device, List<IpAddress> dpnsTepIps,
                                                    List<IpAddress> l2GwDevicesTepIps) {
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
                        .createHwvtepPhysicalLocatorAugmentation(String.valueOf(dhcpDesignatedSwitchTepIp.getValue()));
                InstanceIdentifier<TerminationPoint> iid =
                        HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug);
                TerminationPoint terminationPoint = new TerminationPointBuilder()
                                .setKey(HwvtepSouthboundUtils.getTerminationPointKey(phyLocatorAug))
                                .addAugmentation(HwvtepPhysicalLocatorAugmentation.class, phyLocatorAug).build();
                ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                        iid, terminationPoint);
                LOG.info("Adding PhysicalLocator for node: {} with Dhcp designated switch Tep Ip {} "
                        + "as physical locator, elan {}", device.getHwvtepNodeId(),
                        String.valueOf(dhcpDesignatedSwitchTepIp.getValue()), elanName);
            } else {
                LOG.warn("Dhcp designated switch Tep Ip not found for l2 gw node {} and elan {}",
                        device.getHwvtepNodeId(), elanName);
            }
        }
        if (dhcpDesignatedSwitchTepIp != null && !remoteTepIps.contains(dhcpDesignatedSwitchTepIp)) {
            remoteTepIps.add(dhcpDesignatedSwitchTepIp);
        }
        String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName);
        putRemoteMcastMac(transaction, nodeId, logicalSwitchName, remoteTepIps);
        LOG.info("Adding RemoteMcastMac for node: {} with physical locators: {}", device.getHwvtepNodeId(),
                remoteTepIps);
    }

    /**
     * Put remote mcast mac in config DS.
     *
     * @param transaction
     *            the transaction
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param tepIps
     *            the tep ips
     */
    private static void putRemoteMcastMac(WriteTransaction transaction, NodeId nodeId, String logicalSwitchName,
            ArrayList<IpAddress> tepIps) {
        List<LocatorSet> locators = new ArrayList<>();
        for (IpAddress tepIp : tepIps) {
            HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(String.valueOf(tepIp.getValue()));
            HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                    HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, phyLocatorAug));
            locators.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());
        }

        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName)));
        RemoteMcastMacs remoteMcastMac = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(ElanConstants.UNKNOWN_DMAC)).setLogicalSwitchRef(lsRef)
                .setLocatorSet(locators).build();
        InstanceIdentifier<RemoteMcastMacs> iid = HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                remoteMcastMac.getKey());
        ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                iid, remoteMcastMac);

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
     * Handle mcast for elan l2 gw device delete.
     *
     * @param elanName
     *            the elan instance name
     * @param elanInstance
     *            the ElanInstance
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @return the listenable future
     */
    public List<ListenableFuture<Void>> handleMcastForElanL2GwDeviceDelete(String elanName,
            ElanInstance elanInstance, L2GatewayDevice l2GatewayDevice) {
        ListenableFuture<Void> updateMcastMacsFuture = updateMcastMacsForAllElanDevices(
                elanName, elanInstance, l2GatewayDevice, false/* updateThisDevice */);
        ListenableFuture<Void> deleteRemoteMcastMacFuture = deleteRemoteMcastMac(
                new NodeId(l2GatewayDevice.getHwvtepNodeId()), elanName);
        return Arrays.asList(updateMcastMacsFuture, deleteRemoteMcastMacFuture);
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
    private ListenableFuture<Void> deleteRemoteMcastMac(NodeId nodeId, String logicalSwitchName) {
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
            tepIp = elanItmUtils.getSourceDpnTepIp(BigInteger.valueOf(desgSwitch.getDpId()),
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
