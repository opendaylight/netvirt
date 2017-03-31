/*
 * Copyright © 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEth;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclMatches {
    public static final short IP_PROTOCOL_TCP = (short) 6;
    public static final short IP_PROTOCOL_UDP = (short) 17;
    private static final Logger LOG = LoggerFactory.getLogger(AclMatches.class);
    private MatchBuilder matchBuilder;
    private final Matches matches;
    private boolean ipv4EtherTypeSet;

    // TODO need to remove usage of legacy MatchUtils
    //      https://bugs.opendaylight.org/show_bug.cgi?id=8129

    public AclMatches(Matches matches) {
        this.matchBuilder = new MatchBuilder();
        this.matches = matches;
        this.ipv4EtherTypeSet = false;
    }

    /**
     * Convert the ACL into an OpenFlow {@link MatchBuilder}.
     * @return {@link MatchBuilder}
     */
    //TODO: Matches will overwrite previous matches for ethernet and ip since these methods
    // can be called successively for the same ACL.
    // This requires fixing the MatchUtils to preserve previously set fields.
    public MatchBuilder buildMatch() {
        if (matches.getAceType() instanceof AceEth) {
            addEthMatch();
        } else if (matches.getAceType() instanceof AceIp) {
            addIpMatch();
        }

        LOG.debug("buildMatch: {}", matchBuilder.build());

        return matchBuilder;
    }

    private void addEthMatch() {
        AceEth aceEth = (AceEth) matches.getAceType();
        MatchUtils.createEthSrcDstMatch(matchBuilder, aceEth.getSourceMacAddress(),
                aceEth.getDestinationMacAddress());
    }

    private void addIpMatch() {
        AceIp aceIp = (AceIp)matches.getAceType();

        if (aceIp.getDscp() != null) {
            MatchUtils.addDscp(matchBuilder, aceIp.getDscp().getValue());
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

    private void addIpProtocolMatch(AceIp aceIp) {
        // Match on IP
        setIpv4EtherType();
        IpMatchBuilder ipMatch = new IpMatchBuilder();
        ipMatch.setIpProtocol(aceIp.getProtocol());
        matchBuilder.setIpMatch(ipMatch.build());

        // TODO Ranges are not supported yet

        int srcPort = 0;
        if (aceIp.getSourcePortRange() != null && aceIp.getSourcePortRange().getLowerPort() != null) {
            srcPort = aceIp.getSourcePortRange().getLowerPort().getValue();
        }

        int dstPort = 0;
        if (aceIp.getDestinationPortRange() != null && aceIp.getDestinationPortRange().getLowerPort() != null) {
            dstPort = aceIp.getDestinationPortRange().getLowerPort().getValue();
        }

        // Match on a TCP/UDP src/dst port
        if (aceIp.getProtocol() == IP_PROTOCOL_TCP) {
            TcpMatchBuilder tcpMatch = new TcpMatchBuilder();
            if (srcPort != 0) {
                tcpMatch.setTcpSourcePort(new PortNumber(srcPort));
            }
            if (dstPort != 0) {
                tcpMatch.setTcpDestinationPort(new PortNumber(dstPort));
            }
            if (srcPort != 0 || dstPort != 0) {
                matchBuilder.setLayer4Match(tcpMatch.build());
            }
        } else if (aceIp.getProtocol() == IP_PROTOCOL_UDP) {
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

    private void addIpV4Match(AceIp aceIp) {
        setIpv4EtherType();

        // TODO https://bugs.opendaylight.org/show_bug.cgi?id=8128
        // TODO for some reason these matches cause the flow not to be written for netmasks other than 32
        AceIpv4 aceIpv4 = (AceIpv4)aceIp.getAceIpVersion();
        Ipv4MatchBuilder ipv4match = new Ipv4MatchBuilder();
        boolean hasIpMatch = false;
        if (aceIpv4.getDestinationIpv4Network() != null) {
            hasIpMatch = true;
            ipv4match.setIpv4Destination(aceIpv4.getDestinationIpv4Network());
            LOG.info("addIpV4Match with Dst IP [{}]", aceIpv4.getDestinationIpv4Network().getValue());
        }

        if (aceIpv4.getSourceIpv4Network() != null) {
            hasIpMatch = true;
            ipv4match.setIpv4Source(aceIpv4.getSourceIpv4Network());
            LOG.info("addIpV4Match with Src IP [{}]", aceIpv4.getSourceIpv4Network().getValue());
        }

        if (hasIpMatch) {
            matchBuilder.setLayer3Match(ipv4match.build());
        }
    }

    private void addIpV6Match(AceIp aceIp) {
        AceIpv6 aceIpv6 = (AceIpv6)aceIp.getAceIpVersion();

        MatchUtils.createEtherTypeMatch(matchBuilder, new EtherType(MatchUtils.ETHERTYPE_IPV6));
        matchBuilder = MatchUtils.addRemoteIpv6Prefix(matchBuilder, aceIpv6.getSourceIpv6Network(),
                aceIpv6.getDestinationIpv6Network());
    }

    private void setIpv4EtherType() {
        if (this.ipv4EtherTypeSet) {
            // No need to set it twice
            return;
        }

        EthernetTypeBuilder ethTypeBuilder = new EthernetTypeBuilder();
        ethTypeBuilder.setType(new EtherType(MatchUtils.ETHERTYPE_IPV4));
        EthernetMatchBuilder ethernetMatch = new EthernetMatchBuilder();
        ethernetMatch.setEthernetType(ethTypeBuilder.build());
        matchBuilder.setEthernetMatch(ethernetMatch.build());
        this.ipv4EtherTypeSet = true;
    }
}
