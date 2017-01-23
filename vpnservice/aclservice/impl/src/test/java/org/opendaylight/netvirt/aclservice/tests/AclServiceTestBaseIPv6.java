/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;

import org.junit.Test;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeV6;

public abstract class AclServiceTestBaseIPv6 extends AclServiceTestBase {

    static String IPv6_PREFIX_1 = "2001:db8::1/64";
    static String IPv6_PREFIX_2 = "2001:db8::2/64";
    static String IPv6_PREFIX_3 = "2001:db8::3/64";

    @Override
    @Test
    public void newInterfaceWithEtherTypeAcl() throws Exception {
        Matches matches = newMatch(EthertypeV6.class, -1, -1,-1, -1,
            null, AclConstants.IPV6_ALL_NETWORK, (short)-1);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());

        matches = newMatch(EthertypeV6.class, -1, -1,-1, -1,
            AclConstants.IPV6_ALL_NETWORK, null, (short)-1);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithEtherTypeAclCheck();
    }

    @Override
    abstract void newInterfaceWithEtherTypeAclCheck();

    @Override
    @Test
    public void newInterfaceWithTcpDstAcl() throws Exception {
        // Given
        Matches matches = newMatch(EthertypeV6.class, -1, -1, 80, 80,
            null, AclConstants.IPV6_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());
        matches = newMatch(EthertypeV6.class, -1, -1, 80, 80,
            AclConstants.IPV6_ALL_NETWORK, null, (short)NwConstants.IP_PROT_TCP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithTcpDstAclCheck();
    }

    @Override
    abstract void newInterfaceWithTcpDstAclCheck();

    @Override
    @Test
    public void newInterfaceWithUdpDstAcl() throws Exception {
        // Given
        Matches matches = newMatch(EthertypeV6.class, -1, -1, 80, 80,
            null, AclConstants.IPV6_ALL_NETWORK, (short)NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());

        matches = newMatch(EthertypeV6.class, -1, -1, 80, 80,
            AclConstants.IPV6_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithUdpDstAclCheck();
    }

    @Override
    abstract void newInterfaceWithUdpDstAclCheck();

    @Override
    @Test
    public void newInterfaceWithIcmpAcl() throws Exception {
        // Given
        Matches matches = newMatch(EthertypeV6.class, -1, -1, 2, 3,
            null, AclConstants.IPV6_ALL_NETWORK, (short)NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID_1)).build());

        matches = newMatch( EthertypeV6.class, -1, -1, 2, 3,
            AclConstants.IPV6_ALL_NETWORK, null, (short)NwConstants.IP_PROT_ICMP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithIcmpAclCheck();
    }

    @Override
    abstract void newInterfaceWithIcmpAclCheck();

    @Override
    @Test
    public void newInterfaceWithDstPortRange() throws Exception {
        // Given
        Matches matches = newMatch(EthertypeV6.class, -1, -1, 333, 777,
            null, AclConstants.IPV6_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());
        matches = newMatch(EthertypeV6.class, -1, -1, 2000, 2003,
            AclConstants.IPV6_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithDstPortRangeCheck();
    }

    @Override
    abstract void newInterfaceWithDstPortRangeCheck();

    @Override
    @Test
    public void newInterfaceWithDstAllPorts() throws Exception {
        // Given
        Matches matches = newMatch(EthertypeV6.class, -1, -1, 1, 65535,
            null, AclConstants.IPV6_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());
        matches = newMatch(EthertypeV6.class, -1, -1, 1, 65535,
            AclConstants.IPV6_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);

        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID_1)
            .newRuleName(SR_UUID_1_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithDstAllPortsCheck();
    }

    @Override
    abstract void newInterfaceWithDstAllPortsCheck();

    @Override
    @Test
    public void newInterfaceWithTwoAclsHavingSameRules() throws Exception {
        // Given
        Matches icmpEgressMatches = newMatch(EthertypeV6.class, -1, -1, 2, 3, null, AclConstants.IPV6_ALL_NETWORK,
                (short) NwConstants.IP_PROT_ICMP);
        Matches icmpIngressMatches = newMatch(EthertypeV6.class, -1, -1, 2, 3, AclConstants.IPV6_ALL_NETWORK, null,
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

        asyncEventsWaiter.awaitEventsConsumption();

        // Then
        newInterfaceWithTwoAclsHavingSameRulesCheck();
    }

    @Override
    abstract void newInterfaceWithTwoAclsHavingSameRulesCheck();

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
        AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
        if (srcRemoteIpPrefix != null) {
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(srcRemoteIpPrefix));
        }
        if (dstRemoteIpPrefix != null) {
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(dstRemoteIpPrefix));
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        aceIpBuilder.setAceIpVersion(aceIpv6Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        return matchesBuilder.build();

    }
}
