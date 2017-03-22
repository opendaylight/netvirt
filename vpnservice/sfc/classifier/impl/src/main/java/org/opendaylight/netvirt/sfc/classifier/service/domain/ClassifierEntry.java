/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;

public final class ClassifierEntry {
    private final NodeKey sourceNode;
    private final InterfaceKey sourceInterface;
    private final Matches matches;
    private final Long nsp;
    private final Short nsi;
    private final NodeKey destinationNode;
    private final String destinationIp;

    public ClassifierEntry(NodeKey sourceNode, InterfaceKey sourceInterface, Matches matches, Long nsp,
                           Short nsi, NodeKey destinationNode, String destinationIp) {
        this.sourceNode = sourceNode;
        this.sourceInterface = sourceInterface;
        this.matches = matches;
        this.nsp = nsp;
        this.nsi = nsi;
        this.destinationNode = destinationNode;
        this.destinationIp = destinationIp;
    }

    public NodeKey getSourceNode() {
        return sourceNode;
    }

    public InterfaceKey getSourceInterface() {
        return sourceInterface;
    }

    public Matches getMatches() {
        return matches;
    }

    public Long getNsp() {
        return nsp;
    }

    public Short getNsi() {
        return nsi;
    }

    public NodeKey getDestinationNode() {
        return destinationNode;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceNode,
                sourceInterface,
                matches,
                nsp,
                nsi,
                destinationNode,
                destinationIp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj)) {
            return false;
        }
        if (!Objects.equals(obj.getClass(), ClassifierEntry.class)) {
            return false;
        }
        ClassifierEntry other = (ClassifierEntry) obj;
        return Objects.equals(sourceNode, other.sourceNode)
                && Objects.equals(sourceInterface, other.sourceInterface)
                && Objects.equals(matches, other.matches)
                && Objects.equals(nsp, other.nsp)
                && Objects.equals(nsi, other.nsi)
                && Objects.equals(destinationNode, other.destinationNode)
                && Objects.equals(destinationIp, other.destinationIp);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sourceNode", sourceNode)
                .add("sourceInterface", sourceInterface)
                .add("matches", matches)
                .add("nsp", nsp)
                .add("nsi", nsi)
                .add("destinationNode", destinationNode)
                .add("destinationIp", destinationIp)
                .toString();
    }
}
