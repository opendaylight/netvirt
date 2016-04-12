/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.l2gw.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.vpnservice.utils.clustering.ClusteringUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.port.attributes.VlanBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan._interface.forwarding.entries.ElanInterfaceMac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.AddL2GwDeviceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * It gathers a set of utility methods that handle ELAN configuration in external Devices (where external means
 * "not-CSS". As of now: TORs).
 *
 * It makes use of HwvtepUtils class located under ovsdb/hwvtepsouthbound project for low-level mdsal operations
 *
 * @author eperefr
 *
 */
public class ElanL2GatewayUtils {

    private static DataBroker         broker;
    private static ItmRpcService      itmRpcService;

    private static final Logger LOG = LoggerFactory.getLogger(ElanL2GatewayUtils.class);

    /**
     * Sets the data broker.
     *
     * @param dataBroker
     *            the new data broker
     */
    public static void setDataBroker(DataBroker dataBroker) {
        broker = dataBroker;
    }

    /**
     * Sets the itm rpc service.
     *
     * @param itmRpc
     *            the new itm rpc service
     */
    public static void setItmRpcService(ItmRpcService itmRpc) {
        itmRpcService = itmRpc;
    }

    /**
     * Installs the given MAC as a remote mac in all external devices (as of
     * now, TORs) that participate in the given Elan.
     *
     * @param elanInstance
     *            Elan to which the interface belongs to
     * @param dpId
     *            Id of the DPN where the macs are located. Needed for selecting
     *            the right tunnel
     * @param macAddresses
     *            the mac addresses
     */
    public static void installMacsInElanExternalDevices(ElanInstance elanInstance, BigInteger dpId,
            List<PhysAddress> macAddresses) {
        String logicalSwitchName = getElanFromLogicalSwitch(elanInstance.getElanInstanceName());
        ConcurrentMap<String, L2GatewayDevice> elanDevices = ElanL2GwCacheUtils
                .getAllElanL2GatewayDevicesFromCache(elanInstance.getElanInstanceName());
        for (L2GatewayDevice externalDevice : elanDevices.values()) {
            NodeId nodeId = new NodeId(externalDevice.getHwvtepNodeId());
            IpAddress dpnTepIp = getSourceDpnTepIp(dpId, nodeId);
            LOG.trace("Dpn Tep IP: {} for dpnId: {} and nodeId: {}", dpnTepIp, dpId, nodeId);
            if (dpnTepIp == null) {
                LOG.error("TEP IP not found for dpnId {} and nodeId {}", dpId, nodeId);
                continue;
            }
            installMacsInExternalDeviceAsRemoteUcastMacs(externalDevice.getHwvtepNodeId(), macAddresses,
                    logicalSwitchName, dpnTepIp);
        }
    }

    /**
     * Installs a list of Mac Addresses as remote Ucast address in an external
     * device using the hwvtep-southbound.
     *
     * @param deviceNodeId
     *            NodeId if the ExternalDevice where the macs must be installed
     *            in.
     * @param macAddresses
     *            List of Mac addresses to be installed in the external device.
     * @param logicalSwitchName
     *            the logical switch name
     * @param remoteVtepIp
     *            VTEP's IP in this CSS used for the tunnel with external
     *            device.
     */
    private static ListenableFuture<Void> installMacsInExternalDeviceAsRemoteUcastMacs(String deviceNodeId,
            List<PhysAddress> macAddresses, String logicalSwitchName, IpAddress remoteVtepIp) {
        NodeId nodeId = new NodeId(deviceNodeId);
        HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepSouthboundUtils
                .createHwvtepPhysicalLocatorAugmentation(String.valueOf(remoteVtepIp.getValue()));
        List<RemoteUcastMacs> macs = new ArrayList<RemoteUcastMacs>();
        for (PhysAddress mac : macAddresses) {
            // TODO: Query ARP cache to get IP address corresponding to
            // the MAC
            IpAddress ipAddress = null;
            macs.add(HwvtepSouthboundUtils.createRemoteUcastMac(nodeId, mac.getValue(), ipAddress, logicalSwitchName,
                    phyLocatorAug));
        }
        return HwvtepUtils.addRemoteUcastMacs(broker, nodeId, macs);
    }

    /**
     * Install macs in external device as remote ucast macs.
     *
     * @param elanName
     *            the elan name
     * @param lstElanInterfaceNames
     *            the lst Elan interface names
     * @param dpnId
     *            the dpn id
     * @param externalNodeId
     *            the external node id
     * @return the listenable future
     */
    public static ListenableFuture<Void> installMacsInExternalDeviceAsRemoteUcastMacs(String elanName,
            Set<String> lstElanInterfaceNames, BigInteger dpnId, NodeId externalNodeId) {
        SettableFuture<Void> future = SettableFuture.create();
        future.set(null);
        if (lstElanInterfaceNames == null || lstElanInterfaceNames.isEmpty()) {
            return future;
        }

        IpAddress dpnTepIp = getSourceDpnTepIp(dpnId, externalNodeId);
        if (dpnTepIp == null) {
            return future;
        }

        WriteTransaction transaction = broker.newWriteOnlyTransaction();
        HwvtepPhysicalLocatorAugmentation phyLocatorAug = HwvtepUtils.getPhysicalLocator(broker,
                LogicalDatastoreType.CONFIGURATION, externalNodeId, dpnTepIp);
        if (phyLocatorAug == null) {
            phyLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(String.valueOf(dpnTepIp.getValue()));
            HwvtepUtils.putPhysicalLocator(transaction, externalNodeId, phyLocatorAug);
        }

        String logicalSwitchName = getLogicalSwitchFromElan(elanName);
        for (String interfaceName : lstElanInterfaceNames) {
            ElanInterfaceMac elanInterfaceMac = ElanUtils.getElanInterfaceMacByInterfaceName(interfaceName);
            if (elanInterfaceMac != null && elanInterfaceMac.getMacEntry() != null) {
                for (MacEntry macEntry : elanInterfaceMac.getMacEntry()) {
                    // TODO: Query ARP cache to get IP address corresponding to
                    // the MAC
                    IpAddress ipAddress = null;
                    RemoteUcastMacs mac = HwvtepSouthboundUtils.createRemoteUcastMac(externalNodeId,
                            macEntry.getMacAddress().getValue(), ipAddress, logicalSwitchName, phyLocatorAug);
                    HwvtepUtils.putRemoteUcastMac(transaction, externalNodeId, mac);
                }
            }
        }
        LOG.debug("Installing macs in external device [{}] for dpn [{}], elan [{}], no of interfaces [{}]",
                externalNodeId.getValue(), dpnId, elanName, lstElanInterfaceNames.size());
        return transaction.submit();
    }

    /**
     * Removes the given MAC Addresses from all the External Devices belonging
     * to the specified ELAN.
     *
     * @param elanInstance
     *            the elan instance
     * @param macAddresses
     *            the mac addresses
     */
    public static void removeMacsFromElanExternalDevices(ElanInstance elanInstance, List<PhysAddress> macAddresses) {
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices = ElanL2GwCacheUtils
                .getAllElanL2GatewayDevicesFromCache(elanInstance.getElanInstanceName());
        for (L2GatewayDevice l2GatewayDevice : elanL2GwDevices.values()) {
            removeRemoteUcastMacsFromExternalDevice(l2GatewayDevice.getHwvtepNodeId(),
                    elanInstance.getElanInstanceName(), macAddresses);
        }
    }

    /**
     * Removes the given MAC Addresses from the specified External Device.
     *
     * @param deviceNodeId
     *            the device node id
     * @param logicalSwitchName
     * @param macAddresses
     *            the mac addresses
     * @return the listenable future
     */
    private static ListenableFuture<Void> removeRemoteUcastMacsFromExternalDevice(String deviceNodeId,
            String logicalSwitchName, List<PhysAddress> macAddresses) {
        NodeId nodeId = new NodeId(deviceNodeId);

        // TODO (eperefr)
        List<MacAddress> lstMac = Lists.transform(macAddresses, new Function<PhysAddress, MacAddress>() {
            @Override
            public MacAddress apply(PhysAddress physAddress) {
                return (physAddress != null) ? new MacAddress(physAddress.getValue()) : null;
            }
        });
        return HwvtepUtils.deleteRemoteUcastMacs(broker, nodeId, logicalSwitchName, lstMac);
    }

    public static ElanInstance getElanInstanceForUcastLocalMac(LocalUcastMacs localUcastMac) {
        Optional<LogicalSwitches> lsOpc = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL,
                (InstanceIdentifier<LogicalSwitches>) localUcastMac.getLogicalSwitchRef().getValue());
        if (lsOpc.isPresent()) {
            LogicalSwitches ls = lsOpc.get();
            if (ls != null) {
                // Logical switch name is Elan name
                String elanName = getElanFromLogicalSwitch(ls.getHwvtepNodeName().getValue());
                return ElanUtils.getElanInstanceByName(elanName);
            } else {
                String macAddress = localUcastMac.getMacEntryKey().getValue();
                LOG.error("Could not find logical_switch for {} being added/deleted", macAddress);
            }
        }
        return null;
    }

    /**
     * Install external device local macs in dpn.
     *
     * @param dpnId
     *            the dpn id
     * @param l2gwDeviceNodeId
     *            the l2gw device node id
     * @param elan
     *            the elan
     */
    public static void installL2gwDeviceLocalMacsInDpn(BigInteger dpnId, NodeId l2gwDeviceNodeId, ElanInstance elan) {
        String elanName = elan.getElanInstanceName();
        L2GatewayDevice l2gwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName,
                l2gwDeviceNodeId.getValue());
        if (l2gwDevice == null) {
            LOG.debug("L2 gw device not found in elan cache for device name {}", l2gwDeviceNodeId.getValue());
            return;
        }

        List<LocalUcastMacs> l2gwDeviceLocalMacs = l2gwDevice.getUcastLocalMacs();
        if (l2gwDeviceLocalMacs != null && !l2gwDeviceLocalMacs.isEmpty()) {
            for (LocalUcastMacs localUcastMac : l2gwDeviceLocalMacs) {
                ElanUtils.installDmacFlowsToExternalRemoteMac(dpnId, l2gwDeviceNodeId.getValue(), elan.getElanTag(),
                        elan.getVni(), localUcastMac.getMacEntryKey().getValue(), elanName);
            }
        }
        LOG.debug("Installing L2gw device [{}] local macs [size: {}] in dpn [{}] for elan [{}]",
                l2gwDeviceNodeId.getValue(), l2gwDeviceLocalMacs.size(), dpnId, elanName);
    }

    public static void installL2GwUcastMacInElan(EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, final ElanInstance elan,
            L2GatewayDevice extL2GwDevice, final String macToBeAdded) {
        final String extDeviceNodeId = extL2GwDevice.getHwvtepNodeId();
        final String elanInstanceName = elan.getElanInstanceName();

        // Retrieve all participating DPNs in this Elan. Populate this MAC in DMAC table.
        // Looping through all DPNs in order to add/remove mac flows in their DMAC table
        List<DpnInterfaces> elanDpns = ElanUtils.getInvolvedDpnsInElan(elanInstanceName);
        for (DpnInterfaces elanDpn : elanDpns) {
            final BigInteger dpnId = elanDpn.getDpId();
            final String nodeId = getNodeIdFromDpnId(dpnId);

            ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                    entityOwnershipService, MDSALUtil.NODE_PREFIX, nodeId);
            Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean isOwner) {
                    if (isOwner) {
                        LOG.info("Installing DMAC flows in {} connected to cluster node owner", dpnId.toString());

                        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                        dataStoreCoordinator.enqueueJob(nodeId, new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                return ElanUtils.installDmacFlowsToExternalRemoteMac(dpnId, extDeviceNodeId,
                                        elan.getElanTag(), elan.getVni(), macToBeAdded, elanInstanceName);
                            }
                        }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                    } else {
                        LOG.info("Install DMAC flows is not executed on the cluster node as this is not owner " +
                                    "for the DPN {}", dpnId.toString());
                    }
                }

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("Failed to install DMAC flows", error);
                }
            });
        }

        final IpAddress extL2GwDeviceTepIp = extL2GwDevice.getTunnelIp();
        final List<PhysAddress> macList = new ArrayList<PhysAddress>();
        macList.add(new PhysAddress(macToBeAdded));

        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices =
                ElanL2GwCacheUtils.getAllElanL2GatewayDevicesFromCache(elanInstanceName);
        for (L2GatewayDevice otherDevice : elanL2GwDevices.values()) {
            if (!otherDevice.getHwvtepNodeId().equals(extDeviceNodeId) && !areMLAGDevices(extL2GwDevice, otherDevice)) {
                final String hwvtepId = otherDevice.getHwvtepNodeId();
                InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                        entityOwnershipService, HwvtepSouthboundConstants.HWVTEP_ENTITY_TYPE,
                        bindingNormalizedNodeSerializer.toYangInstanceIdentifier(iid));
                Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isOwner) {
                        if (isOwner) {
                            LOG.info("Adding DMAC entry in {} connected to cluster node owner", hwvtepId);

                            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                            dataStoreCoordinator.enqueueJob(hwvtepId, new Callable<List<ListenableFuture<Void>>>() {
                                @Override
                                public List<ListenableFuture<Void>> call() throws Exception {
                                    final String logicalSwitchName = getLogicalSwitchFromElan(elanInstanceName);
                                    ListenableFuture<Void> installFuture = installMacsInExternalDeviceAsRemoteUcastMacs(
                                            hwvtepId, macList, logicalSwitchName, extL2GwDeviceTepIp);

                                    Futures.addCallback(installFuture, new FutureCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void noarg) {
                                            if (LOG.isTraceEnabled()) {
                                                LOG.trace("Successful in initiating ucast_remote_macs addition" +
                                                        "related to {} in {}", logicalSwitchName, hwvtepId);
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable error) {
                                            LOG.error(String.format("Failed adding ucast_remote_macs related to " +
                                                    "%s in %s", logicalSwitchName, hwvtepId), error);
                                        }
                                    });

                                    return Lists.newArrayList(installFuture);
                                }
                            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                        } else {
                            LOG.info("DMAC entry addition is not executed on the cluster node as this is not owner for " +
                                    "the Hwvtep {}", hwvtepId);
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error("Failed to install DMAC entry", error);
                    }
                });
            }
        }
    }

    public static void unInstallL2GwUcastMacFromElan(EntityOwnershipService entityOwnershipService,
            BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, final ElanInstance elan,
            L2GatewayDevice extL2GwDevice, final LocalUcastMacs macToBeRemoved) {
        final String extDeviceNodeId = extL2GwDevice.getHwvtepNodeId();
        final String elanInstanceName = elan.getElanInstanceName();

        // Retrieve all participating DPNs in this Elan. Populate this MAC in DMAC table.
        // Looping through all DPNs in order to add/remove mac flows in their DMAC table
        List<DpnInterfaces> elanDpns = ElanUtils.getInvolvedDpnsInElan(elanInstanceName);
        for (DpnInterfaces elanDpn : elanDpns) {
            final BigInteger dpnId = elanDpn.getDpId();
            final String nodeId = getNodeIdFromDpnId(dpnId);

            ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                    entityOwnershipService, MDSALUtil.NODE_PREFIX, nodeId);
            Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean isOwner) {
                    if (isOwner) {
                        LOG.info("Uninstalling DMAC flows from {} connected to cluster node owner",
                                dpnId.toString());

                        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                        dataStoreCoordinator.enqueueJob(nodeId, new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                return ElanUtils.deleteDmacFlowsToExternalMac(elan.getElanTag(), dpnId,
                                        extDeviceNodeId, macToBeRemoved.getMacEntryKey().getValue());
                            }
                        }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                    } else {
                        LOG.info("Uninstall DMAC flows is not executed on the cluster node as this is not owner " +
                                    "for the DPN {}", dpnId.toString());
                    }
                }

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("Failed to uninstall DMAC flows", error);
                }
            });
        }

        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices =
                ElanL2GwCacheUtils.getAllElanL2GatewayDevicesFromCache(elanInstanceName);
        for (L2GatewayDevice otherDevice : elanL2GwDevices.values()) {
            if (!otherDevice.getHwvtepNodeId().equals(extDeviceNodeId) && !areMLAGDevices(extL2GwDevice, otherDevice)) {
                final String hwvtepId = otherDevice.getHwvtepNodeId();
                final NodeId hwvtepNodeId = new NodeId(hwvtepId);
                InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(hwvtepNodeId);
                ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                        entityOwnershipService, HwvtepSouthboundConstants.HWVTEP_ENTITY_TYPE,
                        bindingNormalizedNodeSerializer.toYangInstanceIdentifier(iid));
                Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isOwner) {
                        if (isOwner) {
                            LOG.info("Removing DMAC entry from {} connected to cluster node owner", hwvtepId);

                            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                            dataStoreCoordinator.enqueueJob(hwvtepId, new Callable<List<ListenableFuture<Void>>>() {
                                @Override
                                public List<ListenableFuture<Void>> call() throws Exception {
                                    final String logicalSwitchName = getLogicalSwitchFromElan(elanInstanceName);
                                    ListenableFuture<Void> uninstallFuture = HwvtepUtils.deleteRemoteUcastMac(broker,
                                            hwvtepNodeId, logicalSwitchName, macToBeRemoved.getMacEntryKey());

                                    Futures.addCallback(uninstallFuture, new FutureCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void noarg) {
                                            if (LOG.isTraceEnabled()) {
                                                LOG.trace("Successful in initiating ucast_remote_macs deletion " +
                                                        "related to {} in {}", logicalSwitchName, hwvtepId);
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable error) {
                                            LOG.error(String.format("Failed removing ucast_remote_macs related " +
                                                    "to %s in %s", logicalSwitchName, hwvtepId), error);
                                        }
                                    });

                                    return Lists.newArrayList(uninstallFuture);
                                }
                            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
                        } else {
                            LOG.info("DMAC entry removal is not executed on the cluster node as this is not owner for " +
                                    "the Hwvtep {}", hwvtepId);
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error("Failed to uninstall DMAC entry", error);
                    }
                });
            }
        }
    }

    /**
     * Delete elan macs from L2 gateway device.<br>
     * This includes deleting ELAN mac table entries plus external device
     * UcastLocalMacs which are part of the same ELAN.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @param elanName
     *            the elan name
     * @return the listenable future
     */
    public static ListenableFuture<Void> deleteElanMacsFromL2GatewayDevice(L2GatewayDevice l2GatewayDevice,
            String elanName) {
        List<MacAddress> elanMacTableEntries = getElanMacTableEntries(elanName);
        List<MacAddress> elanL2GatewayDevicesLocalMacs = getElanL2GatewayDevicesLocalMacs(l2GatewayDevice, elanName);

        List<MacAddress> lstElanLocalMacs = new ArrayList<>(elanMacTableEntries);
        lstElanLocalMacs.addAll(elanL2GatewayDevicesLocalMacs);

        return HwvtepUtils.deleteRemoteUcastMacs(broker, new NodeId(l2GatewayDevice.getHwvtepNodeId()),
                elanName, lstElanLocalMacs);
    }

    /**
     * Gets the elan mac table entries.
     *
     * @param elanName
     *            the elan name
     * @return the elan mac table entries as list
     */
    public static List<MacAddress> getElanMacTableEntries(String elanName) {
        MacTable macTable = ElanUtils.getElanMacTable(elanName);
        if (macTable == null || macTable.getMacEntry() == null || macTable.getMacEntry().isEmpty()) {
            LOG.trace("MacTable is empty for elan: {}", elanName);
            return Collections.emptyList();
        }
        List<MacAddress> lstMacs = Lists.transform(macTable.getMacEntry(), new Function<MacEntry, MacAddress>() {
            @Override
            public MacAddress apply(MacEntry macEntry) {
                return (macEntry != null) ? new MacAddress(macEntry.getMacAddress().getValue()) : null;
            }
        });
        return lstMacs;
    }

    /**
     * Gets the elan l2 gateway devices local macs.
     *
     * @param l2GwDeviceToBeExcluded
     *            the l2 gw device to be excluded
     * @param elanName
     *            the elan name
     * @return the elan l2 gateway devices local macs
     */
    public static List<MacAddress> getElanL2GatewayDevicesLocalMacs(L2GatewayDevice l2GwDeviceToBeExcluded,
            String elanName) {
        List<MacAddress> lstL2GatewayDeviceMacs = new ArrayList<>();

        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevicesFromCache = ElanL2GwCacheUtils
                .getAllElanL2GatewayDevicesFromCache(elanName);
        if (elanL2GwDevicesFromCache != null) {
            for (L2GatewayDevice otherDevice : elanL2GwDevicesFromCache.values()) {
                if (!otherDevice.getHwvtepNodeId().equals(l2GwDeviceToBeExcluded.getHwvtepNodeId())) {
                    List<LocalUcastMacs> lstUcastLocalMacs = otherDevice.getUcastLocalMacs();
                    if (lstUcastLocalMacs != null) {
                        List<MacAddress> l2GwDeviceMacs = Lists.transform(lstUcastLocalMacs,
                                new Function<LocalUcastMacs, MacAddress>() {
                                    @Override
                                    public MacAddress apply(LocalUcastMacs localUcastMac) {
                                        return (localUcastMac != null) ? localUcastMac.getMacEntryKey() : null;
                                    }
                                });
                        lstL2GatewayDeviceMacs.addAll(l2GwDeviceMacs);
                    }
                }
            }
        }
        return lstL2GatewayDeviceMacs;
    }

    /**
     * Install ELAN macs in L2 Gateway device.<br>
     * This includes installing ELAN mac table entries plus external device
     * UcastLocalMacs which are part of the same ELAN.
     *
     * @param elanName
     *            the elan name
     * @param l2GatewayDevice
     *            the l2 gateway device which has to be configured
     * @return the listenable future
     */
    public static ListenableFuture<Void> installElanMacsInL2GatewayDevice(String elanName,
            L2GatewayDevice l2GatewayDevice) {
        String logicalSwitchName = getLogicalSwitchFromElan(elanName);
        NodeId hwVtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());

        List<RemoteUcastMacs> lstL2GatewayDevicesMacs = getL2GatewayDevicesUcastLocalMacsAsRemoteUcastMacs(elanName,
                l2GatewayDevice, hwVtepNodeId, logicalSwitchName);
        List<RemoteUcastMacs> lstElanMacTableEntries = getElanMacTableEntriesAsRemoteUcastMacs(elanName,
                l2GatewayDevice, hwVtepNodeId, logicalSwitchName);

        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<>(lstL2GatewayDevicesMacs);
        lstRemoteUcastMacs.addAll(lstElanMacTableEntries);

        ListenableFuture<Void> future = HwvtepUtils.addRemoteUcastMacs(broker, hwVtepNodeId, lstRemoteUcastMacs);

        LOG.info("Added RemoteUcastMacs entries [{}] in config DS. NodeID: {}, LogicalSwitch: {}",
                lstRemoteUcastMacs.size(), hwVtepNodeId.getValue(), logicalSwitchName);
        return future;
    }

    /**
     * Gets the l2 gateway devices ucast local macs as remote ucast macs.
     *
     * @param elanName
     *            the elan name
     * @param l2GatewayDeviceToBeConfigured
     *            the l2 gateway device to be configured
     * @param hwVtepNodeId
     *            the hw vtep node Id to be configured
     * @param logicalSwitchName
     *            the logical switch name
     * @return the l2 gateway devices macs as remote ucast macs
     */
    public static List<RemoteUcastMacs> getL2GatewayDevicesUcastLocalMacsAsRemoteUcastMacs(String elanName,
            L2GatewayDevice l2GatewayDeviceToBeConfigured, NodeId hwVtepNodeId, String logicalSwitchName) {
        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<RemoteUcastMacs>();
        ConcurrentMap<String, L2GatewayDevice> elanL2GwDevicesFromCache = ElanL2GwCacheUtils
                .getAllElanL2GatewayDevicesFromCache(elanName);

        if (elanL2GwDevicesFromCache != null) {
            for (L2GatewayDevice otherDevice : elanL2GwDevicesFromCache.values()) {
                if (l2GatewayDeviceToBeConfigured.getHwvtepNodeId().equals(otherDevice.getHwvtepNodeId())) {
                    continue;
                }
                if (!areMLAGDevices(l2GatewayDeviceToBeConfigured, otherDevice)) {
                    List<LocalUcastMacs> lstUcastLocalMacs = otherDevice.getUcastLocalMacs();
                    if (lstUcastLocalMacs != null) {
                        for (LocalUcastMacs localUcastMac : lstUcastLocalMacs) {
                            HwvtepPhysicalLocatorAugmentation physLocatorAug = HwvtepSouthboundUtils
                                    .createHwvtepPhysicalLocatorAugmentation(
                                            String.valueOf(otherDevice.getTunnelIp().getValue()));
                            RemoteUcastMacs remoteUcastMac = HwvtepSouthboundUtils.createRemoteUcastMac(hwVtepNodeId,
                                    localUcastMac.getMacEntryKey().getValue(), localUcastMac.getIpaddr(),
                                    logicalSwitchName, physLocatorAug);
                            lstRemoteUcastMacs.add(remoteUcastMac);
                        }
                    }
                }
            }
        }
        return lstRemoteUcastMacs;
    }

    /**
     * Are MLAG devices.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @param otherL2GatewayDevice
     *            the other l2 gateway device
     * @return true, if both the specified l2 gateway devices are part of same
     *         MLAG
     */
    public static boolean areMLAGDevices(L2GatewayDevice l2GatewayDevice, L2GatewayDevice otherL2GatewayDevice) {
        // If tunnel IPs are same, then it is considered to be part of same MLAG
        return Objects.equals(l2GatewayDevice.getTunnelIp(), otherL2GatewayDevice.getTunnelIp());
    }

    /**
     * Gets the elan mac table entries as remote ucast macs. <br>
     * Note: ELAN MAC table only contains internal switches MAC's. It doesn't
     * contain external device MAC's.
     *
     * @param elanName
     *            the elan name
     * @param l2GatewayDeviceToBeConfigured
     *            the l2 gateway device to be configured
     * @param hwVtepNodeId
     *            the hw vtep node id
     * @param logicalSwitchName
     *            the logical switch name
     * @return the elan mac table entries as remote ucast macs
     */
    public static List<RemoteUcastMacs> getElanMacTableEntriesAsRemoteUcastMacs(String elanName,
            L2GatewayDevice l2GatewayDeviceToBeConfigured, NodeId hwVtepNodeId, String logicalSwitchName) {
        List<RemoteUcastMacs> lstRemoteUcastMacs = new ArrayList<RemoteUcastMacs>();

        MacTable macTable = ElanUtils.getElanMacTable(elanName);
        if (macTable == null || macTable.getMacEntry() == null || macTable.getMacEntry().isEmpty()) {
            LOG.trace("MacTable is empty for elan: {}", elanName);
            return lstRemoteUcastMacs;
        }

        for (MacEntry macEntry : macTable.getMacEntry()) {
            BigInteger dpnId = ElanUtils.getDpidFromInterface(macEntry.getInterface());
            if (dpnId == null) {
                LOG.error("DPN ID not found for interface {}", macEntry.getInterface());
                continue;
            }

            IpAddress dpnTepIp = getSourceDpnTepIp(dpnId, hwVtepNodeId);
            LOG.trace("Dpn Tep IP: {} for dpnId: {} and nodeId: {}", dpnTepIp, dpnId, hwVtepNodeId.getValue());
            if (dpnTepIp == null) {
                LOG.error("TEP IP not found for dpnId {} and nodeId {}", dpnId, hwVtepNodeId.getValue());
                continue;
            }
            HwvtepPhysicalLocatorAugmentation physLocatorAug = HwvtepSouthboundUtils
                    .createHwvtepPhysicalLocatorAugmentation(String.valueOf(dpnTepIp.getValue()));
            // TODO: Query ARP cache to get IP address corresponding to the
            // MAC
            IpAddress ipAddress = null;
            RemoteUcastMacs remoteUcastMac = HwvtepSouthboundUtils.createRemoteUcastMac(hwVtepNodeId,
                    macEntry.getMacAddress().getValue(), ipAddress, logicalSwitchName, physLocatorAug);
            lstRemoteUcastMacs.add(remoteUcastMac);
        }
        return lstRemoteUcastMacs;
    }

    /**
     * Gets the external tunnel interface name.
     *
     * @param sourceNode
     *            the source node
     * @param dstNode
     *            the dst node
     * @return the external tunnel interface name
     */
    public static String getExternalTunnelInterfaceName(String sourceNode, String dstNode) {
        String tunnelInterfaceName = null;
        try {
            Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = itmRpcService
                    .getExternalTunnelInterfaceName(new GetExternalTunnelInterfaceNameInputBuilder()
                            .setSourceNode(sourceNode).setDestinationNode(dstNode).build());

            RpcResult<GetExternalTunnelInterfaceNameOutput> rpcResult = output.get();
            if (rpcResult.isSuccessful()) {
                tunnelInterfaceName = rpcResult.getResult().getInterfaceName();
                LOG.debug("Tunnel interface name: {} for sourceNode: {} and dstNode: {}", tunnelInterfaceName,
                        sourceNode, dstNode);
            } else {
                LOG.warn("RPC call to ITM.GetExternalTunnelInterfaceName failed with error: {}",
                        rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Failed to get external tunnel interface name for sourceNode: {} and dstNode: {}: {} ",
                    sourceNode, dstNode, e);
        }
        return tunnelInterfaceName;
    }

    /**
     * Gets the source dpn tep ip.
     *
     * @param srcDpnId
     *            the src dpn id
     * @param dstHwVtepNodeId
     *            the dst hw vtep node id
     * @return the dpn tep ip
     */
    public static IpAddress getSourceDpnTepIp(BigInteger srcDpnId, NodeId dstHwVtepNodeId) {
        IpAddress dpnTepIp = null;
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(srcDpnId),
                dstHwVtepNodeId.getValue());
        if (tunnelInterfaceName != null) {
            Interface tunnelInterface = getInterfaceFromConfigDS(new InterfaceKey(tunnelInterfaceName), broker);
            if (tunnelInterface != null) {
                dpnTepIp = tunnelInterface.getAugmentation(IfTunnel.class).getTunnelSource();
            } else {
                LOG.warn("Tunnel interface not found for tunnelInterfaceName {}", tunnelInterfaceName);
            }
        } else {
            LOG.warn("Tunnel interface name not found for srcDpnId {} and dstHwVtepNodeId {}", srcDpnId,
                    dstHwVtepNodeId);
        }
        return dpnTepIp;
    }

    /**
     * Update vlan bindings in l2 gateway device.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @param hwVtepDevice
     *            the hardware device
     * @param defaultVlanId
     *            the default vlan id
     * @return the listenable future
     */
    public static ListenableFuture<Void> updateVlanBindingsInL2GatewayDevice(NodeId nodeId, String logicalSwitchName,
            Devices hwVtepDevice, Integer defaultVlanId) {
        if (hwVtepDevice == null || hwVtepDevice.getInterfaces() == null || hwVtepDevice.getInterfaces().isEmpty()) {
            String errMsg = "HwVtepDevice is null or interfaces are empty.";
            LOG.error(errMsg);
            return Futures.immediateFailedFuture(new RuntimeException(errMsg));
        }

        WriteTransaction transaction = broker.newWriteOnlyTransaction();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces deviceInterface : hwVtepDevice
                .getInterfaces()) {
            List<VlanBindings> vlanBindings = new ArrayList<>();
            if (deviceInterface.getSegmentationIds() != null && !deviceInterface.getSegmentationIds().isEmpty()) {
                for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                    vlanBindings.add(HwvtepSouthboundUtils.createVlanBinding(nodeId, vlanId, logicalSwitchName));
                }
            } else {
                // Use defaultVlanId (specified in L2GatewayConnection) if Vlan
                // ID not specified at interface level.
                vlanBindings.add(HwvtepSouthboundUtils.createVlanBinding(nodeId, defaultVlanId, logicalSwitchName));
            }
            HwvtepUtils.mergeVlanBindings(transaction, nodeId, hwVtepDevice.getDeviceName(),
                    deviceInterface.getInterfaceName(), vlanBindings);
        }
        ListenableFuture<Void> future = transaction.submit();
        LOG.info("Updated Hwvtep VlanBindings in config DS. NodeID: {}, LogicalSwitch: {}", nodeId.getValue(),
                logicalSwitchName);
        return future;
    }

    /**
     * Delete vlan bindings from l2 gateway device.
     *
     * @param nodeId
     *            the node id
     * @param hwVtepDevice
     *            the hw vtep device
     * @param defaultVlanId
     *            the default vlan id
     * @return the listenable future
     */
    public static ListenableFuture<Void> deleteVlanBindingsFromL2GatewayDevice(NodeId nodeId, Devices hwVtepDevice,
            Integer defaultVlanId) {
        if (hwVtepDevice == null || hwVtepDevice.getInterfaces() == null || hwVtepDevice.getInterfaces().isEmpty()) {
            String errMsg = "HwVtepDevice is null or interfaces are empty.";
            LOG.error(errMsg);
            return Futures.immediateFailedFuture(new RuntimeException(errMsg));
        }
        NodeId physicalSwitchNodeId = HwvtepSouthboundUtils.createManagedNodeId(nodeId, hwVtepDevice.getDeviceName());

        WriteTransaction transaction = broker.newWriteOnlyTransaction();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.devices.Interfaces deviceInterface : hwVtepDevice
                .getInterfaces()) {
            String phyPortName = deviceInterface.getInterfaceName();
            if (deviceInterface.getSegmentationIds() != null && !deviceInterface.getSegmentationIds().isEmpty()) {
                for (Integer vlanId : deviceInterface.getSegmentationIds()) {
                    HwvtepUtils.deleteVlanBinding(transaction, physicalSwitchNodeId, phyPortName, vlanId);
                }
            } else {
                // Use defaultVlanId (specified in L2GatewayConnection) if Vlan
                // ID not specified at interface level.
                HwvtepUtils.deleteVlanBinding(transaction, physicalSwitchNodeId, phyPortName, defaultVlanId);
            }
        }
        ListenableFuture<Void> future = transaction.submit();

        LOG.info("Deleted Hwvtep VlanBindings from config DS. NodeID: {}, hwVtepDevice: {}, defaultVlanId: {} ",
                nodeId.getValue(), hwVtepDevice, defaultVlanId);
        return future;
    }

    /**
     * Gets the elan name from logical switch name.
     *
     * @param logicalSwitchName
     *            the logical switch name
     * @return the elan name from logical switch name
     */
    public static String getElanFromLogicalSwitch(String logicalSwitchName) {
        // Assuming elan name is same as logical switch name
        String elanName = logicalSwitchName;
        return elanName;
    }

    /**
     * Gets the logical switch name from elan name.
     *
     * @param elanName
     *            the elan name
     * @return the logical switch from elan name
     */
    public static String getLogicalSwitchFromElan(String elanName) {
        // Assuming logical switch name is same as elan name
        String logicalSwitchName = elanName;
        return logicalSwitchName;
    }

    /**
     * Gets the l2 gateway connection job key.
     *
     * @param nodeId
     *            the node id
     * @param logicalSwitchName
     *            the logical switch name
     * @return the l2 gateway connection job key
     */
    public static String getL2GatewayConnectionJobKey(String nodeId, String logicalSwitchName) {
        return new StringBuilder(nodeId).append(logicalSwitchName).toString();
    }

    public static InstanceIdentifier<Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> interfaceInstanceIdentifierBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    public static Interface getInterfaceFromConfigDS(InterfaceKey interfaceKey, DataBroker dataBroker) {
        InstanceIdentifier<Interface> interfaceId = getInterfaceIdentifier(interfaceKey);
        Optional<Interface> interfaceOptional = IfmUtil.read(LogicalDatastoreType.CONFIGURATION, interfaceId, dataBroker);
        if (!interfaceOptional.isPresent()) {
            return null;
        }

        return interfaceOptional.get();
    }

    /**
     * Delete l2 gateway device ucast local macs from elan.<br>
     * Deletes macs from internal ELAN nodes and also on rest of external l2
     * gateway devices which are part of the ELAN.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device whose ucast local macs to be deleted
     *            from elan
     * @param elanName
     *            the elan name
     * @return the listenable future
     */
    public static List<ListenableFuture<Void>> deleteL2GatewayDeviceUcastLocalMacsFromElan(
            L2GatewayDevice l2GatewayDevice, String elanName) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        ElanInstance elan = ElanUtils.getElanInstanceByName(elanName);
        if (elan == null) {
            LOG.error("Could not find Elan by name: {}", elanName);
            return futures;
        }

        List<LocalUcastMacs> lstLocalUcastMacs = l2GatewayDevice.getUcastLocalMacs();
        if (lstLocalUcastMacs != null) {
            for (LocalUcastMacs localUcastMac : lstLocalUcastMacs) {
                List<DpnInterfaces> dpnInterfaces = ElanUtils.getInvolvedDpnsInElan(elanName);
                if (dpnInterfaces != null) {
                    // TODO: Need to check if it can be optimized
                    for (DpnInterfaces elanDpn : dpnInterfaces) {
                        ElanUtils.deleteDmacFlowsToExternalMac(elan.getElanTag(), elanDpn.getDpId(),
                                l2GatewayDevice.getHwvtepNodeId(), localUcastMac.getMacEntryKey().getValue());
                    }
                }
            }

            List<MacAddress> lstMac = Lists.transform(lstLocalUcastMacs, new Function<LocalUcastMacs, MacAddress>() {
                @Override
                public MacAddress apply(LocalUcastMacs mac) {
                    return (mac != null) ? mac.getMacEntryKey() : null;
                }
            });

            ConcurrentMap<String, L2GatewayDevice> elanL2GwDevices = ElanL2GwCacheUtils
                    .getAllElanL2GatewayDevicesFromCache(elanName);
            for (L2GatewayDevice otherDevice : elanL2GwDevices.values()) {
                if (!otherDevice.getHwvtepNodeId().equals(l2GatewayDevice.getHwvtepNodeId())) {
                    futures.add(HwvtepUtils.deleteRemoteUcastMacs(broker, new NodeId(otherDevice.getHwvtepNodeId()),
                            elanName, lstMac));
                }
            }
        }
        return futures;
    }

    public static void createItmTunnels(ItmRpcService itmRpcService, String hwvtepId, String psName,
            IpAddress tunnelIp) {
        AddL2GwDeviceInputBuilder builder = new AddL2GwDeviceInputBuilder();
        builder.setTopologyId(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID.getValue());
        builder.setNodeId(HwvtepSouthboundUtils.createManagedNodeId(new NodeId(hwvtepId), psName).getValue());
        builder.setIpAddress(tunnelIp);
        try {
            Future<RpcResult<Void>> result = itmRpcService.addL2GwDevice(builder.build());
            RpcResult<Void> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                LOG.info("Created ITM tunnels for {}", hwvtepId);
            } else {
                LOG.error("Failed to create ITM Tunnels: ", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("RPC to create ITM tunnels failed", e);
        }
    }

    public static String getNodeIdFromDpnId(BigInteger dpnId) {
        return MDSALUtil.NODE_PREFIX + MDSALUtil.SEPARATOR + dpnId.toString();
    }

}
