/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.netvirt.natservice.internal.NaptPacketInHandler.NatPacketProcessingState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

public class NAPTEntryEvent {
    private final String ipAddress;
    private final int portNumber;
    private final Long routerId;
    private final Operation op;
    private final Protocol protocol;
    private final PacketReceived packetReceived;
    private final boolean pktProcessed;
    private final long objectCreationTime;
    private final NatPacketProcessingState state;

    NAPTEntryEvent(String ipAddress, int portNumber, Long routerId, Operation op, Protocol protocol,
            PacketReceived packetReceived, boolean pktProcessed, NatPacketProcessingState state) {
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.routerId = routerId;
        this.op = op;
        this.protocol = protocol;
        this.packetReceived = packetReceived;
        this.pktProcessed = pktProcessed;
        this.state = state;
        this.objectCreationTime = System.currentTimeMillis();
    }

    public PacketReceived getPacketReceived() {
        return packetReceived;
    }

    public boolean isPktProcessed() {
        return pktProcessed;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public Long getRouterId() {
        return routerId;
    }

    public Operation getOperation() {
        return op;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public long getObjectCreationTime() {
        return objectCreationTime;
    }

    public NatPacketProcessingState getState() {
        return state;
    }

    public enum Operation {
        ADD, DELETE
    }

    public enum Protocol {
        TCP, UDP
    }
}
