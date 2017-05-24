/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenflowRenderer implements ClassifierEntryRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenflowRenderer.class);

    private final OpenFlow13Provider openFlow13Provider;
    private final GeniusProvider geniusProvider;
    private final DataBroker dataBroker;

    public OpenflowRenderer(OpenFlow13Provider openFlow13Provider, GeniusProvider geniusProvider,
        DataBroker dataBroker) {
        this.openFlow13Provider = openFlow13Provider;
        this.geniusProvider = geniusProvider;
        this.dataBroker = dataBroker;
    }

    @Override
    public void renderIngress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    public void renderNode(NodeId nodeId) {
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createIngressClassifierFilterVxgpeNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterEthNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNoNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierAclNoMatchFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierFilterNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierFilterNoNshFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierNextHopC1C2Flow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierNextHopNoC1C2Flow(nodeId));

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx));
        tx.submit();
    }

    @Override
    public void renderPath(NodeId nodeId, Long nsp, String nodeIp) {
        Optional<Long> egressPort = geniusProvider.getEgressVxlanPortForNode(
                OpenFlow13Provider.getDpnIdFromNodeId(nodeId));
        if (!egressPort.isPresent()) {
            LOG.error("OpenflowRenderer: cant get egressPort for nodeId [{}]", nodeId.getValue());
            return;
        }
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createEgressClassifierTransportEgressRemoteFlow(
                nodeId, nsp, egressPort.get()));
        flows.add(this.openFlow13Provider.createEgressClassifierTransportEgressLocalFlow(nodeId, nsp, nodeIp));

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx));
        tx.submit();
    }

    @Override
    public void renderMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi, String destIp) {
        Long port = OpenFlow13Provider.getPortNoFromNodeConnector(connector);
        Flow flow = this.openFlow13Provider.createIngressClassifierAclFlow(
                nodeId, this.openFlow13Provider.getMatchBuilderFromAceMatches(matches), port, destIp, nsp, nsi);

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx);
        tx.submit();
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
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createIngressClassifierFilterVxgpeNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterEthNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNoNshFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierFilterNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierFilterNoNshFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierNextHopC1C2Flow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierNextHopNoC1C2Flow(nodeId));

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx));
        tx.submit();
    }

    @Override
    public void suppressPath(NodeId nodeId, Long nsp, String nodeIp) {
        Optional<Long> egressPort = geniusProvider.getEgressVxlanPortForNode(
                OpenFlow13Provider.getDpnIdFromNodeId(nodeId));
        if (!egressPort.isPresent()) {
            LOG.error("OpenflowRenderer: cant get egressPort for nodeId [{}]", nodeId.getValue());
            return;
        }
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createEgressClassifierTransportEgressRemoteFlow(
                nodeId, nsp, egressPort.get()));
        flows.add(this.openFlow13Provider.createEgressClassifierTransportEgressLocalFlow(nodeId, nsp, nodeIp));

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx));
        tx.submit();
    }

    @Override
    public void suppressMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi, String destIp) {
        Long port = OpenFlow13Provider.getPortNoFromNodeConnector(connector);
        Flow flow = this.openFlow13Provider.createIngressClassifierAclFlow(
                nodeId, this.openFlow13Provider.getMatchBuilderFromAceMatches(matches), port, destIp, nsp, nsi);

        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx);
        tx.submit();
    }

    @Override
    public void suppressEgress(InterfaceKey interfaceKey) {
        // noop
    }

}
