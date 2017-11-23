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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IfMgr implements ElementCache {
    static final Logger LOG = LoggerFactory.getLogger(IfMgr.class);

    private final Map<Uuid, VirtualRouter> vrouters = new HashMap<>();
    private final Map<Uuid, VirtualNetwork> vnetworks = new HashMap<>();
    private final Map<Uuid, VirtualSubnet> vsubnets = new HashMap<>();
    private final Map<Uuid, VirtualPort> vintfs = new HashMap<>();
    private final Map<Uuid, VirtualPort> vrouterv6IntfMap = new HashMap<>();
    private final Map<Uuid, List<VirtualPort>> unprocessedRouterIntfs = new HashMap<>();
    private final Map<Uuid, List<VirtualPort>> unprocessedSubnetIntfs = new HashMap<>();
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final IElanService elanProvider;
    private final IMdsalApiManager mdsalUtil;
    private final Ipv6ServiceUtils ipv6ServiceUtils = new Ipv6ServiceUtils();
    private final DataBroker dataBroker;
    private final PacketProcessingService packetService;
    private final Ipv6PeriodicTrQueue ipv6Queue = new Ipv6PeriodicTrQueue(portId -> transmitUnsolicitedRA(portId));

    private final Ipv6ServiceUtils ipv6Utils = Ipv6ServiceUtils.getInstance();

    @Inject
    public IfMgr(DataBroker dataBroker, IElanService elanProvider, OdlInterfaceRpcService interfaceManagerRpc,
            IMdsalApiManager mdsalUtil, PacketProcessingService packetService) {
        this.dataBroker = dataBroker;
        this.elanProvider = elanProvider;
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.mdsalUtil = mdsalUtil;
        this.packetService = packetService;
        LOG.info("IfMgr is enabled");
    }

    /**
     * Add router.
     *
     * @param rtrUuid router uuid
     * @param rtrName router name
     * @param tenantId tenant id
     */
    public void addRouter(Uuid rtrUuid, String rtrName, Uuid tenantId) {

        VirtualRouter rtr = new VirtualRouter();

        rtr.setTenantID(tenantId)
                .setRouterUUID(rtrUuid)
                .setName(rtrName);
        vrouters.put(rtrUuid, rtr);

        List<VirtualPort> intfList = unprocessedRouterIntfs.get(rtrUuid);

        if (intfList == null) {
            LOG.info("No unprocessed interfaces for the router {}", rtrUuid);
            return;
        }

        for (VirtualPort intf : intfList) {
            if (intf != null) {
                intf.setRouter(rtr);
                rtr.addInterface(intf);

                for (VirtualSubnet snet : intf.getSubnets()) {
                    rtr.addSubnet(snet);
                }
            }
        }

        removeUnprocessed(unprocessedRouterIntfs, rtrUuid);
    }

    /**
     * Remove Router.
     *
     * @param rtrUuid router uuid
     */
    public void removeRouter(Uuid rtrUuid) {

        VirtualRouter rtr = vrouters.get(rtrUuid);
        if (rtr != null) {
            rtr.removeSelf();
            vrouters.remove(rtrUuid);
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

        VirtualSubnet snet = new VirtualSubnet();
        snet.setTenantID(tenantId)
                .setSubnetUUID(snetId)
                .setName(name)
                .setGatewayIp(gatewayIp)
                .setIPVersion(ipVersion)
                .setSubnetCidr(subnetCidr)
                .setIpv6AddressMode(ipV6AddressMode)
                .setIpv6RAMode(ipV6RaMode);

        vsubnets.put(snetId, snet);

        List<VirtualPort> intfList = unprocessedSubnetIntfs.get(snetId);
        if (intfList == null) {
            LOG.info("No unprocessed interfaces for the subnet {}", snetId);
            return;
        }
        for (VirtualPort intf : intfList) {
            if (intf != null) {
                intf.setSubnet(snetId, snet);
                snet.addInterface(intf);

                VirtualRouter rtr = intf.getRouter();
                if (rtr != null) {
                    rtr.addSubnet(snet);
                }
            }
        }

        removeUnprocessed(unprocessedSubnetIntfs, snetId);
    }

    /**
     * Remove Subnet.
     *
     * @param snetId subnet id
     */
    public void removeSubnet(Uuid snetId) {
        VirtualSubnet snet = vsubnets.get(snetId);
        if (snet != null) {
            LOG.info("removeSubnet is invoked for {}", snetId);
            snet.removeSelf();
            vsubnets.remove(snetId);
            removeUnprocessed(unprocessedSubnetIntfs, snetId);
        }
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

        VirtualPort intf = vintfs.get(portId);
        boolean newIntf = false;
        if (intf == null) {
            intf = new VirtualPort();
            vintfs.put(portId, intf);
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(true)
                    .setDeviceOwner(deviceOwner);
            intf.setPeriodicTimer(ipv6Queue);
            newIntf = true;
            MacAddress ifaceMac = MacAddress.getDefaultInstance(macAddress);
            Ipv6Address llAddr = ipv6Utils.getIpv6LinkLocalAddressFromMac(ifaceMac);
            /* A new router interface is created. This is basically triggered when an
            IPv6 subnet is associated to the router. Check if network is already hosting
            any VMs. If so, on all the hosts that have VMs on the network, program the
            icmpv6 punt flows in IPV6_TABLE(45).
             */
            programIcmpv6RSPuntFlows(intf, Ipv6Constants.ADD_FLOW);
            programIcmpv6NSPuntFlowForAddress(intf, llAddr, Ipv6Constants.ADD_FLOW);
        } else {
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualRouter rtr = vrouters.get(rtrId);
        VirtualSubnet snet = vsubnets.get(snetId);

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

        vrouterv6IntfMap.put(networkId, intf);
        programIcmpv6NSPuntFlowForAddress(intf, fixedIp.getIpv6Address(), Ipv6Constants.ADD_FLOW);

        if (newIntf) {
            LOG.debug("start the periodic RA Timer for routerIntf {}", portId);
            transmitUnsolicitedRA(intf);
        }
    }

    public void updateRouterIntf(Uuid portId, Uuid rtrId, List<FixedIps> fixedIpsList) {
        LOG.info("updateRouterIntf portId {}, fixedIpsList {} ", portId, fixedIpsList);
        VirtualPort intf = vintfs.get(portId);
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
            intf.setSubnetInfo(fip.getSubnetId(), fixedIp);

            VirtualRouter rtr = vrouters.get(rtrId);
            VirtualSubnet snet = vsubnets.get(fip.getSubnetId());

            if (rtr != null && snet != null) {
                snet.setRouter(rtr);
                intf.setSubnet(fip.getSubnetId(), snet);
                rtr.addSubnet(snet);
            } else if (snet != null) {
                intf.setSubnet(fip.getSubnetId(), snet);
                addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
            } else {
                addUnprocessed(unprocessedRouterIntfs, rtrId, intf);
                addUnprocessed(unprocessedSubnetIntfs, fip.getSubnetId(), intf);
            }
            vrouterv6IntfMap.put(intf.getNetworkID(), intf);

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
            programIcmpv6NSPuntFlowForAddress(intf, ipv6Address, Ipv6Constants.ADD_FLOW);
        }

        for (Ipv6Address ipv6Address: existingIPv6AddressList) {
            // Some v6 subnets are disassociated from the routerPort, remove the corresponding NS Flows.
            programIcmpv6NSPuntFlowForAddress(intf, ipv6Address, Ipv6Constants.DEL_FLOW);
        }
    }

    public void addHostIntf(Uuid portId, Uuid snetId, Uuid networkId,
                            IpAddress fixedIp, String macAddress, String deviceOwner) {
        LOG.debug("addHostIntf portId {}, snetId {}, networkId {}, ip {}, mac {}",
            portId, snetId, networkId, fixedIp, macAddress);

        //Save the interface ipv6 address in its fully expanded format
        Ipv6Address addr = new Ipv6Address(InetAddresses
                .forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
        fixedIp = new IpAddress(addr);
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            intf = new VirtualPort();
            vintfs.put(portId, intf);
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(false)
                    .setDeviceOwner(deviceOwner);
            Long elanTag = getNetworkElanTag(networkId);
            // Do service binding for the port and set the serviceBindingStatus to true.
            ipv6ServiceUtils.bindIpv6Service(dataBroker, portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
            intf.setServiceBindingStatus(true);

            /* Update the intf dpnId/ofPort from the Operational Store */
            updateInterfaceDpidOfPortInfo(portId);

        } else {
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualSubnet snet = vsubnets.get(snetId);

        if (snet != null) {
            intf.setSubnet(snetId, snet);
        } else {
            addUnprocessed(unprocessedSubnetIntfs, snetId, intf);
        }
    }

    public void clearAnyExistingSubnetInfo(Uuid portId) {
        VirtualPort intf = vintfs.get(portId);
        if (intf != null) {
            intf.clearSubnetInfo();
        }
    }

    public void updateHostIntf(Uuid portId, Boolean portIncludesV6Address) {
        VirtualPort intf = vintfs.get(portId);
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
                ipv6ServiceUtils.bindIpv6Service(dataBroker, portId.getValue(), elanTag, NwConstants.IPV6_TABLE);
                intf.setServiceBindingStatus(true);
            }
        } else {
            LOG.info("In updateHostIntf, removing service binding for portId {}", portId);
            ipv6ServiceUtils.unbindIpv6Service(dataBroker, portId.getValue());
            intf.setServiceBindingStatus(true);
        }
    }

    public void updateDpnInfo(Uuid portId, BigInteger dpId, Long ofPort) {
        LOG.info("In updateDpnInfo portId {}, dpId {}, ofPort {}",
            portId, dpId, ofPort);
        VirtualPort intf = vintfs.get(portId);
        if (intf != null) {
            intf.setDpId(dpId)
                    .setOfPort(ofPort);

            // Update the network <--> List[dpnIds, List<ports>] cache.
            VirtualNetwork vnet = vnetworks.get(intf.getNetworkID());
            if (null != vnet) {
                vnet.updateDpnPortInfo(dpId, ofPort, Ipv6Constants.ADD_ENTRY);
            }
        }
    }

    public void updateInterfaceDpidOfPortInfo(Uuid portId) {
        LOG.debug("In updateInterfaceDpidOfPortInfo portId {}", portId);
        Interface interfaceState = Ipv6ServiceUtils.getInterfaceStateFromOperDS(dataBroker, portId.getValue());
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
        VirtualPort intf = vintfs.get(portId);
        if (intf != null) {
            intf.removeSelf();
            if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6Constants.NETWORK_ROUTER_INTERFACE)) {
                LOG.info("In removePort for router interface, portId {}", portId);
                vrouterv6IntfMap.remove(intf.getNetworkID(), intf);
                /* Router port is deleted. Remove the corresponding icmpv6 punt flows on all
                the dpnIds which were hosting the VMs on the network.
                 */
                programIcmpv6RSPuntFlows(intf, Ipv6Constants.DEL_FLOW);
                for (Ipv6Address ipv6Address: intf.getIpv6Addresses()) {
                    programIcmpv6NSPuntFlowForAddress(intf, ipv6Address, Ipv6Constants.DEL_FLOW);
                }
                transmitRouterAdvertisement(intf, Ipv6RtrAdvertType.CEASE_ADVERTISEMENT);
                Ipv6TimerWheel timer = Ipv6TimerWheel.getInstance();
                timer.cancelPeriodicTransmissionTimeout(intf.getPeriodicTimeout());
                intf.resetPeriodicTimeout();
                LOG.debug("Reset the periodic RA Timer for intf {}", intf.getIntfUUID());
            } else {
                LOG.info("In removePort for host interface, portId {}", portId);
                // Remove the serviceBinding entry for the port.
                ipv6ServiceUtils.unbindIpv6Service(dataBroker, portId.getValue());
                // Remove the portId from the (network <--> List[dpnIds, List <ports>]) cache.
                VirtualNetwork vnet = vnetworks.get(intf.getNetworkID());
                if (null != vnet) {
                    BigInteger dpId = intf.getDpId();
                    vnet.updateDpnPortInfo(dpId, intf.getOfPort(), Ipv6Constants.DEL_ENTRY);
                }
            }
            vintfs.remove(portId);
        }
    }

    public void deleteInterface(Uuid interfaceUuid, String dpId) {
        // Nothing to do for now
    }

    public void addUnprocessed(Map<Uuid, List<VirtualPort>> unprocessed, Uuid id, VirtualPort intf) {
        unprocessed.computeIfAbsent(id, key -> new ArrayList<>()).add(intf);
    }

    public void removeUnprocessed(Map<Uuid, List<VirtualPort>> unprocessed, Uuid id) {
        unprocessed.remove(id);
    }

    public VirtualPort getRouterV6InterfaceForNetwork(Uuid networkId) {
        LOG.debug("obtaining the virtual interface for {}", networkId);
        return vrouterv6IntfMap.get(networkId);
    }

    public VirtualPort obtainV6Interface(Uuid id) {
        VirtualPort intf = vintfs.get(id);
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

    private void programIcmpv6RSPuntFlows(IVirtualPort routerPort, int action) {
        Long elanTag = getNetworkElanTag(routerPort.getNetworkID());
        int flowStatus;
        VirtualNetwork vnet = vnetworks.get(routerPort.getNetworkID());
        if (vnet != null) {
            List<BigInteger> dpnList = vnet.getDpnsHostingNetwork();
            for (BigInteger dpId : dpnList) {
                flowStatus = vnet.getRSPuntFlowStatusOnDpnId(dpId);
                if (action == Ipv6Constants.ADD_FLOW && flowStatus == Ipv6Constants.FLOWS_NOT_CONFIGURED) {
                    ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                            mdsalUtil, Ipv6Constants.ADD_FLOW);
                    vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_CONFIGURED);
                } else if (action == Ipv6Constants.DEL_FLOW && flowStatus == Ipv6Constants.FLOWS_CONFIGURED) {
                    ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                            mdsalUtil, Ipv6Constants.DEL_FLOW);
                    vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_NOT_CONFIGURED);
                }
            }
        }
    }

    private void programIcmpv6NSPuntFlowForAddress(IVirtualPort routerPort, Ipv6Address ipv6Address, int action) {
        Long elanTag = getNetworkElanTag(routerPort.getNetworkID());
        VirtualNetwork vnet = vnetworks.get(routerPort.getNetworkID());
        if (vnet != null) {
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                if (action == Ipv6Constants.ADD_FLOW && !dpnIfaceInfo.ndTargetFlowsPunted.contains(ipv6Address)) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address.getValue(), mdsalUtil, Ipv6Constants.ADD_FLOW);
                    dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
                } else if (action == Ipv6Constants.DEL_FLOW && dpnIfaceInfo.ndTargetFlowsPunted.contains(ipv6Address)) {
                    ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpnIfaceInfo.getDpId(),
                            elanTag, ipv6Address.getValue(), mdsalUtil, Ipv6Constants.DEL_FLOW);
                    dpnIfaceInfo.updateNDTargetAddress(ipv6Address, action);
                }
            }
        }
    }

    public void programIcmpv6PuntFlowsIfNecessary(Uuid vmPortId, BigInteger dpId, VirtualPort routerPort) {
        IVirtualPort vmPort = vintfs.get(vmPortId);
        if (null != vmPort) {
            VirtualNetwork vnet = vnetworks.get(vmPort.getNetworkID());
            if (null != vnet) {
                VirtualNetwork.DpnInterfaceInfo dpnInfo = vnet.getDpnIfaceInfo(dpId);
                if (null != dpnInfo) {
                    Long elanTag = getNetworkElanTag(routerPort.getNetworkID());
                    if (vnet.getRSPuntFlowStatusOnDpnId(dpId) == Ipv6Constants.FLOWS_NOT_CONFIGURED) {
                        ipv6ServiceUtils.installIcmpv6RsPuntFlow(NwConstants.IPV6_TABLE, dpId, elanTag,
                                mdsalUtil, Ipv6Constants.ADD_FLOW);
                        vnet.setRSPuntFlowStatusOnDpnId(dpId, Ipv6Constants.FLOWS_CONFIGURED);
                    }

                    for (Ipv6Address ipv6Address: routerPort.getIpv6Addresses()) {
                        if (!dpnInfo.ndTargetFlowsPunted.contains(ipv6Address)) {
                            ipv6ServiceUtils.installIcmpv6NsPuntFlow(NwConstants.IPV6_TABLE, dpId,
                                    elanTag, ipv6Address.getValue(), mdsalUtil, Ipv6Constants.ADD_FLOW);
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
                VirtualNetwork net = vnetworks.get(networkId);
                if (null != net) {
                    net.setElanTag(elanTag);
                }
            }
        }
        return elanTag;
    }

    public Long getNetworkElanTag(Uuid networkId) {
        Long elanTag = null;
        IVirtualNetwork net = vnetworks.get(networkId);
        if (null != net) {
            elanTag = net.getElanTag();
            if (null == elanTag) {
                elanTag = updateNetworkElanTag(networkId);
            }
        }
        return elanTag;
    }

    public void addNetwork(Uuid networkId) {
        VirtualNetwork net = vnetworks.get(networkId);
        if (null == net) {
            net = new VirtualNetwork();
            net.setNetworkUuid(networkId);
            vnetworks.put(networkId, net);
            updateNetworkElanTag(networkId);
        }
    }

    public void removeNetwork(Uuid networkId) {
        // Delete the network and the corresponding dpnIds<-->List(ports) cache.
        VirtualNetwork net = vnetworks.get(networkId);
        if (null != net) {
            net.removeSelf();
            vnetworks.remove(networkId);
        }
    }

    private void transmitRouterAdvertisement(VirtualPort intf, Ipv6RtrAdvertType advType) {
        Ipv6RouterAdvt ipv6RouterAdvert = new Ipv6RouterAdvt(packetService);

        LOG.debug("in transmitRouterAdvertisement for {}", advType);
        VirtualNetwork vnet = vnetworks.get(intf.getNetworkID());
        if (vnet != null) {
            String nodeName;
            String outPort;
            Collection<VirtualNetwork.DpnInterfaceInfo> dpnIfaceList = vnet.getDpnIfaceList();
            for (VirtualNetwork.DpnInterfaceInfo dpnIfaceInfo : dpnIfaceList) {
                nodeName = Ipv6Constants.OPENFLOW_NODE_PREFIX + dpnIfaceInfo.getDpId();
                List<NodeConnectorRef> ncRefList = new ArrayList<>();
                for (Long ofPort: dpnIfaceInfo.ofPortList) {
                    outPort = nodeName + ":" + ofPort;
                    LOG.debug("Transmitting RA {} for node {}, port {}", advType, nodeName, outPort);
                    InstanceIdentifier<NodeConnector> outPortId = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(nodeName)))
                            .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId(outPort)))
                            .build();
                    ncRefList.add(new NodeConnectorRef(outPortId));
                }
                if (!ncRefList.isEmpty()) {
                    ipv6RouterAdvert.transmitRtrAdvertisement(advType, intf, ncRefList, null);
                }
            }
        }
    }

    public void transmitUnsolicitedRA(Uuid portId) {
        VirtualPort port = vintfs.get(portId);
        LOG.debug("in transmitUnsolicitedRA for {}, port", portId, port);
        if (port != null) {
            transmitUnsolicitedRA(port);
        }
    }

    public void transmitUnsolicitedRA(VirtualPort port) {
        transmitRouterAdvertisement(port, Ipv6RtrAdvertType.UNSOLICITED_ADVERTISEMENT);
        Ipv6TimerWheel timer = Ipv6TimerWheel.getInstance();
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
}
