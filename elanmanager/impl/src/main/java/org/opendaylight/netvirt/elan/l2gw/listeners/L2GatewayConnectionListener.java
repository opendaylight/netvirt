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
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ConfigMcastCache;
import org.opendaylight.netvirt.elan.cache.ItmExternalTunnelCache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayConnectionInstanceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
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
public class L2GatewayConnectionListener extends AbstractClusteredAsyncDataTreeChangeListener<L2gatewayConnection>
        implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionListener.class);
    private static final int MAX_READ_TRIALS = 120;

    private static final Function<Node, InstanceIdentifier<Node>> TO_GLOBAL_PATH =
        HwvtepHAUtil::getGlobalNodePathFromPSNode;

    private static final Function<Node, InstanceIdentifier<Node>> TO_NODE_PATH =
        (node) -> HwvtepSouthboundUtils.createInstanceIdentifier(node.getNodeId());

    private static final Function<InstanceIdentifier<Node>, String> GET_DEVICE_NAME = HwvtepHAUtil::getPsName;

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE = (psIid) -> {
        return HwvtepHAUtil.getPsName(psIid) != null;
    };

    private static final Predicate<Node> IS_HA_PARENT_NODE = (node) -> {
        HwvtepGlobalAugmentation augmentation = node.augmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null && augmentation.nonnullManagers() != null) {
            return augmentation.nonnullManagers().values().stream().anyMatch(
                manager -> manager.key().getTarget().getValue().equals(HwvtepHAUtil.MANAGER_KEY));
        }
        return false;
    };

    private static final BiPredicate<InstanceIdentifier<Node>, Node> PS_NODE_OF_PARENT_NODE =
        (psIid, node) -> psIid.firstKeyOf(Node.class).getNodeId().getValue().contains(node.getNodeId().getValue());

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private final Scheduler scheduler;
    private final L2GatewayCache l2GatewayCache;
    private final ConfigMcastCache configMcastCache;
    private final L2GatewayListener l2GatewayListener;
    private final ItmExternalTunnelCache itmExternalTunnelCache;
    private final HwvtepPhysicalSwitchListener hwvtepPhysicalSwitchListener;
    private final ManagedNewTransactionRunner txRunner;

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
        super(db, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class).child(L2gatewayConnection.class),
            Executors.newListeningSingleThreadExecutor("L2GatewayConnectionListener", LOG));
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
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
    @SuppressWarnings("illegalcatch")
    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.broker);
        scheduler.getScheduledExecutorService().schedule(() -> {
            txRunner.callWithNewReadOnlyTransactionAndClose(CONFIGURATION, tx -> {
                try {
                    LOG.trace("Loading l2gw device cache");
                    loadL2GwDeviceCache(tx);
                    LOG.trace("Loading l2gw Mcast cache");
                    fillConfigMcastCache();
                    LOG.trace("Loading l2gw connection cache");
                    loadL2GwConnectionCache(tx);
                } catch (Exception e) {
                    LOG.error("Failed to load cache", e);
                } finally {
                    allNodes.clear();
                    l2GatewayListener.registerListener();
                    ///configMcastCache.registerListener(CONFIGURATION, broker);
                    //itmExternalTunnelCache.registerListener(CONFIGURATION, broker);
                    registerListener();
                    hwvtepPhysicalSwitchListener.registerListener();
                }
            });
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void register() {
        LOG.info("Registering L2GatewayConnectionListener Override Method");
        super.register();
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void registerListener() {
        super.register();
        LOG.info("Registering L2GatewayConnectionListener");
    }

    public void deregisterListener() {
        super.close();
        LOG.info("Deregistering L2GatewayConnectionListener");
    }

    @Override
    public void add(final InstanceIdentifier<L2gatewayConnection> identifier, final L2gatewayConnection input) {
        LOG.trace("Adding L2gatewayConnection: {}", input);

        // Get associated L2GwId from 'input'
        // Create logical switch in each of the L2GwDevices part of L2Gw
        // Logical switch name is network UUID
        // Add L2GwDevices to ELAN
        l2GatewayConnectionUtils.addL2GatewayConnection(input);
    }

    @Override
    public void remove(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection input) {
        LOG.trace("Removing L2gatewayConnection: {}", input);

        l2GatewayConnectionUtils.deleteL2GatewayConnection(input);
    }

    @Override
    public void update(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection original,
            L2gatewayConnection update) {
        LOG.trace("Updating L2gatewayConnection : original value={}, updated value={}", original, update);
    }

    private void addL2DeviceToCache(InstanceIdentifier<Node> psIid, Node globalNode, Node psNode) {
        LOG.trace("L2GatewayConnectionListener Adding device to cache {}", psNode.getNodeId().getValue());
        String deviceName = HwvtepHAUtil.getPsName(psIid);
        List<TunnelIps> tunnelIps = new ArrayList<>(getTunnelIps(psNode));
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
                .filter(entry -> entry.getValue().augmentation(HwvtepGlobalAugmentation.class) != null)
                .filter(entry ->
                        entry.getValue().augmentation(HwvtepGlobalAugmentation.class).getRemoteMcastMacs() != null)
                .forEach(entry -> {
                    entry.getValue().augmentation(HwvtepGlobalAugmentation.class).getRemoteMcastMacs().values().stream()
                            .forEach(mac -> {
                                configMcastCache.added(getMacIid(entry.getKey(), mac), mac);
                            });
                });
    }

    private InstanceIdentifier<RemoteMcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, RemoteMcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class).child(RemoteMcastMacs.class, mac.key());
    }

    public void loadL2GwConnectionCache(TypedReadTransaction<Configuration> tx) {
        InstanceIdentifier<L2gatewayConnections> parentIid = InstanceIdentifier
                .create(Neutron.class)
                .child(L2gatewayConnections.class);
        Optional<L2gatewayConnections> optional = Optional.empty();
        try {
            optional = tx.read(parentIid).get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading l2gwconnecton for populating Cache");
        }
        if (optional.isPresent() && optional.get().getL2gatewayConnection() != null) {
            LOG.trace("Found some connections to fill in l2gw connection cache");
            optional.get().getL2gatewayConnection().values()
                .forEach(connection -> {
                    add(parentIid.child(L2gatewayConnection.class, connection.key()), connection);
                });
        }
    }

    private void loadL2GwDeviceCache(TypedReadTransaction tx) {
        allNodes = (Map<InstanceIdentifier<Node>, Node>) readAllConfigNodes(tx)
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

    private Collection<TunnelIps> getTunnelIps(Node psNode) {
        if (psNode.augmentation(PhysicalSwitchAugmentation.class) != null) {
            return psNode.augmentation(PhysicalSwitchAugmentation.class).nonnullTunnelIps().values();
        }
        return Collections.emptyList();
    }

    private List<Node> readAllConfigNodes(TypedReadTransaction<Configuration> tx) {


        int trialNo = 1;
        Optional<Topology> topologyOptional = Optional.empty();
        do {
            try {
                topologyOptional = tx.read(HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()).get();
                break;
            } catch (ExecutionException | InterruptedException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    LOG.trace("Sleep interrupted");
                }
            }
        } while (trialNo++ < MAX_READ_TRIALS);
        if (topologyOptional != null && topologyOptional.isPresent() && topologyOptional.get().getNode() != null) {
            return  new ArrayList<>(topologyOptional.get().nonnullNode().values());
        }
        return Collections.emptyList();
    }
}
