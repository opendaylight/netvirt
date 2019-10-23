/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

public class PeerDownEvent {
    private String ipAddress;
    private Long asNumber;

    public PeerDownEvent(String ipAddress,Long asNumber) {
        this.ipAddress = ipAddress;
        this.asNumber = asNumber;
    }

    @Override
    public String toString() {
        return "PeerDownEvent{"
                + "ipAddress='" + ipAddress + '\''
                + ", asNumber=" + asNumber
                + '}';
    }
}
