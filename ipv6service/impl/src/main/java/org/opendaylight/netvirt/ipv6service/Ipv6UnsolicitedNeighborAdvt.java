/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborAdvertisePacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Ipv6UnsolicitedNeighborAdvt {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6UnsolicitedNeighborAdvt.class);

    private final PacketProcessingService packetService;

    public Ipv6UnsolicitedNeighborAdvt(PacketProcessingService packetService) {
        this.packetService = packetService;
    }

    public void transmitUnsolicitedNeighborAdvt(List<NodeConnectorRef> outportList,
                                                MacAddress srcMacAddress, Ipv6Address srcIpv6Address,
                                                Ipv6Address targetIpv6Address) {
        LOG.debug("transmitUnsolicitedNA from router interface Src MAC {} and Src IPv6 Address {} to target "
                        + "IPv6 Address {}", srcMacAddress, srcIpv6Address, targetIpv6Address);
        //formulate the Unsolicited NA response
        NeighborAdvertisePacketBuilder naPacket = new NeighborAdvertisePacketBuilder();
        updateNAResponse(srcMacAddress, srcIpv6Address, targetIpv6Address, naPacket);
        // serialize the response packet
        byte[] txPayload = fillNeighborAdvertisementPacket(naPacket.build());
        for (NodeConnectorRef outport: outportList) {
            InstanceIdentifier<Node> outNode = outport.getValue().firstIdentifierOf(Node.class);
            TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                    .setNode(new NodeRef(outNode))
                    .setEgress(outport).build();
            LOG.debug("Transmitting the Unsolicited Neighbor Advt packet out {}", outport);
            JdkFutures.addErrorLogging(packetService.transmitPacket(input), LOG, "transmitPacket");
        }
    }

    protected void updateNAResponse(MacAddress srcMacAddress, Ipv6Address srcIpv6Address,
                                        Ipv6Address targetIpv6Address, NeighborAdvertisePacketBuilder naPacket) {

        naPacket.setSourceMac(srcMacAddress);
        naPacket.setDestinationMac(new MacAddress(Ipv6Constants.DEF_MCAST_MAC));
        naPacket.setEthertype(Ipv6Constants.IP_V6_ETHTYPE);

        naPacket.setSourceIpv6(srcIpv6Address);
        naPacket.setDestinationIpv6(targetIpv6Address);
        naPacket.setHopLimit(Ipv6Constants.ICMP_V6_MAX_HOP_LIMIT);
        naPacket.setIcmp6Type(Ipv6Constants.ICMP_V6_NA_CODE);
        naPacket.setIcmp6Code((short) 0);
        long flag = 0xA0; // Set Router and Override Flag.
        flag = flag << 24;
        naPacket.setFlags(flag);
        naPacket.setFlowLabel(Ipv6Constants.DEF_FLOWLABEL);
        naPacket.setIpv6Length(32);
        naPacket.setNextHeader(Ipv6Constants.ICMP6_NHEADER);

        naPacket.setOptionType(Ipv6Constants.ICMP_V6_OPTION_TARGET_LLA);
        naPacket.setTargetAddrLength((short) 1);
        naPacket.setTargetAddress(srcIpv6Address);
        naPacket.setTargetLlAddress(srcMacAddress);
        naPacket.setVersion(Ipv6Constants.IPV6_VERSION);
        naPacket.setIcmp6Chksum(0);
        return;
    }

    private byte[] icmp6NAPayloadtoByte(NeighborAdvertisePacket pdu) {
        byte[] data = new byte[36];
        Arrays.fill(data, (byte) 0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte) pdu.getIcmp6Type().shortValue());
        buf.put((byte) pdu.getIcmp6Code().shortValue());
        buf.putShort((short) pdu.getIcmp6Chksum().intValue());
        buf.putInt((int) pdu.getFlags().longValue());
        try {
            byte[] address = null;
            address = InetAddress.getByName(pdu.getTargetAddress().getValue()).getAddress();
            buf.put(address);
        } catch (UnknownHostException e) {
            LOG.error("Serializing NA target address failed", e);
        }
        buf.put((byte) pdu.getOptionType().shortValue());
        buf.put((byte) pdu.getTargetAddrLength().shortValue());
        buf.put(Ipv6ServiceUtils.bytesFromHexString(pdu.getTargetLlAddress().getValue()));
        return data;
    }

    private byte[] fillNeighborAdvertisementPacket(NeighborAdvertisePacket pdu) {
        ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

        buf.put(Ipv6ServiceUtils.convertEthernetHeaderToByte(pdu), 0, 14);
        buf.put(Ipv6ServiceUtils.convertIpv6HeaderToByte(pdu), 0, 40);
        buf.put(icmp6NAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
        int checksum = Ipv6ServiceUtils.calcIcmpv6Checksum(buf.array(), pdu);
        buf.putShort(Ipv6Constants.ICMPV6_OFFSET + 2, (short) checksum);
        return buf.array();
    }

}
