/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.hwvtep.HwvtepAbstractDataTreeChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2gwServiceProvider;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
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
public class HwvtepPhysicalSwitchListener
        extends HwvtepAbstractDataTreeChangeListener<PhysicalSwitchAugmentation, HwvtepPhysicalSwitchListener>
        implements ClusteredDataTreeChangeListener<PhysicalSwitchAugmentation>, AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepPhysicalSwitchListener.class);

    /** The data broker. */
    private final DataBroker dataBroker;

    /** The itm rpc service. */
    private final ItmRpcService itmRpcService;

    private final EntityOwnershipUtils entityOwnershipUtils;

    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;

    private final HwvtepHACache hwvtepHACache = HwvtepHACache.getInstance();

    protected final L2gwServiceProvider l2gwServiceProvider;

    private final L2GatewayUtils l2GatewayUtils;

    static BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> DEVICE_NOT_CACHED_OR_PARENT_CONNECTED =
        (l2GatewayDevice, globalIid) -> {
            return l2GatewayDevice == null || l2GatewayDevice.getHwvtepNodeId() == null
                    || !Objects.equals(l2GatewayDevice.getHwvtepNodeId(),
                    globalIid.firstKeyOf(Node.class).getNodeId().getValue());
        };

    BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> childConnectedAfterParent =
        (l2GwDevice, globalIid) -> {
            return !hwvtepHACache.isHAParentNode(globalIid)
                    && l2GwDevice != null
                    && !Objects.equals(l2GwDevice.getHwvtepNodeId(), globalIid);
        };

    Predicate<L2GatewayDevice> alreadyHasL2Gwids =
        (l2GwDevice) -> {
            return l2GwDevice != null && HwvtepHAUtil.isEmpty(l2GwDevice.getL2GatewayIds());
        };


    BiPredicate<L2GatewayDevice, InstanceIdentifier<Node>> parentConnectedAfterChild =
        (l2GwDevice, globalIid) -> {
            InstanceIdentifier<Node> existingIid = globalIid;
            if (l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null) {
                existingIid = HwvtepHAUtil.convertToInstanceIdentifier(l2GwDevice.getHwvtepNodeId());
            }
            return hwvtepHACache.isHAParentNode(globalIid)
                    && l2GwDevice != null
                    && !Objects.equals(l2GwDevice.getHwvtepNodeId(), globalIid)
                    && Objects.equals(globalIid, hwvtepHACache.getParent(existingIid));
        };

    static Predicate<PhysicalSwitchAugmentation> TUNNEL_IP_AVAILABLE =
        phySwitch -> !HwvtepHAUtil.isEmpty(phySwitch.getTunnelIps());

    static Predicate<PhysicalSwitchAugmentation> TUNNEL_IP_NOT_AVAILABLE =
        TUNNEL_IP_AVAILABLE.negate();

    static BiPredicate<PhysicalSwitchAugmentation, L2GatewayDevice> TUNNEL_IP_CHANGED =
        (phySwitchAfter, existingDevice) -> {
            return TUNNEL_IP_AVAILABLE.test(phySwitchAfter)
                    && !Objects.equals(
                    existingDevice.getTunnelIp(),  phySwitchAfter.getTunnelIps().get(0).getTunnelIpsKey());
        };


    HAOpClusteredListener haOpClusteredListener;

    /**
     * Instantiates a new hwvtep physical switch listener.
     *
     * @param dataBroker
     *            the data broker
     * @param itmRpcService itm rpc
     * @param entityOwnershipUtils entity ownership utils
     * @param l2GatewayConnectionUtils l2gw connection utils
     * @param l2gwServiceProvider l2gw service Provider
     * @param l2GatewayUtils utils
     * @param haListener HA Op node listners
     */
    public HwvtepPhysicalSwitchListener(final DataBroker dataBroker, ItmRpcService itmRpcService,
                                        EntityOwnershipUtils entityOwnershipUtils,
                                        L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                        L2gwServiceProvider l2gwServiceProvider,
                                        L2GatewayUtils l2GatewayUtils, HAOpClusteredListener haListener) {
        super(PhysicalSwitchAugmentation.class, HwvtepPhysicalSwitchListener.class);
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.entityOwnershipUtils = entityOwnershipUtils;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.l2gwServiceProvider = l2gwServiceProvider;
        this.l2GatewayUtils = l2GatewayUtils;
        this.haOpClusteredListener = haListener;
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<PhysicalSwitchAugmentation> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                .augmentation(PhysicalSwitchAugmentation.class);
    }

    @Override
    protected HwvtepPhysicalSwitchListener getDataTreeChangeListener() {
        return HwvtepPhysicalSwitchListener.this;
    }

    @Override
    protected void removed(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
            PhysicalSwitchAugmentation phySwitchDeleted) {
        NodeId nodeId = getNodeId(identifier);
        String psName = phySwitchDeleted.getHwvtepNodeName().getValue();
        LOG.info("Received physical switch {} removed event for node {}", psName, nodeId.getValue());

        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
        if (l2GwDevice != null) {
            if (!L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                L2GatewayCacheUtils.removeL2DeviceFromCache(psName);
                LOG.debug("{} details removed from L2Gateway Cache", psName);
                MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                        HwvtepSouthboundUtils.createInstanceIdentifier(nodeId));
            } else {
                LOG.debug("{} details are not removed from L2Gateway Cache as it has L2Gateway reference", psName);
            }

            l2GwDevice.setConnected(false);
            //ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(psName);
        } else {
            LOG.error("Unable to find L2 Gateway details for {}", psName);
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
            LOG.error("Could not find the physical switch name for node {}", nodeId.getValue());
            return;
        }
        L2GatewayDevice existingDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
        LOG.info("Received physical switch {} update event for node {}", psName, nodeId.getValue());
        InstanceIdentifier<Node> globalNodeIid = getManagedByNodeIid(identifier);

        if (DEVICE_NOT_CACHED_OR_PARENT_CONNECTED.test(existingDevice, globalNodeIid)) {
            if (TUNNEL_IP_AVAILABLE.test(phySwitchAfter)) {
                added(identifier, phySwitchAfter);
            }
        } else {
            if (!Objects.equals(phySwitchAfter.getTunnelIps(), phySwitchBefore.getTunnelIps())
                    && TUNNEL_IP_CHANGED.test(phySwitchAfter, existingDevice)) {

                final String hwvtepId = existingDevice.getHwvtepNodeId();
                ElanClusterUtils.runOnlyInOwnerNode(entityOwnershipUtils, existingDevice.getDeviceName(),
                        "handling Physical Switch add create itm tunnels ", () -> {
                        LOG.info("Deleting itm tunnels for device {}", existingDevice.getDeviceName());
                        L2GatewayUtils.deleteItmTunnels(itmRpcService, hwvtepId,
                                existingDevice.getDeviceName(), existingDevice.getTunnelIp());
                        Thread.sleep(10000L);//TODO remove these sleeps
                        LOG.info("Creating itm tunnels for device {}", existingDevice.getDeviceName());
                        ElanL2GatewayUtils.createItmTunnels(itmRpcService, hwvtepId, psName,
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
        final InstanceIdentifier<Node> wildCard = globalNodeIid.firstIdentifierOf(Topology.class).child(Node.class);
        NodeId nodeId = getNodeId(identifier);
        if (TUNNEL_IP_NOT_AVAILABLE.test(phySwitchAdded)) {
            LOG.error("Could not find the /tunnel ips for node {}", nodeId.getValue());
            return;
        }
        final String psName = getPsName(identifier);
        LOG.trace("Received physical switch {} added event received for node {}", psName, nodeId.getValue());

        haOpClusteredListener.runAfterNodeIsConnected(globalNodeIid, (node) -> {
            LOG.trace("Running job for node {} ", globalNodeIid);
            if (!node.isPresent()) {
                LOG.error("Global node is absent {}", globalNodeId);
                return;
            }
            HAOpClusteredListener.addToCacheIfHAChildNode(globalNodeIid, node.get());
            if (hwvtepHACache.isHAEnabledDevice(globalNodeIid)) {
                LOG.trace("Ha enabled device {}", globalNodeIid);
                return;
            }
            LOG.trace("Updating cache for node {}", globalNodeIid);
            L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
            if (childConnectedAfterParent.test(l2GwDevice, globalNodeIid)) {
                LOG.trace("Device {} {} is already Connected by ",
                        psName, globalNodeId, l2GwDevice.getHwvtepNodeId());
                return;
            }
            InstanceIdentifier<Node> existingIid = globalNodeIid;
            if (l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null) {
                existingIid = HwvtepHAUtil.convertToInstanceIdentifier(l2GwDevice.getHwvtepNodeId());
            }
            if (parentConnectedAfterChild.test(l2GwDevice, globalNodeIid)
                    && alreadyHasL2Gwids.test(l2GwDevice)) {
                LOG.error("Child node {} having l2gw configured became ha node "
                                + " removing the l2device {} from all elan cache and provision parent node {}",
                          existingIid, psName, globalNodeIid);
                ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(l2GwDevice.getHwvtepNodeId());
            }
            l2GwDevice = L2GatewayCacheUtils.updateL2GatewayCache(
                    psName, globalNodeId, phySwitchAdded.getTunnelIps());
            handleAdd(l2GwDevice);
            return;
        });
    }

    boolean updateHACacheIfHANode(DataBroker broker, InstanceIdentifier<Node> globalNodeId)
            throws ExecutionException, InterruptedException {
        ReadWriteTransaction transaction = broker.newReadWriteTransaction();
        Node node = transaction.read(LogicalDatastoreType.OPERATIONAL, globalNodeId).get().get();
        HAOpClusteredListener.addToCacheIfHAChildNode(globalNodeId, node);
        return hwvtepHACache.isHAEnabledDevice(globalNodeId);
    }

    /**
     * Handle add.
     *
     * @param l2GwDevice
     *            the l2 gw device
     */
    private void handleAdd(L2GatewayDevice l2GwDevice) {
        final String psName = l2GwDevice.getDeviceName();
        final String hwvtepNodeId = l2GwDevice.getHwvtepNodeId();
        Set<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
        if (tunnelIps != null) {
            for (final IpAddress tunnelIpAddr : tunnelIps) {
                if (L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                    LOG.debug("L2Gateway {} associated for {} physical switch; creating ITM tunnels for {}",
                            l2GwDevice.getL2GatewayIds(), psName, tunnelIpAddr);
                    l2gwServiceProvider.provisionItmAndL2gwConnection(l2GwDevice, psName, hwvtepNodeId, tunnelIpAddr);
                } else {
                    LOG.info("l2gw.provision.skip {}", hwvtepNodeId, psName);
                }
            }
        }
    }


    /**
     * Gets the node id.
     *
     * @param identifier
     *            the identifier
     * @return the node id
     */
    private NodeId getNodeId(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        return identifier.firstKeyOf(Node.class).getNodeId();
    }

    /**
     * Checks if is tunnel ip newly configured.
     *
     * @param phySwitchBefore
     *            the phy switch before
     * @param phySwitchAfter
     *            the phy switch after
     * @return true, if is tunnel ip newly configured
     */
    private boolean isTunnelIpNewlyConfigured(PhysicalSwitchAugmentation phySwitchBefore,
            PhysicalSwitchAugmentation phySwitchAfter) {
        return (phySwitchBefore.getTunnelIps() == null || phySwitchBefore.getTunnelIps().isEmpty())
                && phySwitchAfter.getTunnelIps() != null && !phySwitchAfter.getTunnelIps().isEmpty();
    }

    /**
     * Gets the managed by node id.
     *
     * @param globalRef
     *            the global ref
     * @return the managed by node id
     */
    private String getManagedByNodeId(HwvtepGlobalRef globalRef) {
        InstanceIdentifier<?> instId = globalRef.getValue();
        return instId.firstKeyOf(Node.class).getNodeId().getValue();
    }

    private String getManagedByNodeId(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return psNodeId.substring(0, psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
        }
        return psNodeId;
    }

    private InstanceIdentifier<Node> getManagedByNodeIid(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            psNodeId = psNodeId.substring(0, psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH));
            return identifier.firstIdentifierOf(Topology.class).child(Node.class, new NodeKey(new NodeId(psNodeId)));
        }
        return null;
    }

    private String getPsName(InstanceIdentifier<PhysicalSwitchAugmentation> identifier) {
        String psNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        if (psNodeId.contains(HwvtepHAUtil.PHYSICALSWITCH)) {
            return psNodeId.substring(psNodeId.indexOf(HwvtepHAUtil.PHYSICALSWITCH) + HwvtepHAUtil.PHYSICALSWITCH
                    .length());
        }
        return null;
    }
}
