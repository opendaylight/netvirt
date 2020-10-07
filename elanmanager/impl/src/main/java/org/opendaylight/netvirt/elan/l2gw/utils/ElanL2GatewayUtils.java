/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.jobs.DeleteL2GwDeviceMacsFromElanJob;
import org.opendaylight.netvirt.elan.l2gw.jobs.DeleteLogicalSwitchJob;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanDmacUtils;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.IetfYangUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.DeviceVteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddL2GwDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddL2GwDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepMacTableGenericAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * It gathers a set of utility methods that handle ELAN configuration in
 * external Devices (where external means "not-CSS". As of now: TORs).
 *
 * <p>It makes use of HwvtepUtils class located under ovsdb/hwvtepsouthbound
 * project for low-level mdsal operations
 *
 * @author eperefr
 */
@Singleton
public class ElanL2GatewayUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanL2GatewayUtils.class);
    private static final int DEFAULT_LOGICAL_SWITCH_DELETE_DELAY_SECS = 20;

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanDmacUtils elanDmacUtils;
    private final ElanItmUtils elanItmUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final OdlInterfaceRpcService interfaceManagerRpcService;
    private final JobCoordinator jobCoordinator;
    private final ElanUtils elanUtils;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInstanceDpnsCache elanInstanceDpnsCache;

    private final ConcurrentMap<Pair<NodeId, String>, ScheduledFuture> logicalSwitchDeletedTasks
            = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<NodeId, String>, DeleteLogicalSwitchJob> deleteJobs = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    private final ElanConfig elanConfig;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final InstanceIdDataObjectCache<TerminationPoint> portsCache;

    @Inject
    public ElanL2GatewayUtils(DataBroker broker, ElanDmacUtils elanDmacUtils, ElanItmUtils elanItmUtils,
                              ElanClusterUtils elanClusterUtils, OdlInterfaceRpcService interfaceManagerRpcService,
                              JobCoordinator jobCoordinator, ElanUtils elanUtils,
                              Scheduler scheduler, ElanConfig elanConfig, ElanInstanceCache elanInstanceCache,
                              ElanInstanceDpnsCache elanInstanceDpnsCache,
                              ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                              CacheProvider cacheProvider) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanDmacUtils = elanDmacUtils;
        this.elanItmUtils = elanItmUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.interfaceManagerRpcService = interfaceManagerRpcService;
        this.jobCoordinator = jobCoordinator;
        this.elanUtils = elanUtils;
        this.scheduler = scheduler;
        this.elanConfig = elanConfig;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        InstanceIdentifier<TerminationPoint> iid = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class).child(TerminationPoint.class);
        LOG.info("termination point iid: {}", iid);
        portsCache = new InstanceIdDataObjectCache<>(TerminationPoint.class, broker,
                LogicalDatastoreType.CONFIGURATION, iid, cacheProvider);
    }

    @PreDestroy
    public void close() {
    }

    public long getLogicalSwitchDeleteDelaySecs() {
        return elanConfig.getL2gwLogicalSwitchDelaySecs() != null
                ? elanConfig.getL2gwLogicalSwitchDelaySecs().toJava() : DEFAULT_LOGICAL_SWITCH_DELETE_DELAY_SECS;
    }

    /**
     * gets the macs addresses for elan interfaces.
     *
     * @param lstElanInterfaceNames
     *            the lst elan interface names
     * @return the list
     */
    /*
    public List<PhysAddress> getElanDpnMacsFromInterfaces(Set<String> lstElanInterfaceNames) {
        List<PhysAddress> result = new ArrayList<>();
        for (String interfaceName : lstElanInterfaceNames) {
            ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(broker, interfaceName);
            if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
                for (MacEntry macEntry : elanInterfaceMac.getMacEntry()) {
                    result.add(macEntry.getMacAddress());
                }
            }
        }
        return result;
    }*/

    /**
     * Check if phy locator already exists in remote mcast entry.
     *
     * @param nodeId
     *            the node id
     * @param remoteMcastMac
     *            the remote mcast mac
     * @param expectedPhyLocatorIp
     *            the expected phy locator ip
     * @return true, if successful
     */
    public static boolean checkIfPhyLocatorAlreadyExistsInRemoteMcastEntry(NodeId nodeId,
            RemoteMcastMacs remoteMcastMac, IpAddress expectedPhyLocatorIp) {
        if (remoteMcastMac != null) {
            HwvtepPhysicalLocatorAugmentation expectedPhyLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(expectedPhyLocatorIp);
            HwvtepPhysicalLocatorRef expectedPhyLocRef = new HwvtepPhysicalLocatorRef(
                    HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(nodeId, expectedPhyLocatorAug));
            if (remoteMcastMac.getLocatorSet() != null) {
                for (LocatorSet locatorSet : remoteMcastMac.getLocatorSet()) {
                    if (Objects.equals(locatorSet.getLocatorRef(), expectedPhyLocRef)) {
                        LOG.trace("matched phyLocRef: {}", expectedPhyLocRef);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the remote mcast mac.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param datastoreType
     *            the datastore type
     * @return the remote mcast mac
     */
    public RemoteMcastMacs readRemoteMcastMac(NodeId nodeId, String logicalSwitchName,
            LogicalDatastoreType datastoreType) {
        InstanceIdentifier<LogicalSwitches> logicalSwitch = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(nodeId, new HwvtepNodeName(logicalSwitchName));
        RemoteMcastMacsKey remoteMcastMacsKey = new RemoteMcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch),
                new MacAddress(ElanConstants.UNKNOWN_DMAC));
        try {
            return HwvtepUtils.getRemoteMcastMac(broker, datastoreType, nodeId, remoteMcastMacsKey);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("readRemoteMcastMac: Exception while reading LogicalSwitches DS for the nodeId {}, "
                    + "logicalSwitchName {}", nodeId.getValue(), logicalSwitch, e);
        }
        return null;
    }

    /**
     * Removes the given MAC Addresses from all the External Devices belonging
     * to the specified ELAN.
     *
     * @param elanInstance
     *            the elan instance
     * @param macAddresses
     *            the mac addresses
     */
    public void removeMacsFromElanExternalDevices(ElanInstance elanInstance, List<PhysAddress> macAddresses) {
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInstance.getElanInstanceName());
        for (L2GatewayDevice l2GatewayDevice : elanL2GwDevices.values()) {
            removeRemoteUcastMacsFromExternalDevice(l2GatewayDevice.getHwvtepNodeId(),
                    elanInstance.getElanInstanceName(), macAddresses);
        }
    }

    /**
     * Removes the given MAC Addresses from the specified External Device.
     *
     * @param deviceNodeId
     *            the device node id
     * @param macAddresses
     *            the mac addresses
     * @return the listenable future
     */
    private FluentFuture<? extends @NonNull CommitInfo> removeRemoteUcastMacsFromExternalDevice(String deviceNodeId,
            String logicalSwitchName, List<PhysAddress> macAddresses) {
        NodeId nodeId = new NodeId(deviceNodeId);

        // TODO (eperefr)
        List<MacAddress> lstMac = macAddresses.stream().filter(Objects::nonNull).map(
            physAddress -> new MacAddress(physAddress.getValue().toLowerCase(Locale.getDefault())))
            .collect(Collectors.toList());
        return HwvtepUtils.deleteRemoteUcastMacs(broker, nodeId, logicalSwitchName, lstMac);
    }

    @Nullable
    public ElanInstance getElanInstanceForUcastLocalMac(LocalUcastMacs localUcastMac) {
        Optional<LogicalSwitches> lsOpc = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL,
                (InstanceIdentifier<LogicalSwitches>) localUcastMac.getLogicalSwitchRef().getValue());
        if (lsOpc.isPresent()) {
            LogicalSwitches ls = lsOpc.get();
            // Logical switch name is Elan name
            String elanName = getElanFromLogicalSwitch(ls.getHwvtepNodeName().getValue());
            return elanInstanceCache.get(elanName).orElse(null);
        }
        return null;
    }

    /**
     * Install external device local macs in dpn.
     *
     * @param dpnId
     *            the dpn id
     * @param l2gwDeviceNodeId
     *            the l2gw device node id
     * @param elan
     *            the elan
     * @param interfaceName
     *            the interface name
     */
    public void installL2gwDeviceMacsInDpn(Uint64 dpnId, NodeId l2gwDeviceNodeId, ElanInstance elan,
            String interfaceName) {
        L2GatewayDevice l2gwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elan.getElanInstanceName(),
                l2gwDeviceNodeId.getValue());
        if (l2gwDevice == null) {
            LOG.debug("L2 gw device not found in elan cache for device name {}", l2gwDeviceNodeId.getValue());
            return;
        }

        installDmacFlowsOnDpn(dpnId, l2gwDevice, elan, interfaceName);
    }

    /**
     * Install dmac flows on dpn.
     *
     * @param dpnId
     *            the dpn id
     * @param l2gwDevice
     *            the l2gw device
     * @param elan
     *            the elan
     * @param interfaceName
     *            the interface name
     */
    public List<ListenableFuture<Void>> installDmacFlowsOnDpn(Uint64 dpnId, L2GatewayDevice l2gwDevice,
                                                              ElanInstance elan, String interfaceName) {
        String elanName = elan.getElanInstanceName();
        List<ListenableFuture<Void>> fts = new ArrayList<>();
        Collection<LocalUcastMacs> l2gwDeviceLocalMacs = l2gwDevice.getUcastLocalMacs();
        if (l2gwDeviceLocalMacs != null && !l2gwDeviceLocalMacs.isEmpty()) {
            for (LocalUcastMacs localUcastMac : l2gwDeviceLocalMacs) {
                fts.addAll(elanDmacUtils.installDmacFlowsToExternalRemoteMacInBatch(dpnId, l2gwDevice.getHwvtepNodeId(),
                        elan.getElanTag().toJava(), ElanUtils.getVxlanSegmentationId(elan).longValue(),
                        localUcastMac.getMacEntryKey().getValue(), elanName, interfaceName));
            }
            LOG.debug("Installing L2gw device [{}] local macs [size: {}] in dpn [{}] for elan [{}]",
                    l2gwDevice.getHwvtepNodeId(), l2gwDeviceLocalMacs.size(), dpnId, elanName);
        }
        return fts;
    }

    /**
     * Install elan l2gw devices local macs in dpn.
     *
     * @param dpnId
     *            the dpn id
     * @param elan
     *            the elan
     * @param interfaceName
     *            the interface name
     */
    public void installElanL2gwDevicesLocalMacsInDpn(Uint64 dpnId, ElanInstance elan, String interfaceName) {
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevicesFromCache = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elan.getElanInstanceName());
        if (elanL2GwDevicesFromCache != null) {
            for (L2GatewayDevice l2gwDevice : elanL2GwDevicesFromCache.values()) {
                installDmacFlowsOnDpn(dpnId, l2gwDevice, elan, interfaceName);
            }
        } else {
            LOG.debug("No Elan l2 gateway devices in cache for [{}] ", elan.getElanInstanceName());
        }
    }

    public void installL2GwUcastMacInElan(final ElanInstance elan, final L2GatewayDevice extL2GwDevice,
            final String macToBeAdded, final LocalUcastMacs localUcastMacs, @Nullable String interfaceName) {
        final String extDeviceNodeId = extL2GwDevice.getHwvtepNodeId();
        final String elanInstanceName = elan.getElanInstanceName();
        final Collection<DpnInterfaces> elanDpns = getElanDpns(elanInstanceName);
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanInstanceName);

        // Retrieve all participating DPNs in this Elan. Populate this MAC in
        // DMAC table.
        // Looping through all DPNs in order to add/remove mac flows in their
        // DMAC table
        if (elanDpns.size() > 0 || elanL2GwDevices.values().size() > 0) {
            String jobKey = elanInstanceName + ":" + macToBeAdded;
            IpAddress extL2GwDeviceTepIp = extL2GwDevice.getTunnelIp();
            List<PhysAddress> macList = Lists.newArrayList(new PhysAddress(macToBeAdded));

            elanClusterUtils.runOnlyInOwnerNode(jobKey, "install l2gw macs in dmac table", () -> {
                if (doesLocalUcastMacExistsInCache(extL2GwDevice, localUcastMacs)) {
                    List<ListenableFuture<?>> futures = new ArrayList<>();
                    for (DpnInterfaces elanDpn : elanDpns) {
                        futures.addAll(elanDmacUtils.installDmacFlowsToExternalRemoteMacInBatch(elanDpn.getDpId(),
                                extDeviceNodeId, elan.getElanTag().toJava(),
                                ElanUtils.getVxlanSegmentationId(elan).longValue(),
                                macToBeAdded, elanInstanceName, interfaceName));
                    }
                    for (L2GatewayDevice otherDevice : elanL2GwDevices.values()) {
                        if (!otherDevice.getHwvtepNodeId().equals(extDeviceNodeId)
                                && !areMLAGDevices(extL2GwDevice, otherDevice)) {
                            final String hwvtepId = otherDevice.getHwvtepNodeId();
                            final String logicalSwitchName = elanInstanceName;
                            HwvtepUtils.installUcastMacs(
                                    broker, hwvtepId, macList, logicalSwitchName, extL2GwDeviceTepIp);
                        }
                    }
                    return futures;
                } else {
                    LOG.trace("Skipping install of dmac flows for mac {} as it is not found in cache",
                            macToBeAdded);
                }
                return Collections.emptyList();
            });
        }
    }

    /**
     * Does local ucast mac exists in cache.
     *
     * @param elanL2GwDevice
     *            the elan L2 Gw device
     * @param macAddress
     *            the mac address to be verified
     * @return true, if successful
     */
    private static boolean doesLocalUcastMacExistsInCache(L2GatewayDevice elanL2GwDevice, LocalUcastMacs macAddress) {
        return elanL2GwDevice.containsUcastMac(macAddress);
    }

    /**
     * Uninstall l2gw macs from other l2gw devices in the elanName provided.
     * @param elanName - Elan Name for which other l2gw devices will be scanned.
     * @param l2GwDevice - l2gwDevice whose macs are required to be cleared from other devices.
     * @param macAddresses - Mac address to be cleared.
     */
    public void unInstallL2GwUcastMacFromL2gwDevices(final String elanName,
                                                     final L2GatewayDevice l2GwDevice,
                                                     final Collection<MacAddress> macAddresses) {
        if (macAddresses == null || macAddresses.isEmpty()) {
            return;
        }

        if (elanName == null) {
            return;
        }

        DeleteL2GwDeviceMacsFromElanJob job = new DeleteL2GwDeviceMacsFromElanJob(elanName, l2GwDevice,
                macAddresses);
        elanClusterUtils.runOnlyInOwnerNode(job.getJobKey(), "delete remote ucast macs in l2gw devices", job);
    }

    /**
     * Uninstall l2gw macs from other DPNs in the elan instance provided.
     * @param elan - Elan Instance for which other DPNs will be scanned.
     * @param l2GwDevice - l2gwDevice whose macs are required to be cleared from other devices.
     * @param macAddresses - Mac address to be cleared.
     */
    public void unInstallL2GwUcastMacFromElanDpns(final ElanInstance elan, final L2GatewayDevice l2GwDevice,
                                                  final Collection<MacAddress> macAddresses) {
        if (macAddresses == null || macAddresses.isEmpty()) {
            return;
        }
        if (elan == null || elan.getElanInstanceName() == null) {
            LOG.error("Could not delete l2gw ucast macs, Failed to find the elan for device {}",
                    l2GwDevice.getHwvtepNodeId());
            return;
        }

        final Collection<DpnInterfaces> elanDpns = getElanDpns(elan.getElanInstanceName());

        // Retrieve all participating DPNs in this Elan. Populate this MAC in
        // DMAC table. Looping through all DPNs in order to add/remove mac flows
        // in their DMAC table
        List<ListenableFuture<Void>> result = new ArrayList<>();
        for (final MacAddress mac : macAddresses) {
            elanClusterUtils.runOnlyInOwnerNode(elan.getElanInstanceName() + ":" + mac.getValue(),
                    "delete remote ucast macs in elan DPNs", () -> {
                    for (DpnInterfaces elanDpn : elanDpns) {
                        Uint64 dpnId = elanDpn.getDpId();
                        result.addAll(elanDmacUtils.deleteDmacFlowsToExternalMac(elan.getElanTag().toJava(), dpnId,
                            l2GwDevice.getHwvtepNodeId(),
                            IetfYangUtil.INSTANCE.canonizeMacAddress(mac).getValue()));
                    }
                    return result;
                });
        }
    }

    /**
     * Delete elan l2 gateway devices ucast local macs from dpn.
     *
     * @param elanName
     *            the elan name
     * @param dpnId
     *            the dpn id
     */
    public void deleteElanL2GwDevicesUcastLocalMacsFromDpn(final String elanName, final Uint64 dpnId) {
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices = ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName);
        if (elanL2GwDevices == null || elanL2GwDevices.isEmpty()) {
            LOG.trace("No L2 gateway devices in Elan [{}] cache.", elanName);
            return;
        }
        final ElanInstance elan = elanInstanceCache.get(elanName).orElse(null);
        if (elan == null) {
            LOG.error("Could not find Elan by name: {}", elanName);
            return;
        }
        LOG.info("Deleting Elan [{}] L2GatewayDevices UcastLocalMacs from Dpn [{}]", elanName, dpnId);

        final Long elanTag = elan.getElanTag().toJava();
        for (final L2GatewayDevice l2GwDevice : elanL2GwDevices.values()) {
            getL2GwDeviceLocalMacsAndRunCallback(elan.getElanInstanceName(), l2GwDevice, (localMacs) -> {
                for (MacAddress mac : localMacs) {
                    String jobKey = elanName + ":" + mac.getValue();
                    elanClusterUtils.runOnlyInOwnerNode(jobKey,
                        () -> elanDmacUtils.deleteDmacFlowsToExternalMac(elanTag, dpnId,
                                l2GwDevice.getHwvtepNodeId(), mac.getValue()));
                }
                return null;
            });
        }
    }

    public void getL2GwDeviceLocalMacsAndRunCallback(String elanName, L2GatewayDevice l2gwDevice,
                                                     Function<Collection<MacAddress>, Void> function) {
        if (l2gwDevice == null) {
            return;
        }
        Set<MacAddress> macs = new HashSet<>();
        Collection<LocalUcastMacs> lstUcastLocalMacs = l2gwDevice.getUcastLocalMacs();
        if (!lstUcastLocalMacs.isEmpty()) {
            macs.addAll(lstUcastLocalMacs.stream().filter(Objects::nonNull)
                    .map(mac -> new MacAddress(mac.getMacEntryKey().getValue().toLowerCase(Locale.getDefault())))
                    .collect(Collectors.toList()));
        }

        InstanceIdentifier<Node> nodeIid = HwvtepSouthboundUtils.createInstanceIdentifier(
                new NodeId(l2gwDevice.getHwvtepNodeId()));
        Futures.addCallback(broker.newReadOnlyTransaction().read(LogicalDatastoreType.CONFIGURATION, nodeIid),
                new FutureCallback<Optional<Node>>() {
                    @Override
                    public void onSuccess(Optional<Node> configNode) {
                        if (configNode != null && configNode.isPresent()) {
                            HwvtepGlobalAugmentation augmentation = configNode.get().augmentation(
                                    HwvtepGlobalAugmentation.class);
                            if (augmentation != null && augmentation.nonnullLocalUcastMacs() != null) {
                                macs.addAll(augmentation.nonnullLocalUcastMacs().values().stream()
                                        .filter(mac -> getLogicalSwitchName(mac).equals(elanName))
                                        .map(mac -> mac.getMacEntryKey())
                                        .collect(Collectors.toSet()));
                            }
                            function.apply(macs);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        LOG.error("Failed to read config topology node {}", nodeIid);
                    }
                }, MoreExecutors.directExecutor());
    }

    private String getLogicalSwitchName(LocalUcastMacs mac) {
        return ((InstanceIdentifier<LogicalSwitches>)mac.getLogicalSwitchRef().getValue())
                .firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    /**
     * Delete elan macs from L2 gateway device.<br>
     * This includes deleting ELAN mac table entries plus external device
     * UcastLocalMacs which are part of the same ELAN.
     *
     * @param hwvtepNodeId
     *            the hwvtepNodeId
     * @param elanName
     *            the elan name
     * @return the listenable future
     */
    public FluentFuture<? extends @NonNull CommitInfo> deleteElanMacsFromL2GatewayDevice(String hwvtepNodeId,
        String elanName) {

        String logicalSwitch = getLogicalSwitchFromElan(elanName);
        List<MacAddress> lstElanMacs = getRemoteUcastMacs(new NodeId(hwvtepNodeId), logicalSwitch,
                LogicalDatastoreType.CONFIGURATION);
        if (LOG.isDebugEnabled()) {
            List<String> elanMacs = lstElanMacs.stream().map(MacAddress::getValue).collect(Collectors.toList());
            LOG.debug("Deleting elan [{}] macs from node [{}]. Deleted macs = {}", elanName, hwvtepNodeId, elanMacs);
        }
        return HwvtepUtils.deleteRemoteUcastMacs(broker, new NodeId(hwvtepNodeId),
                logicalSwitch, lstElanMacs);
    }

    /**
     * Gets the remote ucast macs from hwvtep node filtering based on logical
     * switch.
     *
     * @param hwvtepNodeId
     *            the hwvtep node id
     * @param logicalSwitch
     *            the logical switch
     * @param datastoreType
     *            the datastore type
     * @return the remote ucast macs
     */
    public List<MacAddress> getRemoteUcastMacs(NodeId hwvtepNodeId, String logicalSwitch,
            LogicalDatastoreType datastoreType) {
        List<MacAddress> lstMacs = Collections.emptyList();
        Node hwvtepNode = null;
        try {
            hwvtepNode = HwvtepUtils.getHwVtepNode(broker, datastoreType, hwvtepNodeId);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception While Reading Node {}", hwvtepNodeId, e);
        }
        if (hwvtepNode != null) {
            Collection<RemoteUcastMacs> remoteUcastMacs = hwvtepNode.augmentation(HwvtepGlobalAugmentation.class)
                .nonnullRemoteUcastMacs().values();
            if (remoteUcastMacs != null && !remoteUcastMacs.isEmpty()) {
                // Filtering remoteUcastMacs based on the logical switch and
                // forming a list of MacAddress
                lstMacs = remoteUcastMacs.stream()
                        .filter(mac -> logicalSwitch.equals(mac.getLogicalSwitchRef().getValue()
                                .firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue()))
                        .map(HwvtepMacTableGenericAttributes::getMacEntryKey).collect(Collectors.toList());
            }
        }
        return lstMacs;
    }

    /**
     * Install ELAN macs in L2 Gateway device.<br>
     * This includes installing ELAN mac table entries plus external device
     * UcastLocalMacs which are part of the same ELAN.
     *
     * @param elanName
     *            the elan name
     * @param l2GatewayDevice
     *            the l2 gateway device which has to be configured
     * @return the listenable future
     */
    public FluentFuture<? extends CommitInfo> installElanMacsInL2GatewayDevice(String elanName,
            L2GatewayDevice l2GatewayDevice) {
        String logicalSwitchName = getLogicalSwitchFromElan(elanName);
        NodeId hwVtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());

        List<RemoteUcastMacs> lstL2GatewayDevicesMacs = getOtherDevicesMacs(elanName, l2GatewayDevice, hwVtepNodeId,
                logicalSwitchName);
        List<RemoteUcastMacs> lstElanMacTableEntries = getElanMacTableEntriesMacs(elanName,
                hwVtepNodeId, logicalSwitchName);

        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<>(lstL2GatewayDevicesMacs);
        lstRemoteUcastMacs.addAll(lstElanMacTableEntries);

        FluentFuture<? extends CommitInfo> future = HwvtepUtils.addRemoteUcastMacs(broker, hwVtepNodeId,
            lstRemoteUcastMacs);

        LOG.info("Added RemoteUcastMacs entries [{}] in config DS. NodeID: {}, LogicalSwitch: {}",
                lstRemoteUcastMacs.size(), hwVtepNodeId.getValue(), logicalSwitchName);
        return future;
    }

    /**
     * Gets the l2 gateway devices ucast local macs as remote ucast macs.
     *
     * @param elanName
     *            the elan name
     * @param l2GatewayDeviceToBeConfigured
     *            the l2 gateway device to be configured
     * @param hwVtepNodeId
     *            the hw vtep node Id to be configured
     * @param logicalSwitchName
     *            the logical switch name
     * @return the l2 gateway devices macs as remote ucast macs
     */
    public static List<RemoteUcastMacs> getOtherDevicesMacs(String elanName,
            L2GatewayDevice l2GatewayDeviceToBeConfigured, NodeId hwVtepNodeId, String logicalSwitchName) {
        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<>();
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevicesFromCache = ElanL2GwCacheUtils
                .getInvolvedL2GwDevices(elanName);

        if (elanL2GwDevicesFromCache != null) {
            for (L2GatewayDevice otherDevice : elanL2GwDevicesFromCache.values()) {
                if (l2GatewayDeviceToBeConfigured.getHwvtepNodeId().equals(otherDevice.getHwvtepNodeId())) {
                    continue;
                }
                if (!areMLAGDevices(l2GatewayDeviceToBeConfigured, otherDevice)) {
                    for (LocalUcastMacs localUcastMac : otherDevice.getUcastLocalMacs()) {
                        HwvtepPhysicalLocatorAugmentation physLocatorAug = HwvtepSouthboundUtils
                                .createHwvtepPhysicalLocatorAugmentation(otherDevice.getTunnelIp().stringValue());
                        RemoteUcastMacs remoteUcastMac = HwvtepSouthboundUtils.createRemoteUcastMac(hwVtepNodeId,
                                localUcastMac.getMacEntryKey().getValue().toLowerCase(Locale.getDefault()),
                                localUcastMac.getIpaddr(), logicalSwitchName, physLocatorAug);
                        lstRemoteUcastMacs.add(remoteUcastMac);
                    }
                }
            }
        }
        return lstRemoteUcastMacs;
    }

    /**
     * Are MLAG devices.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @param otherL2GatewayDevice
     *            the other l2 gateway device
     * @return true, if both the specified l2 gateway devices are part of same
     *         MLAG
     */
    public static boolean areMLAGDevices(L2GatewayDevice l2GatewayDevice, L2GatewayDevice otherL2GatewayDevice) {
        // If tunnel IPs are same, then it is considered to be part of same MLAG
        return Objects.equals(l2GatewayDevice.getTunnelIp(), otherL2GatewayDevice.getTunnelIp());
    }

    /**
     * Gets the elan mac table entries as remote ucast macs. <br>
     * Note: ELAN MAC table only contains internal switches MAC's. It doesn't
     * contain external device MAC's.
     *
     * @param elanName
     *            the elan name
     * @param hwVtepNodeId
     *            the hw vtep node id
     * @param logicalSwitchName
     *            the logical switch name
     * @return the elan mac table entries as remote ucast macs
     */
    public List<RemoteUcastMacs> getElanMacTableEntriesMacs(String elanName,
            NodeId hwVtepNodeId, String logicalSwitchName) {
        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<>();

        MacTable macTable = ElanUtils.getElanMacTable(broker, elanName);
        if (macTable == null || macTable.getMacEntry() == null || macTable.getMacEntry().isEmpty()) {
            LOG.trace("MacTable is empty for elan: {}", elanName);
            return lstRemoteUcastMacs;
        }

        for (MacEntry macEntry : macTable.nonnullMacEntry().values()) {
            Uint64 dpnId = getDpidFromInterface(macEntry.getInterface());
            if (dpnId == null) {
                LOG.error("DPN ID not found for interface {}", macEntry.getInterface());
                continue;
            }

            IpAddress dpnTepIp = elanItmUtils.getSourceDpnTepIp(dpnId, hwVtepNodeId);
            LOG.trace("Dpn Tep IP: {} for dpnId: {} and nodeId: {}", dpnTepIp, dpnId, hwVtepNodeId.getValue());
            if (dpnTepIp == null) {
                LOG.error("TEP IP not found for dpnId {} and nodeId {}", dpnId, hwVtepNodeId.getValue());
                continue;
            }
            HwvtepPhysicalLocatorAugmentation physLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(dpnTepIp);
            // TODO: Query ARP cache to get IP address corresponding to the
            // MAC
            RemoteUcastMacs remoteUcastMac = HwvtepSouthboundUtils.createRemoteUcastMac(hwVtepNodeId,
                    IetfYangUtil.INSTANCE.canonizePhysAddress(macEntry.getMacAddress()).getValue(), null /*IpAddress*/,
                    logicalSwitchName, physLocatorAug);
            lstRemoteUcastMacs.add(remoteUcastMac);
        }
        return lstRemoteUcastMacs;
    }

    /**
     * Gets the dpid from interface.
     *
     * @param interfaceName
     *            the interface name
     * @return the dpid from interface
     */
    public Uint64 getDpidFromInterface(String interfaceName) {
        Uint64 dpId = null;
        Future<RpcResult<GetDpidFromInterfaceOutput>> output = interfaceManagerRpcService
                .getDpidFromInterface(new GetDpidFromInterfaceInputBuilder().setIntfName(interfaceName).build());
        try {
            RpcResult<GetDpidFromInterfaceOutput> rpcResult = output.get();
            if (rpcResult != null && rpcResult.isSuccessful()) {
                dpId = rpcResult.getResult().getDpid();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to get the DPN ID for interface: {} ", interfaceName, e);
        }
        return dpId;
    }

    /**
     * Update vlan bindings in l2 gateway device.
     *
     * @param nodeId
     *            the node id
     * @param psName
     *            the physical switch name
     * @param interfaceName
     *            the interface in physical switch
     * @param vlanBindings
     *            the vlan bindings to be configured
     * @return the listenable future
     */
    public FluentFuture<?> updateVlanBindingsInL2GatewayDevice(NodeId nodeId, String psName,
                                                                      String interfaceName,
                                                                      List<VlanBindings> vlanBindings) {
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            mergeVlanBindings(tx, nodeId, psName, interfaceName, vlanBindings, false);
            LOG.info("Updated Hwvtep VlanBindings in config DS. NodeID: {}", nodeId.getValue());
        });
    }

    /**
     * Update vlan bindings in l2 gateway device.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param hwVtepDevice
     *            the hardware device
     * @param defaultVlanId
     *            the default vlan id
     * @return the listenable future
     */
    public FluentFuture<?> updateVlanBindingsInL2GatewayDevice(NodeId nodeId, String logicalSwitchName,
            Devices hwVtepDevice, Integer defaultVlanId) {
        if (hwVtepDevice == null || hwVtepDevice.getInterfaces() == null || hwVtepDevice.getInterfaces().isEmpty()) {
            String errMsg = "HwVtepDevice is null or interfaces are empty.";
            LOG.error(errMsg);
            return FluentFutures.immediateFailedFluentFuture(new RuntimeException(errMsg));
        }
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712
                .l2gateway.attributes.devices.Interfaces deviceInterface : hwVtepDevice
                .nonnullInterfaces().values()) {
                //Removed the check for checking terminationPoint present in OP or not
                //for coniguring vlan bindings
                //As we are not any more dependent on it , plugin takes care of this
                // with port reconcilation.
                List<VlanBindings> vlanBindings = new ArrayList<>();
                if (deviceInterface.getSegmentationIds() != null && !deviceInterface
                    .getSegmentationIds().isEmpty()) {
                    for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                        vlanBindings.add(HwvtepSouthboundUtils
                            .createVlanBinding(nodeId, vlanId, logicalSwitchName));
                    }
                } else {
                    // Use defaultVlanId (specified in L2GatewayConnection) if Vlan
                    // ID not specified at interface level.
                    vlanBindings.add(HwvtepSouthboundUtils
                        .createVlanBinding(nodeId, defaultVlanId, logicalSwitchName));
                }

                TerminationPointKey tpKey = new TerminationPointKey(
                    new TpId(deviceInterface.getInterfaceName()));
                InstanceIdentifier<TerminationPoint> portIid = HwvtepSouthboundUtils
                    .createTerminationPointId(HwvtepSouthboundUtils.createManagedNodeId(nodeId,
                        hwVtepDevice.getDeviceName()), tpKey);

                try {
                    boolean createParent = false;
                    if (!portsCache.get(portIid).isPresent()) {
                        //create port
                        //pass additional flag
                        createParent = true;
                    }
                    mergeVlanBindings(tx, nodeId, hwVtepDevice.getDeviceName(),
                        deviceInterface.getInterfaceName(), vlanBindings, createParent);
                } catch (ReadFailedException e) {
                    LOG.error("Read Failed for PortIid {} {}", portIid, e.getMessage());
                }
            }
            LOG.info("Updated Hwvtep VlanBindings in config DS. NodeID: {}, LogicalSwitch: {}",
                nodeId.getValue(),logicalSwitchName);
        });
    }

    private static void mergeVlanBindings(TypedWriteTransaction tx, NodeId nodeId, String phySwitchName,
                                         String phyPortName, List<VlanBindings> vlanBindings,
                                         boolean createParent) {
        NodeId physicalSwitchNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, phySwitchName);
        mergeVlanBindings(tx, physicalSwitchNodeId, phyPortName, vlanBindings, createParent);
    }

    private static void mergeVlanBindings(TypedWriteTransaction tx, NodeId physicalSwitchNodeId,
                                         String phyPortName, List<VlanBindings> vlanBindings,
                                         boolean createParent) {
        HwvtepPhysicalPortAugmentation phyPortAug = (new HwvtepPhysicalPortAugmentationBuilder())
                .setHwvtepNodeName(new HwvtepNodeName(phyPortName)).setVlanBindings(vlanBindings).build();
        InstanceIdentifier<HwvtepPhysicalPortAugmentation> iid = HwvtepSouthboundUtils
                .createPhysicalPortInstanceIdentifier(physicalSwitchNodeId, phyPortName);
        if (createParent) {
            InstanceIdentifier<TerminationPoint> iid2 =
                    createPhysicalPortInstanceIdentifier(physicalSwitchNodeId, phyPortName);
            TerminationPointBuilder tpBuilder = new TerminationPointBuilder().setTpId(new TpId(phyPortName))
                    .addAugmentation(phyPortAug);
            tx.merge(iid2, tpBuilder.build());
        } else {
            tx.merge(iid, phyPortAug);
        }
    }

    public static InstanceIdentifier<TerminationPoint> createPhysicalPortInstanceIdentifier(
            NodeId physicalSwitchNodeId, String phyPortName) {
        return createInstanceIdentifier(physicalSwitchNodeId).child(TerminationPoint.class,
                new TerminationPointKey(new TpId(phyPortName)));
    }

    public static InstanceIdentifier<Node> createInstanceIdentifier(NodeId nodeId) {
        return InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class, new NodeKey(nodeId));
    }

    /**
     * Delete vlan bindings from l2 gateway device.
     *
     * @param nodeId
     *            the node id
     * @param hwVtepDevice
     *            the hw vtep device
     * @param defaultVlanId
     *            the default vlan id
     * @return the listenable future
     */
    public ListenableFuture<?> deleteVlanBindingsFromL2GatewayDevice(NodeId nodeId, Devices hwVtepDevice,
            Integer defaultVlanId) {
        if (hwVtepDevice == null || hwVtepDevice.getInterfaces() == null || hwVtepDevice.getInterfaces().isEmpty()) {
            String errMsg = "HwVtepDevice is null or interfaces are empty.";
            LOG.error(errMsg);
            return Futures.immediateFailedFuture(new RuntimeException(errMsg));
        }
        NodeId physicalSwitchNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, hwVtepDevice.getDeviceName());

        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712
                .l2gateway.attributes.devices.Interfaces deviceInterface : hwVtepDevice.nonnullInterfaces().values()) {
                String phyPortName = deviceInterface.getInterfaceName();
                if (deviceInterface.getSegmentationIds() != null && !deviceInterface.getSegmentationIds().isEmpty()) {
                    for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                        HwvtepUtils.deleteVlanBinding(tx, physicalSwitchNodeId, phyPortName, vlanId);
                    }
                } else {
                    // Use defaultVlanId (specified in L2GatewayConnection) if Vlan
                    // ID not specified at interface level.
                    HwvtepUtils.deleteVlanBinding(tx, physicalSwitchNodeId, phyPortName, defaultVlanId);
                }
            }
            LOG.info("Deleted Hwvtep VlanBindings from config DS. NodeID: {}, hwVtepDevice: {}, defaultVlanId: {} ",
                    nodeId.getValue(), hwVtepDevice, defaultVlanId);
        });
    }

    /**
     * Gets the elan name from logical switch name.
     *
     * @param logicalSwitchName
     *            the logical switch name
     * @return the elan name from logical switch name
     */
    public static String getElanFromLogicalSwitch(String logicalSwitchName) {
        // Assuming elan name is same as logical switch name
        String elanName = logicalSwitchName;
        return elanName;
    }

    /**
     * Gets the logical switch name from elan name.
     *
     * @param elanName
     *            the elan name
     * @return the logical switch from elan name
     */
    public static String getLogicalSwitchFromElan(String elanName) {
        // Assuming logical switch name is same as elan name
        String logicalSwitchName = elanName;
        return logicalSwitchName;
    }

    /**
     * Gets the l2 gateway connection job key.
     *
     * @param logicalSwitchName
     *            the logical switch name
     * @return the l2 gateway connection job key
     */
    public static String getL2GatewayConnectionJobKey(String logicalSwitchName) {
        return logicalSwitchName;
    }

    public static InstanceIdentifier<Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> interfaceInstanceIdentifierBuilder = InstanceIdentifier
                .builder(Interfaces.class).child(Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    @Nullable
    public static Interface getInterfaceFromConfigDS(InterfaceKey interfaceKey, DataBroker dataBroker) {
        InstanceIdentifier<Interface> interfaceId = getInterfaceIdentifier(interfaceKey);
        try {
            return SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, interfaceId).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            // TODO remove this, and propagate ReadFailedException instead of re-throw RuntimeException
            LOG.error("getInterfaceFromConfigDS({}) failed", interfaceKey, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete l2 gateway device ucast local macs from elan.<br>
     * Deletes macs from internal ELAN nodes and also on rest of external l2
     * gateway devices which are part of the ELAN.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device whose ucast local macs to be deleted
     *            from elan
     * @param elanName
     *            the elan name
     */
    public void deleteL2GwDeviceUcastLocalMacsFromElan(L2GatewayDevice l2GatewayDevice,
            String elanName) {
        LOG.info("Deleting L2GatewayDevice [{}] UcastLocalMacs from elan [{}]", l2GatewayDevice.getHwvtepNodeId(),
                elanName);

        ElanInstance elan = elanInstanceCache.get(elanName).orElse(null);
        if (elan == null) {
            LOG.error("Could not find Elan by name: {}", elanName);
            return;
        }

        Collection<MacAddress> localMacs = getL2GwDeviceLocalMacs(elanName, l2GatewayDevice);
        unInstallL2GwUcastMacFromL2gwDevices(elanName, l2GatewayDevice, localMacs);
        unInstallL2GwUcastMacFromElanDpns(elan, l2GatewayDevice, localMacs);
    }

    public static void createItmTunnels(DataBroker dataBroker, ItmRpcService itmRpcService,
                                        String hwvtepId, String psName, IpAddress tunnelIp) {
        AddL2GwDeviceInputBuilder builder = new AddL2GwDeviceInputBuilder();
        builder.setTopologyId(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID.getValue());
        builder.setNodeId(HwvtepSouthboundUtils.createManagedNodeId(new NodeId(hwvtepId), psName).getValue());
        builder.setIpAddress(tunnelIp);
        try {
            deleteStaleTunnelsOfHwvtepInITM(dataBroker, itmRpcService, hwvtepId, psName, tunnelIp);
            RpcResult<AddL2GwDeviceOutput> rpcResult = itmRpcService.addL2GwDevice(builder.build()).get();
            if (rpcResult.isSuccessful()) {
                LOG.info("Created ITM tunnels for {}", hwvtepId);
            } else {
                LOG.error("Failed to create ITM Tunnels: {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("RPC to create ITM tunnels failed", e);
        }
    }

    private static void deleteStaleTunnelsOfHwvtepInITM(DataBroker dataBroker,
                                                        ItmRpcService itmRpcService,
                                                        String globalNodeId,
                                                        String psName,
                                                        IpAddress tunnelIp) {
        try {
            Optional<TransportZones> tzonesoptional = readTransportZone(dataBroker);
            if (!tzonesoptional.isPresent() || tzonesoptional.get().getTransportZone() == null) {
                return;
            }
            String psNodeId = globalNodeId + HwvtepHAUtil.PHYSICALSWITCH + psName;
            tzonesoptional.get().nonnullTransportZone().stream()
                .filter(zone -> zone.getDeviceVteps() != null)
                .flatMap(zone -> new ArrayList<>(zone.nonnullDeviceVteps().values()).stream())
                .filter(deviceVteps -> Objects.equals(getPsName(deviceVteps), psName)) //get device with same ps name
                .filter(deviceVteps -> !Objects.equals(psNodeId, deviceVteps.getNodeId())
                        || !Objects.equals(tunnelIp, deviceVteps.getIpAddress()))//node id or tunnel ip is changed
                .forEach(deviceVteps -> deleteStaleL2gwTep(dataBroker, itmRpcService, deviceVteps));
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Failed delete stale tunnels for {}", globalNodeId);
        }
    }

    private static Optional<TransportZones> readTransportZone(DataBroker dataBroker) throws ExecutionException,
            InterruptedException {
        return new SingleTransactionDataBroker(dataBroker).syncReadOptional(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(TransportZones.class).build());
    }

    private static String getPsName(DeviceVteps deviceVteps) {
        return HwvtepHAUtil.getPsName(HwvtepHAUtil.convertToInstanceIdentifier(deviceVteps.getNodeId()));
    }

    private static void deleteStaleL2gwTep(DataBroker dataBroker,
                                            ItmRpcService itmRpcService,
                                            DeviceVteps deviceVteps) {
        String psName = HwvtepHAUtil.getPsName(HwvtepHAUtil.convertToInstanceIdentifier(deviceVteps.getNodeId()));
        String globalNodeId = HwvtepHAUtil.convertToGlobalNodeId(deviceVteps.getNodeId());
        try {
            LOG.info("Deleting stale tep {} ", deviceVteps);
            L2GatewayUtils.deleteItmTunnels(itmRpcService, globalNodeId, psName, deviceVteps.getIpAddress());
            Optional<ElanInstances> optionalElan = readElanInstances(dataBroker);
            if (!optionalElan.isPresent()) {
                return;
            }
            LoggingFutures.addErrorLogging(
                new ManagedNewTransactionRunnerImpl(dataBroker).callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    tx -> optionalElan.get().nonnullElanInstance().values().stream()
                        .flatMap(elan -> elan.nonnullExternalTeps().values().stream()
                            .map(externalTep -> ElanL2GatewayMulticastUtils.buildExternalTepPath(
                                elan.getElanInstanceName(), externalTep.getTepIp())))
                        .filter(externalTepIid -> Objects.equals(
                            deviceVteps.getIpAddress(), externalTepIid.firstKeyOf(ExternalTeps.class).getTepIp()))
                        .peek(externalTepIid -> LOG.info("Deleting stale external tep {}", externalTepIid))
                        .forEach(tx::delete)), LOG,
                "Failed to delete stale external teps {}", deviceVteps);
            Thread.sleep(10000);//TODO remove the sleep currently it waits for interfacemgr to finish the cleanup
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Failed to delete stale l2gw tep {}", deviceVteps, e);
        }
    }

    private static Optional<ElanInstances> readElanInstances(DataBroker dataBroker) throws ExecutionException,
        InterruptedException {
        return new SingleTransactionDataBroker(dataBroker).syncReadOptional(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(ElanInstances.class).build());
    }
    /*public static void createItmTunnels(IpAddress tunnelIp, DataBroker dataBroker) {
        ManagedNewTransactionRunner txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            InstanceIdentifier<L2GatewayIp> iid = InstanceIdentifier
                .builder(L2GatewayIpList.class)
                .child(L2GatewayIp.class, new L2GatewayIpKey(tunnelIp))
                .build();
            tx.put(iid, new L2GatewayIpBuilder().setIpAddress(tunnelIp).build());
        });
    }

    public static FluentFuture<? extends CommitInfo> deleteItmTunnels(
            IpAddress tunnelIp, DataBroker dataBroker) {
        ManagedNewTransactionRunner txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(LogicalDatastoreType.CONFIGURATION, tx -> {
            InstanceIdentifier<L2GatewayIp> iid = InstanceIdentifier
                .builder(L2GatewayIpList.class)
                .child(L2GatewayIp.class, new L2GatewayIpKey(tunnelIp))
                .build();
            tx.delete(iid);
        });
    }*/

    public static String getNodeIdFromDpnId(BigInteger dpnId) {
        return MDSALUtil.NODE_PREFIX + MDSALUtil.SEPARATOR + dpnId.toString();
    }

    public void scheduleAddDpnMacInExtDevices(String elanName, Uint64 dpId,
            List<PhysAddress> staticMacAddresses) {
        ConcurrentMap<String, L2GatewayDevice> elanDevices = ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName);
        for (final L2GatewayDevice externalDevice : elanDevices.values()) {
            scheduleAddDpnMacsInExtDevice(elanName, dpId, staticMacAddresses, externalDevice);
        }
    }

    public FluentFuture<? extends @NonNull CommitInfo> scheduleAddDpnMacsInExtDevice(final String elanName, Uint64 dpId,
                                                                      final List<PhysAddress> staticMacAddresses,
                                                                      final L2GatewayDevice externalDevice) {
        NodeId nodeId = new NodeId(externalDevice.getHwvtepNodeId());
        final IpAddress dpnTepIp = elanItmUtils.getSourceDpnTepIp(dpId, nodeId);
        LOG.trace("Dpn Tep IP: {} for dpnId: {} and nodeId: {}", dpnTepIp, dpId, nodeId);
        if (dpnTepIp == null) {
            LOG.error("could not install dpn mac in l2gw TEP IP not found for dpnId {} and nodeId {}", dpId, nodeId);
            return null;
        }
        //TerminationPointKey tpKey = HwvtepSouthboundUtils.getTerminationPointKey(dpnTepIp
        // .getIpv4Address().getValue());
        //InstanceIdentifier<TerminationPoint> tpPath = HwvtepSouthboundUtils.createTerminationPointId(nodeId, tpKey);
        //TODO: to  be batched in genius
        return HwvtepUtils.installUcastMacs(broker, externalDevice.getHwvtepNodeId(), staticMacAddresses,
                elanName, dpnTepIp);

    }

    public void scheduleDeleteLogicalSwitch(NodeId hwvtepNodeId, String lsName) {
        scheduleDeleteLogicalSwitch(hwvtepNodeId, lsName, false);
    }

    public void scheduleDeleteLogicalSwitch(final NodeId hwvtepNodeId, final String lsName, final boolean clearUcast) {
        final Pair<NodeId, String> nodeIdLogicalSwitchNamePair = new ImmutablePair<>(hwvtepNodeId, lsName);
        logicalSwitchDeletedTasks.computeIfAbsent(nodeIdLogicalSwitchNamePair,
            (key) -> scheduler.getScheduledExecutorService().schedule(() -> {
                DeleteLogicalSwitchJob deleteLsJob = new DeleteLogicalSwitchJob(broker,
                        ElanL2GatewayUtils.this, hwvtepNodeId, lsName, clearUcast);
                jobCoordinator.enqueueJob(deleteLsJob.getJobKey(), deleteLsJob,
                        SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                deleteJobs.put(nodeIdLogicalSwitchNamePair, deleteLsJob);
                logicalSwitchDeletedTasks.remove(nodeIdLogicalSwitchNamePair);
            }, getLogicalSwitchDeleteDelaySecs(), TimeUnit.SECONDS));
    }

    public void cancelDeleteLogicalSwitch(final NodeId hwvtepNodeId, final String lsName) {
        Pair<NodeId, String> nodeIdLogicalSwitchNamePair = new ImmutablePair<>(hwvtepNodeId, lsName);
        ScheduledFuture logicalSwitchDeleteTask = logicalSwitchDeletedTasks.remove(nodeIdLogicalSwitchNamePair);
        if (logicalSwitchDeleteTask != null) {
            LOG.debug("Delete logical switch {} action on node {} cancelled", lsName, hwvtepNodeId);
            logicalSwitchDeleteTask.cancel(true);
            DeleteLogicalSwitchJob deleteLogicalSwitchJob = deleteJobs.remove(nodeIdLogicalSwitchNamePair);
            if (deleteLogicalSwitchJob != null) {
                deleteLogicalSwitchJob.cancel();
            }
        }
    }

    @NonNull
    public Collection<DpnInterfaces> getElanDpns(String elanName) {
        Collection<DpnInterfaces> dpnInterfaces = elanInstanceDpnsCache.get(elanName);
        if (!dpnInterfaces.isEmpty()) {
            return dpnInterfaces;
        }

        return elanUtils.getElanDPNByName(elanName);
    }

    /**
     * Gets the l2 gw device local macs.
     * @param elanName
     *            name of the elan
     * @param l2gwDevice
     *            the l2gw device
     * @return the l2 gw device local macs
     */
    public List<MacAddress> getL2GwDeviceLocalMacs(String elanName, L2GatewayDevice l2gwDevice) {
        Set<MacAddress> macs = new HashSet<>();
        if (l2gwDevice == null) {
            return Collections.emptyList();
        }
        Collection<LocalUcastMacs> lstUcastLocalMacs = l2gwDevice.getUcastLocalMacs();
        if (lstUcastLocalMacs != null && !lstUcastLocalMacs.isEmpty()) {
            macs.addAll(lstUcastLocalMacs.stream().filter(Objects::nonNull)
                    .map(mac -> new MacAddress(mac.getMacEntryKey().getValue().toLowerCase(Locale.getDefault())))
                    .collect(Collectors.toList()));
        }
        Optional<Node> configNode = null;
        try {
            configNode = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION,
                HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(l2gwDevice.getHwvtepNodeId())));
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getL2GwDeviceLocalMacs: Exception while reading l2gwDevice DS for the elan {}, l2gwDevice {}",
                elanName, l2gwDevice, e);
            return Collections.emptyList();
        }

        if (configNode.isPresent()) {
            HwvtepGlobalAugmentation augmentation = configNode.get().augmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null && augmentation.getLocalUcastMacs() != null) {
                macs.addAll(augmentation.nonnullLocalUcastMacs().values().stream()
                        .filter(mac -> getLogicalSwitchName(mac).equals(elanName))
                        .map(mac -> mac.getMacEntryKey())
                        .collect(Collectors.toSet()));
            }
        }
        return new ArrayList<>(macs);
    }
}
