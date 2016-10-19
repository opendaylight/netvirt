/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.junit.Assert.assertTrue;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.genius.mdsalutil.NwConstants.IP_PROT_ICMP;
import static org.opendaylight.genius.mdsalutil.NwConstants.IP_PROT_TCP;
import static org.opendaylight.genius.mdsalutil.NwConstants.IP_PROT_UDP;
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;
import static org.opendaylight.netvirt.aclservice.tests.infra.AssertBuilderBeans.assertEqualBeans;
import static org.opendaylight.netvirt.aclservice.utils.AclConstants.IPV4_ALL_NETWORK;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import javax.inject.Inject;
import org.junit.Rule;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.netvirt.aclservice.tests.infra.DataBrokerPairsUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public class AclServiceTest {

    public @Rule GuiceRule guice = new GuiceRule(AclServiceModule.class, AclServiceTestModule.class);

    private static final String PORT_MAC_1 = "0D:AA:D8:42:30:F3";
    private static final String PORT_MAC_2 = "0D:AA:D8:42:30:F4";
    private static final String PORT_1 = "port1";
    private static final String PORT_2 = "port2";
    private static String SG_UUID  = "85cc3048-abc3-43cc-89b3-377341426ac5";
    private static String SR_UUID_1 = "85cc3048-abc3-43cc-89b3-377341426ac6";
    private static String SR_UUID_2 = "85cc3048-abc3-43cc-89b3-377341426ac7";
    private static String ELAN = "elan1";
    private static String IP_PREFIX_1 = "10.0.0.1/24";
    private static String IP_PREFIX_2 = "10.0.0.2/24";
    private static long ELAN_TAG = 5000L;

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
        assertFlows(FlowEntryObjects.expectedFlows(PORT_MAC_1));
    }

    @Test
    public void newInterfaceWithEtherTypeAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = new MatchesWithAceIp.Builder()
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());

        matches = new MatchesWithAceIp.Builder()
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.etherFlows());
    }

    @Test
    public void newInterfaceWithTcpDstAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(80, 80)
                .protocol(IP_PROT_TCP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID)).build());
        matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(80, 80)
                .protocol(IP_PROT_TCP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.tcpFlows());
    }

    @Test
    public void newInterfaceWithUdpDstAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(80, 80)
                .protocol(IP_PROT_UDP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .build());
        matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(80, 80)
                .protocol(IP_PROT_UDP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .newRemoteGroupId(new Uuid(SG_UUID)).build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.udpFlows());
    }

    @Test
    public void newInterfaceWithIcmpAcl() throws Exception {
        // Given
        setUpData();
        Matches matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(2, 3)
                .protocol(IP_PROT_ICMP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_1)
            .newMatches(matches)
            .newDirection(DirectionEgress.class)
            .newRemoteGroupId(new Uuid(SG_UUID)).build());
        matches = new MatchesWithAceIp.Builder()
                .destinationPortRange(2, 3)
                .protocol(IP_PROT_ICMP)
                .destRemoteIpPrefix(IPV4_ALL_NETWORK)
                .build().create();
        dataBrokerUtil.put(ImmutableIdentifiedAceBuilder.builder()
            .sgUuid(SG_UUID)
            .newRuleName(SR_UUID_2)
            .newMatches(matches)
            .newDirection(DirectionIngress.class)
            .build());

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.icmpFlows());
    }

    private void assertFlows(Iterable<FlowEntity> expectedFlows) {
        List<FlowEntity> flows = mdsalApiManager.getFlows();
        if (!Iterables.isEmpty(expectedFlows)) {
            assertTrue("No Flows created (bean wiring may be broken?)", !flows.isEmpty());
        }
        // TODO Support Iterable <-> List directly within XtendBeanGenerator
        List<FlowEntity> expectedFlowsAsNewArrayList = Lists.newArrayList(expectedFlows);
        assertEqualBeans(expectedFlowsAsNewArrayList, flows);
    }

    private void newAllowedAddressPair(String portName, String sgUuid, String ipAddress, String macAddress )
            throws TransactionCommitFailedException {
        AllowedAddressPairs allowedAddressPair = new AllowedAddressPairsBuilder()
                .setIpAddress(new IpPrefixOrAddress(new IpPrefix(ipAddress.toCharArray())))
                .setMacAddress(new MacAddress(macAddress))
                .build();

        dataBrokerUtil.put(ImmutableIdentifiedInterfaceWithAclBuilder.builder()
            .interfaceName(portName)
            .portSecurity(true)
            .addNewSecurityGroups(new Uuid(SG_UUID))
            .addIfAllowedAddressPairs(allowedAddressPair).build());

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

    public void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1 ,true);
        newElanInterface(ELAN, PORT_2, true);
        newAllowedAddressPair(PORT_1, SG_UUID, IP_PREFIX_1, PORT_MAC_1);
        newAllowedAddressPair(PORT_2, SG_UUID, IP_PREFIX_2, PORT_MAC_2);
    }

}
