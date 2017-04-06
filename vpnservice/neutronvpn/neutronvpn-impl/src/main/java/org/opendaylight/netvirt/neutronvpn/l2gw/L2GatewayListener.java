/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.l2gw;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.clustering.ClusteringUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayListener extends AsyncClusteredDataTreeChangeListenerBase<L2gateway, L2GatewayListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayListener.class);
    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final IL2gwService l2gwService;
    private final EntityOwnershipService entityOwnershipService;

    @Inject
    public L2GatewayListener(final DataBroker dataBroker, final EntityOwnershipService entityOwnershipService,
                             final ItmRpcService itmRpcService, IL2gwService l2gwService) {
        this.dataBroker = dataBroker;
        this.entityOwnershipService = entityOwnershipService;
        this.itmRpcService = itmRpcService;
        this.l2gwService = l2gwService;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        L2GatewayCacheUtils.createL2DeviceCache();
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

        List<Devices> l2Devices = input.getDevices();
        for (Devices l2Device : l2Devices) {
            LOG.trace("Removing L2gateway device: {}", l2Device);
            removeL2Device(l2Device, input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<L2gateway> identifier, L2gateway original, L2gateway update) {
        LOG.trace("Updating L2gateway : key: {}, original value={}, update value={}", identifier, original, update);
    }

    private synchronized void addL2Device(Devices l2Device, L2gateway input) {
        String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.updateCacheUponL2GatewayAdd(l2DeviceName, input.getUuid());
        if (l2GwDevice.getHwvtepNodeId() == null) {
            LOG.info("L2GW provisioning skipped for device {}",l2DeviceName);
        } else {
            LOG.info("Provisioning l2gw for device {}",l2DeviceName);
            l2gwService.provisionItmAndL2gwConnection(l2GwDevice, l2DeviceName, l2GwDevice.getHwvtepNodeId(),
                    l2GwDevice.getTunnelIp());
        }
    }

    private void removeL2Device(Devices l2Device, L2gateway input) {
        final String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
        if (l2GwDevice != null) {
            // Delete ITM tunnels if it's last Gateway deleted and device is connected
            // Also, do not delete device from cache if it's connected
            if (L2GatewayUtils.isLastL2GatewayBeingDeleted(l2GwDevice)) {
                if (l2GwDevice.isConnected()) {
                    l2GwDevice.removeL2GatewayId(input.getUuid());
                    // Delete ITM tunnels
                    final String hwvtepId = l2GwDevice.getHwvtepNodeId();
                    InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                    ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                            entityOwnershipService, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                            HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
                    final Set<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
                    Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean isOwner) {
                            if (isOwner) {
                                LOG.info("Deleting ITM Tunnels for {} connected to cluster node owner", l2DeviceName);
                                for (IpAddress tunnelIp : tunnelIps) {
                                    L2GatewayUtils.deleteItmTunnels(itmRpcService, hwvtepId, l2DeviceName, tunnelIp);
                                }
                            } else {
                                LOG.info("ITM Tunnels are not deleted on the cluster node as this is not owner for {}",
                                        l2DeviceName);
                            }
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            LOG.error("Failed to delete ITM tunnels", error);
                        }
                    });
                } else {
                    L2GatewayCacheUtils.removeL2DeviceFromCache(l2DeviceName);
                    // Cleaning up the config DS
                    NodeId nodeId = new NodeId(l2GwDevice.getHwvtepNodeId());
                    NodeId psNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, l2DeviceName);
                    //FIXME: These should be removed
                    MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            HwvtepSouthboundUtils.createInstanceIdentifier(nodeId));
                    MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
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
}
