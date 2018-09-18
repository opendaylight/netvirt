/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;

public class PrefixWithdrawEvent {
    private protocol_type protocolType;
    private String rd;
    private String prefix;
    private int plen;
    private String nexthop;
    private String macaddress;

    public PrefixWithdrawEvent(protocol_type protocolType, String rd, String prefix, int plen, String nextHop,
                               String macaddress) {

        this.protocolType = protocolType;
        this.rd = rd;
        this.prefix = prefix;
        this.plen = plen;
        this.nexthop = nextHop;
        this.macaddress = macaddress;

    }

    @Override
    public String toString() {
        return "PrefixWithdrawEvent{"
                + "protocolType=" + protocolType
                + ", rd='" + rd + '\''
                + ", prefix='" + prefix + '\''
                + ", plen=" + plen
                + ", nexthop='" + nexthop + '\''
                + ", macaddress='" + macaddress + '\''
                + '}';
    }
}


