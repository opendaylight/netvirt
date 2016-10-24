/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.utils.cache.CacheUtil;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;


public class AclServiceTestUtils {

    public static void verifyGeneralFlows(List<MatchInfoBase> srcFlowMatches, String protocol, String srcIpv4Net,
            String dstIpv4Net, String mask) {
        assertTrue(srcFlowMatches.contains(MatchEthernetType.IPV4));
        assertTrue(srcFlowMatches.contains(new MatchIpProtocol(Short.parseShort(protocol))));
        assertTrue(srcFlowMatches.contains(new MatchIpv4Source(srcIpv4Net, mask)));
        assertTrue(srcFlowMatches.contains(new MatchIpv4Destination(dstIpv4Net, mask)));
    }

    public static AceIpBuilder prepareAceIpBuilder(String srcIpv4Net, String dstIpv4Net, String lowerPort,
            String upperPort, short protocol) {
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

    public static void verifyMatchInfo(List<MatchInfoBase> flowMatches, NxMatchFieldType matchType, String... params) {
        List<MatchInfoBase> matches = flowMatches.stream().filter(
            item -> item instanceof NxMatchInfo && ((NxMatchInfo) item).getMatchField().equals(matchType)).collect(
                Collectors.toList());
        assertFalse(matches.isEmpty());
        for (MatchInfoBase baseMatch : matches) {
            verifyMatchValues((NxMatchInfo)baseMatch, params);
        }
    }

    public static void verifyMatchValues(NxMatchInfo match, String... params) {
        switch (match.getMatchField()) {
            case nx_tcp_src_with_mask:
            case nx_tcp_dst_with_mask:
            case nx_udp_src_with_mask:
            case nx_udp_dst_with_mask:
            case ct_state:
                long[] values = Arrays.stream(params).mapToLong(l -> Long.parseLong(l)).toArray();
                Assert.assertArrayEquals(values, match.getMatchValues());
                break;
            default:
                assertTrue("match type is not supported", false);
                break;
        }
    }

    public static void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches,
            Class<? extends MatchInfo> matchType) {
        Assert.assertFalse("unexpected match type " + matchType.getSimpleName(), flowMatches.stream().anyMatch(
            item -> matchType.isAssignableFrom(item.getClass())));
    }

    public static void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches, NxMatchFieldType matchType) {
        Assert.assertFalse("unexpected match type " + matchType.name(), flowMatches.stream().anyMatch(
            item -> item instanceof NxMatchInfo && ((NxMatchInfo) item).getMatchField().equals(matchType)));
    }

    public static void prepareAclDataUtil(AclDataUtil aclDataUtil, AclInterface inter, String... updatedAclNames) {
        aclDataUtil.addAclInterfaceMap(prapreaAclIds(updatedAclNames), inter);
    }

    public static Acl prepareAcl(String aclName, String... aces) {
        AccessListEntries aceEntries = mock(AccessListEntries.class);
        List<Ace> aceList = prepareAceList(aces);
        when(aceEntries.getAce()).thenReturn(aceList);

        Acl acl = mock(Acl.class);
        when(acl.getAccessListEntries()).thenReturn(aceEntries);
        when(acl.getAclName()).thenReturn(aclName);
        return acl;
    }

    public static List<Ace> prepareAceList(String... aces) {
        List<Ace> aceList = new ArrayList<>();
        for (String aceName : aces) {
            Ace aceMock = mock(Ace.class);
            when(aceMock.getRuleName()).thenReturn(aceName);
            aceList.add(aceMock);
        }
        return aceList;
    }

    public static List<Uuid> prapreaAclIds(String... names) {
        return Stream.of(names).map(name -> new Uuid(name)).collect(Collectors.toList());
    }

    public static void verifyActionTypeExist(InstructionInfo instructionInfo, Class<? extends ActionInfo> actionType) {
        if (instructionInfo instanceof InstructionApplyActions) {
            verifyActionTypeExist(((InstructionApplyActions) instructionInfo).getActionInfos(), actionType);
        }
    }

    public static void verifyActionTypeExist(List<ActionInfo> flowActions, Class<? extends ActionInfo> actionType) {
        assertTrue(flowActions.stream().anyMatch(actionInfo -> actionInfo.getClass().equals(actionType)));
    }

    public static void verifyActionInfo(InstructionInfo instructionInfo, ActionInfo actionInfo) {
        if (instructionInfo instanceof InstructionApplyActions) {
            verifyActionInfo(((InstructionApplyActions) instructionInfo).getActionInfos(), actionInfo);
        }
    }

    public static void verifyActionInfo(List<ActionInfo> flowActions, ActionInfo actionInfo) {
        assertTrue(flowActions.contains(actionInfo));
    }

    public static void verifyActionLearn(InstructionInfo instructionInfo, ActionLearn actionLearn) {
        if (instructionInfo instanceof InstructionApplyActions) {
            verifyActionLearn(((InstructionApplyActions) instructionInfo).getActionInfos(), actionLearn);
        }
    }

    public static void verifyActionLearn(List<ActionInfo> flowActions, ActionLearn actionLearn) {
        for (ActionInfo actionInfo : flowActions) {
            if (actionInfo instanceof ActionLearn) {
                ActionLearn check = (ActionLearn) actionInfo;
                assertEquals(actionLearn.getCookie(), check.getCookie());
                assertEquals(actionLearn.getFinHardTimeout(), check.getFinHardTimeout());
                assertEquals(actionLearn.getFinIdleTimeout(), check.getFinIdleTimeout());
                assertEquals(actionLearn.getFlags(), check.getFlags());
                assertEquals(actionLearn.getHardTimeout(), check.getHardTimeout());
                assertEquals(actionLearn.getIdleTimeout(), check.getIdleTimeout());
                assertEquals(actionLearn.getPriority(), check.getPriority());
                assertEquals(actionLearn.getTableId(), check.getTableId());
            }
        }
    }

    public static void prepareAclClusterUtil(String entityName) {
        if (CacheUtil.getCache("entity.owner.cache") == null) {
            CacheUtil.createCache("entity.owner.cache");
        }
        ConcurrentMap entityOwnerCache = CacheUtil.getCache("entity.owner.cache");
        if (entityOwnerCache != null) {
            entityOwnerCache.put(entityName, true);
        }

    }

}
