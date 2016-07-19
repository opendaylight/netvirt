/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice;

import static com.google.common.collect.Iterables.filter;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;

import com.google.common.collect.Iterables;

public class AclServiceTestUtils {

    public static void verifyGeneralFlows(List<MatchInfoBase> srcFlowMatches, String protocol, String srcIpv4Net,
            String dstIpv4Net, String mask) {
        verifyMatchInfo(srcFlowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ip_proto, protocol);
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ipv4_source, srcIpv4Net, mask);
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ipv4_destination, dstIpv4Net, mask);
    }
    
    public static AceIpBuilder prepareAceIpBuilder(String srcIpv4Net, String dstIpv4Net, String lowerPort, String upperPort,
            short protocol) {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        if (srcIpv4Net != null) {
            v4builder.setSourceIpv4Network(new Ipv4Prefix(srcIpv4Net));
        } else {
            v4builder.setSourceIpv4Network(null);
        }

        if (dstIpv4Net != null) {
            v4builder.setDestinationIpv4Network(new Ipv4Prefix(dstIpv4Net));
        } else {
            v4builder.setDestinationIpv4Network(null);
        }
        builder.setAceIpVersion(v4builder.build());
        if (lowerPort != null && upperPort != null) {
            SourcePortRangeBuilder srcPortBuilder = new SourcePortRangeBuilder();
            srcPortBuilder.setLowerPort(PortNumber.getDefaultInstance(lowerPort));
            srcPortBuilder.setUpperPort(PortNumber.getDefaultInstance(upperPort));
            builder.setSourcePortRange(srcPortBuilder.build());
            DestinationPortRangeBuilder dstPortBuilder = new DestinationPortRangeBuilder();
            dstPortBuilder.setLowerPort(PortNumber.getDefaultInstance(lowerPort));
            dstPortBuilder.setUpperPort(PortNumber.getDefaultInstance(upperPort));
            builder.setDestinationPortRange(dstPortBuilder.build());
        }
        builder.setProtocol(protocol);
        return builder;
    }

    public static void verifyMatchInfo(List<MatchInfoBase> flowMatches, MatchFieldType matchType, String... params) {
        
        Iterable<MatchInfoBase> matches = filter(flowMatches,
                (item -> ((MatchInfo) item).getMatchField().equals(matchType)));
        
        for (MatchInfoBase baseMatch : matches) {
            verifyMatchValues((MatchInfo) baseMatch, params);
        }
    }

    public static void verifyMatchValues(MatchInfo match, String... params) {
        switch (match.getMatchField()) {
            case tcp_src:
            case tcp_dst:
            case ip_proto:
            case udp_src:
            case udp_dst:
            case eth_type:
                long[] values = Arrays.stream(params).mapToLong(l -> Long.parseLong(l)).toArray();
                Assert.assertArrayEquals(values, match.getMatchValues());
                break;
            case ipv4_source:
            case ipv4_destination:
                Assert.assertArrayEquals(params, match.getStringMatchValues());
                break;
            default:
                assertTrue("match type is not supported", true);
                break;
        }
    }

    public static void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches, MatchFieldType matchType) {
        Iterable<MatchInfoBase> matches = filter(flowMatches,
                (item -> ((MatchInfo) item).getMatchField().equals(matchType)));
        
        Assert.assertTrue("unexpected match type " + matchType.name(), Iterables.isEmpty(matches));
    }
}
