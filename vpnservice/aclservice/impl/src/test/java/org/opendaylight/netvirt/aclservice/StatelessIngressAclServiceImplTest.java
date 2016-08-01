package org.opendaylight.netvirt.aclservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceTestUtils;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.netvirt.aclservice.utils.MethodInvocationParamSaver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class StatelessIngressAclServiceImplTest {

    private StatelessIngressAclServiceImpl testedService;

    @Mock
    DataBroker dataBroker;
    @Mock
    IMdsalApiManager mdsalManager;
    @Mock
    OdlInterfaceRpcService interfaceManager;
    @Mock
    WriteTransaction mockWriteTx;
    @Mock
    ReadOnlyTransaction mockReadTx;

    @Before
    public void setUp() {
        testedService = new StatelessIngressAclServiceImpl(dataBroker, interfaceManager, mdsalManager);
        doReturn(Futures.immediateCheckedFuture(null)).when(mockWriteTx).submit();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
    }

    @Test
    public void addAcl__NullInterface() {
        assertEquals(false, testedService.applyAcl(null));
    }

    @Test
    public void addAcl__MissingInterfaceStateShouldFail() throws Exception {
        InterfaceBuilder ib = new InterfaceBuilder();
        ib.setName("if-name");
        InterfaceAclBuilder iab = new InterfaceAclBuilder();
        iab.setPortSecurityEnabled(true);
        ib.addAugmentation(InterfaceAcl.class, iab.build());
        assertEquals(false, testedService.applyAcl(ib.build()));

    }

    @Test
    public void addAcl__SinglePort() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        InterfaceBuilder ib = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        MethodInvocationParamSaver<Void> valueSaver = new MethodInvocationParamSaver<Void>(null);
        doAnswer(valueSaver).when(mdsalManager).installFlow(any(FlowEntity.class));
        assertEquals(true, testedService.applyAcl(ib.build()));
        assertEquals(1, valueSaver.getNumOfInvocations());

        FlowEntity firstRangeFlow = (FlowEntity) valueSaver.getInvocationParams(0).get(0);
        assertNotNull(AclServiceUtils.getMatchInfoByType(firstRangeFlow.getMatchInfoList(), MatchFieldType.tcp_dst));
        AclServiceTestUtils.verifyMatchInfo(firstRangeFlow.getMatchInfoList(), MatchFieldType.tcp_flags, "2");
        AclServiceTestUtils.verifyInstructionInfo(firstRangeFlow.getInstructionInfoList(), InstructionType.goto_table,
                "" + NwConstants.INGRESS_ACL_NEXT_TABLE_ID);
    }

    @Test
    public void addAcl__MultipleRanges() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        InterfaceBuilder ib = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 84);
        MethodInvocationParamSaver<Void> valueSaver = new MethodInvocationParamSaver<Void>(null);
        doAnswer(valueSaver).when(mdsalManager).installFlow(any(FlowEntity.class));
        assertEquals(true, testedService.applyAcl(ib.build()));
        assertEquals(2, valueSaver.getNumOfInvocations());
        FlowEntity firstRangeFlow = (FlowEntity) valueSaver.getInvocationParams(0).get(0);
        // should have been 80-83 will be fixed as part of the port range support
        // https://bugs.opendaylight.org/show_bug.cgi?id=6200
        AclServiceTestUtils.verifyMatchInfo(firstRangeFlow.getMatchInfoList(), MatchFieldType.tcp_dst, "80");
        AclServiceTestUtils.verifyMatchInfo(firstRangeFlow.getMatchInfoList(), MatchFieldType.tcp_flags, "2");

        FlowEntity secondRangeFlow = (FlowEntity) valueSaver.getInvocationParams(1).get(0);
        assertNotNull(AclServiceUtils.getMatchInfoByType(secondRangeFlow.getMatchInfoList(), MatchFieldType.tcp_dst));
        AclServiceTestUtils.verifyMatchInfo(secondRangeFlow.getMatchInfoList(), MatchFieldType.tcp_dst, "84");
        AclServiceTestUtils.verifyMatchInfo(secondRangeFlow.getMatchInfoList(), MatchFieldType.tcp_flags, "2");
    }

    @Test
    public void addAcl__UdpSinglePortShouldDoNothing() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        InterfaceBuilder ib = stubUdpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        MethodInvocationParamSaver<Void> valueSaver = new MethodInvocationParamSaver<Void>(null);
        doAnswer(valueSaver).when(mdsalManager).installFlow(any(FlowEntity.class));
        assertEquals(true, testedService.applyAcl(ib.build()));
        assertEquals(0, valueSaver.getNumOfInvocations());
    }

    @Test
    public void removeAcl__SinglePort() throws Exception {
        Uuid sgUuid = new Uuid("12345678-1234-1234-1234-123456789012");
        InterfaceBuilder ib = stubTcpAclInterface(sgUuid, "if_name", "1.1.1.1/32", 80, 80);
        MethodInvocationParamSaver<Void> valueSaver = new MethodInvocationParamSaver<Void>(null);
        doAnswer(valueSaver).when(mdsalManager).removeFlow(any(FlowEntity.class));
        assertEquals(true, testedService.removeAcl(ib.build()));
        assertEquals(1, valueSaver.getNumOfInvocations());
        FlowEntity firstSynFlow = (FlowEntity) valueSaver.getInvocationParams(0).get(0);
        assertNotNull(AclServiceUtils.getMatchInfoByType(firstSynFlow.getMatchInfoList(), MatchFieldType.tcp_dst));
        AclServiceTestUtils.verifyMatchInfo(firstSynFlow.getMatchInfoList(), MatchFieldType.tcp_flags,
                AclConstants.TCP_FLAG_SYN + "");

    }

    private InterfaceBuilder stubUdpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr,
            int tcpPortLower, int tcpPortUpper) {
        InterfaceBuilder ib = new InterfaceBuilder();
        ib.setName(ifName);
        InterfaceAclBuilder iab = new InterfaceAclBuilder();
        iab.setPortSecurityEnabled(true);
        iab.setSecurityGroups(Arrays.asList(sgUuid));
        stubInterfaceAcl(ifName, ib, iab);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short)NwConstants.IP_PROT_UDP);
        return ib;
    }

    private InterfaceBuilder stubTcpAclInterface(Uuid sgUuid, String ifName, String ipv4PrefixStr,
            int tcpPortLower, int tcpPortUpper) {
        InterfaceBuilder ib = new InterfaceBuilder();
        ib.setName(ifName);
        InterfaceAclBuilder iab = new InterfaceAclBuilder();
        iab.setPortSecurityEnabled(true);
        iab.setSecurityGroups(Arrays.asList(sgUuid));
        stubInterfaceAcl(ifName, ib, iab);

        stubAccessList(sgUuid, ipv4PrefixStr, tcpPortLower, tcpPortUpper, (short)NwConstants.IP_PROT_TCP);
        return ib;
    }

    private void stubInterfaceAcl(String ifName, InterfaceBuilder ib, InterfaceAclBuilder iab) {
        AllowedAddressPairsBuilder aapb = new AllowedAddressPairsBuilder();
        aapb.setIpAddress(new IpPrefixOrAddress("1.1.1.1/32".toCharArray()));
        aapb.setMacAddress(new MacAddress("AA:BB:CC:DD:EE:FF"));
        iab.setAllowedAddressPairs(Arrays.asList(aapb.build()));
        ib.addAugmentation(InterfaceAcl.class, iab.build());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .InterfaceBuilder isb = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.InterfaceBuilder();
        isb.setPhysAddress(new PhysAddress("AA:BB:CC:DD:EE:FF"));
        isb.setName(ifName);
        isb.setIfIndex(3);
        isb.setLowerLayerIf(Arrays.asList("1:1:1"));
        InstanceIdentifier<Interface> buildStateInterfaceId = AclServiceUtils.buildStateInterfaceId(ifName);
        when(mockReadTx.read(LogicalDatastoreType.OPERATIONAL, buildStateInterfaceId))
            .thenReturn(Futures.immediateCheckedFuture(Optional.of(isb.build())));

    }

    private void stubAccessList(Uuid sgUuid, String ipv4PrefixStr, int portLower, int portUpper, short protocol) {
        AclBuilder ab = new AclBuilder();
        ab.setAclName("AAA");
        ab.setKey(new AclKey(sgUuid.getValue(),Ipv4Acl.class));

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        DestinationPortRangeBuilder dprb = new DestinationPortRangeBuilder();
        dprb.setLowerPort(new PortNumber(portLower));
        dprb.setUpperPort(new PortNumber(portUpper));
        aceIpBuilder.setDestinationPortRange(dprb.build());
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        Ipv4Prefix ipv4Prefix = new Ipv4Prefix(ipv4PrefixStr);
        aceIpv4Builder.setDestinationIpv4Network(ipv4Prefix);
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());
        aceIpBuilder.setProtocol(protocol);
        MatchesBuilder matches = new MatchesBuilder();
        matches.setAceType(aceIpBuilder.build());
        AceBuilder aceBuilder = new AceBuilder();
        aceBuilder.setMatches(matches.build());
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        securityRuleAttrBuilder.setDirection(DirectionIngress.class);
        aceBuilder.addAugmentation(SecurityRuleAttr.class, securityRuleAttrBuilder.build());
        AccessListEntriesBuilder aleb = new AccessListEntriesBuilder();
        aleb.setAce(Arrays.asList(aceBuilder.build()));
        ab.setAccessListEntries(aleb.build());

        InstanceIdentifier<Acl> aclKey = AclServiceUtils.getAclInstanceIdentifier(sgUuid.getValue());
        when(mockReadTx.read(LogicalDatastoreType.CONFIGURATION, aclKey))
            .thenReturn(Futures.immediateCheckedFuture(Optional.of(ab.build())));
    }
}
