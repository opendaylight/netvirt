/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import com.google.common.net.InetAddresses;
import org.opendaylight.netvirt.routemgr.utils.RoutemgrUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.AllocationPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IfMgr {

    /**
     * Logger instance.
     */
    static final Logger logger = LoggerFactory.getLogger(IfMgr.class);
    public static final String NETWORK_ROUTER_INTERFACE = "network:router_interface";

    public static final String DHCPV6_OFF = "DHCPV6_OFF";
    public static final String IPV6_SLAAC = "IPV6_SLAAC";
    public static final String IPV6_DHCPV6_STATEFUL = "DHCPV6_STATEFUL";
    public static final String IPV6_DHCPV6_STATELESS = "DHCPV6_STATELESS";
    public static final String IPV6_AUTO_ADDRESS_SUBNETS = IPV6_SLAAC + IPV6_DHCPV6_STATELESS;

    public static final String IP_VERSION_V4 = "IPv4";
    public static final String IP_VERSION_V6 = "IPv6";

    // router objects - routers, subnets, interfaces
    private HashMap<Uuid, VirtualRouter> vrouters;
    private HashMap<Uuid, VirtualSubnet> vsubnets;
    private HashMap<Uuid, VirtualPort> vintfs;
    private HashMap<Ipv6Address, VirtualPort> v6IntfMap;
    private HashMap<String, VirtualPort> v6MacToPortMapping;
    private HashMap<Uuid, List<VirtualPort>> unprocessedRouterIntfs;
    private HashMap<Uuid, List<VirtualPort>> unprocessedSubnetIntfs;
    private static final IfMgr IFMGR_INSTANCE = new IfMgr();
    private IPv6RtrFlow ipv6RtrFlow;

    private IfMgr() {
        init();
    }

    void init() {
        this.vrouters = new HashMap<>();
        this.vsubnets = new HashMap<>();
        this.vintfs = new HashMap<>();
        this.v6IntfMap = new HashMap<>();
        this.v6MacToPortMapping = new HashMap<>();
        this.unprocessedRouterIntfs = new HashMap<>();
        this.unprocessedSubnetIntfs = new HashMap<>();
        logger.info("IfMgr is enabled");
    }

    public static IfMgr getIfMgrInstance() {
        return IFMGR_INSTANCE;
    }

    /**
     * Add router
     *
     * @param rtrUuid router uuid
     * @param rtrName router name
     * @param tenantId tenant id
     * @param isAdminStateUp admin up
     */
    public void addRouter(Uuid rtrUuid, String rtrName, Uuid tenantId, Boolean isAdminStateUp) {

        VirtualRouter rtr = new VirtualRouter();
        if (rtr != null) {
            rtr.setTenantID(tenantId)
                    .setRouterUUID(rtrUuid)
                    .setName(rtrName);
            vrouters.put(rtrUuid, rtr);

            List<VirtualPort> intfList = unprocessedRouterIntfs.get(rtrUuid);

            if (intfList == null) {
                logger.info("intfList is null for {}", rtrUuid);
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

        } else {
            logger.error("Create router failed for :{}", rtrUuid);
        }

        return;
    }

    /**
     * Remove Router
     *
     * @param rtrUuid router uuid
     */
    public void removeRouter(Uuid rtrUuid) {

        VirtualRouter rtr = vrouters.get(rtrUuid);
        if (rtr != null) {
            rtr.removeSelf();
            vrouters.remove(rtrUuid);
            removeUnprocessed(unprocessedRouterIntfs, rtrUuid);
            rtr = null;
        } else {
            logger.error("Delete router failed for :{}", rtrUuid);
        }
        return;
    }

    /**
     * Add Subnet
     * @param snetId subnet id
     * @param name subnet name
     * @param networkId network id
     * @param tenantId tenant id
     * @param gatewayIp gateway ip address
     * @param poolsList pools list
     * @param ipVersion IP Version "IPv4 or IPv6"
     * @param subnetCidr subnet CIDR
     * @param ipV6AddressMode Address Mode of IPv6 Subnet
     * @param ipV6RaMode RA Mode of IPv6 Subnet.
     */
    public void addSubnet(Uuid snetId, String name, Uuid networkId, Uuid tenantId,
                          IpAddress gatewayIp, List<AllocationPools> poolsList,
                          String ipVersion, IpPrefix subnetCidr,
                          String ipV6AddressMode, String ipV6RaMode) {

        // Save the gateway ipv6 address in its fully expanded format. We always store the v6Addresses
        // in expanded form and are used during Neighbor Discovery Support.
        if (gatewayIp.getIpv6Address() != null) {
            Ipv6Address addr = new Ipv6Address
                    (InetAddresses.forString(gatewayIp.getIpv6Address().getValue()).getHostAddress());
            gatewayIp = new IpAddress(addr);
        }

        VirtualSubnet snet = new VirtualSubnet();
        if (snet != null) {
            snet.setTenantID(tenantId)
                    .setSubnetUUID(snetId)
                    .setName(name)
                    .setGatewayIp(gatewayIp)
                    .setIPVersion(ipVersion)
                    .setSubnetCidr(subnetCidr)
                    .setIpv6AddressMode(ipV6AddressMode)
                    .setIpv6RAMode(ipV6RaMode);

            // Add address pool
            for (AllocationPools pool : poolsList) {
                snet.addPool(pool.getStart(), pool.getEnd());
            }

            vsubnets.put(snetId, snet);

            List<VirtualPort> intfList = unprocessedSubnetIntfs.get(snetId);
            if (intfList == null) {
                logger.info("interfaces are not available for the subnet {}", snetId);
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

        } else {
            logger.error("Create subnet failed for :{}", snetId);
        }
        return;
    }

    /**
     * Remove Subnet
     *
     * @param snetId subnet id
     */
    public void removeSubnet(Uuid snetId) {

        VirtualSubnet snet = vsubnets.get(snetId);
        if (snet != null) {
            snet.removeSelf();
            vsubnets.remove(snetId);
            removeUnprocessed(unprocessedSubnetIntfs, snetId);
            snet = null;
        } else {
            logger.error("Delete subnet failed for :{}", snetId);
        }
        return;
    }

    public void addRouterIntf(Uuid portId, Uuid rtrId, Uuid snetId,
                              Uuid networkId, IpAddress fixedIp, String macAddress,
                              String deviceOwner) {
        logger.debug("addRouterIntf portId {}, rtrId {}, snetId {}, networkId {}, ip {}, mac {}",
            portId, rtrId, snetId, networkId, fixedIp, macAddress);
        //Save the interface ipv6 address in its fully expanded format
        if (fixedIp.getIpv6Address() != null) {
            Ipv6Address addr = new Ipv6Address
                (InetAddresses.forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
            fixedIp = new IpAddress(addr);
        }
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            intf = new VirtualPort();
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                logger.error("Create rtr intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setNodeUUID(rtrId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(true)
                    .setDeviceOwner(deviceOwner);
            v6MacToPortMapping.put(macAddress, intf);
            // Currently we only require the RouterIface LLA mapping in the v6IntfMap, hence storing
            // only this info and not the LLA address of the HostIfaces.
            RoutemgrUtil instance = RoutemgrUtil.getInstance();
            MacAddress ifaceMac = MacAddress.getDefaultInstance(macAddress);
            Ipv6Address llAddr = instance.getIpv6LinkLocalAddressFromMac(ifaceMac);
            v6IntfMap.put(llAddr, intf);
            programNSFlowForAddress(intf, llAddr, true);
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
        if (fixedIp.getIpv6Address() != null) {
            v6IntfMap.put(fixedIp.getIpv6Address(), intf);
            programNSFlowForAddress(intf, fixedIp.getIpv6Address(), true);
        }
        return;
    }

    public void addHostIntf(Uuid portId, Uuid snetId, Uuid networkId,
                            IpAddress fixedIp, String macAddress, String deviceOwner) {
        logger.debug("addHostIntf portId {}, snetId {}, networkId {}, ip {}, mac {}",
            portId, snetId, networkId, fixedIp, macAddress);
        //Save the interface ipv6 address in its fully expanded format
        if (fixedIp.getIpv6Address() != null) {
            Ipv6Address addr = new Ipv6Address
                (InetAddresses.forString(fixedIp.getIpv6Address().getValue()).getHostAddress());
            fixedIp = new IpAddress(addr);
        }
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            intf = new VirtualPort();
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                logger.error("Create host intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(false)
                    .setDeviceOwner(deviceOwner);
            v6MacToPortMapping.put(macAddress, intf);
        } else {
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualSubnet snet = vsubnets.get(snetId);

        if (snet != null) {
            intf.setSubnet(snetId, snet);
        } else {
            addUnprocessed(unprocessedSubnetIntfs, snetId, intf);
        }
        if (fixedIp.getIpv6Address() != null) {
            v6IntfMap.put(fixedIp.getIpv6Address(), intf);
        }
        return;
    }

    public void updateInterface(Uuid portId, String dpId, Long ofPort) {
        logger.debug("in updateInterface portId {}, dpId {}, ofPort {}",
            portId, dpId, ofPort);
        VirtualPort intf = vintfs.get(portId);

        if (intf == null) {
            intf = new VirtualPort();
            intf.setIntfUUID(portId);
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                logger.error("updateInterface failed for :{}", portId);
            }
        }

        if (intf != null) {
            intf.setDpId(dpId)
                    .setOfPort(ofPort);
        }
        return;
    }

    public void removePort(Uuid portId) {
        VirtualPort intf = vintfs.get(portId);
        if (intf != null) {
            intf.removeSelf();
            v6MacToPortMapping.remove(intf.getMacAddress());
            if (intf.getDeviceOwner().equalsIgnoreCase(NETWORK_ROUTER_INTERFACE)) {
                RoutemgrUtil instance = RoutemgrUtil.getInstance();
                MacAddress ifaceMac = MacAddress.getDefaultInstance(intf.getMacAddress());
                v6IntfMap.remove(instance.getIpv6LinkLocalAddressFromMac(ifaceMac), intf);
                programNSFlowForAddress(intf, instance.getIpv6LinkLocalAddressFromMac(ifaceMac), false);
            }
            for (IpAddress ipAddr : intf.getIpAddresses()) {
                if (ipAddr.getIpv6Address() != null) {
                    v6IntfMap.remove(ipAddr.getIpv6Address());
                    if (intf.getDeviceOwner().equalsIgnoreCase(NETWORK_ROUTER_INTERFACE)) {
                        programNSFlowForAddress(intf, ipAddr.getIpv6Address(), false);
                    }
                }
            }
            vintfs.remove(portId);
            intf = null;
        } else {
            logger.error("Delete intf failed for :{}", portId);
        }
        return;
    }

    public void deleteInterface(Uuid interfaceUuid, String dpId) {
        // Nothing to do for now
        return;
    }

    public void addUnprocessed(HashMap<Uuid, List<VirtualPort>> unprocessed, Uuid id, VirtualPort intf) {

        List<VirtualPort> intfList = unprocessed.get(id);

        if (intfList == null) {
            intfList = new ArrayList();
            intfList.add(intf);
            unprocessed.put(id, intfList);
        } else {
            intfList.add(intf);
        }
        return;
    }

    public void removeUnprocessed(HashMap<Uuid, List<VirtualPort>> unprocessed, Uuid id) {

        List<VirtualPort> intfList = unprocessed.get(id);
        intfList = null;
        return;
    }

    public VirtualPort getInterfaceForAddress(Ipv6Address addr) {
        logger.debug("obtaining the virtual interface for {}", addr);
        return (v6IntfMap.get(addr));
    }

    /**
     * Retrieve the VirtualPort corresponding to the given MAC Address
     *
     * @param macAddress Interface MAC Address.
     * @return VirtualPort corresponding to the macAddress
     */
    public VirtualPort getInterfaceForMacAddress(String macAddress) {
        logger.debug("obtaining the virtual interface for {}", macAddress);
        return (v6MacToPortMapping.get(macAddress));
    }

    public VirtualPort obtainV6Interface(Uuid id) {
        VirtualPort intf = vintfs.get(id);
        if (intf == null) {
            return null;
        }
        for (VirtualSubnet snet : intf.getSubnets()) {
            if (snet.getGatewayIp().getIpv6Address() != null) {
                return intf;
            }
        }
        return null;
    }

    public void setIPv6RtrFlowService(IPv6RtrFlow ipv6RtrFlowService) {
        this.ipv6RtrFlow = ipv6RtrFlowService;
    }

    private void programNSFlowForAddress(VirtualPort intf, Ipv6Address address, boolean action) {
        Uuid netId = intf.getNetworkID();
        for (VirtualPort port : vintfs.values()) {
            if ((port.getOfPort() != null) && (port.getNetworkID().equals(netId))) {
                if (action == true) {
                   ipv6RtrFlow.addIcmpv6NSFlow2Controller(port.getDpId(), address);
                } else {
                    ipv6RtrFlow.removeIcmpv6NSFlow2Controller(port.getDpId(), address);
                }
            }
        }
    }
}
