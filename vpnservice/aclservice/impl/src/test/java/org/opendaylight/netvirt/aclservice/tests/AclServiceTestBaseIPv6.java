/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
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

    static String IPv6_PREFIX_1 = "2001:db8::1/64";
    static String IPv6_PREFIX_2 = "2001:db8::2/64";
    static String IPv6_PREFIX_3 = "2001:db8::3/64";

    @Override
    protected Matches newMatch(int srcLowerPort, int srcUpperPort, int destLowerPort, int destupperPort,
            boolean srcRemoteIpPrefix, boolean dstRemoteIpPrefix, short protocol) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (destLowerPort != -1) {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destLowerPort));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destupperPort));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
        if (srcRemoteIpPrefix) {
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(AclConstants.IPV6_ALL_NETWORK));
        }
        if (dstRemoteIpPrefix) {
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
    public void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1 ,true);
        newElanInterface(ELAN, PORT_2, true);
        newElanInterface(ELAN, PORT_3, true);
        newAllowedAddressPair(PORT_1, Arrays.asList(SG_UUID_1), IPv6_PREFIX_1, PORT_MAC_1);
        newAllowedAddressPair(PORT_2, Arrays.asList(SG_UUID_1), IPv6_PREFIX_2, PORT_MAC_2);
        newAllowedAddressPair(PORT_3, Arrays.asList(SG_UUID_1, SG_UUID_2), IPv6_PREFIX_3, PORT_MAC_3);
    }
}
