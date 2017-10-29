/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeV4;

public interface NeutronSecurityRuleConstants {
    Class<DirectionEgress> DIRECTION_EGRESS = DirectionEgress.class;
    Class<DirectionIngress> DIRECTION_INGRESS = DirectionIngress.class;

    Short PROTOCOL_ICMP = 1;
    Short PROTOCOL_TCP = 6;
    Short PROTOCOL_UDP = 17;
    Short PROTOCOL_ICMPV6 = 58;

    Class<EthertypeV4> ETHERTYPE_IPV4 = EthertypeV4.class;

    String IPV4_ALL_NETWORK = "0.0.0.0/0";
    String IPV6_ALL_NETWORK = "::/0";

    // default acp type
    Class<? extends AclBase> ACLTYPE = Ipv4Acl.class;

}
