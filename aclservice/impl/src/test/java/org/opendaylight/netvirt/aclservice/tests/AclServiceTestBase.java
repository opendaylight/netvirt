/*
 * Copyright © 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.eclipse.xtext.xbase.lib.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorEventsWaiter;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.genius.testutils.TestInterfaceManager;
import org.opendaylight.infrautils.testutils.LogCaptureRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netvirt.aclservice.tests.infra.DataBrokerPairsUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AclServiceTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(AclServiceTestBase.class);

    public @Rule LogRule logRule = new LogRule();
    public @Rule LogCaptureRule logCaptureRule = new LogCaptureRule();

    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    static final String PORT_MAC_1 = "0D:AA:D8:42:30:F3";
    static final String PORT_MAC_2 = "0D:AA:D8:42:30:F4";
    static final String PORT_MAC_3 = "0D:AA:D8:42:30:F5";
    static final String PORT_MAC_4 = "0D:AA:D8:42:30:F6";
    static final String PORT_1 = "port1";
    static final String PORT_2 = "port2";
    static final String PORT_3 = "port3";
    static final String PORT_4 = "port4";
    static String SG_UUID = "85cc3048-abc3-43cc-89b3-377341426ac5";
    static String SR_UUID_1 = "85cc3048-abc3-43cc-89b3-377341426ac6";
    static String SR_UUID_2 = "85cc3048-abc3-43cc-89b3-377341426ac7";
    static String SG_UUID_1 = "85cc3048-abc3-43cc-89b3-377341426ac5";
    static String SG_UUID_2 = "85cc3048-abc3-43cc-89b3-377341426ac8";
    static String SR_UUID_1_1 = "85cc3048-abc3-43cc-89b3-377341426ac6";
    static String SR_UUID_1_2 = "85cc3048-abc3-43cc-89b3-377341426ac7";
    static String SR_UUID_2_1 = "85cc3048-abc3-43cc-89b3-377341426a21";
    static String SR_UUID_2_2 = "85cc3048-abc3-43cc-89b3-377341426a22";
    static String ELAN = "elan1";
    static String IP_PREFIX_1 = "10.0.0.1/32";
    static String IP_PREFIX_2 = "10.0.0.2/32";
    static String IP_PREFIX_3 = "10.0.0.3/32";
    static String IP_PREFIX_4 = "10.0.0.4/32";
    static long ELAN_TAG = 5000L;

    static String SUBNET_IP_PREFIX_1 = "10.0.0.0/24";
    static Uuid SUBNET_ID_1 = new Uuid("39add98b-63b7-42e6-8368-ff807eee165e");
    static SubnetInfo SUBNET_INFO_1 = buildSubnetInfo(SUBNET_ID_1, SUBNET_IP_PREFIX_1, IpVersionV4.class, "10.0.0.1");

    static final AllowedAddressPairs AAP_PORT_1 = buildAap(IP_PREFIX_1, PORT_MAC_1);
    static final AllowedAddressPairs AAP_PORT_2 = buildAap(IP_PREFIX_2, PORT_MAC_2);
    static final AllowedAddressPairs AAP_PORT_3 = buildAap(IP_PREFIX_3, PORT_MAC_3);
    static final AllowedAddressPairs AAP_PORT_4 = buildAap(IP_PREFIX_4, PORT_MAC_4);

    @Inject DataBroker dataBroker;
    @Inject DataBrokerPairsUtil dataBrokerUtil;
    SingleTransactionDataBroker singleTransactionDataBroker;
    @Inject TestIMdsalApiManager mdsalApiManager;
    @Inject AsyncEventsWaiter asyncEventsWaiter;
    @Inject JobCoordinatorEventsWaiter coordinatorEventsWaiter;
    @Inject TestInterfaceManager testInterfaceManager;

    @Before
    public void beforeEachTest() throws Exception {
        singleTransactionDataBroker = new SingleTransactionDataBroker(dataBroker);
        setUpData();
    }

    private InterfaceInfo newInterfaceInfo(String testInterfaceName) {
        InterfaceInfo interfaceInfo = new InterfaceInfo(BigInteger.valueOf(789), "port1");
        interfaceInfo.setInterfaceName(testInterfaceName);
        return interfaceInfo;
    }

    @Test
    public void newInterface() throws Exception {
        LOG.info("newInterface - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        testInterfaceManager.addInterfaceInfo(newInterfaceInfo("port1"));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName("port1").addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        // When
        putNewStateInterface(dataBroker, "port1", PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceCheck();
        LOG.info("newInterface - end");
    }

    abstract void newInterfaceCheck();

    @Test
    public void newInterfaceWithEtherTypeAcl() throws Exception {
        LOG.info("newInterfaceWithEtherTypeAcl - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_UNSPECIFIED,
                AclConstants.DEST_UPPER_PORT_UNSPECIFIED, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) -1);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_UNSPECIFIED, AclConstants.DEST_UPPER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) -1);
        dataBrokerUtil.put(
                new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2).newMatches(matches)
                        .newDirection(DirectionIngress.class).newRemoteGroupId(new Uuid(SG_UUID_1)).build());
        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithEtherTypeAclCheck();
        LOG.info("newInterfaceWithEtherTypeAcl - end");
    }

    abstract void newInterfaceWithEtherTypeAclCheck();

    @Test
    public void newInterfaceWithMultipleAcl() throws Exception {
        LOG.info("newInterfaceWithEtherTypeAcl - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_UNSPECIFIED,
                AclConstants.DEST_UPPER_PORT_UNSPECIFIED, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) -1);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_UNSPECIFIED, AclConstants.DEST_UPPER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) -1);
        dataBrokerUtil.put(
                new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2).newMatches(matches)
                        .newDirection(DirectionIngress.class).newRemoteGroupId(new Uuid(SG_UUID_1)).build());
        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithEtherTypeAclCheck();

        LOG.info("newInterfaceWithEtherTypeAcl - end");

        // Given
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_HTTP,
                AclConstants.DEST_UPPER_PORT_HTTP, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_1)
                .newMatches(matches).newDirection(DirectionEgress.class).newRemoteGroupId(new Uuid(SG_UUID_2)).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_HTTP, AclConstants.DEST_UPPER_PORT_HTTP,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_TCP);

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_2)
                .newMatches(matches).newDirection(DirectionIngress.class).build());
        List<String> sgList = new ArrayList<>();
        sgList.add(SG_UUID_1);
        sgList.add(SG_UUID_2);
        newAllowedAddressPair(PORT_1, sgList, Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, sgList, Collections.singletonList(AAP_PORT_2));

        asyncEventsWaiter.awaitEventsConsumption();
        newInterfaceWithMultipleAclCheck();
    }

    abstract void newInterfaceWithMultipleAclCheck();

    @Test
    public void newInterfaceWithTcpDstAcl() throws Exception {
        LOG.info("newInterfaceWithTcpDstAcl - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        // Given
        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_HTTP,
                AclConstants.DEST_UPPER_PORT_HTTP, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).newRemoteGroupId(new Uuid(SG_UUID_1)).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_HTTP, AclConstants.DEST_UPPER_PORT_HTTP,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_TCP);

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(matches).newDirection(DirectionIngress.class).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithTcpDstAclCheck();
        LOG.info("newInterfaceWithTcpDstAcl - end");
    }

    abstract void newInterfaceWithTcpDstAclCheck();

    @Test
    public void newInterfaceWithUdpDstAcl() throws Exception {
        LOG.info("newInterfaceWithUdpDstAcl - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        // Given
        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_HTTP,
                AclConstants.DEST_UPPER_PORT_HTTP, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).build());

        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_HTTP, AclConstants.DEST_UPPER_PORT_HTTP,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(
                new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2).newMatches(matches)
                        .newDirection(DirectionIngress.class).newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithUdpDstAclCheck();
        LOG.info("newInterfaceWithUdpDstAcl - end");
    }

    abstract void newInterfaceWithUdpDstAclCheck();

    @Test
    public void newInterfaceWithIcmpAcl() throws Exception {
        LOG.info("newInterfaceWithIcmpAcl - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        // Given
        prepareInterfaceWithIcmpAcl();

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithIcmpAclCheck();
        LOG.info("newInterfaceWithIcmpAcl - end");
    }

    abstract void newInterfaceWithIcmpAclCheck();

    @Test
    public void newInterfaceWithDstPortRange() throws Exception {
        LOG.info("newInterfaceWithDstPortRange - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        // Given
        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, 333, 777, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, 2000,
                2003, AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(matches).newDirection(DirectionIngress.class).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithDstPortRangeCheck();
        LOG.info("newInterfaceWithDstPortRange - end");
    }

    abstract void newInterfaceWithDstPortRangeCheck();

    @Test
    public void newInterfaceWithDstAllPorts() throws Exception {
        LOG.info("newInterfaceWithDstAllPorts - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        // Given
        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, 1, 65535, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).build());
        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, 1,
                65535, AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(matches).newDirection(DirectionIngress.class).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithDstAllPortsCheck();
        LOG.info("newInterfaceWithDstAllPorts - end");
    }

    abstract void newInterfaceWithDstAllPortsCheck();

    @Test
    public void newInterfaceWithTwoAclsHavingSameRules() throws Exception {
        LOG.info("newInterfaceWithTwoAclsHavingSameRules - start");

        newAllowedAddressPair(PORT_3, Arrays.asList(SG_UUID_1, SG_UUID_2), Collections.singletonList(AAP_PORT_3));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_3).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        // Given
        Matches icmpEgressMatches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_2,
                AclConstants.DEST_UPPER_PORT_3, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_ICMP);
        Matches icmpIngressMatches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_2,
                AclConstants.DEST_UPPER_PORT_3, AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED, (short) NwConstants.IP_PROT_ICMP);

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(icmpEgressMatches).newDirection(DirectionEgress.class).build());

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(icmpIngressMatches).newDirection(DirectionIngress.class).build());

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_1)
                .newMatches(icmpEgressMatches).newDirection(DirectionEgress.class).build());

        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_2).newRuleName(SR_UUID_2_2)
                .newMatches(icmpIngressMatches).newDirection(DirectionIngress.class).build());

        // When
        putNewStateInterface(dataBroker, PORT_3, PORT_MAC_3);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithTwoAclsHavingSameRulesCheck();
        LOG.info("newInterfaceWithTwoAclsHavingSameRules - end");
    }

    abstract void newInterfaceWithTwoAclsHavingSameRulesCheck();

    @Test
    public void newInterfaceWithIcmpAclHavingOverlappingMac() throws Exception {
        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_2));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        // Given
        prepareInterfaceWithIcmpAcl();

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithIcmpAclCheck();
    }

    @Test
    public void newInterfaceWithAapIpv4All() throws Exception {
        LOG.info("newInterfaceWithAapIpv4All test - start");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1),
                Arrays.asList(AAP_PORT_2, buildAap(AclConstants.IPV4_ALL_NETWORK, PORT_MAC_2)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        prepareInterfaceWithIcmpAcl();
        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithAapIpv4AllCheck();
        LOG.info("newInterfaceWithAapIpv4All test - end");
    }

    abstract void newInterfaceWithAapIpv4AllCheck();

    @Test
    public void newInterfaceWithAap() throws Exception {
        LOG.info("newInterfaceWithAap test - start");

        // AAP with same MAC and different IP
        AllowedAddressPairs aapWithSameMac = buildAap("10.0.0.100/32", PORT_MAC_2);
        // AAP with different MAC and different IP
        AllowedAddressPairs aapWithDifferentMac = buildAap("10.0.0.101/32", "0D:AA:D8:42:30:A4");

        newAllowedAddressPair(PORT_1, Collections.singletonList(SG_UUID_1), Collections.singletonList(AAP_PORT_1));
        newAllowedAddressPair(PORT_2, Collections.singletonList(SG_UUID_1),
                Arrays.asList(AAP_PORT_2, aapWithSameMac, aapWithDifferentMac));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_1).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));
        dataBrokerUtil.put(new IdentifiedPortSubnetBuilder().interfaceName(PORT_2).addAllSubnetInfo(
                Collections.singletonList(SUBNET_INFO_1)));

        prepareInterfaceWithIcmpAcl();
        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithAapCheck();
        LOG.info("newInterfaceWithAap test - end");
    }

    abstract void newInterfaceWithAapCheck();

    protected void assertFlowsInAnyOrder(Iterable<FlowEntity> expectedFlows) {
        asyncEventsWaiter.awaitEventsConsumption();
        coordinatorEventsWaiter.awaitEventsConsumption();
        mdsalApiManager.assertFlowsInAnyOrder(expectedFlows);
    }

    protected void prepareInterfaceWithIcmpAcl() throws TransactionCommitFailedException {
        // Given
        Matches matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED,
                AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED, AclConstants.DEST_LOWER_PORT_2,
                AclConstants.DEST_UPPER_PORT_3, AclConstants.SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED,
                AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED, (short) NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_1)
                .newMatches(matches).newDirection(DirectionEgress.class).newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        matches = newMatch(AclConstants.SOURCE_LOWER_PORT_UNSPECIFIED, AclConstants.SOURCE_UPPER_PORT_UNSPECIFIED,
                AclConstants.DEST_LOWER_PORT_2, AclConstants.DEST_UPPER_PORT_3,
                AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED, AclConstants.DEST_REMOTE_IP_PREFIX_UNSPECIFIED,
                (short) NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(new IdentifiedAceBuilder().sgUuid(SG_UUID_1).newRuleName(SR_UUID_1_2)
                .newMatches(matches).newDirection(DirectionIngress.class).build());
    }

    protected void newAllowedAddressPair(String portName, List<String> sgUuidList, List<AllowedAddressPairs> aapList)
            throws TransactionCommitFailedException {
        List<Uuid> sgList = sgUuidList.stream().map(Uuid::new).collect(Collectors.toList());
        Pair<DataTreeIdentifier<Interface>, Interface> port = new IdentifiedInterfaceWithAclBuilder()
                .interfaceName(portName)
                .portSecurity(true)
                .addAllNewSecurityGroups(sgList)
                .addAllIfAllowedAddressPairs(aapList).build();
        dataBrokerUtil.put(port);
        testInterfaceManager.addInterface(port.getValue());
    }

    protected void newElan(String elanName, long elanId) throws TransactionCommitFailedException {
        ElanInstance elan = new ElanInstanceBuilder().setElanInstanceName(elanName).setElanTag(5000L).build();
        singleTransactionDataBroker.syncWrite(CONFIGURATION,
                AclServiceUtils.getElanInstanceConfigurationDataPath(elanName), elan);
    }

    protected void newElanInterface(String elanName, String portName, boolean isWrite)
            throws TransactionCommitFailedException {
        ElanInterface elanInterface =
                new ElanInterfaceBuilder().setName(portName).setElanInstanceName(elanName).build();
        InstanceIdentifier<ElanInterface> id = AclServiceUtils.getElanInterfaceConfigurationDataPathId(portName);
        if (isWrite) {
            singleTransactionDataBroker.syncWrite(CONFIGURATION, id, elanInterface);
        } else {
            singleTransactionDataBroker.syncDelete(CONFIGURATION, id);
        }
    }

    // TODO refactor this instead of stealing it from org.opendaylight.netvirt.neutronvpn.NeutronSecurityRuleListener
    protected Matches newMatch(int srcLowerPort, int srcUpperPort, int destLowerPort, int destupperPort,
            int srcRemoteIpPrefix, int dstRemoteIpPrefix, short protocol) {

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        if (destLowerPort != -1) {
            DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
            destinationPortRangeBuilder.setLowerPort(new PortNumber(destLowerPort));
            destinationPortRangeBuilder.setUpperPort(new PortNumber(destupperPort));
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        if (srcRemoteIpPrefix == AclConstants.SOURCE_REMOTE_IP_PREFIX_SPECIFIED) {
            aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(AclConstants.IPV4_ALL_NETWORK));
        }
        if (dstRemoteIpPrefix == AclConstants.DEST_REMOTE_IP_PREFIX_SPECIFIED) {
            aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(AclConstants.IPV4_ALL_NETWORK));
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();
    }

    protected static AllowedAddressPairs buildAap(String ipAddress, String macAddress) {
        return new AllowedAddressPairsBuilder()
                .setIpAddress(new IpPrefixOrAddress(new IpPrefix(ipAddress.toCharArray())))
                .setMacAddress(new MacAddress(macAddress)).build();
    }

    protected static SubnetInfo buildSubnetInfo(Uuid subnetId, String ipPrefix,
            Class<? extends IpVersionBase> ipVersion, String gwIp) {
        return new SubnetInfoBuilder().setKey(new SubnetInfoKey(subnetId)).setIpVersion(ipVersion)
                .setIpPrefix(new IpPrefixOrAddress(ipPrefix.toCharArray()))
                .setGatewayIp(new IpAddress(gwIp.toCharArray())).build();
    }

    protected void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1, true);
        newElanInterface(ELAN, PORT_2, true);
        newElanInterface(ELAN, PORT_3, true);
        newElanInterface(ELAN, PORT_4, true);
    }

}
