/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.routemgr.utils.BitBufferHelper;
import org.opendaylight.netvirt.routemgr.utils.BufferException;
import org.opendaylight.netvirt.routemgr.utils.RoutemgrUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.router.advertisement.packet.PrefixList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.router.advertisement.packet.PrefixListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class PktHandler implements PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(PktHandler.class);
    public static final int IPv6_ETHTYPE = 34525;
    public static final int ICMP_v6 = 1;

    public static final int ETHTYPE_START = 96;
    public static final int ONE_BYTE  = 8;
    public static final int TWO_BYTES = 16;
    public static final int IPv6_HDR_START = 112;
    public static final int IPv6_NEXT_HDR = 48;
    public static final int ICMPV6_HDR_START = 432;
    public static final int ICMPV6_OFFSET = 54;

    public static final int IPV6_DEFAULT_HOP_LIMIT = 64;
    public static final int IPV6_ROUTER_LIFETIME = 4500;
    public static final int IPV6_RA_VALID_LIFETIME = 2592000;
    public static final int IPV6_RA_PREFERRED_LIFETIME = 604800;

    private final ExecutorService packetProcessor = Executors.newCachedThreadPool();

    private DataBroker dataService;
    private PacketProcessingService pktProcessService;
    private IfMgr ifMgr;
    private long pktProccessedCounter;

    public void setDataBrokerService(DataBroker dataService) {
        this.dataService = dataService;
    }

    public void setPacketProcessingService(PacketProcessingService packetProcessingService) {
        this.pktProcessService = packetProcessingService;
    }

    public void setIfMgrInstance(IfMgr instance) {
        this.ifMgr = instance;
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {
        boolean result = false;
        int     ethType;
        int     v6NxtHdr;

        if (packetReceived == null) {
            LOG.debug("receiving null packet. returning without any processing");
            return;
        }
        byte[] data = packetReceived.getPayload();
        if (data.length <= 0) {
            LOG.debug("received packet with invalid length {}", data.length);
            return;
        }
        try {
            ethType = BitBufferHelper.getInt(BitBufferHelper.getBits(data, ETHTYPE_START, TWO_BYTES));
            if (ethType == IPv6_ETHTYPE) {
                v6NxtHdr = BitBufferHelper.getByte(BitBufferHelper.getBits(data,
                            (IPv6_HDR_START + IPv6_NEXT_HDR), ONE_BYTE));
                if (v6NxtHdr == RoutemgrUtil.ICMPv6_TYPE) {
                    int icmpv6Type = BitBufferHelper.getInt(BitBufferHelper.getBits(data,
                                        ICMPV6_HDR_START, ONE_BYTE));
                    if ((icmpv6Type == RoutemgrUtil.ICMPv6_RS_CODE) || (icmpv6Type == RoutemgrUtil.ICMPv6_NS_CODE)) {
                        packetProcessor.submit(new PacketHandler(icmpv6Type, packetReceived));
                    }
                } else {
                    LOG.debug("IPv6 Pdu received on port {} with next-header {} ",
                        packetReceived.getIngress(), v6NxtHdr);
                }
            } else {
                return;
            }
        } catch (Exception e) {
            LOG.warn("Failed to decode packet: {}", e.getMessage());
            return;
        }
    }

    public void close() {
        packetProcessor.shutdown();
    }

    public long getPacketProcessedCounter() {
        return pktProccessedCounter;
    }

    private class PacketHandler implements Runnable {
        int type;
        PacketReceived packet;

        public PacketHandler(int icmpv6Type, PacketReceived packet) {
            this.type = icmpv6Type;
            this.packet = packet;
        }

        @Override
        public void run() {
            if (type == RoutemgrUtil.ICMPv6_NS_CODE) {
                byte[] data = packet.getPayload();
                //deserialize the packet
                NeighborSolicitationPacket nsPdu = deserializeNSPacket(data);
                LOG.debug ("deserialized the received NS packet {}", nsPdu);
                //validate the checksum
                Ipv6Header ipv6Header = (Ipv6Header)nsPdu;
                RoutemgrUtil instance = RoutemgrUtil.getInstance();
                if (instance.validateChecksum(data, ipv6Header, nsPdu.getIcmp6Chksum()) == false) {
                    pktProccessedCounter++;
                    LOG.warn("Received NS packet with invalid checksum  on {}. Ignoring the packet",
                        packet.getIngress());
                    return;
                }

                // obtain the interface
                LOG.debug("valid checksum obtaining ifMgr and virtual port");
                VirtualPort port = ifMgr.getInterfaceForAddress(nsPdu.getTargetIpAddress());
                if (port == null) {
                    pktProccessedCounter++;
                    LOG.warn("No learnt interface is available for the given target IP {}",
                        nsPdu.getTargetIpAddress());
                    return;
                }
                //formulate the NA response
                NeighborAdvertisePacketBuilder naPacket = new NeighborAdvertisePacketBuilder();
                updateNAResponse(nsPdu, port, naPacket);
                LOG.debug("NA msg {}", naPacket.build());
                // serialize the response packet
                byte[] txPayload = fillNeighborAdvertisementPacket(naPacket.build());
                InstanceIdentifier<Node> outNode = packet.getIngress().getValue().firstIdentifierOf(Node.class);
                TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                                              .setNode(new NodeRef(outNode))
                                              .setEgress(packet.getIngress()).build();
                // Tx the packet out of the controller.
                if(pktProcessService != null) {
                    LOG.debug("transmitting the packet out on {}", packet.getIngress());
                    pktProcessService.transmitPacket(input);
                    pktProccessedCounter++;
                }
            } else {
                if (type == RoutemgrUtil.ICMPv6_RS_CODE) {
                    byte[] data = packet.getPayload();
                    IpAddress gatewayIp;
                    String prefix = "";
                    String gatewayMac = null;
                    RouterSolicitationPacket rsPdu = deserializeRSPacket(data);
                    InstanceIdentifier<Node> outNode = packet.getIngress().getValue().firstIdentifierOf(Node.class);
                    // Todo: Validate the checksum.
                    VirtualPort port = ifMgr.getInterfaceForMacAddress(rsPdu.getSourceMac().getValue().toUpperCase());
                    if (port == null) {
                        LOG.warn("No learnt interface is available for the given MAC Address {}",
                                rsPdu.getSourceMac());
                        return;
                    }


                    for (VirtualSubnet subnet : port.getSubnets()) {
                        gatewayIp = subnet.getGatewayIp();
                        if (gatewayIp.getIpv4Address() != null)
                            continue;
                        gatewayMac = ifMgr.getInterfaceForAddress(new Ipv6Address(gatewayIp.getIpv6Address().getValue())).getMacAddress();
                        String autoAddressSubnets = ifMgr.IPV6_SLAAC + ifMgr.IPV6_DHCPV6_STATELESS;

                        if (autoAddressSubnets.contains(subnet.getIpv6AddressMode())
                            || autoAddressSubnets.contains(subnet.getIpv6RAMode())) {
                            prefix = subnet.getSubnetCidr();
                            //Todo: Handle multiple prefix use-case.
                            break;
                        } else {
                            LOG.warn("Subnet is not an auto-address subnet. Returning.");
                            return;
                        }
                    }

                    RouterAdvertisementPacketBuilder raPacket = new RouterAdvertisementPacketBuilder();
                    updateRAResponse(rsPdu, raPacket, gatewayMac, rsPdu.getSourceMac(), prefix);
                    LOG.warn("Router Advt message {}.", raPacket.build());
                    // serialize the response packet
                    byte[] txPayload = fillRouterAdvertisementPacket(raPacket.build());
                    TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                            .setNode(new NodeRef(outNode))
                            .setEgress(packet.getIngress()).build();
                    // Tx the packet out of the controller.
                    if (pktProcessService != null) {
                        LOG.warn("Transmitting the packet out {}", packet.getIngress());
                        pktProcessService.transmitPacket(input);
                    }
                }
            }
        }

        private RouterSolicitationPacket deserializeRSPacket(byte[] data) {
            RouterSolicitationPacketBuilder rsPdu = new RouterSolicitationPacketBuilder();
            int bitOffset = 0;
            RoutemgrUtil instance = RoutemgrUtil.getInstance();

            try {
                // TODO: We may not need to deserialize the whole packet. Just de-serialize the necessary fields.
                rsPdu.setDestinationMac(new MacAddress(
                        instance.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setSourceMac(new MacAddress(
                        instance.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = IPv6_HDR_START;
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

                //Todo: Validate that Option-type is 1 (Source Link Layer Option)
                rsPdu.setOptionType(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                rsPdu.setSourceAddrLength(BitBufferHelper.getShort(BitBufferHelper.getBits(data, bitOffset, 8)));
                bitOffset = bitOffset + 8;
                if (rsPdu.getOptionType() == 1) {
                    rsPdu.setSourceLlAddress(new MacAddress(
                            instance.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                }
            } catch (BufferException | UnknownHostException  e) {
                LOG.warn("Exception obtained when deserializing Router Solicitation packet", e.toString());
            }
            return rsPdu.build();
        }

        private void updateRAResponse(RouterSolicitationPacket pdu, RouterAdvertisementPacketBuilder raPacket,
                                      String gatewayMac, MacAddress vmMac, String ipV6Prefix) {
            RoutemgrUtil instance = RoutemgrUtil.getInstance();
            MacAddress mac = MacAddress.getDefaultInstance(gatewayMac);
            raPacket.setSourceMac(mac);
            raPacket.setDestinationMac(vmMac);
            raPacket.setEthertype(pdu.getEthertype());

            raPacket.setVersion(pdu.getVersion());
            raPacket.setFlowLabel(pdu.getFlowLabel());
            raPacket.setIpv6Length(56); // Todo Length. Should this be 44?
            raPacket.setNextHeader(pdu.getNextHeader());
            raPacket.setHopLimit(RoutemgrUtil.ICMPv6_MAX_HOP_LIMIT);
            raPacket.setSourceIpv6(instance.getIpv6LinkLocalAddressFromMac(mac));
            raPacket.setDestinationIpv6(new Ipv6Address("FF02::1"));

            raPacket.setIcmp6Type(RoutemgrUtil.ICMPv6_RA_CODE);
            raPacket.setIcmp6Code((short)0);
            raPacket.setIcmp6Chksum(0);

            raPacket.setCurHopLimit((short) IPV6_DEFAULT_HOP_LIMIT);
            raPacket.setFlags((short) 0);
            raPacket.setRouterLifetime(IPV6_ROUTER_LIFETIME);
            raPacket.setReachableTime((long) 0);
            raPacket.setRetransTime((long) 0);

            raPacket.setOptionSourceAddr((short)1);
            raPacket.setSourceAddrLength((short)1);
            raPacket.setSourceLlAddress(MacAddress.getDefaultInstance(gatewayMac));

            PrefixListBuilder prefix = new PrefixListBuilder();
            prefix.setOptionType((short)3);
            prefix.setOptionLength((short)4);
            prefix.setPrefixLength((short)64);
            short flag = 0;
            flag = (short) (flag | (1 << 7));
            flag = (short) (flag | (1 << 6));
            prefix.setFlags((short)flag);
            prefix.setValidLifetime((long) IPV6_RA_VALID_LIFETIME);
            prefix.setPreferredLifetime((long) IPV6_RA_PREFERRED_LIFETIME);
            prefix.setReserved((long) 0);

            prefix.setPrefix(new Ipv6Prefix(ipV6Prefix));

            List<PrefixList> pList = new ArrayList<PrefixList>(1);
            pList.add(prefix.build());
            raPacket.setPrefixList((List<PrefixList>) pList);

            return;
        }

        private NeighborSolicitationPacket deserializeNSPacket(byte[] data) {
            NeighborSolicitationPacketBuilder nsPdu = new NeighborSolicitationPacketBuilder();
            int bitOffset = 0;
            RoutemgrUtil instance = RoutemgrUtil.getInstance();

            try {
                nsPdu.setDestinationMac(new MacAddress(
                    instance.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setSourceMac(new MacAddress(
                    instance.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = IPv6_HDR_START;
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

        private void updateNAResponse(NeighborSolicitationPacket pdu, VirtualPort port, NeighborAdvertisePacketBuilder naPacket) {
            long flag = 0;
            if (!pdu.getSourceIpv6().equals(RoutemgrUtil.UNSPECIFIED_ADDR)) {
                naPacket.setDestinationIpv6(pdu.getSourceIpv6());
                flag = 0x60;
            } else {
                naPacket.setDestinationIpv6(RoutemgrUtil.ALL_NODES_MCAST_ADDR);
                flag = 0x20;
            }
            naPacket.setDestinationMac(pdu.getSourceMac());
            naPacket.setEthertype(pdu.getEthertype());
            naPacket.setSourceIpv6(pdu.getTargetIpAddress());
            naPacket.setSourceMac(new MacAddress(port.getMacAddress()));
            naPacket.setHopLimit(RoutemgrUtil.ICMPv6_MAX_HOP_LIMIT);
            naPacket.setIcmp6Type(RoutemgrUtil.ICMPv6_NA_CODE);
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

        private byte[] fillRouterAdvertisementPacket(RouterAdvertisementPacket pdu) {
            ByteBuffer buf = ByteBuffer.allocate(ICMPV6_OFFSET+pdu.getIpv6Length());
            RoutemgrUtil instance = RoutemgrUtil.getInstance();

            buf.put(instance.convertEthernetHeaderToByte((EthernetHeader)pdu), 0, 14);
            buf.put(instance.convertIpv6HeaderToByte((Ipv6Header)pdu), 0, 40);
            buf.put(icmp6RAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
            int checksum = instance.calcIcmpv6Checksum(buf.array(), (Ipv6Header) pdu);
            buf.putShort((ICMPV6_OFFSET + 2), (short)checksum);
            return (buf.array());
        }

        private byte[] icmp6RAPayloadtoByte(RouterAdvertisementPacket pdu) {
            RoutemgrUtil instance = RoutemgrUtil.getInstance();
            LOG.debug("in RouterAdvt icmp6RAPayloadtoByte");
            byte[] data = new byte[60]; // Todo: Should be actually 56 bytes.
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
            buf.put(instance.bytesFromHexString(pdu.getSourceLlAddress().getValue().toString()));

            PrefixList prefix = pdu.getPrefixList().get(0);

            buf.put((byte)prefix.getOptionType().shortValue());
            buf.put((byte)prefix.getOptionLength().shortValue());
            buf.put((byte)prefix.getPrefixLength().shortValue());
            buf.put((byte)prefix.getFlags().shortValue());
            buf.putInt((int)prefix.getValidLifetime().longValue());
            buf.putInt((int)prefix.getPreferredLifetime().longValue());
            buf.putInt((int)prefix.getReserved().longValue());
            buf.put(IetfInetUtil.INSTANCE.ipv6PrefixToBytes(new Ipv6Prefix(prefix.getPrefix())));
            LOG.debug("icmp6 payload {}", data);
            return data;
        }

        private byte[] icmp6NAPayloadtoByte(NeighborAdvertisePacket pdu) {
            byte[] data = new byte[36];
            Arrays.fill(data, (byte)0);
            RoutemgrUtil instance = RoutemgrUtil.getInstance();

            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.put((byte)pdu.getIcmp6Type().shortValue());
            buf.put((byte)pdu.getIcmp6Code().shortValue());
            buf.putShort((short)pdu.getIcmp6Chksum().intValue());
            buf.putInt((int)pdu.getFlags().longValue());
            try {
                byte[] bAddr = null;
                bAddr = InetAddress.getByName(pdu.getTargetAddress().getValue()).getAddress();
                buf.put(bAddr);
            } catch (UnknownHostException e) {
                LOG.error("serializing NA target address failed", e);
            }
            buf.put((byte)pdu.getOptionType().shortValue());
            buf.put((byte)pdu.getTargetAddrLength().shortValue());
            buf.put(instance.bytesFromHexString(pdu.getTargetLlAddress().getValue().toString()));
            return data;
        }

        private byte[] fillNeighborAdvertisementPacket(NeighborAdvertisePacket pdu) {
            ByteBuffer buf = ByteBuffer.allocate(RoutemgrUtil.ICMPV6_OFFSET+pdu.getIpv6Length());

            RoutemgrUtil instance = RoutemgrUtil.getInstance();
            buf.put(instance.convertEthernetHeaderToByte((EthernetHeader)pdu), 0, 14);
            buf.put(instance.convertIpv6HeaderToByte((Ipv6Header)pdu), 0, 40);
            buf.put(icmp6NAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
            int checksum = instance.calcIcmpv6Checksum(buf.array(), (Ipv6Header) pdu);
            buf.putShort((RoutemgrUtil.ICMPV6_OFFSET + 2), (short)checksum);
            return (buf.array());
        }
    }
}
