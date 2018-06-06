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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
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
    public static final int INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY = 10;
    public static final int INGRESS_CLASSIFIER_FILTER_CHAIN_EGRESS_PRIORITY = 520;
    public static final int INGRESS_CLASSIFIER_FILTER_NSH_TUN_PRIORITY = 510;
    public static final int INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 511;
    public static final int INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 500;
    public static final int INGRESS_CLASSIFIER_ACL_MATCH_PRIORITY = 500;
    public static final int INGRESS_CLASSIFIER_ACL_NOMATCH_PRIORITY = 10;
    public static final int EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_NEXTHOP_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_EGRESS_PRIORITY = 250;

    // Flow names for each table
    public static final String INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_ETH_NSH_TRAFFIC_FLOW_NAME =
            "nvsfc_ingr_class_capture_sfc_tunnel_eth_nsh";
    public static final String INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_NSH_TRAFFIC_FLOW_NAME =
            "nvsfc_ingr_class_capture_sfc_tunnel_nsh";
    public static final String INGRESS_CLASSIFIER_FILTER_NSH_CHAIN_EGRESS_FLOW_NAME =
            "nvsfc_ingr_class_filter_chain_egress";
    public static final String INGRESS_CLASSIFIER_FILTER_NSH_TUN_FLOW_NAME = "nvsfc_ingr_class_filter_nsh_tun";
    public static final String INGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME = "nvsfc_ingr_class_filter_nsh";
    public static final String INGRESS_CLASSIFIER_FILTER_ETH_NSH_FLOW_NAME = "nvsfc_ingr_class_filter_eth_nsh";
    public static final String INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_ingr_class_filter_nonsh";
    public static final String INGRESS_CLASSIFIER_ACL_FLOW_NAME = "nvsfc_ingr_class_acl";
    public static final String EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME = "nvsfc_egr_class_filter_nsh";
    public static final String EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_egr_class_filter_nonsh";
    public static final String EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME = "nvsfc_egr_class_nexthop_noc1c2";
    public static final String EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME = "nvsfc_egr_class_ tport egress";

    public static final long SFC_TUNNEL_ID = 0L;
    public static final String OF_URI_SEPARATOR = ":";
    public static final Ipv4Address NULL_IP = new Ipv4Address("0.0.0.0");

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
            .child(Flow.class, flow.key())
            .build();

        tx.put(LogicalDatastoreType.CONFIGURATION, iidFlow, flow, WriteTransaction.CREATE_MISSING_PARENTS);
    }

    public void appendFlowForDelete(NodeId node, Flow flow, WriteTransaction tx) {
        NodeKey nodeKey = new NodeKey(node);
        InstanceIdentifier<Flow> iidFlow = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeKey)
            .augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId()))
            .child(Flow.class, flow.key())
            .build();

        tx.delete(LogicalDatastoreType.CONFIGURATION, iidFlow);
    }

    /*
     * Ingress Classifier SFC Tunnel Traffic Capture Flow
     *     Captures eth+nsh traffic coming from tunnel port, normalizes
     *     the packet type to nsh by removing the outer ethernet header
     *     and redirects it to the ingress classifier pipeline.
     */
    public Flow createIngressClassifierTunnelEthNshTrafficCaptureFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchTunId(match, SFC_TUNNEL_ID);
        OpenFlow13Utils.addMatchEthNsh(match);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxDecap(actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        String flowIdStr = INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_ETH_NSH_TRAFFIC_FLOW_NAME + nodeId.getValue();


        return OpenFlow13Utils.createFlowBuilder(NwConstants.INTERNAL_TUNNEL_TABLE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_ETH_NSH_TRAFFIC_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier SFC Tunnel Traffic Capture Flow
     *     Captures nsh traffic coming from tunnel port,
     *     and redirects it to the ingress classifier pipeline.
     */
    public Flow createIngressClassifierTunnelNshTrafficCaptureFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchTunId(match, SFC_TUNNEL_ID);
        OpenFlow13Utils.addMatchPacketTypeNsh(match);

        InstructionsBuilder isb = new InstructionsBuilder();
        OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        String flowIdStr = INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_NSH_TRAFFIC_FLOW_NAME + nodeId.getValue();


        return OpenFlow13Utils.createFlowBuilder(NwConstants.INTERNAL_TUNNEL_TABLE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE,
                INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_NSH_TRAFFIC_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter tunnel packet type NSH flow:
     *     Prevents nsh packets coming from a tunnel port to proceed.
     *     This is the least priority filter flow so it wont match
     *     packets coming from other than tunnel ports.
     *     Since no service port binding and thus no dispatching is
     *     available on tunnel port, resubmit direct to SFC pipeline.
     */
    public Flow createIngressClassifierFilterTunnelNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchPacketTypeNsh(match);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.SFC_TRANSPORT_INGRESS_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_NSH_TUN_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                INGRESS_CLASSIFIER_FILTER_NSH_TUN_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
                INGRESS_CLASSIFIER_FILTER_NSH_TUN_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter Eth NSH flow:
     *     Prevents eth+nsh packets coming from other than a tunnel port
     *     to proceed. Verify that packet are not coming from tunnel
     *     by verifying there is no tunnel ip set with high priority
     *     flow. Resubmit to ingress dispatcher so that other nsh
     *     service handles the packet.
     */
    public Flow createIngressClassifierFilterEthNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchEthNsh(match);
        OpenFlow13Utils.addMatchTunDstIp(match, NULL_IP);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_ETH_NSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
                INGRESS_CLASSIFIER_FILTER_ETH_NSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Ingress Classifier Filter packet type NSH floww:
     *     Prevents nsh packets coming from other than a tunnel port
     *     to proceed. Verify that packet are not coming from tunnel
     *     by checking there is no tunnel ip set with high priority
     *     flow. Resubmit to ingress dispatcher so that other nsh
     *     service handles the packet.
     */
    public Flow createIngressClassifierFilterNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchPacketTypeNsh(match);
        OpenFlow13Utils.addMatchTunDstIp(match, NULL_IP);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
                actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = INGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, INGRESS_CLASSIFIER_FILTER_COOKIE,
                INGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Classifier chain termination flow:
     *     Handle packets at the end of the chain for which the final
     *     destination might be the classifier node.
     *     Match nsh packets on classified paths at their final index.
     *     Restores the lport tag on reg6, removes the nsh header and
     *     resubmits to egress dispatcher.
     */
    public Flow createIngressClassifierFilterChainEgressFlow(NodeId nodeId, long nsp, short egressNsi) {

        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchPacketTypeNsh(match);
        OpenFlow13Utils.addMatchNsp(match, nsp);
        OpenFlow13Utils.addMatchNsi(match, egressNsi);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxMoveNsc4ToReg6Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxDecap(actionList.size()));
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
     *     Non nsh packets, those that have not been matched by other
     *     higher priority nsh matching flows, proceed to the ACL
     *     table.
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
     *     Performs the ACL classification, and sends packets to back
     *     to ingress dispatcher.
     *     Add the in_port (corresponds to Neutron NW/tenant) to the ACL match,
     *     and sets the nsp and nsi on the registry for later usage.
     */
    public Flow createIngressClassifierAclFlow(NodeId nodeId, MatchBuilder match, Long port, long nsp, short nsi) {
        OpenFlow13Utils.addMatchInPort(match, nodeId, port);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxLoadNspToReg2High(nsp, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsiToReg2Low(nsi, actionList.size()));

        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);

        // The flowIdStr needs to be unique, so the best way to make it unique is to use the match
        String flowIdStr = INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue() + match.build().toString();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE,
                INGRESS_CLASSIFIER_ACL_MATCH_PRIORITY, INGRESS_CLASSIFIER_ACL_COOKIE, INGRESS_CLASSIFIER_ACL_FLOW_NAME,
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
     * Egress Classifier Filter NoNsh flow:
     *     Filters out packets that have not been classified in the
      *    ingress classifier, those for which registry has not
     *     been set with nsp and nsi. Resubmit to egress dispatcher.
     */
    public Flow createEgressClassifierFilterNoNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();

        OpenFlow13Utils.addMatchReg2(match, 0);

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
     * Egress Classifier Filter Nsh flow.
     *     Packets proceed to NextHop table.
     */
    public Flow createEgressClassifierFilterNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();

        InstructionsBuilder isb = new InstructionsBuilder();
        isb = OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
                EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier NextHop flow:
     *     Encapsulates the packet and sets the NSH header values.
     *     Packets proceed to TransportEgress table.
     */
    public Flow createEgressClassifierNextHopFlow(NodeId nodeId) {
        final MatchBuilder match = new MatchBuilder();

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxEncapNsh(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg2HighToNsp(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg2LowToNsi(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadReg2(0, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg0ToNsc1Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIdToNsc2Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg6ToNsc4Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadTunId(SFC_TUNNEL_ID, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        OpenFlow13Utils.appendGotoTableInstruction(isb, NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE,
                EGRESS_CLASSIFIER_NEXTHOP_PRIORITY, EGRESS_CLASSIFIER_NEXTHOP_COOKIE,
                EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Local Flow
     *     First SFF is located on same node. NSH packets are
     *     sent to the SFC pipeline.
     */
    public Flow createEgressClassifierTransportEgressLocalFlow(NodeId nodeId, long nsp) {
        MatchBuilder match = new MatchBuilder();

        OpenFlow13Utils.addMatchPacketTypeNsh(match);
        OpenFlow13Utils.addMatchNsp(match, nsp);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.SFC_TRANSPORT_INGRESS_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
                EGRESS_CLASSIFIER_EGRESS_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
                EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Remote Flow
     *     Sends packet to a remote SFF ip though a tunnel port.
     *     Packets are not encapsulated with an extra ethernet header.
     */
    public Flow createEgressClassifierTransportEgressRemoteNshFlow(NodeId nodeId, long nsp, long outport,
                                                                   String firstHopIp) {
        MatchBuilder match = new MatchBuilder();

        OpenFlow13Utils.addMatchPacketTypeNsh(match);
        OpenFlow13Utils.addMatchNsp(match, nsp);

        Long ipl = InetAddresses.coerceToInteger(InetAddresses.forString(firstHopIp)) & 0xffffffffL;
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxLoadTunIpv4Dst(ipl, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionOutPort("output:" + outport, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
                EGRESS_CLASSIFIER_EGRESS_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
                EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier TransportEgress Remote Flow
     *     Sends packet to a remote SFF ip though a tunnel port.
     *     Packets are encapsulated with an extra ethernet header.
     */
    public Flow createEgressClassifierTransportEgressRemoteEthNshFlow(NodeId nodeId, long nsp, long outport,
                                                                      String firstHopIp) {
        MatchBuilder match = new MatchBuilder();

        OpenFlow13Utils.addMatchPacketTypeNsh(match);
        OpenFlow13Utils.addMatchNsp(match, nsp);

        Long ipl = InetAddresses.coerceToInteger(InetAddresses.forString(firstHopIp)) & 0xffffffffL;
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxEncapEthernet(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadTunIpv4Dst(ipl, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionOutPort("output:" + outport, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
                EGRESS_CLASSIFIER_EGRESS_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
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
        return BigInteger.valueOf(Long.parseLong(nodeId.getValue().split(OF_URI_SEPARATOR)[1]));
    }

}
