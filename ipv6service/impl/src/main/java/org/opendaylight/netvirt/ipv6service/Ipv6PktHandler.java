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
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants.Ipv6RtrAdvertType;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.BitBufferHelper;
import org.opendaylight.openflowplugin.libraries.liblldp.BufferException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.Ipv6Header;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.RouterSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6PktHandler implements AutoCloseable, PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6PktHandler.class);
    private final AtomicLong pktProccessedCounter = new AtomicLong(0);
    private final PacketProcessingService pktService;
    private final IfMgr ifMgr;
    private final ExecutorService packetProcessor = Executors.newCachedThreadPool();

    @Inject
    public Ipv6PktHandler(PacketProcessingService pktService, IfMgr ifMgr) {
        this.pktService = pktService;
        this.ifMgr = ifMgr;
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
            if (ethType == Ipv6Constants.IP_V6_ETHTYPE) {
                v6NxtHdr = BitBufferHelper.getByte(BitBufferHelper.getBits(data,
                        Ipv6Constants.IP_V6_HDR_START + Ipv6Constants.IP_V6_NEXT_HDR, Ipv6Constants.ONE_BYTE));
                if (v6NxtHdr == Ipv6Constants.ICMP_V6_TYPE) {
                    int icmpv6Type = BitBufferHelper.getInt(BitBufferHelper.getBits(data,
                            Ipv6Constants.ICMPV6_HDR_START, Ipv6Constants.ONE_BYTE));
                    if (icmpv6Type == Ipv6Constants.ICMP_V6_RS_CODE
                            || icmpv6Type == Ipv6Constants.ICMP_V6_NS_CODE) {
                        packetProcessor.execute(new PacketHandler(icmpv6Type, packetReceived));
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
        return pktProccessedCounter.get();
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
            if (type == Ipv6Constants.ICMP_V6_NS_CODE) {
                LOG.info("Received Neighbor Solicitation request");
                processNeighborSolicitationRequest();
            } else if (type == Ipv6Constants.ICMP_V6_RS_CODE) {
                LOG.info("Received Router Solicitation request");
                processRouterSolicitationRequest();
            }
        }

        private void processNeighborSolicitationRequest() {
            byte[] data = packet.getPayload();
            NeighborSolicitationPacket nsPdu = deserializeNSPacket(data);
            Ipv6Header ipv6Header = nsPdu;
            if (Ipv6ServiceUtils.validateChecksum(data, ipv6Header, nsPdu.getIcmp6Chksum()) == false) {
                pktProccessedCounter.incrementAndGet();
                LOG.warn("Received Neighbor Solicitation with invalid checksum on {}. Ignoring the packet.",
                        packet.getIngress());
                return;
            }

            BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
            long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
            String interfaceName = ifMgr.getInterfaceNameFromTag(portTag);
            VirtualPort port = ifMgr.obtainV6Interface(new Uuid(interfaceName));
            if (port == null) {
                pktProccessedCounter.incrementAndGet();
                LOG.warn("Port {} not found, skipping.", interfaceName);
                return;
            }

            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
            if (routerPort == null) {
                pktProccessedCounter.incrementAndGet();
                LOG.warn("Port for network Id {} is not associated to a Router, skipping NS request.",
                        port.getNetworkID());
                return;
            }

            if (!routerPort.getIpv6Addresses().contains(nsPdu.getTargetIpAddress())) {
                pktProccessedCounter.incrementAndGet();
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
                JdkFutures.addErrorLogging(pktService.transmitPacket(input), LOG, "transmitPacket");
                pktProccessedCounter.incrementAndGet();
            }
        }

        private NeighborSolicitationPacket deserializeNSPacket(byte[] data) {
            NeighborSolicitationPacketBuilder nsPdu = new NeighborSolicitationPacketBuilder();
            int bitOffset = 0;

            try {
                nsPdu.setDestinationMac(new MacAddress(
                        Ipv6ServiceUtils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setSourceMac(new MacAddress(
                        Ipv6ServiceUtils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                nsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = Ipv6Constants.IP_V6_HDR_START;
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
                LOG.warn("Exception obtained when deserializing NS packet", e);
            }
            return nsPdu.build();
        }

        private void updateNAResponse(NeighborSolicitationPacket pdu,
                                      VirtualPort port, NeighborAdvertisePacketBuilder naPacket) {
            long flag = 0;
            if (!pdu.getSourceIpv6().equals(Ipv6ServiceUtils.UNSPECIFIED_ADDR)) {
                naPacket.setDestinationIpv6(pdu.getSourceIpv6());
                flag = 0xE0; // Set Router, Solicited and Override Flag.
            } else {
                naPacket.setDestinationIpv6(Ipv6ServiceUtils.ALL_NODES_MCAST_ADDR);
                flag = 0xA0; // Set Router and Override Flag.
            }
            naPacket.setDestinationMac(pdu.getSourceMac());
            naPacket.setEthertype(pdu.getEthertype());
            naPacket.setSourceIpv6(pdu.getTargetIpAddress());
            naPacket.setSourceMac(new MacAddress(port.getMacAddress()));
            naPacket.setHopLimit(Ipv6Constants.ICMP_V6_MAX_HOP_LIMIT);
            naPacket.setIcmp6Type(Ipv6Constants.ICMP_V6_NA_CODE);
            naPacket.setIcmp6Code(pdu.getIcmp6Code());
            flag = flag << 24;
            naPacket.setFlags(flag);
            naPacket.setFlowLabel(pdu.getFlowLabel());
            naPacket.setIpv6Length(32);
            naPacket.setNextHeader(pdu.getNextHeader());
            naPacket.setOptionType(Ipv6Constants.ICMP_V6_OPTION_TARGET_LLA);
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
            buf.put(Ipv6ServiceUtils.bytesFromHexString(pdu.getTargetLlAddress().getValue()));
            return data;
        }

        private byte[] fillNeighborAdvertisementPacket(NeighborAdvertisePacket pdu) {
            ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

            buf.put(Ipv6ServiceUtils.convertEthernetHeaderToByte(pdu), 0, 14);
            buf.put(Ipv6ServiceUtils.convertIpv6HeaderToByte(pdu), 0, 40);
            buf.put(icmp6NAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
            int checksum = Ipv6ServiceUtils.calcIcmpv6Checksum(buf.array(), pdu);
            buf.putShort(Ipv6Constants.ICMPV6_OFFSET + 2, (short)checksum);
            return buf.array();
        }

        private void processRouterSolicitationRequest() {
            byte[] data = packet.getPayload();
            RouterSolicitationPacket rsPdu = deserializeRSPacket(data);
            Ipv6Header ipv6Header = rsPdu;
            if (Ipv6ServiceUtils.validateChecksum(data, ipv6Header, rsPdu.getIcmp6Chksum()) == false) {
                pktProccessedCounter.incrementAndGet();
                LOG.warn("Received RS packet with invalid checksum on {}. Ignoring the packet.",
                        packet.getIngress());
                return;
            }

            BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
            long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
            String interfaceName = ifMgr.getInterfaceNameFromTag(portTag);
            Uuid portId = new Uuid(interfaceName);
            VirtualPort port = ifMgr.obtainV6Interface(portId);
            if (port == null) {
                pktProccessedCounter.incrementAndGet();
                LOG.info("Port {} not found, skipping.", interfaceName);
                return;
            }

            VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(port.getNetworkID());
            if (routerPort == null) {
                pktProccessedCounter.incrementAndGet();
                LOG.warn("Port for networkId {} is not associated to a Router, skipping.", port.getNetworkID());
                return;
            }
            Ipv6RouterAdvt ipv6RouterAdvert = new Ipv6RouterAdvt(pktService, ifMgr);
            LOG.debug("Sending Solicited Router Advertisement for the port {} belongs to the network {}", port,
                    port.getNetworkID());
            ipv6RouterAdvert.transmitRtrAdvertisement(Ipv6RtrAdvertType.SOLICITED_ADVERTISEMENT,
                    routerPort, 0, rsPdu, port.getDpId(), port.getIntfUUID());
            pktProccessedCounter.incrementAndGet();
        }

        private RouterSolicitationPacket deserializeRSPacket(byte[] data) {
            RouterSolicitationPacketBuilder rsPdu = new RouterSolicitationPacketBuilder();
            int bitOffset = 0;

            try {
                rsPdu.setDestinationMac(new MacAddress(
                        Ipv6ServiceUtils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setSourceMac(new MacAddress(
                        Ipv6ServiceUtils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                bitOffset = bitOffset + 48;
                rsPdu.setEthertype(BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitOffset, 16)));

                bitOffset = Ipv6Constants.IP_V6_HDR_START;
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
                    if (rsPdu.getOptionType() == Ipv6Constants.ICMP_V6_OPTION_SOURCE_LLA) {
                        rsPdu.setSourceLlAddress(new MacAddress(
                                Ipv6ServiceUtils.bytesToHexString(BitBufferHelper.getBits(data, bitOffset, 48))));
                    }
                }
            } catch (BufferException | UnknownHostException e) {
                LOG.warn("Exception obtained when deserializing Router Solicitation packet", e);
            }
            return rsPdu.build();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        packetProcessor.shutdown();
    }
}
