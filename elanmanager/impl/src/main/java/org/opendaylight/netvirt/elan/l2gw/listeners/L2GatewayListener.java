/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.elan.ElanEntityOwnerStatusMonitor;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpNodeListener;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayInstanceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2gwZeroDayConfigUtil;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayListener extends AsyncClusteredDataTreeChangeListenerBase<L2gateway, L2GatewayListener>
        implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");
    private final DataBroker dataBroker;
    private final IL2gwService l2gwService;
    private final L2GatewayCache l2GatewayCache;
    private final HAOpNodeListener haOpNodeListener;
    private final HAOpClusteredListener haOpClusteredListener;
    private final ElanClusterUtils elanClusterUtils;
    private final L2gwZeroDayConfigUtil l2gwZeroDayConfigUtil;
    private final L2GwTransportZoneListener transportZoneListener;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public L2GatewayListener(final DataBroker dataBroker,
                             final IL2gwService l2gwService,
                             final L2GatewayCache l2GatewayCache,
                             HAOpNodeListener haOpNodeListener,
                             HAOpClusteredListener haOpClusteredListener,
                             L2GatewayInstanceRecoveryHandler l2GatewayInstanceRecoveryHandler,
                             ServiceRecoveryRegistry serviceRecoveryRegistry,
                             L2gwZeroDayConfigUtil l2gwZeroDayConfigUtil,
                             L2GwTransportZoneListener transportZoneListener,
                             ElanClusterUtils elanClusterUtils,
                             ElanEntityOwnerStatusMonitor elanEntityOwnerStatusMonitor) {
        this.dataBroker = dataBroker;
        this.l2gwService = l2gwService;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.l2gwZeroDayConfigUtil = l2gwZeroDayConfigUtil;
        this.transportZoneListener = transportZoneListener;
        this.l2GatewayCache = l2GatewayCache;
        this.haOpClusteredListener = haOpClusteredListener;
        this.haOpNodeListener = haOpNodeListener;
        this.elanClusterUtils = elanClusterUtils;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayInstanceRecoveryHandler.buildServiceRegistryKey(),
                this);
    }

    @PostConstruct
    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        LOG.info("{} init", getClass().getSimpleName());
        // registerListener(); called from L2GatewayConnection listener
    }

    public void registerListener() {
        LOG.info("Registering L2Gateway Listener");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    public void deregisterListener() {
        LOG.info("Deregistering L2GatewayListener");
        super.deregisterListener();
    }

    @Override
    protected InstanceIdentifier<L2gateway> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class);
    }

    @Override
    protected void add(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Adding L2gateway with ID: {}", input);

        List<Devices> l2Devices = input.getDevices();
        for (Devices l2Device : l2Devices) {
            LOG.info("Adding L2gateway device: {}", l2Device);
            addL2Device(l2Device, input);
        }
    }

    @Override
    protected void remove(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Removing L2gateway with ID: {}", input);
        List<L2gatewayConnection> connections = l2gwService
                .getL2GwConnectionsByL2GatewayId(input.getUuid());

        txRunner.callWithNewWriteOnlyTransactionAndSubmit(LogicalDatastoreType.CONFIGURATION, tx -> {
            try {
                for (L2gatewayConnection connection : connections) {
                    InstanceIdentifier<L2gatewayConnection> iid = InstanceIdentifier.create(Neutron.class)
                        .child(L2gatewayConnections.class).child(L2gatewayConnection.class, connection.getKey());
                    tx.delete(iid);
                }
            } catch (TransactionCommitFailedException e) {
                LOG.error("Failed to delete associated l2gwconnection while deleting l2gw {} with id beacause of {}",
                    input.getUuid(), e.getLocalizedMessage());
                //TODO :retry
            }
        });

        Collection<Devices> l2Devices = input.getDevices().values();
        for (Devices l2Device : l2Devices) {
            LOG.info("Removing L2gateway device: {}", l2Device);
            removeL2Device(l2Device, input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<L2gateway> identifier, L2gateway original, L2gateway update) {
        LOG.info("Updating L2gateway : key: {}, original value={}, update value={}", identifier, original, update);
        List<L2gatewayConnection> connections = l2gwService.getAssociatedL2GwConnections(
                Sets.newHashSet(update.getUuid()));
        if (connections == null) {
            LOG.warn("There are no connections associated with l2 gateway uuid {} name {}",
                    update.getUuid(), update.getName());
            return;
        }
        if (original.getDevices() == null) {
            connections.forEach(
                (connection) -> l2gwService.addL2GatewayConnection(connection));
            return;
        }
        elanClusterUtils.runOnlyInOwnerNode("l2gw.update", () -> {
            DeviceInterfaces updatedDeviceInterfaces = new DeviceInterfaces(update);
            List<ListenableFuture<Void>> fts = new ArrayList<>();
            fts.add(txRunner.callWithNewReadWriteTransactionAndSubmit(LogicalDatastoreType.CONFIGURATION, tx -> {
                original.getDevices().values()
                    .stream()
                    .filter((originalDevice) -> originalDevice.getInterfaces() != null)
                    .forEach((originalDevice) -> {
                        String deviceName = originalDevice.getDeviceName();
                        L2GatewayDevice l2GwDevice = l2GatewayCache.get(deviceName);
                        NodeId physicalSwitchNodeId = HwvtepSouthboundUtils.createManagedNodeId(
                            new NodeId(l2GwDevice.getHwvtepNodeId()), deviceName);
                        originalDevice.getInterfaces().values()
                            .stream()
                            .filter((intf) -> !updatedDeviceInterfaces.containsInterface(
                                deviceName, intf.getInterfaceName()))
                            .forEach((intf) -> {
                                connections.forEach((connection) -> {
                                    Integer vlanId = connection.getSegmentId();
                                    if (intf.getSegmentationIds() != null
                                        && !intf.getSegmentationIds().isEmpty()) {
                                        for (Integer vlan : intf.getSegmentationIds()) {
                                            HwvtepUtils.deleteVlanBinding(tx,
                                                physicalSwitchNodeId, intf.getInterfaceName(), vlan);
                                        }
                                    } else {
                                        LOG.info("Deleting vlan binding {} {} {}",
                                            physicalSwitchNodeId, intf.getInterfaceName(), vlanId);
                                        HwvtepUtils.deleteVlanBinding(tx, physicalSwitchNodeId,
                                            intf.getInterfaceName(), vlanId);
                                    }
                                });
                            });
                    });
            }));
            Futures.addCallback(fts.get(0), new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void success) {
                    LOG.info("Successfully deleted vlan bindings for l2gw update {}", update);
                        connections.forEach((l2GwConnection) ->
                                l2gwService.addL2GatewayConnection(l2GwConnection, null, update));
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Failed to delete vlan bindings as part of l2gw udpate {}", update);
                }
            }, MoreExecutors.directExecutor());
        });
    }

    private synchronized void addL2Device(Devices l2Device, L2gateway input) {
        String l2DeviceName = l2Device.getDeviceName();

        L2GatewayDevice l2GwDevice = l2GatewayCache.addOrGet(l2DeviceName);
        String hwvtepNodeId = l2GwDevice.getHwvtepNodeId();
        HwvtepHACache haCache = HwvtepHACache.getInstance();
        if (hwvtepNodeId == null) {
            scanNodesAndReplayDeviceGlobalNode(l2Device, input, l2DeviceName);
        } else if (!haCache.isHAParentNode(HwvtepHAUtil.convertToInstanceIdentifier(hwvtepNodeId))) {
            replayGlobalNode(l2Device, input, l2DeviceName, hwvtepNodeId);
        }
        l2GwDevice.addL2GatewayId(input.getUuid());

        if (l2GwDevice.getHwvtepNodeId() == null) {
            LOG.info("L2GW provisioning skipped for device {}",l2DeviceName);
        } else {
            transportZoneListener.createZeroDayForL2Device(l2GwDevice);
            LOG.info("Provisioning l2gw for device {}",l2DeviceName);
            l2gwService.provisionItmAndL2gwConnection(l2GwDevice, l2DeviceName, l2GwDevice.getHwvtepNodeId(),
                    l2GwDevice.getTunnelIp());
        }
    }

    private void removeL2Device(Devices l2Device, L2gateway input) {
        final String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = l2GatewayCache.get(l2DeviceName);
        if (l2GwDevice != null) {
            // Delete ITM tunnels if it's last Gateway deleted and device is connected
            // Also, do not delete device from cache if it's connected
            if (L2GatewayUtils.isLastL2GatewayBeingDeleted(l2GwDevice)) {
                if (l2GwDevice.isConnected()) {
                    /*
                    l2GwDevice.removeL2GatewayId(input.getUuid());
                    // Delete ITM tunnels
                    final String hwvtepId = l2GwDevice.getHwvtepNodeId();

                    final Set<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
                    jobCoordinator.enqueueJob(hwvtepId, () -> {
                        if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                                HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                            LOG.info("Deleting ITM Tunnels for {} connected to cluster node owner", l2DeviceName);
                            for (IpAddress tunnelIp : tunnelIps) {
                                L2GatewayUtils.deleteItmTunnels(itmRpcService, hwvtepId, l2DeviceName, tunnelIp);
                            }
                        } else {
                            LOG.info("ITM Tunnels are not deleted on the cluster node as this is not owner for {}",
                                    l2DeviceName);
                        }

                        return null;
                    });
                    */
                } else {
                    l2GatewayCache.remove(l2DeviceName);
                }
                l2GwDevice.removeL2GatewayId(input.getUuid());
                //Delete itm tunnels
                elanClusterUtils.runOnlyInOwnerNode(l2GwDevice.getDeviceName(),
                    "handling delete of l2gwdevice delete itm tunnels ",
                    () -> {
                        if (l2GwDevice.getHwvtepNodeId() == null) {
                            return Collections.emptyList();
                        }
                        // Cleaning up the config DS
                        NodeId nodeId = new NodeId(l2GwDevice.getHwvtepNodeId());
                        LOG.info("L2GatewayListener deleting the config nodes {} {}", nodeId, l2DeviceName);
                        NodeId psNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, l2DeviceName);
                        InstanceIdentifier<Node> psNodeIid = HwvtepSouthboundUtils.createInstanceIdentifier(psNodeId);
                        InstanceIdentifier<Node> globalIid = HwvtepSouthboundUtils.createInstanceIdentifier(nodeId);

                        List<ListenableFuture<Void>> result = new ArrayList<ListenableFuture<Void>>();
                        result.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(LogicalDatastoreType.CONFIGURATION,
                            tx -> {
                                LOG.info("Deleting the zero day config for l2gw delete {}", psNodeIid);
                                l2gwZeroDayConfigUtil.deleteZeroDayConfig(tx, globalIid, l2GwDevice);
                            }));
                        LOG.info("L2GatewayListener Deleting itm tunnels for {}", l2GwDevice.getDeviceName());
                        for (final IpAddress tunnelIpAddr :  l2GwDevice.getTunnelIps()) {
                            result.add(ElanL2GatewayUtils.deleteItmTunnels(tunnelIpAddr, dataBroker));
                            LOG.info("L2GatewayListener Deleting itm tunnel {}", tunnelIpAddr);
                        }
                        return result;
                    }
                );
            } else {
                l2GwDevice.removeL2GatewayId(input.getUuid());
                LOG.info("ITM tunnels are not deleted for {} as this device has other L2gateway associations",
                        l2DeviceName);
            }
        } else {
            LOG.error("L2GatewayListener Unable to find L2 Gateway details for {}", l2DeviceName);
        }
    }

    @Override
    protected L2GatewayListener getDataTreeChangeListener() {
        return this;
    }

    static class DeviceInterfaces {
        Map<String, Map<String, Interfaces>> deviceInterfacesMap = new HashMap<>();

        DeviceInterfaces(L2gateway l2gateway) {
            if (l2gateway.getDevices() != null) {
                l2gateway.getDevices().forEach((device) -> {
                    deviceInterfacesMap.putIfAbsent(device.getDeviceName(), new HashMap<>());
                    if (device.getInterfaces() != null) {
                        device.getInterfaces().forEach((intf) ->
                                deviceInterfacesMap.get(device.getDeviceName()).put(intf.getInterfaceName(), intf));
                    }
                });
            }
        }

        boolean containsInterface(String deviceName, String interfaceName) {
            if (deviceInterfacesMap.containsKey(deviceName)) {
                return deviceInterfacesMap.get(deviceName).containsKey(interfaceName);
            }
            return false;
        }
    }

    private void scanNodesAndReplayDeviceGlobalNode(Devices l2Device, L2gateway input, String l2DeviceName) {
        txRunner.callWithNewReadOnlyTransactionAndClose(LogicalDatastoreType.OPERATIONAL, tx -> {
            List<Node> allNodes = readAllOperNodes(tx);
            for (Node psNode : allNodes) {
                if (Objects.equals(HwvtepHAUtil.getPsName(psNode), l2DeviceName)) {
                    String globalNodeId = HwvtepHAUtil.getGlobalNodePathFromPSNode(psNode)
                        .firstKeyOf(Node.class).getNodeId().getValue();
                    replayGlobalNode(l2Device, input, l2DeviceName, globalNodeId);
                }
            }
        });

    }

    private Collection<Node> readAllOperNodes(ReadWriteTransaction tx) {
        Optional<Topology> topologyOptional = null;
        try {
            topologyOptional = tx.read(HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read oper nodes", e);
        }
        if (topologyOptional != null && topologyOptional.isPresent() && topologyOptional.get().getNode() != null) {
            return topologyOptional.get().getNode().values();
        }
        return Collections.emptyList();
    }


    private void replayGlobalNode(Devices l2Device, L2gateway input,
                                  String l2DeviceName, String hwvtepNodeId) {
        HwvtepHACache haCache = HwvtepHACache.getInstance();
        if (haCache.isHAParentNode(HwvtepHAUtil.convertToInstanceIdentifier(hwvtepNodeId))) {
            return;
        }
        InstanceIdentifier<Node> globalIid = HwvtepHAUtil.convertToInstanceIdentifier(hwvtepNodeId);
        InstanceIdentifier<Node> psIid = HwvtepHAUtil.convertToInstanceIdentifier(
                hwvtepNodeId + "/physicalswitch/" + l2DeviceName);
        replayGlobalNode(globalIid, psIid, l2Device, input, haCache, hwvtepNodeId, l2DeviceName);
    }

    private void replayGlobalNode(InstanceIdentifier<Node> globalIid,
                                  final InstanceIdentifier<Node> psIid,
                                  final Devices l2Device, final L2gateway input,
                                  HwvtepHACache haCache,
                                  String hwvtepNodeId,
                                  String l2DeviceName) {
        final String globalId = hwvtepNodeId;
        final Optional<Node> globalNode = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, globalIid);
        if (!globalNode.isPresent()) {
            LOG.error("replayGlobalNode Global Node not present in oper store {}", globalId);
            return;
        }
        final Optional<Node> psNode = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, psIid);

        txRunner.callWithNewReadWriteTransactionAndSubmit(LogicalDatastoreType.OPERATIONAL, tx -> {
            haOpClusteredListener.onGlobalNodeAdd(globalIid, globalNode.get(), tx);
            if (!haCache.isHAEnabledDevice(globalIid)) {
                LOG.error("replayGlobalNode Non ha node connected {}", globalId);
                return;
            }
            globalId = haCache.getParent(globalIid).firstKeyOf(Node.class).getNodeId().getValue();
            haOpNodeListener.onGlobalNodeAdd(globalIid, globalNode.get(), tx);
            if (!psNode.isPresent()) {
                LOG.error("replayGlobalNode ps node not present in oper store {}", psIid);
                return;
            }
            haOpNodeListener.onPsNodeAdd(psIid, psNode.get(), tx);
            PhysicalSwitchAugmentation psAugmentation = psNode.get().augmentation(
                PhysicalSwitchAugmentation.class);
            if (psAugmentation != null
                && psAugmentation.getTunnelIps() != null && !psAugmentation.getTunnelIps().isEmpty()) {
                l2GatewayCache.updateL2GatewayCache(
                    l2DeviceName, globalId, psAugmentation.getTunnelIps());
            } else {
                LOG.error("replayGlobalNode Failed to find tunnel ips for {}", psIid);
            }
        });

    }
}
