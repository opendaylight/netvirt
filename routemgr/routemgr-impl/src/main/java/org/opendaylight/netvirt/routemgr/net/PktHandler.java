/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.routemgr.utils.BitBufferHelper;
import org.opendaylight.netvirt.routemgr.utils.BufferException;
import org.opendaylight.netvirt.routemgr.utils.RoutemgrUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.EthernetHeader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.Ipv6Header;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.NeighborAdvertisePacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.NeighborAdvertisePacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.NeighborSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.NeighborSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




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
    private final ExecutorService packetProcessor = Executors.newCachedThreadPool();

    private DataBroker dataService;
    private PacketProcessingService pktProcessService;


    public void setDataBrokerService(DataBroker dataService) {
        this.dataService = dataService;
    }

    public void setPacketProcessingService(PacketProcessingService packetProcessingService) {
        this.pktProcessService = packetProcessingService;
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
                    LOG.warn("Received NS packet with invalid checksum  on {}. Ignoring the packet",
                        packet.getIngress());
                    return;
                }

                // obtain the interface
                LOG.debug("valid checksum obtaining ifMgr and virtual port");
                IfMgr ifMgr = IfMgr.getIfMgrInstance();
                VirtualPort port = ifMgr.getInterfaceForAddress(nsPdu.getTargetIpAddress());
                if (port == null) {
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
                }
            } else if (type == RoutemgrUtil.ICMPv6_RS_CODE) {
                // TODO
            }

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

        private byte[] icmp6NAPayloadtoByte(NeighborAdvertisePacket pdu) {
            byte[] data = new byte[36];
            Arrays.fill(data, (byte)0);
            RoutemgrUtil instance = RoutemgrUtil.getInstance();

            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.put((byte)pdu.getIcmp6Type().shortValue());
            buf.put((byte)pdu.getIcmp6Code().shortValue());
            buf.putShort((short)pdu.getIcmp6Chksum().intValue());
            buf.putInt((int)pdu.getFlags().longValue());
            buf.put(IetfInetUtil.INSTANCE.ipv6AddressBytes(pdu.getTargetAddress()));
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
