/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import org.opendaylight.netvirt.sfc.classifier.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public class OpenflowRenderer implements ClassifierRenderer {

    private final OpenFlow13Provider openFlow13Provider;

    public OpenflowRenderer(OpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    @Override
    public void render(ClassifierEntry entry) {
        openFlow13Provider.writeClassifierFlows(
                entry.getSourceNode().getId(),
                entry.getSourceInterface().getName(),
                // TODO should be entry.getMatches(), and get the MatchBuilder inside provider
                null,
                //TODO here we should also pass entry.getPathId
                // TODO should be entry.getDestinationNode().getId(); the node has been obtained previously which is
                // important in the case of SF mobility
                null
        );
    }

    @Override
    public void render(NodeKey nodeKey) {
        // TODO here we can initialize flows per node that needs to be done only once
    }

    @Override
    public void render(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void suppress(ClassifierEntry entry) {
        openFlow13Provider.removeClassifierFlows(
                entry.getSourceNode().getId(),
                entry.getSourceInterface().getName(),
                // TODO should be entry.getMatches(), and get the MatchBuilder inside provider
                null,
                //TODO here we should also pass entry.getPathId
                // TODO should be entry.getDestinationNode().getId(); the node has been obtained previously which is
                // important in the case of SF mobility
                null
        );

    }

    @Override
    public void suppress(NodeKey nodeKey) {
        // TODO here we can de-initialize flows per node that needs to be done only once
    }

    @Override
    public void suppress(InterfaceKey interfaceKey) {
        // noop
    }
}
