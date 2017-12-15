/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
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
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
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
    private static final int STALE_CLEANUP_DELAY = ElanConstants.L2GW_STALE_VLAN_CLEANUP_DELAY;

    @Inject
    public StaleVlanBindingsCleaner(final DataBroker broker,
                                    final ElanUtils elanUtils,
                                    final ElanL2GatewayUtils elanL2GatewayUtils) {
        this.broker = broker;
        this.elanUtils = elanUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
    }

    private DataBroker broker;
    private ElanUtils elanUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;

    private final Map<NodeId, ScheduledFuture> cleanupTasks = new ConcurrentHashMap<>();

    private static final Function<VlanBindings, String> LOGICAL_SWITCH_FROM_BINDING = (binding) -> {
        InstanceIdentifier<LogicalSwitches> lsRef = (InstanceIdentifier<LogicalSwitches>)
                binding.getLogicalSwitchRef().getValue();
        return lsRef.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    };

    private final Predicate<String> isValidNetwork = (elan) -> {
        return elanUtils.getElanInstanceByName(broker, elan) != null;
    };

    private static final BiPredicate<List<String>, String> IS_STALE_LOGICAL_SWITCH = (validNetworks, logicalSwitch) -> {
        return !validNetworks.contains(logicalSwitch);
    };

    private static final Predicate<TerminationPoint> CONTAINS_VLANBINDINGS = (port) -> {
        return port.getAugmentation(HwvtepPhysicalPortAugmentation.class) != null
                && port.getAugmentation(HwvtepPhysicalPortAugmentation.class).getVlanBindings() != null;
    };

    public void scheduleStaleCleanup(final String deviceName,
                                     final InstanceIdentifier<Node> globalNodeIid,
                                     final InstanceIdentifier<Node> psNodeIid) {
        NodeId psNodeId = psNodeIid.firstKeyOf(Node.class).getNodeId();
        ScheduledFuture ft = cleanupTasks.remove(psNodeId);
        if (ft != null) {
            ft.cancel(false);
        }
        ft = SchedulerUtils.getScheduledExecutorService().schedule(
                new VlanCleanupTask(deviceName, globalNodeIid, psNodeIid), STALE_CLEANUP_DELAY, TimeUnit.SECONDS);
        LOG.trace("Scheduled stale logical switch clean up job {}", deviceName);
        cleanupTasks.put(psNodeId, ft);
    }

    class VlanCleanupTask implements Runnable {
        volatile boolean cancelled = false;

        L2GatewayDevice l2GwDevice;
        String deviceName;
        InstanceIdentifier<Node> globalNodeIid;
        InstanceIdentifier<Node> psNodeIid;

        VlanCleanupTask(final String deviceName,
                        final InstanceIdentifier<Node> globalNodeIid,
                        final InstanceIdentifier<Node> psNodeIid) {
            this.deviceName = deviceName;
            this.globalNodeIid = globalNodeIid;
            this.psNodeIid = psNodeIid;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public void run() {
            if (!cancelled) {
                try {
                    this.l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(deviceName);
                    NodeId psNodeId = psNodeIid.firstKeyOf(Node.class).getNodeId();
                    NodeId globalNodeId = globalNodeIid.firstKeyOf(Node.class).getNodeId();
                    Node configNode = MDSALUtil.read(broker, CONFIGURATION, globalNodeIid)
                            .or(defaultNode(globalNodeId));
                    Node configPsNode = MDSALUtil.read(broker, CONFIGURATION, psNodeIid).or(defaultNode(psNodeId));
                    cleanupStaleLogicalSwitches(l2GwDevice, configNode, configPsNode);
                } catch (Exception e) {
                    LOG.error("Failed to do clean up ", e);
                }
            }
            cleanupTasks.remove(psNodeIid.firstKeyOf(Node.class).getNodeId());
        }
    }

    private Node defaultNode(final NodeId nodeId) {
        return new NodeBuilder().setNodeId(nodeId).build();
    }

    public void cleanupStaleLogicalSwitches(final L2GatewayDevice l2GwDevice,
                                            final Node configNode,
                                            final Node configPsNode) {

        String globalNodeId = configNode.getNodeId().getValue();
        List<L2gatewayConnection> connectionsOfDevice = L2GatewayConnectionUtils.getAssociatedL2GwConnections(
                broker, l2GwDevice.getL2GatewayIds());

        List<String> validNetworks = connectionsOfDevice.stream()
                .map((connection) -> connection.getNetworkId().getValue())
                .filter(isValidNetwork)
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
            return Collections.EMPTY_MAP;
        }
        Map<String, List<InstanceIdentifier<VlanBindings>>> vlans = new ConcurrentHashMap<>();
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
        vlans.computeIfAbsent(logicalSwitch, (name) -> new ArrayList<>());
        LOG.trace("Put vlan {} for logical switch {}", binding, logicalSwitch);
        vlans.get(logicalSwitch).add(
                createVlanIid(configPsNode.getNodeId(), port, binding));
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
        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to delete stale vlan bindings from node {}", globalNodeId);
        }
        elanL2GatewayUtils.scheduleDeleteLogicalSwitch(new NodeId(globalNodeId), staleLogicalSwitch,
                ElanConstants.LOGICAL_SWITCH_DELETE_DELAY, true);
    }

    private List<String> getLogicalSwitchesOnDevice(final Node globalConfigNode) {
        HwvtepGlobalAugmentation augmentation = globalConfigNode.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation == null || augmentation.getLogicalSwitches() == null) {
            return Collections.EMPTY_LIST;
        }
        return augmentation
                .getLogicalSwitches()
                .stream()
                .map((ls) -> ls.getHwvtepNodeName().getValue())
                .collect(Collectors.toList());
    }
}
