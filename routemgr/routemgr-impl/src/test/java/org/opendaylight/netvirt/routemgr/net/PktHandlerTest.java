/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class PktHandlerTest {
    private PacketProcessingService pktProcessService;
    private PktHandler pktHandler;
    private IfMgr ifMgrInstance;
    private long counter;
    private static final int THREAD_WAIT_TIME = 100;

    @Before
    public void initTest() {
        pktProcessService = Mockito.mock(PacketProcessingService.class);
        pktHandler = new PktHandler();
        pktHandler.setPacketProcessingService(pktProcessService);
        ifMgrInstance = Mockito.mock(IfMgr.class);
        pktHandler.setIfMgrInstance(ifMgrInstance);
        counter = pktHandler.getPacketProcessedCounter();
    }

    @Test
    public void testOnPacketReceivedWithInvalidPacket() throws Exception {
        pktHandler.onPacketReceived(null);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
        byte pktArray[] = {};
        PacketReceived packet = new PacketReceivedBuilder().setPayload(pktArray).build();
        pktHandler.onPacketReceived(packet);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testOnPacketReceivedWithInvalidParams() throws Exception {
        //invalid ethtype
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "80 00",                                           // Invalid (fake IPv6)
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is authentication
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00"  // Destination IP
        )).build());
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //invalid ipv6 header
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "33",                                              // Next header is authentication
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00"  // Destination IP
        )).build());
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //invalid icmpv6 header
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "85",                                              // ICMPv6 router solicitation
                "00",                                              // Code
                "67 3C",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).build());
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedNeighborSolicitationWithInvalidPayload() throws Exception {
        //incorrect checksum
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "87",                                              // ICMPv6 neighbor solicitation
                "00",                                              // Code
                "67 3E",                                           // Checksum (invalid, should be 67 3C)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //unavailable ip
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(null);
        counter = pktHandler.getPacketProcessedCounter();
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "87",                                              // ICMPv6 neighbor solicitation
                "00",                                              // Code
                "67 3C",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithInvalidPayload() throws Exception {
        // incorrect checksum in Router Solicitation
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "85",                                              // ICMPv6 router solicitation
                "00",                                              // Code
                "69 3E",                                           // Checksum (invalid, should be 67 3C)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        // Request from an unknown port (i.e., unknown MAC Address)
        when(ifMgrInstance.getInterfaceForMacAddress(any())).thenReturn(null);
        counter = pktHandler.getPacketProcessedCounter();
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "85",                                              // ICMPv6 router solicitation
                "00",                                              // Code
                "69 3C",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedNeighborSolicitationWithValidPayload() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(intf);
        when(intf.getMacAddress()).thenReturn("33:33:FF:F5:00:00");
        when(intf.getDeviceOwner()).thenReturn(ifMgrInstance.NETWORK_ROUTER_INTERFACE);
        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

        byte[] expected_payload = buildPacket(
                "00 01 02 03 04 05",                               // Destination MAC
                "33 33 FF F5 00 00",                               // Source MAC
                "86 DD",                                           // Ethertype - IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 00 00 00 00 01", // Destination IP
                "88",                                              // ICMPv6 neighbor advertisement.
                "00",                                              // Code
                "7E 88",                                           // Checksum (valid)
                "A0 00 00 00",                                     // Flags
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00", // Target Address
                "02",                                              // Type: Target Link-Layer Option
                "01",                                              // Option length
                "33 33 FF F5 00 00"                                // Target Link layer address
        );

        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 FF F5 00 00",                               // Destination MAC
                "00 01 02 03 04 05",                               // Source MAC
                "86 DD",                                           // IPv6
                "6E 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 18",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF F5 00 00", // Destination IP
                "87",                                              // ICMPv6 neighbor solicitation
                "00",                                              // Code
                "67 3C",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 C0 00 54 FF FE F5 00 00"  // Target
        )).setIngress(ncRef).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));
        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expected_payload).
                setNode(new NodeRef(ncId)).
                setEgress(ncRef).build());
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithSingleSubnet() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getMacAddress()).thenReturn("fa:16:3e:4e:18:0c");
        when(ifMgrInstance.getInterfaceForMacAddress(any())).thenReturn(intf);
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(intf);

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8::1"));

        VirtualSubnet v6Subnet = new VirtualSubnet();
        VirtualRouter vRouter = new VirtualRouter();
        v6Subnet.setRouter(vRouter);
        v6Subnet.setGatewayIp(gwIpAddress);
        v6Subnet.setIpv6AddressMode(ifMgrInstance.IPV6_SLAAC);
        v6Subnet.setIpv6RAMode(ifMgrInstance.IPV6_SLAAC);
        v6Subnet.setSubnetCidr(new IpPrefix("2001:db8::/64".toCharArray()));

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet);
        when(intf.getSubnets()).thenReturn(subnetList);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);
        byte[] expected_payload = buildPacket(
                "FA 16 3E 69 2C F3",                               // Destination MAC
                "FA 16 3E 4E 18 0C",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 38",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 4E 18 0C", // Source IP
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Destination IP
                "86",                                              // ICMPv6 router advertisement.
                "00",                                              // Code
                "F0 71",                                           // Checksum (valid)
                "40",                                              // Current Hop Limit
                "00",                                              // ICMPv6 RA Flags
                "11 94",                                           // Router Lifetime
                "00 00 00 00",                                     // Reachable time
                "00 00 00 00",                                     // Retransmission time.
                "01",                                              // Type: Source Link-Layer Option
                "01",                                              // Option length
                "FA 16 3E 4E 18 0C",                               // Source Link layer address
                "03",                                              // Type: Prefix Information
                "04",                                              // Option length
                "40",                                              // Prefix length
                "C0",                                              // Prefix flags
                "00 27 8D 00",                                     // Valid lifetime
                "00 09 3A 80",                                     // Preferred lifetime
                "00 00 00 00",                                     // Reserved
                "20 01 0D B8 00 00 00 00 00 00 00 00 00 00 00 00"  // Prefix
        );

        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 00 00 00 02",                               // Destination MAC
                "FA 16 3E 69 2C F3",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 10",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 00 00 00 00 02", // Destination IP
                "85",                                              // ICMPv6 router solicitation
                "00",                                              // Code
                "B4 47",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "01",                                              // ICMPv6 Option: Source Link Layer Address
                "01",                                              // Length
                "FA 16 3E 69 2C F3"                                // Link Layer Address
        )).setIngress(ncRef).build());

        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));
        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expected_payload).
                setNode(new NodeRef(ncId)).
                setEgress(ncRef).build());
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithMultipleSubnets() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getMacAddress()).thenReturn("50:7B:9D:78:54:F3");
        when(ifMgrInstance.getInterfaceForMacAddress(any())).thenReturn(intf);
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(intf);

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8:1111::1"));

        VirtualSubnet v6Subnet1 = new VirtualSubnet();
        VirtualRouter vRouter = new VirtualRouter();
        v6Subnet1.setRouter(vRouter);
        v6Subnet1.setGatewayIp(gwIpAddress);
        v6Subnet1.setIpv6AddressMode(ifMgrInstance.IPV6_SLAAC);
        v6Subnet1.setIpv6RAMode(ifMgrInstance.IPV6_SLAAC);
        v6Subnet1.setSubnetCidr(new IpPrefix("2001:db8:1111::/64".toCharArray()));

        VirtualSubnet v6Subnet2 = new VirtualSubnet();
        v6Subnet2.setRouter(vRouter);
        v6Subnet2.setGatewayIp(gwIpAddress);
        v6Subnet2.setIpv6AddressMode(ifMgrInstance.IPV6_DHCPV6_STATELESS);
        v6Subnet2.setIpv6RAMode(ifMgrInstance.IPV6_DHCPV6_STATELESS);
        v6Subnet2.setSubnetCidr(new IpPrefix("2001:db8:2222::/64".toCharArray()));

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet1);
        subnetList.add(v6Subnet2);
        when(intf.getSubnets()).thenReturn(subnetList);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);
        byte[] expected_payload = buildPacket(
                "FA 16 3E 69 2C F3",                               // Destination MAC
                "50 7B 9D 78 54 F3",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 58",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 52 7B 9D FF FE 78 54 F3", // Source IP
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Destination IP
                "86",                                              // ICMPv6 router advertisement.
                "00",                                              // Code
                "9A C4",                                           // Checksum (valid)
                "40",                                              // Current Hop Limit
                "40",                                              // ICMPv6 RA Flags
                "11 94",                                           // Router Lifetime
                "00 00 00 00",                                     // Reachable time
                "00 00 00 00",                                     // Retransmission time.
                "01",                                              // Type: Source Link-Layer Option
                "01",                                              // Option length
                "50 7B 9D 78 54 F3",                               // Source Link layer address
                "03",                                              // Type: Prefix Information
                "04",                                              // Option length
                "40",                                              // Prefix length
                "C0",                                              // Prefix flags
                "00 27 8D 00",                                     // Valid lifetime
                "00 09 3A 80",                                     // Preferred lifetime
                "00 00 00 00",                                     // Reserved
                "20 01 0D B8 11 11 00 00 00 00 00 00 00 00 00 00", // Prefix
                "03",                                              // Type: Prefix Information
                "04",                                              // Option length
                "40",                                              // Prefix length
                "C0",                                              // Prefix flags
                "00 27 8D 00",                                     // Valid lifetime
                "00 09 3A 80",                                     // Preferred lifetime
                "00 00 00 00",                                     // Reserved
                "20 01 0D B8 22 22 00 00 00 00 00 00 00 00 00 00"  // Prefix
        );

        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(buildPacket(
                "33 33 00 00 00 02",                               // Destination MAC
                "FA 16 3E 69 2C F3",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 10",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 00 00 00 00 02", // Destination IP
                "85",                                              // ICMPv6 router solicitation
                "00",                                              // Code
                "B4 47",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "01",                                              // ICMPv6 Option: Source Link Layer Address
                "01",                                              // Length
                "FA 16 3E 69 2C F3"                                // Link Layer Address
        )).setIngress(ncRef).build());

        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));
        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expected_payload).
                setNode(new NodeRef(ncId)).
                setEgress(ncRef).build());
    }

    private void waitForPacketProcessing() throws InterruptedException {
        int timeOut = 1;
        while (timeOut < 5) {
            if (pktHandler.getPacketProcessedCounter() > counter) {
                break;
            }
            Thread.sleep(THREAD_WAIT_TIME);
            timeOut++;
        }
    }

    private byte[] buildPacket(String... contents) {
        List<String[]> splitContents = new ArrayList<>();
        int packetLength = 0;
        for (String content : contents) {
            String[] split = content.split(" ");
            packetLength += split.length;
            splitContents.add(split);
        }
        byte[] packet = new byte[packetLength];
        int index = 0;
        for (String[] split : splitContents) {
            for (String component : split) {
                // We can't use Byte.parseByte() here, it refuses anything > 7F
                packet[index] = (byte) Integer.parseInt(component, 16);
                index++;
            }
        }
        return packet;
    }
}
