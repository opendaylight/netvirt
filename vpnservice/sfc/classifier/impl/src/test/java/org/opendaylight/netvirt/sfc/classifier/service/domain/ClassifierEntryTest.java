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

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

@RunWith(MockitoJUnitRunner.class)
public class ClassifierEntryTest {

    @Mock
    private ClassifierEntryRenderer renderer;

    @Test
    public void equalsContract() throws Exception {
        EqualsVerifier.forClass(ClassifierEntry.class).verify();
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
        ClassifierEntry entry = ClassifierEntry.buildEgressEntry(interfaceKey);
        entry.render(renderer);
        verify(renderer).renderEgress(interfaceKey);
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
        String ip = "127.0.0.1";
        ClassifierEntry entry = ClassifierEntry.buildPathEntry(nodeId, nsp, ip);
        entry.render(renderer);
        verify(renderer).renderPath(nodeId, nsp, ip);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void renderMatchEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        String connector = "openflow:0123456789:1";
        Long nsp = 2L;
        Short nsi = (short) 254;
        String ip = "127.0.0.1";
        Matches matches = new MatchesBuilder().build();
        ClassifierEntry entry = ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi, ip);
        entry.render(renderer);
        verify(renderer).renderMatch(nodeId, connector, matches, nsp, nsi, ip);
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
        ClassifierEntry entry = ClassifierEntry.buildEgressEntry(interfaceKey);
        entry.suppress(renderer);
        verify(renderer).suppressEgress(interfaceKey);
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
        String ip = "127.0.0.1";
        ClassifierEntry entry = ClassifierEntry.buildPathEntry(nodeId, nsp, ip);
        entry.suppress(renderer);
        verify(renderer).suppressPath(nodeId, nsp, ip);
        verifyNoMoreInteractions(renderer);
    }

    @Test
    public void suppressMatchEntry() throws Exception {
        NodeId nodeId = new NodeId("node");
        String connector = "openflow:0123456789:1";
        Long nsp = 2L;
        Short nsi = (short) 254;
        String ip = "127.0.0.1";
        Matches matches = new MatchesBuilder().build();
        ClassifierEntry entry = ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi, ip);
        entry.suppress(renderer);
        verify(renderer).suppressMatch(nodeId, connector, matches, nsp, nsi, ip);
        verifyNoMoreInteractions(renderer);
    }

}
