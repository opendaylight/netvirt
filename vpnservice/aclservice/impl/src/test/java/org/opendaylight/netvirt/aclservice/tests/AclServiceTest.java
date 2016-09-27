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
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.tests.infra.DataBrokerPairsUtil;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
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

    private static final String PORT_MAC = "0D:AA:D8:42:30:F3";
    private static final String SR_UUID = "85cc3048-abc3-43cc-89b3-377341426ac5";
    private static final String SG_UUID = "85cc3048-abc3-43cc-89b3-377341426ac6";

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
        putNewStateInterface(dataBroker, "port1", PORT_MAC);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertFlows(FlowEntryObjects.expectedFlows(PORT_MAC));
    }


    private void assertFlows(List<FlowEntity> expectedFlows) {
        List<FlowEntity> flows = mdsalApiManager.getFlows();
        if (!expectedFlows.isEmpty()) {
            assertTrue("No Flows created (bean wiring may be broken?)", !flows.isEmpty());
        }
        assertEqualBeans(expectedFlows, flows);
    }

    @Test
    public void newInterfaceWithSecurityGroup() throws Exception {
        // Given
        Ace ace = newAce(SR_UUID, SG_UUID, null, DirectionEgress.class, EthertypeV4.class, 80, 80,
                "0.0.0.0/0", (short)6);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(SR_UUID, SG_UUID);
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION, identifier, ace);

        ElanInstance elan = new ElanInstanceBuilder().setElanInstanceName("elan1").setElanTag(5000L).build();
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION,
                AclServiceUtils.getElanInstanceConfigurationDataPath("elan1"),
                elan);
        ElanInterface elanInterface = new ElanInterfaceBuilder().setName("port1").setElanInstanceName("elan1").build();
        MDSALUtil.syncWrite(dataBroker, CONFIGURATION,
                AclServiceUtils.getElanInterfaceConfigurationDataPathId("port1"),
                elanInterface);

        AllowedAddressPairs allowedAddressPair = new AllowedAddressPairsBuilder()
                    .setIpAddress(new IpPrefixOrAddress(new IpPrefix("10.0.0.1/24".toCharArray())))
                    .setMacAddress(new MacAddress(PORT_MAC))
                    .build();
        dataBrokerUtil.put(ImmutableIdentifiedInterfaceWithAclBuilder.builder()
            .interfaceName("port1")
            .portSecurity(true)
            .addNewSecurityGroups(new Uuid(SG_UUID))
            .addIfAllowedAddressPairs(allowedAddressPair).build());

        // When
        putNewStateInterface(dataBroker, "port1", PORT_MAC);

        // TODO Later could do work for better synchronization here..
        Thread.sleep(500);

        // Then
        assertEqualBeans(FlowEntryObjects.expectedFlows(PORT_MAC), mdsalApiManager.getFlows());
    }

    // TODO refactor this instead of stealing it from org.opendaylight.netvirt.neutronvpn.NeutronSecurityRuleListener
    private Ace newAce(String slrUuid, String sgUuid, String remoteSgUuid,
            Class<? extends DirectionBase> newDirection, Class<? extends EthertypeBase> newEtherType,
            int lowerPort, int upperPort, String remoteIpPrefix, short protocol) {
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        SourcePortRangeBuilder sourcePortRangeBuilder = new SourcePortRangeBuilder();
        DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
        boolean isDirectionIngress = false;
        securityRuleAttrBuilder.setDirection(newDirection);
        destinationPortRangeBuilder.setLowerPort(new PortNumber(lowerPort));
        destinationPortRangeBuilder.setUpperPort(new PortNumber(upperPort));

        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
        aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(remoteIpPrefix));
        if (remoteSgUuid != null) {
            securityRuleAttrBuilder.setRemoteGroupId(new Uuid(remoteSgUuid));
        }
        aceIpBuilder.setProtocol(protocol);

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
