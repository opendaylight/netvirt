/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
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
    private final ManagedNewTransactionRunner txRunner;

    public OpenflowRenderer(OpenFlow13Provider openFlow13Provider, GeniusProvider geniusProvider,
        DataBroker dataBroker) {
        this.openFlow13Provider = openFlow13Provider;
        this.geniusProvider = geniusProvider;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public void renderIngress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    // FindBugs reports "Useless object stored in variable flows" however it doesn't recognize the usage of forEach.
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public void renderNode(NodeId nodeId) {
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createIngressClassifierFilterTunnelNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterEthNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNoNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierAclNoMatchFlow(nodeId));

        flows.add(this.openFlow13Provider.createIngressClassifierTunnelEthNshTrafficCaptureFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierTunnelNshTrafficCaptureFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierFilterNoNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierFilterNshFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierNextHopFlow(nodeId));

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> flows.forEach(flow -> this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx))), LOG,
            "Error rendering node");
    }

    @Override
    // FindBugs reports "Useless object stored in variable flows" however it doesn't recognize the usage of forEach.
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public void renderPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp) {

        List<Flow> flows = new ArrayList<>();
        if (firstHopIp != null) {
            Long port = geniusProvider.getEgressVxlanPortForNode(OpenFlow13Provider.getDpnIdFromNodeId(nodeId))
                    .orElse(null);
            if (port == null) {
                LOG.error("OpenflowRenderer: cant get egressPort for nodeId [{}]", nodeId.getValue());
                return;
            }
            Flow flow;
            flow = openFlow13Provider.createEgressClassifierTransportEgressRemoteEthNshFlow(
                    nodeId, nsp, port, firstHopIp);
            flows.add(flow);
        } else {
            Flow flow;
            flow = openFlow13Provider.createEgressClassifierTransportEgressLocalFlow(nodeId, nsp);
            flows.add(flow);
        }
        short egressNsi = (short) (nsi - nsl);
        flows.add(openFlow13Provider.createIngressClassifierFilterChainEgressFlow(nodeId, nsp, egressNsi));
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> flows.forEach(flow -> this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx))), LOG,
            "Error rendering a path");
    }

    @Override
    public void renderMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi) {
        Long port = OpenFlow13Provider.getPortNoFromNodeConnector(connector);
        Flow flow = this.openFlow13Provider.createIngressClassifierAclFlow(
                nodeId, this.openFlow13Provider.getMatchBuilderFromAceMatches(matches), port, nsp, nsi);

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> this.openFlow13Provider.appendFlowForCreate(nodeId, flow, tx)), LOG, "Error rendering a match");
    }

    @Override
    public void renderEgress(InterfaceKey interfaceKey, String destinationIp) {
        // noop
    }

    @Override
    public void suppressIngress(InterfaceKey interfaceKey) {
        // noop
    }

    @Override
    // FindBugs reports "Useless object stored in variable flows" however it doesn't recognize the usage of forEach.
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public void suppressNode(NodeId nodeId) {
        List<Flow> flows = new ArrayList<>();
        flows.add(this.openFlow13Provider.createIngressClassifierFilterTunnelNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterEthNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierFilterNoNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierAclNoMatchFlow(nodeId));

        flows.add(this.openFlow13Provider.createIngressClassifierTunnelEthNshTrafficCaptureFlow(nodeId));
        flows.add(this.openFlow13Provider.createIngressClassifierTunnelNshTrafficCaptureFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierFilterNoNshFlow(nodeId));
        flows.add(this.openFlow13Provider.createEgressClassifierFilterNshFlow(nodeId));

        flows.add(this.openFlow13Provider.createEgressClassifierNextHopFlow(nodeId));

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> flows.forEach(flow -> this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx))), LOG,
            "Error deleting a node");
    }

    @Override
    // FindBugs reports "Useless object stored in variable flows" however it doesn't recognize the usage of forEach.
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public void suppressPath(NodeId nodeId, Long nsp, short nsi, short nsl, String firstHopIp) {
        List<Flow> flows = new ArrayList<>();
        if (firstHopIp != null) {
            Long port = geniusProvider.getEgressVxlanPortForNode(OpenFlow13Provider.getDpnIdFromNodeId(nodeId))
                    .orElse(null);
            if (port == null) {
                LOG.error("OpenflowRenderer: cant get egressPort for nodeId [{}]", nodeId.getValue());
                return;
            }
            Flow flow;
            flow = openFlow13Provider.createEgressClassifierTransportEgressRemoteEthNshFlow(
                    nodeId, nsp, port, firstHopIp);
            flows.add(flow);
        } else {
            Flow flow;
            flow = openFlow13Provider.createEgressClassifierTransportEgressLocalFlow(nodeId, nsp);
            flows.add(flow);
        }
        short egressNsi = (short) (nsi - nsl);
        flows.add(openFlow13Provider.createIngressClassifierFilterChainEgressFlow(nodeId, nsp, egressNsi));
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> flows.forEach(flow -> this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx))), LOG,
            "Error deleting a path");
    }

    @Override
    public void suppressMatch(NodeId nodeId, String connector, Matches matches, Long nsp, Short nsi) {
        Long port = OpenFlow13Provider.getPortNoFromNodeConnector(connector);
        Flow flow = this.openFlow13Provider.createIngressClassifierAclFlow(
                nodeId, this.openFlow13Provider.getMatchBuilderFromAceMatches(matches), port, nsp, nsi);

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> this.openFlow13Provider.appendFlowForDelete(nodeId, flow, tx)), LOG, "Error deleting a match");
    }

    @Override
    public void suppressEgress(InterfaceKey interfaceKey, String destinationIp) {
        // noop
    }
}
