/*
 * Copyright © 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import java.math.BigInteger;

import java.util.Collections;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpFlags;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceTestUtils;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.netvirt.aclservice.utils.MethodInvocationParamSaver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class LearnEgressAclServiceImplTest {

    LearnEgressAclServiceImpl testedService;

    @Mock DataBroker dataBroker;
    @Mock IMdsalApiManager mdsalManager;
    @Mock WriteTransaction mockWriteTx;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock AclserviceConfig config;
    @Mock IdManagerService idManager;

    MethodInvocationParamSaver<Void> installFlowValueSaver = null;
    MethodInvocationParamSaver<Void> removeFlowValueSaver = null;

    final Integer tcpFinIdleTimeoutValue = 60;

    @Before
    public void setUp() {
        AclDataUtil aclDataUtil = new AclDataUtil();
        AclServiceUtils aclServiceUtils = new AclServiceUtils(aclDataUtil, config, idManager);
        testedService = new LearnEgressAclServiceImpl(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
        installFlowValueSaver = new MethodInvocationParamSaver<>(null);
        doAnswer(installFlowValueSaver).when(mdsalManager).installFlow(any(FlowEntity.class));
        removeFlowValueSaver = new MethodInvocationParamSaver<>(null);
        doAnswer(installFlowValueSaver).when(mdsalManager).removeFlow(any(FlowEntity.class));
        doReturn(tcpFinIdleTimeoutValue).when(config).getSecurityGroupTcpFinIdleTimeout();
    }

    @Test
    public void addAcl__NullInterface() {
        assertEquals(false, testedService.applyAcl(null));
    }

    @Test
    public void addAcl__MissingInterfaceStateShouldFail() throws Exception {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setDpId(BigInteger.ONE);
        assertEquals(false, testedService.applyAcl(ai));
    }

    @Test
    public void addAcl__SinglePort() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.applyAcl(ai));
        assertEquals(10, installFlowValueSaver.getNumOfInvocations());

        FlowEntity flow = (FlowEntity) installFlowValueSaver.getInvocationParams(9).get(0);
        assertTrue(flow.getMatchInfoList().contains(new NxMatchTcpDestinationPort(80, 65535)));
        AclServiceTestUtils.verifyActionTypeExist(flow.getInstructionInfoList().get(0), ActionLearn.class);

        // verify that tcpFinIdleTimeout is used for TCP
        AclServiceTestUtils.verifyActionLearn(flow.getInstructionInfoList().get(0),
                new ActionLearn(
                        0,
                        0,
                        AclConstants.PROTO_MATCH_PRIORITY,
                        AclConstants.COOKIE_ACL_BASE,
                        AclConstants.LEARN_DELETE_LEARNED_FLAG_VALUE,
                        NwConstants.EGRESS_LEARN_TABLE,
                        tcpFinIdleTimeoutValue,
                        0,
                        Collections.emptyList()));
    }

    @Test
    public void addAcl__AllowAll() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubAllowAllInterface(sgUuid, "if_name");
        assertEquals(true, testedService.applyAcl(ai));
        assertEquals(10, installFlowValueSaver.getNumOfInvocations());

        FlowEntity flow = (FlowEntity) installFlowValueSaver.getInvocationParams(9).get(0);
        AclServiceTestUtils.verifyActionTypeExist(flow.getInstructionInfoList().get(0), ActionLearn.class);
    }

    @Test
    public void addAcl__MultipleRanges() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 84);
        assertEquals(true, testedService.applyAcl(ai));
        assertEquals(11, installFlowValueSaver.getNumOfInvocations());
        FlowEntity firstRangeFlow = (FlowEntity) installFlowValueSaver.getInvocationParams(9).get(0);
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new NxMatchTcpDestinationPort(80, 65532)));

        FlowEntity secondRangeFlow = (FlowEntity) installFlowValueSaver.getInvocationParams(10).get(0);
        assertTrue(secondRangeFlow.getMatchInfoList().contains(new NxMatchTcpDestinationPort(84, 65535)));
    }

    @Test
    public void addAcl__UdpSinglePortShouldNotCreateSynRule() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubUdpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.applyAcl(ai));
        assertEquals(10, installFlowValueSaver.getNumOfInvocations());
        FlowEntity flow = (FlowEntity) installFlowValueSaver.getInvocationParams(9).get(0);
        assertTrue(flow.getMatchInfoList().contains(new NxMatchUdpDestinationPort(80, 65535)));
        AclServiceTestUtils.verifyActionTypeExist(flow.getInstructionInfoList().get(0), ActionLearn.class);

        // verify that even though tcpFinIdleTimeout is set to non-zero, it is not used for UDP
        AclServiceTestUtils.verifyActionLearn(flow.getInstructionInfoList().get(0),
                new ActionLearn(
                        0,
                        0,
                        AclConstants.PROTO_MATCH_PRIORITY,
                        AclConstants.COOKIE_ACL_BASE,
                        AclConstants.LEARN_DELETE_LEARNED_FLAG_VALUE,
                        NwConstants.EGRESS_LEARN_TABLE,
                        0,
                        0,
                        Collections.emptyList()));
    }

    @Test
    @Ignore
    public void removeAcl__SinglePort() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.removeAcl(ai));
        assertEquals(5, removeFlowValueSaver.getNumOfInvocations());
        FlowEntity firstRangeFlow = (FlowEntity) removeFlowValueSaver.getInvocationParams(4).get(0);
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new MatchTcpFlags(2)));
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new NxMatchTcpDestinationPort(80, 65535)));
    }

    private AclInterface stubUdpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr,
            int tcpPortLower, int tcpPortUpper) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short)NwConstants.IP_PROT_UDP);
        return ai;
    }

    private AclInterface stubTcpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr,
            int tcpPortLower, int tcpPortUpper) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short)NwConstants.IP_PROT_TCP);
        return ai;
    }

    private void stubInterfaceAcl(String ifName, AclInterface ai) {
        AllowedAddressPairsBuilder aapb = new AllowedAddressPairsBuilder();
        aapb.setIpAddress(new IpPrefixOrAddress("1.1.1.1/32".toCharArray()));
        aapb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        ai.setAllowedAddressPairs(Collections.singletonList(aapb.build()));
    }

    private AclInterface stubAllowAllInterface(Uuid sgUuid, String ifName) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, null, -1, -1, (short)-1);
        return ai;
    }

    private void stubAccessList(Uuid sgUuid, String ipv4PrefixStr, int portLower, int portUpper, short protocol) {
        AclBuilder ab = new AclBuilder();
        ab.setAclName("AAA");
        ab.setKey(new AclKey(sgUuid.getValue(),Ipv4Acl.class));

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (portLower != -1 && portUpper != -1) {
            DestinationPortRangeBuilder dprb = new DestinationPortRangeBuilder();
            dprb.setLowerPort(new PortNumber(portLower));
            dprb.setUpperPort(new PortNumber(portUpper));
            aceIpBuilder.setDestinationPortRange(dprb.build());
        }
        if (ipv4PrefixStr != null) {
            AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
            Ipv4Prefix ipv4Prefix = new Ipv4Prefix(ipv4PrefixStr);
            aceIpv4Builder.setSourceIpv4Network(ipv4Prefix);
            aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        MatchesBuilder matches = new MatchesBuilder();
        matches.setAceType(aceIpBuilder.build());
        AceBuilder aceBuilder = new AceBuilder();
        ActionsBuilder actions = new ActionsBuilder().setPacketHandling(new PermitBuilder().build());
        aceBuilder.setActions(actions.build());
        aceBuilder.setMatches(matches.build());
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        securityRuleAttrBuilder.setDirection(DirectionEgress.class);
        aceBuilder.addAugmentation(SecurityRuleAttr.class, securityRuleAttrBuilder.build());
        AccessListEntriesBuilder aleb = new AccessListEntriesBuilder();
        aleb.setAce(Collections.singletonList(aceBuilder.build()));
        ab.setAccessListEntries(aleb.build());

        InstanceIdentifier<Acl> aclKey = AclServiceUtils.getAclInstanceIdentifier(sgUuid.getValue());
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION, aclKey))
            .thenReturn(Futures.immediateCheckedFuture(Optional.of(ab.build())));
    }
}
