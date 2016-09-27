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
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;
import static org.opendaylight.netvirt.aclservice.tests.infra.AssertBuilderBeans.assertEqualBeans;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mycila.guice.ext.closeable.CloseableInjector;
import java.util.List;
import java.util.UUID;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.EthertypeV4;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class AclServiceTest {

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
        Ace ace = newAce(SR_UUID_1, SG_UUID, null, DirectionEgress.class, EthertypeV4.class, -1, -1,-1, -1,
            null, AclConstants.IPV4_ALL_NETWORK, (short)-1);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(SR_UUID_1, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        ace = newAce(SR_UUID_2, SG_UUID, SG_UUID, DirectionIngress.class, EthertypeV4.class, -1, -1,-1, -1,
            AclConstants.IPV4_ALL_NETWORK, null, (short)-1);
        identifier = getAceInstanceIdentifier(SR_UUID_2, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

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
        Ace ace = newAce(SR_UUID_1, SG_UUID, SG_UUID, DirectionEgress.class, EthertypeV4.class, -1, -1, 80, 80,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_TCP);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(SR_UUID_1, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        ace = newAce(SR_UUID_2, SG_UUID, null, DirectionIngress.class, EthertypeV4.class, -1, -1, 80, 80,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_TCP);
        identifier = getAceInstanceIdentifier(SR_UUID_2, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

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
        Ace ace = newAce(SR_UUID_1, SG_UUID, null, DirectionEgress.class, EthertypeV4.class, -1, -1, 80, 80,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_UDP);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(SR_UUID_1, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        ace = newAce(SR_UUID_2, SG_UUID, SG_UUID, DirectionIngress.class, EthertypeV4.class, -1, -1, 80, 80,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_UDP);
        identifier = getAceInstanceIdentifier(SR_UUID_2, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

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
        Ace ace = newAce(SR_UUID_1, SG_UUID, SG_UUID, DirectionEgress.class, EthertypeV4.class, -1, -1, 2, 3,
            null, AclConstants.IPV4_ALL_NETWORK, (short)NwConstants.IP_PROT_ICMP);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(SR_UUID_1, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        ace = newAce(SR_UUID_2, SG_UUID, null, DirectionIngress.class, EthertypeV4.class, -1, -1, 2, 3,
            AclConstants.IPV4_ALL_NETWORK, null, (short)NwConstants.IP_PROT_ICMP);
        identifier = getAceInstanceIdentifier(SR_UUID_2, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        // When
        putNewStateInterface(dataBroker, PORT_1, PORT_MAC_1);
        putNewStateInterface(dataBroker, PORT_2, PORT_MAC_2);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.icmpFlows());
    }

    private void assertFlows(List<FlowEntity> expectedFlows) {
        List<FlowEntity> flows = mdsalApiManager.getFlows();
        if (!expectedFlows.isEmpty()) {
            assertTrue("No Flows created (bean wiring may be broken?)", !flows.isEmpty());
        }
        assertEqualBeans(expectedFlows, flows);
    }

    private void newAllowedAddressPair(String portName, String sgUuid, String ipAddress, String macAddress )
            throws TransactionCommitFailedException {
        AllowedAddressPairs allowedAddressPair = new AllowedAddressPairsBuilder()
                .setIpAddress(new IpPrefixOrAddress(new IpPrefix(ipAddress.toCharArray())))
                .setMacAddress(new MacAddress(macAddress))
                .build();

        dataBrokerUtil.put(ImmutableIdentifiedInterfaceWithAclBuilder.builder()
            .interfaceName("port1")
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

    // TODO refactor this instead of stealing it from org.opendaylight.netvirt.neutronvpn.NeutronSecurityRuleListener
    private Ace newAce(String slrUuid, String sgUuid, String remoteSgUuid,
            Class<? extends DirectionBase> newDirection, Class<? extends EthertypeBase> newEtherType,
            int srcLowerPort, int srcUpperPort, int destLowerPort, int destupperPort, String srcRemoteIpPrefix,
            String dstRemoteIpPrefix, short protocol) {
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        securityRuleAttrBuilder.setDirection(newDirection);
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
        if (remoteSgUuid != null) {
            securityRuleAttrBuilder.setRemoteGroupId(new Uuid(remoteSgUuid));
        }
        if (protocol != -1) {
            aceIpBuilder.setProtocol(protocol);
        }
        aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        // set acl action as permit for the security rule
        ActionsBuilder actionsBuilder = new ActionsBuilder();
        actionsBuilder.setPacketHandling(new PermitBuilder().setPermit(true).build());

        AceBuilder aceBuilder = new AceBuilder();
        aceBuilder.setKey(new AceKey(slrUuid));
        aceBuilder.setRuleName(slrUuid);
        aceBuilder.setMatches(matchesBuilder.build());
        aceBuilder.setActions(actionsBuilder.build());
        aceBuilder.addAugmentation(SecurityRuleAttr.class, securityRuleAttrBuilder.build());

        return aceBuilder.build();
    }

    private String generateUuid() {
        return UUID.randomUUID().toString();
    }

    private InstanceIdentifier<Ace> getAceInstanceIdentifier(String securityRuleUuid, String securityRuleGroupId) {
        return InstanceIdentifier
                .builder(AccessLists.class)
                .child(Acl.class,
                        new AclKey(securityRuleGroupId, Ipv4Acl.class))
                .child(AccessListEntries.class)
                .child(Ace.class,
                        new AceKey(securityRuleUuid))
                .build();
    }

    public void setUpData() throws Exception {
        newElan(ELAN, ELAN_TAG);
        newElanInterface(ELAN, PORT_1 ,true);
        newElanInterface(ELAN, PORT_2, true);
        newAllowedAddressPair(PORT_1, SG_UUID, IP_PREFIX_1, PORT_MAC_1);
        newAllowedAddressPair(PORT_2, SG_UUID, IP_PREFIX_2, PORT_MAC_2);
    }

    // TODO Replace below with https://git.opendaylight.org/gerrit/#/c/46041/

    private Injector injector;

    @Before
    public void setUp() {
        injector = Guice.createInjector(new AclServiceModule(), new AclServiceTestModule());
        injector.injectMembers(this);
        injector.getInstance(AclServiceManager.class);
    }

    @After
    public void tearDown() throws Exception {
        // http://code.mycila.com/guice/#3-jsr-250
        injector.getInstance(CloseableInjector.class).close();
    }
}
