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
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.internal.ElanInterfaceManager;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.SchedulerUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
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

    private final DataBroker broker;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;
    private final ElanInterfaceManager elanInterfaceManager;

    @Inject
    public L2GatewayConnectionListener(final DataBroker db, L2GatewayConnectionUtils l2GatewayConnectionUtils,
                                       ElanInterfaceManager elanInterfaceManager) {
        super(L2gatewayConnection.class, L2GatewayConnectionListener.class);
        this.broker = db;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.elanInterfaceManager = elanInterfaceManager;
    }

    static Function<Node, InstanceIdentifier<Node>> TO_GLOBAL_PATH = (psNode) -> {
        return HwvtepHAUtil.getGlobalNodePathFromPSNode(psNode);
    };

    static Function<Node, InstanceIdentifier<Node>> TO_NODE_PATH = (node) -> {
        return HwvtepSouthboundUtils.createInstanceIdentifier(node.getNodeId());
    };

    static Function<InstanceIdentifier<Node>, String> GET_DEVICE_NAME = (psIid) -> {
        return HwvtepHAUtil.getPsName(psIid);
    };

    static Predicate<InstanceIdentifier<Node>> IS_PS_NODE = (psIid) -> {
        return HwvtepHAUtil.getPsName(psIid) != null;
    };

    static Predicate<Node> IS_HA_PARENT_NODE = (node) -> {
        HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (augmentation != null && augmentation.getManagers() != null) {
            return augmentation.getManagers().stream().anyMatch(
                manager -> manager.getKey().getTarget().getValue().equals(HwvtepHAUtil.MANAGER_KEY));
        }
        return false;
    };

    static BiPredicate<InstanceIdentifier<Node>, Node> PS_NODE_OF_PARENT_NODE = (psIid, node) -> {
        return psIid.firstKeyOf(Node.class).getNodeId().getValue().contains(node.getNodeId().getValue());
    };

    @PostConstruct
    public void init() {
        SchedulerUtils.getScheduledExecutorService().schedule(() -> {
            loadL2GwDeviceCache();
            loadL2GwConnectionCache();
            registerListener(CONFIGURATION, broker);
            LOG.trace("Starting elan interface listener");
            //start the elan interface manager after the l2gw cache is loaded
            elanInterfaceManager.registerListener(CONFIGURATION, broker);
        }, 1, TimeUnit.SECONDS);
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

    private void loadL2GwConnectionCache() {
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

    private List<TunnelIps> getTunnelIps(Node psNode) {
        if (psNode.getAugmentation(PhysicalSwitchAugmentation.class) != null) {
            return psNode.getAugmentation(PhysicalSwitchAugmentation.class).getTunnelIps();
        }
        return Collections.EMPTY_LIST;
    }

    private void loadL2GwDeviceCache() {
        Map<InstanceIdentifier<Node>, Node> allNodes = readAllConfigNodes()
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
                .forEach(parentNode -> {
                    allIids.stream()
                            .filter(IS_PS_NODE)
                            .filter(psIid -> PS_NODE_OF_PARENT_NODE.test(psIid, parentNode))
                            .forEach(psIid -> {
                                addL2DeviceToCache(psIid, parentNode, allNodes.get(psIid));
                            });
                });

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
        List<TunnelIps> teps = getTunnelIps(psNode);
        String hwvtepNodeId = globalNode.getNodeId().getValue();
        L2GatewayCacheUtils.updateL2GatewayCache(deviceName, hwvtepNodeId, teps);
    }
}
