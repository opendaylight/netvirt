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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
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
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.metrics.Counter;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayConnectionListener extends AsyncClusteredDataTreeChangeListenerBase<L2gatewayConnection,
        L2GatewayConnectionListener> {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionListener.class);
    private static final int MAX_READ_TRIALS = 120;

    private static final Function<Node, InstanceIdentifier<Node>> TO_GLOBAL_PATH =
            HwvtepHAUtil::getGlobalNodePathFromPSNode;

    private static final Function<Node, InstanceIdentifier<Node>> TO_NODE_PATH =
        (node) -> HwvtepSouthboundUtils.createInstanceIdentifier(node.getNodeId());

    private static final Function<InstanceIdentifier<Node>, String> GET_DEVICE_NAME = HwvtepHAUtil::getPsName;

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE = (psIid) ->
            HwvtepHAUtil.getPsName(psIid) != null;

    private static final Predicate<Node> IS_HA_PARENT_NODE = (node) -> {
        HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null && augmentation.getManagers() != null) {
            return augmentation.getManagers().stream().anyMatch(
                manager -> manager.getKey().getTarget().getValue().equals(HwvtepHAUtil.MANAGER_KEY));
        }
        return false;
    };

    private static final BiPredicate<InstanceIdentifier<Node>, Node> PS_NODE_OF_PARENT_NODE =
        (psIid, node) -> psIid.firstKeyOf(Node.class).getNodeId().getValue().contains(node.getNodeId().getValue());

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private final Scheduler scheduler;
    private final L2GatewayCache l2GatewayCache;
    private final Labeled<Labeled<Counter>> elanConnectionsCounter;

    @Inject
    public L2GatewayConnectionListener(final DataBroker db, L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                       Scheduler scheduler, L2GatewayCache l2GatewayCache,
                                       MetricProvider metricProvider) {
        super(L2gatewayConnection.class, L2GatewayConnectionListener.class);
        this.broker = db;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.scheduler = scheduler;
        this.l2GatewayCache = l2GatewayCache;
        this.elanConnectionsCounter = metricProvider.newCounter(MetricDescriptor.builder()
        .anchor(this).project("netvirt").module("l2gw").id("connections").build(), "modification", "elan");
    }

    @PostConstruct
    public void init() {
        loadL2GwDeviceCache(1);
    }

    @Override
    protected void add(final InstanceIdentifier<L2gatewayConnection> identifier, final L2gatewayConnection input) {
        LOG.trace("Adding L2gatewayConnection: {}", input);
        elanConnectionsCounter.label("add").label(input.getNetworkId().getValue()).increment();
        // Get associated L2GwId from 'input'
        // Create logical switch in each of the L2GwDevices part of L2Gw
        // Logical switch name is network UUID
        // Add L2GwDevices to ELAN
        l2GatewayConnectionUtils.addL2GatewayConnection(input);
    }

    @Override
    protected void remove(InstanceIdentifier<L2gatewayConnection> identifier, L2gatewayConnection input) {
        LOG.trace("Removing L2gatewayConnection: {}", input);
        elanConnectionsCounter.label("delete").label(input.getNetworkId().getValue()).increment();
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

    private void loadL2GwDeviceCache(final int trialNo) {
        scheduler.getScheduledExecutorService().schedule(() -> {
            if (trialNo == MAX_READ_TRIALS) {
                LOG.error("Failed to read config topology");
                return;
            }
            ReadOnlyTransaction tx = broker.newReadOnlyTransaction();
            InstanceIdentifier<Topology> topoIid = HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier();
            Futures.addCallback(tx.read(CONFIGURATION, topoIid), new FutureCallback<Optional<Topology>>() {
                @Override
                public void onSuccess(Optional<Topology> topologyOptional) {
                    if (topologyOptional != null && topologyOptional.isPresent()) {
                        loadL2GwDeviceCache(topologyOptional.get().getNode());
                    }
                    registerListener(CONFIGURATION, broker);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    loadL2GwDeviceCache(trialNo + 1);
                }
            }, MoreExecutors.directExecutor());
            tx.close();
        }, 1, TimeUnit.SECONDS);
    }

    private void loadL2GwDeviceCache(List<Node> nodes) {
        if (nodes == null) {
            LOG.debug("No config topology nodes are present");
            return;
        }
        Map<InstanceIdentifier<Node>, Node> allNodes = nodes
                .stream()
                .collect(toMap(TO_NODE_PATH, Function.identity()));

        LOG.trace("Loading all config nodes");

        Set<InstanceIdentifier<Node>> allIids = allNodes.keySet();

        Map<String, List<InstanceIdentifier<Node>>> psNodesByDeviceName = allIids
                .stream()
                .filter(IS_PS_NODE)
                .collect(groupingBy(GET_DEVICE_NAME, toList()));

        //Process HA nodes
        allNodes.values().stream()
                .filter(IS_HA_PARENT_NODE)
                .forEach(parentNode -> allIids.stream()
                        .filter(IS_PS_NODE)
                        .filter(psIid -> PS_NODE_OF_PARENT_NODE.test(psIid, parentNode))
                        .forEach(psIid -> addL2DeviceToCache(psIid, parentNode, allNodes.get(psIid))));

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

    void addL2DeviceToCache(InstanceIdentifier<Node> psIid, Node globalNode, Node psNode) {
        LOG.trace("Adding device to cache {}", psNode.getNodeId().getValue());
        String deviceName = HwvtepHAUtil.getPsName(psIid);
        L2GatewayDevice l2GwDevice = l2GatewayCache.addOrGet(deviceName);
        l2GwDevice.setConnected(true);
        l2GwDevice.setHwvtepNodeId(globalNode.getNodeId().getValue());

        List<TunnelIps> tunnelIps = psNode.getAugmentation(PhysicalSwitchAugmentation.class) != null
                ? psNode.getAugmentation(PhysicalSwitchAugmentation.class).getTunnelIps() : null;
        if (tunnelIps != null) {
            for (TunnelIps tunnelIp : tunnelIps) {
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }
    }
}
