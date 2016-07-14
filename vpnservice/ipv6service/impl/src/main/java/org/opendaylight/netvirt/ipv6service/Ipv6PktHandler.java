/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.liblldp.BitBufferHelper;
import org.opendaylight.controller.liblldp.BufferException;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.EthernetHeader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.Ipv6Header;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterAdvertisementPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterAdvertisementPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.router.advertisement.packet.PrefixList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.router.advertisement.packet.PrefixListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6PktHandler implements AutoCloseable, PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6PktHandler.class);
    private long pktProccessedCounter = 0;
    private PacketProcessingService pktService;
    private IfMgr ifMgr;
    private Ipv6ServiceUtils ipv6Utils;
    private final ExecutorService packetProcessor = Executors.newCachedThreadPool();

    public Ipv6PktHandler() {
        this.ipv6Utils = Ipv6ServiceUtils.getInstance();
    }

    public void setPacketProcessingService(PacketProcessingService packetService) {
        this.pktService = packetService;
    }

    public void setIfMgrInstance(IfMgr instance) {
        this.ifMgr = instance;
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {
        int     ethType;
        int     v6NxtHdr;

        if (packetReceived == null) {
            LOG.debug("Received null packet. Returning without any processing");
            return;
        }

        byte[] data = packetReceived.getPayload();
        if (data.length <= 0) {
            LOG.debug("Received packet with invalid length {}", data.length);
            return;
        }
        try {
            ethType = BitBufferHelper.getInt(BitBufferHelper.getBits(data, Ipv6Constants.ETHTYPE_START,
                    Ipv6Constants.TWO_BYTES));
            if (ethType == Ipv6Constants.IPv6_ETHTYPE) {
                v6NxtHdr = BitBufferHelper.getByte(BitBufferHelper.getBits(data,
                        (Ipv6Constants.IPv6_HDR_START + Ipv6Constants.IPv6_NEXT_HDR), Ipv6Constants.ONE_BYTE));
                if (v6NxtHdr == Ipv6Constants.ICMPv6_TYPE) {
                    int icmpv6Type = BitBufferHelper.getInt(BitBufferHelper.getBits(data,
                            Ipv6Constants.ICMPV6_HDR_START, Ipv6Constants.ONE_BYTE));
                    if ((icmpv6Type == Ipv6Constants.ICMPv6_RS_CODE) || (icmpv6Type == Ipv6Constants.ICMPv6_NS_CODE)) {
                        packetProcessor.submit(new PacketHandler(icmpv6Type, packetReceived));
                    }
                } else {
                    LOG.debug("IPv6 Pdu received on port {} with next-header {} ",
                            packetReceived.getIngress(), v6NxtHdr);
                }
            } else {
                return;
            }
        } catch (BufferException e) {
            LOG.warn("Failed to decode packet: {}", e.getMessage());
            return;
        }
    }

    public long getPacketProcessedCounter() {
        return pktProccessedCounter;
    }

    private class PacketHandler implements Runnable {
        int type;
        PacketReceived packet;

        PacketHandler(int icmpv6Type, PacketReceived packet) {
            this.type = icmpv6Type;
            this.packet = packet;
        }

        @Override
        public void run() {
            if (type == Ipv6Constants.ICMPv6_NS_CODE) {
                LOG.info("Received Neighbor Solicitation request");
                processNeighborSolicitationRequest();
            } else if (type == Ipv6Constants.ICMPv6_RS_CODE) {
                LOG.info("Received Router Solicitation request");
                processRouterSolicitationRequest();
            }
        }

        private void processNeighborSolicitationRequest() {
            byte[] data = packet.getPayload();
            NeighborSolicitationPacket nsPdu = deserializeNSPacket(data);
            Ipv6Header ipv6Header = (Ipv6Header) nsPdu;
            if (ipv6Utils.validateChecksum(data, ipv6Header, nsPdu.getIcmp6Chksum()) == false) {
                pktProccessedCounter++;
                LOG.warn("Received Neighbor Solicitation with invalid checksum on {}. Ignoring the packet.",
                        packet.getIngress());
                return;
            }

            BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
            long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
            String interfaceName = ifMgr.getInterfaceNameFromTag(portTag);
            VirtualPort port = ifMgr.obtainV6Interface(new Uuid(interfaceName));
            if (port == null) {
                pktProccessedCounter++;
                LOG.warn("Port {} not found, skipping.", port);
                return;
            }

            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
            if (routerPort == null) {
                pktProccessedCounter++;
                LOG.warn("Port {} is not associated to a Router, skipping NS request.", routerPort);
                return;
            }

            if (!routerPort.getIpv6Addresses().contains(nsPdu.getTargetIpAddress())) {
                pktProccessedCounter++;
                LOG.warn("No Router interface with address {} on the network {}, skipping NS request.",
                        nsPdu.getTargetIpAddress(), port.getNetworkID());
                return;
            }

            //formulate the NA response
            NeighborAdvertisePacketBuilder naPacket = new NeighborAdvertisePacketBuilder();
            updateNAResponse(nsPdu, routerPort, naPacket);
            // serialize the response packet
            byte[] txPayload = fillNeighborAdvertisementPacket(naPacket.build());
            InstanceIdentifier<Node> outNode = packet.getIngress().getValue().firstIdentifierOf(Node.class);
            TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                    .setNode(new NodeRef(outNode))
                    .setEgress(packet.getIngress()).build();
            // Tx the packet out of the controller.
            if (pktService != null) {
                LOG.debug("Transmitting the Neighbor Advt packet out on {}", packet.getIngress());
                pktService.transmitPacket(input);
                pktProccessedCounter++;
            }
        }

        private NeighborSolicitationPacket deserializeNSPacket(byte[] data) {
            NeighborSolicitationPacketBuilder nsPdu = new NeighborSolicitationPacketBuilder();
            int bitOffset = 0;

            try {
                nsPdu.setDestinationMac(new MacAddress(
                        ipv6Utils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setSourceMac(new MacAddress(
                        ipv6Utils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = Ipv6Constants.IPv6_HDR_START;
                nsPdu.setVersion(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 4)));
                bitOffset = bitOffset + 4;
                nsPdu.setFlowLabel(BitBufferHelper.getLong(BitBufferHelper.getBits(data, bitOffset, 28)));
                bitOffset = bitOffset + 28;
                nsPdu.setIpv6Length(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));
                bitOffset = bitOffset + 16;
                nsPdu.setNextHeader(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                nsPdu.setHopLimit(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                nsPdu.setSourceIpv6(Ipv6Address.getDefaultInstance(
                        InetAddress.getByAddress(BitBufferHelper.getBits(data, bitOffset, 128)).getHostAddress()));
                bitOffset = bitOffset + 128;
                nsPdu.setDestinationIpv6(Ipv6Address.getDefaultInstance(
                        InetAddress.getByAddress(BitBufferHelper.getBits(data, bitOffset, 128)).getHostAddress()));
                bitOffset = bitOffset + 128;

                nsPdu.setIcmp6Type(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                nsPdu.setIcmp6Code(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                nsPdu.setIcmp6Chksum(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));
                bitOffset = bitOffset + 16;
                nsPdu.setReserved(Long.valueOf(0));
                bitOffset = bitOffset + 32;
                nsPdu.setTargetIpAddress(Ipv6Address.getDefaultInstance(
                        InetAddress.getByAddress(BitBufferHelper.getBits(data, bitOffset, 128)).getHostAddress()));
            } catch (BufferException | UnknownHostException  e) {
                LOG.warn("Exception obtained when deserializing NS packet", e.toString());
            }
            return nsPdu.build();
        }

        private void updateNAResponse(NeighborSolicitationPacket pdu,
                                      VirtualPort port, NeighborAdvertisePacketBuilder naPacket) {
            long flag = 0;
            if (!pdu.getSourceIpv6().equals(ipv6Utils.UNSPECIFIED_ADDR)) {
                naPacket.setDestinationIpv6(pdu.getSourceIpv6());
                flag = 0xE0; // Set Router, Solicited and Override Flag.
            } else {
                naPacket.setDestinationIpv6(ipv6Utils.ALL_NODES_MCAST_ADDR);
                flag = 0xA0; // Set Router and Override Flag.
            }
            naPacket.setDestinationMac(pdu.getSourceMac());
            naPacket.setEthertype(pdu.getEthertype());
            naPacket.setSourceIpv6(pdu.getTargetIpAddress());
            naPacket.setSourceMac(new MacAddress(port.getMacAddress()));
            naPacket.setHopLimit(Ipv6Constants.ICMPv6_MAX_HOP_LIMIT);
            naPacket.setIcmp6Type(Ipv6Constants.ICMPv6_NA_CODE);
            naPacket.setIcmp6Code(pdu.getIcmp6Code());
            flag = flag << 24;
            naPacket.setFlags(flag);
            naPacket.setFlowLabel(pdu.getFlowLabel());
            naPacket.setIpv6Length(32);
            naPacket.setNextHeader(pdu.getNextHeader());
            naPacket.setOptionType((short)2);
            naPacket.setTargetAddrLength((short)1);
            naPacket.setTargetAddress(pdu.getTargetIpAddress());
            naPacket.setTargetLlAddress(new MacAddress(port.getMacAddress()));
            naPacket.setVersion(pdu.getVersion());
            naPacket.setIcmp6Chksum(0);
            return;
        }

        private byte[] icmp6NAPayloadtoByte(NeighborAdvertisePacket pdu) {
            byte[] data = new byte[36];
            Arrays.fill(data, (byte)0);

            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.put((byte)pdu.getIcmp6Type().shortValue());
            buf.put((byte)pdu.getIcmp6Code().shortValue());
            buf.putShort((short)pdu.getIcmp6Chksum().intValue());
            buf.putInt((int)pdu.getFlags().longValue());
            try {
                byte[] address = null;
                address = InetAddress.getByName(pdu.getTargetAddress().getValue()).getAddress();
                buf.put(address);
            } catch (UnknownHostException e) {
                LOG.error("Serializing NA target address failed", e);
            }
            buf.put((byte)pdu.getOptionType().shortValue());
            buf.put((byte)pdu.getTargetAddrLength().shortValue());
            buf.put(ipv6Utils.bytesFromHexString(pdu.getTargetLlAddress().getValue().toString()));
            return data;
        }

        private byte[] fillNeighborAdvertisementPacket(NeighborAdvertisePacket pdu) {
            ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

            buf.put(ipv6Utils.convertEthernetHeaderToByte((EthernetHeader)pdu), 0, 14);
            buf.put(ipv6Utils.convertIpv6HeaderToByte((Ipv6Header)pdu), 0, 40);
            buf.put(icmp6NAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
            int checksum = ipv6Utils.calcIcmpv6Checksum(buf.array(), (Ipv6Header) pdu);
            buf.putShort((Ipv6Constants.ICMPV6_OFFSET + 2), (short)checksum);
            return (buf.array());
        }

        private void processRouterSolicitationRequest() {
            byte[] data = packet.getPayload();
            List<String> prefixList;
            RouterSolicitationPacket rsPdu = deserializeRSPacket(data);
            Ipv6Header ipv6Header = (Ipv6Header) rsPdu;
            if (ipv6Utils.validateChecksum(data, ipv6Header, rsPdu.getIcmp6Chksum()) == false) {
                pktProccessedCounter++;
                LOG.warn("Received RS packet with invalid checksum on {}. Ignoring the packet.",
                        packet.getIngress());
                return;
            }

            BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
            long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
            String interfaceName = ifMgr.getInterfaceNameFromTag(portTag);
            VirtualPort port = ifMgr.obtainV6Interface(new Uuid(interfaceName));
            if (port == null) {
                pktProccessedCounter++;
                LOG.warn("Port {} not found, skipping.", port);
                return;
            }

            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
            if (routerPort == null) {
                pktProccessedCounter++;
                LOG.warn("Port {} is not associated to a Router, skipping.", routerPort);
                return;
            }

            RouterAdvertisementPacketBuilder raPacket = new RouterAdvertisementPacketBuilder();
            updateRAResponse(rsPdu, raPacket, rsPdu.getSourceMac(), routerPort);
            // Serialize the response packet
            byte[] txPayload = fillRouterAdvertisementPacket(raPacket.build());
            InstanceIdentifier<Node> outNode = packet.getIngress().getValue().firstIdentifierOf(Node.class);
            TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                    .setNode(new NodeRef(outNode))
                    .setEgress(packet.getIngress()).build();
            if (pktService != null) {
                LOG.debug("Transmitting the Router Advt packet out {}", packet.getIngress());
                pktService.transmitPacket(input);
                pktProccessedCounter++;
            }
        }

        private RouterSolicitationPacket deserializeRSPacket(byte[] data) {
            RouterSolicitationPacketBuilder rsPdu = new RouterSolicitationPacketBuilder();
            int bitOffset = 0;

            try {
                rsPdu.setDestinationMac(new MacAddress(
                        ipv6Utils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setSourceMac(new MacAddress(
                        ipv6Utils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = Ipv6Constants.IPv6_HDR_START;
                rsPdu.setVersion(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 4)));
                bitOffset = bitOffset + 4;
                rsPdu.setFlowLabel(BitBufferHelper.getLong(BitBufferHelper.getBits(data, bitOffset, 28)));
                bitOffset = bitOffset + 28;

                rsPdu.setIpv6Length(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));
                bitOffset = bitOffset + 16;
                rsPdu.setNextHeader(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                rsPdu.setHopLimit(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                rsPdu.setSourceIpv6(Ipv6Address.getDefaultInstance(
                        InetAddress.getByAddress(BitBufferHelper.getBits(data, bitOffset, 128)).getHostAddress()));
                bitOffset = bitOffset + 128;
                rsPdu.setDestinationIpv6(Ipv6Address.getDefaultInstance(
                        InetAddress.getByAddress(BitBufferHelper.getBits(data, bitOffset, 128)).getHostAddress()));
                bitOffset = bitOffset + 128;

                rsPdu.setIcmp6Type(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                rsPdu.setIcmp6Code(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                rsPdu.setIcmp6Chksum(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));
                bitOffset = bitOffset + 16;
                rsPdu.setReserved(Long.valueOf(0));
                bitOffset = bitOffset + 32;

                if (rsPdu.getIpv6Length() > Ipv6Constants.ICMPV6_RA_LENGTH_WO_OPTIONS) {
                    rsPdu.setOptionType(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                    bitOffset = bitOffset + 8;
                    rsPdu.setSourceAddrLength(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                    bitOffset = bitOffset + 8;
                    if (rsPdu.getOptionType() == 1) {
                        rsPdu.setSourceLlAddress(new MacAddress(
                                ipv6Utils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                    }
                }
            } catch (BufferException | UnknownHostException e) {
                LOG.warn("Exception obtained when deserializing Router Solicitation packet", e.toString());
            }
            return rsPdu.build();
        }

        private void updateRAResponse(RouterSolicitationPacket pdu,
                                      RouterAdvertisementPacketBuilder raPacket,
                                      MacAddress vmMac, VirtualPort routerPort) {
            short icmpv6RaFlags = 0;
            String gatewayMac = null;
            IpAddress gatewayIp;
            List<String> autoConfigPrefixList = new ArrayList<String>();
            List<String> statefulConfigPrefixList = new ArrayList<String>();

            for (VirtualSubnet subnet : routerPort.getSubnets()) {
                gatewayIp = subnet.getGatewayIp();
                // Skip if its a v4 subnet.
                if (gatewayIp.getIpv4Address() != null) {
                    continue;
                }

                if (!subnet.getIpv6RAMode().isEmpty()) {
                    if (Ipv6Constants.IPV6_AUTO_ADDRESS_SUBNETS.contains(subnet.getIpv6RAMode())) {
                        autoConfigPrefixList.add(String.valueOf(subnet.getSubnetCidr().getValue()));
                    }

                    if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6Constants.IPV6_DHCPV6_STATEFUL)) {
                        statefulConfigPrefixList.add(String.valueOf(subnet.getSubnetCidr().getValue()));
                    }
                }

                if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6Constants.IPV6_DHCPV6_STATELESS)) {
                    icmpv6RaFlags = (short) (icmpv6RaFlags | (1 << 6)); // Other Configuration.
                } else if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6Constants.IPV6_DHCPV6_STATEFUL)) {
                    icmpv6RaFlags = (short) (icmpv6RaFlags | (1 << 7)); // Managed Address Conf.
                }
            }

            gatewayMac = routerPort.getMacAddress();

            MacAddress sourceMac = MacAddress.getDefaultInstance(gatewayMac);
            raPacket.setSourceMac(sourceMac);
            raPacket.setDestinationMac(vmMac);
            raPacket.setEthertype(pdu.getEthertype());

            raPacket.setVersion(pdu.getVersion());
            raPacket.setFlowLabel(pdu.getFlowLabel());
            int prefixListLength = autoConfigPrefixList.size() + statefulConfigPrefixList.size();
            raPacket.setIpv6Length(Ipv6Constants.ICMPV6_RA_LENGTH_WO_OPTIONS
                    + Ipv6Constants.ICMPV6_OPTION_SOURCE_LLA_LENGTH
                    + prefixListLength * Ipv6Constants.ICMPV6_OPTION_PREFIX_LENGTH);
            raPacket.setNextHeader(pdu.getNextHeader());
            raPacket.setHopLimit(Ipv6Constants.ICMPv6_MAX_HOP_LIMIT);
            raPacket.setSourceIpv6(ipv6Utils.getIpv6LinkLocalAddressFromMac(sourceMac));
            raPacket.setDestinationIpv6(pdu.getSourceIpv6());

            raPacket.setIcmp6Type(Ipv6Constants.ICMPv6_RA_CODE);
            raPacket.setIcmp6Code((short)0);
            raPacket.setIcmp6Chksum(0);

            raPacket.setCurHopLimit((short) Ipv6Constants.IPV6_DEFAULT_HOP_LIMIT);
            raPacket.setFlags((short) icmpv6RaFlags);
            raPacket.setRouterLifetime(Ipv6Constants.IPV6_ROUTER_LIFETIME);
            raPacket.setReachableTime((long) 0);
            raPacket.setRetransTime((long) 0);

            raPacket.setOptionSourceAddr((short)1);
            raPacket.setSourceAddrLength((short)1);
            raPacket.setSourceLlAddress(MacAddress.getDefaultInstance(gatewayMac));

            List<PrefixList> prefixList = new ArrayList<PrefixList>();
            PrefixListBuilder prefix = new PrefixListBuilder();
            prefix.setOptionType((short)3);
            prefix.setOptionLength((short)4);
            // Note: EUI-64 auto-configuration requires 64 bits.
            prefix.setPrefixLength((short)64);
            prefix.setValidLifetime((long) Ipv6Constants.IPV6_RA_VALID_LIFETIME);
            prefix.setPreferredLifetime((long) Ipv6Constants.IPV6_RA_PREFERRED_LIFETIME);
            prefix.setReserved((long) 0);

            short autoConfPrefixFlags = 0;
            autoConfPrefixFlags = (short) (autoConfPrefixFlags | (1 << 7)); // On-link flag
            autoConfPrefixFlags = (short) (autoConfPrefixFlags | (1 << 6)); // Autonomous address-configuration flag.
            for (String v6Prefix : autoConfigPrefixList) {
                prefix.setFlags((short)autoConfPrefixFlags);
                prefix.setPrefix(new Ipv6Prefix(v6Prefix));
                prefixList.add(prefix.build());
            }

            short statefulPrefixFlags = 0;
            statefulPrefixFlags = (short) (statefulPrefixFlags | (1 << 7)); // On-link flag
            for (String v6Prefix : statefulConfigPrefixList) {
                prefix.setFlags((short)statefulPrefixFlags);
                prefix.setPrefix(new Ipv6Prefix(v6Prefix));
                prefixList.add(prefix.build());
            }

            raPacket.setPrefixList((List<PrefixList>) prefixList);

            return;
        }

        private byte[] fillRouterAdvertisementPacket(RouterAdvertisementPacket pdu) {
            ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

            buf.put(ipv6Utils.convertEthernetHeaderToByte((EthernetHeader)pdu), 0, 14);
            buf.put(ipv6Utils.convertIpv6HeaderToByte((Ipv6Header)pdu), 0, 40);
            buf.put(icmp6RAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
            int checksum = ipv6Utils.calcIcmpv6Checksum(buf.array(), (Ipv6Header) pdu);
            buf.putShort((Ipv6Constants.ICMPV6_OFFSET + 2), (short)checksum);
            return (buf.array());
        }

        private byte[] icmp6RAPayloadtoByte(RouterAdvertisementPacket pdu) {
            byte[] data = new byte[pdu.getIpv6Length()];
            Arrays.fill(data, (byte)0);

            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.put((byte)pdu.getIcmp6Type().shortValue());
            buf.put((byte)pdu.getIcmp6Code().shortValue());
            buf.putShort((short)pdu.getIcmp6Chksum().intValue());
            buf.put((byte)pdu.getCurHopLimit().shortValue());
            buf.put((byte)pdu.getFlags().shortValue());
            buf.putShort((short)pdu.getRouterLifetime().intValue());
            buf.putInt((int)pdu.getReachableTime().longValue());
            buf.putInt((int)pdu.getRetransTime().longValue());
            buf.put((byte)pdu.getOptionSourceAddr().shortValue());
            buf.put((byte)pdu.getSourceAddrLength().shortValue());
            buf.put(ipv6Utils.bytesFromHexString(pdu.getSourceLlAddress().getValue().toString()));

            for (PrefixList prefix : pdu.getPrefixList()) {
                buf.put((byte)prefix.getOptionType().shortValue());
                buf.put((byte)prefix.getOptionLength().shortValue());
                buf.put((byte)prefix.getPrefixLength().shortValue());
                buf.put((byte)prefix.getFlags().shortValue());
                buf.putInt((int)prefix.getValidLifetime().longValue());
                buf.putInt((int)prefix.getPreferredLifetime().longValue());
                buf.putInt((int)prefix.getReserved().longValue());
                buf.put(IetfInetUtil.INSTANCE.ipv6PrefixToBytes(new Ipv6Prefix(prefix.getPrefix())),0,16);
            }
            return data;
        }

    }

    @Override
    public void close() throws Exception {
        packetProcessor.shutdown();
    }
}
