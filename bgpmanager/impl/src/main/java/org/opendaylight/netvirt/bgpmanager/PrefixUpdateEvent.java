/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.yangtools.yang.common.Uint32;

public class PrefixUpdateEvent {
    private protocol_type protocolType;
    private String rd;
    private String prefix;
    private int plen;
    private String nexthop;
    private String macaddress;
    private Uint32 l3label;
    private Uint32 l2label;
    private String routermac;
    private af_afi afi;

    public PrefixUpdateEvent(protocol_type protocolType, String rd, String prefix, int plen,
                             String nexthop, String macaddress, Uint32 l3label, Uint32 l2label,
                             String routermac, af_afi afi) {
        this.protocolType = protocolType;
        this.rd = rd;
        this.prefix = prefix;
        this.plen = plen;
        this.nexthop = nexthop;
        this.macaddress = macaddress;
        this.l3label = l3label;
        this.l2label = l2label;
        this.routermac = routermac;
        this.afi = afi;
    }

    @Override
    public String toString() {
        return "PrefixUpdateEvent{"
                + ", protocolType=" + protocolType
                + ", rd='" + rd + '\''
                + ", prefix='" + prefix + '\''
                + ", plen=" + plen
                + ", nexthop='" + nexthop + '\''
                + ", macaddress='" + macaddress + '\''
                + ", l3label=" + l3label
                + ", l2label=" + l2label
                + ", routermac='" + routermac + '\''
                + ", afi=" + afi
                + '}';
    }
}

