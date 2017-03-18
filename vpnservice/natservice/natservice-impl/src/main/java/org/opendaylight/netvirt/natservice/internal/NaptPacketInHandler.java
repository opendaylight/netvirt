/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.primitives.Ints;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.mdsalutil.packet.TCP;
import org.opendaylight.genius.mdsalutil.packet.UDP;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NaptPacketInHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(NaptPacketInHandler.class);
    public static final HashMap<String,NatPacketProcessingState> INCOMING_PACKET_MAP = new HashMap<>();
    private final NaptEventHandler naptEventHandler;
    private ExecutorService executorService;

    public NaptPacketInHandler(NaptEventHandler naptEventHandler) {
        this.naptEventHandler = naptEventHandler;
    }

    public void init() {
        executorService = Executors.newFixedThreadPool(NatConstants.PACKET_IN_THEAD_POOL_SIZE);
    }

    public void close() {
        executorService.shutdown();
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPacketReceived(PacketReceived packetReceived) {
        String internalIPAddress = "";
        int portNumber = 0;
        long routerId = 0L;
        NAPTEntryEvent.Operation operation = NAPTEntryEvent.Operation.ADD;
        NAPTEntryEvent.Protocol protocol;

        Short tableId = packetReceived.getTableId().getValue();

        LOG.trace("packet: {}, tableId {}", packetReceived, tableId);

        if (tableId == NwConstants.OUTBOUND_NAPT_TABLE) {
            LOG.debug("NAT Service : NAPTPacketInHandler Packet for Outbound NAPT Table");
            byte[] inPayload = packetReceived.getPayload();
            Ethernet ethPkt = new Ethernet();
            if (inPayload != null) {
                try {
                    ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NumBitsInAByte);
                } catch (Exception e) {
                    LOG.warn("Failed to decode Packet", e);
                    return;
                }
                if (ethPkt.getPayload() instanceof IPv4) {
                    IPv4 ipPkt = (IPv4) ethPkt.getPayload();
                    byte[] ipSrc = Ints.toByteArray(ipPkt.getSourceAddress());

                    internalIPAddress = NWUtil.toStringIpAddress(ipSrc);
                    LOG.trace("Retrieved internalIPAddress {}", internalIPAddress);
                    if (ipPkt.getPayload() instanceof TCP) {
                        TCP tcpPkt = (TCP) ipPkt.getPayload();
                        portNumber = tcpPkt.getSourcePort();
                        if (portNumber < 0) {
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("Retrieved and extracted TCP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.TCP;
                        LOG.trace("Retrieved TCP portNumber {}", portNumber);
                    } else if (ipPkt.getPayload() instanceof UDP) {
                        UDP udpPkt = (UDP) ipPkt.getPayload();
                        portNumber = udpPkt.getSourcePort();
                        if (portNumber < 0) {
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("Retrieved and extracted UDP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.UDP;
                        LOG.trace("Retrieved UDP portNumber {}", portNumber);
                    } else {
                        LOG.error("Incoming Packet is neither TCP or UDP packet");
                        return;
                    }
                } else {
                    LOG.error("Incoming Packet is not IPv4 packet");
                    return;
                }

                if (internalIPAddress != null) {
                    String sourceIPPortKey = internalIPAddress + NatConstants.NAT_COLON + portNumber;
                    LOG.debug("NAT Service : sourceIPPortKey {} mapping maintained in the map", sourceIPPortKey);
                    BigInteger metadata = packetReceived.getMatch().getMetadata().getMetadata();
                    routerId = MetaDataUtil.getNatRouterIdFromMetadata(metadata);
                    if (routerId <= 0) {
                        LOG.error("NAT Service : Router ID is invalid");
                        return;
                    }
                    if (!INCOMING_PACKET_MAP.containsKey(sourceIPPortKey)) {
                        INCOMING_PACKET_MAP.put(sourceIPPortKey,
                                new NatPacketProcessingState(NatConstants.PACKET_INPROGRESS, 1));
                        LOG.trace("NAT Service : Processing new Packet");

                        //send to Event Queue
                        LOG.trace("NAT Service : Creating NaptEvent for routerId {} and sourceIp {} and Port {}",
                            routerId, internalIPAddress, portNumber);
                        NAPTEntryEvent naptEntryEvent = new NAPTEntryEvent(internalIPAddress, portNumber, routerId,
                            operation, protocol, packetReceived, false);
                        LOG.trace("NAT Service : Packet IN Queue Size : {}",
                                ((ThreadPoolExecutor)executorService).getQueue().size());
                        executorService.execute(new Runnable() {
                            public void run() {
                                naptEventHandler.handleEvent(naptEntryEvent);
                            }
                        });
                        LOG.trace("NAT Service : PacketInHandler sent event to NaptEventHandler");
                    } else {
                        LOG.trace("NAT Service : Packet already processed");
                        NAPTEntryEvent naptEntryEvent = new NAPTEntryEvent(internalIPAddress, portNumber, routerId,
                            operation, protocol, packetReceived, true);
                        LOG.trace("NAT Service : Packet IN Queue Size : {}",
                                ((ThreadPoolExecutor)executorService).getQueue().size());
                        executorService.execute(new Runnable() {
                            public void run() {
                                int count =0;
                                NatPacketProcessingState packetState = INCOMING_PACKET_MAP.get(sourceIPPortKey);
                                if (packetState != null && packetState.getFlowRetryCount() >= 3) {
                                    LOG.warn("NAT Service : Flow Installtion has been tried 3 times and "
                                            + "Been failed for NAT Session {}. Dropping packet", sourceIPPortKey);
                                    return;
                                }
                                while(count < 3) {
                                    if (packetState != null
                                            && NatConstants.PACKET_INPROGRESS.equals(packetState.getStatus())) {
                                        LOG.debug("NAT Service : First Packet processing in Progress."
                                                + "Wait 50ms for flows to get installed by first packet");
                                        try {
                                            Thread.sleep(50);
                                        } catch (InterruptedException ex) {
                                            LOG.error("NAT Service : Exception occured during sleep");
                                        }
                                    } else {
                                        break;
                                    }
                                    count++;
                                    packetState = INCOMING_PACKET_MAP.get(sourceIPPortKey);
                                }
                                if (count == 3 && packetState != null
                                        && NatConstants.PACKET_INPROGRESS.equals(packetState.getStatus())) {
                                    LOG.debug("NAT Service : Maximum wait(150ms) done by 2nd packet."
                                            + "Will try to reinstall the flow");
                                    naptEntryEvent.setPktProcessed(false);
                                    packetState.setFlowRetryCount(packetState.getFlowRetryCount() + 1);
                                }
                                naptEventHandler.handleEvent(naptEntryEvent);
                            }
                        });
                        LOG.trace("NAT Service : PacketInHandler sent event to NaptEventHandler");
                    }
                } else {
                    LOG.error("Nullpointer exception in retrieving internalIPAddress");
                }
            }
        } else {
            LOG.trace("Packet is not from the Outbound NAPT table");
        }
    }

    public void removeIncomingPacketMap(String sourceIPPortKey) {
        INCOMING_PACKET_MAP.remove(sourceIPPortKey);
        LOG.debug("NAT Service : sourceIPPortKey {} mapping is removed from map", sourceIPPortKey);
    }

    protected class NatPacketProcessingState {

        private String status;
        private int flowRetryCount;

        NatPacketProcessingState(String status, int flowRetryCount) {
            this.status = status;
            this.flowRetryCount = flowRetryCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getFlowRetryCount() {
            return flowRetryCount;
        }

        public void setFlowRetryCount(int flowRetryCount) {
            this.flowRetryCount = flowRetryCount;
        }
    }
}
