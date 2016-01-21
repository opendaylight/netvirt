/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.SettableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets
        .VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.vpn.instance
        .Ipv4FamilyBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.AssociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.AssociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.AssociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.AssociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.CreateL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.CreateL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.CreateL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DeleteL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DeleteL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DeleteL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DissociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DissociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DissociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.DissociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.GetL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.GetL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.GetL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.GetL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.L3vpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.createl3vpn.input.L3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.getl3vpn.output.L3vpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.getl3vpn.output
        .L3vpnInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NeutronvpnManager implements NeutronvpnService, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(NeutronvpnManager.class);
    private final DataBroker broker;
    private LockManagerService lockManager;
    IMdsalApiManager mdsalUtil;

    /**
     * @param db           - dataBroker reference
     * @param mdsalManager - MDSAL Util API access
     */
    public NeutronvpnManager(final DataBroker db, IMdsalApiManager mdsalManager) {
        broker = db;
        mdsalUtil = mdsalManager;
    }

    public void setLockManager(LockManagerService lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public void close() throws Exception {
        logger.info("Neutron VPN Manager Closed");
    }

    protected Subnetmap updateSubnetNode(Uuid subnetId, Uuid tenantId, Uuid networkId, Uuid routerId, Uuid vpnId,
                                         Uuid portId) {

        try {
            SubnetmapBuilder builder = null;

            InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                    child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
            Optional<Subnetmap> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, id);
            logger.debug("updating Subnet :read: ");
            if (sn.isPresent()) {
                builder = new SubnetmapBuilder(sn.get());
                logger.debug("updating Subnet :existing: ");
            } else {
                builder = new SubnetmapBuilder().setKey(new SubnetmapKey(subnetId)).setId(subnetId);
                logger.debug("updating Subnet :new: ");
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

            if (portId != null) {
                List<Uuid> portList = builder.getPortList();
                if (portList == null) {
                    portList = new ArrayList<Uuid>();
                }
                portList.add(portId);
                builder.setPortList(portList);
            }

            Subnetmap subnetmap = builder.build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            logger.debug("Created/Updated subnetmap node: {} ", subnetId.getValue());

            return subnetmap;
        } catch (Exception e) {
            logger.error("Update local subnetmap failed for node: {} {} {} {} {} {} ",
                    subnetId.getValue(), tenantId.getValue(), networkId.getValue(), routerId.getValue(), vpnId
                            .getValue(), portId.getValue());
            throw new RuntimeException(e);
        }

    }

    protected Subnetmap removeFromSubnetNode(Uuid subnetId, Uuid networkId, Uuid routerId, Uuid vpnId, Uuid portId) {
        Subnetmap subnetmap = null;
        try {
            InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                    child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
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
                logger.debug("Removing from existing subnetmap node: {} ", subnetId.getValue());
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            } else {
                logger.warn("remove from non-existing subnetmap node: {} ", subnetId.getValue());
            }
        } catch (Exception e) {
            logger.error("Remove from subnetmap failed for node: {} {} {} {} {} {} ", subnetId.getValue(), networkId
                    .getValue(), routerId.getValue(), vpnId.getValue(), portId.getValue());
            throw new RuntimeException(e);
        }

        return subnetmap;
    }

    private void updateVpnInstanceNode(String name, List<String> rd, List<String> irt, List<String> ert) {

        try {
            VpnInstanceBuilder builder = null;
            List<VpnTarget> vpnTargetList = new ArrayList<VpnTarget>();
            InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class).
                    child(VpnInstance.class, new VpnInstanceKey(name)).build();
            Optional<VpnInstance> optionalVpn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                    vpnIdentifier);
            logger.debug("Creating/Updating a new vpn-instance node: {} ", name);
            if (optionalVpn.isPresent()) {
                builder = new VpnInstanceBuilder(optionalVpn.get());
                logger.debug("updating existing vpninstance node");
            } else {
                builder = new VpnInstanceBuilder().setKey(new VpnInstanceKey(name)).setVpnInstanceName(name);
            }

            if (irt != null && !irt.isEmpty()) {
                if (ert != null && !ert.isEmpty()) {
                    List<String> commonRT = new ArrayList<String>(irt);
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
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier, newVpn);
            logger.debug("Created/Updated vpn-instance for {} ", name);
        } catch (Exception e) {
            logger.error("Update VPN Instance node failed for node: {} {} {} {}", name, rd, irt, ert);
            throw new RuntimeException(e);
        }
    }

    private void deleteVpnMapsNode(Uuid vpnid) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnid)).build();
        logger.debug("removing vpnMaps node: {} ", vpnid.getValue());
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
    }

    private void updateVpnMaps(Uuid vpnId, String name, Uuid router, Uuid tenantId, List<Uuid> networks) {
        VpnMapBuilder builder;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
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
                nwList = new ArrayList<Uuid>();
            }
            nwList.addAll(networks);
            builder.setNetworkIds(nwList);
        }

        logger.debug("Creating/Updating vpnMaps node: {} ", vpnId.getValue());
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, builder.build());
        logger.debug("VPNMaps DS updated for VPN {} ", vpnId.getValue());
    }

    private void clearFromVpnMaps(Uuid id, Uuid router, List<Uuid> networks) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(id)).build();
        Optional<VpnMap> optionalVpnMap = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            VpnMapBuilder vpnMapBuilder = new VpnMapBuilder(vpnMap);
            if (router != null) {
                if (vpnMap.getNetworkIds() == null && router.equals(vpnMap.getVpnId())) {
                    // remove entire node in case of internal VPN
                    logger.debug("removing vpnMaps node: {} ", id);
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
                    return;
                }
                vpnMapBuilder.setRouterId(null);
            }
            if (networks != null) {
                List<Uuid> vpnNw = vpnMap.getNetworkIds();
                for (Uuid nw : networks) {
                    vpnNw.remove(nw);
                }
                if (vpnNw.isEmpty()) {
                    logger.debug("setting networks null in vpnMaps node: {} ", id.getValue());
                    vpnMapBuilder.setNetworkIds(null);
                } else {
                    vpnMapBuilder.setNetworkIds(vpnNw);
                }
            }

            logger.debug("clearing from vpnMaps node: {} ", id.getValue());
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMapBuilder.build());
        } else {
            logger.error("VPN : {} not found", id.getValue());
        }
        logger.debug("VPNMaps DS clear success for VPN {} ", id.getValue());
    }

    private void deleteVpnInstance(Uuid vpnId) {

        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class).
                child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue())).build();
        logger.debug("removing vpn Instance {}", vpnId.getValue());
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
    }


    protected void createVpnInterface(Uuid vpnId, Port port) {

        if (vpnId == null || port == null) {
            return;
        }
        String portname = NeutronvpnUtils.uuidToTapPortName(port.getUuid());
        List<Adjacency> adjList = new ArrayList<Adjacency>();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                child(VpnInterface.class, new VpnInterfaceKey(portname)).build();
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
                    .setMacAddress(port.getMacAddress()).build();
            adjList.add(vmAdj);
            // create extra route adjacency
            if (rtr != null && rtr.getRoutes() != null) {
                List<String> routeList = rtr.getRoutes();
                List<Adjacency> erAdjList = addAdjacencyforExtraRoute(routeList, false, portname);
                if (erAdjList != null) {
                    adjList.addAll(erAdjList);
                }
            }
        }
        // create vpn-interface on this neutron port
        Adjacencies adjs = new AdjacenciesBuilder().setAdjacency(adjList).build();
        VpnInterfaceBuilder vpnb = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(portname)).
                setName(portname).setVpnInstanceName(vpnId.getValue()).addAugmentation(Adjacencies.class, adjs);
        VpnInterface vpnIf = vpnb.build();

        NeutronvpnUtils.lockVpnInterface(lockManager, portname);
        try {
            logger.debug("Creating vpn interface {}", vpnIf);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, vpnIf);
        } catch (Exception ex) {
            logger.error("Creation of vpninterface {} failed due to {}", portname, ex);
        } finally {
            NeutronvpnUtils.unlockVpnInterface(lockManager, portname);
        }
    }

    protected void deleteVpnInterface(Port port) {

        if (port != null) {
            String pname = NeutronvpnUtils.uuidToTapPortName(port.getUuid());
            InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                    child(VpnInterface.class, new VpnInterfaceKey(pname)).build();

            NeutronvpnUtils.lockVpnInterface(lockManager, pname);
            try {
                logger.debug("Deleting vpn interface {}", pname);
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier);
            } catch (Exception ex) {
                logger.error("Deletion of vpninterface {} failed due to {}", pname, ex);
            } finally {
                NeutronvpnUtils.unlockVpnInterface(lockManager, pname);
            }
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
        List<RpcError> errorList = new ArrayList<RpcError>();
        int failurecount = 0;
        int warningcount = 0;

        List<L3vpn> vpns = input.getL3vpn();
        for (L3vpn vpn : vpns) {
            RpcError error;
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
            try {
                createL3Vpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), vpn.getRouteDistinguisher(),
                        vpn.getImportRT(), vpn.getExportRT(), vpn.getRouterId(), vpn.getNetworkIds());
            } catch (Exception ex) {
                msg = String.format("Creation of L3VPN failed for VPN %s", vpn.getId().getValue());
                logger.error(msg, ex.getMessage());
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
        List<VpnInstance> vpns = new ArrayList<VpnInstance>();

        try {
            if (inputVpnId == null) {
                // get all vpns
                InstanceIdentifier<VpnInstances> vpnsIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class).build();
                Optional<VpnInstances> optionalVpns = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                        vpnsIdentifier);
                if (optionalVpns.isPresent()) {
                    for (VpnInstance vpn : optionalVpns.get().getVpnInstance()) {
                        vpns.add(vpn);
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
            List<L3vpnInstances> l3vpnList = new ArrayList<L3vpnInstances>();
            for (VpnInstance vpnInstance : vpns) {
                Uuid vpnId = new Uuid(vpnInstance.getVpnInstanceName());
                // create VpnMaps id
                InstanceIdentifier<VpnMap> vpnMapIdentifier =
                        InstanceIdentifier.builder(VpnMaps.class)
                                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
                L3vpnInstancesBuilder l3vpn = new L3vpnInstancesBuilder();

                List<String> rd = Arrays.asList(vpnInstance.getIpv4Family().getRouteDistinguisher().split(","));
                List<VpnTarget> vpnTargetList = vpnInstance.getIpv4Family().getVpnTargets().getVpnTarget();

                List<String> ertList = new ArrayList<String>();
                List<String> irtList = new ArrayList<String>();

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
            logger.error(message);
            result.set(RpcResultBuilder.<GetL3VPNOutput>failed().withError(ErrorType.APPLICATION, message).build());
        }

        return result;
    }

    @Override
    public Future<RpcResult<DeleteL3VPNOutput>> deleteL3VPN(DeleteL3VPNInput input) {

        DeleteL3VPNOutputBuilder opBuilder = new DeleteL3VPNOutputBuilder();
        SettableFuture<RpcResult<DeleteL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<RpcError>();

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
                logger.error(msg, ex.getMessage());
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
        Subnetmap sn = updateSubnetNode(subnet, null, null, null, vpnId, null);
        // Check if there are ports on this subnet and add corresponding vpn-interfaces
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (Uuid port : sn.getPortList()) {
                logger.debug("adding vpn-interface for port {}", port.getValue());
                createVpnInterface(vpnId, getNeutronPort(port));
            }
        }
    }

    protected List<Adjacency> addAdjacencyforExtraRoute(List<String> routeList, boolean rtrUp, String vpnifname) {
        try {
            List<Adjacency> adjList = new ArrayList<Adjacency>();
            for (String route : routeList) {
                // assuming extra route is strictly in the format "nexthop destination" > "10.1.1.10 40.0.1.0/24"
                String[] parts = route.split(" ");
                if (parts.length == 2) {
                    String nextHop = parts[0];
                    String destination = parts[1];

                    String tapPortName = NeutronvpnUtils.getNeutronPortNamefromPortFixedIp(broker, nextHop);
                    logger.trace("Adding extra route with nexthop {}, destination {}, ifName {}", nextHop,
                            destination, tapPortName);
                    Adjacency erAdj = new AdjacencyBuilder().setIpAddress(destination).setNextHopIp(nextHop).setKey
                            (new AdjacencyKey(destination)).build();
                    if (rtrUp == false) {
                        if (tapPortName.equals(vpnifname)) {
                            adjList.add(erAdj);
                        }
                        continue;
                    }
                    InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                            child(VpnInterface.class, new VpnInterfaceKey(tapPortName)).build();
                    Optional<VpnInterface> optionalVpnInterface = NeutronvpnUtils.read(broker, LogicalDatastoreType
                            .CONFIGURATION, vpnIfIdentifier);
                    if (optionalVpnInterface.isPresent()) {
                        Adjacencies erAdjs = new AdjacenciesBuilder().setAdjacency(Arrays.asList(erAdj)).build();
                        VpnInterface vpnIf = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(tapPortName))
                                .addAugmentation(Adjacencies.class, erAdjs).build();
                        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, vpnIf);
                        logger.trace("extra route {} added successfully", route);
                    } else {
                        logger.error("VM adjacency for interface {} not present ; cannot add extra route adjacency",
                                tapPortName);
                    }
                } else {
                    logger.error("Incorrect input received for extra route. {}", parts);
                }
            }
            return adjList;
        } catch (Exception e) {
            logger.error("exception in adding extra route: {}" + e);
        }
        return null;
    }

    protected void removeAdjacencyforExtraRoute(List<String> routeList) {
        try {
            for (String route : routeList) {
                // assuming extra route is strictly in the format "nexthop destination" > "10.1.1.10 40.0.1.0/24"
                String[] parts = route.split(" ");
                if (parts.length == 2) {
                    String nextHop = parts[0];
                    String destination = parts[1];

                    String tapPortName = NeutronvpnUtils.getNeutronPortNamefromPortFixedIp(broker, nextHop);
                    logger.trace("Removing extra route with nexthop {}, destination {}, ifName {}", nextHop,
                            destination, tapPortName);
                    InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class).
                            child(VpnInterface.class, new VpnInterfaceKey(tapPortName)).augmentation(Adjacencies.class)
                            .child(Adjacency.class, new AdjacencyKey(destination)).build();
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                    logger.trace("extra route {} deleted successfully", route);
                } else {
                    logger.error("Incorrect input received for extra route. {}", parts);
                }
            }
        } catch (Exception e) {
            logger.error("exception in deleting extra route: {}" + e);
        }
    }

    protected void addPortToVpn(Uuid vpnId, Uuid port) {
        logger.debug("Adding Port to vpn node...");
        createVpnInterface(vpnId, getNeutronPort(port));
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

    protected void removePortFromVpn(Uuid vpnId, Uuid port) {
        logger.debug("Removing Port from vpn node...");
        deleteVpnInterface(getNeutronPort(port));
    }

    protected void removeSubnetFromVpn(Uuid vpnId, Uuid subnet) {
        logger.debug("Removing subnet {} from vpn {}", subnet.getValue(), vpnId.getValue());
        Subnetmap sn = NeutronvpnUtils.getSubnetmap(broker, subnet);
        if (sn != null) {
            // Check if there are ports on this subnet; remove corresponding vpn-interfaces
            List<Uuid> portList = sn.getPortList();
            if (portList != null) {
                for (Uuid port : sn.getPortList()) {
                    logger.debug("removing vpn-interface for port {}", port.getValue());
                    deleteVpnInterface(getNeutronPort(port));
                }
            }
            // update subnet-vpn association
            removeFromSubnetNode(subnet, null, null, vpnId, null);
        } else {
            logger.warn("Subnetmap for subnet {} not found", subnet.getValue());
        }
    }

    protected void associateRouterToVpn(Uuid vpnId, Uuid routerId) {

        List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);

        if (!vpnId.equals(routerId)) {
            logger.debug("Removing subnets from internal vpn {}", routerId.getValue());
            if (routerSubnets != null) {
                for (Uuid subnet : routerSubnets) {
                    removeSubnetFromVpn(routerId, subnet);
                }
            }
        }
        logger.debug("Adding subnets to vpn {}", vpnId.getValue());
        for (Uuid subnet : routerSubnets) {
            addSubnetToVpn(vpnId, subnet);
        }

        updateVpnMaps(vpnId, null, routerId, null, null);
    }

    protected void dissociateRouterFromVpn(Uuid vpnId, Uuid routerId) {

        // remove existing external vpn interfaces
        List<Uuid> routerSubnets = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerId);

        if (routerSubnets != null) {
            for (Uuid subnet : routerSubnets) {
                logger.debug("Removing subnets from external vpn {}", vpnId.getValue());
                removeSubnetFromVpn(vpnId, subnet);
                logger.debug("Adding subnets to internal vpn {}", routerId.getValue());
                addSubnetToVpn(routerId, subnet);
            }
        }
        clearFromVpnMaps(vpnId, routerId, null);
    }

    protected List<String> associateNetworksToVpn(Uuid vpn, List<Uuid> networks) {
        List<String> failed = new ArrayList<String>();
        if (!networks.isEmpty()) {
            // store in Data Base
            updateVpnMaps(vpn, null, null, null, networks);
            // process corresponding subnets for VPN
            for (Uuid nw : networks) {
                if (NeutronvpnUtils.getNeutronNetwork(broker, nw) == null) {
                    failed.add(nw.getValue());
                } else {
                    List<Uuid> networkSubnets = NeutronvpnUtils.getSubnetIdsFromNetworkId(broker, nw);
                    logger.debug("Adding network subnets...");
                    if (networkSubnets != null) {
                        for (Uuid subnet : networkSubnets) {
                            addSubnetToVpn(vpn, subnet);
                        }
                    }
                }
            }
        }
        return failed;
    }

    protected List<String> dissociateNetworksFromVpn(Uuid vpn, List<Uuid> networks) {
        List<String> failed = new ArrayList<String>();
        if (networks != null && !networks.isEmpty()) {
            // store in Data Base
            clearFromVpnMaps(vpn, null, networks);
            // process corresponding subnets for VPN
            for (Uuid nw : networks) {
                if (NeutronvpnUtils.getNeutronNetwork(broker, nw) == null) {
                    failed.add(nw.getValue());
                } else {
                    List<Uuid> networkSubnets = NeutronvpnUtils.getSubnetIdsFromNetworkId(broker, nw);
                    logger.debug("Removing network subnets...");
                    if (networkSubnets != null) {
                        for (Uuid subnet : networkSubnets) {
                            removeSubnetFromVpn(vpn, subnet);
                        }
                    }
                }
            }
        }
        return failed;
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
                        returnMsg.append("network(s) not found : ").append(failed);
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
            logger.error(message);
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
                    if (vpnMap.getRouterId() != null) {
                        returnMsg.append("vpn ").append(vpnId.getValue()).append(" already associated to router ")
                                .append(vpnMap.getRouterId().getValue());
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
            logger.error(message);
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
                        returnMsg.append("netowrk(s) not found : ").append(failed);
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("disssociate Networks to vpn %s failed due to %s", vpnId.getValue(),
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
            logger.error(message);
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
                        dissociateRouterFromVpn(vpnId, routerId);
                    } else {
                        returnMsg.append("router not found : ").append(routerId.getValue());
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("disssociate router %s to vpn %s failed due to %s", routerId.getValue(),
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
            logger.error(message);
            result.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, message).build());
        }
        logger.debug("dissociateRouter returns..");

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

    protected Subnet getNeutronSubnet(Uuid subnetId) {
        InstanceIdentifier<Subnet> inst = InstanceIdentifier.create(Neutron.class).
                child(Subnets.class).child(Subnet.class, new SubnetKey(subnetId));
        Optional<Subnet> sn = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, inst);

        if (sn.isPresent()) {
            return sn.get();
        }
        return null;
    }

    protected IpAddress getNeutronSubnetGateway(Uuid subnetId) {
        Subnet sn = getNeutronSubnet(subnetId);
        if (null != sn) {
            return sn.getGatewayIp();
        }
        return null;
    }

    protected Port getNeutronPort(String name) {
        Uuid portId = NeutronvpnUtils.getNeutronPortIdfromPortName(broker, name);
        if (portId != null) {
            InstanceIdentifier<Port> pid = InstanceIdentifier.create(Neutron.class).
                    child(Ports.class).child(Port.class, new PortKey(portId));
            Optional<Port> optPort = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, pid);
            if (optPort.isPresent()) {
                return optPort.get();
            }
        } else {
            logger.error("Port {} not Found!!", name);
        }
        return null;
    }

    protected Port getNeutronPort(Uuid portId) {
        InstanceIdentifier<Port> pid = InstanceIdentifier.create(Neutron.class).
                child(Ports.class).child(Port.class, new PortKey(portId));
        Optional<Port> optPort = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, pid);
        if (optPort.isPresent()) {
            return optPort.get();
        }
        return null;
    }

    protected List<Uuid> getSubnetsforVpn(Uuid vpnid) {
        List<Uuid> subnets = new ArrayList<Uuid>();
        //read subnetmaps
        InstanceIdentifier<Subnetmaps> subnetmapsid = InstanceIdentifier.builder(Subnetmaps.class).build();
        Optional<Subnetmaps> subnetmaps = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION,
                subnetmapsid);
        if (subnetmaps.isPresent()) {
            Subnetmaps smaps = subnetmaps.get();
            List<Subnetmap> subnetMapList = smaps.getSubnetmap();
            for (Subnetmap subnetMap : subnetMapList) {
                if (subnetMap.getVpnId() != null && subnetMap.getVpnId().equals(vpnid)) {
                    subnets.add(subnetMap.getId());
                }
            }
        }
        return subnets;
    }

    public List<String> showNeutronPortsCLI() {
        List<String> result = new ArrayList<String>();
        result.add(String.format(" %-22s  %-22s  %-22s  %-6s ", "PortName", "Mac Address", "IP Address",
                "Prefix Length"));
        result.add("---------------------------------------------------------------------------------------");
        InstanceIdentifier<Ports> portidentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class);
        try {
            Optional<Ports> ports = NeutronvpnUtils.read(broker, LogicalDatastoreType.CONFIGURATION, portidentifier);
            if (ports.isPresent()) {
                List<Port> portList = ports.get().getPort();
                for (Port port : portList) {
                    result.add(String.format(" %-22s  %-22s  %-22s  %-6s ", NeutronvpnUtils.uuidToTapPortName(port
                            .getUuid()), port.getMacAddress(), port.getFixedIps().get(0).getIpAddress().getIpv4Address()
                            .getValue(), NeutronvpnUtils.getIPPrefixFromPort(broker, port)));
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to retrieve neutronPorts info : ", e);
            System.out.println("Failed to retrieve neutronPorts info : " + e.getMessage());
        }
        return result;
    }

    public List<String> showVpnConfigCLI(Uuid vpnuuid) {
        List<String> result = new ArrayList<String>();
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
            logger.trace("error getting VPN info : ", e);
            System.out.println("error getting VPN info : " + e.getMessage());
        }
        return result;
    }

    private String getshowVpnConfigCLIHelp() {
        StringBuilder help = new StringBuilder("Usage:");
        help.append("display vpn-config [-vid/--vpnid <id>]");
        return help.toString();
    }

}
