/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;

public abstract class AclServiceTestBaseIPv6 extends AclServiceTestBase {

    static String IPv6_PREFIX_1 = "2001:db8:a0b:12f0::1/128";
    static String IPv6_PREFIX_2 = "2001:db8:a0b:12f0::2/128";
    static String IPv6_PREFIX_3 = "2001:db8:a0b:12f0::3/128";
    static String IP_100_PREFIX = "2001:db8:a0b:12f0::101/128";
    static String IP_101_PREFIX = "2001:db8:a0b:12f0::101/128";

    @Override
    protected Matches newMatch(int srcLowerPort, int srcUpperPort, int destLowerPort, int destupperPort,
            int srcRemoteIpPrefix, int dstRemoteIpPrefix, short protocol) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (destLowerPort != -1) {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destLowerPort));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destupperPort));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
        if (srcRemoteIpPrefix == AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED) {
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(AclConstants.IPV6_ALL_NETWORK));
        }
        if (dstRemoteIpPrefix == AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED) {
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(AclConstants.IPV6_ALL_NETWORK));
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        aceIpBuilder.setAceIpVersion(aceIpv6Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();

    }

    @Override
    protected void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1 ,true);
        newElanInterface(ELAN, PORT_2, true);
        newElanInterface(ELAN, PORT_3, true);

        AAP_PORT_1 = buildAap(IPv6_PREFIX_1, PORT_MAC_1);
        AAP_PORT_2 = buildAap(IPv6_PREFIX_2, PORT_MAC_2);
        AAP_PORT_3 = buildAap(IPv6_PREFIX_3, PORT_MAC_3);
        AAP_PORT_100 = buildAap(IP_100_PREFIX, PORT_MAC_2);
        AAP_PORT_101 = buildAap(IP_101_PREFIX, "0D:AA:D8:42:30:A4");

    }
}
