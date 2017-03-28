/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import org.opendaylight.netvirt.sfc.classifier.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

public class OpenflowRenderer implements ClassifierEntryRenderer {

    private final OpenFlow13Provider openFlow13Provider;

    public OpenflowRenderer(OpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    @Override
    public void renderIngress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void renderNode(NodeId nodeId) {
        // TODO
    }

    @Override
    public void renderPath(NodeId nodeId, Long nsp, String ip) {
        // TODO
    }

    @Override
    public void renderMatch(NodeId nodeId, Long port, Matches matches, Long nsp, Short nsi) {
        // TODO
    }

    @Override
    public void renderEgress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void suppressIngress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void suppressNode(NodeId nodeId) {
        // TODO
    }

    @Override
    public void suppressPath(NodeId nodeId, Long nsp, String ip) {
        // TODO
    }

    @Override
    public void suppressMatch(NodeId nodeId, Long port, Matches matches, Long nsp, Short nsi) {
        // TODO
    }

    @Override
    public void suppressEgress(InterfaceKey interfaceKey) {
        // noop
    }
}
