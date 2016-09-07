/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.it;

import java.util.Random;
import java.util.UUID;

public class PortInfo {
    public String id;
    public String name;
    public String ip;
    public String ipPfx;
    public String mac;
    public long ofPort;
    public String macPfx = "f4:00:00:0f:00:";
    private NeutronPort neutronPort;
    protected int ovsInstance;

    PortInfo(int ovsInstance, long ofPort, String ipPfx) {
        this.ovsInstance = ovsInstance;
        this.ofPort = ofPort;
        this.ipPfx = ipPfx;
        this.ip = ipFor(ipPfx, ofPort);
        this.mac = macFor(ofPort);
        this.id = UUID.randomUUID().toString();
        this.name = "tap" + id.substring(0, 11);
    }

    public void setNeutronPort(NeutronPort neutronPort) {
        this.neutronPort = neutronPort;
    }

    public NeutronPort getNeutronPort() {
        return neutronPort;
    }

    /**
     * Get the mac address for the n'th port created on this network (starts at 1).
     *
     * @param portNum index of port created
     * @return the mac address
     */
    public String macFor(long portNum) {
        //for router interface use a random number, because we could have multiple interfaces with the same "portNum".
        if (portNum == NetvirtITConstants.GATEWAY_SUFFIX) {
            Random rn = new Random(System.currentTimeMillis());
            portNum = rn.nextInt(10) + 100;
        }
        return macPfx + String.format("%02x", portNum);
    }

    /**
     * Get the ip address for the n'th port created on this network (starts at 1).
     *
     * @param portNum index of port created
     * @return the mac address
     */
    public String ipFor(String ipPfx, long portNum) {
        return ipPfx + portNum;
    }

    @Override
    public String toString() {
        return "PortInfo [name=" + name
                + ", ofPort=" + ofPort
                + ", id=" + id
                + ", mac=" + mac
                + ", ip=" + ip
                + "]";
    }
}
