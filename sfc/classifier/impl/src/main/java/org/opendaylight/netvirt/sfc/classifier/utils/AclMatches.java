/*
 * Copyright © 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Layer4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclMatches {
    private static final Logger LOG = LoggerFactory.getLogger(AclMatches.class);
    private final MatchBuilder matchBuilder;
    private final Set<TcpMatch> tcpMatches;
    private final Matches matches;
    private boolean ipv4EtherTypeSet;
    private boolean ipv6EtherTypeSet;

    public AclMatches(Matches matches) {
        this.matchBuilder = new MatchBuilder();
        this.tcpMatches = new HashSet<>();
        this.matches = matches;
        this.ipv4EtherTypeSet = false;
        this.ipv6EtherTypeSet = false;
    }

    /**
     * Convert the ACL into an OpenFlow {@link MatchBuilder}.
     * @return {@link MatchBuilder}
     */
    public List<MatchBuilder> buildMatch() {
        List<MatchBuilder> newMatches = new ArrayList<>();
        if (matches.getAceType() instanceof AceEth) {
            addEthMatch();
        } else if (matches.getAceType() instanceof AceIp) {
            addIpMatch();
        }
        if (tcpMatches.isEmpty()) {
            MatchBuilder matchBuilds = matchBuilder;
            LOG.debug("buildMatch: {}", newMatches.add(new MatchBuilder(matchBuilds.build())));
        } else {
            for (Layer4Match newL4Match : tcpMatches) {
                MatchBuilder matchBuilds = matchBuilder.setLayer4Match(newL4Match);
                LOG.debug("buildMatch: {}", newMatches.add(new MatchBuilder(matchBuilds.build())));
            }
        }
        LOG.debug("returned matches: {}", newMatches);
        return newMatches;
    }

    private void addEthMatch() {
        AceEth aceEth = (AceEth) matches.getAceType();

        if (aceEth.getSourceMacAddress() != null) {
            EthernetMatchBuilder ethernetMatch = new EthernetMatchBuilder();
            EthernetSourceBuilder ethSourceBuilder = new EthernetSourceBuilder();
            ethSourceBuilder.setAddress(new MacAddress(aceEth.getSourceMacAddress()));
            ethernetMatch.setEthernetSource(ethSourceBuilder.build());

            matchBuilder.setEthernetMatch(mergeEthernetMatch(matchBuilder, ethernetMatch));
        }

        if (aceEth.getDestinationMacAddress() != null) {
            EthernetMatchBuilder ethernetMatch = new EthernetMatchBuilder();
            EthernetDestinationBuilder ethDestBuilder = new EthernetDestinationBuilder();
            ethDestBuilder.setAddress(new MacAddress(aceEth.getDestinationMacAddress()));
            ethernetMatch.setEthernetDestination(ethDestBuilder.build());

            matchBuilder.setEthernetMatch(mergeEthernetMatch(matchBuilder, ethernetMatch));
        }
    }

    private void addIpMatch() {
        AceIp aceIp = (AceIp)matches.getAceType();

        if (aceIp.getDscp() != null) {
            addDscpMatch(aceIp);
        }

        if (aceIp.getProtocol() != null) {
            addIpProtocolMatch(aceIp);
        }

        if (aceIp.getAceIpVersion() instanceof AceIpv4) {
            addIpV4Match(aceIp);
        }

        if (aceIp.getAceIpVersion() instanceof AceIpv6) {
            addIpV6Match(aceIp);
        }
    }

    private void addDscpMatch(AceIp aceIp) {
        setIpv4EtherType();

        IpMatchBuilder ipMatch = new IpMatchBuilder();
        Dscp dscp = new Dscp(aceIp.getDscp());
        ipMatch.setIpDscp(dscp);

        matchBuilder.setIpMatch(mergeIpMatch(matchBuilder, ipMatch));
    }

    private void addIpProtocolMatch(AceIp aceIp) {
        // Match on IP
        setIpv4EtherType();
        IpMatchBuilder ipMatch = new IpMatchBuilder();
        ipMatch.setIpProtocol(aceIp.getProtocol());
        matchBuilder.setIpMatch(mergeIpMatch(matchBuilder, ipMatch));

        // TODO Ranges are not supported yet

        int srcPort = 0;
        if (aceIp.getSourcePortRange() != null && aceIp.getSourcePortRange().getLowerPort() != null) {
            srcPort = aceIp.getSourcePortRange().getLowerPort().getValue();
        }

        int dstPort = 0;
        if (aceIp.getDestinationPortRange() != null && aceIp.getDestinationPortRange().getLowerPort() != null) {
            dstPort = aceIp.getDestinationPortRange().getLowerPort().getValue();
        }

        int dstPortMax = 0;
        if (aceIp.getDestinationPortRange() != null && aceIp.getDestinationPortRange().getUpperPort() != null) {
            LOG.debug("dest port");
            dstPortMax = aceIp.getDestinationPortRange().getUpperPort().getValue();
        }
        // Match on a TCP/UDP src/dst port
        if (aceIp.getProtocol() == IPProtocols.TCP.shortValue()) {
            TcpMatchBuilder tcpMatch = new TcpMatchBuilder();
            if (srcPort != 0) {
                tcpMatch.setTcpSourcePort(new PortNumber(srcPort));
                matchBuilder.setLayer4Match(tcpMatch.build());
            }
            if (dstPort < dstPortMax) {
                Set<Integer> destPorts = createSetFromRange(dstPort, dstPortMax);
                for (Integer destPort : destPorts) {
                    TcpMatchBuilder tpMatch = new TcpMatchBuilder();
                    tcpMatches.add(tpMatch.setTcpDestinationPort(new PortNumber(destPort)).build());
                }
            } else if (dstPort != 0) {
                tcpMatch.setTcpDestinationPort(new PortNumber(dstPort));
                matchBuilder.setLayer4Match(tcpMatch.build());
            }
        } else if (aceIp.getProtocol() == IPProtocols.UDP.shortValue()) {
            UdpMatchBuilder udpMatch = new UdpMatchBuilder();
            if (srcPort != 0) {
                udpMatch.setUdpSourcePort(new PortNumber(srcPort));
            }
            if (dstPort != 0) {
                udpMatch.setUdpDestinationPort(new PortNumber(dstPort));
            }
            if (srcPort != 0 || dstPort != 0) {
                matchBuilder.setLayer4Match(udpMatch.build());
            }
        }
    }

    private Set<Integer> createSetFromRange(int minVal, int maxVal) {
        Set<Integer> res = new HashSet<>();
        if (minVal != 0 && maxVal != 0) {
            final int min = minVal;
            final int max = maxVal;
            for (int val = min; val <= max; val++) {
                res.add(val);
            }
        }
        return res;
    }

    private void addIpV4Match(AceIp aceIp) {
        setIpv4EtherType();

        AceIpv4 aceIpv4 = (AceIpv4)aceIp.getAceIpVersion();
        if (aceIpv4.getDestinationIpv4Network() != null) {
            Ipv4MatchBuilder ipv4match = new Ipv4MatchBuilder();
            ipv4match.setIpv4Destination(aceIpv4.getDestinationIpv4Network());
            matchBuilder.setLayer3Match(mergeIpv4Match(matchBuilder, ipv4match));
        }

        if (aceIpv4.getSourceIpv4Network() != null) {
            Ipv4MatchBuilder ipv4match = new Ipv4MatchBuilder();
            ipv4match.setIpv4Source(aceIpv4.getSourceIpv4Network());
            matchBuilder.setLayer3Match(mergeIpv4Match(matchBuilder, ipv4match));
        }
    }

    private void addIpV6Match(AceIp aceIp) {
        setIpv6EtherType();

        AceIpv6 aceIpv6 = (AceIpv6)aceIp.getAceIpVersion();
        if (aceIpv6.getSourceIpv6Network() != null) {
            Ipv6MatchBuilder ipv6match = new Ipv6MatchBuilder();
            ipv6match.setIpv6Source(aceIpv6.getSourceIpv6Network());
            matchBuilder.setLayer3Match(mergeIpv6Match(matchBuilder, ipv6match));
        }

        if (aceIpv6.getDestinationIpv6Network() != null) {
            Ipv6MatchBuilder ipv6match = new Ipv6MatchBuilder();
            ipv6match.setIpv6Destination(aceIpv6.getDestinationIpv6Network());
            matchBuilder.setLayer3Match(mergeIpv6Match(matchBuilder, ipv6match));
        }
    }

    // If we call multiple Layer3 match methods, the MatchBuilder
    // Ipv4Match object gets overwritten each time, when we actually
    // want to set additional fields on the existing Ipv4Match object
    private static Ipv4Match mergeIpv4Match(MatchBuilder match, Ipv4MatchBuilder ipMatchBuilder) {
        Ipv4Match ipv4Match = (Ipv4Match) match.getLayer3Match();
        if (ipv4Match == null) {
            return ipMatchBuilder.build();
        }

        if (ipv4Match.getIpv4Destination() != null) {
            ipMatchBuilder.setIpv4Destination(ipv4Match.getIpv4Destination());
        }

        if (ipv4Match.getIpv4Source() != null) {
            ipMatchBuilder.setIpv4Source(ipv4Match.getIpv4Source());
        }

        return ipMatchBuilder.build();
    }

    private void setIpv6EtherType() {
        if (this.ipv6EtherTypeSet) {
            // No need to set it twice
            return;
        }

        setEtherType(NwConstants.ETHTYPE_IPV6);
        this.ipv6EtherTypeSet = true;
    }

    private void setIpv4EtherType() {
        if (this.ipv4EtherTypeSet) {
            // No need to set it twice
            return;
        }

        setEtherType(NwConstants.ETHTYPE_IPV4);
        this.ipv4EtherTypeSet = true;
    }

    private void setEtherType(long etherType) {
        EthernetTypeBuilder ethTypeBuilder = new EthernetTypeBuilder();
        ethTypeBuilder.setType(new EtherType(etherType));
        EthernetMatchBuilder ethernetMatch = new EthernetMatchBuilder();
        ethernetMatch.setEthernetType(ethTypeBuilder.build());
        matchBuilder.setEthernetMatch(mergeEthernetMatch(matchBuilder, ethernetMatch));
    }

    // If we call multiple Layer3 match methods, the MatchBuilder
    // Ipv6Match object gets overwritten each time, when we actually
    // want to set additional fields on the existing Ipv6Match object
    private static Ipv6Match mergeIpv6Match(MatchBuilder match, Ipv6MatchBuilder ipMatchBuilder) {
        Ipv6Match ipv6Match = (Ipv6Match) match.getLayer3Match();
        if (ipv6Match == null) {
            return ipMatchBuilder.build();
        }

        if (ipv6Match.getIpv6Destination() != null) {
            ipMatchBuilder.setIpv6Destination(ipv6Match.getIpv6Destination());
        }

        if (ipv6Match.getIpv6Source() != null) {
            ipMatchBuilder.setIpv6Source(ipv6Match.getIpv6Source());
        }

        return ipMatchBuilder.build();
    }

    // If we call multiple IpMatch match methods, the MatchBuilder
    // IpMatch object gets overwritten each time, when we actually
    // want to set additional fields on the existing IpMatch object
    private static IpMatch mergeIpMatch(MatchBuilder match, IpMatchBuilder ipMatchBuilder) {
        IpMatch ipMatch = match.getIpMatch();
        if (ipMatch == null) {
            return ipMatchBuilder.build();
        }

        if (ipMatch.getIpDscp() != null) {
            ipMatchBuilder.setIpDscp(ipMatch.getIpDscp());
        }

        if (ipMatch.getIpEcn() != null) {
            ipMatchBuilder.setIpEcn(ipMatch.getIpEcn());
        }

        if (ipMatch.getIpProto() != null) {
            ipMatchBuilder.setIpProto(ipMatch.getIpProto());
        }

        if (ipMatch.getIpProtocol() != null) {
            ipMatchBuilder.setIpProtocol(ipMatch.getIpProtocol());
        }

        return ipMatchBuilder.build();
    }

    // If we call multiple ethernet match methods, the MatchBuilder
    // EthernetMatch object gets overwritten each time, when we actually
    // want to set additional fields on the existing EthernetMatch object
    private static EthernetMatch mergeEthernetMatch(MatchBuilder match, EthernetMatchBuilder ethMatchBuilder) {
        EthernetMatch ethMatch = match.getEthernetMatch();
        if (ethMatch == null) {
            return ethMatchBuilder.build();
        }

        if (ethMatch.getEthernetDestination() != null) {
            ethMatchBuilder.setEthernetDestination(ethMatch.getEthernetDestination());
        }

        if (ethMatch.getEthernetSource() != null) {
            ethMatchBuilder.setEthernetSource(ethMatch.getEthernetSource());
        }

        if (ethMatch.getEthernetType() != null) {
            ethMatchBuilder.setEthernetType(ethMatch.getEthernetType());
        }

        return ethMatchBuilder.build();
    }

    public static Matches invertMatches(Matches matches) {
        LOG.trace("Invert matches: {}", matches);
        MatchesBuilder matchesBuilder = new MatchesBuilder(matches);

        if (matches.getAceType() instanceof AceIp) {
            AceIp aceIp = (AceIp) matches.getAceType();
            AceIpBuilder aceIpBuilder = new AceIpBuilder(aceIp);
            aceIpBuilder.setDestinationPortRange(null);
            aceIpBuilder.setSourcePortRange(null);
            SourcePortRange sourcePortRange = aceIp.getSourcePortRange();
            DestinationPortRange destinationPortRange = aceIp.getDestinationPortRange();

            if (sourcePortRange != null) {
                DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
                destinationPortRangeBuilder.setLowerPort(sourcePortRange.getLowerPort());
                destinationPortRangeBuilder.setUpperPort(sourcePortRange.getUpperPort());
                aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
            }

            if (destinationPortRange != null) {
                SourcePortRangeBuilder sourcePortRangeBuilder = new SourcePortRangeBuilder();
                sourcePortRangeBuilder.setLowerPort(destinationPortRange.getLowerPort());
                sourcePortRangeBuilder.setUpperPort(destinationPortRange.getUpperPort());
                aceIpBuilder.setSourcePortRange(sourcePortRangeBuilder.build());
            }

            if (aceIp.getAceIpVersion() instanceof AceIpv4) {
                AceIpv4 aceIpv4 = (AceIpv4) aceIp.getAceIpVersion();
                Ipv4Prefix destinationIpv4Network = aceIpv4.getDestinationIpv4Network();
                Ipv4Prefix sourceIpv4Network = aceIpv4.getSourceIpv4Network();

                AceIpv4Builder aceIpv4Builder = new AceIpv4Builder(aceIpv4);
                aceIpv4Builder.setDestinationIpv4Network(sourceIpv4Network);
                aceIpv4Builder.setSourceIpv4Network(destinationIpv4Network);
                aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

            } else if (aceIp.getAceIpVersion() instanceof AceIpv6) {
                AceIpv6 aceIpv6 = (AceIpv6) aceIp.getAceIpVersion();
                Ipv6Prefix destinationIpv6Network = aceIpv6.getDestinationIpv6Network();
                Ipv6Prefix sourceIpv6Network = aceIpv6.getSourceIpv6Network();

                AceIpv6Builder aceIpv6Builder = new AceIpv6Builder(aceIpv6);
                aceIpv6Builder.setDestinationIpv6Network(sourceIpv6Network);
                aceIpv6Builder.setSourceIpv6Network(destinationIpv6Network);
                aceIpBuilder.setAceIpVersion(aceIpv6Builder.build());
            }

            matchesBuilder.setAceType(aceIpBuilder.build());

        } else if (matches.getAceType() instanceof AceEth) {
            AceEth aceEth = (AceEth) matches.getAceType();
            MacAddress destinationMacAddress = aceEth.getDestinationMacAddress();
            MacAddress destinationMacAddressMask = aceEth.getDestinationMacAddressMask();
            MacAddress sourceMacAddress = aceEth.getSourceMacAddress();
            MacAddress sourceMacAddressMask = aceEth.getSourceMacAddressMask();

            AceEthBuilder aceEthBuilder = new AceEthBuilder(aceEth);
            aceEthBuilder.setDestinationMacAddress(sourceMacAddress);
            aceEthBuilder.setDestinationMacAddressMask(sourceMacAddressMask);
            aceEthBuilder.setSourceMacAddress(destinationMacAddress);
            aceEthBuilder.setSourceMacAddressMask(destinationMacAddressMask);
            matchesBuilder.setAceType(aceEthBuilder.build());
        }

        Matches invertedMatches = matchesBuilder.build();
        LOG.trace("Inverted matches: {}", invertedMatches);
        return invertedMatches;
    }

}
