/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Listener to handle physical switch updates.
 */
public class HwvtepPhysicalSwitchListener
        extends AsyncDataTreeChangeListenerBase<PhysicalSwitchAugmentation, HwvtepPhysicalSwitchListener>
        implements ClusteredDataTreeChangeListener<PhysicalSwitchAugmentation>, AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepPhysicalSwitchListener.class);

    /** The data broker. */
    private DataBroker dataBroker;

    /** The itm rpc service. */
    private ItmRpcService itmRpcService;

    /**
     * Instantiates a new hwvtep physical switch listener.
     *
     * @param dataBroker
     *            the data broker
     * @param itmRpcService
     *            the itm rpc service
     */
    public HwvtepPhysicalSwitchListener(final DataBroker dataBroker, ItmRpcService itmRpcService) {
        super(PhysicalSwitchAugmentation.class, HwvtepPhysicalSwitchListener.class);
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#getWildCardPath()
     */
    @Override
    protected InstanceIdentifier<PhysicalSwitchAugmentation> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class)
                .augmentation(PhysicalSwitchAugmentation.class);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
    @Override
    protected HwvtepPhysicalSwitchListener getDataTreeChangeListener() {
        return HwvtepPhysicalSwitchListener.this;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#remove(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void remove(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
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
                        HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(l2GwDevice.getHwvtepNodeId())));
                MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION,
                        HwvtepSouthboundUtils.createInstanceIdentifier(nodeId));
            } else {
                LOG.debug("{} details are not removed from L2Gateway Cache as it has L2Gateway reference", psName);
            }

            l2GwDevice.setConnected(false);
            ElanL2GwCacheUtils.removeL2GatewayDeviceFromAllElanCache(psName);
        } else {
            LOG.error("Unable to find L2 Gateway details for {}", psName);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#update(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void update(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
            PhysicalSwitchAugmentation phySwitchBefore, PhysicalSwitchAugmentation phySwitchAfter) {
        NodeId nodeId = getNodeId(identifier);
        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "Received PhysicalSwitch Update Event for node {}: PhysicalSwitch Before: {}, PhysicalSwitch After: {}",
                    nodeId.getValue(), phySwitchBefore, phySwitchAfter);
        }
        String psName = phySwitchBefore.getHwvtepNodeName().getValue();
        LOG.info("Received physical switch {} update event for node {}", psName, nodeId.getValue());

        if (isTunnelIpNewlyConfigured(phySwitchBefore, phySwitchAfter)) {
            final L2GatewayDevice l2GwDevice = updateL2GatewayCache(psName, phySwitchAfter);

            ElanClusterUtils.runOnlyInLeaderNode(HwvtepSouthboundConstants.ELAN_ENTITY_NAME,
                    "handling Physical Switch add", new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            handleAdd(l2GwDevice);
                            return Collections.emptyList();
                        }
                    });
        } else {
            LOG.debug("Other updates in physical switch {} for node {}", psName, nodeId.getValue());
            // TODO: handle tunnel ip change
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#add(org.opendaylight.yangtools.yang.
     * binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<PhysicalSwitchAugmentation> identifier,
            final PhysicalSwitchAugmentation phySwitchAdded) {
        NodeId nodeId = getNodeId(identifier);
        final String psName = phySwitchAdded.getHwvtepNodeName().getValue();
        LOG.info("Received physical switch {} added event received for node {}", psName, nodeId.getValue());

        final L2GatewayDevice l2GwDevice = updateL2GatewayCache(psName, phySwitchAdded);

        ElanClusterUtils.runOnlyInLeaderNode(HwvtepSouthboundConstants.ELAN_ENTITY_NAME, "handling Physical Switch add",
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        handleAdd(l2GwDevice);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * Handle add.
     *
     * @param l2GwDevice
     *            the l2 gw device
     */
    private void handleAdd(L2GatewayDevice l2GwDevice) {
        String psName = l2GwDevice.getDeviceName();
        String hwvtepNodeId = l2GwDevice.getHwvtepNodeId();
        Set<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
        if (tunnelIps != null) {
            for (IpAddress tunnelIpAddr : tunnelIps) {
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
                    List<L2gatewayConnection> l2GwConns = L2GatewayConnectionUtils.getAssociatedL2GwConnections(
                            dataBroker, l2GwDevice.getL2GatewayIds());
                    if (l2GwConns != null) {
                        LOG.debug("L2GatewayConnections associated for {} physical switch", psName);

                        for (L2gatewayConnection l2GwConn : l2GwConns) {
                            LOG.trace("L2GatewayConnection {} changes executed on physical switch {}",
                                    l2GwConn.getL2gatewayId(), psName);

                            L2GatewayConnectionUtils.addL2GatewayConnection(l2GwConn, psName);
                        }
                    }
                    // TODO handle deleted l2gw connections while the device is
                    // offline
                }
            }
        }
    }

    /**
     * Update l2 gateway cache.
     *
     * @param psName
     *            the ps name
     * @param phySwitch
     *            the phy switch
     * @return the l2 gateway device
     */
    private L2GatewayDevice updateL2GatewayCache(String psName, PhysicalSwitchAugmentation phySwitch) {
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
        String hwvtepNodeId = getManagedByNodeId(phySwitch.getManagedBy());
        l2GwDevice.setHwvtepNodeId(hwvtepNodeId);

        List<TunnelIps> tunnelIps = phySwitch.getTunnelIps();
        if (tunnelIps != null && !tunnelIps.isEmpty()) {
            for (TunnelIps tunnelIp : tunnelIps) {
                IpAddress tunnelIpAddr = tunnelIp.getTunnelIpsKey();
                l2GwDevice.addTunnelIp(tunnelIpAddr);
            }
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("L2Gateway cache updated with below details: {}", l2GwDevice);
        }
        return l2GwDevice;
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
                && (phySwitchAfter.getTunnelIps() != null && !phySwitchAfter.getTunnelIps().isEmpty());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#close()
     */
    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
