/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StaleVlanBindingsCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(StaleVlanBindingsCleaner.class);
    private static final int DEFAULT_STALE_CLEANUP_DELAY_SECS = 900;

    private static Function<VlanBindings, String> LOGICAL_SWITCH_FROM_BINDING = (binding) -> {
        InstanceIdentifier<LogicalSwitches> lsRef = (InstanceIdentifier<LogicalSwitches>)
                binding.getLogicalSwitchRef().getValue();
        return lsRef.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    };

    private static BiPredicate<List<String>, String> IS_STALE_LOGICAL_SWITCH = (validNetworks, logicalSwitch) -> {
        return !validNetworks.contains(logicalSwitch);
    };

    private static Predicate<TerminationPoint> CONTAINS_VLANBINDINGS = (port) -> {
        return port.getAugmentation(HwvtepPhysicalPortAugmentation.class) != null
                && port.getAugmentation(HwvtepPhysicalPortAugmentation.class).getVlanBindings() != null;
    };


    private final DataBroker broker;
    private final ElanUtils elanUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final Scheduler scheduler;
    private final ElanConfig elanConfig;
    private final L2GatewayCache l2GatewayCache;
    private final ElanInstanceCache elanInstanceCache;
    private final Map<NodeId, ScheduledFuture> cleanupTasks = new ConcurrentHashMap<>();

    @Inject
    public StaleVlanBindingsCleaner(final DataBroker broker,
                                    final ElanUtils elanUtils,
                                    final ElanL2GatewayUtils elanL2GatewayUtils,
                                    final Scheduler scheduler,
                                    final ElanConfig elanConfig,
                                    final L2GatewayCache l2GatewayCache,
                                    final ElanInstanceCache elanInstanceCache) {
        this.broker = broker;
        this.elanUtils = elanUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.scheduler = scheduler;
        this.elanConfig = elanConfig;
        this.l2GatewayCache = l2GatewayCache;
        this.elanInstanceCache = elanInstanceCache;
    }

    private long getCleanupDelay() {
        return elanConfig.getL2gwStaleVlanCleanupDelaySecs() != null
                ? elanConfig.getL2gwStaleVlanCleanupDelaySecs() : DEFAULT_STALE_CLEANUP_DELAY_SECS;
    }

    public void scheduleStaleCleanup(final String deviceName,
                                     final InstanceIdentifier<Node> globalNodeIid,
                                     final InstanceIdentifier<Node> psNodeIid) {
        NodeId psNodeId = psNodeIid.firstKeyOf(Node.class).getNodeId();
        cleanupTasks.compute(psNodeId, (key, ft) -> {
            if (ft != null) {
                ft.cancel(false);
            }
            return scheduler.getScheduledExecutorService().schedule(
                () -> {
                    L2GatewayDevice l2GwDevice = l2GatewayCache.get(deviceName);
                    NodeId globalNodeId = globalNodeIid.firstKeyOf(Node.class).getNodeId();
                    Node configNode = MDSALUtil.read(broker, CONFIGURATION, globalNodeIid)
                            .or(defaultNode(globalNodeId));
                    Node configPsNode = MDSALUtil.read(broker, CONFIGURATION, psNodeIid).or(defaultNode(psNodeId));
                    cleanupStaleLogicalSwitches(l2GwDevice, configNode, configPsNode);
                    cleanupTasks.remove(psNodeIid.firstKeyOf(Node.class).getNodeId());
                }, getCleanupDelay(), TimeUnit.SECONDS);
        });
    }

    private Node defaultNode(final NodeId nodeId) {
        return new NodeBuilder().setNodeId(nodeId).build();
    }

    private void cleanupStaleLogicalSwitches(final L2GatewayDevice l2GwDevice,
                                             final Node configNode,
                                             final Node configPsNode) {

        String globalNodeId = configNode.getNodeId().getValue();
        List<L2gatewayConnection> connectionsOfDevice = L2GatewayConnectionUtils.getAssociatedL2GwConnections(
                broker, l2GwDevice.getL2GatewayIds());

        List<String> validNetworks = connectionsOfDevice.stream()
                .map((connection) -> connection.getNetworkId().getValue())
                .filter(elan -> elanInstanceCache.get(elan).isPresent())
                .collect(Collectors.toList());

        List<String> logicalSwitchesOnDevice = getLogicalSwitchesOnDevice(configNode);

        List<String> staleLogicalSwitches = logicalSwitchesOnDevice.stream()
                .filter((staleLogicalSwitch) -> IS_STALE_LOGICAL_SWITCH.test(validNetworks, staleLogicalSwitch))
                .collect(Collectors.toList());

        if (!staleLogicalSwitches.isEmpty()) {
            Map<String, List<InstanceIdentifier<VlanBindings>>> vlansByLogicalSwitch = getVlansByLogicalSwitchOnDevice(
                    configPsNode);
            staleLogicalSwitches.forEach((staleLogicalSwitch) -> cleanupStaleBindings(
                    globalNodeId, vlansByLogicalSwitch, staleLogicalSwitch));
        }
    }

    private Map<String, List<InstanceIdentifier<VlanBindings>>> getVlansByLogicalSwitchOnDevice(
            final Node configPsNode) {
        List<TerminationPoint> ports = configPsNode.getTerminationPoint();
        if (ports == null) {
            return Collections.emptyMap();
        }
        Map<String, List<InstanceIdentifier<VlanBindings>>> vlans = new HashMap<>();
        ports.stream()
                .filter(CONTAINS_VLANBINDINGS)
                .forEach((port) -> {
                    port.getAugmentation(HwvtepPhysicalPortAugmentation.class)
                            .getVlanBindings()
                            .forEach((binding) -> putVlanBindingVsLogicalSwitch(configPsNode, vlans, port, binding));
                });
        return vlans;
    }

    private void putVlanBindingVsLogicalSwitch(final Node configPsNode,
                                               final Map<String, List<InstanceIdentifier<VlanBindings>>> vlans,
                                               final TerminationPoint port,
                                               final VlanBindings binding) {
        String logicalSwitch = LOGICAL_SWITCH_FROM_BINDING.apply(binding);
        vlans.computeIfAbsent(logicalSwitch, (name) -> new ArrayList<>())
                .add(createVlanIid(configPsNode.getNodeId(), port, binding));
    }

    private InstanceIdentifier<VlanBindings> createVlanIid(final NodeId nodeId,
                                                           final TerminationPoint tp,
                                                           final VlanBindings vlanBinding) {
        return HwvtepSouthboundUtils.createInstanceIdentifier(nodeId)
                .child(TerminationPoint.class, tp.getKey())
                .augmentation(HwvtepPhysicalPortAugmentation.class)
                .child(VlanBindings.class, vlanBinding.getKey());
    }

    private void cleanupStaleBindings(final String globalNodeId,
                                      final Map<String, List<InstanceIdentifier<VlanBindings>>> vlans,
                                      final String staleLogicalSwitch) {

        LOG.trace("CleanupStaleBindings for logical switch {}", staleLogicalSwitch);
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        if (vlans.containsKey(staleLogicalSwitch)) {
            vlans.get(staleLogicalSwitch)
                    .forEach((vlanIid) -> tx.delete(LogicalDatastoreType.CONFIGURATION, vlanIid));
        }
        ListenableFutures.addErrorLogging(tx.submit(), LOG,
                "Failed to delete stale vlan bindings from node {}", globalNodeId);
        elanL2GatewayUtils.scheduleDeleteLogicalSwitch(new NodeId(globalNodeId), staleLogicalSwitch, true);
    }

    private List<String> getLogicalSwitchesOnDevice(final Node globalConfigNode) {
        HwvtepGlobalAugmentation augmentation = globalConfigNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation == null || augmentation.getLogicalSwitches() == null) {
            return Collections.emptyList();
        }
        return augmentation
                .getLogicalSwitches()
                .stream()
                .map((ls) -> ls.getHwvtepNodeName().getValue())
                .collect(Collectors.toList());
    }
}
