/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Metadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.packet.received.MatchBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class Ipv6PktHandlerTest {
    private PacketProcessingService pktProcessService;
    private Ipv6PktHandler pktHandler;
    private IfMgr ifMgrInstance;
    private NotificationPublishService notificationPublishService;
    private long counter;
    private static final int THREAD_WAIT_TIME = 100;
    private Ipv6TestUtils ipv6TestUtils;

    @Before
    public void initTest() {
        pktProcessService = Mockito.mock(PacketProcessingService.class);
        ifMgrInstance = Mockito.mock(IfMgr.class);
        notificationPublishService = Mockito.mock(NotificationPublishService.class);
        pktHandler = new Ipv6PktHandler(pktProcessService, ifMgrInstance, notificationPublishService);
        counter = pktHandler.getPacketProcessedCounter();
        ipv6TestUtils = new Ipv6TestUtils();
    }

    @Test
    public void testOnPacketReceivedWithInvalidPacket() throws Exception {
        pktHandler.onPacketReceived(null);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
        byte[] pktArray = {};
        PacketReceived packet = new PacketReceivedBuilder().setPayload(pktArray).build();
        pktHandler.onPacketReceived(packet);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testOnPacketReceivedWithInvalidParams() throws Exception {
        //invalid ethtype
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(null);
        counter = pktHandler.getPacketProcessedCounter();
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(null);
        counter = pktHandler.getPacketProcessedCounter();
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        when(intf.getNetworkID()).thenReturn(new Uuid("eeec9dba-d831-4ad7-84b9-00d7f65f0555"));
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);
        VirtualPort routerIntf = Mockito.mock(VirtualPort.class);
        when(ifMgrInstance.getRouterV6InterfaceForNetwork(any())).thenReturn(routerIntf);
        List<Ipv6Address> ipv6AddrList = new ArrayList<>();
        when(routerIntf.getMacAddress()).thenReturn("08:00:27:FE:8F:95");
        Ipv6Address llAddr = Ipv6Util.getIpv6LinkLocalAddressFromMac(new MacAddress("08:00:27:FE:8F:95"));
        ipv6AddrList.add(llAddr);
        when(routerIntf.getIpv6Addresses()).thenReturn(ipv6AddrList);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

        BigInteger mdata = new BigInteger(String.valueOf(0x1000000));
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
                "33 33 FF FE 8F 95",                               // Destination MAC
                "08 00 27 D4 10 BB",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class 0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 0A 00 27 FF FE D4 10 BB", // Source IP
                "FF 02 00 00 00 00 00 00 00 00 00 01 FF FE 8F 95", // Destination IP
                "87",                                              // ICMPv6 neighbor solicitation
                "00",                                              // Code
                "A9 57",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "FE 80 00 00 00 00 00 00 0A 00 27 FF FE FE 8F 95", // Target
                "01",                                              // ICMPv6 Option: Source Link Layer Address
                "01",                                              // Length
                "08 00 27 D4 10 BB"                                // Link Layer Address
        )).setIngress(ncRef).setMatch(matchbuilder.build()).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));

        byte[] expectedPayload = ipv6TestUtils.buildPacket(
                "08 00 27 D4 10 BB",                               // Destination MAC
                "08 00 27 FE 8F 95",                               // Source MAC
                "86 DD",                                           // Ethertype - IPv6
                "60 00 00 00",                                     // Version 6, traffic class 0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 0A 00 27 FF FE FE 8F 95", // Source IP
                "FE 80 00 00 00 00 00 00 0A 00 27 FF FE D4 10 BB", // Destination IP
                "88",                                              // ICMPv6 neighbor advertisement.
                "00",                                              // Code
                "17 D6",                                           // Checksum (valid)
                "E0 00 00 00",                                     // Flags
                "FE 80 00 00 00 00 00 00 0A 00 27 FF FE FE 8F 95", // Target Address
                "02",                                              // Type: Target Link-Layer Option
                "01",                                              // Option length
                "08 00 27 FE 8F 95"                                // Target Link layer address
        );
        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expectedPayload)
                .setNode(new NodeRef(ncId)).setEgress(ncRef).build());
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithSingleSubnet() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getDpId()).thenReturn(new BigInteger(String.valueOf(1)));
        when(intf.getIntfUUID()).thenReturn(Uuid.getDefaultInstance("ddec9dba-d831-4ad7-84b9-00d7f65f052f"));
        when(intf.getMacAddress()).thenReturn("fa:16:3e:4e:18:0c");
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        List<Action> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE).buildAction());
        when(ifMgrInstance.getEgressAction(any())).thenReturn(actions);
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);
        when(ifMgrInstance.getRouterV6InterfaceForNetwork(any())).thenReturn(intf);

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8::1"));

        VirtualSubnet v6Subnet = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix("2001:db8::/64".toCharArray()))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_SLAAC).ipv6RAMode(Ipv6ServiceConstants.IPV6_SLAAC).build();

        VirtualRouter virtualRouter = VirtualRouter.builder().build();
        v6Subnet.setRouter(virtualRouter);

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet);
        when(intf.getSubnets()).thenReturn(subnetList);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);
        BigInteger mdata = new BigInteger(String.valueOf(0x1000000));
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        )).setIngress(ncRef).setMatch(matchbuilder.build()).build());

        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));

        byte[] expectedPayload = ipv6TestUtils.buildPacket(
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
                "1B B0",                                           // Checksum (valid)
                "40",                                              // Current Hop Limit
                "00",                                              // ICMPv6 RA Flags
                "11 94",                                           // Router Lifetime
                "00 01 D4 C0",                                     // Reachable time
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
        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expectedPayload)
                .setNode(new NodeRef(ncId)).setEgress(ncRef).setIngress(ncRef).setAction(any(List.class)).build());
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithMultipleSubnets() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getDpId()).thenReturn(new BigInteger(String.valueOf(1)));
        when(intf.getMacAddress()).thenReturn("50:7B:9D:78:54:F3");
        when(intf.getIntfUUID()).thenReturn(Uuid.getDefaultInstance("ddec9dba-d831-4ad7-84b9-00d7f65f052f"));
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        when(ifMgrInstance.getRouterV6InterfaceForNetwork(any())).thenReturn(intf);
        List<Action> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE).buildAction());
        when(ifMgrInstance.getEgressAction(any())).thenReturn(actions);

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8:1111::1"));

        VirtualSubnet v6Subnet1 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix("2001:db8:1111::/64".toCharArray()))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_SLAAC).ipv6RAMode(Ipv6ServiceConstants.IPV6_SLAAC).build();
        VirtualRouter virtualRouter = VirtualRouter.builder().build();
        v6Subnet1.setRouter(virtualRouter);

        VirtualSubnet v6Subnet2 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix("2001:db8:2222::/64".toCharArray()))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS)
                .ipv6RAMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS).build();
        v6Subnet2.setRouter(virtualRouter);

        VirtualSubnet v6Subnet3 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix("2001:db8:3333::/64".toCharArray()))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL)
                .ipv6RAMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL).build();
        v6Subnet3.setRouter(virtualRouter);

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet1);
        subnetList.add(v6Subnet2);
        subnetList.add(v6Subnet3);
        when(intf.getSubnets()).thenReturn(subnetList);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);
        BigInteger mdata = new BigInteger(String.valueOf(0x1000000));
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
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
        )).setIngress(ncRef).setMatch(matchbuilder.build()).build());

        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(1)).transmitPacket(any(TransmitPacketInput.class));

        byte[] expectedPayload = ipv6TestUtils.buildPacket(
                "FA 16 3E 69 2C F3",                               // Destination MAC
                "50 7B 9D 78 54 F3",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class E0, no flowlabel
                "00 78",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 52 7B 9D FF FE 78 54 F3", // Source IP
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Destination IP
                "86",                                              // ICMPv6 router advertisement.
                "00",                                              // Code
                "59 41",                                           // Checksum (valid)
                "40",                                              // Current Hop Limit
                "C0",                                              // ICMPv6 RA Flags
                "11 94",                                           // Router Lifetime
                "00 01 D4 C0",                                     // Reachable time
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
                "20 01 0D B8 22 22 00 00 00 00 00 00 00 00 00 00", // Prefix
                "03",                                              // Type: Prefix Information
                "04",                                              // Option length
                "40",                                              // Prefix length
                "80",                                              // Prefix flags
                "00 27 8D 00",                                     // Valid lifetime
                "00 09 3A 80",                                     // Preferred lifetime
                "00 00 00 00",                                     // Reserved
                "20 01 0D B8 33 33 00 00 00 00 00 00 00 00 00 00"  // Prefix
        );

        verify(pktProcessService).transmitPacket(new TransmitPacketInputBuilder().setPayload(expectedPayload)
                .setNode(new NodeRef(ncId))
                .setEgress(ncRef).setIngress(ncRef).setAction(any(List.class)).build());
    }

    private void waitForPacketProcessing() throws InterruptedException {
        int timeOut = 1;
        while (timeOut < 20) {
            Thread.sleep(THREAD_WAIT_TIME);
            if (pktHandler.getPacketProcessedCounter() > counter) {
                break;
            }
            timeOut++;
        }
    }
}
