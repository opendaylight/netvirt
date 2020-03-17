/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static java.util.Collections.emptyList;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.hwvtep.HwvtepClusteredDataTreeChangeListener;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalPortAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listener for physical locator presence in operational datastore.
 */
@Singleton
public class HwvtepTerminationPointListener
        extends HwvtepClusteredDataTreeChangeListener<TerminationPoint, HwvtepTerminationPointListener> {

    private static final Logger LOG = LoggerFactory.getLogger(HwvtepTerminationPointListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final L2GatewayCache l2GatewayCache;

    @Inject
    public HwvtepTerminationPointListener(DataBroker broker, ElanL2GatewayUtils elanL2GatewayUtils,
            ElanClusterUtils elanClusterUtils, L2GatewayCache l2GatewayCache,
            HwvtepNodeHACache hwvtepNodeHACache) {
        super(TerminationPoint.class, HwvtepTerminationPointListener.class, hwvtepNodeHACache);

        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.l2GatewayCache = l2GatewayCache;
        //No longer needed as port reconciliation is added in plugin
        //registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        LOG.debug("created HwvtepTerminationPointListener");
    }

    @Override
    protected void removed(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint del) {
        LOG.trace("physical locator removed {}", identifier);
        final HwvtepPhysicalPortAugmentation portAugmentation =
                del.augmentation(HwvtepPhysicalPortAugmentation.class);
        if (portAugmentation != null) {
            elanClusterUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_NAME,
                "Handling Physical port delete",
                () -> handlePortDeleted(identifier));
        }
    }

    @Override
    protected void updated(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint original,
                           TerminationPoint update) {
        LOG.trace("physical locator available {}", identifier);
    }

    @Override
    protected void added(InstanceIdentifier<TerminationPoint> identifier, final TerminationPoint add) {
        final HwvtepPhysicalPortAugmentation portAugmentation =
                add.augmentation(HwvtepPhysicalPortAugmentation.class);
        if (portAugmentation != null) {
            final NodeId nodeId = identifier.firstIdentifierOf(Node.class).firstKeyOf(Node.class).getNodeId();
            elanClusterUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_NAME,
                () -> handlePortAdded(add, nodeId));
            return;
        }

        LOG.trace("physical locator available {}", identifier);
    }

    @Override
    protected InstanceIdentifier<TerminationPoint> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                .child(TerminationPoint.class);
    }

    @Override
    protected HwvtepTerminationPointListener getDataTreeChangeListener() {
        return this;
    }

    private List<ListenableFuture<Void>> handlePortAdded(TerminationPoint portAdded, NodeId psNodeId) {
        Node psNode = HwvtepUtils.getHwVtepNode(broker, LogicalDatastoreType.OPERATIONAL, psNodeId);
        if (psNode != null) {
            String psName = psNode.augmentation(PhysicalSwitchAugmentation.class).getHwvtepNodeName().getValue();
            L2GatewayDevice l2GwDevice = l2GatewayCache.get(psName);
            if (l2GwDevice != null) {
                if (isL2GatewayConfigured(l2GwDevice)) {
                    List<L2gatewayConnection> l2GwConns = L2GatewayConnectionUtils.getAssociatedL2GwConnections(broker,
                            l2GwDevice.getL2GatewayIds());
                    String newPortId = portAdded.getTpId().getValue();
                    NodeId hwvtepNodeId = new NodeId(l2GwDevice.getHwvtepNodeId());
                    List<VlanBindings> vlanBindings = getVlanBindings(l2GwConns, hwvtepNodeId, psName, newPortId);
                    return Collections.singletonList(
                            elanL2GatewayUtils.updateVlanBindingsInL2GatewayDevice(hwvtepNodeId, psName, newPortId,
                                    vlanBindings));
                }
            } else {
                LOG.error("{} details are not present in L2Gateway Cache", psName);
            }
        } else {
            LOG.error("{} entry not in config datastore", psNodeId);
        }
        return emptyList();
    }

    private List<ListenableFuture<Void>> handlePortDeleted(InstanceIdentifier<TerminationPoint> identifier) {
        InstanceIdentifier<Node> psNodeIid = identifier.firstIdentifierOf(Node.class);
        return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
            tx -> tx.read(psNodeIid).get().ifPresent(node -> tx.delete(identifier))));
    }

    private List<VlanBindings> getVlanBindings(List<L2gatewayConnection> l2GwConns, NodeId hwvtepNodeId, String psName,
                                               String newPortId) {
        List<VlanBindings> vlanBindings = new ArrayList<>();
        for (L2gatewayConnection l2GwConn : l2GwConns) {
            L2gateway l2Gateway = L2GatewayConnectionUtils.getNeutronL2gateway(broker, l2GwConn.getL2gatewayId());
            if (l2Gateway == null) {
                LOG.error("L2Gateway with id {} is not present", l2GwConn.getL2gatewayId().getValue());
            } else {
                String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(
                        l2GwConn.getNetworkId().getValue());
                List<Devices> l2Devices = l2Gateway.nonnullDevices();
                for (Devices l2Device : l2Devices) {
                    String l2DeviceName = l2Device.getDeviceName();
                    if (l2DeviceName != null && l2DeviceName.equals(psName)) {
                        for (Interfaces deviceInterface : l2Device.nonnullInterfaces()) {
                            if (Objects.equals(deviceInterface.getInterfaceName(), newPortId)) {
                                if (deviceInterface.getSegmentationIds() != null
                                        && !deviceInterface.getSegmentationIds().isEmpty()) {
                                    for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                                        vlanBindings.add(HwvtepSouthboundUtils.createVlanBinding(hwvtepNodeId, vlanId,
                                                logicalSwitchName));
                                    }
                                } else {
                                    // Use defaultVlanId (specified in L2GatewayConnection) if Vlan
                                    // ID not specified at interface level.
                                    Integer segmentationId = l2GwConn.getSegmentId();
                                    int defaultVlanId = segmentationId != null ? segmentationId : 0;
                                    vlanBindings.add(HwvtepSouthboundUtils.createVlanBinding(hwvtepNodeId,
                                            defaultVlanId, logicalSwitchName));
                                }
                            }
                        }
                    }
                }
            }
        }
        return vlanBindings;
    }

    private static boolean isL2GatewayConfigured(L2GatewayDevice l2GwDevice) {
        return l2GwDevice.getHwvtepNodeId() != null
                && !l2GwDevice.getL2GatewayIds().isEmpty() && l2GwDevice.getTunnelIp() != null;
    }
}
