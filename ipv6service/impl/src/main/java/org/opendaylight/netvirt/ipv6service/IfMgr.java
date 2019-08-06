/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import io.netty.util.Timeout;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.ipv6util.api.Ipv6Constants.Ipv6RouterAdvertisementType;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.ipv6service.api.ElementCache;
import org.opendaylight.netvirt.ipv6service.api.IVirtualNetwork;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.api.IVirtualRouter;
import org.opendaylight.netvirt.ipv6service.api.IVirtualSubnet;
import org.opendaylight.netvirt.ipv6service.utils.IpV6NAConfigHelper;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6PeriodicTrQueue;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6TimerWheel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev181010.Ipv6serviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IfMgr implements ElementCache, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(IfMgr.class);

    private final ConcurrentMap<Uuid, VirtualRouter> vrouters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, VirtualNetwork> vnetworks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, VirtualSubnet> vsubnets = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, VirtualPort> vintfs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, VirtualPort> vrouterv6IntfMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Set<VirtualPort>> unprocessedRouterIntfs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Set<VirtualPort>> unprocessedSubnetIntfs = new ConcurrentHashMap<>();
    private static ConcurrentMap<Uuid, Set<VirtualPort>> unprocessedNetIntfs = new ConcurrentHashMap<>();
    private static ConcurrentMap<Uuid, Integer> unprocessedNetRSFlowIntfs = new ConcurrentHashMap<>();
    private static ConcurrentMap<Uuid, Set<Ipv6Address>> unprocessedNetNSFlowIntfs = new ConcurrentHashMap<>();
    private static ConcurrentMap<Uuid, Set<VirtualSubnet>> unprocessedNetNaFlowIntfs = new ConcurrentHashMap<>();
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final IElanService elanProvider;
    private final Ipv6ServiceUtils ipv6ServiceUtils;
    private final IpV6NAConfigHelper ipV6NAConfigHelper;
    private final DataBroker dataBroker;
    private final Ipv6ServiceEosHandler ipv6ServiceEosHandler;
    private final ManagedNewTransactionRunner txRunner;
    private final PacketProcessingService packetService;
    private final Ipv6PeriodicTrQueue ipv6Queue = new Ipv6PeriodicTrQueue(this::transmitUnsolicitedRA);
    private final Ipv6TimerWheel timer = new Ipv6TimerWheel();
    private final ExecutorService ndExecutorService = SpecialExecutors.newBlockingBoundedFastThreadPool(
            10, Integer.MAX_VALUE, "NDExecutorService", IfMgr.class);
    private final JobCoordinator coordinator;

    @Inject
    public IfMgr(DataBroker dataBroker, IElanService elanProvider, OdlInterfaceRpcService interfaceManagerRpc,
                 PacketProcessingService packetService, Ipv6ServiceUtils ipv6ServiceUtils ,
                 IpV6NAConfigHelper ipV6NAConfigHelper, final JobCoordinator coordinator,
                 Ipv6ServiceEosHandler ipv6ServiceEosHandler) {
        this.dataBroker = dataBroker;
        this.elanProvider = elanProvider;
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.packetService = packetService;
        this.ipv6ServiceUtils = ipv6ServiceUtils;
        this.ipV6NAConfigHelper = ipV6NAConfigHelper;
        this.ipv6ServiceEosHandler = ipv6ServiceEosHandler;
        this.coordinator = coordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        LOG.info("IfMgr is enabled");
    }

    @Override
    @PreDestroy
    public void close() {
        ndExecutorService.shutdown();
        timer.close();
    }

    public ExecutorService getNDExecutorService() {
        return ndExecutorService;
    }

    /**
     * Add router.
     *
     * @param rtrUuid router uuid
     * @param rtrName router name
     * @param tenantId tenant id
     */
    public void addRouter(Uuid rtrUuid, String rtrName, Uuid tenantId) {

        VirtualRouter rtr = VirtualRouter.builder().routerUUID(rtrUuid).tenantID(tenantId).name(rtrName).build();
        vrouters.put(rtrUuid, rtr);

        Set<VirtualPort> intfList = unprocessedRouterIntfs.remove(rtrUuid);
        if (intfList == null) {
            LOG.debug("No unprocessed interfaces for the router {}", rtrUuid);
            return;
        }

        synchronized (intfList) {
            for (VirtualPort intf : intfList) {
                if (intf != null) {
                    intf.setRouter(rtr);
                    rtr.addInterface(intf);

                    for (VirtualSubnet snet : intf.getSubnets()) {
                        rtr.addSubnet(snet);
                    }
                }
            }
        }
    }

    /**
     * Remove Router.
     *
     * @param rtrUuid router uuid
     */
    public void removeRouter(Uuid rtrUuid) {

        VirtualRouter rtr = rtrUuid != null ? vrouters.remove(rtrUuid) : null;
        if (rtr != null) {
            rtr.removeSelf();
            removeUnprocessed(unprocessedRouterIntfs, rtrUuid);
        } else {
            LOG.error("Delete router failed for :{}", rtrUuid);
        }
    }

    /**
     * Add Subnet.
     *
     * @param snetId subnet id
     * @param name subnet name
     * @param tenantId tenant id
     * @param gatewayIp gateway ip address
     * @param ipVersion IP Version "IPv4 or IPv6"
     * @param subnetCidr subnet CIDR
     * @param ipV6AddressMode Address Mode of IPv6 Subnet
     * @param ipV6RaMode RA Mode of IPv6 Subnet.
     */
    public void addSubnet(Uuid snetId, String name, Uuid tenantId,
                          IpAddress gatewayIp, String ipVersion, IpPrefix subnetCidr,
                          String ipV6AddressMode, String ipV6RaMode) {

        // Save the gateway ipv6 address in its fully expanded format. We always store the v6Addresses
        // in expanded form and are used during Neighbor Discovery Support.
        if (gatewayIp != null) {
            Ipv6Address addr = new Ipv6Address(InetAddresses
                    .forString(gatewayIp.getIpv6Address().getValue()).getHostAddress());
            gatewayIp = new IpAddress(addr);
        }

        VirtualSubnet snet = VirtualSubnet.builder().subnetUUID(snetId).tenantID(tenantId).name(name)
                .gatewayIp(gatewayIp).subnetCidr(subnetCidr).ipVersion(ipVersion).ipv6AddressMode(ipV6AddressMode)
                .ipv6RAMode(ipV6RaMode).build();

        vsubnets.put(snetId, snet);

        Set<VirtualPort> intfList = unprocessedSubnetIntfs.remove(snetId);
        if (intfList == null) {
            LOG.debug("No unprocessed interfaces for the subnet {}", snetId);
            return;
        }

        synchronized (intfList) {
            for (VirtualPort intf : intfList) {
                if (intf != null) {
                    intf.setSubnet(snetId, snet);
                    snet.addInterface(intf);

                    VirtualRouter rtr = intf.getRouter();
                    if (rtr != null) {
                        rtr.addSubnet(snet);
                    }
                    updateInterfaceDpidOfPortInfo(intf.getIntfUUID());
                }
            }
        }
    }

    /**
     * Remove Subnet.
     *
     * @param snetId subnet id
     */
    public void removeSubnet(Uuid snetId) {
        VirtualSubnet snet = snetId != null ? vsubnets.remove(snetId) : null;
        if (snet != null) {
            LOG.info("removeSubnet is invoked for {}", snetId);
            snet.removeSelf();
            removeUnprocessed(unprocessedSubnetIntfs, snetId);
        }
    }

    @Nullable
    private VirtualRouter getRouter(Uuid rtrId) {
        return rtrId != null ? vrouters.get(rtrId) : null;
    }

    public void addRouterIntf(Uuid portId, Uuid rtrId, Uuid snetId,
                              Uuid networkId, IpAddress fixedIp, String macAddress,
                              String deviceOwner) {
        LOG.debug("addRouterIntf portId {}, rtrId {}, snetId {}, networkId {}, ip {}, mac {}",
                portId, rtrId, snetId, networkId, fixedIp, macAddress);
        /* Added the below logic for supporting neutron router interface creation.
         * Since when neutron port is created with fixed Ipv6 address, that time it will be
         * treated as a host port and it will be added into the vintfs map through
         * NeutronPortChangeListener ADD() event.
         * Later the same neutron port is added to router this time it will be treated as
         * a router_interface through NeutronPortChangeListener UPDATE() event.
         */
        VirtualPort virInterface = vintfs.get(portId);
        if (virInterface != null && Strings.isNullOrEmpty(virInterface.getDeviceOwner())) {
            vintfs.remove(portId);
        }
        //Save the interface ipv6 address in its fully expanded format
        Ipv6Address addr = new Ipv6Address(InetAddresses
                .forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
        fixedIp = new IpAddress(addr);

        VirtualPort intf = VirtualPort.builder().intfUUID(portId).networkID(networkId).macAddress(macAddress)
                .routerIntfFlag(true).deviceOwner(deviceOwner).build();
        intf.setSubnetInfo(snetId, fixedIp);
        intf.setPeriodicTimer(ipv6Queue);
        int networkMtu = getNetworkMtu(networkId);
        if (networkMtu != 0) {
            intf.setMtu(networkMtu);
        }

        boolean newIntf = false;
        VirtualPort prevIntf = vintfs.putIfAbsent(portId, intf);
        if (prevIntf == null) {
            newIntf = true;
            MacAddress ifaceMac = MacAddress.getDefaultInstance(macAddress);
            Ipv6Address llAddr = Ipv6Util.getIpv6LinkLocalAddressFromMac(ifaceMac);
            /* A new router interface is created. This is basically triggered when an
            IPv6 subnet is associated to the router. Check if network is already hosting
            any VMs. If so, on all the hosts that have VMs on the network, program the
            icmpv6 punt flows in IPV6_TABLE(45).
             */
            programIcmpv6RSPuntFlows(intf.getNetworkID(), Ipv6ServiceConstants.ADD_FLOW);
            if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
                checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.ADD_FLOW);
            }
            else {
                programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), llAddr, Ipv6ServiceConstants.ADD_FLOW);
            }
            programIcmpv6NaForwardFlows(intf.getNetworkID(), getSubnet(snetId), Ipv6ServiceConstants.ADD_FLOW);
        } else {
            intf = prevIntf;
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualRouter rtr = getRouter(rtrId);
        VirtualSubnet snet = getSubnet(snetId);

        if (rtr != null && snet != null) {
            snet.setRouter(rtr);
            intf.setSubnet(snetId, snet);
            rtr.addSubnet(snet);
        } else if (snet != null) {
            intf.setSubnet(snetId, snet);
            addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
        } else {
            addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
            addUnprocessed(unprocessedSubnetIntfs, snetId, intf);
        }

        if (networkId != null) {
            LOG.trace("Adding vrouterv6IntfMap for network:{} intf:{}", networkId, intf);
            vrouterv6IntfMap.put(networkId, intf);
        }

        if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Controller)) {
            programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), fixedIp.getIpv6Address(),
                    Ipv6ServiceConstants.ADD_FLOW);
        }
        programIcmpv6NaPuntFlows(networkId, intf.getSubnets(), Ipv6ServiceConstants.ADD_FLOW);

        if (newIntf) {
            LOG.debug("start the periodic RA Timer for routerIntf {}", portId);
            transmitUnsolicitedRA(intf);
        }
    }

    public void updateRouterIntf(Uuid portId, Uuid rtrId, List<FixedIps> fixedIpsList, Set<FixedIps> deletedIps) {
        LOG.info("updateRouterIntf portId {}, fixedIpsList {} ", portId, fixedIpsList);
        VirtualPort intf = getPort(portId);
        if (intf == null) {
            LOG.info("Skip Router interface update for non-ipv6 port {}", portId);
            return;
        }
        Uuid networkID = intf.getNetworkID();
        intf.clearSubnetInfo();
        for (FixedIps fip : fixedIpsList) {
            IpAddress fixedIp = fip.getIpAddress();

            if (fixedIp.getIpv4Address() != null) {
                continue;
            }

            //Save the interface ipv6 address in its fully expanded format
            Ipv6Address addr = new Ipv6Address(InetAddresses
                    .forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
            fixedIp = new IpAddress(addr);
            Uuid subnetId = fip.getSubnetId();
            intf.setSubnetInfo(subnetId, fixedIp);

            VirtualRouter rtr = getRouter(rtrId);
            VirtualSubnet snet = getSubnet(subnetId);

            if (rtr != null && snet != null) {
                snet.setRouter(rtr);
                intf.setSubnet(subnetId, snet);
                rtr.addSubnet(snet);
            } else if (snet != null) {
                intf.setSubnet(subnetId, snet);
                addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
            } else {
                addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
                addUnprocessed(unprocessedSubnetIntfs, subnetId, intf);
            }

            if (networkID != null) {
                vrouterv6IntfMap.put(networkID, intf);
            }
        }

        /*
         * This is a port update event for routerPort. Check if any IPv6 subnet is added or removed from the
         * router port. Depending on subnet added/removed, we add/remove the corresponding flows from
         * IPV6_TABLE(45). Add is handled in addInterfaceInfo(), delete case is handled here.
         */
        for (FixedIps ips : deletedIps) {
            VirtualSubnet snet = getSubnet(ips.getSubnetId());
            programIcmpv6NaPuntFlows(networkID, Lists.newArrayList(snet), Ipv6ServiceConstants.DEL_FLOW);
            if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
                checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.DEL_FLOW);
            } else {
                programIcmpv6NSPuntFlowForAddress(networkID, ips.getIpAddress().getIpv6Address(),
                        Ipv6ServiceConstants.DEL_FLOW);
            }
        }
    }

    @Nullable
    private VirtualSubnet getSubnet(Uuid snetId) {
        return snetId != null ? vsubnets.get(snetId) : null;
    }

    public void addHostIntf(Uuid portId, Uuid snetId, Uuid networkId,
                            IpAddress fixedIp, String macAddress, String deviceOwner) {
        LOG.debug("addHostIntf portId {}, snetId {}, networkId {}, ip {}, mac {}",
                portId, snetId, networkId, fixedIp, macAddress);

        //Save the interface ipv6 address in its fully expanded format
        Ipv6Address addr = new Ipv6Address(InetAddresses
                .forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
        fixedIp = new IpAddress(addr);

        VirtualPort intf = VirtualPort.builder().intfUUID(portId).networkID(networkId).macAddress(macAddress)
                .routerIntfFlag(false).deviceOwner(deviceOwner).build();
        intf.setSubnetInfo(snetId, fixedIp);

        VirtualPort prevIntf = vintfs.putIfAbsent(portId, intf);
        if (prevIntf == null) {
            Long elanTag = getNetworkElanTag(networkId);
            final VirtualPort virtIntf = intf;
            String jobKey = Ipv6ServiceUtils.buildIpv6MonitorJobKey(portId.getValue());
            coordinator.enqueueJob(jobKey, () -> {
                // Do service binding for the port and set the serviceBindingStatus to true.
                ipv6ServiceUtils.bindIpv6Service(portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
                virtIntf.setServiceBindingStatus(true);

                /* Update the intf dpnId/ofPort from the Operational Store */
                updateInterfaceDpidOfPortInfo(portId);

                if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
                    checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, virtIntf, Ipv6ServiceConstants.ADD_FLOW);
                }
                return Collections.emptyList();
            });
        } else {
            intf = prevIntf;
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualSubnet snet = getSubnet(snetId);

        if (snet != null) {
            intf.setSubnet(snetId, snet);
        } else {
            addUnprocessed(unprocessedSubnetIntfs, snetId, intf);
        }
    }

    @Nullable
    private VirtualPort getPort(Uuid portId) {
        return portId != null ? vintfs.get(portId) : null;
    }

    public void clearAnyExistingSubnetInfo(Uuid portId) {
        VirtualPort intf = getPort(portId);
        if (intf != null) {
            intf.clearSubnetInfo();
        }
    }

    public void updateHostIntf(Uuid portId, Boolean portIncludesV6Address) {
        VirtualPort intf = getPort(portId);
        if (intf == null) {
            LOG.debug("Update Host interface failed. Could not get Host interface details {}", portId);
            return;
        }

        /* If the VMPort initially included an IPv6 address (along with IPv4 address) and IPv6 address
         was removed, we will have to unbind the service on the VM port. Similarly we do a ServiceBind
         if required.
          */
        String jobKey = Ipv6ServiceUtils.buildIpv6MonitorJobKey(portId.getValue());
        coordinator.enqueueJob(jobKey, () -> {
            if (portIncludesV6Address) {
                if (!intf.getServiceBindingStatus()) {
                    Long elanTag = getNetworkElanTag(intf.getNetworkID());
                    LOG.info("In updateHostIntf, service binding for portId {}", portId);
                    ipv6ServiceUtils.bindIpv6Service(portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
                    intf.setServiceBindingStatus(true);
                    if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(),
                            Ipv6serviceConfig.NaResponderMode.Switch)) {
                        LOG.debug("In updateHostIntf: Host Interface {}", intf);
                        checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.ADD_FLOW);
                    }
                }
            } else {
                LOG.info("In updateHostIntf, removing service binding for portId {}", portId);
                ipv6ServiceUtils.unbindIpv6Service(portId.getValue());
                intf.setServiceBindingStatus(false);
                if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
                    LOG.debug("In updateHostIntf: Host Interface {}", intf);
                    checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.DEL_FLOW);
                }
            }
            return Collections.emptyList();
        });
    }

    public void updateDpnInfo(Uuid portId, BigInteger dpId, Long ofPort) {
        LOG.info("In updateDpnInfo portId {}, dpId {}, ofPort {}",
                portId, dpId, ofPort);
        VirtualPort intf = getPort(portId);
        if (intf != null) {
            intf.setDpId(dpId);
            intf.setOfPort(ofPort);

            // Update the network <--> List[dpnIds, List<ports>] cache.
            VirtualNetwork vnet = getNetwork(intf.getNetworkID());
            if (vnet != null) {
                vnet.updateDpnPortInfo(dpId, ofPort, portId, Ipv6ServiceConstants.ADD_ENTRY);
            } else {
                LOG.error("In updateDpnInfo networks NOT FOUND: networkID {}, portId {}, dpId {}, ofPort {}",
                           intf.getNetworkID(), portId, dpId, ofPort);
                addUnprocessed(unprocessedNetIntfs, intf.getNetworkID(), intf);
            }
        } else {
            LOG.error("In updateDpnInfo port NOT FOUND: portId {}, dpId {}, ofPort {}",
                    portId, dpId, ofPort);
        }
    }


    public void updateInterfaceDpidOfPortInfo(Uuid portId) {
        LOG.debug("In updateInterfaceDpidOfPortInfo portId {}", portId);
        Interface interfaceState = ipv6ServiceUtils.getInterfaceStateFromOperDS(portId.getValue());
        if (interfaceState == null) {
            LOG.warn("In updateInterfaceDpidOfPortInfo, port info not found in Operational Store {}.", portId);
            return;
        }

        List<String> ofportIds = interfaceState.getLowerLayerIf();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpId = ipv6ServiceUtils.getDpIdFromInterfaceState(interfaceState);
        if (!dpId.equals(Ipv6ServiceConstants.INVALID_DPID)) {
            Long ofPort = MDSALUtil.getOfPortNumberFromPortName(nodeConnectorId);
            updateDpnInfo(portId, dpId, ofPort);
        }
    }


    public void removePort(Uuid portId) {
        VirtualPort intf = portId != null ? vintfs.remove(portId) : null;
        if (intf != null) {
            intf.removeSelf();
            Uuid networkID = intf.getNetworkID();
            if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
                LOG.info("In removePort for router interface, portId {}", portId);

                if (networkID != null) {
                    vrouterv6IntfMap.remove(networkID, intf);
                }

                /* Router port is deleted. Remove the corresponding icmpv6 punt flows on all
                the dpnIds which were hosting the VMs on the network.
                 */
                programIcmpv6RSPuntFlows(intf.getNetworkID(), Ipv6ServiceConstants.DEL_FLOW);
                programIcmpv6NaPuntFlows(networkID, intf.getSubnets(), Ipv6ServiceConstants.DEL_FLOW);

                if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
                    checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.DEL_FLOW);
                } else {
                    for (Ipv6Address ipv6Address : intf.getIpv6Addresses()) {
                        programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), ipv6Address,
                                Ipv6ServiceConstants.DEL_FLOW);
                    }
                }

                for (VirtualSubnet subnet : intf.getSubnets()) {
                    programIcmpv6NaForwardFlows(networkID, subnet, Ipv6ServiceConstants.DEL_FLOW);
                }
            } else {
                LOG.info("In removePort for host interface, portId {}", portId);
                String jobKey = Ipv6ServiceUtils.buildIpv6MonitorJobKey(portId.getValue());
                coordinator.enqueueJob(jobKey, () -> {
                    // Remove the serviceBinding entry for the port.
                    ipv6ServiceUtils.unbindIpv6Service(portId.getValue());
                    if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(),
                            Ipv6serviceConfig.NaResponderMode.Switch)) {
                        if (intf.getDpId() != null) {
                            checkIcmpv6NsMatchAndResponderFlow(intf.getDpId(), 0, intf, Ipv6ServiceConstants.DEL_FLOW);
                        } else {
                            checkIcmpv6NsMatchAndResponderFlow(BigInteger.ZERO, 0, intf, Ipv6ServiceConstants.DEL_FLOW);
                        }
                    }
                    // Remove the portId from the (network <--> List[dpnIds, List <ports>]) cache.
                    VirtualNetwork vnet = getNetwork(networkID);
                    if (vnet != null) {
                        BigInteger dpId = intf.getDpId();
                        vnet.updateDpnPortInfo(dpId, intf.getOfPort(), portId, Ipv6ServiceConstants.DEL_ENTRY);
                    }
                    return Collections.emptyList();
                });
            }
            transmitRouterAdvertisement(intf, Ipv6RouterAdvertisementType.CEASE_ADVERTISEMENT);
            timer.cancelPeriodicTransmissionTimeout(intf.getPeriodicTimeout());
            intf.resetPeriodicTimeout();
            LOG.debug("Reset the periodic RA Timer for intf {}", intf.getIntfUUID());
        }
    }

    public void deleteInterface(Uuid interfaceUuid, String dpId) {
        // Nothing to do for now
    }

    public void addUnprocessed(Map<Uuid, Set<VirtualPort>> unprocessed, Uuid id, VirtualPort intf) {
        if (id != null) {
            unprocessed.computeIfAbsent(id,
                key -> Collections.synchronizedSet(ConcurrentHashMap.newKeySet())).add(intf);
        }
    }

    @Nullable
    public Set<VirtualPort> removeUnprocessed(Map<Uuid, Set<VirtualPort>> unprocessed, Uuid id) {
        if (id != null) {
            return unprocessed.remove(id);
        }
        return null;
    }

    public void addUnprocessedRSFlows(Map<Uuid, Integer> unprocessed, Uuid id, Integer action) {
        unprocessed.put(id, action);

    }

    public Integer removeUnprocessedRSFlows(Map<Uuid, Integer> unprocessed, Uuid id) {
        return unprocessed.remove(id);
    }

    private void addUnprocessedNSFlows(Map<Uuid, Set<Ipv6Address>> unprocessed, Uuid id, Ipv6Address ipv6Address,
            Integer action) {
        if (Ipv6ServiceUtils.isActionAdd(action)) {
            unprocessed.computeIfAbsent(id, key -> Collections.synchronizedSet(ConcurrentHashMap.newKeySet()))
                    .add(ipv6Address);
        } else if (Ipv6ServiceUtils.isActionDelete(action)) {
            Set<Ipv6Address> ipv6AddressesList = unprocessed.get(id);
            if ((ipv6AddressesList != null) && (ipv6AddressesList.contains(ipv6Address))) {
                ipv6AddressesList.remove(ipv6Address);
            }
        }
    }

    private Set<Ipv6Address> removeUnprocessedNSFlows(Map<Uuid, Set<Ipv6Address>> unprocessed, Uuid id) {
        Set<Ipv6Address> removedIps = unprocessed.remove(id);
        return removedIps != null ? removedIps : Collections.emptySet();
    }

    private void addUnprocessedNaFlows(Uuid networkId, Collection<VirtualSubnet> subnets, Integer action) {
        if (action == Ipv6ServiceConstants.ADD_FLOW) {
            unprocessedNetNaFlowIntfs.computeIfAbsent(networkId, key -> ConcurrentHashMap.newKeySet())
                    .addAll(subnets);
        } else if (Ipv6ServiceUtils.isActionDelete(action)) {
            Set<VirtualSubnet> subnetsInCache = unprocessedNetNaFlowIntfs.get(networkId);
            if (subnetsInCache != null) {
                subnetsInCache.removeAll(subnets);
            }
        }
    }

    private Set<VirtualSubnet> removeUnprocessedNaFlows(Uuid networkId) {
        Set<VirtualSubnet> removedSubnets = unprocessedNetNaFlowIntfs.remove(networkId);
        return removedSubnets != null ? removedSubnets : Collections.emptySet();
    }

    @Nullable
    public VirtualPort getRouterV6InterfaceForNetwork(Uuid networkId) {
        LOG.debug("obtaining the virtual interface for {}", networkId);
        return networkId != null ? vrouterv6IntfMap.get(networkId) : null;
    }

    @Nullable
    public VirtualPort obtainV6Interface(Uuid id) {
        VirtualPort intf = getPort(id);
        if (intf == null) {
            return null;
        }
        for (VirtualSubnet snet : intf.getSubnets()) {
            if (snet.getIpVersion().equals(Ipv6ServiceConstants.IP_VERSION_V6)) {
                return intf;
            }
        }
        return null;
    }

    private void programIcmpv6RSPuntFlows(Uuid networkId, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }

        Long elanTag = getNetworkElanTag(networkId);
        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            List<BigInteger> dpnList = vnet.getDpnsHostingNetwork();
            for (BigInteger dpId : dpnList) {
                programIcmpv6RSPuntFlow(elanTag, vnet, dpId, action);
            }
        } else {
            addUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId, action);
        }
    }

    private void programIcmpv6RSPuntFlow(Long elanTag, VirtualNetwork vnet, BigInteger dpId, int action) {
        int flowStatus = vnet.getRSPuntFlowStatusOnDpnId(dpId);
        if (Ipv6ServiceUtils.isActionAdd(action) && flowStatus == Ipv6ServiceConstants.FLOWS_NOT_CONFIGURED) {
            ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                    Ipv6ServiceConstants.ADD_FLOW);
            vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6ServiceConstants.FLOWS_CONFIGURED);
        } else if (Ipv6ServiceUtils.isActionDelete(action) && flowStatus == Ipv6ServiceConstants.FLOWS_CONFIGURED) {
            ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                    Ipv6ServiceConstants.DEL_FLOW);
            vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6ServiceConstants.FLOWS_NOT_CONFIGURED);
        }
    }

    protected void programIcmpv6NSPuntFlowForAddress(Uuid networkId, Ipv6Address ipv6Address, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }

        Long elanTag = getNetworkElanTag(networkId);
        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                programIcmpv6NSPuntFlow(ipv6Address, elanTag, dpnIfaceInfo, action);
            }
        } else {
            addUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId, ipv6Address, action);
        }
    }

    private void programIcmpv6NSPuntFlow(Ipv6Address ipv6Address, Long elanTag,
                                         VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo, int action) {
        if (dpnIfaceInfo.getDpId() == null) {
            LOG.error("programIcmpv6NSPuntFlow - DpnId is null for ipv6Address={}, elanTag={}, dpnIfaceInfo={},"
                    + " action={}", ipv6Address, elanTag, dpnIfaceInfo, action);
            return;
        }
        if (Ipv6ServiceUtils.isActionAdd(action) && !dpnIfaceInfo.isNdTargetFlowAlreadyConfigured(ipv6Address)) {
            ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(), elanTag,
                    ipv6Address, Ipv6ServiceConstants.ADD_FLOW);
            dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
        } else if (Ipv6ServiceUtils.isActionDelete(action)
                && dpnIfaceInfo.isNdTargetFlowAlreadyConfigured(ipv6Address)) {
            ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(), elanTag,
                    ipv6Address, Ipv6ServiceConstants.DEL_FLOW);
            dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
        }
    }

    private void programIcmpv6NaPuntFlows(Uuid networkID, Collection<VirtualSubnet> subnets, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }
        Long elanTag = getNetworkElanTag(networkID);
        VirtualNetwork vnet = getNetwork(networkID);
        if (vnet != null) {
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                programIcmpv6NaPuntFlow(subnets, networkID, elanTag, dpnIfaceInfo, action);
            }
        } else {
            addUnprocessedNaFlows(networkID, subnets, action);
        }
    }

    private void programIcmpv6NaPuntFlow(Collection<VirtualSubnet> subnets, Uuid networkId, Long elanTag,
                                         VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo, int action) {
        if (dpnIfaceInfo.getDpId() == null) {
            LOG.error("programIcmpv6NaPuntFlow - DpnId is null, networkId={}, elanTag={}, dpnIfaceInfo={},"
                    + " action={}", networkId, elanTag, dpnIfaceInfo, action);
            return;
        }
        for (VirtualSubnet subnet : subnets) {
            Ipv6Prefix ipv6SubnetPrefix = subnet.getSubnetCidr().getIpv6Prefix();
            if (ipv6SubnetPrefix != null) {
                if (Ipv6ServiceUtils.isActionAdd(action)
                        && !dpnIfaceInfo.isSubnetCidrFlowAlreadyConfigured(subnet.getSubnetUUID())) {
                    ipv6ServiceUtils.installIcmpv6NaPuntFlow(NwConstants.IPV6_TABLE, ipv6SubnetPrefix,
                            dpnIfaceInfo.getDpId(), elanTag, action);
                    dpnIfaceInfo.updateSubnetCidrFlowStatus(subnet.getSubnetUUID(), action);
                } else if (Ipv6ServiceUtils.isActionDelete(action)
                        && dpnIfaceInfo.isSubnetCidrFlowAlreadyConfigured(subnet.getSubnetUUID())) {
                    ipv6ServiceUtils.installIcmpv6NaPuntFlow(NwConstants.IPV6_TABLE, ipv6SubnetPrefix,
                            dpnIfaceInfo.getDpId(), elanTag, action);
                    dpnIfaceInfo.updateSubnetCidrFlowStatus(subnet.getSubnetUUID(), action);
                }
            }
        }
    }

    public void handleInterfaceStateEvent(VirtualPort port, BigInteger dpId, VirtualPort routerPort,int lportTag ,
                                          int addOrRemove) {
        Long elanTag = getNetworkElanTag(port.getNetworkID());
        if (routerPort != null) {
            programIcmpv6PuntFlows(port, dpId, elanTag, routerPort, lportTag, addOrRemove);
        }

        if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(), Ipv6serviceConfig.NaResponderMode.Switch)) {
            checkIcmpv6NsMatchAndResponderFlow(dpId, lportTag, port, addOrRemove);
        }

        if (elanTag != null) {
            programIcmpv6NaForwardFlow(port, dpId, elanTag, addOrRemove);
        } else {
            addUnprocessedNaFlows(port.getNetworkID(), port.getSubnets(), addOrRemove);
        }
    }

    /**
     * Checks and programs icmpv6 punt flows if its the first VM for the network on
     * the DPN. Removes flows if it is the last VM for the network on the DPN.
     *
     * @param vmPort the vm port
     * @param dpId the dp id
     * @param elanTag the elan tag
     * @param routerPort the router port
     * @param lportTag the lport tag
     * @param action the action
     */

    private void programIcmpv6PuntFlows(IVirtualPort vmPort, BigInteger dpId, Long elanTag, VirtualPort routerPort,
                                        int lportTag, int action) {
        Uuid networkId = vmPort.getNetworkID();
        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            VirtualNetwork.DpnInterfaceInfo dpnInfo = vnet.getDpnIfaceInfo(dpId);
            if (dpnInfo == null) {
                LOG.error(
                        "programIcmpv6PuntFlows dpnInfo is null port={}, dpId={}, elanTag={}, routerPort={},"
                                + " lportTag={}, action={}",
                        vmPort.getIntfUUID(), dpId, elanTag, routerPort.getIntfUUID(), lportTag, action);
                return;
            }
            if (Ipv6ServiceUtils.isActionAdd(action)
                    || isLastPortStateToDeleteOnDpn(action, dpnInfo.ofPortMap.size())) {
                programIcmpv6RSPuntFlow(elanTag, vnet, dpId, action);
                programIcmpv6NaPuntFlow(routerPort.getSubnets(), networkId, elanTag, dpnInfo, action);

                if (Objects.equals(ipV6NAConfigHelper.getNaResponderMode(),
                        Ipv6serviceConfig.NaResponderMode.Controller)) {
                    for (Ipv6Address ipv6Address : routerPort.getIpv6Addresses()) {
                        addUnprocessedNaFlows(networkId, routerPort.getSubnets(), action);
                    }
                }
            }
        } else {
            addUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId, action);
            for (Ipv6Address ipv6Address : routerPort.getIpv6Addresses()) {
                addUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId, ipv6Address, action);
            }
            addUnprocessedNaFlows(networkId, routerPort.getSubnets(), action);
        }
    }

    private boolean isLastPortStateToDeleteOnDpn(int action, int noOfPortsOnDpn) {
        return Ipv6ServiceUtils.isActionDelete(action)
                && noOfPortsOnDpn <= Ipv6ServiceConstants.FIRST_OR_LAST_VM_ON_DPN;
    }

    private void programIcmpv6NaForwardFlows(Uuid networkId, VirtualSubnet subnet, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }
        if (subnet == null) {
            LOG.debug("Subnet is null, skipping programIcmpv6NaForwardFlows.");
            return;
        }

        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            if (!Ipv6ServiceUtils.isIpv6Subnet(subnet)) {
                return;
            }
            Long elanTag = getNetworkElanTag(networkId);
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                if (dpnIfaceInfo.getDpId() == null) {
                    continue;
                }
                List<VirtualPort> vmPorts = getVmPortsInSubnetByDpId(subnet.getSubnetUUID(), dpnIfaceInfo.getDpId());
                for (VirtualPort vmPort : vmPorts) {
                    programIcmpv6NaForwardFlow(vmPort, dpnIfaceInfo.getDpId(), elanTag, action);
                }
            }
        } else {
            addUnprocessedNaFlows(networkId, Sets.newHashSet(subnet), action);
        }
    }

    /**
     * Programs ICMPv6 NA forwarding flow for fixed IPs of neutron port. NA's from non-fixed IPs are
     * punted to controller for learning.
     *
     * @param vmPort the VM port
     * @param dpId the DP ID
     * @param elanTag the ELAN tag
     * @param addOrRemove the add or remove flag
     */
    private void programIcmpv6NaForwardFlow(IVirtualPort vmPort, BigInteger dpId, Long elanTag, int addOrRemove) {
        ipv6ServiceUtils.installIcmpv6NaForwardFlow(NwConstants.IPV6_TABLE, vmPort, dpId, elanTag, addOrRemove);
    }

    public List<VirtualPort> getVmPortsInSubnetByDpId(Uuid snetId, BigInteger dpId) {
        List<VirtualPort> vmPorts = new ArrayList<>();
        for (VirtualPort port : vintfs.values()) {
            if (dpId.equals(port.getDpId()) && Ipv6ServiceUtils.isVmPort(port.getDeviceOwner())) {
                for (VirtualSubnet subnet : port.getSubnets()) {
                    if (subnet.getSubnetUUID().equals(snetId)) {
                        vmPorts.add(port);
                    }
                }
            }
        }
        return vmPorts;
    }

    public String getInterfaceNameFromTag(long portTag) {
        String interfaceName = null;
        GetInterfaceFromIfIndexInput input = new GetInterfaceFromIfIndexInputBuilder()
                .setIfIndex((int) portTag).build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> futureOutput =
                interfaceManagerRpc.getInterfaceFromIfIndex(input);
        try {
            GetInterfaceFromIfIndexOutput output = futureOutput.get().getResult();
            interfaceName = output.getInterfaceName();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error while retrieving the interfaceName from tag using getInterfaceFromIfIndex RPC");
        }
        LOG.trace("Returning interfaceName {} for tag {} form getInterfaceNameFromTag", interfaceName, portTag);
        return interfaceName;
    }

    @Nullable
    public Long updateNetworkElanTag(Uuid networkId) {
        Long elanTag = null;
        if (this.elanProvider != null) {
            ElanInstance elanInstance = this.elanProvider.getElanInstance(networkId.getValue());
            if (elanInstance != null) {
                elanTag = elanInstance.getElanTag();
                VirtualNetwork net = getNetwork(networkId);
                if (net != null) {
                    net.setElanTag(elanTag);
                }
            }
        }
        return elanTag;
    }

    public void updateNetworkMtuInfo(Uuid networkId, int mtu) {
        VirtualNetwork net = getNetwork(networkId);
        if (net != null) {
            net.setMtu(mtu);
        }
    }

    @Nullable
    public VirtualNetwork getNetwork(Uuid networkId) {
        return networkId != null ? vnetworks.get(networkId) : null;
    }

    @Nullable
    public Long getNetworkElanTag(Uuid networkId) {
        Long elanTag = null;
        IVirtualNetwork net = getNetwork(networkId);
        if (net != null) {
            elanTag = net.getElanTag();
            if (elanTag == null) {
                elanTag = updateNetworkElanTag(networkId);
            }
        }
        return elanTag;
    }

    @Nullable
    public int getNetworkMtu(Uuid networkId) {
        int mtu = 0;
        IVirtualNetwork net = getNetwork(networkId);
        if (net != null) {
            mtu = net.getMtu();
        }
        return mtu;
    }

    public void addNetwork(Uuid networkId, int mtu) {
        if (networkId == null) {
            return;
        }

        if (vnetworks.putIfAbsent(networkId, new VirtualNetwork(networkId)) == null) {
            updateNetworkMtuInfo(networkId, mtu);
            updateNetworkElanTag(networkId);
        }
        Set<VirtualPort> intfList = removeUnprocessed(unprocessedNetIntfs, networkId);
        if (intfList == null) {
            LOG.info("No unprocessed interfaces for the net {}", networkId);
            return;
        }

        for (VirtualPort intf : intfList) {
            if (intf != null) {
                updateInterfaceDpidOfPortInfo(intf.getIntfUUID());
            }
        }

        Set<Ipv6Address> ipv6Addresses =
                removeUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId);

        for (Ipv6Address ipv6Address : ipv6Addresses) {
            programIcmpv6NSPuntFlowForAddress(networkId, ipv6Address, Ipv6ServiceConstants.ADD_FLOW);
        }

        Integer action = removeUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId);
        programIcmpv6RSPuntFlows(networkId, action);

        Set<VirtualSubnet> subnets = removeUnprocessedNaFlows(networkId);
        programIcmpv6NaPuntFlows(networkId, subnets, Ipv6ServiceConstants.ADD_FLOW);
        for (VirtualSubnet subnet : subnets) {
            programIcmpv6NaForwardFlows(networkId, subnet, action);
        }
    }

    public void removeNetwork(Uuid networkId) {
        // Delete the network and the corresponding dpnIds<-->List(ports) cache.
        VirtualNetwork net = networkId != null ? vnetworks.remove(networkId) : null;
        if (null != net && null != net.getNetworkUuid()) {
            /* removing all RS flows when network gets removed, as the DPN-list is maintained only as part of
            * network cache. After removal of network there is no way to remove them today. */
            programIcmpv6RSPuntFlows(net.getNetworkUuid(), Ipv6ServiceConstants.DEL_FLOW);
            removeAllIcmpv6NSPuntFlowForNetwork(net.getNetworkUuid());
            net.removeSelf();
        }
        removeUnprocessed(unprocessedNetIntfs, networkId);
        removeUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId);
        removeUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId);
    }

    private void transmitRouterAdvertisement(VirtualPort intf, Ipv6RouterAdvertisementType advType) {
        Ipv6RouterAdvt ipv6RouterAdvert = new Ipv6RouterAdvt(packetService, this);

        VirtualNetwork vnet = getNetwork(intf.getNetworkID());
        if (vnet != null) {
            long elanTag = vnet.getElanTag();
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                LOG.debug("transmitRouterAdvertisement: Transmitting RA {} for ELAN Tag {}",
                        advType, elanTag);
                if (dpnIfaceInfo.getDpId() != null) {
                    ipv6RouterAdvert.transmitRtrAdvertisement(advType, intf, elanTag, null,
                            dpnIfaceInfo.getDpId(), intf.getIntfUUID(),
                            ipV6NAConfigHelper.getIpv6RouterReachableTimeinMS());
                }
            }
        }
    }


    private void removeAllIcmpv6NSPuntFlowForNetwork(Uuid networkId) {
        Long elanTag = getNetworkElanTag(networkId);
        VirtualNetwork vnet = vnetworks.get(networkId);
        if (vnet == null) {
            return;
        }

        Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();

        for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
            for (Ipv6Address ipv6Address : dpnIfaceInfo.ndTargetFlowsPunted) {
                if (ipv6ServiceEosHandler.isClusterOwner()) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address, Ipv6ServiceConstants.DEL_FLOW);
                }
                dpnIfaceInfo.updateNDTargetAddress(ipv6Address, Ipv6ServiceConstants.DEL_ENTRY);
            }
        }
    }


    public void transmitUnsolicitedRA(Uuid portId) {
        VirtualPort port = getPort(portId);
        LOG.debug("in transmitUnsolicitedRA for {}, port {}", portId, port);
        if (port != null) {
            transmitUnsolicitedRA(port);
        }
    }

    public void transmitUnsolicitedRA(VirtualPort port) {
        if (ipv6ServiceEosHandler.isClusterOwner()) {
            /* Only the Cluster Owner would be sending out the Periodic RAs.
               However, the timer is configured on all the nodes to handle cluster fail-over scenarios.
             */
            transmitRouterAdvertisement(port, Ipv6RouterAdvertisementType.UNSOLICITED_ADVERTISEMENT);
        }
        Timeout portTimeout = timer.setPeriodicTransmissionTimeout(port.getPeriodicTimer(),
                Ipv6ServiceConstants.PERIODIC_RA_INTERVAL,
                TimeUnit.SECONDS);
        port.setPeriodicTimeout(portTimeout);
        LOG.debug("re-started periodic RA Timer for routerIntf {}, int {}s", port.getIntfUUID(),
                Ipv6ServiceConstants.PERIODIC_RA_INTERVAL);
    }

    @Override
    public List<IVirtualPort> getInterfaceCache() {
        List<IVirtualPort> virtualPorts = new ArrayList<>();
        for (VirtualPort vport: vintfs.values()) {
            virtualPorts.add(vport);
        }
        return virtualPorts;
    }

    @Override
    public List<IVirtualNetwork> getNetworkCache() {
        List<IVirtualNetwork> virtualNetworks = new ArrayList<>();
        for (VirtualNetwork vnet: vnetworks.values()) {
            virtualNetworks.add(vnet);
        }
        return virtualNetworks;
    }

    @Override
    public List<IVirtualSubnet> getSubnetCache() {
        List<IVirtualSubnet> virtualSubnets = new ArrayList<>();
        for (VirtualSubnet vsubnet: vsubnets.values()) {
            virtualSubnets.add(vsubnet);
        }
        return virtualSubnets;
    }

    @Override
    public List<IVirtualRouter> getRouterCache() {
        List<IVirtualRouter> virtualRouter = new ArrayList<>();
        for (VirtualRouter vrouter: vrouters.values()) {
            virtualRouter.add(vrouter);
        }
        return virtualRouter;
    }

    public List<Action> getEgressAction(String interfaceName) {
        List<Action> actions = null;
        try {
            GetEgressActionsForInterfaceInputBuilder egressAction =
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(interfaceName);
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    interfaceManagerRpc.getEgressActionsForInterface(egressAction.build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}",
                        interfaceName, rpcResult.getErrors());
            } else {
                actions = rpcResult.getResult().getAction();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", interfaceName, e);
        }
        return actions;
    }

    /**
     * Check ICMPv6 NS related match.
     *
     * @param dpnId DPN ID
     * @param lportTag VM Lport Tag
     * @param intf Virtual Interface
     * @param action ADD or DEL
     */
    protected void checkIcmpv6NsMatchAndResponderFlow(BigInteger dpnId, int lportTag, final IVirtualPort intf,
                                                      int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("checkIcmpv6NsMatchAndResponderFlow: Not a cluster Owner, skip flow programming.");
            return;
        }
        VirtualNetwork vnet = getNetwork(intf.getNetworkID());
        Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = Collections.emptyList();
        if (vnet != null) {
            dpnIfaceList = vnet.getDpnIfaceList();
        }
        //VM interface case

        if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY)
                || intf.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.DEVICE_OWNER_DHCP)) {
            LOG.info("NA Responder Doesn't configure flows for type {}", intf.getDeviceOwner());
            return;
        }
        String strAction = Ipv6ServiceUtils.isActionAdd(action) ? "Added" : "Removed";
        if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
            if (!dpnIfaceList.isEmpty()) {
                LOG.debug("checkIcmpv6NsMatchAndResponderFlow: Router interface {} is {} for the network {}",
                        intf.getIntfUUID(), strAction, intf.getNetworkID().getValue());
                final long networkElanTag = getNetworkElanTag(intf.getNetworkID());
                //check if any VM is booted already before subnet is added to the router
                for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                    ndExecutorService.execute(() ->
                            checkVmBootBeforeSubnetAddRouter(dpnIfaceInfo, intf, networkElanTag, action));

                    /* Router interface case. Default NS punt flows per subnet
                     * 1. flow for matching nd_target = <Subnet_GW_IP>
                     * 2. flow for matching nd_target = <Subnet_GW_LLA>
                     */
                    for (Ipv6Address ndTarget : intf.getIpv6Addresses()) {
                        LOG.debug(
                                "checkIcmpv6NsMatchAndResponderFlow: Subnet {} on Interface {} specific flows to be {}"
                                        + " for {}",
                                intf.getNetworkID().getValue(), intf.getIntfUUID(), strAction, ndTarget.getValue());
                        programIcmpv6NsDefaultPuntFlows(intf, ndTarget, action);
                    }
                }
            }
        } else {
            if (dpnId.equals(BigInteger.ZERO) || lportTag == 0) {
                Interface interfaceState = ipv6ServiceUtils.getInterfaceStateFromOperDS(intf.getIntfUUID().getValue());
                if (interfaceState == null) {
                    LOG.warn("checkIcmpv6NsMatchAndResponderFlow: Unable to retrieve interface state for port {}.",
                            intf.getIntfUUID().getValue());
                    return;
                }
                List<String> ofportIds = interfaceState.getLowerLayerIf();
                NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
                dpnId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
                lportTag = interfaceState.getIfIndex();
            }
            LOG.debug("checkIcmpv6NsMatchAndResponderFlow: VM interface {} is {} on DPN {} for the network {}",
                    intf.getIntfUUID(), strAction, dpnId, intf.getNetworkID().getValue());
            VirtualPort routerPort = getRouterV6InterfaceForNetwork(intf.getNetworkID());
            if (routerPort == null) {
                LOG.debug("checkIcmpv6NsMatchAndResponderFlow: routerPort is null for network={}, interface={},"
                        + " action={}", intf.getNetworkID().getValue(), intf.getIntfUUID(), strAction);
                return;
            }
            for (Ipv6Address ndTargetAddr : routerPort.getIpv6Addresses()) {
                final BigInteger dpnIdFinal = dpnId;
                final int lportTagFinal = lportTag;
                coordinator.enqueueJob("NSResponder-" + lportTagFinal + ndTargetAddr.getValue(), () -> {
                    programIcmpv6NsMatchAndResponderFlow(dpnIdFinal, lportTagFinal,
                            getNetworkElanTag(intf.getNetworkID()), intf, ndTargetAddr, routerPort.getMacAddress(),
                            action);
                    return Collections.emptyList();
                });
            }
            // check if VM is first or last VM on the DPN for the network
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                if (!Objects.equals(dpnId, dpnIfaceInfo.getDpId())) {
                    continue;
                }
                dpnIfaceInfo.setOvsNaResponderFlowConfiguredStatus(intf.getIntfUUID(), lportTag, action);

                if (Ipv6ServiceUtils.isActionAdd(action)
                        || isLastPortStateToDeleteOnDpn(action, dpnIfaceInfo.ofPortMap.size())) {
                    LOG.debug("checkIcmpv6NsMatchAndResponderFlow: First or Last VM booted or removed on " + "DPN {} "
                            + "for the network {} action {}", dpnId, intf.getNetworkID().getValue(), action);
                    for (Ipv6Address ndTarget : routerPort.getIpv6Addresses()) {
                        /* programIcmpv6NsDefaultPuntFlows(routerPort, ndTarget, action); */
                        coordinator.enqueueJob("NSResponder-" + ndTarget.getValue(), () -> {
                            programIcmp6NsDefaultPuntFlowsperDpn(routerPort, ndTarget, action, dpnIfaceInfo);
                            return Collections.emptyList();
                        });
                    }
                }
            }
        }
    }

    public void checkVmBootBeforeSubnetAddRouter(final VirtualNetwork.DpnInterfaceInfo dpnInterfaceInfo,
                                                 final IVirtualPort ivirtualPort, final long networkElanTag,
                                                 int action) {
        for (Uuid vmPort : dpnInterfaceInfo.ofPortMap.values()) {
            LOG.info("checkVmBootBeforeSubnetAddRouter: ofportMap VM port values {} and port name {}",
                    dpnInterfaceInfo.ofPortMap.values(), vmPort);
            Interface interfaceState = ipv6ServiceUtils.getInterfaceStateFromOperDS(vmPort.getValue());
            final int lportTag = interfaceState.getIfIndex();
            VirtualPort vmVirtualPort = getPort(vmPort);
            for (Ipv6Address ndTarget : ivirtualPort.getIpv6Addresses()) {
                coordinator.enqueueJob("NSResponder-" + lportTag + ndTarget.getValue(), () -> {
                    programIcmpv6NsMatchAndResponderFlow(dpnInterfaceInfo.getDpId(),
                            lportTag, networkElanTag, vmVirtualPort, ndTarget,
                            ivirtualPort.getMacAddress(), action);
                    return Collections.emptyList();
                });
            }
            dpnInterfaceInfo.setOvsNaResponderFlowConfiguredStatus(vmPort, lportTag, action);
        }
    }


    /**
     * Check ICMPv6 NS related match and action.
     *
     * @param dpnId DPN ID
     * @param lportTag VM LPort Tag
     * @param elanTag IPv6 Network Elan Tag
     * @param intf Virtual Interface
     * @param ndTargetAddr ND Target Address
     * @param rtrIntMacAddress Router Interface MAC Address
     * @param action ADD or DEL
     */
    private void programIcmpv6NsMatchAndResponderFlow(BigInteger dpnId, int lportTag, long elanTag, IVirtualPort intf,
                                                      Ipv6Address ndTargetAddr, String rtrIntMacAddress,
                                                      int action) {

        LOG.trace("programIcmpv6NsMatchAndResponderFlow: Programming OVS based NA Responder Flow on DPN {} "
                        + "for network {}, port {} and IPv6Address {}.", dpnId, intf.getNetworkID().getValue(),
                intf.getIntfUUID(), ndTargetAddr);

        //NITHI
        //ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION,
            tx -> {
                /*Exact VM vNIC interface IPv6 and MAC address match flow */
                ipv6ServiceUtils.instIcmpv6NsMatchFlow(NwConstants.IPV6_TABLE, dpnId,
                         elanTag, lportTag, intf.getMacAddress(), ndTargetAddr, action, tx, true);
                /*Exact VM vNIC interface IPv6 address and missing ICMPv6 SLL option field match flow*/
                ipv6ServiceUtils.instIcmpv6NsMatchFlow(NwConstants.IPV6_TABLE, dpnId,
                         elanTag, lportTag, intf.getMacAddress(), ndTargetAddr, action, tx, false);
                /*table 81 NA responder flow with ICMPv6 TLL option field */
                ipv6ServiceUtils.installIcmpv6NaResponderFlow(NwConstants.ARP_RESPONDER_TABLE,
                         dpnId, elanTag, lportTag, intf, ndTargetAddr, rtrIntMacAddress, action, tx, true);
                /*table 81 NA responder flow without ICMPv6 TLL option field */
                ipv6ServiceUtils.installIcmpv6NaResponderFlow(NwConstants.ARP_RESPONDER_TABLE,
                         dpnId, elanTag, lportTag, intf, ndTargetAddr, rtrIntMacAddress, action, tx, false);
            }), LOG, "Error" + (Ipv6ServiceUtils.isActionAdd(action) ? "Installing" : "Uninstalling")
                + " installing OVS based NA responder flows");
    }

    public void programIcmp6NsDefaultPuntFlowsperDpn(IVirtualPort routerPort, Ipv6Address ndTarget, int action,
                                                     VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }
        boolean alreadyConfigured = dpnIfaceInfo.isNdTargetFlowAlreadyConfigured(ndTarget);
        if ((Ipv6ServiceUtils.isActionAdd(action) && !alreadyConfigured)
                || (Ipv6ServiceUtils.isActionDelete(action) && alreadyConfigured)) {
            Long elanTag = getNetworkElanTag(routerPort.getNetworkID());
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                ipv6ServiceUtils.installIcmpv6NsDefaultPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(), elanTag,
                        ndTarget, action, tx);
                dpnIfaceInfo.updateNDTargetAddress(ndTarget, action);
            }), LOG, "Error " + (Ipv6ServiceUtils.isActionAdd(action) ? "Installing" : "Uninstalling")
                    + "OVS based NA responder default subnet punt flows");
        }
    }

    /**
     * NS unspecified use case.
     *
     * @param routerPort Neutron Router Virtual Port
     * @param ndTarget ND Target Address
     * @param action ADD or DEL
     */
    public void programIcmpv6NsDefaultPuntFlows(IVirtualPort routerPort, Ipv6Address ndTarget, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }
        /*Long elanTag = getNetworkElanTag(routerPort.getNetworkID()); */
        VirtualNetwork vnet = getNetwork(routerPort.getNetworkID());
        if (vnet != null) {
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                coordinator.enqueueJob("NSResponder-" + ndTarget.getValue(), () -> {
                    programIcmp6NsDefaultPuntFlowsperDpn(routerPort, ndTarget , action, dpnIfaceInfo);
                    return Collections.emptyList();
                });

            }
        }
    }
}
