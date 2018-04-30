/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
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
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.NeighborSolicitationPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6NeighborSolicitation {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6NeighborSolicitation.class);

    private final PacketProcessingService packetService;

    public Ipv6NeighborSolicitation(PacketProcessingService packetService) {
        this.packetService = packetService;
    }

    private byte[] frameNeighborSolicitationRequest(MacAddress srcMacAddress, Ipv6Address srcIpv6Address,
                                                  Ipv6Address targetIpv6Address) {
        MacAddress macAddress = Ipv6ServiceUtils.getIpv6MulticastMacAddress(targetIpv6Address);
        Ipv6Address snMcastAddr = Ipv6ServiceUtils.getIpv6SolicitedNodeMcastAddress(targetIpv6Address);

        NeighborSolicitationPacketBuilder nsPacket = new NeighborSolicitationPacketBuilder();
        nsPacket.setSourceMac(srcMacAddress);
        nsPacket.setDestinationMac(macAddress);
        nsPacket.setEthertype(NwConstants.ETHTYPE_IPV6);

        nsPacket.setVersion(Ipv6Constants.IP_VERSION_6);
        nsPacket.setFlowLabel((long) 0);
        nsPacket.setIpv6Length(32);
        nsPacket.setNextHeader(Ipv6Constants.ICMP_V6_TYPE);
        nsPacket.setHopLimit(Ipv6Constants.ICMP_V6_MAX_HOP_LIMIT);
        nsPacket.setSourceIpv6(srcIpv6Address);
        nsPacket.setDestinationIpv6(snMcastAddr);

        nsPacket.setIcmp6Type(Ipv6Constants.ICMP_V6_NS_CODE);
        nsPacket.setIcmp6Code((short) 0);
        nsPacket.setIcmp6Chksum(0);

        nsPacket.setReserved((long) 0);
        nsPacket.setTargetIpAddress(targetIpv6Address);
        nsPacket.setOptionType(Ipv6Constants.ICMP_V6_OPTION_SOURCE_LLA);
        nsPacket.setSourceAddrLength((short)1);
        nsPacket.setSourceLlAddress(srcMacAddress);

        return fillNeighborSolicitationPacket(nsPacket.build());
    }

    private byte[] icmp6NsPayloadtoByte(NeighborSolicitationPacket pdu) {
        byte[] data = new byte[36];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte)pdu.getIcmp6Type().shortValue());
        buf.put((byte)pdu.getIcmp6Code().shortValue());
        buf.putShort((short)pdu.getIcmp6Chksum().intValue());
        buf.putInt((int)pdu.getReserved().longValue());
        try {
            byte[] address = null;
            address = InetAddress.getByName(pdu.getTargetIpAddress().getValue()).getAddress();
            buf.put(address);
        } catch (UnknownHostException e) {
            LOG.error("Serializing NS target address failed", e);
        }

        buf.put((byte)pdu.getOptionType().shortValue());
        buf.put((byte)pdu.getSourceAddrLength().shortValue());
        buf.put(Ipv6ServiceUtils.bytesFromHexString(pdu.getSourceLlAddress().getValue()));
        return data;
    }

    private byte[] fillNeighborSolicitationPacket(NeighborSolicitationPacket pdu) {
        ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

        buf.put(Ipv6ServiceUtils.convertEthernetHeaderToByte(pdu), 0, 14);
        buf.put(Ipv6ServiceUtils.convertIpv6HeaderToByte(pdu), 0, 40);
        buf.put(icmp6NsPayloadtoByte(pdu), 0, pdu.getIpv6Length());
        int checksum = Ipv6ServiceUtils.calcIcmpv6Checksum(buf.array(), pdu);
        buf.putShort(Ipv6Constants.ICMPV6_OFFSET + 2, (short)checksum);
        return buf.array();
    }

    public boolean transmitNeighborSolicitation(BigInteger dpnId, NodeConnectorRef nodeRef,
                                                MacAddress srcMacAddress, Ipv6Address srcIpv6Address,
                                                Ipv6Address targetIpv6Address) {
        byte[] txPayload = frameNeighborSolicitationRequest(srcMacAddress, srcIpv6Address, targetIpv6Address);
        NodeConnectorRef nodeConnectorRef = MDSALUtil.getNodeConnRef(dpnId, "0xfffffffd");
        TransmitPacketInput input = new TransmitPacketInputBuilder().setPayload(txPayload)
                .setNode(new NodeRef(InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(new NodeId("openflow:" + dpnId))).build()))
                .setEgress(nodeRef).setIngress(nodeConnectorRef).build();

        // Tx the packet out of the controller.
        LOG.debug("Transmitting the Neighbor Solicitation packet out on {}", dpnId);
        ListenableFutures.addErrorLogging(packetService.transmitPacket(input), LOG, "transmitPacket");
        return true;
    }

    public void transmitNeighborSolicitationToOfGroup(BigInteger dpId, MacAddress srcMacAddress,
            Ipv6Address srcIpv6Address, Ipv6Address targetIpv6Address, long ofGroupId) {
        byte[] txPayload = frameNeighborSolicitationRequest(srcMacAddress, srcIpv6Address, targetIpv6Address);
        List<ActionInfo> lstActionInfo = new ArrayList<>();
        lstActionInfo.add(new ActionGroup(ofGroupId));

        TransmitPacketInput input = MDSALUtil.getPacketOutDefault(lstActionInfo, txPayload, dpId);
        // Tx the packet out of the controller.
        LOG.debug(
                "Transmitting Neighbor Solicitation packet out. srcMacAddress={}, srcIpv6Address={}, "
                        + "targetIpv6Address={}, dpId={}, ofGroupId={}",
                srcMacAddress.getValue(), srcIpv6Address.getValue(), targetIpv6Address.getValue(), dpId, ofGroupId);
        ListenableFutures.addErrorLogging(packetService.transmitPacket(input), LOG, "transmitPacket");
    }
}
