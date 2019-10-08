/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEth;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEthBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Dscp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.of.tcp.dst.grouping.NxmOfTcpDst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.of.tcp.src.grouping.NxmOfTcpSrc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.of.udp.dst.grouping.NxmOfUdpDst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.of.udp.src.grouping.NxmOfUdpSrc;

public class AclMatchesTest {

    private static final String MAC_SRC_STR = "11:22:33:44:55:66";
    private static final String MAC_DST_STR = "66:55:44:33:22:11";
    private static final String IPV4_DST_STR = "10.1.2.0/24";
    private static final String IPV4_SRC_STR = "10.1.2.3/32";
    private static final String IPV6_DST_STR = "2001:DB8:AC10:FE01::/64";
    private static final String IPV6_SRC_STR = "2001:db8:85a3:7334::/64";
    private static final int TCP_SRC_LOWER_PORT = 80;
    private static final int TCP_SRC_UPPER_PORT = 82;
    private static final int TCP_DST_LOWER_PORT = 800;
    private static final int TCP_DST_UPPER_PORT = 800;
    private static final int UDP_SRC_LOWER_PORT = 90;
    private static final int UDP_SRC_UPPER_PORT = 90;
    private static final int UDP_DST_LOWER_PORT = 900;
    private static final int UDP_DST_UPPER_PORT = 902;
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
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        for (MatchBuilder matchBuilder : matchBuilds) {
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
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        for (MatchBuilder matchBuilder : matchBuilds) {
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
        assertEquals(1, matchBuilds.size());
    }

    @Test
    public void buildIpv4SrcLwrTcpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(TCP_SRC_LOWER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> srcTcpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its TCP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

            NxmOfTcpSrc tcpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpSrc();


            if (tcpSrc != null) {
                srcTcpMatches.add(tcpSrc.getPort().getValue().toJava());
                srcTcpMatches.add(tcpSrc.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, srcTcpMatches.size());
        assertTrue(srcTcpMatches.contains(TCP_SRC_LOWER_PORT));
        assertTrue(srcTcpMatches.contains(65535));
    }

    @Test
    public void buildIpv4SrcTcpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(TCP_SRC_LOWER_PORT));
        srcPortRange.setUpperPort(new PortNumber(TCP_SRC_UPPER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> srcTcpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its TCP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

            NxmOfTcpSrc tcpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpSrc();


            if (tcpSrc != null) {
                srcTcpMatches.add(tcpSrc.getPort().getValue().toJava());
                srcTcpMatches.add(tcpSrc.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(2, matchBuilds.size());
        assertEquals(4, srcTcpMatches.size());
        assertTrue(srcTcpMatches.contains(TCP_SRC_LOWER_PORT));
        assertTrue(srcTcpMatches.contains(TCP_SRC_UPPER_PORT));
        assertTrue(srcTcpMatches.contains(65535));
        assertTrue(srcTcpMatches.contains(65534));
    }

    @Test
    public void buildIpv4DstLwrTcpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(TCP_DST_LOWER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> dstTcpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its TCP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

            NxmOfTcpDst tcpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpDst();

            if (tcpDst != null) {
                dstTcpMatches.add(tcpDst.getPort().getValue().toJava());
                dstTcpMatches.add(tcpDst.getMask().toJava());
            }
            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, dstTcpMatches.size());
        assertTrue(dstTcpMatches.contains(TCP_DST_LOWER_PORT));
        assertTrue(dstTcpMatches.contains(65535));
    }

    @Test
    public void buildIpv4DstTcpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(TCP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(TCP_DST_UPPER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> dstTcpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its TCP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

            NxmOfTcpDst tcpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpDst();

            if (tcpDst != null) {
                dstTcpMatches.add(tcpDst.getPort().getValue().toJava());
                dstTcpMatches.add(tcpDst.getMask().toJava());
            }
            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, dstTcpMatches.size());
        assertTrue(dstTcpMatches.contains(TCP_DST_LOWER_PORT));
        assertTrue(dstTcpMatches.contains(TCP_DST_UPPER_PORT));
        assertTrue(dstTcpMatches.contains(65535));
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

        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> dstTcpMatches = new HashSet<>();
        Set<Integer> srcTcpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its TCP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.TCP.shortValue()));

            NxmOfTcpSrc tcpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpSrc();

            NxmOfTcpDst tcpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(1)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfTcpDst();

            if (tcpSrc != null) {
                srcTcpMatches.add(tcpSrc.getPort().getValue().toJava());
                srcTcpMatches.add(tcpSrc.getMask().toJava());
            }

            if (tcpDst != null) {
                dstTcpMatches.add(tcpDst.getPort().getValue().toJava());
                dstTcpMatches.add(tcpDst.getMask().toJava());
            }
            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(2, matchBuilds.size());
        assertEquals(4, srcTcpMatches.size());
        assertEquals(2, dstTcpMatches.size());

        assertTrue(srcTcpMatches.contains(TCP_SRC_LOWER_PORT));
        assertTrue(srcTcpMatches.contains(TCP_SRC_UPPER_PORT));
        assertTrue(srcTcpMatches.contains(65535));
        assertTrue(srcTcpMatches.contains(65534));

        assertTrue(dstTcpMatches.contains(TCP_DST_LOWER_PORT));
        assertTrue(dstTcpMatches.contains(TCP_DST_UPPER_PORT));
        assertTrue(dstTcpMatches.contains(65535));
    }

    @Test
    public void buildIpv4SrcLwrUdpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(UDP_SRC_LOWER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> srcUdpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its UDP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

            NxmOfUdpSrc udpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpSrc();

            if (udpSrc != null) {
                srcUdpMatches.add(udpSrc.getPort().getValue().toJava());
                srcUdpMatches.add(udpSrc.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, srcUdpMatches.size());
        assertTrue(srcUdpMatches.contains(UDP_SRC_LOWER_PORT));
        assertTrue(srcUdpMatches.contains(65535));
    }

    @Test
    public void buildIpv4SrcUdpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(UDP_SRC_LOWER_PORT));
        srcPortRange.setUpperPort(new PortNumber(UDP_SRC_UPPER_PORT));
        aceIpBuilder.setSourcePortRange(srcPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> srcUdpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its UDP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

            NxmOfUdpSrc udpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpSrc();

            if (udpSrc != null) {
                srcUdpMatches.add(udpSrc.getPort().getValue().toJava());
                srcUdpMatches.add(udpSrc.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, srcUdpMatches.size());
        assertTrue(srcUdpMatches.contains(UDP_SRC_LOWER_PORT));
        assertTrue(srcUdpMatches.contains(UDP_SRC_UPPER_PORT));
        assertTrue(srcUdpMatches.contains(65535));
    }

    @Test
    public void buildIpv4DstLwrUdpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(UDP_DST_LOWER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> dstUdpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its UDP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

            NxmOfUdpDst udpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpDst();

            if (udpDst != null) {
                dstUdpMatches.add(udpDst.getPort().getValue().toJava());
                dstUdpMatches.add(udpDst.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(1, matchBuilds.size());
        assertEquals(2, dstUdpMatches.size());
        assertTrue(dstUdpMatches.contains(UDP_DST_LOWER_PORT));
        assertTrue(dstUdpMatches.contains(65535));
    }

    @Test
    public void buildIpv4DstUdpMatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setAceIpVersion(new AceIpv4Builder().build());
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(UDP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(UDP_DST_UPPER_PORT));
        aceIpBuilder.setDestinationPortRange(dstPortRange.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());

        // Create the aclMatches that is the object to be tested
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> dstUdpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its UDP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

            NxmOfUdpDst udpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpDst();

            if (udpDst != null) {
                dstUdpMatches.add(udpDst.getPort().getValue().toJava());
                dstUdpMatches.add(udpDst.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(2, matchBuilds.size());
        assertEquals(4, dstUdpMatches.size());
        assertTrue(dstUdpMatches.contains(UDP_DST_LOWER_PORT));
        assertTrue(dstUdpMatches.contains(UDP_DST_UPPER_PORT));
        assertTrue(dstUdpMatches.contains(65534));
        assertTrue(dstUdpMatches.contains(65535));
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
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        Set<Integer> srcUdpMatches = new HashSet<>();
        Set<Integer> dstUdpMatches = new HashSet<>();

        for (MatchBuilder matchBuilder : matchBuilds) {
            // There should be an IPv4 etherType set
            EthernetMatch ethMatch = matchBuilder.getEthernetMatch();
            assertNotNull(ethMatch);
            assertEquals(ethMatch.getEthernetType().getType().getValue(), Long.valueOf(NwConstants.ETHTYPE_IPV4));

            // Make sure its UDP
            IpMatch ipMatch = matchBuilder.getIpMatch();
            assertNotNull(ipMatch);
            assertEquals(ipMatch.getIpProtocol(), Short.valueOf(IPProtocols.UDP.shortValue()));

            NxmOfUdpSrc udpSrc = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(0)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpSrc();

            NxmOfUdpDst udpDst = matchBuilder
                    .augmentation(GeneralAugMatchNodesNodeTableFlow.class).getExtensionList().get(1)
                    .getExtension().augmentation(NxAugMatchNodesNodeTableFlow.class).getNxmOfUdpDst();

            if (udpSrc != null) {
                srcUdpMatches.add(udpSrc.getPort().getValue().toJava());
                srcUdpMatches.add(udpSrc.getMask().toJava());
            }

            if (udpDst != null) {
                dstUdpMatches.add(udpDst.getPort().getValue().toJava());
                dstUdpMatches.add(udpDst.getMask().toJava());
            }

            // The layer3 match should be null
            assertNull(matchBuilder.getLayer3Match());
        }
        assertEquals(2, matchBuilds.size());
        assertEquals(2, srcUdpMatches.size());
        assertEquals(4, dstUdpMatches.size());

        assertTrue(srcUdpMatches.contains(UDP_SRC_LOWER_PORT));
        assertTrue(srcUdpMatches.contains(UDP_SRC_UPPER_PORT));
        assertTrue(srcUdpMatches.contains(65535));

        assertTrue(dstUdpMatches.contains(UDP_DST_LOWER_PORT));
        assertTrue(dstUdpMatches.contains(UDP_DST_UPPER_PORT));
        assertTrue(dstUdpMatches.contains(65534));
        assertTrue(dstUdpMatches.contains(65535));
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
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        for (MatchBuilder matchBuilder : matchBuilds) {
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
        List<MatchBuilder> matchBuilds = aclMatches.buildMatch();

        for (MatchBuilder matchBuilder : matchBuilds) {
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

    @Test
    public void invertEthMatchTest() {
        AceEthBuilder aceEthBuilder = new AceEthBuilder();
        aceEthBuilder.setDestinationMacAddress(new MacAddress(MAC_DST_STR));
        aceEthBuilder.setSourceMacAddress(new MacAddress(MAC_SRC_STR));
        AceEth aceEth = aceEthBuilder.build();

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceEth);
        Matches matches = matchesBuilder.build();

        Matches invertedMatches = AclMatches.invertMatches(matches);

        assertNotEquals(matches, invertedMatches);

        AceEth invertedAceEth = (AceEth) invertedMatches.getAceType();
        assertEquals(invertedAceEth.getDestinationMacAddress(), aceEth.getSourceMacAddress());
        assertEquals(invertedAceEth.getSourceMacAddress(), aceEth.getDestinationMacAddress());
    }

    @Test
    public void invertIpv4MatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setProtocol(IPProtocols.UDP.shortValue());
        aceIpBuilder.setDscp(new Dscp(DSCP_VALUE));

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(UDP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(UDP_DST_UPPER_PORT));
        DestinationPortRange destinationPortRange = dstPortRange.build();
        aceIpBuilder.setDestinationPortRange(destinationPortRange);

        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(IPV4_DST_STR));
        aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(IPV4_SRC_STR));
        AceIpv4 aceIpv4 = aceIpv4Builder.build();
        aceIpBuilder.setAceIpVersion(aceIpv4);

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        AceIp aceIp = aceIpBuilder.build();
        matchesBuilder.setAceType(aceIp);

        Matches matches = matchesBuilder.build();

        Matches invertedMatches = AclMatches.invertMatches(matches);

        assertNotEquals(matches, invertedMatches);

        AceIp invertedAceIp = (AceIp) invertedMatches.getAceType();
        assertEquals(invertedAceIp.getDscp(), aceIp.getDscp());
        assertEquals(invertedAceIp.getProtocol(), aceIp.getProtocol());

        DestinationPortRange invertedDestinationPortRange = invertedAceIp.getDestinationPortRange();
        assertNull(invertedDestinationPortRange);

        SourcePortRange invertedSourcePortRange = invertedAceIp.getSourcePortRange();
        assertEquals(invertedSourcePortRange.getLowerPort(), destinationPortRange.getLowerPort());
        assertEquals(invertedSourcePortRange.getUpperPort(), destinationPortRange.getUpperPort());

        AceIpv4 invertedAceIpv4 = (AceIpv4) invertedAceIp.getAceIpVersion();
        assertEquals(invertedAceIpv4.getDestinationIpv4Network(), aceIpv4.getSourceIpv4Network());
        assertEquals(invertedAceIpv4.getSourceIpv4Network(), aceIpv4.getDestinationIpv4Network());
    }

    @Test
    public void invertIpv6MatchTest() {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setProtocol(IPProtocols.TCP.shortValue());
        aceIpBuilder.setDscp(new Dscp(DSCP_VALUE));

        SourcePortRangeBuilder srcPortRange = new SourcePortRangeBuilder();
        srcPortRange.setLowerPort(new PortNumber(TCP_SRC_LOWER_PORT));
        srcPortRange.setUpperPort(new PortNumber(TCP_SRC_UPPER_PORT));
        SourcePortRange sourcePortRange = srcPortRange.build();
        aceIpBuilder.setSourcePortRange(sourcePortRange);

        DestinationPortRangeBuilder dstPortRange = new DestinationPortRangeBuilder();
        dstPortRange.setLowerPort(new PortNumber(TCP_DST_LOWER_PORT));
        dstPortRange.setUpperPort(new PortNumber(TCP_DST_UPPER_PORT));
        DestinationPortRange destinationPortRange = dstPortRange.build();
        aceIpBuilder.setDestinationPortRange(destinationPortRange);

        AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
        aceIpv6Builder.setDestinationIpv6Network(new Ipv6Prefix(IPV6_DST_STR));
        aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(IPV6_SRC_STR));
        AceIpv6 aceIpv6 = aceIpv6Builder.build();
        aceIpBuilder.setAceIpVersion(aceIpv6);

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        AceIp aceIp = aceIpBuilder.build();
        matchesBuilder.setAceType(aceIp);

        Matches matches = matchesBuilder.build();

        Matches invertedMatches = AclMatches.invertMatches(matches);

        assertNotEquals(matches, invertedMatches);

        AceIp invertedAceIp = (AceIp) invertedMatches.getAceType();
        assertEquals(invertedAceIp.getDscp(), aceIp.getDscp());
        assertEquals(invertedAceIp.getProtocol(), aceIp.getProtocol());

        DestinationPortRange invertedDestinationPortRange = invertedAceIp.getDestinationPortRange();
        assertEquals(invertedDestinationPortRange.getLowerPort(), sourcePortRange.getLowerPort());
        assertEquals(invertedDestinationPortRange.getUpperPort(), sourcePortRange.getUpperPort());

        SourcePortRange invertedSourcePortRange = invertedAceIp.getSourcePortRange();
        assertEquals(invertedSourcePortRange.getLowerPort(), destinationPortRange.getLowerPort());
        assertEquals(invertedSourcePortRange.getUpperPort(), destinationPortRange.getUpperPort());

        AceIpv6 invertedAceIpv6 = (AceIpv6) invertedAceIp.getAceIpVersion();
        assertEquals(invertedAceIpv6.getDestinationIpv6Network(), aceIpv6.getSourceIpv6Network());
        assertEquals(invertedAceIpv6.getSourceIpv6Network(), aceIpv6.getDestinationIpv6Network());
    }

    @Test
    public void testgetLayer4MaskForRange_SinglePort() {
        Map<Integer, Integer> layer4MaskForRange = AclMatches.getLayer4MaskForRange(1111, 1111);
        assertEquals("port L4 mask missing", 1, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_MultiplePorts() {
        Map<Integer, Integer> layer4MaskForRange = AclMatches.getLayer4MaskForRange(1024, 2048);
        assertEquals("port L4 mask missing", 2, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_ExceedMin() {
        Map<Integer, Integer> layer4MaskForRange = AclMatches.getLayer4MaskForRange(0, 1);

        assertEquals("port L4 mask missing", 1, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_ExceedMax() {
        Map<Integer, Integer> layer4MaskForRange = AclMatches.getLayer4MaskForRange(1, 65536);
        assertEquals("Illegal ports range", 0, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_MinGreaterThanMax() {
        Map<Integer, Integer> layer4MaskForRange = AclMatches.getLayer4MaskForRange(8192, 4096);
        assertEquals("Illegal ports range", 0, layer4MaskForRange.size());
    }
}
