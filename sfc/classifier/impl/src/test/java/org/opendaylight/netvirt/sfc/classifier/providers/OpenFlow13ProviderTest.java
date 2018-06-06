/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.net.InetAddresses;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.sfc.classifier.utils.AclMatches;
import org.opendaylight.netvirt.sfc.classifier.utils.OpenFlow13Utils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg0;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.grouping.Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.list.grouping.ExtensionList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc1Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc2Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc4Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNsiCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNspCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIdCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIpv4DstCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionDecapNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionEncapNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegMoveNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.write.actions._case.write.actions.action.action.NxActionResubmitNodesNodeTableFlowWriteActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc1Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc2Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc4Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNsiCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNspCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxRegCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxTunIdCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxAugMatchNodesNodeTableFlow;


public class OpenFlow13ProviderTest {

    private OpenFlow13Provider openflowProvider;
    private final NodeId nodeId;
    private static final String NODE_ID_STR = "openflow:1234";
    private static final String SFF_IP_STR = "192.168.0.1";
    private static final Long IN_PORT = 8L;
    private static final Long OUT_PORT = 12L;
    private static final Long NSP = 6500L;
    private static final Short NSI = (short) 255;
    private static final Short EGRESS_NSI = (short) 253;

    public OpenFlow13ProviderTest() {
        nodeId = new NodeId(NODE_ID_STR);
    }

    @Before
    public void setUp() {
        openflowProvider = new OpenFlow13Provider();
    }

    @Test
    public void createIngressClassifierFilterTunnelNshFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterTunnelNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_TUN_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_TUN_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.SFC_TRANSPORT_INGRESS_TABLE);
    }

    @Test
    public void createIngressClassifierFilterEthNshFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterEthNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_ETH_NSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchEthNsh(flow.getMatch());
        checkMatchTunDstIp(flow.getMatch(), OpenFlow13Provider.NULL_IP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createIngressClassifierFilterNshFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchTunDstIp(flow.getMatch(), OpenFlow13Provider.NULL_IP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createIngressClassifierFilterChainEgressFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterChainEgressFlow(nodeId, NSP, EGRESS_NSI);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_CHAIN_EGRESS_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_CHAIN_EGRESS_FLOW_NAME
                        + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchNsp(flow.getMatch(), NSP);
        checkMatchNsi(flow.getMatch(), EGRESS_NSI);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 3);
        checkActionMoveNsc4(actionList.get(0), true);
        checkActionMoveReg(actionList.get(0), NxmNxReg6.class, 0, 31, false);
        checkActionDecap(actionList.get(1));
        checkActionResubmit(curInstruction, NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createIngressClassifierFilterNoNshFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterNoNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchEmpty(flow.getMatch());

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionGotoTable(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE);
    }

    @Test
    public void createIngressClassifierAclFlow() {
        // Create an empty AclMatch to pass in
        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(new AceIpBuilder().build());
        AclMatches aclMatches = new AclMatches(matchesBuilder.build());
        MatchBuilder matchBuilder = aclMatches.buildMatch();

        Flow flow = openflowProvider.createIngressClassifierAclFlow(
                nodeId, matchBuilder, IN_PORT, NSP, NSI);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_MATCH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue()
                + matchBuilder.build().toString());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_COOKIE);

        // Only checking the inport match, since the rest is tested in AclMatchesTest
        checkMatchInport(flow.getMatch(), nodeId.getValue() + ":" + IN_PORT);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 3);

        checkActionLoadReg(actionList.get(0), NxmNxReg2.class, 8, 31, NSP);
        checkActionLoadReg(actionList.get(1), NxmNxReg2.class, 0, 7, NSI);
        checkActionResubmit(curInstruction, NwConstants.LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createIngressClassifierAclNoMatchFlow() {
        Flow flow = openflowProvider.createIngressClassifierAclNoMatchFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_NOMATCH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_COOKIE);

        checkMatchEmpty(flow.getMatch());

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createEgressClassifierFilterNshFlow() {
        Flow flow = openflowProvider.createEgressClassifierFilterNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_NSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchEmpty(flow.getMatch());

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionGotoTable(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
    }

    @Test
    public void createEgressClassifierFilterNoNshFlow() {
        Flow flow = openflowProvider.createEgressClassifierFilterNoNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchReg(flow.getMatch(), NxmNxReg2.class, 0);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createEgressClassifierNextHopFlow() {
        Flow flow = openflowProvider.createEgressClassifierNextHopFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_COOKIE);

        checkMatchEmpty(flow.getMatch());

        assertEquals(2, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 8);

        checkActionEncap(actionList.get(0), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkActionMoveReg(actionList.get(1), NxmNxReg2.class, 8,31, true);
        checkActionMoveNsp(actionList.get(1), false);
        checkActionMoveReg(actionList.get(2), NxmNxReg2.class, 0,7, true);
        checkActionMoveNsi(actionList.get(2), false);
        checkActionLoadReg(actionList.get(3), NxmNxReg2.class, 0 , 31, 0);
        checkActionMoveReg(actionList.get(4), NxmNxReg0.class, 0, 31, true);
        checkActionMoveNsc1(actionList.get(4), false);
        checkActionMoveTunId(actionList.get(5), true);
        checkActionMoveNsc2(actionList.get(5), false);
        checkActionMoveReg(actionList.get(6), NxmNxReg6.class, 0, 31, true);
        checkActionMoveNsc4(actionList.get(6), false);
        checkActionLoadTunId(actionList.get(7), OpenFlow13Provider.SFC_TUNNEL_ID);

        curInstruction = flow.getInstructions().getInstruction().get(1).getInstruction();
        checkActionGotoTable(curInstruction, NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
    }

    @Test
    public void createEgressClassifierTransportEgressLocalFlow() {
        Flow flow = openflowProvider.createEgressClassifierTransportEgressLocalFlow(nodeId, NSP);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_EGRESS_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchNsp(flow.getMatch(), NSP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.SFC_TRANSPORT_INGRESS_TABLE);
    }

    @Test
    public void createEgressClassifierTransportEgressRemoteNshFlow() {
        Flow flow = openflowProvider.createEgressClassifierTransportEgressRemoteNshFlow(
                nodeId, NSP, OUT_PORT, SFF_IP_STR);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_EGRESS_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchNsp(flow.getMatch(), NSP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        List<Action> actionList = checkApplyActionSize(
                flow.getInstructions().getInstruction().get(0).getInstruction(), 2);

        checkActionLoadTunIpv4(actionList.get(0), SFF_IP_STR);
        checkActionOutport(actionList.get(1), "output:" + OUT_PORT);
    }

    @Test
    public void createEgressClassifierTransportEgressRemoteEthNshFlow() {
        Flow flow = openflowProvider.createEgressClassifierTransportEgressRemoteEthNshFlow(
                nodeId, NSP, OUT_PORT, SFF_IP_STR);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_EGRESS_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchNsp(flow.getMatch(), NSP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        List<Action> actionList = checkApplyActionSize(
                flow.getInstructions().getInstruction().get(0).getInstruction(), 3);

        checkActionEncap(actionList.get(0), OpenFlow13Utils.PACKET_TYPE_ETH);
        checkActionLoadTunIpv4(actionList.get(1), SFF_IP_STR);
        checkActionOutport(actionList.get(2), "output:" + OUT_PORT);
    }

    @Test
    public void createIngressClassifierTunnelEthNshTrafficCaptureFlow() {
        Flow flow = openflowProvider.createIngressClassifierTunnelEthNshTrafficCaptureFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INTERNAL_TUNNEL_TABLE);
        assertEquals(flow.getPriority().intValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_ETH_NSH_TRAFFIC_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE);

        checkMatchEthNsh(flow.getMatch());
        checkMatchTunId(flow.getMatch(), OpenFlow13Provider.SFC_TUNNEL_ID);

        assertEquals(2, flow.getInstructions().getInstruction().size());
        List<Action> actionList = checkApplyActionSize(
                flow.getInstructions().getInstruction().get(0).getInstruction(), 1);
        checkActionDecap(actionList.get(0));
        checkActionGotoTable(flow.getInstructions().getInstruction().get(1).getInstruction(),
                NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
    }

    @Test
    public void createIngressClassifierTunnelNshTrafficCaptureFlow() {
        Flow flow = openflowProvider.createIngressClassifierTunnelNshTrafficCaptureFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INTERNAL_TUNNEL_TABLE);
        assertEquals(flow.getPriority().intValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_NSH_TRAFFIC_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE);

        checkMatchPacketType(flow.getMatch(), OpenFlow13Utils.PACKET_TYPE_NSH);
        checkMatchTunId(flow.getMatch(), OpenFlow13Provider.SFC_TUNNEL_ID);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionGotoTable(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
    }

    //
    // Internal util methods to check Flow Matches
    //

    private void checkMatchEmpty(Match match) {
        assertNull(match.getPacketTypeMatch());
        assertNull(match.getEthernetMatch());
        assertNull(match.getIpMatch());
        assertNull(match.getLayer3Match());
        assertNull(match.getLayer4Match());
        assertNull(match.augmentation(GeneralAugMatchNodesNodeTableFlow.class));
    }

    private void checkMatchPacketType(Match match, long packetType) {
        assertEquals(packetType, match.getPacketTypeMatch().getPacketType().longValue());
    }

    private void checkMatchTunId(Match match, long value) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.augmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.augmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxTunId() != null) {
                assertEquals(nxAugMatch.getNxmNxTunId().getValue().longValue(), value);
            }
        }
    }

    private void checkMatchTunDstIp(Match match, Ipv4Address value) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.augmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.augmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxTunIpv4Dst() != null) {
                assertEquals(nxAugMatch.getNxmNxTunIpv4Dst().getIpv4Address(), value);
            }
        }
    }

    private void checkMatchEthNsh(Match match) {
        assertEquals(match.getEthernetMatch().getEthernetType().getType().getValue().longValue(),
                OpenFlow13Utils.ETHERTYPE_NSH);
    }

    private void checkMatchInport(Match match, String inportStr) {
        assertEquals(inportStr, match.getInPort().getValue());
    }

    private void checkMatchNsp(Match match, long nsp) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.augmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.augmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNsp() != null) {
                assertEquals(nxAugMatch.getNxmNxNsp().getValue().longValue(), nsp);
            }
        }
    }

    private void checkMatchNsi(Match match, short nsi) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.augmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.augmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNsi() != null) {
                assertEquals(nxAugMatch.getNxmNxNsi().getNsi().shortValue(), nsi);
            }
        }
    }

    private void checkMatchReg(Match match, Class<? extends NxmNxReg> reg, long value) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.augmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.augmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxReg() != null) {
                assertEquals(nxAugMatch.getNxmNxReg().getReg(), reg);
                assertEquals(nxAugMatch.getNxmNxReg().getValue().longValue(), value);
                assertNull(nxAugMatch.getNxmNxReg().getMask());
            }
        }
    }

    //
    // Internal util methods to check Flow Actions
    //

    private void checkActionResubmit(Instruction curInstruction, short nextTableId) {
        assertTrue(curInstruction instanceof ApplyActionsCase);
        boolean resubmitActionFound = false;
        for (Action action : ((ApplyActionsCase) curInstruction).getApplyActions().getAction()) {
            if (action.getAction() instanceof NxActionResubmitNodesNodeTableFlowWriteActionsCase) {
                NxActionResubmitNodesNodeTableFlowWriteActionsCase resubmitAction =
                        (NxActionResubmitNodesNodeTableFlowWriteActionsCase) action.getAction();
                assertEquals(resubmitAction.getNxResubmit().getTable().shortValue(), nextTableId);
                resubmitActionFound = true;
            }
        }

        assertTrue(resubmitActionFound);
    }

    private void checkActionGotoTable(Instruction curInstruction, short nextTableId) {
        if (curInstruction instanceof GoToTableCase) {
            GoToTableCase goToTablecase = (GoToTableCase) curInstruction;
            assertEquals(goToTablecase.getGoToTable().getTableId().shortValue(), nextTableId);
        } else {
            fail();
        }
    }

    private List<Action> checkApplyActionSize(Instruction curInstruction, int numActions) {
        assertTrue(curInstruction instanceof ApplyActionsCase);
        ApplyActionsCase action = (ApplyActionsCase) curInstruction;
        assertEquals(numActions, action.getApplyActions().getAction().size());

        return action.getApplyActions().getAction();
    }

    private void checkActionLoadTunIpv4(Action action, String ip) {
        long ipl = InetAddresses.coerceToInteger(InetAddresses.forString(ip)) & 0xffffffffL;
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxTunIpv4DstCase tunDstTypeCase = (DstNxTunIpv4DstCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(tunDstTypeCase.isNxTunIpv4Dst());
        assertEquals(regLoad.getNxRegLoad().getValue().longValue(), ipl);
    }

    private void checkActionLoadTunId(Action action, long tunId) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxTunIdCase mdTypeCase = (DstNxTunIdCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(mdTypeCase.isNxTunId());
        assertEquals(regLoad.getNxRegLoad().getValue().longValue(), tunId);
    }

    private void checkActionOutport(Action action, String outport) {
        OutputActionCase output = (OutputActionCase) action.getAction();
        assertEquals(output.getOutputAction().getOutputNodeConnector().getValue(), outport);
    }

    private void checkActionMoveNsp(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            assertTrue(((SrcNxNspCase) regMove.getNxRegMove().getSrc().getSrcChoice()).isNxNspDst());
        } else {
            assertTrue(((DstNxNspCase) regMove.getNxRegMove().getDst().getDstChoice()).isNxNspDst());
        }
    }

    private void checkActionMoveNsi(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            assertTrue(((SrcNxNsiCase) regMove.getNxRegMove().getSrc().getSrcChoice()).isNxNsiDst());
        } else {
            assertTrue(((DstNxNsiCase) regMove.getNxRegMove().getDst().getDstChoice()).isNxNsiDst());
        }
    }

    private void checkActionMoveNsc1(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            SrcNxNshc1Case src = (SrcNxNshc1Case) regMove.getNxRegMove().getSrc().getSrcChoice();
            assertTrue(src.isNxNshc1Dst());
        } else {
            DstNxNshc1Case dst = (DstNxNshc1Case) regMove.getNxRegMove().getDst().getDstChoice();
            assertTrue(dst.isNxNshc1Dst());
        }
    }

    private void checkActionMoveNsc2(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            SrcNxNshc2Case src = (SrcNxNshc2Case) regMove.getNxRegMove().getSrc().getSrcChoice();
            assertTrue(src.isNxNshc2Dst());
        } else {
            DstNxNshc2Case dst = (DstNxNshc2Case) regMove.getNxRegMove().getDst().getDstChoice();
            assertTrue(dst.isNxNshc2Dst());
        }
    }

    private void checkActionMoveNsc4(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            SrcNxNshc4Case src = (SrcNxNshc4Case) regMove.getNxRegMove().getSrc().getSrcChoice();
            assertTrue(src.isNxNshc4Dst());
        } else {
            DstNxNshc4Case dst = (DstNxNshc4Case) regMove.getNxRegMove().getDst().getDstChoice();
            assertTrue(dst.isNxNshc4Dst());
        }
    }

    private void checkActionMoveTunId(Action action, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            SrcNxTunIdCase src = (SrcNxTunIdCase) regMove.getNxRegMove().getSrc().getSrcChoice();
            assertTrue(src.isNxTunId());
        } else {
            DstNxTunIdCase dst = (DstNxTunIdCase) regMove.getNxRegMove().getDst().getDstChoice();
            assertTrue(dst.isNxTunId());
        }
    }

    private void checkActionLoadReg(Action action, Class<? extends NxmNxReg> reg,
                                    int startOffset,
                                    int endOffset,
                                    long value) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        assertEquals(reg, ((DstNxRegCase) regLoad.getNxRegLoad().getDst().getDstChoice()).getNxReg());
        assertEquals(startOffset, regLoad.getNxRegLoad().getDst().getStart().intValue());
        assertEquals(endOffset, regLoad.getNxRegLoad().getDst().getEnd().intValue());
        assertEquals(value, regLoad.getNxRegLoad().getValue().longValue());
    }

    private void checkActionMoveReg(Action action, Class<? extends NxmNxReg> reg,
                                    int startOffset,
                                    int endOffset,
                                    boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            assertEquals(reg, ((SrcNxRegCase) regMove.getNxRegMove().getSrc().getSrcChoice()).getNxReg());
            assertEquals(startOffset, regMove.getNxRegMove().getSrc().getStart().intValue());
            assertEquals(endOffset, regMove.getNxRegMove().getSrc().getEnd().intValue());
        } else {
            assertEquals(reg, ((DstNxRegCase) regMove.getNxRegMove().getDst().getDstChoice()).getNxReg());
            assertEquals(startOffset, regMove.getNxRegMove().getDst().getStart().intValue());
            assertEquals(endOffset, regMove.getNxRegMove().getDst().getEnd().intValue());
        }
    }

    private void checkActionEncap(Action action, long packetType) {
        NxActionEncapNodesNodeTableFlowApplyActionsCase encap =
                (NxActionEncapNodesNodeTableFlowApplyActionsCase) action.getAction();
        assertEquals(packetType, encap.getNxEncap().getPacketType().longValue());
    }

    private void checkActionDecap(Action action) {
        NxActionDecapNodesNodeTableFlowApplyActionsCase decap =
                (NxActionDecapNodesNodeTableFlowApplyActionsCase) action.getAction();
        assertNotNull(decap.getNxDecap());
    }

}
