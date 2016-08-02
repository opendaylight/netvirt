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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants.Ipv6RtrAdvertType;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6TimerWheel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfMgr {
    static final Logger LOG = LoggerFactory.getLogger(IfMgr.class);

    private HashMap<Uuid, VirtualRouter> vrouters;
    private HashMap<Uuid, VirtualSubnet> vsubnets;
    private HashMap<Uuid, VirtualPort> vintfs;
    private HashMap<Uuid, VirtualPort> vrouterv6IntfMap;
    private HashMap<Uuid, List<VirtualPort>> unprocessedRouterIntfs;
    private HashMap<Uuid, List<VirtualPort>> unprocessedSubnetIntfs;
    private HashMap<String, ImmutablePair<String, VirtualPort>> ifaceFlowCache;
    private OdlInterfaceRpcService interfaceManagerRpc;
    private static final IfMgr IFMGR_INSTANCE = new IfMgr();
    private Ipv6ServiceUtils ipv6Utils = Ipv6ServiceUtils.getInstance();
    private DataBroker broker;

    private IfMgr() {
        init();
    }

    void init() {
        this.vrouters = new HashMap<>();
        this.vsubnets = new HashMap<>();
        this.vintfs = new HashMap<>();
        this.vrouterv6IntfMap = new HashMap<>();
        this.unprocessedRouterIntfs = new HashMap<>();
        this.unprocessedSubnetIntfs = new HashMap<>();
        this.ifaceFlowCache = new HashMap<>();
        LOG.info("IfMgr is enabled");
    }

    public static IfMgr getIfMgrInstance() {
        return IFMGR_INSTANCE;
    }

    public void setInterfaceManagerRpc(OdlInterfaceRpcService interfaceManagerRpc) {
        LOG.trace("Registered interfaceManager successfully");
        this.interfaceManagerRpc = interfaceManagerRpc;
    }

    public void setDataBroker(final DataBroker db) {
        LOG.trace("Updated Databroker in ifMgr instance.");
        this.broker = db;
    }

    /**
     * Add router.
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
                LOG.info("intfList is null for {}", rtrUuid);
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
            LOG.error("Create router failed for :{}", rtrUuid);
        }

        return;
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
            rtr = null;
        } else {
            LOG.error("Delete router failed for :{}", rtrUuid);
        }
        return;
    }

    /**
     * Add Subnet.
     *
     * @param snetId subnet id
     * @param name subnet name
     * @param networkId network id
     * @param tenantId tenant id
     * @param gatewayIp gateway ip address
     * @param ipVersion IP Version "IPv4 or IPv6"
     * @param subnetCidr subnet CIDR
     * @param ipV6AddressMode Address Mode of IPv6 Subnet
     * @param ipV6RaMode RA Mode of IPv6 Subnet.
     */
    public void addSubnet(Uuid snetId, String name, Uuid networkId, Uuid tenantId,
                          IpAddress gatewayIp, String ipVersion, IpPrefix subnetCidr,
                          String ipV6AddressMode, String ipV6RaMode) {

        // Save the gateway ipv6 address in its fully expanded format. We always store the v6Addresses
        // in expanded form and are used during Neighbor Discovery Support.
        if (gatewayIp.getIpv6Address() != null) {
            Ipv6Address addr = new Ipv6Address(InetAddresses
                    .forString(gatewayIp.getIpv6Address().getValue()).getHostAddress());
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

            vsubnets.put(snetId, snet);

            List<VirtualPort> intfList = unprocessedSubnetIntfs.get(snetId);
            if (intfList == null) {
                LOG.info("interfaces are not available for the subnet {}", snetId);
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
            LOG.error("Create subnet failed for :{}", snetId);
        }
        return;
    }

    /**
     * Remove Subnet.
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
        }
        return;
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
        if (intf == null) {
            intf = new VirtualPort();
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                LOG.error("Create rtr intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(true)
                    .setDeviceOwner(deviceOwner);
            intf.setPeriodicTimer();
            LOG.debug("start the periodic RA Timer for routerIntf {}, int {}s", portId,
                       Ipv6Constants.PERIODIC_RA_INTERVAL);
            transmitUnsolicitedRA(intf);
            MacAddress ifaceMac = MacAddress.getDefaultInstance(macAddress);
            Ipv6Address llAddr = ipv6Utils.getIpv6LinkLocalAddressFromMac(ifaceMac);
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

        vrouterv6IntfMap.put(networkId, intf);
        programNSFlowForAddress(intf, fixedIp.getIpv6Address(), true);
        return;
    }

    public void updateRouterIntf(Uuid portId, Uuid rtrId, List<FixedIps> fixedIpsList) {
        LOG.debug("updateRouterIntf portId {}, fixedIpsList {} ", portId, fixedIpsList);
        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            LOG.error("Update Router interface failed. Could not get router interface details {}", portId);
            return;
        }

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
        }
        return;
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
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                LOG.error("Create host intf failed for :{}", portId);
                return;
            }
            intf.setIntfUUID(portId)
                    .setSubnetInfo(snetId, fixedIp)
                    .setNetworkID(networkId)
                    .setMacAddress(macAddress)
                    .setRouterIntfFlag(false)
                    .setDeviceOwner(deviceOwner);
        } else {
            intf.setSubnetInfo(snetId, fixedIp);
        }

        VirtualSubnet snet = vsubnets.get(snetId);

        if (snet != null) {
            intf.setSubnet(snetId, snet);
        } else {
            addUnprocessed(unprocessedSubnetIntfs, snetId, intf);
        }
        return;
    }

    public void updateHostIntf(Uuid portId, List<FixedIps> fixedIpsList) {
        LOG.debug("updateHostIntf portId {}, fixedIpsList {} ", portId, fixedIpsList);

        VirtualPort intf = vintfs.get(portId);
        if (intf == null) {
            LOG.error("Update Host interface failed. Could not get Host interface details {}", portId);
            return;
        }

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

            VirtualSubnet snet = vsubnets.get(fip.getSubnetId());

            if (snet != null) {
                intf.setSubnet(fip.getSubnetId(), snet);
            } else {
                addUnprocessed(unprocessedSubnetIntfs, fip.getSubnetId(), intf);
            }
        }
        return;
    }

    public void updateInterface(Uuid portId, String dpId, Long ofPort) {
        LOG.debug("in updateInterface portId {}, dpId {}, ofPort {}",
            portId, dpId, ofPort);
        VirtualPort intf = vintfs.get(portId);

        if (intf == null) {
            intf = new VirtualPort();
            intf.setIntfUUID(portId);
            if (intf != null) {
                vintfs.put(portId, intf);
            } else {
                LOG.error("updateInterface failed for :{}", portId);
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
            if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6Constants.NETWORK_ROUTER_INTERFACE)) {
                MacAddress ifaceMac = MacAddress.getDefaultInstance(intf.getMacAddress());
                Ipv6Address llAddr = ipv6Utils.getIpv6LinkLocalAddressFromMac(ifaceMac);
                vrouterv6IntfMap.remove(intf.getNetworkID(), intf);
                programNSFlowForAddress(intf, llAddr, false);
                transmitRouterAdvertisement(intf, Ipv6RtrAdvertType.CEASE_ADVERTISEMENT);
                Ipv6TimerWheel timer = Ipv6TimerWheel.getInstance();
                timer.cancelPeriodicTransmissionTimeout(intf.getPeriodicTimeout());
                intf.resetPeriodicTimeout();
                LOG.debug("Reset the periodic RA Timer for intf {}", intf.getIntfUUID());
            }
            for (IpAddress ipAddr : intf.getIpAddresses()) {
                if (ipAddr.getIpv6Address() != null) {
                    if (intf.getDeviceOwner().equalsIgnoreCase(Ipv6Constants.NETWORK_ROUTER_INTERFACE)) {
                        programNSFlowForAddress(intf, ipAddr.getIpv6Address(), false);
                    }
                }
            }
            vintfs.remove(portId);
            intf = null;
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

    public VirtualPort getRouterV6InterfaceForNetwork(Uuid networkId) {
        LOG.debug("obtaining the virtual interface for {}", networkId);
        return (vrouterv6IntfMap.get(networkId));
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

    private void programNSFlowForAddress(VirtualPort intf, Ipv6Address address, boolean action) {
        //Todo: Will be done in a separate patch.
    }

    public String getInterfaceNameFromTag(long portTag) {
        String interfaceName = null;
        GetInterfaceFromIfIndexInput input = new GetInterfaceFromIfIndexInputBuilder()
                .setIfIndex(new Integer((int)portTag)).build();
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

    public void updateInterfaceCache(String interfaceName, ImmutablePair<String, VirtualPort> pair) {
        ifaceFlowCache.put(interfaceName, pair);
    }

    public ImmutablePair<String, VirtualPort> getInterfaceCache(String interfaceName) {
        return ifaceFlowCache.get(interfaceName);
    }

    public void removeInterfaceCache(String interfaceName) {
        ifaceFlowCache.remove(interfaceName);
    }

    private void transmitRouterAdvertisement(VirtualPort intf, Ipv6RtrAdvertType advType) {
        Ipv6RouterAdvt ipv6RouterAdvert = new Ipv6RouterAdvt();
        List<DpnInterfaces> dpnsIfaceList;
        VirtualPort port;

        LOG.debug("in transmitRouterAdvertisement for {}", advType);
        // Get the list of <dpns, iface-list> for the network. For each iface on the dpnId, send out an RA.
        dpnsIfaceList = Ipv6ServiceUtils.getDpnInterfaceListForElan(broker, intf.getNetworkID().getValue());
        for (DpnInterfaces dpnInterfaces : dpnsIfaceList) {
            String nodeName = Ipv6Constants.OPENFLOW_NODE_PREFIX + dpnInterfaces.getDpId().toString();
            for (String portId: dpnInterfaces.getInterfaces()) {
                port = vintfs.get(new Uuid(portId));
                if (null != port) {
                    String outPort = nodeName + ":" + port.getOfPort();
                    LOG.debug("Transmitting RA {} for node {}, port {}", advType, nodeName, outPort);
                    InstanceIdentifier<NodeConnector> outPortId = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(nodeName)))
                            .child(NodeConnector.class,
                                    new NodeConnectorKey(new NodeConnectorId(outPort)))
                            .build();
                    ipv6RouterAdvert.transmitRtrAdvertisement(advType, intf, new NodeConnectorRef(outPortId), null);
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
}
