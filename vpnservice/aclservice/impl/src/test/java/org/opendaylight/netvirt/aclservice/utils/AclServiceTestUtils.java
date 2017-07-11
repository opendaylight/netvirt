/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

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

    public static void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches,
            Class<? extends MatchInfoBase> matchType) {
        Assert.assertFalse("unexpected match type " + matchType.getSimpleName(), flowMatches.stream().anyMatch(
            item -> matchType.isAssignableFrom(item.getClass())));
    }

    public static void prepareAclDataUtil(AclDataUtil aclDataUtil, AclInterface inter, String... updatedAclNames) {
        aclDataUtil.addAclInterfaceMap(prapreaAclIds(updatedAclNames), inter);
    }

    public static Acl prepareAcl(String aclName, boolean includeAug, String... aces) {
        AccessListEntries aceEntries = mock(AccessListEntries.class);
        List<Ace> aceList = prepareAceList(includeAug, aces);
        when(aceEntries.getAce()).thenReturn(aceList);

        Acl acl = mock(Acl.class);
        when(acl.getAccessListEntries()).thenReturn(aceEntries);
        when(acl.getAclName()).thenReturn(aclName);
        return acl;
    }

    public static List<Ace> prepareAceList(boolean includeAug, String... aces) {
        List<Ace> aceList = new ArrayList<>();
        for (String aceName : aces) {
            Ace aceMock = mock(Ace.class);
            when(aceMock.getRuleName()).thenReturn(aceName);
            if (includeAug) {
                when(aceMock.getAugmentation(SecurityRuleAttr.class)).thenReturn(mock(SecurityRuleAttr.class));
            }
            aceList.add(aceMock);
        }
        return aceList;
    }

    public static List<Uuid> prapreaAclIds(String... names) {
        return Stream.of(names).map(Uuid::new).collect(Collectors.toList());
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

    public static void prepareElanTag(ReadOnlyTransaction mockReadTx, Long elanTag) {
        InstanceIdentifier<ElanInterface> elanInterfaceKey =
                AclServiceUtils.getElanInterfaceConfigurationDataPathId(null);
        ElanInterfaceBuilder elanInterfaceBuilder = new ElanInterfaceBuilder();
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION, elanInterfaceKey))
                .thenReturn(Futures.immediateCheckedFuture(Optional.of(elanInterfaceBuilder.build())));

        InstanceIdentifier<ElanInstance> elanInstanceKey = AclServiceUtils.getElanInstanceConfigurationDataPath(null);
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder();
        elanInstanceBuilder.setElanTag(elanTag);
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION, elanInstanceKey))
                .thenReturn(Futures.immediateCheckedFuture(Optional.of(elanInstanceBuilder.build())));
    }

    /**
     * Deprecated await.
     * @deprecated just use asyncEventsWaiter.awaitEventsConsumption() directly
     */
    @Deprecated
    public static void waitABit(AsyncEventsWaiter asyncEventsWaiter) throws InterruptedException {
        asyncEventsWaiter.awaitEventsConsumption();
    }

    public static FlowEntity verifyMatchInfoInSomeFlow(MethodInvocationParamSaver<Future<?>> installFlowValueSaver,
            NxMatchInfo match) {
        for (int i = 0; i < installFlowValueSaver.getNumOfInvocations(); i++) {
            FlowEntity flow = (FlowEntity) installFlowValueSaver.getInvocationParams(i).get(1);
            if (flow.getMatchInfoList().contains(match)) {
                return flow;
            }
        }

        fail();
        return null;

    }

}
