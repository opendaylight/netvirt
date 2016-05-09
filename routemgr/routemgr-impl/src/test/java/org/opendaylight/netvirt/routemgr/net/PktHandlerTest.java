/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import org.junit.Test;
import org.mockito.Mockito;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

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
        byte pktArray[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                           0x05,(byte)0x80,0x00,0x6e,0x00,0x00,0x00,0x00,0x18,0x3a,(byte)0xff,
                           0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                           0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00};
        PacketReceived packet = new PacketReceivedBuilder().setPayload(pktArray).build();
        pktHandler.onPacketReceived(packet);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //invalid ipv6 header
        byte pktArray1[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                            0x05,(byte)0x86,(byte)0xdd,0x6e,0x00,0x00,0x00,0x00,0x18,0x33,
                            (byte)0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00};
        packet = new PacketReceivedBuilder().setPayload(pktArray1).build();
        pktHandler.onPacketReceived(packet);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //invalid icmpv6 header
        byte pktArray2[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                            0x05,(byte)0x86,(byte)0xdd,0x6e,0x00,0x00,0x00,0x00,0x18,0x3a,
                            (byte)0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00,(byte)0x85,
                            0x00,0x67,(byte)0x3c,0x00,0x00,0x00,0x00,(byte)0xfe,(byte)0x80,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xc0,0x00,0x54,(byte)0xff,(byte)0xfe,
                            (byte)0xf5,0x00,0x00};
        packet = new PacketReceivedBuilder().setPayload(pktArray2).build();
        pktHandler.onPacketReceived(packet);
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedWithInvalidPayload() throws Exception {
        //incorrect checksum
        byte pktArray[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                            0x05,(byte)0x86,(byte)0xdd,0x6e,0x00,0x00,0x00,0x00,0x18,0x3a,
                            (byte)0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00,(byte)0x87,
                            0x00,0x67,(byte)0x3e,0x00,0x00,0x00,0x00,(byte)0xfe,(byte)0x80,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xc0,0x00,0x54,(byte)0xff,(byte)0xfe,
                            (byte)0xf5,0x00,0x00};
        PacketReceived packet = new PacketReceivedBuilder().setPayload(pktArray).build();
        pktHandler.onPacketReceived(packet);
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));

        //unavailable ip
        byte pktArray1[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                            0x05,(byte)0x86,(byte)0xdd,0x6e,0x00,0x00,0x00,0x00,0x18,0x3a,
                            (byte)0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00,(byte)0x87,
                            0x00,0x67,(byte)0x3c,0x00,0x00,0x00,0x00,(byte)0xfe,(byte)0x80,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xc0,0x00,0x54,(byte)0xff,(byte)0xfe,
                            (byte)0xf5,0x00,0x00};
        packet = new PacketReceivedBuilder().setPayload(pktArray1).build();
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(null);
        pktHandler.onPacketReceived(packet);
        //wait on this thread until the async job is completed in the packet handler.
        waitForPacketProcessing();
        verify(pktProcessService, times(0)).transmitPacket(any(TransmitPacketInput.class));
    }

    @Test
    public void testonPacketReceivedWithValidPayload() throws Exception {
        byte pktArray[] = {0x33,0x33,(byte)0xff,(byte)0xf5,0x00,0x00,0x00,0x01,0x02,0x03,0x04,
                            0x05,(byte)0x86,(byte)0xdd,0x6e,0x00,0x00,0x00,0x00,0x18,0x3a,
                            (byte)0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xff,0x02,0x00,0x00,0x00,0x00,0x00,
                            0x00,0x00,0x00,0x00,0x01,(byte)0xff,(byte)0xf5,0x00,0x00,(byte)0x87,
                            0x00,0x67,(byte)0x3c,0x00,0x00,0x00,0x00,(byte)0xfe,(byte)0x80,0x00,
                            0x00,0x00,0x00,0x00,0x00,(byte)0xc0,0x00,0x54,(byte)0xff,(byte)0xfe,
                            (byte)0xf5,0x00,0x00};
        VirtualPort intf = Mockito.mock(VirtualPort.class);
        when(ifMgrInstance.getInterfaceForAddress(any(Ipv6Address.class))).thenReturn(intf);
        when(intf.getMacAddress()).thenReturn("00:01:02:03:04:05");
        InstanceIdentifier<NodeConnector> ncId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:1")))
                .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId("1"))).build();
        NodeConnectorRef ncRef = new NodeConnectorRef(ncId);
        PacketReceived packet = new PacketReceivedBuilder().setPayload(pktArray).setIngress(ncRef).build();

        pktHandler.onPacketReceived(packet);
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
}
