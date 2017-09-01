/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceEthBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

@RunWith(MockitoJUnitRunner.class)
public class ClassifierEntryTest {

    @Mock
    private ClassifierEntryRenderer renderer;

    @Test
    public void equalsContract() throws Exception {
        new EqualsTester()
                .addEqualityGroup(buildIngressEntry(), buildIngressEntry())
                .addEqualityGroup(buildNodeEntry(), buildNodeEntry())
                .addEqualityGroup(buildMatchEntry(), buildMatchEntry())
                .addEqualityGroup(buildPathEntry(), buildPathEntry())
                .addEqualityGroup(buildEgressEntry(), buildEgressEntry())
                .testEquals();
    }

    private ClassifierEntry buildIngressEntry() {
        return ClassifierEntry.buildIngressEntry(new InterfaceKey("input"));
    }

    private ClassifierEntry buildEgressEntry() {
        return ClassifierEntry.buildEgressEntry(new InterfaceKey("output"), "127.0.0.1");
    }

    private ClassifierEntry buildNodeEntry() {
        return ClassifierEntry.buildNodeEntry(new NodeId("node"));
    }

    private ClassifierEntry buildPathEntry() {
        return ClassifierEntry.buildPathEntry(new NodeId("node"), 100L, (short) 254, (short) 253, "127.0.0.10");
    }

    private ClassifierEntry buildMatchEntry() {
        AceType aceType = new AceEthBuilder()
                .setDestinationMacAddress(new MacAddress("12:34:56:78:90:AB"))
                .build();
        Matches matches = new MatchesBuilder().setAceType(aceType).build();
        return ClassifierEntry.buildMatchEntry(
                new NodeId("node"),
                "connector",
                matches,
                100L,
                (short) 254);
    }

    @Test
    public void renderIngressEntry() throws Exception {
        InterfaceKey interfaceKey = new InterfaceKey("interface");
        ClassifierEntry entry = ClassifierEntry.buildIngressEntry(interfaceKey);
        entry.render(renderer);
        verify(renderer).renderIngress(interfaceKey);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void renderEgressEntry() throws Exception {
        InterfaceKey interfaceKey = new InterfaceKey("interface");
        ClassifierEntry entry = ClassifierEntry.buildEgressEntry(interfaceKey, "127.0.0.1");
        entry.render(renderer);
        verify(renderer).renderEgress(interfaceKey, "127.0.0.1");
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void renderNodeEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        ClassifierEntry entry = ClassifierEntry.buildNodeEntry(nodeId);
        entry.render(renderer);
        verify(renderer).renderNode(nodeId);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void renderPathEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        Long nsp = 2L;
        short nsi = (short) 254;
        short nsl = (short) 252;
        String firstHopIp = "127.0.0.1";
        ClassifierEntry entry = ClassifierEntry.buildPathEntry(nodeId, nsp, nsi, nsl, firstHopIp);
        entry.render(renderer);
        verify(renderer).renderPath(nodeId, nsp, nsi, nsl, firstHopIp);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void renderMatchEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        String connector = "openflow:0123456789:1";
        Long nsp = 2L;
        Short nsi = (short) 254;
        Matches matches = new MatchesBuilder().build();
        ClassifierEntry entry = ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi);
        entry.render(renderer);
        verify(renderer).renderMatch(nodeId, connector, matches, nsp, nsi);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressIngressEntry() throws Exception {
        InterfaceKey interfaceKey = new InterfaceKey("interface");
        ClassifierEntry entry = ClassifierEntry.buildIngressEntry(interfaceKey);
        entry.suppress(renderer);
        verify(renderer).suppressIngress(interfaceKey);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressEgressEntry() throws Exception {
        InterfaceKey interfaceKey = new InterfaceKey("interface");
        ClassifierEntry entry = ClassifierEntry.buildEgressEntry(interfaceKey, "127.0.0.1");
        entry.suppress(renderer);
        verify(renderer).suppressEgress(interfaceKey, "127.0.0.1");
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressNodeEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        ClassifierEntry entry = ClassifierEntry.buildNodeEntry(nodeId);
        entry.suppress(renderer);
        verify(renderer).suppressNode(nodeId);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressPathEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        Long nsp = 2L;
        short nsi = (short) 254;
        short nsl = (short) 252;
        String firstHopIp = "127.0.0.1";
        ClassifierEntry entry = ClassifierEntry.buildPathEntry(nodeId, nsp, nsi, nsl, firstHopIp);
        entry.suppress(renderer);
        verify(renderer).suppressPath(nodeId, nsp, nsi, nsl, firstHopIp);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressMatchEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        String connector = "openflow:0123456789:1";
        Long nsp = 2L;
        Short nsi = (short) 254;
        Matches matches = new MatchesBuilder().build();
        ClassifierEntry entry = ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi);
        entry.suppress(renderer);
        verify(renderer).suppressMatch(nodeId, connector, matches, nsp, nsi);
        verifyNoMoreInteractions(renderer);
    }

}
