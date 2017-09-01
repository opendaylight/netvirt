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
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.sfc.classifier.utils.AclMatches;
import org.opendaylight.netvirt.sfc.classifier.utils.OpenFlow13Utils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
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
    public static final BigInteger INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE =
            new BigInteger("F005BA1100000006", 16);

    // Priorities for each flow
    public static final int INGRESS_CLASSIFIER_FILTER_CHAIN_EGRESS_PRIORITY = 520;
    public static final int INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 510;
    public static final int INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 500;
    public static final int INGRESS_CLASSIFIER_ACL_PRIORITY = 500;
    public static final int INGRESS_CLASSIFIER_ACL_NOMATCH_PRIORITY = 10;
    public static final int INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY = 10;
    public static final int EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY = 250;

    // Flow names for each table
    public static final String INGRESS_CLASSIFIER_FILTER_NSH_CHAIN_EGRESS_FLOW_NAME =
            "nvsfc_ingr_class_filter_chain_egress";
    public static final String INGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME = "nvsfc_ingr_class_filter_vxgpe";
    public static final String INGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME = "nvsfc_ingr_class_filter_eth";
    public static final String INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_ingr_class_filter_nonsh";
    public static final String INGRESS_CLASSIFIER_ACL_FLOW_NAME = "nvsfc_ingr_class_acl";
    public static final String INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_FLOW_NAME =
            "nvsfc_ingr_class_capture_sfc_tunnel";
    public static final String EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME = "nvsfc_egr_class_filter_nsh";
    public static final String EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_egr_class_filter_nonsh";
    public static final String EGRESS_CLASSIFIER_NEXTHOP_C1C2_FLOW_NAME = "nvsfc_egr_class_nexthop_c1c2";
    public static final String EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_FLOW_NAME = "nvsfc_egr_class_nexthop_noc1c2";
    public static final String EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME = "nvsfc_egr_class_ tport egress";

    public static final short NSH_MDTYPE_ONE = 0x01;
    public static final short NSH_NP_ETH = 0x3;
    public static final long DEFAULT_NSH_CONTEXT_VALUE = 0L;
    public static final long ACL_FLAG_CONTEXT_VALUE = 0xFFFFFFL;
    public static final long SFC_TUNNEL_ID = 0L;
    public static final String OF_URI_SEPARATOR = ":";

    public MatchBuilder getMatchBuilderFromAceMatches(Matches matches) {
        if (matches == null) {
            return null;
        }

        return new AclMatches(matches).buildMatch();
    }

    public void appendFlowForCreate(NodeId node, Flow flow, WriteTransaction tx) {
        NodeKey nodeKey = new NodeKey(node);
        InstanceIdentifier<Flow> iidFlow = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeKey)
            .augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId()))
            .child(Flow.class, flow.getKey())
            .build();

        tx.put(LogicalDatastoreType.CONFIGURATION, iidFlow, flow, WriteTransaction.CREATE_MISSING_PARENTS);
    }

    public void appendFlowForDelete(NodeId node, Flow flow, WriteTransaction tx) {
        NodeKey nodeKey = new NodeKey(node);
        InstanceIdentifier<Flow> iidFlow = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeKey)
            .augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId()))
            .child(Flow.class, flow.getKey())
            .build();

        tx.delete(LogicalDatastoreType.CONFIGURATION, iidFlow);
    }

    /*
     * Ingress Classifier SFC Tunnel Traffic Capture Flow
     *     Captures SFC traffic coming from tunnel port and redirects it
     *     to the ingress classifier pipeline. From there, if no chain
     *     egress actions apply, it will be sent back to SFC pipeline.
     *     Match on SFC VNI = 0, and resubmit to ingress classifier.
     */
    public Flow createIngressClassifierSfcTunnelTrafficCaptureFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchTunId(match, SFC_TUNNEL_ID);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_FLOW_NAME + nodeId.getValue();


        return OpenFlow13Utils.createFlowBuilder(NwConstants.INTERNAL_TUNNEL_TABLE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter Vxgpe NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match on NSP and resubmit to SFC (we don't resubmit to
     *     since it is still not used for tunnel ports)
     *
     */
    public Flow createIngressClassifierFilterVxgpeNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchVxgpeNsh(match);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.SFC_TRANSPORT_INGRESS_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
            INGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter Eth NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match on NSP and resubmit to Ingress Dispatch on match
     */
    public Flow createIngressClassifierFilterEthNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchEthNsh(match);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
            INGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Classifier chain termination flow:
     *     Handle packets at the end of the chain
     *     Match C1 on local IP, NSP and ending NSI, restore metadata and
     *     resubmit to egress dispatcher
     */
    public Flow createIngressClassifierFilterChainEgressFlow(NodeId nodeId, long nsp, short egressNsi) {

        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchNsp(match, nsp);
        OpenFlow13Utils.addMatchNsi(match, egressNsi);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxMoveNsc4ToReg6Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxPopNsh(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
                actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_NSH_CHAIN_EGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                INGRESS_CLASSIFIER_FILTER_CHAIN_EGRESS_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
                INGRESS_CLASSIFIER_FILTER_NSH_CHAIN_EGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter No NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match Any (NSH not present), Goto Classifier ACL table
     */
    public Flow createIngressClassifierFilterNoNshFlow(NodeId nodeId) {
        // MatchAny
        MatchBuilder match = new MatchBuilder();

        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
            INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier ACL flow:
     *     Performs the ACL classification, and sends packets to Ingress Dispatcher
     *     Match on inport (corresponds to Neutron NW/tenant), Push NSH, init(nsp, nsi, C1, C2),
     *     and resubmit to Ingress Dispatcher to be sent down the rest of
     *     the pipeline
     */
    public Flow createIngressClassifierAclFlow(NodeId nodeId, MatchBuilder match, Long port, long nsp, short nsi) {
        OpenFlow13Utils.addMatchInPort(match, nodeId, port);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxPushNsh(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNp(NSH_NP_ETH, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsp((int) nsp, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsi(nsi, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc1(ACL_FLAG_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc2(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        // The flowIdStr needs to be unique, so the best way to make it unique is to use the match
        String flowIdStr = INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue() + match.build().toString();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE,
            INGRESS_CLASSIFIER_ACL_PRIORITY, INGRESS_CLASSIFIER_ACL_COOKIE, INGRESS_CLASSIFIER_ACL_FLOW_NAME,
            flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier ACL NoMatch flow:
     *     If there are no ACL classification matches, then resubmit back to
     *     the Ingress Dispatcher to let other services handle the packet.
     */
    public Flow createIngressClassifierAclNoMatchFlow(NodeId nodeId) {
        // This is a MatchAny flow
        MatchBuilder match = new MatchBuilder();

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
                actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        String flowIdStr = INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE,
                INGRESS_CLASSIFIER_ACL_NOMATCH_PRIORITY, INGRESS_CLASSIFIER_ACL_COOKIE,
                INGRESS_CLASSIFIER_ACL_FLOW_NAME, flowIdStr, match, isb).build();
    }

    //
    // Internal EgressFlow util methods
    //

    /*
     * Egress Classifier Filter NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     Match on NSH MdType=1, Goto table Egress Classifier NextHop on match
     * Since we need to check if the packet has passed through the classifier and has been
     * encapsulated with NSH. We cant check for Vxgpe+NSH or Eth+NSH yet, since the outer
     * encapsulation wont be added until the packet egresses, so instead check for NSH MD-type,
     * which was set in the classification flow.
     */
    public Flow createEgressClassifierFilterNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchNshNsc1(match, ACL_FLAG_CONTEXT_VALUE);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc1(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        isb = OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier Filter No NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     MatchAny (NSH not present), Resubmit to Egress Dispatcher
     *     since the packet is not for SFC
     */
    public Flow createEgressClassifierFilterNoNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier NextHop No C1/C2 flow:
     *     Set C1/C2 accordingly
     *     Match [C1, C2] == [0, 0], Move [TunIpv4Dst, TunVnid] to [C1, C2],
     *     Move Reg0 (SFF IP) to TunIpv4Dst, and goto Egress Classifier
     *     Transport Egress table on match
     */
    public Flow createEgressClassifierNextHopNoC1C2Flow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchNshNsc1(match, DEFAULT_NSH_CONTEXT_VALUE);
        OpenFlow13Utils.addMatchNshNsc2(match, DEFAULT_NSH_CONTEXT_VALUE);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxMoveReg0ToNsc1Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIdToNsc2Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg6ToNsc4Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadTunId(SFC_TUNNEL_ID, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE,
            EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY, EGRESS_CLASSIFIER_NEXTHOP_COOKIE,
            EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier NextHop with C1/C2 flow:
     *     Set C1/C2 accordingly
     *     MatchAny (C1, C2 already set) goto Egress Classifier
     *     Transport Egress table
     */
    public Flow createEgressClassifierNextHopC1C2Flow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();

        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
                NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_NEXTHOP_C1C2_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE,
            EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY, EGRESS_CLASSIFIER_NEXTHOP_COOKIE,
            EGRESS_CLASSIFIER_NEXTHOP_C1C2_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Local Flow
     *     Final egress processing and egress packets. Resubmit to Ingress
     *     Dispatcher to be processed by SFC SFF on match
     */
    public Flow createEgressClassifierTransportEgressLocalFlow(NodeId nodeId, long nsp) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.SFC_TRANSPORT_INGRESS_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
            EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
            EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Remote Flow
     *     Final egress processing and egress packets. Determines if the
     *     packet should go to a local or remote SFF.
     *     Match on Nsp, Output to port to send to remote SFF on match.
     */
    public Flow createEgressClassifierTransportEgressRemoteFlow(NodeId nodeId, long nsp, long outport,
                                                                String firstHopIp) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        Long ipl = InetAddresses.coerceToInteger(InetAddresses.forString(firstHopIp)) & 0xffffffffL;
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxLoadTunIpv4Dst(ipl, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionOutPort("output:" + outport, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
            EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
            EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }

    public static Long getPortNoFromNodeConnector(String connector) {
        /*
         * NodeConnectorId is of the form 'openflow:dpnid:portnum'
         */
        return Long.valueOf(connector.split(OF_URI_SEPARATOR)[2]);
    }

    public static BigInteger getDpnIdFromNodeId(NodeId nodeId) {
        /*
         * NodeId is of the form 'openflow:dpnid'
         */
        return BigInteger.valueOf(Long.valueOf(nodeId.getValue().split(OF_URI_SEPARATOR)[1]));
    }

}
