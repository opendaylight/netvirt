/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Icmpv6MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class IPv6RtrFlow {

    private static final Logger LOG = LoggerFactory.getLogger(IPv6RtrFlow.class);
    private AtomicLong ipv6FlowId = new AtomicLong();
    private AtomicLong ipv6Cookie = new AtomicLong();
    private static final short TABEL_FOR_ICMPv6_FLOW = Service.ARP_RESPONDER.getTable();
    private static int FLOW_HARD_TIMEOUT = 0;
    private static int FLOW_IDLE_TIMEOUT = 0;
    private static final int ICMPv6_TO_CONTROLLER_FLOW_PRIORITY = 1024;
    private static final int ICMPv6_TYPE_RS = 133;
    private static final int ICMPv6_TYPE_NS = 135;
    private static final String ICMPv6_TO_CONTROLLER_RS_FLOW = "GatewayRouterSolicitationToController";
    private static final String ICMPv6_TO_CONTROLLER_NS_FLOW = "GatewayNeighborSolicitationToController";
    public static final String OPENFLOW_NODE_PREFIX = "openflow:";
    private ConcurrentMap<String, Flow> gatewayToIcmpv6FlowMap;
    private static DataBroker dataBroker;

    public IPv6RtrFlow() {
        gatewayToIcmpv6FlowMap = new ConcurrentHashMap<String, Flow>();
    }

    public static void setDataBroker(DataBroker dataBrokerService) {
        dataBroker = Preconditions.checkNotNull(dataBrokerService, "DataBrokerService should not be null");
    }

    private long getDataPathId(String dpId) {
        long dpid = 0L;
        if (dpId != null) {
            dpid = new BigInteger(dpId.replaceAll(":", ""), 16).longValue();
        }
        return dpid;
    }

    private void programIcmpv6Flow(String dpId, InstanceIdentifier<Node> nodeIid, String flowName, int icmpType) {
        //Build arp reply router flow
        final Flow icmpv6ToControllerFlow = createIcmpv6ToControllerFlow(nodeIid, flowName, icmpType);

        final InstanceIdentifier<Flow> flowIid = createFlowIid(icmpv6ToControllerFlow, nodeIid);
        final NodeRef nodeRef = new NodeRef(nodeIid);
        WriteTransaction write = dataBroker.newWriteOnlyTransaction();
        write.put(LogicalDatastoreType.CONFIGURATION, flowIid, icmpv6ToControllerFlow, true);
        CheckedFuture<Void, TransactionCommitFailedException> checkFuture = write.submit();

        try {
            checkFuture.checkedGet();
            LOG.debug("Transaction success for write of Flow {}", flowIid);
            gatewayToIcmpv6FlowMap.put(dpId+flowName, icmpv6ToControllerFlow);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            write.cancel();
        }
    }

    public void addIcmpv6Flow2Controller(String dpId) {
        String nodeName = OPENFLOW_NODE_PREFIX + getDataPathId(dpId);

        final InstanceIdentifier<Node> nodeIid = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeName))).build();

        /* TODO: Currently we are programming the flows to send the RS/NS packets to the
        controller without really checking if the network has an IPv6 subnet. This needs
        to be optimized in future patchsets.
         */
        programIcmpv6Flow(dpId, nodeIid, ICMPv6_TO_CONTROLLER_RS_FLOW, ICMPv6_TYPE_RS);
        programIcmpv6Flow(dpId, nodeIid, ICMPv6_TO_CONTROLLER_NS_FLOW, ICMPv6_TYPE_NS);
    }

/***
//wait for flow installation
        Futures.addCallback(JdkFutureAdapters.listenInPoolThread(addFlowResult),
                new FutureCallback<RpcResult<AddFlowOutput>>() {

                    @Override
                    public void onSuccess(RpcResult<AddFlowOutput> result) {
                        if (!result.isSuccessful()) {
                            LOG.warn("ICMPv6 Flow to Controller is not installed successfully : {} \nErrors: {}",
                                    flowIid, result.getErrors());
                            return;
                        }
                        LOG.debug("Flow to route ICMPv6 to Controller installed successfully : {}", nodeIid);

                        //cache flow info
                        gatewayToIcmpv6FlowMap.put(nodeIid, icmpv6ToControllerFlow);
                    }

                    @Override
                    public void onFailure(Throwable fail) {
                        LOG.warn("ICMPv6 to Controller flow was not created: {}", nodeIid);
                    }
                }
        );
    }
***/

    private FlowId createFlowId(final InstanceIdentifier<Node> nodeId, String flowName) {
        String flowId = flowName + "|" + nodeId;
        return new FlowId(flowId);
    }

    private static InstanceIdentifier<Flow> createFlowIid(Flow flow, InstanceIdentifier<Node> nodeIid) {
        return nodeIid.builder()
                .augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId()))
                .child(Flow.class, new FlowKey(flow.getId()))
                .build();
    }

    private Flow createIcmpv6ToControllerFlow(InstanceIdentifier<Node> nodeIid, String flowName, int icmpType) {
        Preconditions.checkNotNull(nodeIid);
        FlowBuilder icmpv6Flow = new FlowBuilder().setTableId(TABEL_FOR_ICMPv6_FLOW)
                .setFlowName(flowName)
                .setPriority(ICMPv6_TO_CONTROLLER_FLOW_PRIORITY)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setIdleTimeout(FLOW_IDLE_TIMEOUT)
                .setHardTimeout(FLOW_HARD_TIMEOUT)
                .setCookie(new FlowCookie(BigInteger.valueOf(ipv6Cookie.incrementAndGet())))
                .setFlags(new FlowModFlags(false, false, false, false, false));

        MatchBuilder matchBuilder = new MatchBuilder();
        EthernetTypeBuilder ethTypeBuilder = new EthernetTypeBuilder();
        ethTypeBuilder.setType(new EtherType(0x86DDL));
        EthernetMatchBuilder eth = new EthernetMatchBuilder();
        eth.setEthernetType(ethTypeBuilder.build());
        matchBuilder.setEthernetMatch(eth.build());

        IpMatchBuilder ipmatch = new IpMatchBuilder();
        ipmatch.setIpProtocol((short) 58);
        matchBuilder.setIpMatch(ipmatch.build());

        final Icmpv6MatchBuilder icmpv6match = new Icmpv6MatchBuilder();
        icmpv6match.setIcmpv6Type((short) icmpType);
        matchBuilder.setIcmpv6Match(icmpv6match.build());

        Action sendToControllerAction = new ActionBuilder().setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(
                        new OutputActionCaseBuilder().setOutputAction(
                                new OutputActionBuilder().setMaxLength(0xffff)
                                        .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                        .build()).build())
                .build();

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(
                ImmutableList.of(sendToControllerAction)).build();
        Instruction sendToControllerInstruction = new InstructionBuilder().setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder().setApplyActions(applyActions).build())
                .build();

        icmpv6Flow.setMatch(matchBuilder.build());
        icmpv6Flow.setInstructions(new InstructionsBuilder().setInstruction(
                ImmutableList.of(sendToControllerInstruction)).build());
        icmpv6Flow.setId(createFlowId(nodeIid, flowName));
        return icmpv6Flow.build();
    }

    private void removeIcmpv6Flow(String dpId, InstanceIdentifier<Node> nodeIid, String flowName) {
        Flow icmpv6ToControllerFlow = gatewayToIcmpv6FlowMap.get(dpId+flowName);
        final InstanceIdentifier<Flow> flowIid = createFlowIid(icmpv6ToControllerFlow, nodeIid);

        WriteTransaction write = dataBroker.newWriteOnlyTransaction();
        write.delete(LogicalDatastoreType.CONFIGURATION, flowIid);
        CheckedFuture<Void, TransactionCommitFailedException> checkFuture = write.submit();

        try {
            checkFuture.checkedGet();
            LOG.debug("Transaction success for delete of Flow {}", flowIid);
            gatewayToIcmpv6FlowMap.remove(dpId+flowName);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            write.cancel();
        }
    }

    public void removeIcmpv6Flow2Controller(String dpId) {
        String nodeName = OPENFLOW_NODE_PREFIX + getDataPathId(dpId);

        final InstanceIdentifier<Node> nodeIid = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeName))).build();

        removeIcmpv6Flow(dpId, nodeIid, ICMPv6_TO_CONTROLLER_RS_FLOW);
        removeIcmpv6Flow(dpId, nodeIid, ICMPv6_TO_CONTROLLER_NS_FLOW);
    }
}


