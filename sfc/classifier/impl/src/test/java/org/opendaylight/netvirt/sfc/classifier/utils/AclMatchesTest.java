/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEthBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Dscp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatch;

public class AclMatchesTest {

    private static final String MAC_SRC_STR = "11:22:33:44:55:66";
    private static final String MAC_DST_STR = "66:55:44:33:22:11";
    private static final String IPV4_DST_STR = "10.1.2.0/24";
    private static final String IPV4_SRC_STR = "10.1.2.3/32";
    private static final String IPV6_DST_STR = "2001:DB8:AC10:FE01::/64";
    private static final String IPV6_SRC_STR = "2001:db8:85a3:7334::/64";
    private static final int TCP_SRC_LOWER_PORT = 1234;
    private static final int TCP_SRC_UPPER_PORT = 2345;
    private static final int TCP_DST_LOWER_PORT = 80;
    private static final int TCP_DST_UPPER_PORT = 800;
    private static final int UDP_SRC_LOWER_PORT = 90;
    private static final int UDP_SRC_UPPER_PORT = 900;
    private static final int UDP_DST_LOWER_PORT = 90;
    private static final int UDP_DST_UPPER_PORT = 900;
    private static final short DSCP_VALUE = (short) 42;


    @Test
    public void buildEthMatchTest() {
        AceEthBuilder aceEthBuilder = new AceEthBuilder();
        aceEthBuilder.setDestinationMacAddress(new MacAddress(MAC_DST_STR));
        aceEthBuilder.setSourceMacAddress(new MacAddress(MAC_SRC_STR));

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceEthBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // The ethernet match should be there with src/dst values
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetSource().getAddress().getValue(), MAC_SRC_STR);
        assertEquals(ethMatch.getEthernetDestination().getAddress().getValue(), MAC_DST_STR);

        // The rest should be null
        assertNull(matchBuilder.getIpMatch());
        assertNull(matchBuilder.getLayer3Match());
        assertNull(matchBuilder.getLayer4Match());
    }

    @Test
    public void buildIpv4MatchTest() {
        AceIpv4Builder aceIpv4 = new AceIpv4Builder();
        aceIpv4.setDestinationIpv4Network(new Ipv4Prefix(IPV4_DST_STR));
        aceIpv4.setSourceIpv4Network(new Ipv4Prefix(IPV4_SRC_STR));

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(aceIpv4.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // The layer3 match should be there with src/dst values
        Ipv4Match l3 = (Ipv4Match) matchBuilder.getLayer3Match();
        assertNotNull(l3);
        assertEquals(l3.getIpv4Destination().getValue().toString(), IPV4_DST_STR);
        assertEquals(l3.getIpv4Source().getValue().toString(), IPV4_SRC_STR);

        // There should be an IPv4 etherType set
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

        // The rest should be null
        assertNull(matchBuilder.getIpMatch());
        assertNull(matchBuilder.getLayer4Match());
    }

    @Test
    public void buildIpv4TcpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(TCP_SRC_LOWER_PORT));
        srcPortRange.setUpperPort(new PortNumber(TCP_SRC_UPPER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(TCP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(TCP_DST_UPPER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // There should be an IPv4 etherType set
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

        // Make sure its TCP
        IpMatch ipMatch = matchBuilder.getIpMatch();
        assertNotNull(ipMatch);
        assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

        // Currently ranges arent supported, only the lower port is used
        TcpMatch tcpMatch = (TcpMatch) matchBuilder.getLayer4Match();
        assertEquals(tcpMatch.getTcpSourcePort().getValue(), Integer.valueOf(TCP_SRC_LOWER_PORT));
        assertEquals(tcpMatch.getTcpDestinationPort().getValue(), Integer.valueOf(TCP_DST_LOWER_PORT));

        // The layer3 match should be null
        assertNull(matchBuilder.getLayer3Match());
    }

    @Test
    public void buildIpv4UdpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(UDP_SRC_LOWER_PORT));
        srcPortRange.setUpperPort(new PortNumber(UDP_SRC_UPPER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(UDP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(UDP_DST_UPPER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // There should be an IPv4 etherType set
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

        // Make sure its UDP
        IpMatch ipMatch = matchBuilder.getIpMatch();
        assertNotNull(ipMatch);
        assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

        // Currently ranges arent supported, only the lower port is used
        UdpMatch udpMatch = (UdpMatch) matchBuilder.getLayer4Match();
        assertEquals(udpMatch.getUdpSourcePort().getValue(), Integer.valueOf(UDP_SRC_LOWER_PORT));
        assertEquals(udpMatch.getUdpDestinationPort().getValue(), Integer.valueOf(UDP_DST_LOWER_PORT));

        // The layer3 match should be null
        assertNull(matchBuilder.getLayer3Match());
    }

    @Test
    public void buildIpv4DscpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setDscp(new Dscp(DSCP_VALUE));

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // There should be an IPv4 etherType set
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

        // Check the DSCP value
        IpMatch ipMatch = matchBuilder.getIpMatch();
        assertNotNull(ipMatch);
        assertEquals(ipMatch.getIpDscp().getValue(), Short.valueOf(DSCP_VALUE));

        // The rest should be null
        assertNull(matchBuilder.getLayer3Match());
        assertNull(matchBuilder.getLayer4Match());
    }

    @Test
    public void buildIpv6MatchTest() {
        AceIpv6Builder aceIpv6 = new AceIpv6Builder();
        aceIpv6.setDestinationIpv6Network(new Ipv6Prefix(IPV6_DST_STR));
        aceIpv6.setSourceIpv6Network(new Ipv6Prefix(IPV6_SRC_STR));

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(aceIpv6.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        // The layer3 match should be there with src/dst values
        Ipv6Match l3 = (Ipv6Match) matchBuilder.getLayer3Match();
        assertNotNull(l3);
        assertEquals(l3.getIpv6Destination().getValue().toString(), IPV6_DST_STR);
        assertEquals(l3.getIpv6Source().getValue().toString(), IPV6_SRC_STR);

        // There should be an IPv6 etherType set
        EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
        assertNotNull(ethMatch);
        assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV6));

        // The rest should be null
        assertNull(matchBuilder.getIpMatch());
        assertNull(matchBuilder.getLayer4Match());
    }

}
