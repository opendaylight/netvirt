/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.base.Optional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.cache.ItmExternalTunnelCache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayConnectionInstanceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayConnectionListener extends AsyncClusteredDataTreeChangeListenerBase<L2gatewayConnection,
        L2GatewayConnectionListener> implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");
    private static final int MAX_READ_TRIALS = 120;

    private static final Function<Node, InstanceIdentifier<Node>> TO_GLOBAL_PATH = (psNode) -> {
        return HwvtepHAUtil.getGlobalNodePathFromPSNode(psNode);
    };

    private static final Function<Node, InstanceIdentifier<Node>> TO_NODE_PATH = (node) -> {
        return HwvtepSouthboundUtils.createInstanceIdentifier(node.getNodeId());
    };

    private static final Function<InstanceIdentifier<Node>, String> GET_DEVICE_NAME = (psIid) -> {
        return HwvtepHAUtil.getPsName(psIid);
    };

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE = (psIid) -> {
        return HwvtepHAUtil.getPsName(psIid) != null;
    };

    private static final Predicate<Node> IS_HA_PARENT_NODE = (node) -> {
        HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null && augmentation.getManagers() != null) {
            return augmentation.getManagers().stream().anyMatch(
                manager -> manager.getKey().getTarget().getValue().equals(HwvtepHAUtil.MANAGER_KEY));
        }
        return false;
    };

    private static final BiPredicate<InstanceIdentifier<Node>, Node> PS_NODE_OF_PARENT_NODE = (psIid, node) -> {
        return psIid.firstKeyOf(Node.class).getNodeId().getValue().contains(node.getNodeId().getValue());
    };

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private final Scheduler scheduler;
    private final L2GatewayCache l2GatewayCache;
    private final ConfigMcastCache configMcastCache;
    private final L2GatewayListener l2GatewayListener;
    private final ItmExternalTunnelCache itmExternalTunnelCache;
    private final HwvtepPhysicalSwitchListener hwvtepPhysicalSwitchListener;

    Map<InstanceIdentifier<Node>, Node> allNodes = null;

    @Inject
    public L2GatewayConnectionListener(final DataBroker db, L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                       Scheduler scheduler, L2GatewayCache l2GatewayCache,
                                       final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                       final L2GatewayConnectionInstanceRecoveryHandler l2InstanceRecoveryHandler,
                                       final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                       ConfigMcastCache configMcastCache,
                                       L2GatewayListener l2GatewayListener,
                                       ItmExternalTunnelCache itmExternalTunnelCache,
                                       HwvtepPhysicalSwitchListener hwvtepPhysicalSwitchListener) {
        super(L2gatewayConnection.class, L2GatewayConnectionListener.class);
        this.broker = db;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.scheduler = scheduler;
        this.l2GatewayCache = l2GatewayCache;
        this.configMcastCache = configMcastCache;
        this.l2GatewayListener = l2GatewayListener;
        this.itmExternalTunnelCache = itmExternalTunnelCache;
        this.hwvtepPhysicalSwitchListener = hwvtepPhysicalSwitchListener;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        serviceRecoveryRegistry.addRecoverableListener(l2InstanceRecoveryHandler.buildServiceRegistryKey(),
                this);
    }

    @PostConstruct
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.broker);
        scheduler.getScheduledExecutorService().schedule(() -> {
            try {
                LOG.trace("Loading l2gw device cache");
                loadL2GwDeviceCache();
                LOG.trace("Loading l2gw Mcast cache");
                fillConfigMcastCache();
                LOG.trace("Loading l2gw connection cache");
                loadL2GwConnectionCache();
            } catch (Exception e) {
                LOG.error("Failed to load cache", e);
            } finally {
                allNodes.clear();
                l2GatewayListener.registerListener();
                configMcastCache.registerListener(CONFIGURATION, broker);
                itmExternalTunnelCache.registerListener(CONFIGURATION, broker);
                registerListener(CONFIGURATION, broker);
                hwvtepPhysicalSwitchListener.registerListener();
            }
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void registerListener() {
        LOG.info("Registering L2GatewayConnectionListener");
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    public void deregisterListener() {
        LOG.info("Deregistering L2GatewayConnectionListener");
        super.deregisterListener();
    }

    @Override
    protected void add(final InstanceIdentifier<L2gatewayConnection> identifier, final L2gatewayConnection input) {
        LOG.trace("Adding L2gatewayConnection: {}", input);

        // Get associated L2GwId from 'input'
        // Create logical switch in each of the L2GwDevices part of L2Gw
        // Logical switch name is network UUID
        // Add L2GwDevices to ELAN
        l2GatewayConnectionUtils.addL2GatewayConnection(input);
    }

    @Override
    protected void remove(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection input) {
        LOG.trace("Removing L2gatewayConnection: {}", input);

        l2GatewayConnectionUtils.deleteL2GatewayConnection(input);
    }

    @Override
    protected void update(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection original,
            L2gatewayConnection update) {
        LOG.trace("Updating L2gatewayConnection : original value={}, updated value={}", original, update);
    }

    @Override
    protected InstanceIdentifier<L2gatewayConnection> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
            .child(L2gatewayConnection.class);
    }

    @Override
    protected L2GatewayConnectionListener getDataTreeChangeListener() {
        return this;
    }

    private void addL2DeviceToCache(InstanceIdentifier<Node> psIid, Node globalNode, Node psNode) {
        LOG.trace("L2GatewayConnectionListener Adding device to cache {}", psNode.getNodeId().getValue());
        String deviceName = HwvtepHAUtil.getPsName(psIid);
        List<TunnelIps> tunnelIps = getTunnelIps(psNode);
        if (tunnelIps != null) {
            l2GatewayCache.updateL2GatewayCache(deviceName, globalNode.getNodeId().getValue(), tunnelIps);
            LOG.info("L2GatewayConnectionListener Added device to cache {} {}",
                    psNode.getNodeId().getValue(), tunnelIps);
        } else {
            LOG.error("L2GatewayConnectionListener Could not add device to l2gw cache no tunnel ip found {}",
                    psNode.getNodeId().getValue());
        }
    }

    private void fillConfigMcastCache() {
        if (allNodes == null) {
            return;
        }
        //allNodes.entrySet().stream().map(entry -> entry);
        allNodes.entrySet().stream()
                .filter(entry -> entry.getValue().getAugmentation(HwvtepGlobalAugmentation.class) != null)
                .filter(entry ->
                        entry.getValue().getAugmentation(HwvtepGlobalAugmentation.class).getRemoteMcastMacs() != null)
                .forEach(entry -> {
                    entry.getValue().getAugmentation(HwvtepGlobalAugmentation.class).getRemoteMcastMacs().stream()
                            .forEach(mac -> {
                                configMcastCache.add(getMacIid(entry.getKey(), mac), mac);
                            });
                });
    }

    private InstanceIdentifier<RemoteMcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, RemoteMcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class).child(RemoteMcastMacs.class, mac.getKey());
    }

    public void loadL2GwConnectionCache() {
        InstanceIdentifier<L2gatewayConnections> parentIid = InstanceIdentifier
                .create(Neutron.class)
                .child(L2gatewayConnections.class);

        Optional<L2gatewayConnections> optional = MDSALUtil.read(broker, CONFIGURATION, parentIid);
        if (optional.isPresent() && optional.get().getL2gatewayConnection() != null) {
            LOG.trace("Found some connections to fill in l2gw connection cache");
            optional.get().getL2gatewayConnection()
                    .forEach(connection -> {
                        add(parentIid.child(L2gatewayConnection.class, connection.getKey()), connection);
                    });
        }
    }

    private void loadL2GwDeviceCache() {
        allNodes = readAllConfigNodes()
                .stream()
                .collect(toMap(TO_NODE_PATH, Function.identity()));

        LOG.trace("Loading all config nodes");

        Set<InstanceIdentifier<Node>> allIids = allNodes.keySet();

        Map<String, List<InstanceIdentifier<Node>>> psNodesByDeviceName = allIids
                .stream()
                .filter(IS_PS_NODE)
                .collect(groupingBy(GET_DEVICE_NAME, toList()));

        //Process HA nodes
        createHANodes(allIids);

        //Process non HA nodes there will be only one ps node iid for each device for non ha nodes
        psNodesByDeviceName.values().stream()
                .filter(psIids -> psIids.size() == 1)
                .map(psIids -> psIids.get(0))
                .forEach(psIid -> {
                    Node psNode = allNodes.get(psIid);
                    Node globalNode = allNodes.get(TO_GLOBAL_PATH.apply(psNode));
                    if (globalNode != null) {
                        addL2DeviceToCache(psIid, globalNode, psNode);
                    }
                });
    }

    private void createHANodes(Set<InstanceIdentifier<Node>> allIids) {
        allNodes.values().stream()
                .filter(IS_HA_PARENT_NODE)
                .forEach(parentNode -> {
                    fillHACache(parentNode);
                    allIids.stream()
                            .filter(IS_PS_NODE)
                            .filter(psIid -> PS_NODE_OF_PARENT_NODE.test(psIid, parentNode))
                            .forEach(psIid -> {
                                addL2DeviceToCache(psIid, parentNode, allNodes.get(psIid));
                            });
                });
    }

    private static void fillHACache(Node parentNode) {
        InstanceIdentifier<Node> parentIid
                = HwvtepHAUtil.convertToInstanceIdentifier(parentNode.getNodeId().getValue());
        List<NodeId> childIids
                = HwvtepHAUtil.getChildNodeIdsFromManagerOtherConfig(Optional.of(parentNode));
        if (childIids != null) {
            for (NodeId childid : childIids) {
                InstanceIdentifier<Node> childIid
                        = HwvtepHAUtil.convertToInstanceIdentifier(childid.getValue());
                HwvtepHACache.getInstance().addChild(parentIid, childIid);
            }
        }
    }

    private List<TunnelIps> getTunnelIps(Node psNode) {
        if (psNode.getAugmentation(PhysicalSwitchAugmentation.class) != null) {
            return psNode.getAugmentation(PhysicalSwitchAugmentation.class).getTunnelIps();
        }
        return Collections.EMPTY_LIST;
    }

    private List<Node> readAllConfigNodes() {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        Optional<Topology> topologyOptional = null;
        int trialNo = 1;
        do {
            try {
                topologyOptional = tx.read(CONFIGURATION,
                        HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()).checkedGet();
                break;
            } catch (ReadFailedException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    LOG.trace("Sleep interrupted");
                }
            }
        } while (trialNo++ < MAX_READ_TRIALS);
        if (topologyOptional.isPresent() && topologyOptional.get().getNode() != null) {
            return topologyOptional.get().getNode();
        }
        return Collections.EMPTY_LIST;
    }
}
