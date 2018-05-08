/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import com.google.common.net.InetAddresses;
import io.netty.util.Timeout;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.ipv6service.api.ElementCache;
import org.opendaylight.netvirt.ipv6service.api.IVirtualNetwork;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.api.IVirtualRouter;
import org.opendaylight.netvirt.ipv6service.api.IVirtualSubnet;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants.Ipv6RtrAdvertType;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6PeriodicTrQueue;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6TimerWheel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
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
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final IElanService elanProvider;
    private final Ipv6ServiceUtils ipv6ServiceUtils;
    private final DataBroker dataBroker;
    private final Ipv6ServiceEosHandler ipv6ServiceEosHandler;
    private final PacketProcessingService packetService;
    private final Ipv6PeriodicTrQueue ipv6Queue = new Ipv6PeriodicTrQueue(this::transmitUnsolicitedRA);
    private final Ipv6TimerWheel timer = new Ipv6TimerWheel();

    @Inject
    public IfMgr(DataBroker dataBroker, IElanService elanProvider, OdlInterfaceRpcService interfaceManagerRpc,
                 PacketProcessingService packetService, Ipv6ServiceUtils ipv6ServiceUtils,
                 Ipv6ServiceEosHandler ipv6ServiceEosHandler) {
        this.dataBroker = dataBroker;
        this.elanProvider = elanProvider;
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.packetService = packetService;
        this.ipv6ServiceUtils = ipv6ServiceUtils;
        this.ipv6ServiceEosHandler = ipv6ServiceEosHandler;
        LOG.info("IfMgr is enabled");
    }

    @Override
    @PreDestroy
    public void close() {
        timer.close();
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

    private VirtualRouter getRouter(Uuid rtrId) {
        return rtrId != null ? vrouters.get(rtrId) : null;
    }

    public void addRouterIntf(Uuid portId, Uuid rtrId, Uuid snetId,
                              Uuid networkId, IpAddress fixedIp, String macAddress,
                              String deviceOwner) {
        LOG.debug("addRouterIntf portId {}, rtrId {}, snetId {}, networkId {}, ip {}, mac {}",
                portId, rtrId, snetId, networkId, fixedIp, macAddress);
        //Save the interface ipv6 address in its fully expanded format
        Ipv6Address addr = new Ipv6Address(InetAddresses
                .forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
        fixedIp = new IpAddress(addr);

        VirtualPort intf = VirtualPort.builder().intfUUID(portId).networkID(networkId).macAddress(macAddress)
                .routerIntfFlag(true).deviceOwner(deviceOwner).build();
        intf.setSubnetInfo(snetId, fixedIp);
        intf.setPeriodicTimer(ipv6Queue);

        boolean newIntf = false;
        VirtualPort prevIntf = vintfs.putIfAbsent(portId, intf);
        if (prevIntf == null) {
            newIntf = true;
            MacAddress ifaceMac = MacAddress.getDefaultInstance(macAddress);
            Ipv6Address llAddr = ipv6ServiceUtils.getIpv6LinkLocalAddressFromMac(ifaceMac);
            /* A new router interface is created. This is basically triggered when an
            IPv6 subnet is associated to the router. Check if network is already hosting
            any VMs. If so, on all the hosts that have VMs on the network, program the
            icmpv6 punt flows in IPV6_TABLE(45).
             */
            programIcmpv6RSPuntFlows(intf.getNetworkID(), Ipv6Constants.ADD_FLOW);
            programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), llAddr, Ipv6Constants.ADD_FLOW);
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
            vrouterv6IntfMap.put(networkId, intf);
        }

        programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), fixedIp.getIpv6Address(), Ipv6Constants.ADD_FLOW);

        if (newIntf) {
            LOG.debug("start the periodic RA Timer for routerIntf {}", portId);
            transmitUnsolicitedRA(intf);
        }
    }

    public void updateRouterIntf(Uuid portId, Uuid rtrId, List<FixedIps> fixedIpsList) {
        LOG.info("updateRouterIntf portId {}, fixedIpsList {} ", portId, fixedIpsList);
        VirtualPort intf = getPort(portId);
        if (intf == null) {
            LOG.info("Skip Router interface update for non-ipv6 port {}", portId);
            return;
        }

        List<Ipv6Address> existingIPv6AddressList = intf.getIpv6AddressesWithoutLLA();
        List<Ipv6Address> newlyAddedIpv6AddressList = new ArrayList<>();
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


            Uuid networkID = intf.getNetworkID();
            if (networkID != null) {
                vrouterv6IntfMap.put(networkID, intf);
            }

            if (existingIPv6AddressList.contains(fixedIp.getIpv6Address())) {
                existingIPv6AddressList.remove(fixedIp.getIpv6Address());
            } else {
                newlyAddedIpv6AddressList.add(fixedIp.getIpv6Address());
            }
        }

        /* This is a port update event for routerPort. Check if any IPv6 subnet is added
         or removed from the router port. Depending on subnet added/removed, we add/remove
         the corresponding flows from IPV6_TABLE(45).
         */
        for (Ipv6Address ipv6Address: newlyAddedIpv6AddressList) {
            // Some v6 subnets are associated to the routerPort add the corresponding NS Flows.
            programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), ipv6Address, Ipv6Constants.ADD_FLOW);
        }

        for (Ipv6Address ipv6Address: existingIPv6AddressList) {
            // Some v6 subnets are disassociated from the routerPort, remove the corresponding NS Flows.
            programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), ipv6Address, Ipv6Constants.DEL_FLOW);
        }
    }

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
            // Do service binding for the port and set the serviceBindingStatus to true.
            ipv6ServiceUtils.bindIpv6Service(portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
            intf.setServiceBindingStatus(true);

            /* Update the intf dpnId/ofPort from the Operational Store */
            updateInterfaceDpidOfPortInfo(portId);

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
        if (portIncludesV6Address) {
            if (!intf.getServiceBindingStatus()) {
                Long elanTag = getNetworkElanTag(intf.getNetworkID());
                LOG.info("In updateHostIntf, service binding for portId {}", portId);
                ipv6ServiceUtils.bindIpv6Service(portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
                intf.setServiceBindingStatus(true);
            }
        } else {
            LOG.info("In updateHostIntf, removing service binding for portId {}", portId);
            ipv6ServiceUtils.unbindIpv6Service(portId.getValue());
            intf.setServiceBindingStatus(false);
        }
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
            if (null != vnet) {
                vnet.updateDpnPortInfo(dpId, ofPort, Ipv6Constants.ADD_ENTRY);
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
        BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        if (!dpId.equals(Ipv6Constants.INVALID_DPID)) {
            Long ofPort = MDSALUtil.getOfPortNumberFromPortName(nodeConnectorId);
            updateDpnInfo(portId, dpId, ofPort);
        }
    }


    public void removePort(Uuid portId) {
        VirtualPort intf = portId != null ? vintfs.remove(portId) : null;
        if (intf != null) {
            intf.removeSelf();
            Uuid networkID = intf.getNetworkID();
            if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6Constants.NETWORK_ROUTER_INTERFACE)) {
                LOG.info("In removePort for router interface, portId {}", portId);

                if (networkID != null) {
                    vrouterv6IntfMap.remove(networkID, intf);
                }

                /* Router port is deleted. Remove the corresponding icmpv6 punt flows on all
                the dpnIds which were hosting the VMs on the network.
                 */
                programIcmpv6RSPuntFlows(intf.getNetworkID(), Ipv6Constants.DEL_FLOW);
                for (Ipv6Address ipv6Address: intf.getIpv6Addresses()) {
                    programIcmpv6NSPuntFlowForAddress(intf.getNetworkID(), ipv6Address, Ipv6Constants.DEL_FLOW);
                }
                transmitRouterAdvertisement(intf, Ipv6RtrAdvertType.CEASE_ADVERTISEMENT);
                timer.cancelPeriodicTransmissionTimeout(intf.getPeriodicTimeout());
                intf.resetPeriodicTimeout();
                LOG.debug("Reset the periodic RA Timer for intf {}", intf.getIntfUUID());
            } else {
                LOG.info("In removePort for host interface, portId {}", portId);
                // Remove the serviceBinding entry for the port.
                ipv6ServiceUtils.unbindIpv6Service(portId.getValue());
                // Remove the portId from the (network <--> List[dpnIds, List <ports>]) cache.
                VirtualNetwork vnet = getNetwork(networkID);
                if (null != vnet) {
                    BigInteger dpId = intf.getDpId();
                    vnet.updateDpnPortInfo(dpId, intf.getOfPort(), Ipv6Constants.DEL_ENTRY);
                }
            }
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

    public Set<VirtualPort> removeUnprocessed(Map<Uuid, Set<VirtualPort>> unprocessed, Uuid id) {
        if (id != null) {
            return unprocessed.remove(id);
        }
        return null;
    }

    public void addUnprocessedRSFlows(Map<Uuid, Integer>
                                              unprocessed, Uuid id, Integer action) {
        unprocessed.put(id, action);

    }

    public Integer removeUnprocessedRSFlows(Map<Uuid, Integer>
                                                    unprocessed, Uuid id) {
        return unprocessed.remove(id);
    }

    public void addUnprocessedNSFlows(Map<Uuid, Set<Ipv6Address>> unprocessed,
                                      Uuid id, Ipv6Address ipv6Address,
                                      Integer action) {
        Set<Ipv6Address> ipv6AddressesList = unprocessed.get(id);
        if (action == Ipv6Constants.ADD_FLOW) {
            unprocessed.computeIfAbsent(id, key -> Collections.synchronizedSet(ConcurrentHashMap.newKeySet()))
                    .add(ipv6Address);
        } else if (action == Ipv6Constants.DEL_FLOW) {
            if ((ipv6AddressesList != null) && (ipv6AddressesList.contains(ipv6Address))) {
                ipv6AddressesList.remove(ipv6Address);
            }
        }
        return;
    }

    public Set<Ipv6Address> removeUnprocessedNSFlows(Map<Uuid, Set<Ipv6Address>>
                                                             unprocessed, Uuid id) {
        return unprocessed.remove(id);
    }

    public VirtualPort getRouterV6InterfaceForNetwork(Uuid networkId) {
        LOG.debug("obtaining the virtual interface for {}", networkId);
        return networkId != null ? vrouterv6IntfMap.get(networkId) : null;
    }

    public VirtualPort obtainV6Interface(Uuid id) {
        VirtualPort intf = getPort(id);
        if (intf == null) {
            return null;
        }
        for (VirtualSubnet snet : intf.getSubnets()) {
            if (snet.getIpVersion().equals(Ipv6Constants.IP_VERSION_V6)) {
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
        int flowStatus;
        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            List<BigInteger> dpnList = vnet.getDpnsHostingNetwork();
            for (BigInteger dpId : dpnList) {
                flowStatus = vnet.getRSPuntFlowStatusOnDpnId(dpId);
                if (action == Ipv6Constants.ADD_FLOW && flowStatus == Ipv6Constants.FLOWS_NOT_CONFIGURED) {
                    ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                            Ipv6Constants.ADD_FLOW);
                    vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_CONFIGURED);
                } else if (action == Ipv6Constants.DEL_FLOW && flowStatus == Ipv6Constants.FLOWS_CONFIGURED) {
                    ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                            Ipv6Constants.DEL_FLOW);
                    vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_NOT_CONFIGURED);
                }
            }
        } else {
            addUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId, action);
        }
    }

    private void programIcmpv6NSPuntFlowForAddress(Uuid networkId, Ipv6Address ipv6Address, int action) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }

        Long elanTag = getNetworkElanTag(networkId);
        VirtualNetwork vnet = getNetwork(networkId);
        if (vnet != null) {
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                if (action == Ipv6Constants.ADD_FLOW && !dpnIfaceInfo.ndTargetFlowsPunted.contains(ipv6Address)
                        && dpnIfaceInfo.getDpId() != null) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address.getValue(), Ipv6Constants.ADD_FLOW);
                    dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
                } else if (action == Ipv6Constants.DEL_FLOW && dpnIfaceInfo.ndTargetFlowsPunted.contains(ipv6Address)) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address.getValue(), Ipv6Constants.DEL_FLOW);
                    dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
                }
            }
        } else {
            addUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId, ipv6Address, action);
        }
    }


    public void programIcmpv6PuntFlowsIfNecessary(Uuid vmPortId, BigInteger dpId, VirtualPort routerPort) {
        if (!ipv6ServiceEosHandler.isClusterOwner()) {
            LOG.trace("Not a cluster Owner, skip flow programming.");
            return;
        }

        IVirtualPort vmPort = getPort(vmPortId);
        if (null != vmPort) {
            VirtualNetwork vnet = getNetwork(vmPort.getNetworkID());
            if (null != vnet) {
                VirtualNetwork.DpnInterfaceInfo dpnInfo = vnet.getDpnIfaceInfo(dpId);
                if (null != dpnInfo) {
                    Long elanTag = getNetworkElanTag(routerPort.getNetworkID());
                    if (vnet.getRSPuntFlowStatusOnDpnId(dpId) == Ipv6Constants.FLOWS_NOT_CONFIGURED) {
                        ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                                Ipv6Constants.ADD_FLOW);
                        vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_CONFIGURED);
                    }

                    for (Ipv6Address ipv6Address: routerPort.getIpv6Addresses()) {
                        if (!dpnInfo.ndTargetFlowsPunted.contains(ipv6Address)) {
                            ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpId,
                                    elanTag, ipv6Address.getValue(), Ipv6Constants.ADD_FLOW);
                            dpnInfo.updateNDTargetAddress(ipv6Address, Ipv6Constants.ADD_FLOW);
                        }
                    }
                }
            }
        }
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

    public Long updateNetworkElanTag(Uuid networkId) {
        Long elanTag = null;
        if (null != this.elanProvider) {
            ElanInstance elanInstance = this.elanProvider.getElanInstance(networkId.getValue());
            if (null != elanInstance) {
                elanTag = elanInstance.getElanTag();
                VirtualNetwork net = getNetwork(networkId);
                if (null != net) {
                    net.setElanTag(elanTag);
                }
            }
        }
        return elanTag;
    }

    private VirtualNetwork getNetwork(Uuid networkId) {
        return networkId != null ? vnetworks.get(networkId) : null;
    }

    public Long getNetworkElanTag(Uuid networkId) {
        Long elanTag = null;
        IVirtualNetwork net = getNetwork(networkId);
        if (null != net) {
            elanTag = net.getElanTag();
            if (null == elanTag) {
                elanTag = updateNetworkElanTag(networkId);
            }
        }
        return elanTag;
    }

    public void addNetwork(Uuid networkId) {
        if (networkId == null) {
            return;
        }

        if (vnetworks.putIfAbsent(networkId, new VirtualNetwork(networkId)) == null) {
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
            programIcmpv6NSPuntFlowForAddress(networkId, ipv6Address, Ipv6Constants.ADD_FLOW);
        }

        Integer action = removeUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId);
        programIcmpv6RSPuntFlows(networkId, action);
    }

    public void removeNetwork(Uuid networkId) {
        // Delete the network and the corresponding dpnIds<-->List(ports) cache.
        VirtualNetwork net = networkId != null ? vnetworks.remove(networkId) : null;
        if (null != net && null != net.getNetworkUuid()) {
            /* removing all RS flows when network gets removed, as the DPN-list is maintained only as part of
            * network cache. After removal of network there is no way to remove them today. */
            programIcmpv6RSPuntFlows(net.getNetworkUuid(), Ipv6Constants.DEL_FLOW);
            removeAllIcmpv6NSPuntFlowForNetwork(net.getNetworkUuid());
            net.removeSelf();
        }
        removeUnprocessed(unprocessedNetIntfs, networkId);
        removeUnprocessedRSFlows(unprocessedNetRSFlowIntfs, networkId);
        removeUnprocessedNSFlows(unprocessedNetNSFlowIntfs, networkId);
    }

    private void transmitRouterAdvertisement(VirtualPort intf, Ipv6RtrAdvertType advType) {
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
                            dpnIfaceInfo.getDpId(), intf.getIntfUUID());
                }
            }
        }
    }


    private void removeAllIcmpv6NSPuntFlowForNetwork(Uuid networkId) {
        Long elanTag = getNetworkElanTag(networkId);
        VirtualNetwork vnet = vnetworks.get(networkId);
        Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();

        for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
            for (Ipv6Address ipv6Address : dpnIfaceInfo.ndTargetFlowsPunted) {
                if (ipv6ServiceEosHandler.isClusterOwner()) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address.getValue(), Ipv6Constants.DEL_FLOW);
                }
                dpnIfaceInfo.updateNDTargetAddress(ipv6Address, Ipv6Constants.DEL_ENTRY);
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
            transmitRouterAdvertisement(port, Ipv6RtrAdvertType.UNSOLICITED_ADVERTISEMENT);
        }
        Timeout portTimeout = timer.setPeriodicTransmissionTimeout(port.getPeriodicTimer(),
                Ipv6Constants.PERIODIC_RA_INTERVAL,
                TimeUnit.SECONDS);
        port.setPeriodicTimeout(portTimeout);
        LOG.debug("re-started periodic RA Timer for routerIntf {}, int {}s", port.getIntfUUID(),
                Ipv6Constants.PERIODIC_RA_INTERVAL);
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
}
