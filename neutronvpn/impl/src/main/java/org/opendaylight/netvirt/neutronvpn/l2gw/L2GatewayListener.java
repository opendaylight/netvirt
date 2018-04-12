/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.l2gw;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayListener extends AsyncClusteredDataTreeChangeListenerBase<L2gateway, L2GatewayListener> {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final ItmRpcService itmRpcService;
    private final IL2gwService l2gwService;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final JobCoordinator jobCoordinator;
    private final L2GatewayCache l2GatewayCache;

    @Inject
    public L2GatewayListener(final DataBroker dataBroker, final EntityOwnershipService entityOwnershipService,
                             final ItmRpcService itmRpcService, final IL2gwService l2gwService,
                             final JobCoordinator jobCoordinator, final L2GatewayCache l2GatewayCache) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
        this.itmRpcService = itmRpcService;
        this.l2gwService = l2gwService;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayCache = l2GatewayCache;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<L2gateway> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class);
    }

    @Override
    protected void add(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Adding L2gateway with ID: {}", input.getUuid());

        List<Devices> l2Devices = input.getDevices();
        for (Devices l2Device : l2Devices) {
            LOG.trace("Adding L2gateway device: {}", l2Device);
            addL2Device(l2Device, input);
        }
    }

    @Override
    protected void remove(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Removing L2gateway with ID: {}", input.getUuid());
        List<L2gatewayConnection> connections = l2gwService
                .getL2GwConnectionsByL2GatewayId(input.getUuid());
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            for (L2gatewayConnection connection : connections) {
                InstanceIdentifier<L2gatewayConnection> iid = InstanceIdentifier.create(Neutron.class)
                        .child(L2gatewayConnections.class).child(L2gatewayConnection.class, connection.getKey());
                tx.delete(LogicalDatastoreType.CONFIGURATION, iid);
            }
            List<Devices> l2Devices = input.getDevices();
            for (Devices l2Device : l2Devices) {
                LOG.trace("Removing L2gateway device: {}", l2Device);
                removeL2Device(tx, l2Device, input);
            }
        }), LOG, "Failed to delete associated l2gwconnection while deleting l2gw {}", input.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<L2gateway> identifier, L2gateway original, L2gateway update) {
        LOG.trace("Updating L2gateway : key: {}, original value={}, update value={}", identifier, original, update);
        List<L2gatewayConnection> connections = l2gwService.getAssociatedL2GwConnections(
                Sets.newHashSet(update.getUuid()));
        if (connections == null) {
            LOG.warn("There are no connections associated with l2 gateway uuid {} name {}",
                    update.getUuid(), update.getName());
            return;
        }
        if (original.getDevices() == null) {
            connections.forEach(l2gwService::addL2GatewayConnection);
            return;
        }
        jobCoordinator.enqueueJob("l2gw.update", () -> {
            DeviceInterfaces updatedDeviceInterfaces = new DeviceInterfaces(update);
            ListenableFuture<Void> future = txRunner.callWithNewReadWriteTransactionAndSubmit(transaction -> {
                original.getDevices()
                        .stream()
                        .filter((originalDevice) -> originalDevice.getInterfaces() != null)
                        .forEach((originalDevice) -> {
                            String deviceName = originalDevice.getDeviceName();
                            L2GatewayDevice l2GwDevice = l2GatewayCache.get(deviceName);
                            NodeId physicalSwitchNodeId = HwvtepSouthboundUtils.createManagedNodeId(
                                    new NodeId(l2GwDevice.getHwvtepNodeId()), deviceName);
                            originalDevice.getInterfaces()
                                    .stream()
                                    .filter((intf) -> !updatedDeviceInterfaces.containsInterface(
                                            deviceName, intf.getInterfaceName()))
                                    .forEach((intf) -> connections.forEach((connection) -> {
                                        Integer vlanId = connection.getSegmentId();
                                        if (intf.getSegmentationIds() != null
                                                && !intf.getSegmentationIds().isEmpty()) {
                                            for (Integer vlan : intf.getSegmentationIds()) {
                                                HwvtepUtils.deleteVlanBinding(transaction,
                                                        physicalSwitchNodeId, intf.getInterfaceName(), vlan);
                                            }
                                        } else {
                                            LOG.debug("Deleting vlan binding {} {} {}",
                                                    physicalSwitchNodeId, intf.getInterfaceName(), vlanId);
                                            HwvtepUtils.deleteVlanBinding(transaction, physicalSwitchNodeId,
                                                    intf.getInterfaceName(), vlanId);
                                        }
                                    }));
                        });
            });
            Futures.addCallback(future, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void success) {
                    LOG.debug("Successfully deleted vlan bindings for l2gw update {}", update);
                    connections.forEach((l2GwConnection) ->
                            l2gwService.addL2GatewayConnection(l2GwConnection, null, update));
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Failed to delete vlan bindings as part of l2gw udpate {}", update);
                }
            }, MoreExecutors.directExecutor());
            return Collections.singletonList(future);
        }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
    }

    private synchronized void addL2Device(Devices l2Device, L2gateway input) {
        String l2DeviceName = l2Device.getDeviceName();

        L2GatewayDevice l2GwDevice = l2GatewayCache.addOrGet(l2DeviceName);
        l2GwDevice.addL2GatewayId(input.getUuid());
        if (l2GwDevice.getHwvtepNodeId() == null) {
            LOG.info("L2GW provisioning skipped for device {}",l2DeviceName);
        } else {
            LOG.info("Provisioning l2gw for device {}",l2DeviceName);
            l2gwService.provisionItmAndL2gwConnection(l2GwDevice, l2DeviceName, l2GwDevice.getHwvtepNodeId(),
                    l2GwDevice.getTunnelIp());
        }
    }

    private void removeL2Device(WriteTransaction tx, Devices l2Device, L2gateway input) {
        final String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = l2GatewayCache.get(l2DeviceName);
        if (l2GwDevice != null) {
            // Delete ITM tunnels if it's last Gateway deleted and device is connected
            // Also, do not delete device from cache if it's connected
            if (L2GatewayUtils.isLastL2GatewayBeingDeleted(l2GwDevice)) {
                if (l2GwDevice.isConnected()) {
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
                } else {
                    l2GatewayCache.remove(l2DeviceName);
                    // Cleaning up the config DS
                    NodeId nodeId = new NodeId(l2GwDevice.getHwvtepNodeId());
                    NodeId psNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, l2DeviceName);
                    //FIXME: These should be removed
                    tx.delete(LogicalDatastoreType.CONFIGURATION,
                            HwvtepSouthboundUtils.createInstanceIdentifier(nodeId));
                    tx.delete(LogicalDatastoreType.CONFIGURATION,
                            HwvtepSouthboundUtils.createInstanceIdentifier(psNodeId));

                }
            } else {
                l2GwDevice.removeL2GatewayId(input.getUuid());
                LOG.trace("ITM tunnels are not deleted for {} as this device has other L2gateway associations",
                        l2DeviceName);
            }
        } else {
            LOG.error("Unable to find L2 Gateway details for {}", l2DeviceName);
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
}
