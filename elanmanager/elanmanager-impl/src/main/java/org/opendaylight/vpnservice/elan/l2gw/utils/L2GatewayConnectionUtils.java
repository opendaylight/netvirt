/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.elan.l2gw.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.elan.internal.ElanInstanceManager;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepLogicalSwitchListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepRemoteMcastMacListener;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.vpnservice.utils.clustering.ClusteringUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class L2GatewayConnectionUtils {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionUtils.class);

    public static boolean isGatewayAssociatedToL2Device(L2GatewayDevice l2GwDevice) {
        return (l2GwDevice.getL2GatewayIds().size() > 0);
    }

    public static L2gateway getNeutronL2gateway(DataBroker broker, Uuid l2GatewayId) {
        LOG.debug("getNeutronL2gateway for {}", l2GatewayId.getValue());
        InstanceIdentifier<L2gateway> inst = InstanceIdentifier.create(Neutron.class).child(L2gateways.class)
                .child(L2gateway.class, new L2gatewayKey(l2GatewayId));
        Optional<L2gateway> l2Gateway = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (l2Gateway.isPresent()) {
            return l2Gateway.get();
        }
        return null;
    }

    public static List<L2gateway> getL2gatewayList(DataBroker broker) {
        InstanceIdentifier<L2gateways> inst = InstanceIdentifier.create(Neutron.class).child(L2gateways.class);
        Optional<L2gateways> l2gateways = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);

        if (l2gateways.isPresent()) {
            return l2gateways.get().getL2gateway();
        }
        return null;
    }

    public static List<L2gatewayConnection> getAllL2gatewayConnections(DataBroker broker) {
        InstanceIdentifier<L2gatewayConnections> inst = InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class);
        Optional<L2gatewayConnections> l2GwConns = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (l2GwConns.isPresent()) {
            return l2GwConns.get().getL2gatewayConnection();
        }
        return null;
    }

    public static void addL2GatewayConnection(DataBroker broker, EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, ElanInstanceManager elanInstanceManager,
            L2gatewayConnection input) {
        addL2GatewayConnection(broker, entityOwnershipService, bindingNormalizedNodeSerializer, elanInstanceManager,
                input, null);
    }

    public static void addL2GatewayConnection(DataBroker broker, EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, ElanInstanceManager elanInstanceManager,
            L2gatewayConnection input, String l2GwDeviceName) {
        Uuid networkUuid = input.getNetworkId();
        ElanInstance elanInstance = elanInstanceManager.getElanInstanceByName(networkUuid.getValue());
        if (elanInstance == null || elanInstance.getVni() == null) {
            LOG.error("Neutron network with id {} is not present", networkUuid.getValue());
        } else {
            Uuid l2GatewayId = input.getL2gatewayId();
            L2gateway l2Gateway = getNeutronL2gateway(broker, l2GatewayId);
            if (l2Gateway == null) {
                LOG.error("L2Gateway with id {} is not present", l2GatewayId.getValue());
            } else {
                associateHwvtepsToElan(broker, entityOwnershipService, bindingNormalizedNodeSerializer, elanInstance,
                        l2Gateway, input.getSegmentId(), l2GwDeviceName);
            }
        }
    }

    public static void deleteL2GatewayConnection(DataBroker broker, EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, ElanInstanceManager elanInstanceManager,
            L2gatewayConnection input) {
        Uuid networkUuid = input.getNetworkId();
        ElanInstance elanInstance = elanInstanceManager.getElanInstanceByName(networkUuid.getValue());
        if (elanInstance == null) {
            LOG.error("Neutron network with id {} is not present", networkUuid.getValue());
        } else {
            Uuid l2GatewayId = input.getL2gatewayId();
            L2gateway l2Gateway = L2GatewayConnectionUtils.getNeutronL2gateway(broker, l2GatewayId);
            if (l2Gateway == null) {
                LOG.error("L2Gateway with id {} is not present", l2GatewayId.getValue());
            } else {
                disAssociateHwvtepsToElan(broker, entityOwnershipService, bindingNormalizedNodeSerializer, elanInstance,
                        l2Gateway, input.getSegmentId());
            }
        }
    }

    private static void disAssociateHwvtepsToElan(final DataBroker broker, EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, final ElanInstance elanInstance,
            L2gateway l2Gateway, final Integer defaultVlan) {
        final String elanName = elanInstance.getElanInstanceName();
        List<Devices> l2Devices = l2Gateway.getDevices();
        for (final Devices l2Device : l2Devices) {
            final String l2DeviceName = l2Device.getDeviceName();
            final L2GatewayDevice l2GatewayDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
            if (isL2GwDeviceConnected(l2GatewayDevice)) {//TODO handle delete while device is offline
                // Delete L2 Gateway device from 'ElanL2GwDevice' cache
                ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(elanName, l2GatewayDevice.getHwvtepNodeId());

                final String hwvtepId = l2GatewayDevice.getHwvtepNodeId();
                InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                        entityOwnershipService, HwvtepSouthboundConstants.HWVTEP_ENTITY_TYPE,
                        bindingNormalizedNodeSerializer.toYangInstanceIdentifier(iid));
                Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isOwner) {
                        if (isOwner) {
                            LOG.info("L2 Gateway device delete is triggered for {} connected to cluster owner node",
                                    l2DeviceName);

                            // Create DataStoreJobCoordinator jobs to create Logical
                            // switches on all physical switches
                            // which are part of L2 Gateway
                            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                            DisAssociateHwvtepFromElan disAssociateHwvtepToElan = new DisAssociateHwvtepFromElan(broker,
                                    l2GatewayDevice, elanInstance, l2Device, defaultVlan);
                            String jobKey = ElanL2GatewayUtils.getL2GatewayConnectionJobKey(hwvtepId, elanName);
                            dataStoreCoordinator.enqueueJob(jobKey, disAssociateHwvtepToElan,
                                    SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                        } else {
                            LOG.info("L2 Gateway device delete is not triggered on the cluster node as this is not " +
                                    "owner for {}", l2DeviceName);
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error("Failed to trigger L2 Gateway device delete action", error);
                    }
                });
            } else {
                LOG.error("could not handle connection delete L2 Gateway device with id {} is not present",
                        l2DeviceName);
            }
        }
    }

    private static void associateHwvtepsToElan(final DataBroker broker, EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, final ElanInstance elanInstance,
            L2gateway l2Gateway, final Integer defaultVlan, String l2GwDeviceName) {
        final String elanName = elanInstance.getElanInstanceName();
        List<Devices> l2Devices = l2Gateway.getDevices();
        for (final Devices l2Device : l2Devices) {
            final String l2DeviceName = l2Device.getDeviceName();
            // L2gateway can have more than one L2 Gw devices. Configure Logical Switch, VLAN mappings,...
            // only on the switch which has come up just now and exclude all other devices from
            // preprovisioning/re-provisioning
            if (l2GwDeviceName != null && !l2GwDeviceName.equals(l2DeviceName)) {
                continue;
            }
            final L2GatewayDevice l2GatewayDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
            if (isL2GwDeviceConnected(l2GatewayDevice)) {
                // Add L2 Gateway device to 'ElanL2GwDevice' cache
                final boolean createLogicalSwitch;
                LogicalSwitches logicalSwitch = HwvtepUtils.getLogicalSwitch(broker, LogicalDatastoreType.OPERATIONAL,
                        new NodeId(l2GatewayDevice.getHwvtepNodeId()), elanName);
                if (logicalSwitch == null) {
                    final HwvtepLogicalSwitchListener hwVTEPLogicalSwitchListener = new HwvtepLogicalSwitchListener(
                            l2GatewayDevice, elanName, l2Device, defaultVlan);
                    hwVTEPLogicalSwitchListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
                    createLogicalSwitch = true;
                } else {
                    addL2DeviceToElanL2GwCache(elanName, l2GatewayDevice);
                    createLogicalSwitch = false;
                }
                final String hwvtepId = l2GatewayDevice.getHwvtepNodeId();
                InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                        entityOwnershipService, HwvtepSouthboundConstants.HWVTEP_ENTITY_TYPE,
                        bindingNormalizedNodeSerializer.toYangInstanceIdentifier(iid));
                Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isOwner) {
                        if (isOwner) {
                            LOG.info("Creating Logical switch on {} connected to cluster owner node", l2DeviceName);

                            // Create DataStoreJobCoordinator jobs to create Logical
                            // switches on all physical switches
                            // which are part of L2 Gateway
                            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                            AssociateHwvtepToElan associateHwvtepToElan = new AssociateHwvtepToElan(broker,
                                    l2GatewayDevice, elanInstance, l2Device, defaultVlan, createLogicalSwitch);
                            String jobKey = ElanL2GatewayUtils.getL2GatewayConnectionJobKey(hwvtepId, elanName);
                            dataStoreCoordinator.enqueueJob(jobKey, associateHwvtepToElan,
                                    SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                        } else {
                            LOG.info("Logical switch creation is not triggered on the cluster node as this is not " +
                                    "owner for {}", l2DeviceName);
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error("Failed to trigger Logical switch creation action", error);
                    }
                });
            } else {
                LOG.error("L2 Gateway device with id {} is not present", l2DeviceName);
            }
        }
    }

    public static void addL2DeviceToElanL2GwCache(String elanName, L2GatewayDevice l2GatewayDevice) {
        L2GatewayDevice elanL2GwDevice = new L2GatewayDevice();
        elanL2GwDevice.setHwvtepNodeId(l2GatewayDevice.getHwvtepNodeId());
        elanL2GwDevice.setDeviceName(l2GatewayDevice.getDeviceName());
        elanL2GwDevice.setTunnelIps(l2GatewayDevice.getTunnelIps());
        ElanL2GwCacheUtils.addL2GatewayDeviceToCache(elanName, elanL2GwDevice);
    }

    private static boolean isL2GwDeviceConnected(L2GatewayDevice l2GwDevice) {
        return (l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null && l2GwDevice.isConnected());
    }

    private static class AssociateHwvtepToElan implements Callable<List<ListenableFuture<Void>>> {
        DataBroker broker;
        L2GatewayDevice l2GatewayDevice;
        ElanInstance elanInstance;
        Devices l2Device;
        Integer defaultVlan;
        boolean createLogicalSwitch;

        public AssociateHwvtepToElan(DataBroker broker, L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
                Devices l2Device, Integer defaultVlan, boolean createLogicalSwitch) {
            this.broker = broker;
            this.l2GatewayDevice = l2GatewayDevice;
            this.elanInstance = elanInstance;
            this.l2Device = l2Device;
            this.defaultVlan = defaultVlan;
            this.createLogicalSwitch = createLogicalSwitch;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            final String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanInstance.getElanInstanceName());

            // Create Logical Switch if it's not created already in
            // the device
            if (createLogicalSwitch) {
                ListenableFuture<Void> lsCreateFuture = createLogicalSwitch(l2GatewayDevice, elanInstance, l2Device);
                futures.add(lsCreateFuture);
            } else {
                // Logical switch is already created; do the rest of
                // configuration
                futures.add(ElanL2GatewayUtils.updateVlanBindingsInL2GatewayDevice(
                        new NodeId(l2GatewayDevice.getHwvtepNodeId()), logicalSwitchName, l2Device, defaultVlan));
                futures.add(ElanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceAdd(logicalSwitchName, l2GatewayDevice));
                HwvtepRemoteMcastMacListener list = new HwvtepRemoteMcastMacListener(ElanUtils.getDataBroker(),
                        logicalSwitchName, l2GatewayDevice,
                        new Callable<List<ListenableFuture<Void>>>() {

                        @Override
                        public List<ListenableFuture<Void>> call() {
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(ElanL2GatewayUtils.installElanMacsInL2GatewayDevice(
                                    logicalSwitchName, l2GatewayDevice));
                            return futures;
                        }}
                    );
            }

            return futures;
        }

        private ListenableFuture<Void> createLogicalSwitch(L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
                Devices l2Device) {
            final String logicalSwitchName = ElanL2GatewayUtils
                    .getLogicalSwitchFromElan(elanInstance.getElanInstanceName());
            String segmentationId = elanInstance.getVni().toString();

            // Register for Logical switch update in opearational DS
            final HwvtepLogicalSwitchListener hwVTEPLogicalSwitchListener = new HwvtepLogicalSwitchListener(
                    l2GatewayDevice, logicalSwitchName, l2Device, defaultVlan);
            hwVTEPLogicalSwitchListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);

            NodeId hwvtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
            InstanceIdentifier<LogicalSwitches> path = HwvtepSouthboundUtils
                    .createLogicalSwitchesInstanceIdentifier(hwvtepNodeId, new HwvtepNodeName(logicalSwitchName));
            LogicalSwitches logicalSwitch = HwvtepSouthboundUtils.createLogicalSwitch(logicalSwitchName,
                    elanInstance.getDescription(), segmentationId);

            ListenableFuture<Void> lsCreateFuture = HwvtepUtils.addLogicalSwitch(broker, hwvtepNodeId, logicalSwitch);
            Futures.addCallback(lsCreateFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void noarg) {
                    // Listener will be closed after all configuration completed
                    // on hwvtep by
                    // listener itself
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Successful in initiating logical switch {} creation", logicalSwitchName);
                    }
                }

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("Failed logical switch {} creation", logicalSwitchName, error);
                    try {
                        hwVTEPLogicalSwitchListener.close();
                    } catch (final Exception e) {
                        LOG.error("Error when cleaning up DataChangeListener.", e);
                    }
                }
            });
            return lsCreateFuture;
        }
    }

    private static class DisAssociateHwvtepFromElan implements Callable<List<ListenableFuture<Void>>> {
        DataBroker broker;
        L2GatewayDevice l2GatewayDevice;
        ElanInstance elanInstance;
        Devices l2Device;
        Integer defaultVlan;

        public DisAssociateHwvtepFromElan(DataBroker broker, L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
                Devices l2Device, Integer defaultVlan) {
            this.broker = broker;
            this.l2GatewayDevice = l2GatewayDevice;
            this.elanInstance = elanInstance;
            this.l2Device = l2Device;
            this.defaultVlan = defaultVlan;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            // Remove remote MACs and vlan mappings from physical port
            // Once all above configurations are deleted, delete logical
            // switch
            NodeId hwvtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
            String elanName = elanInstance.getElanInstanceName();
            futures.add(ElanL2GatewayUtils.deleteElanMacsFromL2GatewayDevice(l2GatewayDevice, elanName));
            futures.addAll(ElanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceDelete(elanInstance,
                    l2GatewayDevice));
            futures.addAll(ElanL2GatewayUtils.deleteL2GatewayDeviceUcastLocalMacsFromElan(l2GatewayDevice, elanName));
            futures.add(ElanL2GatewayUtils.deleteVlanBindingsFromL2GatewayDevice(hwvtepNodeId, l2Device, defaultVlan));
            Thread.sleep(30000);
            futures.add(HwvtepUtils.deleteLogicalSwitch(this.broker, hwvtepNodeId, elanName));

            return futures;
        }
    }
}
