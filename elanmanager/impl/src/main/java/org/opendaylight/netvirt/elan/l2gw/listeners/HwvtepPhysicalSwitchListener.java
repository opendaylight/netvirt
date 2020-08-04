/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.hwvtep.HwvtepAbstractDataTreeChangeListener;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.MdsalEvent;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2gwServiceProvider;
import org.opendaylight.netvirt.elan.l2gw.utils.StaleVlanBindingsCleaner;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener to handle physical switch updates.
 */
@Singleton
public class HwvtepPhysicalSwitchListener
        extends HwvtepAbstractDataTreeChangeListener<PhysicalSwitchAugmentation, HwvtepPhysicalSwitchListener>
        implements ClusteredDataTreeChangeListener<PhysicalSwitchAugmentation>, RecoverableListener {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepPhysicalSwitchListener.class);

    private static final BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> DEVICE_NOT_CACHED_OR_PARENT_CONNECTED =
        (l2GatewayDevice, globalIid) -> {
            return l2GatewayDevice == null || l2GatewayDevice.getHwvtepNodeId() == null
                    || !Objects.equals(l2GatewayDevice.getHwvtepNodeId(),
                            globalIid.firstKeyOf(Node.class).getNodeId().getValue());
        };

    private static final Predicate<PhysicalSwitchAugmentation> TUNNEL_IP_AVAILABLE =
        phySwitch -> !HwvtepHAUtil.isEmpty(phySwitch.nonnullTunnelIps().values());

    private static final Predicate<PhysicalSwitchAugmentation> TUNNEL_IP_NOT_AVAILABLE = TUNNEL_IP_AVAILABLE.negate();

    private static final BiPredicate<PhysicalSwitchAugmentation, L2GatewayDevice> TUNNEL_IP_CHANGED =
        (phySwitchAfter, existingDevice) -> {
            return TUNNEL_IP_AVAILABLE.test(phySwitchAfter)
                    && !Objects.equals(
                            existingDevice.getTunnelIp(),  phySwitchAfter.nonnullTunnelIps().get(0).getTunnelIpsKey());
        };

    /** The data broker. */
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;

    /** The itm rpc service. */
    private final ItmRpcService itmRpcService;

    private final ElanClusterUtils elanClusterUtils;

    private final HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    private final L2gwServiceProvider l2gwServiceProvider;

    private final BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> childConnectedAfterParent =
        (l2GwDevice, globalIid) -> {
            return !hwvtepHACache.isHAParentNode(globalIid)
                    && l2GwDevice != null;
                    // FIXME: The following call to equals compares different types (String and InstanceIdentifier) and
                    // thus will always return false. I don't know what the intention is here so commented out for now.
                    //&& !Objects.equals(l2GwDevice.getHwvtepNodeId(), globalIid);
        };

    private final Predicate<L2GatewayDevice> alreadyHasL2Gwids =
        (l2GwDevice) -> {
            return l2GwDevice != null && HwvtepHAUtil.isEmpty(l2GwDevice.getL2GatewayIds());
        };

    private final BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> parentConnectedAfterChild =
        (l2GwDevice, globalIid) -> {
            InstanceIdentifier<Node> existingIid = globalIid;
            if (l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null) {
                existingIid = HwvtepHAUtil.convertToInstanceIdentifier(l2GwDevice.getHwvtepNodeId());
            }
            return hwvtepHACache.isHAParentNode(globalIid)
                    && l2GwDevice != null
                    // FIXME: The following call to equals compares different types (String and InstanceIdentifier) and
                    // thus will always return false. I don't know what the intention is here so commented out for now.
                    //&& !Objects.equals(l2GwDevice.getHwvtepNodeId(), globalIid)
                    && Objects.equals(globalIid, hwvtepHACache.getParent(existingIid));
        };


    private final HAOpClusteredListener haOpClusteredListener;

    private final L2GatewayCache l2GatewayCache;

    private final StaleVlanBindingsCleaner staleVlanBindingsCleaner;

    private final L2GwTransportZoneListener transportZoneListener;

    /**
     * Instantiates a new hwvtep physical switch listener.
     * @param l2GatewayServiceRecoveryHandler L2GatewayServiceRecoveryHandler
     * @param serviceRecoveryRegistry ServiceRecoveryRegistry
     * @param dataBroker DataBroker
     * @param itmRpcService ItmRpcService
     * @param elanClusterUtils ElanClusterUtils
     * @param l2gwServiceProvider L2gwServiceProvider
     * @param haListener HAOpClusteredListener
     * @param l2GatewayCache L2GatewayCache
     * @param staleVlanBindingsCleaner StaleVlanBindingsCleaner
     */
    @Inject
    public HwvtepPhysicalSwitchListener(final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                        final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                        final DataBroker dataBroker, ItmRpcService itmRpcService,
                                        ElanClusterUtils elanClusterUtils, L2gwServiceProvider l2gwServiceProvider,
                                        HAOpClusteredListener haListener, L2GatewayCache l2GatewayCache,
                                        StaleVlanBindingsCleaner staleVlanBindingsCleaner,
                                        L2GwTransportZoneListener transportZoneListener) {

        super(dataBroker,  DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                .augmentation(PhysicalSwitchAugmentation.class)),
            Executors.newListeningSingleThreadExecutor("HwvtepPhysicalSwitchListener", LOG));

        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.itmRpcService = itmRpcService;
        this.elanClusterUtils = elanClusterUtils;
        this.l2gwServiceProvider = l2gwServiceProvider;
        this.staleVlanBindingsCleaner = staleVlanBindingsCleaner;
        this.haOpClusteredListener = haListener;
        this.l2GatewayCache = l2GatewayCache;
        this.transportZoneListener = transportZoneListener;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        //TODOD recover
    }

    @PostConstruct
    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        //RegisterListener is called from L2GatewayConnectionListener
        //registerListener();
    }

    @Override
    public void register() {
        LOG.info("Registering HwvtepPhysicalSwitchListener in Overwritten Method");
        super.register();
    }

    @Override
    public void registerListener() {
        LOG.info("Registering HwvtepPhysicalSwitchListener");
        super.register();
    }

    public void deregisterListener() {
        LOG.info("Deregistering HwvtepPhysicalSwitchListener");
        super.close();
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    protected void removed(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
            PhysicalSwitchAugmentation phySwitchDeleted) {
        NodeId nodeId = getNodeId(identifier);
        String psName = phySwitchDeleted.getHwvtepNodeName().getValue();
        LOG.info("Received physical switch {} removed event for node {}", psName, nodeId.getValue());

        L2GatewayDevice l2GwDevice = l2GatewayCache.get(psName);
        if (l2GwDevice != null) {
            if (!L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                l2GatewayCache.remove(psName);
                LOG.info("HwvtepPhysicalSwitchListener {} details removed from L2Gateway Cache", psName);
            } else {
                LOG.error("HwvtepPhysicalSwitchListener {} details are not removed from L2Gateway "
                        + " Cache as it has L2Gateway reference", psName);
            }

            l2GwDevice.setConnected(false);
            //ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(psName);
        } else {
            LOG.error("HwvtepPhysicalSwitchListener Unable to find L2 Gateway details for {}", psName);
        }
    }

    /**
     * Upon update checks if the tunnels Ip was null earlier and it got newly added.
     * In that case simply call add.
     * If not then check if Tunnel Ip has been updated from an old value to new value.
     * If yes. delete old ITM tunnels of odl Tunnel Ipand add new ITM tunnels with new Tunnel
     * IP then call added ().
     *
     * @param identifier iid
     * @param phySwitchBefore ps Node before update
     * @param phySwitchAfter ps Node after update
     */
    @Override
    protected void updated(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
            PhysicalSwitchAugmentation phySwitchBefore, PhysicalSwitchAugmentation phySwitchAfter) {
        NodeId nodeId = getNodeId(identifier);
        LOG.trace("Received PhysicalSwitch Update Event for node {}: PhysicalSwitch Before: {}, "
                + "PhysicalSwitch After: {}", nodeId.getValue(), phySwitchBefore, phySwitchAfter);
        String psName = getPsName(identifier);
        if (psName == null) {
            LOG.error("PhysicalSwitchListener Could not find the physical switch name for node {}", nodeId.getValue());
            return;
        }
        L2GatewayDevice existingDevice = l2GatewayCache.get(psName);
        if (!Objects.equals(phySwitchAfter.getTunnelIps(), phySwitchBefore.getTunnelIps())) {
            LOG.info("PhysicalSwitchListener Received physical switch update for {} before teps {} after teps {}",
                    nodeId.getValue(), phySwitchBefore.getTunnelIps(), phySwitchAfter.getTunnelIps());
        }
        InstanceIdentifier<Node> globalNodeIid = getManagedByNodeIid(identifier);

        if (DEVICE_NOT_CACHED_OR_PARENT_CONNECTED.test(existingDevice, globalNodeIid)) {
            if (TUNNEL_IP_AVAILABLE.test(phySwitchAfter)) {
                added(identifier, phySwitchAfter);
            }
        } else {
            if (!Objects.equals(phySwitchAfter.getTunnelIps(), phySwitchBefore.getTunnelIps())
                    && TUNNEL_IP_CHANGED.test(phySwitchAfter, existingDevice)) {

                final String hwvtepId = existingDevice.getHwvtepNodeId();
                elanClusterUtils.runOnlyInOwnerNode(existingDevice.getDeviceName(),
                    "handling Physical Switch add create itm tunnels ",
                    () -> {
                        LOG.info("PhysicalSwitchListener Deleting itm tunnels for {}", existingDevice.getDeviceName());
                        L2GatewayUtils.deleteItmTunnels(itmRpcService, hwvtepId,
                                existingDevice.getDeviceName(), existingDevice.getTunnelIp());
                        Thread.sleep(10000L);//TODO remove these sleeps
                        LOG.info("Creating itm tunnels for device {}", existingDevice.getDeviceName());
                        ElanL2GatewayUtils.createItmTunnels(dataBroker, itmRpcService, hwvtepId, psName,
                            phySwitchAfter.getTunnelIps().get(0).getTunnelIpsKey());
                        return Collections.emptyList();
                    }
                );
                try {
                    Thread.sleep(20000L);//TODO remove the sleep by using better itm api to detect finish of prev op
                } catch (InterruptedException e) {
                    LOG.error("Interrupted ");
                }
                existingDevice.setTunnelIps(new HashSet<>());
                added(identifier, phySwitchAfter);
            }
        }
    }

    @Override
    protected void added(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                         final PhysicalSwitchAugmentation phySwitchAdded) {
        String globalNodeId = getManagedByNodeId(identifier);
        final InstanceIdentifier<Node> globalNodeIid = getManagedByNodeIid(identifier);
        NodeId nodeId = getNodeId(identifier);
        if (TUNNEL_IP_NOT_AVAILABLE.test(phySwitchAdded)) {
            LOG.error("PhysicalSwitchListener Could not find the /tunnel ips for node {}", nodeId.getValue());
            return;
        }
        final String psName = getPsName(identifier);
        LOG.info("PhysicalSwitchListener Received physical switch added event received for node {} {}",
                nodeId.getValue(), phySwitchAdded.getTunnelIps());

        haOpClusteredListener.runAfterNodeIsConnected(globalNodeIid, (node) -> {
            LOG.info("PhysicalSwitchListener Global oper node found for {}", nodeId.getValue());
            if (!node.isPresent()) {
                LOG.error("PhysicalSwitchListener Global node is absent {}", globalNodeId);
                return;
            }
            HAOpClusteredListener.addToCacheIfHAChildNode(globalNodeIid, node.get());
            L2GatewayDevice l2GwDevice = l2GatewayCache.get(psName);
            if (hwvtepHACache.isHAEnabledDevice(globalNodeIid)) {
                InstanceIdentifier<Node> parent = hwvtepHACache.getParent(globalNodeIid);
                if (l2GwDevice == null || !Objects.equals(parent.firstKeyOf(Node.class).getNodeId().getValue(),
                        l2GwDevice.getHwvtepNodeId())) {
                    Collection<TunnelIps> tunnelIps = phySwitchAdded.nonnullTunnelIps().values();
                    if (tunnelIps != null && !tunnelIps.isEmpty()) {
                        l2GatewayCache.updateL2GatewayCache(psName,
                                parent.firstKeyOf(Node.class).getNodeId().getValue(),
                            new ArrayList<>(phySwitchAdded.nonnullTunnelIps().values()));
                    }
                    return;//TODO provision l2gw
                } else {
                    LOG.info("PhysicalSwitchListener Ha enabled device {} connected skip update cache", globalNodeIid);
                    return;
                }
            }
            LOG.info("PhysicalSwitchListener Updating cache for node {} existing {}",
                globalNodeId, (l2GwDevice != null ? l2GwDevice.getDeviceName() : null));
            if (childConnectedAfterParent.test(l2GwDevice, globalNodeIid)) {
                LOG.info("PhysicalSwitchListener Device {} {} is already Connected by {}",
                        psName, globalNodeId, l2GwDevice.getHwvtepNodeId());
                return;
            }
            InstanceIdentifier<Node> existingIid = globalNodeIid;
            if (l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null) {
                existingIid = HwvtepHAUtil.convertToInstanceIdentifier(l2GwDevice.getHwvtepNodeId());
            }
            if (parentConnectedAfterChild.test(l2GwDevice, globalNodeIid)
                    && alreadyHasL2Gwids.test(l2GwDevice)) {
                LOG.error("PhysicalSwitchListener Child node {} having l2gw configured became ha node "
                                + " removing the l2device {} from all elan cache and provision parent node {}",
                          existingIid, psName, globalNodeIid);
                ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(l2GwDevice.getHwvtepNodeId());
            }

            Collection<TunnelIps> tunnelIps = phySwitchAdded.nonnullTunnelIps().values();
            if (tunnelIps != null && !tunnelIps.isEmpty()) {
                l2GatewayCache.updateL2GatewayCache(psName, globalNodeId,
                    new ArrayList<>(phySwitchAdded.nonnullTunnelIps().values()));
                l2GwDevice = l2GatewayCache.get(psName);
                handleAdd(l2GwDevice, identifier, phySwitchAdded);
            }
            /*elanClusterUtils.runOnlyInOwnerNode(psName + ":" + "tunnelIp",
                    "Update config tunnels IP ", () -> {
                    List<ListenableFuture<Void>> result = new ArrayList<>();
                    try {
                        updateConfigTunnelIp(identifier, phySwitchAdded, result);
                    } catch (ReadFailedException e) {
                        LOG.error("PhysicalSwitchListener Failed to update tunnel ips {}", identifier);
                    }
                    return result;
                });
            */
            return;
        });
    }

    /**
     * Handle add.
     *
     * @param l2GwDevice
     *            the l2 gw device
     */
    private void handleAdd(L2GatewayDevice l2GwDevice,
                           InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                           PhysicalSwitchAugmentation phySwitchAdded) {
        LOG.info("PhysicalSwitchListener Handle add of tunnel ips {} psNode {} device {}",
                phySwitchAdded.getTunnelIps(), identifier.firstKeyOf(Node.class).getNodeId(), l2GwDevice);
        final String psName = l2GwDevice.getDeviceName();
        final String hwvtepNodeId = l2GwDevice.getHwvtepNodeId();
        Set<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
        if (tunnelIps != null) {
            //TODO add logical switch and mcast put itm tep event and update mcast
            hwvtepHACache.addDebugEvent(new MdsalEvent("ps add provision", l2GwDevice.getHwvtepNodeId()));
            for (final IpAddress tunnelIpAddr : tunnelIps) {
                if (L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                    LOG.info("PhysicalSwitchListener L2Gateway {} associated for {} physical switch "
                                    + " creating ITM tunnels for {}",
                            l2GwDevice.getL2GatewayIds(), psName, tunnelIpAddr);
                    l2gwServiceProvider.provisionItmAndL2gwConnection(l2GwDevice, psName, hwvtepNodeId, tunnelIpAddr);
                } else {
                    LOG.info("l2gw.provision.skip hwvtepNodeId: {} psName : {}", hwvtepNodeId, psName);
                }
            }
            InstanceIdentifier<Node> globalNodeIid = HwvtepSouthboundUtils.createInstanceIdentifier(
                    new NodeId(hwvtepNodeId));
            HwvtepHACache.getInstance().setTepIpOfNode(globalNodeIid, tunnelIps.iterator().next());
            elanClusterUtils.runOnlyInOwnerNode(psName, "Stale entry cleanup", () -> {
                InstanceIdentifier<Node> psIid = HwvtepSouthboundUtils.createInstanceIdentifier(
                        HwvtepSouthboundUtils.createManagedNodeId(new NodeId(hwvtepNodeId), psName));
                staleVlanBindingsCleaner.scheduleStaleCleanup(psName, globalNodeIid, psIid);
                transportZoneListener.createL2gwZeroDayConfig();
                return Collections.emptyList();
            });
        }
    }


    /**
     * Gets the node id.
     *
     * @param identifier
     *            the identifier
     * @return the node id
     */
    private static NodeId getNodeId(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        return identifier.firstKeyOf(Node.class).getNodeId();
    }

    private static String getManagedByNodeId(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return psNodeId.substring(0, psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
        }
        return psNodeId;
    }

    @Nullable
    private static InstanceIdentifier<Node> getManagedByNodeIid(
                        InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            psNodeId = psNodeId.substring(0, psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
            return identifier.firstIdentifierOf(Topology.class).child(Node.class, new NodeKey(new NodeId(psNodeId)));
        }
        return null;
    }

    @Nullable
    private static String getPsName(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH) + HwvtepHAUtil.PHYSICALSWITCH
                    .length());
        }
        return null;
    }

    /*private void updateConfigTunnelIp(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
                                                        PhysicalSwitchAugmentation phySwitchAdded,
                                                        List<ListenableFuture<Void>> result)
                                                        throws ReadFailedException {
        if (phySwitchAdded.getTunnelIps() != null) {
            result.add(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                Optional<PhysicalSwitchAugmentation> existingSwitch = tx.read(
                        LogicalDatastoreType.CONFIGURATION, identifier).checkedGet();
                PhysicalSwitchAugmentationBuilder psBuilder = new PhysicalSwitchAugmentationBuilder();
                if (existingSwitch.isPresent()) {
                    psBuilder = new PhysicalSwitchAugmentationBuilder(existingSwitch.get());
                }
                psBuilder.setTunnelIps(phySwitchAdded.getTunnelIps());
                tx.put(LogicalDatastoreType.CONFIGURATION, identifier, psBuilder.build(), true);
                LOG.trace("Updating config tunnel ips {}", identifier);
            }));
        }
    }*/
}
