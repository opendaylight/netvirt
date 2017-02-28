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
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpFlags;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.tests.AclServiceModule;
import org.opendaylight.netvirt.aclservice.tests.AclServiceTestModule;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

@RunWith(MockitoJUnitRunner.class)
public class StatelessEgressAclServiceImplTest {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateless), new TestableDataTreeChangeListenerModule());

    private static final Long ELAN_TAG = 500L;

    private static final long ACL_ID = 1L;

    StatelessEgressAclServiceImpl testedService;

    @Mock
    DataBroker dataBroker;
    @Mock
    IMdsalApiManager mdsalManager;
    @Mock
    WriteTransaction mockWriteTx;
    @Mock
    ReadOnlyTransaction mockReadTx;
    @Mock
    AclserviceConfig config;
    @Mock
    IdManagerService idManager;

    @Inject
    AsyncEventsWaiter asyncEventsWaiter;

    MethodInvocationParamSaver<Future<?>> installFlowValueSaver = null;
    MethodInvocationParamSaver<Future<?>> removeFlowValueSaver = null;

    @Before
    public void setUp() {
        AclDataUtil aclDataUtil = new AclDataUtil();
        ListenableFuture<RpcResult<AllocateIdOutput>> idResult = getAclIdResult(ACL_ID);
        doReturn(idResult).when(idManager).allocateId(any(AllocateIdInput.class));
        AclServiceUtils aclServiceUtils = new AclServiceUtils(aclDataUtil, config, idManager);
        testedService = new StatelessEgressAclServiceImpl(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();

        installFlowValueSaver = new MethodInvocationParamSaver<>(Futures.immediateCheckedFuture(null));
        doAnswer(installFlowValueSaver).when(mdsalManager).installFlow(any(BigInteger.class), any(FlowEntity.class));
        removeFlowValueSaver = new MethodInvocationParamSaver<>(Futures.immediateCheckedFuture(null));
        doAnswer(removeFlowValueSaver).when(mdsalManager).removeFlow(any(BigInteger.class), any(FlowEntity.class));
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
        AclServiceTestUtils.prepareElanTag(mockReadTx, ELAN_TAG);
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.applyAcl(ai));
        AclServiceTestUtils.waitABit(asyncEventsWaiter);
        assertEquals(10, installFlowValueSaver.getNumOfInvocations());

        FlowEntity firstRangeFlow = AclServiceTestUtils.verifyMatchInfoInSomeFlow(installFlowValueSaver,
                new NxMatchTcpDestinationPort(80, 65535));
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new MatchTcpFlags(2)));
        AclServiceTestUtils.verifyActionInfo(firstRangeFlow.getInstructionInfoList().get(0),
                new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));

    }

    @Test
    public void addAcl__AllowAll() throws Exception {
        AclServiceTestUtils.prepareElanTag(mockReadTx, ELAN_TAG);
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubAllowAllInterface(sgUuid, "if_name");
        assertEquals(true, testedService.applyAcl(ai));
        AclServiceTestUtils.waitABit(asyncEventsWaiter);
        assertEquals(10, installFlowValueSaver.getNumOfInvocations());

        FlowEntity firstRangeFlow = (FlowEntity) installFlowValueSaver.getInvocationParams(9).get(1);
        AclServiceTestUtils.verifyActionInfo(firstRangeFlow.getInstructionInfoList().get(0),
                new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
    }

    @Test
    public void addAcl__MultipleRanges() throws Exception {
        AclServiceTestUtils.prepareElanTag(mockReadTx, ELAN_TAG);
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 84);
        assertEquals(true, testedService.applyAcl(ai));
        AclServiceTestUtils.waitABit(asyncEventsWaiter);
        assertEquals(11, installFlowValueSaver.getNumOfInvocations());

        FlowEntity firstRangeFlow = AclServiceTestUtils.verifyMatchInfoInSomeFlow(installFlowValueSaver,
                new NxMatchTcpDestinationPort(80, 65532));
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new MatchTcpFlags(2)));

        FlowEntity secondRangeFlow = AclServiceTestUtils.verifyMatchInfoInSomeFlow(installFlowValueSaver,
                new NxMatchTcpDestinationPort(84, 65535));
        assertTrue(secondRangeFlow.getMatchInfoList().contains(new MatchTcpFlags(2)));
    }

    @Test
    public void addAcl__UdpSinglePortShouldNotCreateSynRule() throws Exception {
        AclServiceTestUtils.prepareElanTag(mockReadTx, ELAN_TAG);
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubUdpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.applyAcl(ai));
        AclServiceTestUtils.waitABit(asyncEventsWaiter);
        assertEquals(9, installFlowValueSaver.getNumOfInvocations());
    }

    @Test
    public void removeAcl__SinglePort() throws Exception {
        AclServiceTestUtils.prepareElanTag(mockReadTx, ELAN_TAG);
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        AclInterface ai = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        assertEquals(true, testedService.removeAcl(ai));
        AclServiceTestUtils.waitABit(asyncEventsWaiter);
        assertEquals(10, removeFlowValueSaver.getNumOfInvocations());
        FlowEntity firstRangeFlow = (FlowEntity)AclServiceTestUtils.verifyMatchInfoInSomeFlow(removeFlowValueSaver,
                new NxMatchTcpDestinationPort(80, 65535));
        assertTrue(firstRangeFlow.getMatchInfoList().contains(new MatchTcpFlags(2)));

    }

    private AclInterface stubUdpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr, int tcpPortLower,
            int tcpPortUpper) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short) NwConstants.IP_PROT_UDP);
        return ai;
    }

    private AclInterface stubTcpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr, int tcpPortLower,
            int tcpPortUpper) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short) NwConstants.IP_PROT_TCP);
        return ai;
    }

    private AclInterface stubAllowAllInterface(Uuid sgUuid, String ifName) {
        AclInterface ai = new AclInterface();
        ai.setPortSecurityEnabled(true);
        ai.setSecurityGroups(Collections.singletonList(sgUuid));
        ai.setDpId(BigInteger.ONE);
        ai.setLPortTag(2);
        stubInterfaceAcl(ifName, ai);

        stubAccessList(sgUuid, "0.0.0.0/0", -1, -1, (short) -1);
        return ai;
    }

    private void stubInterfaceAcl(String ifName, AclInterface ai) {
        AllowedAddressPairsBuilder aapb = new AllowedAddressPairsBuilder();
        aapb.setIpAddress(new IpPrefixOrAddress("1.1.1.1/32".toCharArray()));
        aapb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        ai.setAllowedAddressPairs(Collections.singletonList(aapb.build()));
    }

    private void stubAccessList(Uuid sgUuid, String ipv4PrefixStr, int portLower, int portUpper, short protocol) {
        AclBuilder ab = new AclBuilder();
        ab.setAclName("AAA");
        ab.setKey(new AclKey(sgUuid.getValue(), Ipv4Acl.class));

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

    private ListenableFuture<RpcResult<AllocateIdOutput>> getAclIdResult(Long id) {
        AllocateIdOutputBuilder output = new AllocateIdOutputBuilder();
        output.setIdValue(id);

        RpcResultBuilder<AllocateIdOutput> allocateIdRpcBuilder = RpcResultBuilder.success();
        allocateIdRpcBuilder.withResult(output.build());
        ListenableFuture<RpcResult<AllocateIdOutput>> idResult = Futures.immediateFuture(allocateIdRpcBuilder.build());
        return idResult;
    }
}
