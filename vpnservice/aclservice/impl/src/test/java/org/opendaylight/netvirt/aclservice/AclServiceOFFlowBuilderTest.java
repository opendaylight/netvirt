/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;

public class AclServiceOFFlowBuilderTest {

    @Test
    public void testProgramIpFlow_NullMatches() {
        Matches matches = null;
        Map<String, List<MatchInfoBase>> flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
        assertNull(flowMap);
    }
		
    @Test
    public void testprogramOtherProtocolFlow() {
        AceIpBuilder builder = prepareAceIpBuilder("10.1.1.1/24", "20.1.1.1/24", null, null, (short) 1);
        Map<String, List<MatchInfoBase>> flowMatchesMap =
                AclServiceOFFlowBuilder.programOtherProtocolFlow(builder.build());
        List<MatchInfoBase> flowMatches = flowMatchesMap.get("OTHER_PROTO" + "1");
        verifyGeneralFlows(flowMatches, "1", "10.1.1.1", "20.1.1.1", "24");

    }	

    @Test
    public void testprogramIcmpFlow() {

        AceIpBuilder builder = prepareAceIpBuilder("10.1.1.1/24", "20.1.1.1/24", "1024", "2048", (short) 1);

        Map<String, List<MatchInfoBase>> flowMatchesMap = AclServiceOFFlowBuilder.programIcmpFlow(builder.build());

        List<MatchInfoBase> flowMatches = flowMatchesMap.entrySet().iterator().next().getValue();

        verifyGeneralFlows(flowMatches, "1", "10.1.1.1", "20.1.1.1", "24");

        List<MatchInfoBase> icmpv4Matches =
                flowMatches.stream().filter(item -> ((MatchInfo) item).getMatchField().equals(MatchFieldType.icmp_v4))
                        .collect(Collectors.toList());

        verifyMatchValues((MatchInfo) icmpv4Matches.get(0), "1024", "2048");
        verifyMatchValues((MatchInfo) icmpv4Matches.get(1), "4096", "8192");
    }

    @Test
    public void testprogramTcpFlow_NoSrcDstPortRange() {
        AceIpBuilder builder = prepareAceIpBuilder("10.1.1.1/24", "20.1.1.1/24", null, null, (short) 1);

        Map<String, List<MatchInfoBase>> flowMatchesMap = AclServiceOFFlowBuilder.programTcpFlow(builder.build());
        List<MatchInfoBase> flowMatches = flowMatchesMap.get("TCP_SOURCE_ALL_");

        verifyGeneralFlows(flowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.tcp_src);
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.tcp_dst);
    }

    @Test
    public void testprogramTcpFlow_WithSrcDstPortRange() {
        AceIpBuilder builder = prepareAceIpBuilder("10.1.1.1/24", "20.1.1.1/24", "1024", "1024", (short) 1);

        Map<String, List<MatchInfoBase>> flowMatchesMap = AclServiceOFFlowBuilder.programTcpFlow(builder.build());

        List<MatchInfoBase> srcFlowMatches = new ArrayList<MatchInfoBase>();
        List<MatchInfoBase> dstFlowMatches = new ArrayList<MatchInfoBase>();

        for (String flowId : flowMatchesMap.keySet()) {
            if (flowId.startsWith("TCP_SOURCE_")) {
                srcFlowMatches.addAll(flowMatchesMap.get(flowId));
            }
            if (flowId.startsWith("TCP_DESTINATION_")) {
                dstFlowMatches.addAll(flowMatchesMap.get(flowId));
            }
        }

        verifyGeneralFlows(srcFlowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        List<MatchInfoBase> tcpSrcMatches = srcFlowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(MatchFieldType.tcp_src))
                .collect(Collectors.toList());

        verifyMatchValues((MatchInfo) tcpSrcMatches.get(0), "1024");

        verifyGeneralFlows(dstFlowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        List<MatchInfoBase> tcpDstMatches = dstFlowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(MatchFieldType.tcp_dst))
                .collect(Collectors.toList());
        verifyMatchValues((MatchInfo) tcpDstMatches.get(0), "1024");

    }

    private void verifyGeneralFlows(List<MatchInfoBase> srcFlowMatches, String protocol, String srcIpv4Net,
            String dstIpv4Net, String mask) {
        verifyMatchInfo(srcFlowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ip_proto, protocol);
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ipv4_source, srcIpv4Net, mask);
        verifyMatchInfo(srcFlowMatches, MatchFieldType.ipv4_destination, dstIpv4Net, mask);
    }

    @Test
    public void testProgramUdpFlow_NoSrcDstPortRange() {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        v4builder.setSourceIpv4Network(new Ipv4Prefix("10.1.1.1/24"));
        v4builder.setDestinationIpv4Network(new Ipv4Prefix("20.1.1.1/24"));
        builder.setAceIpVersion(v4builder.build());
        builder.setSourcePortRange(null);
        builder.setDestinationPortRange(null);
        short protocol = 1;
        builder.setProtocol(protocol);

        Map<String, List<MatchInfoBase>> flowMatchesMap = AclServiceOFFlowBuilder.programUdpFlow(builder.build());

        List<MatchInfoBase> flowMatches = flowMatchesMap.get("UDP_SOURCE_ALL_");

        verifyGeneralFlows(flowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.udp_src);
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.udp_dst);
    }

    @Test
    public void testprogramUdpFlow_WithSrcDstPortRange() {
        AceIpBuilder builder = prepareAceIpBuilder("10.1.1.1/24", "20.1.1.1/24", "1024", "1024", (short) 1);

        Map<String, List<MatchInfoBase>> flowMatchesMap = AclServiceOFFlowBuilder.programUdpFlow(builder.build());
        List<MatchInfoBase> srcFlowMatches = new ArrayList<MatchInfoBase>();
        List<MatchInfoBase> dstFlowMatches = new ArrayList<MatchInfoBase>();

        for (String flowId : flowMatchesMap.keySet()) {
            if (flowId.startsWith("UDP_SOURCE_")) {
                srcFlowMatches.addAll(flowMatchesMap.get(flowId));
            }
            if (flowId.startsWith("UDP_DESTINATION_")) {
                dstFlowMatches.addAll(flowMatchesMap.get(flowId));
            }
        }

        verifyGeneralFlows(srcFlowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        List<MatchInfoBase> udpSrcMatches = srcFlowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(MatchFieldType.udp_src))
                .collect(Collectors.toList());
        verifyMatchValues((MatchInfo) udpSrcMatches.get(0), "1024");

        verifyGeneralFlows(dstFlowMatches, "1", "10.1.1.1", "20.1.1.1", "24");
        List<MatchInfoBase> udpDstMatches = dstFlowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(MatchFieldType.udp_dst))
                .collect(Collectors.toList());
        verifyMatchValues((MatchInfo) udpDstMatches.get(0), "1024");

    }

    @Test
    public void testaddDstIpMatches_v4() {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        v4builder.setDestinationIpv4Network(new Ipv4Prefix("10.1.1.1/24"));
        builder.setAceIpVersion(v4builder.build());

        List<MatchInfoBase> flowMatches = AclServiceOFFlowBuilder.addDstIpMatches(builder.build());

        verifyMatchInfo(flowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchInfo(flowMatches, MatchFieldType.ipv4_destination, "10.1.1.1", "24");
    }

    @Test
    public void testaddDstIpMatches_v4NoDstNetwork() {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        v4builder.setDestinationIpv4Network(null);
        builder.setAceIpVersion(v4builder.build());

        List<MatchInfoBase> flowMatches = AclServiceOFFlowBuilder.addDstIpMatches(builder.build());

        verifyMatchInfo(flowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.ipv4_destination);

    }

    @Test
    public void testaddSrcIpMatches_v4() {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        v4builder.setSourceIpv4Network(new Ipv4Prefix("10.1.1.1/24"));
        builder.setAceIpVersion(v4builder.build());

        List<MatchInfoBase> flowMatches = AclServiceOFFlowBuilder.addSrcIpMatches(builder.build());

        verifyMatchInfo(flowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchInfo(flowMatches, MatchFieldType.ipv4_source, "10.1.1.1", "24");

    }

    @Test
    public void testaddSrcIpMatches_v4NoSrcNetwork() {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        v4builder.setSourceIpv4Network(null);
        builder.setAceIpVersion(v4builder.build());

        List<MatchInfoBase> flowMatches = AclServiceOFFlowBuilder.addSrcIpMatches(builder.build());
        verifyMatchInfo(flowMatches, MatchFieldType.eth_type, Integer.toString(NwConstants.ETHTYPE_IPV4));
        verifyMatchFieldTypeDontExist(flowMatches, MatchFieldType.ipv4_source);
    }

    private AceIpBuilder prepareAceIpBuilder(String srcIpv4Net, String dstIpv4Net, String lowerPort, String upperPort,
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

    private void verifyMatchInfo(List<MatchInfoBase> flowMatches, MatchFieldType matchType, String... params) {
        List<MatchInfoBase> matches = flowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(matchType)).collect(Collectors.toList());

        for (MatchInfoBase baseMatch : matches) {
            verifyMatchValues((MatchInfo) baseMatch, params);
        }
    }

    private void verifyMatchValues(MatchInfo match, String... params) {
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

    private void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches, MatchFieldType matchType) {
        List<MatchInfoBase> result = flowMatches.stream()
                .filter(item -> ((MatchInfo) item).getMatchField().equals(matchType)).collect(Collectors.toList());

        Assert.assertTrue("unexpected match type " + matchType.name(), result.isEmpty());
    }

    @Test
    public void testgetLayer4MaskForRange_SinglePort() {
        Map<Integer, Integer> layer4MaskForRange = AclServiceOFFlowBuilder.getLayer4MaskForRange(1111, 1111);
        assertEquals("port L4 mask missing", 1, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_MultiplePorts() {
        Map<Integer, Integer> layer4MaskForRange = AclServiceOFFlowBuilder.getLayer4MaskForRange(1024, 2048);
        assertEquals("port L4 mask missing", 2, layer4MaskForRange.size());
    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_ExceedMin() {
        Map<Integer, Integer> layer4MaskForRange = AclServiceOFFlowBuilder.getLayer4MaskForRange(0, 1);
        assertEquals("port L4 mask missing", 1, layer4MaskForRange.size());

    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_ExceedMax() {
        Map<Integer, Integer> layer4MaskForRange = AclServiceOFFlowBuilder.getLayer4MaskForRange(1, 65536);
        assertEquals("Illegal ports range", 0, layer4MaskForRange.size());

    }

    @Test
    public void testgetLayer4MaskForRange_IllegalPortRange_MinGreaterThanMax() {
        Map<Integer, Integer> layer4MaskForRange = AclServiceOFFlowBuilder.getLayer4MaskForRange(8192, 4096);
        assertEquals("Illegal ports range", 0, layer4MaskForRange.size());
    }
}
