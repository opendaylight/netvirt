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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
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

@Singleton
public class NaptPacketInHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(NaptPacketInHandler.class);
    private final ConcurrentMap<String,NatPacketProcessingState> incomingPacketMap = new ConcurrentHashMap<>();
    private final NaptEventHandler naptEventHandler;
    private ExecutorService firstPacketExecutorService;
    private ExecutorService retryPacketExecutorService;

    @Inject
    public NaptPacketInHandler(NaptEventHandler naptEventHandler) {
        this.naptEventHandler = naptEventHandler;
    }

    @PostConstruct
    public void init() {
        firstPacketExecutorService = Executors.newFixedThreadPool(NatConstants.SNAT_PACKET_THEADPOOL_SIZE);
        retryPacketExecutorService = Executors.newFixedThreadPool(NatConstants.SNAT_PACKET_RETRY_THEADPOOL_SIZE);
    }

    @PreDestroy
    public void close() {
        if (firstPacketExecutorService != null) {
            firstPacketExecutorService.shutdown();
        }

        if (retryPacketExecutorService != null) {
            retryPacketExecutorService.shutdown();
        }
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

        LOG.trace("onPacketReceived : packet: {}, tableId {}", packetReceived, tableId);

        if (tableId == NwConstants.OUTBOUND_NAPT_TABLE) {
            LOG.debug("onPacketReceived : NAPTPacketInHandler Packet for Outbound NAPT Table");
            byte[] inPayload = packetReceived.getPayload();
            Ethernet ethPkt = new Ethernet();
            if (inPayload != null) {
                try {
                    ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NumBitsInAByte);
                } catch (Exception e) {
                    LOG.warn("onPacketReceived: Failed to decode Packet", e);
                    return;
                }
                if (ethPkt.getPayload() instanceof IPv4) {
                    IPv4 ipPkt = (IPv4) ethPkt.getPayload();
                    byte[] ipSrc = Ints.toByteArray(ipPkt.getSourceAddress());

                    internalIPAddress = NWUtil.toStringIpAddress(ipSrc);
                    LOG.trace("onPacketReceived : Retrieved internalIPAddress {}", internalIPAddress);
                    if (ipPkt.getPayload() instanceof TCP) {
                        TCP tcpPkt = (TCP) ipPkt.getPayload();
                        portNumber = tcpPkt.getSourcePort();
                        if (portNumber < 0) {
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("onPacketReceived : Retrieved and extracted TCP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.TCP;
                        LOG.trace("onPacketReceived : Retrieved TCP portNumber {}", portNumber);
                    } else if (ipPkt.getPayload() instanceof UDP) {
                        UDP udpPkt = (UDP) ipPkt.getPayload();
                        portNumber = udpPkt.getSourcePort();
                        if (portNumber < 0) {
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("onPacketReceived : Retrieved and extracted UDP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.UDP;
                        LOG.trace("onPacketReceived : Retrieved UDP portNumber {}", portNumber);
                    } else {
                        LOG.error("onPacketReceived : Incoming Packet is neither TCP or UDP packet");
                        return;
                    }
                } else {
                    LOG.error("onPacketReceived : Incoming Packet is not IPv4 packet");
                    return;
                }

                if (internalIPAddress != null) {
                    BigInteger metadata = packetReceived.getMatch().getMetadata().getMetadata();
                    routerId = MetaDataUtil.getNatRouterIdFromMetadata(metadata);
                    if (routerId <= 0) {
                        LOG.error("onPacketReceived : Router ID is invalid");
                        return;
                    }
                    String sourceIPPortKey = routerId + NatConstants.COLON_SEPARATOR
                            + internalIPAddress + NatConstants.COLON_SEPARATOR + portNumber;
                    if (!incomingPacketMap.containsKey(sourceIPPortKey)) {
                        incomingPacketMap.put(sourceIPPortKey,
                                new NatPacketProcessingState(System.currentTimeMillis(), -1));
                        LOG.trace("onPacketReceived : Processing new SNAT({}) Packet", sourceIPPortKey);

                        //send to Event Queue
                        NAPTEntryEvent naptEntryEvent = new NAPTEntryEvent(internalIPAddress, portNumber, routerId,
                            operation, protocol, packetReceived, false, null);
                        LOG.info("onPacketReceived : First Packet IN Queue Size : {}",
                                ((ThreadPoolExecutor)firstPacketExecutorService).getQueue().size());
                        firstPacketExecutorService.execute(() -> naptEventHandler.handleEvent(naptEntryEvent));
                    } else {
                        LOG.trace("onPacketReceived : SNAT({}) Packet already processed.", sourceIPPortKey);
                        NAPTEntryEvent naptEntryEvent = new NAPTEntryEvent(internalIPAddress, portNumber, routerId,
                            operation, protocol, packetReceived, true, null);
                        LOG.info("onPacketReceived : Retry Packet IN Queue Size : {}",
                                ((ThreadPoolExecutor)retryPacketExecutorService).getQueue().size());
                        retryPacketExecutorService.execute(() -> {
                            NatPacketProcessingState state = incomingPacketMap.get(sourceIPPortKey);
                            long firstPacketInTime = state.getFirstPacketInTime();
                            if (System.currentTimeMillis() - firstPacketInTime > 4000) {
                                LOG.error("onPacketReceived : Flow not installed even after 4sec."
                                        + "Dropping SNAT ({}) Packet", sourceIPPortKey);
                                removeIncomingPacketMap(sourceIPPortKey);
                                return;
                            }
                            naptEventHandler.handleEvent(naptEntryEvent);
                        });
                    }
                } else {
                    LOG.error("onPacketReceived : Retrived internalIPAddress is NULL");
                }
            }
        } else {
            LOG.trace("onPacketReceived : Packet is not from the Outbound NAPT table");
        }
    }

    public void removeIncomingPacketMap(String sourceIPPortKey) {
        incomingPacketMap.remove(sourceIPPortKey);
        LOG.debug("removeIncomingPacketMap : sourceIPPortKey {} mapping is removed from map", sourceIPPortKey);
    }

    protected class NatPacketProcessingState {
        long firstPacketInTime;
        long flowInstalledTime;

        public NatPacketProcessingState(long firstPacketInTime, long flowInstalledTime) {
            this.firstPacketInTime = firstPacketInTime;
            this.flowInstalledTime = flowInstalledTime;
        }

        public long getFirstPacketInTime() {
            return firstPacketInTime;
        }

        public void setFirstPacketInTime(long firstPacketInTime) {
            this.firstPacketInTime = firstPacketInTime;
        }

        public long getFlowInstalledTime() {
            return flowInstalledTime;
        }

        public void setFlowInstalledTime(long flowInstalledTime) {
            this.flowInstalledTime = flowInstalledTime;
        }
    }
}
