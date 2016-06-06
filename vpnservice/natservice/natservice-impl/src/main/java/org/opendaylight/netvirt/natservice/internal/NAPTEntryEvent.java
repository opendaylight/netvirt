/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;


public class NAPTEntryEvent {
    private String ipAddress;
    private int portNumber;
    private Long routerId;
    private Operation op;
    private Protocol protocol;

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

    NAPTEntryEvent(String ipAddress, int portNumber, Long routerId, Operation op, Protocol protocol){
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.routerId = routerId;
        this.op = op;
        this.protocol = protocol;
    }

    public enum Operation{
        ADD, DELETE
    }

    public enum Protocol{
        TCP, UDP
    }
}
