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
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

/**
 * A generic {@link ClassifierRenderableEntry} implementation that supports all
 * the different render types.
 */
public final class ClassifierEntry implements ClassifierRenderableEntry {

    private enum EntryType {
        NODE_ENTRY_TYPE,
        INGRESS_INTERFACE_ENTRY_TYPE,
        PATH_ENTRY_TYPE,
        MATCH_ENTRY_TYPE,
        EGRESS_INTERFACE_ENTRY_TYPE
    }

    private final EntryType entryType;
    private final NodeId node;
    private final InterfaceKey interfaceKey;
    private final String connector;
    private final Matches matches;
    private final Long nsp;
    private final Short nsi;
    private final String destinationIp;

    private ClassifierEntry(EntryType entryType, NodeId node, InterfaceKey interfaceKey, String connector,
                            Matches matches, Long nsp, Short nsi, String destinationIp) {

        this.entryType = entryType;
        this.node = node;
        this.interfaceKey = interfaceKey;
        this.connector = connector;
        this.matches = matches;
        this.nsp = nsp;
        this.nsi = nsi;
        this.destinationIp = destinationIp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                entryType,
                node,
                interfaceKey,
                connector,
                matches,
                nsp,
                nsi,
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
        return Objects.equals(entryType, other.entryType)
                && Objects.equals(node, other.node)
                && Objects.equals(interfaceKey, other.interfaceKey)
                && Objects.equals(connector, other.connector)
                && Objects.equals(matches, other.matches)
                && Objects.equals(nsp, other.nsp)
                && Objects.equals(nsi, other.nsi)
                && Objects.equals(destinationIp, other.destinationIp);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("entryType", entryType)
                .add("node", node)
                .add("interfaceKey", interfaceKey)
                .add("connector", connector)
                .add("matches", matches)
                .add("nsp", nsp)
                .add("nsi", nsi)
                .add("destinationIp", destinationIp)
                .toString();
    }

    @Override
    public void render(ClassifierEntryRenderer classifierEntryRenderer) {
        switch (entryType) {
            case NODE_ENTRY_TYPE:
                classifierEntryRenderer.renderNode(node);
                break;
            case INGRESS_INTERFACE_ENTRY_TYPE:
                classifierEntryRenderer.renderIngress(interfaceKey);
                break;
            case PATH_ENTRY_TYPE:
                classifierEntryRenderer.renderPath(node, nsp, destinationIp);
                break;
            case MATCH_ENTRY_TYPE:
                classifierEntryRenderer.renderMatch(node, connector, matches, nsp, nsi, destinationIp);
                break;
            case EGRESS_INTERFACE_ENTRY_TYPE:
                classifierEntryRenderer.renderEgress(interfaceKey);
                break;
            default:
        }
    }

    @Override
    public void suppress(ClassifierEntryRenderer classifierEntryRenderer) {
        switch (entryType) {
            case NODE_ENTRY_TYPE:
                classifierEntryRenderer.suppressNode(node);
                break;
            case INGRESS_INTERFACE_ENTRY_TYPE:
                classifierEntryRenderer.suppressIngress(interfaceKey);
                break;
            case PATH_ENTRY_TYPE:
                classifierEntryRenderer.suppressPath(node, nsp, destinationIp);
                break;
            case MATCH_ENTRY_TYPE:
                classifierEntryRenderer.suppressMatch(node, connector, matches, nsp, nsi, destinationIp);
                break;
            case EGRESS_INTERFACE_ENTRY_TYPE:
                classifierEntryRenderer.suppressEgress(interfaceKey);
                break;
            default:
        }
    }

    /**
     * Build a {@code ClassifierEntry} supporting an ingress render type.
     *
     * @param interfaceKey the ingress interface.
     * @return the {@code ClassifierEntry}.
     */
    public static ClassifierEntry buildIngressEntry(InterfaceKey interfaceKey) {
        return new ClassifierEntry(EntryType.INGRESS_INTERFACE_ENTRY_TYPE, null, interfaceKey, null, null, null,
                null, null);
    }

    /**
     * Build a {@code ClassifierEntry} supporting an node render type.
     *
     * @param node the classifier node identifier.
     * @return the {@code ClassifierEntry}.
     */
    public static ClassifierEntry buildNodeEntry(NodeId node) {
        return new ClassifierEntry(EntryType.NODE_ENTRY_TYPE, node, null, null, null, null,
                null, null);
    }

    /**
     * Build a {@code ClassifierEntry} supporting a path render type.
     *
     * @param node the classifier node identifier.
     * @param nsp the path identifier.
     * @param destinationIp the ip address of the first service function.
     * @return the {@code ClassifierEntry}.
     */
    public static ClassifierEntry buildPathEntry(NodeId node, Long nsp, String destinationIp) {
        return new ClassifierEntry(EntryType.PATH_ENTRY_TYPE, node, null, null, null, nsp,
                null, destinationIp);
    }

    /**
     * Build a {@code ClassifierEntry} supporting an match render type.
     *
     * @param node the classifier node identifier.
     * @param connector the node connector for the ingress interface.
     * @param matches the ACL matches.
     * @param nsp the path identifier.
     * @param nsi the initial path index.
     * @param destinationIp the ip address of the first service function.
     * @return the {@code ClassifierEntry}.
     */
    public static ClassifierEntry buildMatchEntry(NodeId node, String connector, Matches matches, Long nsp, Short nsi,
            String destinationIp) {
        return new ClassifierEntry(EntryType.MATCH_ENTRY_TYPE, node, null, connector, matches, nsp,
                nsi, destinationIp);
    }

    /**
     * Build a {@code ClassifierEntry} supporting an egress render type.
     *
     * @param interfaceKey the egress interface key.
     * @return the {@code ClassifierEntry}.
     */
    public static ClassifierEntry buildEgressEntry(InterfaceKey interfaceKey) {
        return new ClassifierEntry(EntryType.EGRESS_INTERFACE_ENTRY_TYPE, null, interfaceKey, null, null, null,
                null, null);
    }
}
