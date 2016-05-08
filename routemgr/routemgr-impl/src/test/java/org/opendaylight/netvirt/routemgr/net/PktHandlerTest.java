/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

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
    public void testonPacketReceivedWithInvalidPayload() throws Exception {
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
    public void testonPacketReceivedWithValidPayload() throws Exception {
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(intf);
        when(intf.getMacAddress()).thenReturn("00:01:02:03:04:05");
        when(intf.getDeviceOwner()).thenReturn(ifMgrInstance.NETWORK_ROUTER_INTERFACE);
        InstanceIdentifier<NodeConnector> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1")))
                .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId("1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);

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
