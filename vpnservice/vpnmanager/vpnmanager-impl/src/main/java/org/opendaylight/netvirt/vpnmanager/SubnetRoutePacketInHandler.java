/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.primitives.Ints;

import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.genius.mdsalutil.packet.ARP;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.controller.liblldp.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;

public class SubnetRoutePacketInHandler implements PacketProcessingListener {

    private static final Logger s_logger = LoggerFactory.getLogger(SubnetRoutePacketInHandler.class);
    private final DataBroker broker;
    private PacketProcessingService packetService;
    private VpnInterfaceManager ifManager;
    private IdManagerService idManager;
    //List maintains info about the arp request sent - id src+dst ip
    private ArrayList<String> arpList = new ArrayList();

    public SubnetRoutePacketInHandler(DataBroker dataBroker, IdManagerService idManager) {
        broker = dataBroker;
        this.idManager = idManager;
    }

    public void onPacketReceived(PacketReceived notification) {

        s_logger.trace("SubnetRoutePacketInHandler: PacketReceived invoked...");

        short tableId = notification.getTableId().getValue();
        byte[] data = notification.getPayload();
        BigInteger metadata = notification.getMatch().getMetadata().getMetadata();
        Ethernet res = new Ethernet();

        if (tableId == NwConstants.L3_SUBNET_ROUTE_TABLE) {
            s_logger.trace("SubnetRoutePacketInHandler: Some packet received as {}", notification);
            try {
                res.deserialize(data, 0, data.length * NetUtils.NumBitsInAByte);
            } catch (Exception e) {
                s_logger.warn("SubnetRoutePacketInHandler: Failed to decode Packet ", e);
                return;
            }
            try {
                Packet pkt = res.getPayload();
                if (pkt instanceof IPv4) {
                    IPv4 ipv4 = (IPv4) pkt;
                    byte[] srcMac = res.getSourceMACAddress();
                    byte[] dstMac = res.getDestinationMACAddress();
                    byte[] srcIp = Ints.toByteArray(ipv4.getSourceAddress());
                    byte[] dstIp = Ints.toByteArray(ipv4.getDestinationAddress());
                    String dstIpStr = toStringIpAddress(dstIp);
                    String srcIpStr = toStringIpAddress(srcIp);
                    // FIXME 7: To be fixed with VPNManager patch
                    /*if (VpnUtil.getNeutronPortNamefromPortFixedIp(broker, dstIpStr) != null) {
                        s_logger.debug("SubnetRoutePacketInHandler: IPv4 Packet received with "
                                + "Target IP {} is a valid Neutron port, ignoring subnet route processing", dstIpStr);
                        return;
                    }*/
                    long elanTag = MetaDataUtil.getElanTagFromMetadata(metadata);
                    if (elanTag == 0) {
                        s_logger.error("SubnetRoutePacketInHandler: elanTag value from metadata found to be 0, for IPv4 " +
                                "Packet received with Target IP {}", dstIpStr);
                        return;
                    }
                    s_logger.info("SubnetRoutePacketInHandler: Processing IPv4 Packet received with Source IP {} "
                            + "and Target IP {} and elan Tag {}", srcIpStr, dstIpStr, elanTag);
                    BigInteger dpnId = getTargetDpnForPacketOut(broker, elanTag, ipv4.getDestinationAddress());
                    //Handle subnet routes ip requests
                    if (dpnId != BigInteger.ZERO) {
                        long groupid = VpnUtil.getRemoteBCGroup(elanTag);
                        String key = srcIpStr + dstIpStr;
                        sendArpRequest(dpnId, groupid, srcMac, srcIp, dstIp);
                    }
                    return;
                }
            } catch (Exception ex) {
                //Failed to handle packet
                s_logger.error("SubnetRoutePacketInHandler: Failed to handle subnetroute packets ", ex);
            }
            return;
        }

        if (tableId == NwConstants.L3_INTERFACE_TABLE) {
            s_logger.trace("SubnetRoutePacketInHandler: Packet from Table {} received as {}",
                    NwConstants.L3_INTERFACE_TABLE, notification);
            try {
                res.deserialize(data, 0, data.length * NetUtils.NumBitsInAByte);
            } catch (Exception e) {
                s_logger.warn("SubnetRoutePacketInHandler: Failed to decode Table " + NwConstants.L3_INTERFACE_TABLE + " Packet ", e);
                return;
            }
            try {
                Packet pkt = res.getPayload();
                if (pkt instanceof ARP) {
                    s_logger.debug("SubnetRoutePacketInHandler: ARP packet received");
                    ARP arpPacket = (ARP) pkt;
                    boolean arpReply = (arpPacket.getOpCode() == 2);
                    if (arpReply) {
                        //Handle subnet routes arp responses
                        s_logger.debug("SubnetRoutePacketInHandler: ARP reply received");
                        byte[] respSrc = arpPacket.getSenderProtocolAddress();
                        byte[] respDst = arpPacket.getTargetProtocolAddress();
                        String respIp = toStringIpAddress(respSrc);
                        String check = toStringIpAddress(respDst) + respIp;
                        // FIXME 8: To be fixed with VPNManager patch
                        /*if (VpnUtil.getNeutronPortNamefromPortFixedIp(broker, respIp) != null) {
                            s_logger.debug("SubnetRoutePacketInHandler: ARP reply Packet received with "
                                    + "Source IP {} which is a valid Neutron port, ignoring subnet route processing", respIp);
                            return;
                        }*/
                        String destination = VpnUtil.getIpPrefix(respIp);
                        long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
                        s_logger.info("SubnetRoutePacketInHandler: ARP reply received for target IP {} from LPort {}" + respIp, portTag);
                        IfIndexInterface interfaceInfo = VpnUtil.getInterfaceInfoByInterfaceTag(broker, portTag);
                        String ifName = interfaceInfo.getInterfaceName();
                        InstanceIdentifier<VpnInterface> vpnIfIdentifier = VpnUtil.getVpnInterfaceIdentifier(ifName);
                        VpnInterface vpnInterface = VpnUtil.getConfiguredVpnInterface(broker, ifName);

                        //Get VPN interface adjacencies
                        if (vpnInterface != null) {
                            InstanceIdentifier<Adjacencies> path = vpnIfIdentifier.augmentation(Adjacencies.class);
                            Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path);
                            String nextHopIpAddr = null;
                            String nextHopMacAddress = null;
                            if (adjacencies.isPresent()) {
                                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                                for (Adjacency adjacs : adjacencyList) {
                                    if (adjacs.getMacAddress() != null && !adjacs.getMacAddress().isEmpty()) {
                                        nextHopIpAddr = adjacs.getIpAddress();
                                        nextHopMacAddress = adjacs.getMacAddress();
                                        break;
                                    }
                                }
                                if (nextHopMacAddress != null && destination != null) {
                                    String rd = VpnUtil.getVpnRd(broker, vpnInterface.getVpnInstanceName());
                                    long label =
                                            VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                    VpnUtil.getNextHopLabelKey((rd != null) ? rd : vpnInterface.getVpnInstanceName(), destination));
                                    String nextHopIp = nextHopIpAddr.split("/")[0];
                                    // FIXME 9: To be fixed with VPNManager patch
                                    /*Adjacency newAdj = new AdjacencyBuilder().setIpAddress(destination).setKey
                                            (new AdjacencyKey(destination)).setNextHopIp(nextHopIp).build();
                                    adjacencyList.add(newAdj);*/
                                    Adjacencies aug = VpnUtil.getVpnInterfaceAugmentation(adjacencyList);
                                    VpnInterface newVpnIntf = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(vpnInterface.getName())).
                                            setName(vpnInterface.getName()).setVpnInstanceName(vpnInterface.getVpnInstanceName()).
                                            addAugmentation(Adjacencies.class, aug).build();
                                    VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier, newVpnIntf);
                                    s_logger.debug("SubnetRoutePacketInHandler: Successfully stored subnetroute Adjacency into VpnInterface {}", newVpnIntf);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                //Failed to decode packet
                s_logger.error("SubnetRoutePacketInHandler: Failed to handle subnetroute Table " + NwConstants.L3_INTERFACE_TABLE +
                        " packets ", ex);
            }
        }
    }

    private static BigInteger getTargetDpnForPacketOut(DataBroker broker, long elanTag, int ipAddress) {
        BigInteger dpnid = BigInteger.ZERO;
        ElanTagName elanInfo = VpnUtil.getElanInfoByElanTag(broker, elanTag);
        if (elanInfo == null) {
            s_logger.trace("SubnetRoutePacketInHandler: Unable to retrieve ElanInfo for elanTag {}", elanTag);
            return dpnid;
        }
        InstanceIdentifier<NetworkMap> networkId = InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class, new NetworkMapKey(new Uuid(elanInfo.getName()))).build();

        Optional<NetworkMap> optionalNetworkMap = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, networkId);
        if (optionalNetworkMap.isPresent()) {
            List<Uuid> subnetList = optionalNetworkMap.get().getSubnetIdList();
            s_logger.trace("SubnetRoutePacketInHandler: Obtained subnetList as " + subnetList);
            for (Uuid subnetId : subnetList) {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).
                        child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    continue;
                }
                SubnetOpDataEntry subOpEntry = optionalSubs.get();
                if (subOpEntry.getNhDpnId() != null) {
                    s_logger.trace("SubnetRoutePacketInHandler: Viewing Subnet " + subnetId);
                    boolean match = VpnUtil.isIpInSubnet(ipAddress, subOpEntry.getSubnetCidr());
                    s_logger.trace("SubnetRoutePacketInHandler: Viewing Subnet " + subnetId + " matching " + match);
                    if (match) {
                        dpnid = subOpEntry.getNhDpnId();
                        return dpnid;
                    }
                }
            }
        }
        return dpnid;
    }

    private static String toStringIpAddress(byte[] ipAddress)
    {
        String ip = null;
        if (ipAddress == null) {
            return ip;
        }

        try {
            ip = InetAddress.getByAddress(ipAddress).getHostAddress();
        } catch(UnknownHostException e) {
            s_logger.error("SubnetRoutePacketInHandler: Unable to translate byt[] ipAddress to String {}", e);
        }

        return ip;
    }

    public void setPacketProcessingService(PacketProcessingService service) {
        this.packetService = service;
    }

    private long getDpnIdFromPktRcved(PacketReceived packet) {
        InstanceIdentifier<?> identifier = packet.getIngress().getValue();
        NodeId id = identifier.firstKeyOf(Node.class, NodeKey.class).getId();
        return getDpnIdFromNodeName(id.getValue());
    }

    private long getDpnIdFromNodeName(String nodeName) {
        String dpId = nodeName.substring(nodeName.lastIndexOf(":") + 1);
        return Long.parseLong(dpId);
    }

    private void sendArpRequest(BigInteger dpnId, long groupId, byte[] abySenderMAC, byte[] abySenderIpAddress,
                                byte[] abyTargetIpAddress) {

        s_logger.info("SubnetRoutePacketInHandler: sendArpRequest dpnId {}, groupId {}, senderIPAddress {}, targetIPAddress {}",
                dpnId, groupId,
                toStringIpAddress(abySenderIpAddress),toStringIpAddress(abyTargetIpAddress));
        if (abySenderIpAddress != null) {
            byte[] arpPacket;
            byte[] ethPacket;
            List<ActionInfo> lstActionInfo;
            TransmitPacketInput transmitPacketInput;

            arpPacket = createARPPacket(ARP.REQUEST, abySenderMAC, abySenderIpAddress, VpnConstants.MAC_Broadcast,
                    abyTargetIpAddress);
            ethPacket = createEthernetPacket(abySenderMAC, VpnConstants.EthernetDestination_Broadcast, arpPacket);
            lstActionInfo = new ArrayList<>();
            lstActionInfo.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));
            transmitPacketInput = MDSALUtil.getPacketOutDefault(lstActionInfo, ethPacket, dpnId);
            packetService.transmitPacket(transmitPacketInput);
        } else {
            s_logger.info("SubnetRoutePacketInHandler: Unable to send ARP request because client port has no IP  ");
        }
    }

    private static byte[] createARPPacket(short opCode, byte[] senderMacAddress, byte[] senderIP, byte[] targetMacAddress,
                                          byte[] targetIP) {
        ARP arp = new ARP();
        byte[] rawArpPkt = null;
        try {
            arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
            arp.setProtocolType(EtherTypes.IPv4.shortValue());
            arp.setHardwareAddressLength((byte) 6);
            arp.setProtocolAddressLength((byte) 4);
            arp.setOpCode(opCode);
            arp.setSenderHardwareAddress(senderMacAddress);
            arp.setSenderProtocolAddress(senderIP);
            arp.setTargetHardwareAddress(targetMacAddress);
            arp.setTargetProtocolAddress(targetIP);
            rawArpPkt = arp.serialize();
        } catch (Exception ex) {
            s_logger.error("VPNUtil:  Serialized ARP packet with senderIp {} targetIP {} exception ",
                    senderIP, targetIP, ex);
        }

        return rawArpPkt;
    }

    private static byte[] createEthernetPacket(byte[] sourceMAC, byte[] targetMAC, byte[] arp) {
        Ethernet ethernet = new Ethernet();
        byte[] rawEthPkt = null;
        try {
            ethernet.setSourceMACAddress(sourceMAC);
            ethernet.setDestinationMACAddress(targetMAC);
            ethernet.setEtherType(EtherTypes.ARP.shortValue());
            ethernet.setRawPayload(arp);
            rawEthPkt = ethernet.serialize();
        } catch (Exception ex) {
            s_logger.error("VPNUtil:  Serialized Ethernet packet with sourceMacAddress {} targetMacAddress {} exception ",
                    sourceMAC, targetMAC, ex);
        }
        return rawEthPkt;
    }
}
