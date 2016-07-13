/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.SettableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance.Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.L3vpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterAssociatedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterAssociatedToVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterDisassociatedFromVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterDisassociatedFromVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetAddedToVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetDeletedFromVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetUpdatedInVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.InterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.createl3vpn.input.L3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getl3vpn.output.L3vpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getl3vpn.output.L3vpnInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import java.util.EventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NeutronvpnManager implements NeutronvpnService, AutoCloseable, EventListener {

    private static final Logger logger = LoggerFactory.getLogger(NeutronvpnManager.class);
    private final DataBroker broker;
    private LockManagerService lockManager;
    private NeutronvpnNatManager nvpnNatManager;
    IMdsalApiManager mdsalUtil;
    private NotificationPublishService notificationPublishService;
    private NotificationService notificationService;
    private NeutronFloatingToFixedIpMappingChangeListener floatingIpMapListener;
    Boolean isExternalVpn;

    /**
     * @param db           - dataBroker reference
     * @param mdsalManager - MDSAL Util API access
     */
    public NeutronvpnManager(final DataBroker db, IMdsalApiManager mdsalManager,NotificationPublishService notiPublishService,
                             NotificationService notiService, NeutronvpnNatManager vpnNatMgr,
                             NeutronFloatingToFixedIpMappingChangeListener neutronFloatingToFixedIpMappingChangeListener) {
        broker = db;
        mdsalUtil = mdsalManager;
        nvpnNatManager = vpnNatMgr;
        notificationPublishService = notiPublishService;
        notificationService = notiService;
        floatingIpMapListener = neutronFloatingToFixedIpMappingChangeListener;
    }

    public void setLockManager(LockManagerService lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public void close() throws Exception {
        logger.info("Neutron VPN Manager Closed");
    }

    protected Subnetmap updateSubnetNode(Uuid subnetId, String subnetIp, Uuid tenantId, Uuid networkId, Uuid routerId,
                                         Uuid vpnId) {
        Subnetmap subnetmap = null;
        SubnetmapBuilder builder = null;
        boolean isLockAcquired = false;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        try {
            Optional<Subnetmap> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            logger.debug("updating Subnet :read: ");
            if (sn.isPresent()) {
                builder = new SubnetmapBuilder(sn.get());
                logger.debug("updating Subnet :existing: ");
            } else {
                builder = new SubnetmapBuilder().setKey(new SubnetmapKey(subnetId)).setId(subnetId);
                logger.debug("updating Subnet :new: ");
            }

            if (subnetIp != null) {
                builder.setSubnetIp(subnetIp);
            }
            if (routerId != null) {
                builder.setRouterId(routerId);
            }
            if (networkId != null) {
                builder.setNetworkId(networkId);
            }
            if (vpnId != null) {
                builder.setVpnId(vpnId);
            }
            if (tenantId != null) {
                builder.setTenantId(tenantId);
            }

            subnetmap = builder.build();
            isLockAcquired = NeutronvpnUtils.lock(lockManager, subnetId.getValue());
            logger.debug("Creating/Updating subnetMap node: {} ", subnetId.getValue());
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
        } catch (Exception e) {
            logger.error("Updation of subnetMap failed for node: {}", subnetId.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, subnetId.getValue());
            }
        }
        return subnetmap;
    }

    protected Subnetmap removeFromSubnetNode(Uuid subnetId, Uuid networkId, Uuid routerId, Uuid vpnId, Uuid portId) {
        Subnetmap subnetmap = null;
        boolean isLockAcquired = false;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        try {
            Optional<Subnetmap> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (routerId != null) {
                    builder.setRouterId(null);
                }
                if (networkId != null) {
                    builder.setNetworkId(null);
                }
                if (vpnId != null) {
                    builder.setVpnId(null);
                }
                if (portId != null && builder.getPortList() != null) {
                    List<Uuid> portList = builder.getPortList();
                    portList.remove(portId);
                    builder.setPortList(portList);
                }

                subnetmap = builder.build();
                isLockAcquired = NeutronvpnUtils.lock(lockManager, subnetId.getValue());
                logger.debug("Removing from existing subnetmap node: {} ", subnetId.getValue());
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            } else {
                logger.warn("removing from non-existing subnetmap node: {} ", subnetId.getValue());
            }
        } catch (Exception e) {
            logger.error("Removal from subnetmap failed for node: {}", subnetId.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, subnetId.getValue());
            }
        }
        return subnetmap;
    }

    protected Subnetmap updateSubnetmapNodeWithPorts(Uuid subnetId, Uuid portId, Uuid directPortId) {
        Subnetmap subnetmap = null;
        boolean isLockAcquired = false;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
        try {
            Optional<Subnetmap> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (null != portId) {
                    List<Uuid> portList = builder.getPortList();
                    if (null == portList) {
                        portList = new ArrayList<Uuid>();
                    }
                    portList.add(portId);
                    builder.setPortList(portList);
                    logger.debug("Updating existing subnetmap node {} with port {}", subnetId.getValue(),
                            portId.getValue());
                }
                if (null != directPortId) {
                    List<Uuid> directPortList = builder.getDirectPortList();
                    if (null == directPortList) {
                        directPortList = new ArrayList<Uuid>();
                    }
                    directPortList.add(directPortId);
                    builder.setDirectPortList(directPortList);
                    logger.debug("Updating existing subnetmap node {} with port {}", subnetId.getValue(),
                            directPortId.getValue());
                }
                subnetmap = builder.build();
                isLockAcquired = NeutronvpnUtils.lock(lockManager, subnetId.getValue());
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            } else {
                logger.error("Trying to update non-existing subnetmap node {} ", subnetId.getValue());
            }
        } catch (Exception e) {
            logger.error("Updating port list of a given subnetMap failed for node: {} with exception{}",
                    subnetId.getValue(), e);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, subnetId.getValue());
            }
        }
        return subnetmap;
    }

    protected Subnetmap removePortsFromSubnetmapNode(Uuid subnetId, Uuid portId, Uuid directPortId) {
        Subnetmap subnetmap = null;
        boolean isLockAcquired = false;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
        try {
            Optional<Subnetmap> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (null != portId && null != builder.getPortList()) {
                    List<Uuid> portList = builder.getPortList();
                    portList.remove(portId);
                    builder.setPortList(portList);
                    logger.debug("Removing port {} from existing subnetmap node: {} ", portId.getValue(),
                            subnetId.getValue());
                }
                if (null != directPortId && null != builder.getDirectPortList()) {
                    List<Uuid> directPortList = builder.getDirectPortList();
                    directPortList.remove(directPortId);
                    builder.setDirectPortList(directPortList);
                    logger.debug("Removing direct port {} from existing subnetmap node: {} ", directPortId.getValue(),
                            subnetId.getValue());
                }
                subnetmap = builder.build();
                isLockAcquired = NeutronvpnUtils.lock(lockManager, subnetId.getValue());
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            } else {
                logger.error("Trying to remove port from non-existing subnetmap node {}", subnetId.getValue());
            }
        } catch (Exception e) {
            logger.error("Removing a port from port list of a subnetmap failed for node: {} with expection {}",
                    subnetId.getValue(), e);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, subnetId.getValue());
            }
        }
        return subnetmap;
    }

    protected void deleteSubnetMapNode(Uuid subnetId) {
        boolean isLockAcquired = false;
        InstanceIdentifier<Subnetmap> subnetMapIdentifier = InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        logger.debug("removing subnetMap node: {} ", subnetId.getValue());
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, subnetId.getValue());
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, subnetMapIdentifier);
        } catch (Exception e) {
            logger.error("Delete subnetMap node failed for subnet : {} ", subnetId.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, subnetId.getValue());
            }
        }
    }

    private void updateVpnInstanceNode(String vpnName, List<String> rd, List<String> irt, List<String> ert) {

        VpnInstanceBuilder builder = null;
        List<VpnTarget> vpnTargetList = new ArrayList<>();
        boolean isLockAcquired = false;
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class).
                child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        try {
            Optional<VpnInstance> optionalVpn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    vpnIdentifier);
            logger.debug("Creating/Updating a new vpn-instance node: {} ", vpnName);
            if (optionalVpn.isPresent()) {
                builder = new VpnInstanceBuilder(optionalVpn.get());
                logger.debug("updating existing vpninstance node");
            } else {
                builder = new VpnInstanceBuilder().setKey(new VpnInstanceKey(vpnName)).setVpnInstanceName(vpnName);
            }
            if (irt != null && !irt.isEmpty()) {
                if (ert != null && !ert.isEmpty()) {
                    List<String> commonRT = new ArrayList<>(irt);
                    commonRT.retainAll(ert);

                    for (String common : commonRT) {
                        irt.remove(common);
                        ert.remove(common);
                        VpnTarget vpnTarget = new VpnTargetBuilder().setKey(new VpnTargetKey(common)).setVrfRTValue
                                (common).setVrfRTType(VpnTarget.VrfRTType.Both).build();
                        vpnTargetList.add(vpnTarget);
                    }
                }
                for (String importRT : irt) {
                    VpnTarget vpnTarget = new VpnTargetBuilder().setKey(new VpnTargetKey(importRT)).setVrfRTValue
                            (importRT).setVrfRTType(VpnTarget.VrfRTType.ImportExtcommunity).build();
                    vpnTargetList.add(vpnTarget);
                }
            }

            if (ert != null && !ert.isEmpty()) {
                for (String exportRT : ert) {
                    VpnTarget vpnTarget = new VpnTargetBuilder().setKey(new VpnTargetKey(exportRT)).setVrfRTValue
                            (exportRT).setVrfRTType(VpnTarget.VrfRTType.ExportExtcommunity).build();
                    vpnTargetList.add(vpnTarget);
                }
            }

            VpnTargets vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();

            Ipv4FamilyBuilder ipv4vpnBuilder = new Ipv4FamilyBuilder().setVpnTargets(vpnTargets);

            if (rd != null && !rd.isEmpty()) {
                ipv4vpnBuilder.setRouteDistinguisher(rd.get(0));
            }

            VpnInstance newVpn = builder.setIpv4Family(ipv4vpnBuilder.build()).build();
            isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnName);
            logger.debug("Creating/Updating vpn-instance for {} ", vpnName);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier, newVpn);
        } catch (Exception e) {
            logger.error("Update VPN Instance node failed for node: {} {} {} {}", vpnName, rd, irt, ert);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, vpnName);
            }
        }
    }

    private void deleteVpnMapsNode(Uuid vpnid) {
        boolean isLockAcquired = false;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnid)).build();
        logger.debug("removing vpnMaps node: {} ", vpnid.getValue());
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnid.getValue());
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        } catch (Exception e) {
            logger.error("Delete vpnMaps node failed for vpn : {} ", vpnid.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, vpnid.getValue());
            }
        }
    }

    private void updateVpnMaps(Uuid vpnId, String name, Uuid router, Uuid tenantId, List<Uuid> networks) {
        VpnMapBuilder builder;
        boolean isLockAcquired = false;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        try {
            Optional<VpnMap> optionalVpnMap = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    vpnMapIdentifier);
            if (optionalVpnMap.isPresent()) {
                builder = new VpnMapBuilder(optionalVpnMap.get());
            } else {
                builder = new VpnMapBuilder().setKey(new VpnMapKey(vpnId)).setVpnId(vpnId);
            }

            if (name != null) {
                builder.setName(name);
            }
            if (tenantId != null) {
                builder.setTenantId(tenantId);
            }
            if (router != null) {
                builder.setRouterId(router);
            }
            if (networks != null) {
                List<Uuid> nwList = builder.getNetworkIds();
                if (nwList == null) {
                    nwList = new ArrayList<>();
                }
                nwList.addAll(networks);
                builder.setNetworkIds(nwList);
            }

            isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnId.getValue());
            logger.debug("Creating/Updating vpnMaps node: {} ", vpnId.getValue());
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, builder.build());
            logger.debug("VPNMaps DS updated for VPN {} ", vpnId.getValue());
        } catch (Exception e) {
            logger.error("UpdateVpnMaps failed for node: {} ", vpnId.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, vpnId.getValue());
            }
        }
    }

    private void clearFromVpnMaps(Uuid vpnId, Uuid routerId, List<Uuid> networkIds) {
        boolean isLockAcquired = false;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            VpnMapBuilder vpnMapBuilder = new VpnMapBuilder(vpnMap);
            if (routerId != null) {
                if (vpnMap.getNetworkIds() == null && routerId.equals(vpnMap.getVpnId())) {
                    try {
                        // remove entire node in case of internal VPN
                        isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnId.getValue());
                        logger.debug("removing vpnMaps node: {} ", vpnId);
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
                    } catch (Exception e) {
                        logger.error("Deletion of vpnMaps node failed for vpn {}", vpnId.getValue());
                    } finally {
                        if (isLockAcquired) {
                            NeutronvpnUtils.unlock(lockManager, vpnId.getValue());
                        }
                    }
                    return;
                }
                vpnMapBuilder.setRouterId(null);
            }
            if (networkIds != null) {
                List<Uuid> vpnNw = vpnMap.getNetworkIds();
                for (Uuid nw : networkIds) {
                    vpnNw.remove(nw);
                }
                if (vpnNw.isEmpty()) {
                    logger.debug("setting networks null in vpnMaps node: {} ", vpnId.getValue());
                    vpnMapBuilder.setNetworkIds(null);
                } else {
                    vpnMapBuilder.setNetworkIds(vpnNw);
                }
            }

            try {
                isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnId.getValue());
                logger.debug("clearing from vpnMaps node: {} ", vpnId.getValue());
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMapBuilder.build
                        ());
            } catch (Exception e) {
                logger.error("Clearing from vpnMaps node failed for vpn {}", vpnId.getValue());
            } finally {
                if (isLockAcquired) {
                    NeutronvpnUtils.unlock(lockManager, vpnId.getValue());
                }
            }
        } else {
            logger.error("VPN : {} not found", vpnId.getValue());
        }
        logger.debug("Clear from VPNMaps DS successful for VPN {} ", vpnId.getValue());
    }

    private void deleteVpnInstance(Uuid vpnId) {
        boolean isLockAcquired = false;
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class).
                child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue())).build();
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, vpnId.getValue());
            logger.debug("Deleting vpnInstance {}", vpnId.getValue());
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
        } catch (Exception e) {
            logger.error("Deletion of VPNInstance node failed for VPN {}", vpnId.getValue());
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, vpnId.getValue());
            }
        }
    }

    protected void createVpnInterface(Uuid vpnId, Port port) {
        boolean isLockAcquired = false;
        if (vpnId == null || port == null) {
            return;
        }
        String infName = port.getUuid().getValue();
        List<Adjacency> adjList = new ArrayList<>();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);

        // find router associated to vpn
        Uuid routerId = NeutronvpnUtils.getRouterforVpn(broker, vpnId);
        Router rtr = null;
        if (routerId != null) {
            rtr = NeutronvpnUtils.getNeutronRouter(broker, routerId);
        }
        // find all subnets to which this port is associated
        List<FixedIps> ips = port.getFixedIps();
        // create adjacency list
        for (FixedIps ip : ips) {
            // create vm adjacency
            StringBuilder IpPrefixBuild = new StringBuilder(ip.getIpAddress().getIpv4Address().getValue());
            String IpPrefix = IpPrefixBuild.append("/32").toString();
            Adjacency vmAdj = new AdjacencyBuilder().setKey(new AdjacencyKey(IpPrefix)).setIpAddress(IpPrefix)
                    .setMacAddress(port.getMacAddress().getValue()).build();
            adjList.add(vmAdj);
            // create extra route adjacency
            if (rtr != null && rtr.getRoutes() != null) {
                List<Routes> routeList = rtr.getRoutes();
                List<Adjacency> erAdjList = addAdjacencyforExtraRoute(routeList, false, infName);
                if (erAdjList != null && !erAdjList.isEmpty()) {
                    adjList.addAll(erAdjList);
                }
            }
        }
        // create vpn-interface on this neutron port
        Adjacencies adjs = new AdjacenciesBuilder().setAdjacency(adjList).build();
        VpnInterfaceBuilder vpnb = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(infName)).
                setName(infName).setVpnInstanceName(vpnId.getValue()).addAugmentation(Adjacencies.class, adjs);
        VpnInterface vpnIf = vpnb.build();

        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, infName);
            logger.debug("Creating vpn interface {}", vpnIf);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, vpnIf);
        } catch (Exception ex) {
            logger.error("Creation of vpninterface {} failed due to {}", infName, ex);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, infName);
            }
        }
    }

    protected void deleteVpnInterface(Port port) {

        if (port != null) {
            boolean isLockAcquired = false;
            String infName = port.getUuid().getValue();
            InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);

            try {
                isLockAcquired = NeutronvpnUtils.lock(lockManager, infName);
                logger.debug("Deleting vpn interface {}", infName);
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier);
            } catch (Exception ex) {
                logger.error("Deletion of vpninterface {} failed due to {}", infName, ex);
            } finally {
                if (isLockAcquired) {
                    NeutronvpnUtils.unlock(lockManager, infName);
                }
            }
        }
    }

    protected void updateVpnInterface(Uuid vpnId, Port port) {
        if (vpnId == null || port == null) {
            return;
        }
        boolean isLockAcquired = false;
        String infName = port.getUuid().getValue();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);
        try {
            Optional<VpnInterface> optionalVpnInterface = NeutronvpnUtils.read(broker, LogicalDatastoreType
                    .CONFIGURATION, vpnIfIdentifier);
            if (optionalVpnInterface.isPresent()) {
                VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get());
                VpnInterface vpnIf = vpnIfBuilder.setVpnInstanceName(vpnId.getValue()).build();
                isLockAcquired = NeutronvpnUtils.lock(lockManager, infName);
                logger.debug("Updating vpn interface {}", vpnIf);
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, vpnIf);
            } else {
                logger.error("VPN Interface {} not found", infName);
            }
        } catch (Exception ex) {
            logger.error("Updation of vpninterface {} failed due to {}", infName, ex);
        } finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, infName);
            }
        }
    }

    public void createL3InternalVpn(Uuid vpn, String name, Uuid tenant, List<String> rd, List<String> irt, List<String> ert,
                                    Uuid router, List<Uuid> networks) {

        // Update VPN Instance node
        updateVpnInstanceNode(vpn.getValue(), rd, irt, ert);

        // Update local vpn-subnet DS
        updateVpnMaps(vpn, name, router, tenant, networks);

        if (router != null) {
            Uuid existingVpn = NeutronvpnUtils.getVpnForRouter(broker, router, true);
            if (existingVpn != null) {
                List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, router);
                if (routerSubnets != null) {
                    // Update the router interfaces alone and exit
                    for (Uuid subnetId : routerSubnets) {
                        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                                child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
                        Optional<Subnetmap> snMap = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
                        if (snMap.isPresent()) {
                            Subnetmap sn = snMap.get();
                            List<Uuid> portList = sn.getPortList();
                            if (portList != null) {
                                for (Uuid port : sn.getPortList()) {
                                    addToNeutronRouterInterfacesMap(router, port.getValue());
                                }
                            }
                        }
                    }
                }
                logger.info("Creation of Internal L3VPN skipped for VPN {} due to router {} already associated to " +
                        "external VPN {}", vpn.getValue(), router.getValue(), existingVpn.getValue());
                return;
            }
            associateRouterToInternalVpn(vpn, router);
        }
    }

    public void createL3Vpn(Uuid vpn, String name, Uuid tenant, List<String> rd, List<String> irt, List<String> ert,
                            Uuid router, List<Uuid> networks) {

        // Update VPN Instance node
        updateVpnInstanceNode(vpn.getValue(), rd, irt, ert);

        // Update local vpn-subnet DS
        updateVpnMaps(vpn, name, router, tenant, networks);

        if (router != null) {
            associateRouterToVpn(vpn, router);
        }
        if (networks != null) {
            associateNetworksToVpn(vpn, networks);
        }
    }

    @Override
    public Future<RpcResult<CreateL3VPNOutput>> createL3VPN(CreateL3VPNInput input) {

        CreateL3VPNOutputBuilder opBuilder = new CreateL3VPNOutputBuilder();
        SettableFuture<RpcResult<CreateL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();
        int failurecount = 0;
        int warningcount = 0;

        List<L3vpn> vpns = input.getL3vpn();
        for (L3vpn vpn : vpns) {
            RpcError error = null;
            String msg;
            if (vpn.getRouteDistinguisher() == null || vpn.getImportRT() == null || vpn.getExportRT() == null) {
                msg = String.format("Creation of L3VPN failed for VPN %s due to absence of RD/iRT/eRT input",
                        vpn.getId().getValue());
                logger.warn(msg);
                error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                errorList.add(error);
                warningcount++;
                continue;
            }
            if (vpn.getRouteDistinguisher().size() > 1) {
                msg = String.format("Creation of L3VPN failed for VPN %s due to multiple RD input %s",
                        vpn.getId().getValue(), vpn.getRouteDistinguisher());
                logger.warn(msg);
                error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                errorList.add(error);
                warningcount++;
                continue;
            }
            if (vpn.getRouterId() != null) {
                if (NeutronvpnUtils.getNeutronRouter(broker, vpn.getRouterId()) == null) {
                    msg = String.format("Creation of L3VPN failed for VPN %s due to router not found %s",
                            vpn.getId().getValue(), vpn.getRouterId().getValue());
                    logger.warn(msg);
                    error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                    errorList.add(error);
                    warningcount++;
                    continue;
                }
                Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, vpn.getRouterId(), true);
                if (vpnId != null) {
                    msg = String.format("Creation of L3VPN failed for VPN %s due to router %s already associated to " +
                            "another VPN %s", vpn.getId().getValue(), vpn.getRouterId().getValue(), vpnId.getValue());
                    logger.warn(msg);
                    error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                    errorList.add(error);
                    warningcount++;
                    continue;
                }
            }
            if (vpn.getNetworkIds() != null) {
                for (Uuid nw : vpn.getNetworkIds()) {
                    Network network = NeutronvpnUtils.getNeutronNetwork(broker, nw);
                    Uuid vpnId = NeutronvpnUtils.getVpnForNetwork(broker, nw);
                    if (network == null) {
                        msg = String.format("Creation of L3VPN failed for VPN %s due to network not found %s",
                                vpn.getId().getValue(), nw.getValue());
                        logger.warn(msg);
                        error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                        errorList.add(error);
                        warningcount++;
                    } else if (vpnId != null) {
                        msg = String.format("Creation of L3VPN failed for VPN %s due to network %s already associated" +
                                " to another VPN %s", vpn.getId().getValue(), nw.getValue(), vpnId.getValue());
                        logger.warn(msg);
                        error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg);
                        errorList.add(error);
                        warningcount++;
                    }
                }
                if (error != null) {
                    continue;
                }
            }
            try {
                createL3Vpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), vpn.getRouteDistinguisher(),
                        vpn.getImportRT(), vpn.getExportRT(), vpn.getRouterId(), vpn.getNetworkIds());
            } catch (Exception ex) {
                msg = String.format("Creation of L3VPN failed for VPN %s", vpn.getId().getValue());
                logger.error(msg, ex);
                error = RpcResultBuilder.newError(ErrorType.APPLICATION, msg, ex.getMessage());
                errorList.add(error);
                failurecount++;
            }
        }
        // if at least one succeeds; result is success
        // if none succeeds; result is failure
        if (failurecount + warningcount == vpns.size()) {
            result.set(RpcResultBuilder.<CreateL3VPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    String errorResponse = String.format("ErrorType: " + rpcError.getErrorType() + ", " + "ErrorTag: " +
                            rpcError.getTag() + ", " + "ErrorMessage: " + rpcError.getMessage());
                    errorResponseList.add(errorResponse);
                }
            } else {
                errorResponseList.add("Operation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<CreateL3VPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }

    @Override
    public Future<RpcResult<GetL3VPNOutput>> getL3VPN(GetL3VPNInput input) {

        GetL3VPNOutputBuilder opBuilder = new GetL3VPNOutputBuilder();
        SettableFuture<RpcResult<GetL3VPNOutput>> result = SettableFuture.create();
        Uuid inputVpnId = input.getId();
        List<VpnInstance> vpns = new ArrayList<>();

        try {
            if (inputVpnId == null) {
                // get all vpns
                InstanceIdentifier<VpnInstances> vpnsIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class).build();
                Optional<VpnInstances> optionalVpns = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                        vpnsIdentifier);
                if (optionalVpns.isPresent() && optionalVpns.get().getVpnInstance() != null) {
                    for (VpnInstance vpn : optionalVpns.get().getVpnInstance()) {
                        // eliminating internal VPNs from getL3VPN output
                        if (vpn.getIpv4Family().getRouteDistinguisher() != null) {
                            vpns.add(vpn);
                        }
                    }
                } else {
                    // No VPN present
                    result.set(RpcResultBuilder.<GetL3VPNOutput>failed()
                            .withWarning(ErrorType.PROTOCOL, "", "No VPN is present").build());
                    return result;
                }
            } else {
                String name = inputVpnId.getValue();
                InstanceIdentifier<VpnInstance> vpnIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class)
                                .child(VpnInstance.class, new VpnInstanceKey(name)).build();
                // read VpnInstance Info
                Optional<VpnInstance> optionalVpn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                        vpnIdentifier);
                if (optionalVpn.isPresent()) {
                    vpns.add(optionalVpn.get());
                } else {
                    String message = String.format("GetL3VPN failed because VPN %s is not present", name);
                    logger.error(message);
                    result.set(RpcResultBuilder.<GetL3VPNOutput>failed()
                            .withWarning(ErrorType.PROTOCOL, "invalid-value", message).build());
                }
            }
            List<L3vpnInstances> l3vpnList = new ArrayList<>();
            for (VpnInstance vpnInstance : vpns) {
                Uuid vpnId = new Uuid(vpnInstance.getVpnInstanceName());
                // create VpnMaps id
                InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap
                        .class, new VpnMapKey(vpnId)).build();
                L3vpnInstancesBuilder l3vpn = new L3vpnInstancesBuilder();

                List<String> rd = Arrays.asList(vpnInstance.getIpv4Family().getRouteDistinguisher().split(","));
                List<VpnTarget> vpnTargetList = vpnInstance.getIpv4Family().getVpnTargets().getVpnTarget();

                List<String> ertList = new ArrayList<>();
                List<String> irtList = new ArrayList<>();

                for (VpnTarget vpnTarget : vpnTargetList) {
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                        ertList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                        ertList.add(vpnTarget.getVrfRTValue());
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                }

                l3vpn.setId(vpnId).setRouteDistinguisher(rd).setImportRT(irtList).setExportRT(ertList);
                Optional<VpnMap> optionalVpnMap = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                        vpnMapIdentifier);
                if (optionalVpnMap.isPresent()) {
                    VpnMap vpnMap = optionalVpnMap.get();
                    l3vpn.setRouterId(vpnMap.getRouterId()).setNetworkIds(vpnMap.getNetworkIds())
                            .setTenantId(vpnMap.getTenantId()).setName(vpnMap.getName());
                }
                l3vpnList.add(l3vpn.build());
            }

            opBuilder.setL3vpnInstances(l3vpnList);
            result.set(RpcResultBuilder.<GetL3VPNOutput>success().withResult(opBuilder.build()).build());

        } catch (Exception ex) {
            String message = String.format("GetL3VPN failed due to %s", ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<GetL3VPNOutput>failed().withError(ErrorType.APPLICATION, message).build());
        }
        return result;
    }

    @Override
    public Future<RpcResult<DeleteL3VPNOutput>> deleteL3VPN(DeleteL3VPNInput input) {

        DeleteL3VPNOutputBuilder opBuilder = new DeleteL3VPNOutputBuilder();
        SettableFuture<RpcResult<DeleteL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();

        int failurecount = 0;
        int warningcount = 0;
        List<Uuid> vpns = input.getId();
        for (Uuid vpn : vpns) {
            RpcError error;
            String msg;
            try {
                InstanceIdentifier<VpnInstance> vpnIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class)
                                .child(VpnInstance.class, new VpnInstanceKey(vpn.getValue())).build();
                Optional<VpnInstance> optionalVpn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                        vpnIdentifier);
                if (optionalVpn.isPresent()) {
                    removeL3Vpn(vpn);
                } else {
                    msg = String.format("VPN with vpnid: %s does not exist", vpn.getValue());
                    logger.warn(msg);
                    error = RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-value", msg);
                    errorList.add(error);
                    warningcount++;
                }
            } catch (Exception ex) {
                msg = String.format("Deletion of L3VPN failed when deleting for uuid %s", vpn.getValue());
                logger.error(msg, ex);
                error = RpcResultBuilder.newError(ErrorType.APPLICATION, msg, ex.getMessage());
                errorList.add(error);
                failurecount++;
            }
        }
        // if at least one succeeds; result is success
        // if none succeeds; result is failure
        if (failurecount + warningcount == vpns.size()) {
            result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    String errorResponse = String.format("ErrorType: " + rpcError.getErrorType() + ", " + "ErrorTag: " +
                            rpcError.getTag() + ", " + "ErrorMessage: " + rpcError.getMessage());
                    errorResponseList.add(errorResponse);
                }
            } else {
                errorResponseList.add("Operation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<DeleteL3VPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }

    protected void addSubnetToVpn(Uuid vpnId, Uuid subnet) {
        logger.debug("Adding subnet {} to vpn {}", subnet.getValue(), vpnId.getValue());
        Subnetmap sn = updateSubnetNode(subnet, null, null, null, null, vpnId);
        boolean isLockAcquired = false;
        String lockName = vpnId.getValue() + subnet.getValue();
        String elanInstanceName = sn.getNetworkId().getValue();
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class).child
                (ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        Optional<ElanInstance> elanInstance = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                elanIdentifierId);
        //TODO: Cache it in add so can reuse it in update and delete. Best would be to cache in some ElanUtils
        long elanTag = elanInstance.get().getElanTag();
        Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnId).getRouterId();
        if (vpnId.equals(routerId)) {
            isExternalVpn = false;
        } else {
            isExternalVpn = true;
        }
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, lockName);
            checkAndPublishSubnetAddNotification(subnet, sn.getSubnetIp(), vpnId.getValue(), isExternalVpn, elanTag);
            logger.debug("Subnet added to Vpn notification sent");
        }catch (Exception e){
            logger.error("Subnet added to Vpn notification failed",e);
        }finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, lockName);
            }
        }
        // Check if there are ports on this subnet and add corresponding vpn-interfaces
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (Uuid port : sn.getPortList()) {
                logger.debug("adding vpn-interface for port {}", port.getValue());
                createVpnInterface(vpnId, NeutronvpnUtils.getNeutronPort(broker, port));
                if (routerId != null) {
                    addToNeutronRouterInterfacesMap(routerId, port.getValue());
                }
            }
        }
    }

    protected void updateVpnForSubnet(Uuid vpnId, Uuid subnet, boolean isBeingAssociated) {
        logger.debug("Updating VPN {} for subnet {}", vpnId.getValue(), subnet.getValue());
        Subnetmap sn = updateSubnetNode(subnet, null, null, null, null, vpnId);
        boolean isLockAcquired = false;
        String lockName = vpnId.getValue() + subnet.getValue();
        String elanInstanceName = sn.getNetworkId().getValue();
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class).child
                (ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        Optional<ElanInstance> elanInstance = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                elanIdentifierId);
        long elanTag = elanInstance.get().getElanTag();
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, lockName);
            checkAndPublishSubnetUpdNotification(subnet, sn.getSubnetIp(), vpnId.getValue(), isBeingAssociated,
                    elanTag);
            logger.debug("Subnet updated in Vpn notification sent");
        } catch (Exception e) {
            logger.error("Subnet updated in Vpn notification failed", e);
        }finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, lockName);
            }
        }
        // Check for ports on this subnet and update association of corresponding vpn-interfaces to external vpn
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (Uuid port : sn.getPortList()) {
                logger.debug("Updating vpn-interface for port {}", port.getValue());
                updateVpnInterface(vpnId, NeutronvpnUtils.getNeutronPort(broker, port));
            }
        }
    }

    InstanceIdentifier<RouterInterfaces> getRouterInterfacesId(Uuid routerId) {
        return InstanceIdentifier.builder(RouterInterfacesMap.class)
                .child(RouterInterfaces.class, new RouterInterfacesKey(routerId)).build();
    }
    void addToNeutronRouterInterfacesMap(Uuid routerId, String interfaceName) {
        InstanceIdentifier<RouterInterfaces> routerInterfacesId =  getRouterInterfacesId(routerId);
        Optional<RouterInterfaces> optRouterInterfaces = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
        Interfaces routerInterface = new InterfacesBuilder().setKey(new InterfacesKey(interfaceName)).setInterfaceId(interfaceName).build();
        if(optRouterInterfaces.isPresent()) {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId.child(Interfaces.class, new InterfacesKey(interfaceName)), routerInterface);
        } else {
            RouterInterfacesBuilder builder = new RouterInterfacesBuilder().setRouterId(routerId);
            List<Interfaces> interfaces = new ArrayList<>();
            interfaces.add(routerInterface);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId, builder.setInterfaces(interfaces).build());
        }
    }
    
    void removeFromNeutronRouterInterfacesMap(Uuid routerId, String interfaceName) {
        InstanceIdentifier<RouterInterfaces> routerInterfacesId =  getRouterInterfacesId(routerId);
        Optional<RouterInterfaces> optRouterInterfaces = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
        Interfaces routerInterface = new InterfacesBuilder().setKey(new InterfacesKey(interfaceName)).setInterfaceId(interfaceName).build();
        if(optRouterInterfaces.isPresent()) {
            RouterInterfaces routerInterfaces = optRouterInterfaces.get();
            List<Interfaces> interfaces = routerInterfaces.getInterfaces();
            if(interfaces != null && interfaces.remove(routerInterface)) {
                if(interfaces.isEmpty()) {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routerInterfacesId.child(Interfaces.class, new InterfacesKey(interfaceName)));
                }
            }
        }
    }

    protected List<Adjacency> addAdjacencyforExtraRoute(List<Routes> routeList, boolean rtrUp, String vpnifname) {
        List<Adjacency> adjList = new ArrayList<>();
        for (Routes route : routeList) {
            if (route != null && route.getNexthop() != null && route.getDestination() != null) {
                boolean isLockAcquired = false;
                String nextHop = String.valueOf(route.getNexthop().getValue());
                String destination = String.valueOf(route.getDestination().getValue());

                String infName = NeutronvpnUtils.getNeutronPortNamefromPortFixedIp(broker, nextHop);
                logger.trace("Adding extra route with nexthop {}, destination {}, infName {}", nextHop,
                        destination, infName);
                Adjacency erAdj = new AdjacencyBuilder().setIpAddress(destination).setNextHopIp(nextHop).setKey
                        (new AdjacencyKey(destination)).build();
                if (!rtrUp) {
                    if (infName.equals(vpnifname)) {
                        adjList.add(erAdj);
                    }
                    continue;
                }
                InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                        child(VpnInterface.class, new VpnInterfaceKey(infName)).build();
                try {
                    Optional<VpnInterface> optionalVpnInterface = NeutronvpnUtils.read(broker, LogicalDatastoreType
                            .CONFIGURATION, vpnIfIdentifier);
                    if (optionalVpnInterface.isPresent()) {
                        Adjacencies erAdjs = new AdjacenciesBuilder().setAdjacency(Arrays.asList(erAdj)).build();
                        VpnInterface vpnIf = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(infName))
                                .addAugmentation(Adjacencies.class, erAdjs).build();
                        isLockAcquired = NeutronvpnUtils.lock(lockManager, infName);
                        logger.debug("Adding extra route {}", route);
                        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, vpnIf);
                    } else {
                        logger.error("VM adjacency for interface {} not present ; cannot add extra route adjacency",
                                infName);
                    }
                } catch (Exception e) {
                    logger.error("exception in adding extra route: {}" + e);
                } finally {
                    if (isLockAcquired) {
                        NeutronvpnUtils.unlock(lockManager, infName);
                    }
                }
            } else {
                logger.error("Incorrect input received for extra route. {}", route);
            }
        }
        return adjList;
    }

    protected void removeAdjacencyforExtraRoute(List<Routes> routeList) {
        for (Routes route : routeList) {
            if (route != null && route.getNexthop() != null && route.getDestination() != null) {
                boolean isLockAcquired = false;
                String nextHop = String.valueOf(route.getNexthop().getValue());
                String destination = String.valueOf(route.getDestination().getValue());

                String infName = NeutronvpnUtils.getNeutronPortNamefromPortFixedIp(broker, nextHop);
                logger.trace("Removing extra route with nexthop {}, destination {}, infName {}", nextHop,
                        destination, infName);
                InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                        child(VpnInterface.class, new VpnInterfaceKey(infName)).augmentation(Adjacencies.class)
                        .child(Adjacency.class, new AdjacencyKey(destination)).build();
                try {
                    isLockAcquired = NeutronvpnUtils.lock(lockManager, infName);
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                    logger.trace("extra route {} deleted successfully", route);
                } catch (Exception e) {
                    logger.error("exception in deleting extra route: {}" + e);
                } finally {
                    if (isLockAcquired) {
                        NeutronvpnUtils.unlock(lockManager, infName);
                    }
                }
            } else {
                logger.error("Incorrect input received for extra route. {}", route);
            }
        }
    }

    protected void removeL3Vpn(Uuid id) {
        // read VPNMaps
        VpnMap vpnMap = NeutronvpnUtils.getVpnMap(broker, id);
        Uuid router = vpnMap.getRouterId();
        // dissociate router
        if (router != null) {
            dissociateRouterFromVpn(id, router);
        }
        // dissociate networks
        if (!id.equals(router)) {
            dissociateNetworksFromVpn(id, vpnMap.getNetworkIds());
        }
        // remove entire vpnMaps node
        deleteVpnMapsNode(id);

        // remove vpn-instance
        deleteVpnInstance(id);
    }

    protected void removeSubnetFromVpn(Uuid vpnId, Uuid subnet) {
        logger.debug("Removing subnet {} from vpn {}", subnet.getValue(), vpnId.getValue());
        Subnetmap sn = NeutronvpnUtils.getSubnetmap(broker, subnet);
        boolean isLockAcquired = false;
        String lockName = vpnId.getValue() + subnet.getValue();
        String elanInstanceName = sn.getNetworkId().getValue();
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class).child
                (ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        Optional<ElanInstance> elanInstance = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                elanIdentifierId);
        long elanTag = elanInstance.get().getElanTag();
        Uuid routerId = NeutronvpnUtils.getVpnMap(broker, vpnId).getRouterId();
        if (vpnId.equals(routerId)) {
            isExternalVpn = false;
        } else {
            isExternalVpn = true;
        }
        try {
            isLockAcquired = NeutronvpnUtils.lock(lockManager, lockName);
            checkAndPublishSubnetDelNotification(subnet, sn.getSubnetIp(), vpnId.getValue(), isExternalVpn, elanTag);
            logger.debug("Subnet removed from Vpn notification sent");
        }catch (Exception e){
            logger.error("Subnet removed from Vpn notification failed",e);
        }finally {
            if (isLockAcquired) {
                NeutronvpnUtils.unlock(lockManager, lockName);
            }
        }
        if (sn != null) {
            // Check if there are ports on this subnet; remove corresponding vpn-interfaces
            List<Uuid> portList = sn.getPortList();
            if (portList != null) {
                for (Uuid port : sn.getPortList()) {
                    logger.debug("removing vpn-interface for port {}", port.getValue());
                    deleteVpnInterface(NeutronvpnUtils.getNeutronPort(broker, port));
                    if (routerId != null) {
                        removeFromNeutronRouterInterfacesMap(routerId, port.getValue());
                    }
                }
            }
            // update subnet-vpn association
            removeFromSubnetNode(subnet, null, null, vpnId, null);
        } else {
            logger.warn("Subnetmap for subnet {} not found", subnet.getValue());
        }
    }

    protected void associateRouterToVpn(Uuid vpnId, Uuid routerId) {
        updateVpnMaps(vpnId, null, routerId, null, null);
        logger.debug("Updating association of subnets to external vpn {}", vpnId.getValue());
        List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
//      if (!vpnId.equals(routerId)) {
        if (routerSubnets != null) {
            for (Uuid subnetId : routerSubnets) {
                updateVpnForSubnet(vpnId, subnetId, true);
            }
        }
        try {
            checkAndPublishRouterAssociatedtoVpnNotification(routerId, vpnId);
            logger.debug("notification upon association of router {} to VPN {} published", routerId.getValue(),
                    vpnId.getValue());
        } catch (Exception e) {
            logger.error("publishing of notification upon association of router {} to VPN {} failed : ", routerId
                    .getValue(), vpnId.getValue(), e);
        }
    }

    protected void dissociatefixedIPFromFloatingIP(String fixedNeutronPortName) {
        floatingIpMapListener.dissociatefixedIPFromFloatingIP(fixedNeutronPortName);
    }

    protected void associateRouterToInternalVpn(Uuid vpnId, Uuid routerId) {
        List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
        logger.debug("Adding subnets to internal vpn {}", vpnId.getValue());
        for (Uuid subnet : routerSubnets) {
            addSubnetToVpn(vpnId, subnet);
        }
    }

    protected void dissociateRouterFromVpn(Uuid vpnId, Uuid routerId) {

        List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);
        if (routerSubnets != null) {
            for (Uuid subnetId : routerSubnets) {
                logger.debug("Updating association of subnets to internal vpn {}", routerId.getValue());
                updateVpnForSubnet(routerId, subnetId,false);
            }
        }
        clearFromVpnMaps(vpnId, routerId, null);
        try {
            checkAndPublishRouterDisassociatedFromVpnNotification(routerId, vpnId);
            logger.debug("notification upon disassociation of router {} from VPN {} published", routerId.getValue(),
                    vpnId.getValue());
        } catch (Exception e) {
            logger.error("publishing of notification upon disassociation of router {} from VPN {} failed : ", routerId
                    .getValue(), vpnId.getValue(), e);
        }
    }

    protected List<String> associateNetworksToVpn(Uuid vpn, List<Uuid> networks) {

        List<String> failedNwList = new ArrayList<String>();
        List<Uuid> passedNwList = new ArrayList<>();
        if (!networks.isEmpty()) {
            // process corresponding subnets for VPN
            for (Uuid nw : networks) {
                Network network = NeutronvpnUtils.getNeutronNetwork(broker, nw);
                Uuid vpnId = NeutronvpnUtils.getVpnForNetwork(broker, nw);
                if (network == null) {
                    failedNwList.add(String.format("network %s not found", nw.getValue()));
                } else if (vpnId != null) {
                    failedNwList.add(String.format("network %s already associated to another VPN %s", nw.getValue(),
                            vpnId.getValue()));
                } else {
                    List<Uuid> networkSubnets = NeutronvpnUtils.getSubnetIdsFromNetworkId(broker, nw);
                    logger.debug("Adding network subnets...{}", networkSubnets);
                    if (networkSubnets != null) {
                        for (Uuid subnet : networkSubnets) {
                            // check if subnet added as router interface to some router
                            Uuid subnetVpnId = NeutronvpnUtils.getVpnForSubnet(broker, subnet);
                            if (subnetVpnId == null) {
                                addSubnetToVpn(vpn, subnet);
                                passedNwList.add(nw);
                            } else {
                                failedNwList.add(String.format("subnet %s already added as router interface bound to " +
                                        "internal/external VPN %s", subnet.getValue (), subnetVpnId.getValue()));
                            }
                        }
                    }
                    if (network.getAugmentation(NetworkL3Extension.class) != null && network.getAugmentation
                            (NetworkL3Extension.class).isExternal()) {
                        nvpnNatManager.addExternalNetworkToVpn(network, vpn);
                    }
                }
            }
            updateVpnMaps(vpn, null, null, null, passedNwList);
        }
        return failedNwList;
    }

    protected List<String> dissociateNetworksFromVpn(Uuid vpn, List<Uuid> networks) {

        List<String> failedNwList = new ArrayList<String>();
        List<Uuid> passedNwList = new ArrayList<>();
        if (networks != null && !networks.isEmpty()) {
            // process corresponding subnets for VPN
            for (Uuid nw : networks) {
                Network network = NeutronvpnUtils.getNeutronNetwork(broker, nw);
                if (network == null) {
                    failedNwList.add(String.format("network %s not found", nw.getValue()));
                } else {
                    Uuid vpnId = NeutronvpnUtils.getVpnForNetwork(broker, nw);
                    if (vpn.equals(vpnId)) {
                        List<Uuid> networkSubnets = NeutronvpnUtils.getSubnetIdsFromNetworkId(broker, nw);
                        logger.debug("Removing network subnets...");
                        if (networkSubnets != null) {
                            for (Uuid subnet : networkSubnets) {
                                removeSubnetFromVpn(vpn, subnet);
                                passedNwList.add(nw);
                            }
                        }
                    } else {
                        if (vpnId == null) {
                            failedNwList.add(String.format("input network %s not associated to any vpn yet", nw
                                    .getValue()));
                        } else {
                            failedNwList.add(String.format("input network %s associated to a another vpn %s instead " +
                                    "of the one given as input", nw.getValue(), vpnId.getValue()));
                        }
                    }
                    if (network.getAugmentation(NetworkL3Extension.class).isExternal()) {
                        nvpnNatManager.removeExternalNetworkFromVpn(network);
                    }
                }
            }
            clearFromVpnMaps(vpn, null, passedNwList);
        }
        return failedNwList;
    }

    @Override
    public Future<RpcResult<AssociateNetworksOutput>> associateNetworks(AssociateNetworksInput input) {

        AssociateNetworksOutputBuilder opBuilder = new AssociateNetworksOutputBuilder();
        SettableFuture<RpcResult<AssociateNetworksOutput>> result = SettableFuture.create();
        logger.debug("associateNetworks {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();

        try {
            if (NeutronvpnUtils.getVpnMap(broker, vpnId) != null) {
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    List<String> failed = associateNetworksToVpn(vpnId, netIds);
                    if (!failed.isEmpty()) {
                        returnMsg.append(failed);
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("associate Networks to vpn %s failed due to %s", vpnId.getValue(),
                        returnMsg);
                logger.error(message);
                String errorResponse = String.format("ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: " +
                        message);
                opBuilder.setResponse(errorResponse);
                result.set(RpcResultBuilder.<AssociateNetworksOutput>success().withResult(opBuilder.build()).build());
            } else {
                result.set(RpcResultBuilder.<AssociateNetworksOutput>success().build());
            }
        } catch (Exception ex) {
            String message = String.format("associate Networks to vpn %s failed due to %s", input.getVpnId().getValue(),
                    ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION, message)
                    .build());
        }
        logger.debug("associateNetworks returns..");
        return result;
    }

    @Override
    public Future<RpcResult<Void>> associateRouter(AssociateRouterInput input) {

        SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        logger.debug("associateRouter {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();
        Uuid routerId = input.getRouterId();
        try {
            if (routerId != null && vpnId != null) {
                Router rtr = NeutronvpnUtils.getNeutronRouter(broker, routerId);
                VpnMap vpnMap = NeutronvpnUtils.getVpnMap(broker, vpnId);
                if (rtr != null && vpnMap != null) {
                    Uuid extVpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
                    if (vpnMap.getRouterId() != null) {
                        returnMsg.append("vpn ").append(vpnId.getValue()).append(" already associated to router ")
                                .append(vpnMap.getRouterId().getValue());
                    } else if (extVpnId != null) {
                        returnMsg.append("router ").append(routerId.getValue()).append(" already associated to " +
                                "another VPN ").append(extVpnId.getValue());
                    } else {
                        associateRouterToVpn(vpnId, routerId);
                    }
                } else {
                    returnMsg.append("router not found : ").append(routerId.getValue());
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("associate router to vpn %s failed due to %s", routerId.getValue(),
                        returnMsg);
                logger.error(message);
                result.set(RpcResultBuilder.<Void>failed().withWarning(ErrorType.PROTOCOL, "invalid-value", message)
                        .build());
            } else {
                result.set(RpcResultBuilder.<Void>success().build());
            }
        } catch (Exception ex) {
            String message = String.format("associate router %s to vpn %s failed due to %s", routerId.getValue(),
                    vpnId.getValue(), ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, message).build());
        }
        logger.debug("associateRouter returns..");
        return result;
    }

    @Override
    public Future<RpcResult<DissociateNetworksOutput>> dissociateNetworks(DissociateNetworksInput input) {

        DissociateNetworksOutputBuilder opBuilder = new DissociateNetworksOutputBuilder();
        SettableFuture<RpcResult<DissociateNetworksOutput>> result = SettableFuture.create();

        logger.debug("dissociateNetworks {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();

        try {
            if (NeutronvpnUtils.getVpnMap(broker, vpnId) != null) {
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    List<String> failed = dissociateNetworksFromVpn(vpnId, netIds);
                    if (!failed.isEmpty()) {
                        returnMsg.append(failed);
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("dissociate Networks to vpn %s failed due to %s", vpnId.getValue(),
                        returnMsg);
                logger.error(message);
                String errorResponse = String.format("ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: " +
                        message);
                opBuilder.setResponse(errorResponse);
                result.set(RpcResultBuilder.<DissociateNetworksOutput>success().withResult(opBuilder.build()).build());
            } else {
                result.set(RpcResultBuilder.<DissociateNetworksOutput>success().build());
            }
        } catch (Exception ex) {
            String message = String.format("dissociate Networks to vpn %s failed due to %s", input.getVpnId().
                    getValue(), ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION, message)
                    .build());
        }
        logger.debug("dissociateNetworks returns..");
        return result;
    }

    @Override
    public Future<RpcResult<Void>> dissociateRouter(DissociateRouterInput input) {

        SettableFuture<RpcResult<Void>> result = SettableFuture.create();

        logger.debug("dissociateRouter {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();
        Uuid routerId = input.getRouterId();
        try {
            if (NeutronvpnUtils.getVpnMap(broker, vpnId) != null) {
                if (routerId != null) {
                    Router rtr = NeutronvpnUtils.getNeutronRouter(broker, routerId);
                    if (rtr != null) {
                        Uuid routerVpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
                        if (vpnId.equals(routerVpnId)) {
                            dissociateRouterFromVpn(vpnId, routerId);
                        } else {
                            if (routerVpnId == null) {
                                returnMsg.append("input router ").append(routerId.getValue()).append(" not associated" +
                                        " to any vpn yet");
                            } else {
                                returnMsg.append("input router ").append(routerId.getValue()).append(" associated to " +
                                        "vpn ").append(routerVpnId.getValue()).append("instead of the vpn given as " +
                                        "input");
                            }
                        }
                    } else {
                        returnMsg.append("router not found : ").append(routerId.getValue());
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("dissociate router %s to vpn %s failed due to %s", routerId.getValue(),
                        vpnId.getValue(), returnMsg);
                logger.error(message);
                String errorResponse = String.format("ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: " +
                        message);
                result.set(RpcResultBuilder.<Void>failed().withWarning(ErrorType.PROTOCOL, "invalid-value", message)
                        .build());
            } else {
                result.set(RpcResultBuilder.<Void>success().build());
            }
        } catch (Exception ex) {
            String message = String.format("disssociate router %s to vpn %s failed due to %s", routerId.getValue(),
                    vpnId.getValue(), ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, message).build());
        }
        logger.debug("dissociateRouter returns..");

        return result;
    }

    @Override
    public Future<RpcResult<GetFixedIPsForNeutronPortOutput>> getFixedIPsForNeutronPort(GetFixedIPsForNeutronPortInput
                                                                                                input) {
        GetFixedIPsForNeutronPortOutputBuilder opBuilder = new GetFixedIPsForNeutronPortOutputBuilder();
        SettableFuture<RpcResult<GetFixedIPsForNeutronPortOutput>> result = SettableFuture.create();
        Uuid portId = input.getPortId();
        StringBuilder returnMsg = new StringBuilder();
        try {
            List<String> fixedIPList = new ArrayList<>();
            Port port = NeutronvpnUtils.getNeutronPort(broker, portId);
            if (port != null) {
                List<FixedIps> fixedIPs = port.getFixedIps();
                for (FixedIps ip : fixedIPs) {
                    fixedIPList.add(ip.getIpAddress().getIpv4Address().getValue());
                }
            } else {
                returnMsg.append("neutron port: ").append(portId.getValue()).append(" not found");
            }
            if (returnMsg.length() != 0) {
                String message = String.format("Retrieval of FixedIPList for neutron port failed due to %s", returnMsg);
                logger.error(message);
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>failed().withWarning(ErrorType.PROTOCOL,
                        "invalid-value", message).build());
            } else {
                opBuilder.setFixedIPs(fixedIPList);
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>success().withResult(opBuilder.build())
                        .build());
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>success().build());
            }
        } catch (Exception ex) {
            String message = String.format("Retrieval of FixedIPList for neutron port %s failed due to %s", portId
                    .getValue(), ex.getMessage());
            logger.error(message, ex);
            result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>failed().withError(ErrorType.APPLICATION,
                    message).build());
        }
        return result;
    }

    protected void handleNeutronRouterDeleted(Uuid routerId, List<Uuid> routerSubnetIds) {
        // check if the router is associated to some VPN
        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(broker, routerId, true);
        if (vpnId != null) {
            // remove existing external vpn interfaces
            for (Uuid subnetId : routerSubnetIds) {
                removeSubnetFromVpn(vpnId, subnetId);
            }
            clearFromVpnMaps(vpnId, routerId, null);
        } else {
            // remove existing internal vpn interfaces
            for (Uuid subnetId : routerSubnetIds) {
                removeSubnetFromVpn(routerId, subnetId);
            }
        }
        // delete entire vpnMaps node for internal VPN
        deleteVpnMapsNode(routerId);

        // delete vpn-instance for internal VPN
        deleteVpnInstance(routerId);
    }

    protected Subnet getNeutronSubnet(Uuid subnetId){
        return NeutronvpnUtils.getNeutronSubnet(broker, subnetId);
    }

    protected IpAddress getNeutronSubnetGateway(Uuid subnetId) {
        Subnet sn = NeutronvpnUtils.getNeutronSubnet(broker, subnetId);
        if (null != sn) {
            return sn.getGatewayIp();
        }
        return null;
    }

    protected Port getNeutronPort(String name) {
        return NeutronvpnUtils.getNeutronPort(broker, new Uuid(name));
    }

    protected Port getNeutronPort(Uuid portId) {
        return NeutronvpnUtils.getNeutronPort(broker, portId);
    }

    protected List<Uuid> getSubnetsforVpn(Uuid vpnid) {
        List<Uuid> subnets = new ArrayList<>();
        //read subnetmaps
        InstanceIdentifier<Subnetmaps> subnetmapsid = InstanceIdentifier.builder(Subnetmaps.class).build();
        Optional<Subnetmaps> subnetmaps = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                subnetmapsid);
        if (subnetmaps.isPresent() && subnetmaps.get().getSubnetmap() != null) {
            List<Subnetmap> subnetMapList = subnetmaps.get().getSubnetmap();
            for (Subnetmap subnetMap : subnetMapList) {
                if (subnetMap.getVpnId() != null && subnetMap.getVpnId().equals(vpnid)) {
                    subnets.add(subnetMap.getId());
                }
            }
        }
        return subnets;
    }

    public List<String> showNeutronPortsCLI() {
        List<String> result = new ArrayList<>();
        result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", "Port ID", "Mac Address", "Prefix Length", "IP " +
                "Address"));
        result.add("-------------------------------------------------------------------------------------------");
        InstanceIdentifier<Ports> portidentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class);
        try {
            Optional<Ports> ports = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, portidentifier);
            if (ports.isPresent() && ports.get().getPort() != null) {
                for (Port port : ports.get().getPort()) {
                    List<FixedIps> fixedIPs = port.getFixedIps();
                    try {
                        if (fixedIPs != null && !fixedIPs.isEmpty()) {
                            List<String> ipList = new ArrayList<>();
                            for (FixedIps fixedIp : fixedIPs) {
                                IpAddress ipAddress = fixedIp.getIpAddress();
                                if (ipAddress.getIpv4Address() != null) {
                                    ipList.add(ipAddress.getIpv4Address().getValue());
                                } else {
                                    ipList.add((ipAddress.getIpv6Address().getValue()));
                                }
                            }
                            result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", port.getUuid().getValue(), port
                                    .getMacAddress().getValue(), NeutronvpnUtils.getIPPrefixFromPort(broker, port),
                                    ipList.toString()));
                        } else {
                            result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", port.getUuid().getValue(), port
                                    .getMacAddress().getValue(), "Not Assigned", "Not Assigned"));
                        }
                    } catch (Exception e) {
                        logger.error("Failed to retrieve neutronPorts info for port {}: ", port.getUuid().getValue(),
                                e);
                        System.out.println("Failed to retrieve neutronPorts info for port: " + port.getUuid()
                                .getValue() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve neutronPorts info : ", e);
            System.out.println("Failed to retrieve neutronPorts info : " + e.getMessage());
        }
        return result;
    }

    public List<String> showVpnConfigCLI(Uuid vpnuuid) {
        List<String> result = new ArrayList<>();
        if (vpnuuid == null) {
            System.out.println("");
            System.out.println("Displaying VPN config for all VPNs");
            System.out.println("To display VPN config for a particular VPN, use the following syntax");
            System.out.println(getshowVpnConfigCLIHelp());
        }
        try {
            RpcResult<GetL3VPNOutput> rpcResult = getL3VPN(new GetL3VPNInputBuilder().setId(vpnuuid).build()).get();
            if (rpcResult.isSuccessful()) {
                result.add("");
                result.add(String.format(" %-37s %-37s %-7s ", "VPN ID", "Tenant ID", "RD"));
                result.add("");
                result.add(String.format(" %-80s ", "Import-RTs"));
                result.add("");
                result.add(String.format(" %-80s ", "Export-RTs"));
                result.add("");
                result.add(String.format(" %-76s ", "Subnet IDs"));
                result.add("");
                result.add("------------------------------------------------------------------------------------");
                result.add("");
                List<L3vpnInstances> VpnList = rpcResult.getResult().getL3vpnInstances();
                for (L3vpnInstance Vpn : VpnList) {
                    String tenantId = Vpn.getTenantId() != null ? Vpn.getTenantId().getValue() : "\"                 " +
                            "                  \"";
                    result.add(String.format(" %-37s %-37s %-7s ", Vpn.getId().getValue(), tenantId, Vpn
                            .getRouteDistinguisher()));
                    result.add("");
                    result.add(String.format(" %-80s ", Vpn.getImportRT()));
                    result.add("");
                    result.add(String.format(" %-80s ", Vpn.getExportRT()));
                    result.add("");

                    Uuid vpnid = Vpn.getId();
                    List<Uuid> subnetList = getSubnetsforVpn(vpnid);
                    if (!subnetList.isEmpty()) {
                        for (Uuid subnetuuid : subnetList) {
                            result.add(String.format(" %-76s ", subnetuuid.getValue()));
                        }
                    } else {
                        result.add(String.format(" %-76s ", "\"                                    \""));
                    }
                    result.add("");
                    result.add("----------------------------------------");
                    result.add("");
                }
            } else {
                String errortag = rpcResult.getErrors().iterator().next().getTag();
                if (errortag == "") {
                    System.out.println("");
                    System.out.println("No VPN has been configured yet");
                } else if (errortag == "invalid-value") {
                    System.out.println("");
                    System.out.println("VPN " + vpnuuid.getValue() + " is not present");
                } else {
                    System.out.println("error getting VPN info : " + rpcResult.getErrors());
                    System.out.println(getshowVpnConfigCLIHelp());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("error getting VPN info : ", e);
            System.out.println("error getting VPN info : " + e.getMessage());
        }
        return result;
    }

    private String getshowVpnConfigCLIHelp() {
        StringBuilder help = new StringBuilder("Usage:");
        help.append("display vpn-config [-vid/--vpnid <id>]");
        return help.toString();
    }

    private void checkAndPublishSubnetAddNotification(Uuid subnetId, String subnetIp, String vpnName,
                                                      Boolean isExternalvpn, Long elanTag)throws InterruptedException {
        SubnetAddedToVpnBuilder builder = new SubnetAddedToVpnBuilder();

        logger.info("publish notification called");

        builder.setSubnetId(subnetId);
        builder.setSubnetIp(subnetIp);
        builder.setVpnName(vpnName);
        builder.setExternalVpn(isExternalvpn);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
    }

    private void checkAndPublishSubnetDelNotification(Uuid subnetId, String subnetIp, String vpnName,
                                                      Boolean isExternalvpn, Long elanTag) throws InterruptedException {
        SubnetDeletedFromVpnBuilder builder = new SubnetDeletedFromVpnBuilder();

        logger.info("publish notification called");

        builder.setSubnetId(subnetId);
        builder.setSubnetIp(subnetIp);
        builder.setVpnName(vpnName);
        builder.setExternalVpn(isExternalvpn);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
    }

    private void checkAndPublishSubnetUpdNotification(Uuid subnetId, String subnetIp, String vpnName,
                                                      Boolean isExternalvpn, Long elanTag) throws InterruptedException {
        SubnetUpdatedInVpnBuilder builder = new SubnetUpdatedInVpnBuilder();

        logger.info("publish notification called");

        builder.setSubnetId(subnetId);
        builder.setSubnetIp(subnetIp);
        builder.setVpnName(vpnName);
        builder.setExternalVpn(isExternalvpn);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
    }

    private void checkAndPublishRouterAssociatedtoVpnNotification(Uuid routerId, Uuid vpnId) throws
            InterruptedException {
        RouterAssociatedToVpn routerAssociatedToVpn = new RouterAssociatedToVpnBuilder().setRouterId(routerId)
                .setVpnId(vpnId).build();
        logger.info("publishing notification upon association of router to VPN");
        notificationPublishService.putNotification(routerAssociatedToVpn);
    }

    private void checkAndPublishRouterDisassociatedFromVpnNotification(Uuid routerId, Uuid vpnId) throws
            InterruptedException {
        RouterDisassociatedFromVpn routerDisassociatedFromVpn = new RouterDisassociatedFromVpnBuilder().setRouterId
                (routerId).setVpnId(vpnId).build();
        logger.info("publishing notification upon disassociation of router from VPN");
        notificationPublishService.putNotification(routerDisassociatedFromVpn);
    }
}
