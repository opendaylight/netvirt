/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import java.util.List;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

public class GeniusRenderer implements ClassifierEntryRenderer {

    private final GeniusProvider geniusProvider;

    public GeniusRenderer(GeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
    }

    @Override
    public void renderIngress(InterfaceKey interfaceKey) {
        geniusProvider.bindPortOnIngressClassifier(interfaceKey.getName());
    }

    @Override
    public void renderNode(NodeId nodeId) {
        // Egress services must bind to egress ports. Since we dont
        // know before-hand what the egress ports will be, we will
        // bind on all port switches. If the packet doesnt have NSH,
        // it will be returned to the the egress dispatcher table.
        List<String> interfaceUuidStrList = geniusProvider.getInterfacesFromNode(nodeId);
        interfaceUuidStrList.forEach(interfaceUuidStr -> geniusProvider.bindPortOnEgressClassifier(interfaceUuidStr));
    }

    @Override
    public void renderPath(NodeId nodeId, Long nsp, String ip) {
        // noop
    }

    @Override
    public void renderMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi, String ip) {
        // noop
    }

    @Override
    public void renderEgress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void suppressIngress(InterfaceKey interfaceKey) {
        geniusProvider.unbindPortOnIngressClassifier(interfaceKey.getName());
    }

    @Override
    public void suppressNode(NodeId nodeId) {
        // noop
    }

    @Override
    public void suppressPath(NodeId nodeId, Long nsp, String ip) {
        // noop
    }

    @Override
    public void suppressMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi, String ip) {
        // noop
    }

    @Override
    public void suppressEgress(InterfaceKey interfaceKey) {
        geniusProvider.unbindPortOnEgressClassifier(interfaceKey.getName());
    }
}
