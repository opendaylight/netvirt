/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.api;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

public interface ClassifierEntryRenderer {

    void renderIngress(InterfaceKey interfaceKey);

    void renderNode(NodeId nodeId);

    void renderPath(NodeId nodeId, Long nsp, String ip);

    void renderMatch(NodeId nodeId, Long port, Matches matches, Long nsp, Short nsi, String ip);

    void renderEgress(InterfaceKey interfaceKey);

    void suppressIngress(InterfaceKey interfaceKey);

    void suppressNode(NodeId nodeId);

    void suppressPath(NodeId nodeId, Long nsp, String ip);

    void suppressMatch(NodeId nodeId, Long port, Matches matches, Long nsp, Short nsi, String ip);

    void suppressEgress(InterfaceKey interfaceKey);
}
