/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import com.google.common.net.InetAddresses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.AllocationPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfMgr {

    /**
     * Logger instance.
     */
    static final Logger logger = LoggerFactory.getLogger(IfMgr.class);

    // router objects - routers, subnets, interfaces
    private HashMap<Uuid, VirtualRouter> vrouters;
    private HashMap<Uuid, VirtualSubnet> vsubnets;
    private HashMap<Uuid, VirtualPort> vintfs;
    private HashMap<Ipv6Address, VirtualPort> v6IntfMap;
    private HashMap<Uuid, List<VirtualPort>> unprocessedRouterIntfs;
    private HashMap<Uuid, List<VirtualPort>> unprocessedSubnetIntfs;
    private static final IfMgr IFMGR_INSTANCE = new IfMgr();

    private IfMgr() {
        init();
    }

    void init() {
        this.vrouters = new HashMap<>();
        this.vsubnets = new HashMap<>();
        this.vintfs = new HashMap<>();
        this.v6IntfMap = new HashMap<>();
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
     *
     * @param snetId subnet id
     * @param name subnet name
     * @param networkId network id
     * @param tenantId tenant id
     * @param gatewayIp gateway ip address
     * @param poolsList pools list
     */
    public void addSubnet(Uuid snetId, String name, Uuid networkId, Uuid tenantId,
                          IpAddress gatewayIp, List<AllocationPools> poolsList) {

        VirtualSubnet snet = new VirtualSubnet();
        if (snet != null) {
            snet.setTenantID(tenantId)
                    .setSubnetUUID(snetId)
                    .setName(name)
                    .setGatewayIp(gatewayIp);

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
                              Uuid networkId, IpAddress fixedIp, String macAddress) {
        logger.debug("addRouterIntf portId {}, rtrId {}, snetId {}, networkId {}, ip {}, mac {}",
            portId, rtrId, snetId, networkId, fixedIp, macAddress);
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            intf = new VirtualPort();
            if (intf != null) {
                synchronized (IfMgr.class) {
                    vintfs.put(portId, intf);
                }
            } else {
                logger.error("Create rtr intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setNodeUUID(rtrId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(true);
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
        }
        return;
    }

    public void addHostIntf(Uuid portId, Uuid snetId, Uuid networkId,
                            IpAddress fixedIp, String macAddress) {
        logger.debug("addHostIntf portId {}, snetId {}, networkId {}, ip {}, mac {}",
            portId, snetId, networkId, fixedIp, macAddress);
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            intf = new VirtualPort();
            if (intf != null) {
                synchronized (IfMgr.class) {
                    vintfs.put(portId, intf);
                }
            } else {
                logger.error("Create host intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(false);
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
                synchronized (IfMgr.class) {
                    vintfs.put(portId, intf);
                }
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
            for (IpAddress ipAddr : intf.getIpAddresses()) {
                if (ipAddr.getIpv6Address() != null) {
                    v6IntfMap.remove(ipAddr.getIpv6Address());
                }
            }
            synchronized (IfMgr.class) {
                vintfs.remove(portId);
            }
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
        Set<Ipv6Address> addrSet = null;
        logger.debug("obtaining the virtual interface for {}", addr);
        synchronized (IfMgr.class) {
            addrSet = v6IntfMap.keySet();
        }
        for (Ipv6Address intfAddr : addrSet) {
            logger.debug("in getInterfaceForAddress for addr {}", addr);
            if (v6AddressMatch(intfAddr, addr)) {
                return v6IntfMap.get(intfAddr);
            }
        }
        logger.debug("no valid intf available for the address");
        return null;
    }

    private boolean v6AddressMatch(Ipv6Address addr1, Ipv6Address addr2) {
        Ipv6Address fAddr1 = new Ipv6Address(InetAddresses.forString(addr1.getValue()).getHostAddress());
        logger.debug("v6AddressMatch addr1 {}", fAddr1);
        if (fAddr1.equals(new Ipv6Address(InetAddresses.forString(addr2.getValue()).getHostAddress()))) {
            return true;
        }
        return false;
    }
}
