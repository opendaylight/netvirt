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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.CheckedFuture;
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
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationClassifierImplTest {

    private static final String INPUT_INTERFACE = "input";
    private static final String REDIRECT_RSP = "RSP";
    private static final String SF_INTERFACE = "sf";
    private static final NodeId NODE_ID = new NodeId("node");
    private static final String OUTPUT_INTERFACE = "output";
    private static final String SF_IP = "127.0.0.1";
    private static final String INPUT_CONNECTOR = "connector";
    private static final long PATH_ID = 100L;
    private static final short PATH_INDEX = (short) 254;

    @Mock
    private DataBroker dataBroker;

    @Mock
    private ReadOnlyTransaction readOnlyTransaction;

    @Mock
    private CheckedFuture<com.google.common.base.Optional<AccessLists>, ReadFailedException> checkedFuture;

    @Mock
    private com.google.common.base.Optional<AccessLists> optional;

    @Mock
    private AccessLists accessLists;

    @Mock
    private Acl acl;

    @Mock
    private AccessListEntries accessListEntries;

    @Mock
    private Ace ace;

    @Mock
    private Matches matches;

    @Mock
    private Actions actions;

    @Mock
    private RedirectToSfc redirectToSfc;

    @Mock
    private NeutronNetwork neutronNetwork;

    @Mock
    private GeniusProvider geniusProvider;

    @Mock
    private SfcProvider sfcProvider;

    @Mock
    private RenderedServicePath renderedServicePath;

    @Mock
    private NetvirtProvider netvirtProvider;

    private ConfigurationClassifierImpl configurationClassifier;

    @Before
    public void setup() throws ReadFailedException {
        // mock ACEs MDSAL read
        when(dataBroker.newReadOnlyTransaction()).thenReturn(readOnlyTransaction);
        when(readOnlyTransaction.read(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(AccessLists.class)))
                .thenReturn(checkedFuture);
        when(checkedFuture.checkedGet()).thenReturn(optional);
        when(optional.orNull()).thenReturn(accessLists);
        when(accessLists.getAcl()).thenReturn(Collections.singletonList(acl));
        when(acl.getAccessListEntries()).thenReturn(accessListEntries);
        when(accessListEntries.getAce()).thenReturn(Collections.singletonList(ace));

        // mock ACE
        when(ace.getMatches()).thenReturn(matches);
        when(ace.getActions()).thenReturn(actions);

        // mock neutron network
        when(matches.getAugmentation(NeutronNetwork.class)).thenReturn(neutronNetwork);
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork))
                .thenReturn(Collections.singletonList(INPUT_INTERFACE));

        // mock sfc redirect
        when(actions.getAugmentation(RedirectToSfc.class)).thenReturn(redirectToSfc);
        when(redirectToSfc.getRspName()).thenReturn(REDIRECT_RSP);
        when(sfcProvider.getRenderedServicePath(REDIRECT_RSP)).thenReturn(Optional.of(renderedServicePath));
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(renderedServicePath)).thenReturn(Optional.of(SF_INTERFACE));
        when(renderedServicePath.getPathId()).thenReturn(PATH_ID);
        when(renderedServicePath.getStartingIndex()).thenReturn(PATH_INDEX);

        // mock infrastructure
        when(geniusProvider.getIpFromInterfaceName(SF_INTERFACE)).thenReturn(Optional.of(SF_IP));
        when(geniusProvider.getNodeIdFromLogicalInterface(INPUT_INTERFACE)).thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(INPUT_INTERFACE))
                .thenReturn(Optional.of(INPUT_CONNECTOR));
        when(geniusProvider.getInterfacesFromNode(NODE_ID)).thenReturn(Collections.singletonList(OUTPUT_INTERFACE));

        configurationClassifier = new ConfigurationClassifierImpl(geniusProvider,
                netvirtProvider, sfcProvider, dataBroker);
    }

    @Test
    public void getAllEntriesNullAcls() throws Exception {
        when(optional.orNull()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesEmptyAcls() throws Exception {
        when(accessLists.getAcl()).thenReturn(Collections.emptyList());
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullAccessListEntries() throws Exception {
        when(acl.getAccessListEntries()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullAce() throws Exception {
        when(accessListEntries.getAce()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesEmptyAce() throws Exception {
        when(accessListEntries.getAce()).thenReturn(Collections.emptyList());
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullMatches() throws Exception {
        when(ace.getMatches()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullActions() throws Exception {
        when(ace.getActions()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRedirectToSfc() throws Exception {
        when(actions.getAugmentation(RedirectToSfc.class)).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRspName() throws Exception {
        when(redirectToSfc.getRspName()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullRsp() throws Exception {
        when(sfcProvider.getRenderedServicePath(REDIRECT_RSP)).thenReturn(Optional.empty());
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullPathId() throws Exception {
        when(renderedServicePath.getPathId()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullIndex() throws Exception {
        when(renderedServicePath.getStartingIndex()).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullHopInterface() throws Exception {
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(renderedServicePath)).thenReturn(Optional.empty());
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullHopIp() throws Exception {
        when(geniusProvider.getIpFromInterfaceName(SF_INTERFACE)).thenReturn(Optional.empty());
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesNullNeutronNetwork() throws Exception {
        when(matches.getAugmentation(NeutronNetwork.class)).thenReturn(null);
        assertThat(configurationClassifier.getAllEntries(), is(Collections.emptySet()));
    }

    @Test
    public void getAllEntriesEmptyNetworkInterfaces() throws Exception {
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork))
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
        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();
        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleNodes() throws Exception {
        // mock new interface on network on a different node
        String otherInput = "other_input";
        NodeId otherNode = new NodeId("other_node");
        String otherConnector = "other_connector";
        String otherOutput = "other_output";
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork))
                .thenReturn(Arrays.asList(INPUT_INTERFACE, otherInput));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput)).thenReturn(Optional.of(otherNode));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));
        when(geniusProvider.getInterfacesFromNode(otherNode)).thenReturn(Collections.singletonList(otherOutput));

        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(otherNode, otherConnector, matches, PATH_ID, PATH_INDEX, SF_IP),
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
        String otherInput = "other_input";
        String otherConnector = "other_connector";
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork))
                .thenReturn(Arrays.asList(ConfigurationClassifierImplTest.INPUT_INTERFACE, otherInput));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput))
                .thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));

        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(NODE_ID, otherConnector, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleNetworks() throws Exception {
        // mock new ACE to match an additional neutron network to same RSP
        String otherInput = "other_input";
        String otherConnector = "other_connector";
        Ace ace2 = mock(Ace.class);
        Matches matches2 = mock(Matches.class);
        NeutronNetwork neutronNetwork2 = mock(NeutronNetwork.class);
        when(ace2.getMatches()).thenReturn(matches2);
        when(ace2.getActions()).thenReturn(actions);
        when(matches2.getAugmentation(NeutronNetwork.class)).thenReturn(neutronNetwork2);
        when(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork2))
                .thenReturn(Collections.singletonList(otherInput));
        when(accessListEntries.getAce()).thenReturn(Arrays.asList(ace, ace2));
        when(geniusProvider.getNodeIdFromLogicalInterface(otherInput))
                .thenReturn(Optional.of(NODE_ID));
        when(geniusProvider.getNodeConnectorIdFromInterfaceName(otherInput)).thenReturn(Optional.of(otherConnector));

        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildIngressEntry(new InterfaceKey(otherInput)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(NODE_ID, otherConnector, matches2, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleRedirects() throws Exception {
        // mock new ACE to match an same neutron network to different RSP
        String otherRsp = "other_rsp";
        String otherSf = "other_sf";
        String otherSfIp = "127.0.0.2";
        long otherPathId = 102L;
        short otherPathIndex = (short) 254;
        Ace ace2 = mock(Ace.class);
        Actions actions2 = mock(Actions.class);
        RedirectToSfc redirectToSfc2 = mock(RedirectToSfc.class);
        RenderedServicePath renderedServicePath2 = mock(RenderedServicePath.class);
        when(ace2.getMatches()).thenReturn(matches);
        when(ace2.getActions()).thenReturn(actions2);
        when(actions2.getAugmentation(RedirectToSfc.class)).thenReturn(redirectToSfc2);
        when(redirectToSfc2.getRspName()).thenReturn(otherRsp);
        when(sfcProvider.getRenderedServicePath(otherRsp)).thenReturn(Optional.of(renderedServicePath2));
        when(sfcProvider.getFirstHopSfInterfaceFromRsp(renderedServicePath2)).thenReturn(Optional.of(otherSf));
        when(renderedServicePath2.getPathId()).thenReturn(otherPathId);
        when(renderedServicePath2.getStartingIndex()).thenReturn(otherPathIndex);
        when(geniusProvider.getIpFromInterfaceName(otherSf)).thenReturn(Optional.of(otherSfIp));
        when(accessListEntries.getAce()).thenReturn(Arrays.asList(ace, ace2));

        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildMatchEntry(
                        NODE_ID, INPUT_CONNECTOR, matches, otherPathId, otherPathIndex, otherSfIp),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, otherPathId, otherSfIp),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE))
        ));
    }

    @Test
    public void getAllEntriesMultipleOutputs() throws Exception {
        // mock additional output interface on node
        String otherOutput = "other_output";
        when(geniusProvider.getInterfacesFromNode(NODE_ID)).thenReturn(Arrays.asList(OUTPUT_INTERFACE, otherOutput));

        Set<ClassifierRenderableEntry> entries = configurationClassifier.getAllEntries();

        assertThat(entries, Matchers.containsInAnyOrder(
                ClassifierEntry.buildIngressEntry(new InterfaceKey(INPUT_INTERFACE)),
                ClassifierEntry.buildMatchEntry(NODE_ID, INPUT_CONNECTOR, matches, PATH_ID, PATH_INDEX, SF_IP),
                ClassifierEntry.buildPathEntry(NODE_ID, PATH_ID, SF_IP),
                ClassifierEntry.buildNodeEntry(NODE_ID),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(OUTPUT_INTERFACE)),
                ClassifierEntry.buildEgressEntry(new InterfaceKey(otherOutput))
        ));
    }
}
