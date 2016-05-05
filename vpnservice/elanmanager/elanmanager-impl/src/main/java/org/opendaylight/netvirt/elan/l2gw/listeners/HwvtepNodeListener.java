/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.internal.ElanInstanceManager;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.vpnservice.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class HwvtepNodeListener
        extends AsyncClusteredDataChangeListenerBase<Node, HwvtepNodeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepNodeListener.class);

    private DataBroker dataBroker;
    private ItmRpcService itmRpcService;
    ElanInstanceManager elanInstanceManager;

    public HwvtepNodeListener(final DataBroker dataBroker, ElanInstanceManager elanInstanceManager,
            ItmRpcService itmRpcService) {
        super(Node.class, HwvtepNodeListener.class);
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.elanInstanceManager = elanInstanceManager;
    }

    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class);
    }

    @Override
    protected HwvtepNodeListener getDataChangeListener() {
        return HwvtepNodeListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    @Override
    protected void remove(InstanceIdentifier<Node> key, Node nodeDeleted) {
        String nodeId = nodeDeleted.getNodeId().getValue();
        LOG.debug("Received Node Remove Event for {}", nodeId);

        PhysicalSwitchAugmentation psAugmentation = nodeDeleted.getAugmentation(PhysicalSwitchAugmentation.class);
        if (psAugmentation != null) {
            String psName = psAugmentation.getHwvtepNodeName().getValue();
            LOG.info("Physical switch {} removed from node {} event received", psName, nodeId);

            L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
            if (l2GwDevice != null) {
                if (!L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                    L2GatewayCacheUtils.removeL2DeviceFromCache(psName);
                    LOG.debug("{} details removed from L2Gateway Cache", psName);
                } else {
                    LOG.debug("{} details are not removed from L2Gateway Cache as it has L2Gateway refrence", psName);
                }

                l2GwDevice.setConnected(false);
                ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(psName);
            } else {
                LOG.error("Unable to find L2 Gateway details for {}", psName);
            }
        } else {
            LOG.trace("Received Node Remove Event for {} is not related to Physical switch; it's not processed",
                    nodeId);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Node> key, Node nodeBefore, Node nodeAfter) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received Node Update Event: Node Before: {}, Node After: {}", nodeBefore, nodeAfter);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Node> key, Node nodeAdded) {
        String nodeId = nodeAdded.getNodeId().getValue();
        LOG.debug("Received Node Add Event for {}", nodeId);

        PhysicalSwitchAugmentation psAugmentation = nodeAdded.getAugmentation(PhysicalSwitchAugmentation.class);
        if (psAugmentation != null) {
            String psName = psAugmentation.getHwvtepNodeName().getValue();
            LOG.info("Physical switch {} added to node {} event received", psName, nodeId);

            L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(psName);
            if (l2GwDevice == null) {
                LOG.debug("{} details are not present in L2Gateway Cache; added now!", psName);

                l2GwDevice = new L2GatewayDevice();
                l2GwDevice.setDeviceName(psName);
                L2GatewayCacheUtils.addL2DeviceToCache(psName, l2GwDevice);
            } else {
                LOG.debug("{} details are present in L2Gateway Cache and same reference used for updates", psName);
            }

            l2GwDevice.setConnected(true);
            String hwvtepNodeId = getManagedByNodeId(psAugmentation.getManagedBy());
            l2GwDevice.setHwvtepNodeId(hwvtepNodeId);
            List<TunnelIps> tunnelIps = psAugmentation.getTunnelIps();
            if (tunnelIps != null) {
                for (TunnelIps tunnelIp : tunnelIps) {
                    IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                    l2GwDevice.addTunnelIp(tunnelIpAddr);
                    if (L2GatewayConnectionUtils.isGatewayAssociatedToL2Device(l2GwDevice)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("L2Gateway {} associated for {} physical switch; creating ITM tunnels for {}",
                                    l2GwDevice.getL2GatewayIds(), psName, tunnelIpAddr);
                        }

                        // It's a pre-provision scenario
                        // Initiate ITM tunnel creation
                        ElanL2GatewayUtils.createItmTunnels(itmRpcService, hwvtepNodeId, psName, tunnelIpAddr);

                        // Initiate Logical switch creation for associated L2
                        // Gateway Connections
                        List<L2gatewayConnection> l2GwConns = getAssociatedL2GwConnections(dataBroker,
                                l2GwDevice.getL2GatewayIds());
                        if (l2GwConns != null) {
                            LOG.debug("L2GatewayConnections associated for {} physical switch", psName);

                            for (L2gatewayConnection l2GwConn : l2GwConns) {
                                LOG.trace("L2GatewayConnection {} changes executed on physical switch {}",
                                        l2GwConn.getL2gatewayId(), psName);

                                L2GatewayConnectionUtils.addL2GatewayConnection(l2GwConn, psName);
                            }
                        }
                        //TODO handle deleted l2gw connections while the device is offline
                    }
                }
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("L2Gateway cache updated with below details: {}", l2GwDevice);
            }
        } else {
            LOG.trace("Received Node Add Event for {} is not related to Physical switch; it's not processed", nodeId);
        }
    }

    private List<L2gatewayConnection> getAssociatedL2GwConnections(DataBroker broker, List<Uuid> l2GatewayIds) {
        List<L2gatewayConnection> l2GwConnections = null;
        List<L2gatewayConnection> allL2GwConns = getAllL2gatewayConnections(broker);
        if (allL2GwConns != null) {
            l2GwConnections = new ArrayList<L2gatewayConnection>();
            for (Uuid l2GatewayId : l2GatewayIds) {
                for (L2gatewayConnection l2GwConn : allL2GwConns) {
                    if (l2GwConn.getL2gatewayId().equals(l2GatewayId)) {
                        l2GwConnections.add(l2GwConn);
                    }
                }
            }
        }
        return l2GwConnections;
    }

    protected List<L2gatewayConnection> getAllL2gatewayConnections(DataBroker broker) {
        InstanceIdentifier<L2gatewayConnections> inst = InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class);
        Optional<L2gatewayConnections> l2GwConns = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (l2GwConns.isPresent()) {
            return l2GwConns.get().getL2gatewayConnection();
        }
        return null;
    }

    private String getManagedByNodeId(HwvtepGlobalRef globalRef) {
        InstanceIdentifier<?> instId = globalRef.getValue();
        return instId.firstKeyOf(Node.class, NodeKey.class).getNodeId().getValue();
    }
}
