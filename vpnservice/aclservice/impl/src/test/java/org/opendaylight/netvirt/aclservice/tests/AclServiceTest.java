/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;
import static org.opendaylight.netvirt.aclservice.tests.infra.AssertBuilderBeans.assertEqualBeans;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mycila.guice.ext.closeable.CloseableInjector;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.tests.infra.DataBrokerPairsUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeV4;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AclServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceTest.class);

    private static final String PORT_MAC_1 = "0D:AA:D8:42:30:F3";
    private static final String PORT_MAC_2 = "0D:AA:D8:42:30:F4";
    private static final String PORT_MAC_3 = "0D:AA:D8:42:30:F5";
    private static final String PORT_1 = "port1";
    private static final String PORT_2 = "port2";
    private static final String PORT_3 = "port3";
    protected static String SG_UUID_1  = "85cc3048-abc3-43cc-89b3-377341426ac5";
    protected static String SG_UUID_2  = "85cc3048-abc3-43cc-89b3-377341426ac8";
    private static String SR_UUID_1_1 = "85cc3048-abc3-43cc-89b3-377341426ac6";
    private static String SR_UUID_1_2 = "85cc3048-abc3-43cc-89b3-377341426ac7";
    private static String SR_UUID_2_1 = "85cc3048-abc3-43cc-89b3-377341426a21";
    private static String SR_UUID_2_2 = "85cc3048-abc3-43cc-89b3-377341426a22";
    private static String ELAN = "elan1";
    private static String IP_PREFIX_1 = "10.0.0.1/24";
    private static String IP_PREFIX_2 = "10.0.0.2/24";
    private static String IP_PREFIX_3 = "10.0.0.3/24";
    private static long ELAN_TAG = 5000L;

    protected static final Integer FLOW_PRIORITY_SG_1 = 1001;
    protected static final Integer FLOW_PRIORITY_SG_2 = 1002;

    @Inject DataBroker dataBroker;
    @Inject DataBrokerPairsUtil dataBrokerUtil;
    @Inject TestIMdsalApiManager mdsalApiManager;

    @Test
    public void newInterface() throws Exception {
        // Given
        // putNewInterface(dataBroker, "port1", true, Collections.emptyList(), Collections.emptyList());
        dataBrokerUtil.put(ImmutableIdentifiedInterfaceWithAclBuilder.builder()
                .interfaceName("port1")
                .portSecurity(true).build());

        // When
        putNewStateInterface(dataBroker, "port1", PORT_MAC_1);

        // TODO Later could do work for better synchronization here.
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.expectedFlows(PORT_MAC_1));
    }

    @Test
    public void newInterfaceWithEtherTypeAcl() throws Exception {
        // Given
        setUpData();

        Matches matches = newMatch(EthertypeV4.class, -1, -1,-1, -1,
            null, AclConstants.IPV4_ALL_NETWORK, (short)-1);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());

        matches = newMatch(EthertypeV4.class, -1, -1,-1, -1,
            AclConstants.IPV4_ALL_NETWORK, null, (short)-1);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.etherFlows());
    }

    @Test
    public void newInterfaceWithTcpDstAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = newMatch(EthertypeV4.class, -1, -1, 80, 80,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());
        matches = newMatch(EthertypeV4.class, -1, -1, 80, 80,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_TCP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.tcpFlows());
    }

    @Test
    public void newInterfaceWithUdpDstAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = newMatch(EthertypeV4.class, -1, -1, 80, 80,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());

        matches = newMatch(EthertypeV4.class, -1, -1, 80, 80,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.udpFlows());
    }

    @Test
    public void newInterfaceWithIcmpAcl() throws Exception {
        // Given
        setUpData();
        prepareInterfaceWithIcmpAcl();

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.icmpFlows());
    }

    @Test
    public void newInterfaceWithDstPortRange() throws Exception {
        // Given
        setUpData();
        Matches matches = newMatch(EthertypeV4.class, -1, -1, 333, 777,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());
        matches = newMatch(EthertypeV4.class, -1, -1, 2000, 2003,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.dstRangeFlows());
    }

    @Test
    public void newInterfaceWithDstAllPorts() throws Exception {
        // Given
        setUpData();
        Matches matches = newMatch(EthertypeV4.class, -1, -1, 1, 65535,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());
        matches = newMatch(EthertypeV4.class, -1, -1, 1, 65535,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.dstAllFlows());
    }

    @Test
    public void newInterfaceWithTwoAclsHavingSameRules() throws Exception {
        // Given
        setUpData();

        Matches icmpEgressMatches = newMatch(EthertypeV4.class, -1, -1, 2, 3, null, AclConstants.IPV4_ALL_NETWORK,
                (short) NwConstants.IP_PROT_ICMP);
        Matches icmpIngressMatches = newMatch(EthertypeV4.class, -1, -1, 2, 3, AclConstants.IPV4_ALL_NETWORK, null,
                (short) NwConstants.IP_PROT_ICMP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(icmpEgressMatches).newDirection(DirectionEgress.class).build());

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(icmpIngressMatches).newDirection(DirectionIngress.class).build());

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_1)
                .newMatches(icmpEgressMatches).newDirection(DirectionEgress.class).build());

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_2)
                .newMatches(icmpIngressMatches).newDirection(DirectionIngress.class).build());

        // When
        putNewStateInterface(dataBroker, PORT_3, PORT_MAC_3);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.icmpFlowsForTwoAclsHavingSameRules());
    }

    @Test
    public void newInterfaceWithIcmpAclHavingOverlappingMac() throws Exception {
        // Given
    	setUpData();
        prepareInterfaceWithIcmpAcl();

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_1);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlowsInAnyOrder(FlowEntryObjects.icmpFlows());
    }

    // TODO Remove this from here, use the one about to be merged in TestIMdsalApiManager
    // under https://git.opendaylight.org/gerrit/#/c/47842/ *BUT* remember to integrate
    // the ignore ordering fix recently added here to there...
    private void assertFlowsInAnyOrder(Iterable<FlowEntity> expectedFlows) {
        List<FlowEntity> flows = mdsalApiManager.getFlows();
        if (!Iterables.isEmpty(expectedFlows)) {
            assertTrue("No Flows created (bean wiring may be broken?)", !flows.isEmpty());
        }

        // TODO Support Iterable <-> List directly within XtendBeanGenerator
        List<FlowEntity> expectedFlowsAsNewArrayList = Lists.newArrayList(expectedFlows);

        // FYI: This containsExactlyElementsIn() assumes that FlowEntity, and everything in it,
        // has correctly working equals() implementations.  assertEqualBeans() does not assume
        // that, and would work even without equals, because it only uses property reflection.
        // Normally this will lead to the same result, but if one day it doesn't (because of
        // a bug in an equals() implementation somewhere), then it's worth to keep this diff
        // in mind.

        // FTR: This use of G Truth and then catch AssertionError and using assertEqualBeans iff NOK
        // (thus discarding the message from G Truth) is a bit of a hack, but it works well...
        // If you're tempted to improve this, please remember that correctly re-implementing
        // containsExactlyElementsIn (or Hamcrest's similar containsInAnyOrder) isn't a 1 line
        // trivia... e.g. a.containsAll(b) && b.containsAll(a) isn't sufficient, because it
        // won't work for duplicates (which we frequently have here); and ordering before is
        // not viable because FlowEntity is not Comparable, and Comparator based on hashCode
        // is not a good idea (different instances can have same hashCode), and e.g. on
        // System#identityHashCode even less so.
        try {
            LOG.info("expectedFlows = {}", expectedFlowsAsNewArrayList);
            LOG.info("flows = {}",flows);
            assertThat(flows).containsExactlyElementsIn(expectedFlowsAsNewArrayList);
        } catch (AssertionError e) {
            // The point of this is basically just that our assertEqualBeans output,
            // in case of a comparison failure, is *A LOT* more clearly readable
            // than what G Truth (or Hamcrest) can do based on toString.
            assertEqualBeans(expectedFlowsAsNewArrayList, flows);
        }
    }

    protected void prepareInterfaceWithIcmpAcl() throws TransactionCommitFailedException {
        // Given
        Matches matches = newMatch(EthertypeV4.class, -1, -1, 2, 3,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        matches = newMatch( EthertypeV4.class, -1, -1, 2, 3,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());
    }

    protected void newAllowedAddressPair(String portName, List<String> sgUuidList, String ipAddress, String macAddress)
            throws TransactionCommitFailedException {
        AllowedAddressPairs allowedAddressPair = new AllowedAddressPairsBuilder()
                .setIpAddress(new IpPrefixOrAddress(new IpPrefix(ipAddress.toCharArray())))
                .setMacAddress(new MacAddress(macAddress))
                .build();
        List<Uuid> sgList = sgUuidList.stream().map(sg -> new Uuid(sg)).collect(Collectors.toList());

        dataBrokerUtil.put(ImmutableIdentifiedInterfaceWithAclBuilder.builder()
            .interfaceName(portName)
            .portSecurity(true)
            .addAllNewSecurityGroups(sgList)
            .addIfAllowedAddressPair(allowedAddressPair).build());

    }

    private void newElan(String elanName, long elanId) {
        ElanInstance elan = new ElanInstanceBuilder().setElanInstanceName(elanName).setElanTag(5000L).build();
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION,
                AclServiceUtils.getElanInstanceConfigurationDataPath(elanName),
                elan);
    }

    private void newElanInterface(String elanName, String portName, boolean isWrite) {
        ElanInterface elanInterface = new ElanInterfaceBuilder().setName(portName)
                .setElanInstanceName(elanName).build();
        InstanceIdentifier<ElanInterface> id = AclServiceUtils.getElanInterfaceConfigurationDataPathId(portName);
        if (isWrite) {
            MDSALUtil.syncWrite(dataBroker, CONFIGURATION, id, elanInterface);
        } else {
            MDSALUtil.syncDelete(dataBroker, CONFIGURATION, id);
        }
    }

    // TODO refactor this instead of stealing it from org.opendaylight.netvirt.neutronvpn.NeutronSecurityRuleListener
    private Matches newMatch( Class<? extends EthertypeBase> newEtherType,
            int srcLowerPort, int srcUpperPort, int destLowerPort, int destupperPort, String srcRemoteIpPrefix,
            String dstRemoteIpPrefix, short protocol) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (destLowerPort != -1) {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destLowerPort));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destupperPort));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        if (srcRemoteIpPrefix != null) {
            aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(srcRemoteIpPrefix));
        }
        if (dstRemoteIpPrefix != null) {
            aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(dstRemoteIpPrefix));
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();

    }

    public void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1 ,true);
        newElanInterface(ELAN, PORT_2, true);
        newElanInterface(ELAN, PORT_3, true);
        newAllowedAddressPair(PORT_1, Arrays.asList(SG_UUID_1), IP_PREFIX_1, PORT_MAC_1);
        newAllowedAddressPair(PORT_2, Arrays.asList(SG_UUID_1), IP_PREFIX_2, PORT_MAC_2);
        newAllowedAddressPair(PORT_3, Arrays.asList(SG_UUID_1, SG_UUID_2), IP_PREFIX_3, PORT_MAC_3);
    }


    // TODO Factor this out into a.. Rule (or Runner, or superclass, but Rule is best)

    private Injector injector;

    @Before
    public void setUp() {
        injector = Guice.createInjector(new AclServiceTestModule());
        injector.injectMembers(this);
        injector.getInstance(AclServiceManager.class);
    }

    @After
    public void tearDown() throws Exception {
        // http://code.mycila.com/guice/#3-jsr-250
        injector.getInstance(CloseableInjector.class).close();
    }
}
