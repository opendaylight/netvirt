/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.ipv6util.api.decoders.Ipv6NaDecoder;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.netvirt.ipv6service.api.IIpv6PacketListener;
import org.opendaylight.netvirt.ipv6service.utils.IpV6NAConfigHelper;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.NeighborAdvertisePacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.NeighborAdvertisePacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.PacketMetadataBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TableId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;

public class Ipv6PktHandlerTest {
    private PacketProcessingService pktProcessService;
    private Ipv6PktHandler pktHandler;
    private IfMgr ifMgrInstance;
    private IIpv6PacketListener ipv6PktListener;
    private long counter;
    private static final int THREAD_WAIT_TIME = 100;
    private Ipv6TestUtils ipv6TestUtils;
    private Ipv6ServiceUtils ipv6ServiceUtils;
    private IpV6NAConfigHelper ipV6NAConfigHelper;

    @Before
    public void initTest() {
        pktProcessService = Mockito.mock(PacketProcessingService.class);
        ifMgrInstance = Mockito.mock(IfMgr.class);

        ipv6ServiceUtils = Mockito.mock(Ipv6ServiceUtils.class);
        ipV6NAConfigHelper = Mockito.mock(IpV6NAConfigHelper.class);
        pktHandler = new Ipv6PktHandler(pktProcessService, ifMgrInstance, ipv6ServiceUtils, ipV6NAConfigHelper,
                ipv6PktListener);
        ipv6PktListener = Mockito.mock(IIpv6PacketListener.class);
        pktHandler = new Ipv6PktHandler(pktProcessService, ifMgrInstance, ipv6ServiceUtils, ipV6NAConfigHelper,
                ipv6PktListener);
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
        Uint64 mdata = Uint64.valueOf(0x1000000);
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
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
        )).setMatch(matchbuilder.build()).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //unavailable ip
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
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
        )).setMatch(matchbuilder.build()).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithInvalidPayload() throws Exception {
        // incorrect checksum in Router Solicitation
        Uint64 mdata = Uint64.valueOf(0x1000000);
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
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
        )).setMatch(matchbuilder.build()).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        // Request from an unknown port (i.e., unknown MAC Address)
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
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
        )).setMatch(matchbuilder.build()).build());
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
        when(pktProcessService.transmitPacket(any())).thenReturn(Mockito.mock(ListenableFuture.class));

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

        Uint64 mdata = Uint64.valueOf(0x1000000);
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
        when(intf.getDpId()).thenReturn(Uint64.valueOf(1));
        when(intf.getIntfUUID()).thenReturn(Uuid.getDefaultInstance("ddec9dba-d831-4ad7-84b9-00d7f65f052f"));
        when(intf.getMacAddress()).thenReturn("fa:16:3e:4e:18:0c");
        when(intf.getMtu()).thenReturn(1400);
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        List<Action> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE).buildAction());
        when(ifMgrInstance.getEgressAction(any())).thenReturn(actions);
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);
        when(ifMgrInstance.getRouterV6InterfaceForNetwork(any())).thenReturn(intf);
        when(pktProcessService.transmitPacket(any())).thenReturn(Mockito.mock(ListenableFuture.class));

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8::1"));

        VirtualSubnet v6Subnet = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix(new Ipv6Prefix("2001:db8::/64")))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_SLAAC).ipv6RAMode(Ipv6ServiceConstants.IPV6_SLAAC).build();

        VirtualRouter virtualRouter = VirtualRouter.builder().build();
        v6Subnet.setRouter(virtualRouter);

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet);
        when(intf.getSubnets()).thenReturn(subnetList);

        Uint64 dpnId = Uint64.valueOf("1");
        NodeConnectorRef ncRef = MDSALUtil.getDefaultNodeConnRef(dpnId);
        Uint64 mdata = Uint64.valueOf(0x1000000);
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
                "00 40",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 4E 18 0C", // Source IP
                "FE 80 00 00 00 00 00 00 F8 16 3E FF FE 69 2C F3", // Destination IP
                "86",                                              // ICMPv6 router advertisement.
                "00",                                              // Code
                "11 2F",                                           // Checksum (valid)
                "40",                                              // Current Hop Limit
                "00",                                              // ICMPv6 RA Flags
                "11 94",                                           // Router Lifetime
                "00 01 D4 C0",                                     // Reachable time
                "00 00 00 00",                                     // Retransmission time.
                "01",                                              // Type: Source Link-Layer Option
                "01",                                              // Option length
                "FA 16 3E 4E 18 0C",                               // Source Link layer address
                "05",                                              // Type: MTU Option
                "01",                                              // Option length
                "00 00",                                           // Reserved
                "00 00 05 78",                                     // MTU
                "03",                                              // Type: Prefix Information
                "04",                                              // Option length
                "40",                                              // Prefix length
                "C0",                                              // Prefix flags
                "00 27 8D 00",                                     // Valid lifetime
                "00 09 3A 80",                                     // Preferred lifetime
                "00 00 00 00",                                     // Reserved
                "20 01 0D B8 00 00 00 00 00 00 00 00 00 00 00 00"  // Prefix
        );
        TransmitPacketInput transmitPacketInput = MDSALUtil.getPacketOut(actions, expectedPayload, dpnId);
        verify(pktProcessService).transmitPacket(transmitPacketInput);
    }

    @Test
    public void testonPacketReceivedRouterSolicitationWithMultipleSubnets() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getDpId()).thenReturn(Uint64.valueOf(1));
        when(intf.getMacAddress()).thenReturn("50:7B:9D:78:54:F3");
        when(intf.getIntfUUID()).thenReturn(Uuid.getDefaultInstance("ddec9dba-d831-4ad7-84b9-00d7f65f052f"));
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        when(ifMgrInstance.getRouterV6InterfaceForNetwork(any())).thenReturn(intf);
        List<Action> actions = new ArrayList<>();
        actions.add(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE).buildAction());
        when(ifMgrInstance.getEgressAction(any())).thenReturn(actions);
        when(pktProcessService.transmitPacket(any())).thenReturn(Mockito.mock(ListenableFuture.class));

        IpAddress gwIpAddress = Mockito.mock(IpAddress.class);
        when(gwIpAddress.getIpv4Address()).thenReturn(null);
        when(gwIpAddress.getIpv6Address()).thenReturn(new Ipv6Address("2001:db8:1111::1"));

        VirtualSubnet v6Subnet1 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix(new Ipv6Prefix("2001:db8:1111::/64")))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_SLAAC).ipv6RAMode(Ipv6ServiceConstants.IPV6_SLAAC).build();
        VirtualRouter virtualRouter = VirtualRouter.builder().build();
        v6Subnet1.setRouter(virtualRouter);

        VirtualSubnet v6Subnet2 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix(new Ipv6Prefix("2001:db8:2222::/64")))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS)
                .ipv6RAMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS).build();
        v6Subnet2.setRouter(virtualRouter);

        VirtualSubnet v6Subnet3 = VirtualSubnet.builder().gatewayIp(gwIpAddress)
                .subnetCidr(new IpPrefix(new Ipv6Prefix("2001:db8:3333::/64")))
                .ipv6AddressMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL)
                .ipv6RAMode(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL).build();
        v6Subnet3.setRouter(virtualRouter);

        List<VirtualSubnet> subnetList = new ArrayList<>();
        subnetList.add(v6Subnet1);
        subnetList.add(v6Subnet2);
        subnetList.add(v6Subnet3);
        when(intf.getSubnets()).thenReturn(subnetList);

        Uint64 dpnId = Uint64.valueOf("1");
        NodeConnectorRef ncRef = MDSALUtil.getDefaultNodeConnRef(dpnId);
        Uint64 mdata = Uint64.valueOf(0x1000000);
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

        TransmitPacketInput transmitPacketInput = MDSALUtil.getPacketOut(actions, expectedPayload, dpnId);
        verify(pktProcessService).transmitPacket(transmitPacketInput);
    }

    @Test
    public void testonPacketReceivedNeighborAdvertisementWithValidPayload() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getNetworkID()).thenReturn(new Uuid("eeec9dba-d831-4ad7-84b9-00d7f65f0555"));
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

        Uint64 mdata = Uint64.valueOf(0x1000000);
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);
        byte[] data = ipv6TestUtils.buildPacket(
                "FA 16 3E F7 69 4E",                               // Destination MAC
                "FA 16 3E A9 38 94",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class 0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Source IP
                "10 01 0D B8 00 00 00 02 F8 16 3E FF FE F7 69 4E", // Destination IP
                "88",                                              // ICMPv6 neighbor advertisement
                "00",                                              // Code
                "C9 9F",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Target
                "02",                                              // ICMPv6 Option: Target Link Layer Address
                "01",                                              // Length
                "FA 16 3E A9 38 94"                                // Link Layer Address
        );
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(data).setIngress(ncRef)
                .setMatch(matchbuilder.build()).setTableId(new TableId((short) 45)).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();

        NeighborAdvertisePacket naPdu = new Ipv6NaDecoder(data).decode();
        NeighborAdvertisePacket naPacket =
                new NeighborAdvertisePacketBuilder(naPdu)
                        .addAugmentation(new PacketMetadataBuilder().setOfTableId((long) 45)
                                .setMetadata(mdata).setInterface("ddec9dba-d831-4ad7-84b9-00d7f65f052f").build())
                        .build();
        verify(ipv6PktListener).onNaReceived(naPacket);
    }

    @Test
    public void testonPacketReceivedNeighborAdvertisementWithInvalidPayload() throws Exception {
        //incorrect checksum
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(intf.getNetworkID()).thenReturn(new Uuid("eeec9dba-d831-4ad7-84b9-00d7f65f0555"));
        when(ifMgrInstance.getInterfaceNameFromTag(anyLong())).thenReturn("ddec9dba-d831-4ad7-84b9-00d7f65f052f");
        when(ifMgrInstance.obtainV6Interface(any())).thenReturn(intf);

        InstanceIdentifier<Node> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

        Uint64 mdata = Uint64.valueOf(0x1000000);
        Metadata metadata = new MetadataBuilder().setMetadata(mdata).build();
        MatchBuilder matchbuilder = new MatchBuilder().setMetadata(metadata);

        // incorrect checksum
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
                "FA 16 3E F7 69 4E",                               // Destination MAC
                "FA 16 3E A9 38 94",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class 0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Source IP
                "10 01 0D B8 00 00 00 02 F8 16 3E FF FE F7 69 4E", // Destination IP
                "88",                                              // ICMPv6 neighbor advertisement
                "00",                                              // Code
                "C9 9A",                                           // Checksum (invalid)
                "00 00 00 00",                                     // ICMPv6 message body
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Target
                "02",                                              // ICMPv6 Option: Target Link Layer Address
                "01",                                              // Length
                "FA 16 3E A9 38 94"                                // Link Layer Address
        )).setIngress(ncRef).setMatch(matchbuilder.build()).setTableId(new TableId((short) 45)).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(ipv6PktListener, times(0)).onNaReceived(any(NeighborAdvertisePacket.class));

        // incorrect ICMPv6 type
        pktHandler.onPacketReceived(new PacketReceivedBuilder().setPayload(ipv6TestUtils.buildPacket(
                "FA 16 3E F7 69 4E",                               // Destination MAC
                "FA 16 3E A9 38 94",                               // Source MAC
                "86 DD",                                           // IPv6
                "60 00 00 00",                                     // Version 6, traffic class 0, no flowlabel
                "00 20",                                           // Payload length
                "3A",                                              // Next header is ICMPv6
                "FF",                                              // Hop limit
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Source IP
                "10 01 0D B8 00 00 00 02 F8 16 3E FF FE F7 69 4E", // Destination IP
                "87",                                              // ICMPv6 neighbor solicitation (invalid)
                "00",                                              // Code
                "C9 9F",                                           // Checksum (valid)
                "00 00 00 00",                                     // ICMPv6 message body
                "20 01 0D B8 00 00 00 02 00 00 00 00 00 00 11 11", // Target
                "02",                                              // ICMPv6 Option: Target Link Layer Address
                "01",                                              // Length
                "FA 16 3E A9 38 94"                                // Link Layer Address
        )).setIngress(ncRef).setMatch(matchbuilder.build()).setTableId(new TableId((short) 45)).build());
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(ipv6PktListener, times(0)).onNaReceived(any(NeighborAdvertisePacket.class));
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
