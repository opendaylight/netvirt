/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessListsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEthBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfcBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationClassifierImplTest {

    private static final InstanceIdentifier<AccessLists> ACL_LISTS_IID = InstanceIdentifier.create(AccessLists.class);
    private static final String INPUT_INTERFACE = "input";
    private static final String RSP_NAME = "RSP";
    private static final String SF_INTERFACE = "sf";
    private static final NodeId NODE_ID = new NodeId("node");
    private static final String OUTPUT_INTERFACE = "output";
    private static final String SF_IP = "127.0.0.1";
    private static final String INPUT_CONNECTOR = "connector";
    private static final long PATH_ID = 100L;
    private static final short PATH_INDEX = (short) 254;
    private static final RenderedServicePath RSP = buildRsp();
    private static final NeutronNetwork NEUTRON_NETWORK = buildNeutronNetwork();
    private static final Matches MATCHES = buildMatches();
    private static final Actions ACTIONS = buildActions();

    @Mock
    private DataBroker dataBroker;

    @Mock
    private ReadOnlyTransaction readOnlyTransaction;

    @Mock
    private GeniusProvider geniusProvider;

    @Mock
    private SfcProvider sfcProvider;

    @Mock
    private NetvirtProvider netvirtProvider;

    private ConfigurationClassifierImpl configurationClassifier;

    private static NeutronNetwork buildNeutronNetwork() {
        return new NeutronNetworkBuilder().setNetworkUuid("network").build();
    }

    private static RenderedServicePath buildRsp() {
        return new RenderedServicePathBuilder()
                .setName(new RspName(RSP_NAME))
                .setPathId(PATH_ID)
                .setStartingIndex(PATH_INDEX)
                .build();
    }

    private static AccessLists buildAcls() {
        final Ace ace = new AceBuilder().setMatches(MATCHES).setActions(ACTIONS).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(ace)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        return new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();
    }

    private static Matches buildMatches() {
        final AceType aceType = new AceEthBuilder()
                .setDestinationMacAddress(new MacAddress("12:34:56:78:90:AB"))
                .build();
        return new MatchesBuilder().setAceType(aceType).addAugmentation(NeutronNetwork.class, NEUTRON_NETWORK).build();
    }

    private static Actions buildActions() {
        final RedirectToSfc redirectToSfc = new RedirectToSfcBuilder().setRspName(RSP_NAME).build();
        return new ActionsBuilder().addAugmentation(RedirectToSfc.class, redirectToSfc).build();
    }


    @Before
    public void setup() throws ReadFailedException {
        when(dataBroker.newReadOnlyTransaction()).thenReturn(readOnlyTransaction);
        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(buildAcls())));
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(NEUTRON_NETWORK))
                .thenReturn(Collections.singletonList(INPUT_INTERFACE));
        when(sfcProvider.getRenderedServicePath(RSP_NAME)).thenReturn(Optional.of(RSP));
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(RSP)).thenReturn(Optional.of(SF_INTERFACE));
        when(geniusProvider.getIpFromInterfaceName(SF_INTERFACE)).thenReturn(Optional.of(SF_IP));
        when(geniusProvider.getNodeIdFromLogicalInterface(INPUT_INTERFACE)).thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(INPUT_INTERFACE))
                .thenReturn(Optional.of(INPUT_CONNECTOR));
        when(geniusProvider.getInterfacesFromNode(NODE_ID)).thenReturn(Collections.singletonList(OUTPUT_INTERFACE));
        configurationClassifier = new ConfigurationClassifierImpl(geniusProvider,
                netvirtProvider, sfcProvider, dataBroker);
    }

    @Test(expected = Exception.class)
    public void getAllEntriesReadException() throws Exception {
        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateFailedCheckedFuture(new ReadFailedException("")));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }


    @Test
    public void getAllEntriesEmptyAcls() throws Exception {
        final AccessLists emptyAcls = new AccessListsBuilder().setAcl(Collections.emptyList()).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(emptyAcls)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullAccessListEntries() throws Exception {
        final Acl emptyAcl = new AclBuilder().build();
        final AccessLists aclsWithEmptyAcl;
        aclsWithEmptyAcl = new AccessListsBuilder().setAcl(Collections.singletonList(emptyAcl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithEmptyAcl)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullAce() throws Exception {
        final AccessListEntries nullAces = new AccessListEntriesBuilder().build();
        final Acl acl = new AclBuilder().setAccessListEntries(nullAces).build();
        final AccessLists aclsWithNullAces = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullAces)));
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesEmptyAce() throws Exception {
        final AccessListEntries emptyAces = new AccessListEntriesBuilder().setAce(Collections.emptyList()).build();
        final Acl acl = new AclBuilder().setAccessListEntries(emptyAces).build();
        final AccessLists aclsWithEmptyAces =  new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithEmptyAces)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullMatches() throws Exception {
        final Ace aceWithNullMatch = new AceBuilder().setActions(ACTIONS).build();
        final AccessListEntries aces;
        aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(aceWithNullMatch)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists aclsWithNullMatches;
        aclsWithNullMatches = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullMatches)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullActions() throws Exception {
        final Ace aceWithNullAction = new AceBuilder().setMatches(MATCHES).build();
        final AccessListEntries aces;
        aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(aceWithNullAction)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists aclsWithNullActions;
        aclsWithNullActions = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullActions)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullNeutronNetwork() throws Exception {
        final Matches matchesWithNullNetwork = new MatchesBuilder().build();
        final Ace ace = new AceBuilder().setMatches(matchesWithNullNetwork).setActions(ACTIONS).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(ace)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists aclsWithNullNetworks;
        aclsWithNullNetworks = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullNetworks)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRedirectToSfc() throws Exception {
        final Actions actionswithNullRedirect = new ActionsBuilder().build();
        final Ace ace = new AceBuilder().setMatches(MATCHES).setActions(actionswithNullRedirect).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(ace)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists aclsWithNullRedirects;
        aclsWithNullRedirects = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullRedirects)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRspName() throws Exception {
        final RedirectToSfc redirectWithNullRspName = new RedirectToSfcBuilder().build();
        final Actions actions;
        actions = new ActionsBuilder().addAugmentation(RedirectToSfc.class, redirectWithNullRspName).build();
        final Ace ace = new AceBuilder().setMatches(MATCHES).setActions(actions).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Collections.singletonList(ace)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists aclsWithNullRsp =  new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(aclsWithNullRsp)));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRsp() throws Exception {
        when(sfcProvider.getRenderedServicePath(RSP_NAME)).thenReturn(Optional.empty());

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullPathId() throws Exception {
        final RenderedServicePath rspWithNullPathId = new RenderedServicePathBuilder()
                .setName(new RspName(RSP_NAME))
                .setStartingIndex(PATH_INDEX)
                .build();

        when(sfcProvider.getRenderedServicePath(RSP_NAME)).thenReturn(Optional.of(rspWithNullPathId));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullIndex() throws Exception {
        final RenderedServicePath rspWithNullIndex = new RenderedServicePathBuilder()
                .setName(new RspName(RSP_NAME))
                .setPathId(PATH_ID)
                .build();

        when(sfcProvider.getRenderedServicePath(RSP_NAME)).thenReturn(Optional.of(rspWithNullIndex));

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullHopInterface() throws Exception {
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(RSP)).thenReturn(Optional.empty());

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullHopIp() throws Exception {
        when(geniusProvider.getIpFromInterfaceName(SF_INTERFACE)).thenReturn(Optional.empty());

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesEmptyNetworkInterfaces() throws Exception {
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(NEUTRON_NETWORK))
                .thenReturn(Collections.emptyList());

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullNode() throws Exception {
        when(geniusProvider.getNodeIdFromLogicalInterface(INPUT_INTERFACE)).thenReturn(Optional.empty());

        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntries() throws Exception {
        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();
        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleNodes() throws Exception {
        // mock new interface on network on a different node
        final String otherInput = "other_input";
        final NodeId otherNode = new NodeId("other_node");
        final String otherConnector = "other_connector";
        final String otherOutput = "other_output";
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(NEUTRON_NETWORK))
                .thenReturn(Arrays.asList(INPUT_INTERFACE, otherInput));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput)).thenReturn(Optional.of(otherNode));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));
        when(geniusProvider.getInterfacesFromNode(otherNode)).thenReturn(Collections.singletonList(otherOutput));

        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(otherNode, otherConnector, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, 100L, SF_IP),
                ClassifierEntry.buildPathEntry(otherNode, 100L, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildNodeEntry(otherNode),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE)),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(otherOutput))
        ));
    }

    @Test
    public void getAllEntriesMultipleInputs() throws Exception {
        // mock new interface on network on same node
        final String otherInput = "other_input";
        final String otherConnector = "other_connector";
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(NEUTRON_NETWORK))
                .thenReturn(Arrays.asList(ConfigurationClassifierImplTest.INPUT_INTERFACE, otherInput));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput))
                .thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));

        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(NODE_ID, otherConnector, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleNetworks() throws Exception {
        final String otherNetwork = "other_network";
        final String otherInput = "other_input";
        final String otherConnector = "other_connector";
        final NeutronNetwork neutronNetwork = new NeutronNetworkBuilder().setNetworkUuid(otherNetwork).build();
        final Matches otherMatches;
        otherMatches = new MatchesBuilder(MATCHES).addAugmentation(NeutronNetwork.class, neutronNetwork).build();
        final Ace ace1 = new AceBuilder().setActions(ACTIONS).setMatches(MATCHES).build();
        final Ace ace2 = new AceBuilder().setActions(ACTIONS).setMatches(otherMatches).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Arrays.asList(ace1, ace2)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists acls = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();

        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork))
                .thenReturn(Collections.singletonList(otherInput));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput))
                .thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));
        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(acls)));

        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(NODE_ID, otherConnector, otherMatches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleRedirects() throws Exception {
        final String otherRspName = "other_rsp";
        final String otherSf = "other_sf";
        final String otherSfIp = "127.0.0.2";
        final long otherPathId = 102L;
        final short otherPathIndex = (short) 254;
        final RedirectToSfc otherRedirectToSfc = new RedirectToSfcBuilder().setRspName(otherRspName).build();
        final Actions otherActions;
        otherActions = new ActionsBuilder().addAugmentation(RedirectToSfc.class, otherRedirectToSfc).build();
        final Ace ace1 = new AceBuilder().setActions(ACTIONS).setMatches(MATCHES).build();
        final Ace ace2 = new AceBuilder().setActions(otherActions).setMatches(MATCHES).build();
        final AccessListEntries aces = new AccessListEntriesBuilder().setAce(Arrays.asList(ace1, ace2)).build();
        final Acl acl = new AclBuilder().setAccessListEntries(aces).build();
        final AccessLists acls = new AccessListsBuilder().setAcl(Collections.singletonList(acl)).build();
        final RenderedServicePath otherRsp = new RenderedServicePathBuilder()
                .setName(new RspName(otherRspName))
                .setPathId(otherPathId)
                .setStartingIndex(otherPathIndex)
                .build();

        when(sfcProvider.getRenderedServicePath(otherRspName)).thenReturn(Optional.of(otherRsp));
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(otherRsp)).thenReturn(Optional.of(otherSf));
        when(geniusProvider.getIpFromInterfaceName(otherSf)).thenReturn(Optional.of(otherSfIp));
        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, ACL_LISTS_IID))
                .thenReturn(Futures.immediateCheckedFuture(com.google.common.base.Optional.of(acls)));

        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(
                        NODE_ID, INPUT_CONNECTOR, MATCHES, otherPathId, otherPathIndex, otherSfIp),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, otherPathId, otherSfIp),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleOutputs() throws Exception {
        // mock additional output interface on node
        final String otherOutput = "other_output";
        when(geniusProvider.getInterfacesFromNode(NODE_ID)).thenReturn(Arrays.asList(OUTPUT_INTERFACE, otherOutput));

        final Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, MATCHES, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE)),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(otherOutput))
        ));
    }
}
