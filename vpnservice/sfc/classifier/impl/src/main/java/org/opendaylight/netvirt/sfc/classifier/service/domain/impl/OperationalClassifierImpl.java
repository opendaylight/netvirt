/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

public class OperationalClassifierImpl implements ClassifierState {

    private final Set<ClassifierRenderableEntry> entries = new HashSet<>();

    @Override
    public Set<ClassifierRenderableEntry> getAllEntries() {
        return Collections.unmodifiableSet(entries);
    }

    public ClassifierEntryRenderer getRenderer() {
        return new ClassifierEntryRenderer() {

            @Override
            public void renderIngress(InterfaceKey interfaceKey) {
                entries.add(ClassifierEntry.buildIngressEntry(new InterfaceKey(interfaceKey)));
            }

            @Override
            public void renderNode(NodeId nodeId) {
                entries.add(ClassifierEntry.buildNodeEntry(nodeId));
            }

            @Override
            public void renderPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp) {
                entries.add(ClassifierEntry.buildPathEntry(nodeId, nsp, nsi, nsl, firstHopIp));
            }

            @Override
            public void renderMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi) {
                entries.add(ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi));
            }

            @Override
            public void renderEgress(InterfaceKey interfaceKey, String destinationIp) {
                entries.add(ClassifierEntry.buildEgressEntry(interfaceKey, destinationIp));
            }

            @Override
            public void suppressIngress(InterfaceKey interfaceKey) {
                entries.remove(ClassifierEntry.buildIngressEntry(new InterfaceKey(interfaceKey)));
            }

            @Override
            public void suppressNode(NodeId nodeId) {
                entries.remove(ClassifierEntry.buildNodeEntry(nodeId));
            }

            @Override
            public void suppressPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp) {
                entries.remove(ClassifierEntry.buildPathEntry(nodeId, nsp, nsi, nsl, firstHopIp));
            }

            @Override
            public void suppressMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi) {
                entries.remove(ClassifierEntry.buildMatchEntry(nodeId, connector, matches, nsp, nsi));
            }

            @Override
            public void suppressEgress(InterfaceKey interfaceKey, String destinationIp) {
                entries.remove(ClassifierEntry.buildEgressEntry(interfaceKey, destinationIp));
            }
        };
    }

}
