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

/**
 * The interface of classifier entry renderers.
 *
 * <p>
 * There are different independent render types:
 * - Ingress (i.e. ingress bind to interface).
 * - Node (i.e. node initialization).
 * - Path (i.e. write path egress flows).
 * - Match (i.e. write ACL flow).
 * - Egress (i.e egress bind to interface).
 *
 * <p>
 * A renderer may not implement all the render types and is responsible to know
 * if the render applies to it or not (for example, an openflow renderer should
 * not write to a non openflow node).
 */
public interface ClassifierEntryRenderer {

    /**
     * Render ingress interface actions.
     *
     * @param interfaceKey the ingress interface key.
     */
    void renderIngress(InterfaceKey interfaceKey);

    /**
     * Render node wide actions.
     *
     * @param nodeId the classifier node identifier.
     */
    void renderNode(NodeId nodeId);

    /**
     * Render path based actions.
     *
     * @param nodeId the classifier node identifier.
     * @param nsp the path identifier.
     * @param nsi the path starting index.
     * @param nsl the path length.
     * @param firstHopIp the first SFF ip address. Null if the SFF is nodeId.
     */
    void renderPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp);

    /**
     * Rended match based actions.
     *
     * @param nodeId the classifier node identifier.
     * @param connector the node connector for the ingress interface.
     * @param matches the ACL matches.
     * @param nsp the path identifier.
     * @param nsi the initial path index.
     */
    void renderMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi);

    /**
     * Render egress interface actions.
     *
     * @param interfaceKey the egress interface key.
     * @param destinationIp the destination IP address associated to the
     *                      interface. If the interface is a local interface,
     *                      this should be a node local IP address, otherwise
     *                      the remote IP address.
     */
    void renderEgress(InterfaceKey interfaceKey, String destinationIp);

    /**
     * Suppress ingress interface actions.
     *
     * @param interfaceKey the ingress interface key.
     */
    void suppressIngress(InterfaceKey interfaceKey);

    /**
     * Suppress node wide actions.
     *
     * @param nodeId the classifier node identifier.
     */
    void suppressNode(NodeId nodeId);

    /**
     * Supress path based actions.
     *
     * @param nodeId the classifier node identifier.
     * @param nsp the path identifier.
     * @param nsi the path starting index.
     * @param nsl the path length.
     * @param firstHopIp the first SFF ip address. Null if the SFF is nodeId.
     */
    void suppressPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp);

    /**
     * Supress match based actions.
     *
     * @param nodeId the classifier node identifier.
     * @param connector the node connector for the ingress interface.
     * @param matches the ACL matches.
     * @param nsp the path identifier.
     * @param nsi the initial path index.
     */
    void suppressMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi);

    /**
     * Supress egress interface actions.
     *
     * @param interfaceKey the egress interface key.
     * @param destinationIp the destination IP address associated to the
     *                      interface. If the interface is a local interface,
     *                      this should be a node local IP address, otherwise
     *                      the remote IP address.
     */
    void suppressEgress(InterfaceKey interfaceKey, String destinationIp);
}
