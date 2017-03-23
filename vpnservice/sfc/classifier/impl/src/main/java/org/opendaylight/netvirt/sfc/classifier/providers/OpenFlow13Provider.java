/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import com.google.common.net.InetAddresses;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.sfc.classifier.utils.OpenFlow13Utils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class OpenFlow13Provider {
    // Unique cookie values for each type of flow
    public static final BigInteger INGRESS_CLASSIFIER_FILTER_COOKIE = new BigInteger("F005BA1100000001", 16);
    public static final BigInteger INGRESS_CLASSIFIER_ACL_COOKIE = new BigInteger("F005BA1100000002", 16);
    public static final BigInteger EGRESS_CLASSIFIER_FILTER_COOKIE = new BigInteger("F005BA1100000003", 16);
    public static final BigInteger EGRESS_CLASSIFIER_NEXTHOP_COOKIE = new BigInteger("F005BA1100000004", 16);
    public static final BigInteger EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE = new BigInteger("F005BA1100000005", 16);
    public static final int SFC_SERVICE_PRIORITY = 6;

    // Priorities for each flow
    private static final int INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 510;
    private static final int INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 500;
    private static final int INGRESS_CLASSIFIER_ACL_PRIORITY = 500;
    private static final int EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 260;
    private static final int EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 250;
    private static final int EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY = 250;
    private static final int EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY = 260;
    private static final int EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY = 260;
    private static final int EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY = 250;

    // Flow names for each table
    private static final String INGRESS_CLASSIFIER_FILTER_FLOW_NAME = "netvirt ingress classifier filter";
    private static final String INGRESS_CLASSIFIER_ACL_FLOW_NAME = "netvirt ingress classifier acl";
    private static final String EGRESS_CLASSIFIER_FILTER_FLOW_NAME = "netvirt egress classifier filter";
    private static final String EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME = "netvirt egress classifier nexthop";
    private static final String EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME = "netvirt egress classifier tport egress";

    private static final long DEFAULT_NSH_CONTEXT_VALUE = 0L;
    private static final short NSH_MDTYPE_ONE = 0x01;
    private static AtomicLong flowIdInc = new AtomicLong();

    private final DataBroker dataBroker;

    @Inject
    public OpenFlow13Provider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public void writeClassifierFlows(NodeId node, String logicalIf, MatchBuilder match, RenderedServicePath rsp) {
        List<Flow> flows = createClassifierFlows(node, logicalIf, match, rsp);
        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> appendFlowForCreate(node, flow, tx));
        tx.submit();
    }

    public void removeClassifierFlows(NodeId node, String logicalIf, MatchBuilder match, RenderedServicePath rsp) {
        List<Flow> flows = createClassifierFlows(node, logicalIf, match, rsp);
        WriteTransaction tx = this.dataBroker.newWriteOnlyTransaction();
        flows.forEach((flow) -> appendFlowForDelete(node, flow, tx));
        tx.submit();
    }

    private List<Flow> createClassifierFlows(NodeId node, String logicalIf, MatchBuilder match,
                                             RenderedServicePath rsp) {
        String sffIpStr = ""; //TODO get the IP from the node

        List<Flow> flows = new ArrayList<>();

        // Ingress Classifier flows
        flows.add(createIngressClassifierFilterNshFlow(rsp.getPathId()));
        flows.add(createIngressClassifierFilterNoNshFlow());
        flows.add(createIngressClassifierAclFlow(match, node.getValue(), sffIpStr, logicalIf, rsp.getPathId(),
            rsp.getStartingIndex()));

        // Egress Classifier flows
        flows.add(createEgressClassifierFilterNshFlow(rsp.getPathId()));
        flows.add(createEgressClassifierFilterNoNshFlow());
        flows.add(createEgressClassifierNextHopC1C2Flow());
        flows.add(createEgressClassifierNextHopNoC1C2Flow());
        flows.add(createEgressClassifierTransportEgressLocalFlow(rsp.getPathId(), sffIpStr));
        flows.add(createEgressClassifierTransportEgressRemoteFlow(rsp.getPathId(), logicalIf));

        return flows;
    }

    private void appendFlowForCreate(NodeId node, Flow flow, WriteTransaction tx) {
        NodeKey nodeKey = new NodeKey(node);
        InstanceIdentifier<Flow> iidFlow = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeKey)
            .augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId()))
            .child(Flow.class, flow.getKey())
            .build();

        tx.put(LogicalDatastoreType.CONFIGURATION, iidFlow, flow, true);
    }

    private void appendFlowForDelete(NodeId node, Flow flow, WriteTransaction tx) {
        NodeKey nodeKey = new NodeKey(node);
        InstanceIdentifier<Flow> iidFlow = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeKey)
            .augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId()))
            .child(Flow.class, flow.getKey())
            .build();

        tx.delete(LogicalDatastoreType.CONFIGURATION, iidFlow);
    }

    //
    // Internal IngressFlow util methods
    //

    /*
     * Ingress Classifier Filter NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match on NSP and resubmit to Ingress Dispatch on match
     */
    private Flow createIngressClassifierFilterNshFlow(long nsp) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
            INGRESS_CLASSIFIER_FILTER_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Ingress Classifier Filter No NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match Any (NSH not present), Goto Classifier ACL table
     */
    private Flow createIngressClassifierFilterNoNshFlow() {
        // MatchAny
        MatchBuilder match = new MatchBuilder();

        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE);

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
            INGRESS_CLASSIFIER_FILTER_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Ingress Classifier ACL flow:
     *     Performs the ACL classification, and sends packets to Ingress Dispatcher
     *     Match on inport (corresponds to Neutron NW/tenant), Push NSH, init(nsp, nsi, C1, C2),
     *     set SFFIp in Reg0, and resubmit to Ingress Dispatcher to be sent down the rest of
     *     the pipeline
     */
    private Flow createIngressClassifierAclFlow(MatchBuilder match, String nodeName, String sffIpStr, String logicalIf,
                                                long nsp, short nsi) {
        OpenFlow13Utils.addMatchInPort(match, nodeName, logicalIf);

        int sffIp = InetAddresses.coerceToInteger(InetAddresses.forString(sffIpStr));
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxPushNsh(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsp((int) nsp, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsi(nsi, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc1(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc2(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadReg0(sffIp, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE,
            INGRESS_CLASSIFIER_ACL_PRIORITY, INGRESS_CLASSIFIER_ACL_COOKIE, INGRESS_CLASSIFIER_ACL_FLOW_NAME,
            String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    //
    // Internal EgressFlow util methods
    //

    /*
     * Egress Classifier Filter NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     Match on NSP, Goto table Egress Classifier NextHop on match
     */
    private Flow createEgressClassifierFilterNshFlow(long nsp) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        // TODO change this once 53710 is merged
        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            //NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
            NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE);

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Egress Classifier Filter No NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     MatchAny (NSH not present), Resubmit to Egress Dispatcher
     *     since the packet is not for SFC
     */
    private Flow createEgressClassifierFilterNoNshFlow() {
        MatchBuilder match = new MatchBuilder();

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Egress Classifier NextHop No C1/C2 flow:
     *     Set C1/C2 accordingly
     *     Match [C1, C2] == [0, 0], Move [TunIpv4Dst, TunVnid] to [C1, C2],
     *     Move Reg0 (SFF IP) to TunIpv4Dst, and goto Egress Classifier
     *     Transport Egress table on match
     */
    private Flow createEgressClassifierNextHopNoC1C2Flow() {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchNshNsc1(match, DEFAULT_NSH_CONTEXT_VALUE);
        OpenFlow13Utils.addMatchNshNsc2(match, DEFAULT_NSH_CONTEXT_VALUE);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIpv4DstToNsc1Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIdToNsc2Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg0ToTunIpv4Dst(actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        // TODO change this once 53710 is merged
        //OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE);

        // TODO change this once 53710 is merged
        //return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE,
        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE,
            EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY, EGRESS_CLASSIFIER_NEXTHOP_COOKIE,
            EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Egress Classifier NextHop with C1/C2 flow:
     *     Set C1/C2 accordingly
     *     MatchAny (C1, C2 already set) goto Egress Classifier
     *     Transport Egress table
     */
    private Flow createEgressClassifierNextHopC1C2Flow() {
        MatchBuilder match = new MatchBuilder();

        // TODO change this once 53710 is merged
        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            //NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
            NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE);

        // TODO change this once 53710 is merged
        //return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE,
        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE,
            EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY, EGRESS_CLASSIFIER_NEXTHOP_COOKIE,
            EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Local Flow
     *     Final egress processing and egress packets. Determines if the
     *     packet should go to a local or remote SFF.
     *     Match on [Nsp, TunIpv4Dst==thisNodeIp], Resubmit to Ingress
     *     Dispatcher to be processed by SFC SFF on match
     */
    private Flow createEgressClassifierTransportEgressLocalFlow(long nsp, String localIpStr) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);
        OpenFlow13Utils.addMatchTunIpv4Dst(match, localIpStr);

        // TODO what else do we need to Set on the packet here??
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        // TODO change this once 53710 is merged
        //return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE,
            EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
            EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Remote Flow
     *     Final egress processing and egress packets. Determines if the
     *     packet should go to a local or remote SFF.
     *     Match on Nsp, Output to port to send to remote SFF on match.
     */
    private Flow createEgressClassifierTransportEgressRemoteFlow(long nsp, String logicalIf) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        // TODO what else do we need to Set on the packet here??
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionOutPort("output:" + logicalIf, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        // TODO change this once 53710 is merged
        //return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_ACL_TABLE,
            EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
            EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, String.valueOf(flowIdInc.getAndIncrement()), match, isb).build();
    }
}
