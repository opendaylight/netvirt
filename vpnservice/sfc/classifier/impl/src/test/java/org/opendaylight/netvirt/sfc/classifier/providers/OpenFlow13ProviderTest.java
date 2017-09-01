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
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.grouping.Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.list.grouping.ExtensionList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshMdtypeCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshNpCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc1Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc2Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc4Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNsiCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNspCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIdCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIpv4DstCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionPopNshNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionPushNshNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegMoveNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.write.actions._case.write.actions.action.action.NxActionResubmitNodesNodeTableFlowWriteActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc1Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc2Case;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc4Case;
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
    public void createIngressClassifierFilterVxgpeNshFlow() {
        Flow flow = openflowProvider.createIngressClassifierFilterVxgpeNshFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchVxgpeNsh(flow.getMatch());

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
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);

        checkMatchEthNsh(flow.getMatch());

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

        checkMatchNsp(flow.getMatch(), NSP);
        checkMatchNsi(flow.getMatch(), EGRESS_NSI);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 3);
        checkActionMoveNsc4(actionList.get(0), true);
        checkActionMoveTunReg(actionList.get(0), NxmNxReg6.class, false);
        checkActionPopNsh(actionList.get(1));
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
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue()
                + matchBuilder.build().toString());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.INGRESS_CLASSIFIER_ACL_COOKIE);

        // Only checking the inport match, since the rest is tested in AclMatchesTest
        checkMatchInport(flow.getMatch(), nodeId.getValue() + ":" + IN_PORT);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 8);

        checkActionPushNsh(actionList.get(0));
        checkActionLoadNshMdtype(actionList.get(1));
        checkActionLoadNshNp(actionList.get(2));
        checkActionLoadNsp(actionList.get(3));
        checkActionLoadNsi(actionList.get(4));
        checkActionLoadNshc1(actionList.get(5), OpenFlow13Provider.ACL_FLAG_CONTEXT_VALUE);
        checkActionLoadNshc2(actionList.get(6), OpenFlow13Provider.DEFAULT_NSH_CONTEXT_VALUE);
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

        checkMatchNshMdType1(flow.getMatch());

        assertEquals(2, flow.getInstructions().getInstruction().size());
        List<Action> actionList;
        actionList = checkApplyActionSize(flow.getInstructions().getInstruction().get(0).getInstruction(), 1);
        checkActionLoadNshc1(actionList.get(0), OpenFlow13Provider.DEFAULT_NSH_CONTEXT_VALUE);
        checkActionGotoTable(flow.getInstructions().getInstruction().get(1).getInstruction(),
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

        checkMatchEmpty(flow.getMatch());

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
    }

    @Test
    public void createEgressClassifierNextHopNoC1C2Flow() {
        Flow flow = openflowProvider.createEgressClassifierNextHopNoC1C2Flow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_COOKIE);

        checkMatchC1(flow.getMatch(), OpenFlow13Provider.DEFAULT_NSH_CONTEXT_VALUE);
        checkMatchC2(flow.getMatch(), OpenFlow13Provider.DEFAULT_NSH_CONTEXT_VALUE);

        assertEquals(2, flow.getInstructions().getInstruction().size());
        Instruction curInstruction = flow.getInstructions().getInstruction().get(0).getInstruction();
        List<Action> actionList = checkApplyActionSize(curInstruction, 4);

        checkActionMoveTunReg(actionList.get(0), NxmNxReg0.class, true);
        checkActionMoveNsc1(actionList.get(0), false);
        checkActionMoveTunId(actionList.get(1), true);
        checkActionMoveNsc2(actionList.get(1), false);
        checkActionMoveTunReg(actionList.get(2), NxmNxReg6.class, true);
        checkActionMoveNsc4(actionList.get(2), false);
        checkActionLoadTunId(actionList.get(3), OpenFlow13Provider.SFC_TUNNEL_ID);

        curInstruction = flow.getInstructions().getInstruction().get(1).getInstruction();
        checkActionGotoTable(curInstruction, NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
    }

    @Test
    public void createEgressClassifierNextHopC1C2Flow() {
        Flow flow = openflowProvider.createEgressClassifierNextHopC1C2Flow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_C1C2_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_NEXTHOP_COOKIE);

        checkMatchEmpty(flow.getMatch());

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionGotoTable(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
    }

    @Test
    public void createEgressClassifierTransportEgressLocalFlow() {
        Flow flow = openflowProvider.createEgressClassifierTransportEgressLocalFlow(nodeId, NSP);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE);

        checkMatchNsp(flow.getMatch(), NSP);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.SFC_TRANSPORT_INGRESS_TABLE);
    }

    @Test
    public void createEgressClassifierTransportEgressRemoteFlow() {
        Flow flow = openflowProvider.createEgressClassifierTransportEgressRemoteFlow(nodeId, NSP, OUT_PORT, SFF_IP_STR);

        assertEquals(flow.getTableId().shortValue(), NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE);
        assertEquals(flow.getPriority().intValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + NSP);
        assertEquals(flow.getCookie().getValue(), OpenFlow13Provider.EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE);

        checkMatchNsp(flow.getMatch(), NSP);
        assertEquals(1, flow.getInstructions().getInstruction().size());
        List<Action> actionList = checkApplyActionSize(
                flow.getInstructions().getInstruction().get(0).getInstruction(), 2);

        checkActionLoadTunIpv4(actionList.get(0), SFF_IP_STR);
        checkActionOutport(actionList.get(1), "output:" + OUT_PORT);
    }

    @Test
    public void createIngressClassifierSfcTunnelTrafficCaptureFlow() {
        Flow flow = openflowProvider.createIngressClassifierSfcTunnelTrafficCaptureFlow(nodeId);

        assertEquals(flow.getTableId().shortValue(), NwConstants.INTERNAL_TUNNEL_TABLE);
        assertEquals(flow.getPriority().intValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_PRIORITY);
        assertEquals(flow.getId().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_FLOW_NAME + nodeId.getValue());
        assertEquals(flow.getCookie().getValue(),
                OpenFlow13Provider.INGRESS_CLASSIFIER_CAPTURE_SFC_TUNNEL_TRAFFIC_COOKIE);

        checkMatchTunId(flow.getMatch(), OpenFlow13Provider.SFC_TUNNEL_ID);

        assertEquals(1, flow.getInstructions().getInstruction().size());
        checkActionResubmit(flow.getInstructions().getInstruction().get(0).getInstruction(),
                NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE);
    }

    //
    // Internal util methods to check Flow Matches
    //

    private void checkMatchEmpty(Match match) {
        assertNull(match.getEthernetMatch());
        assertNull(match.getIpMatch());
        assertNull(match.getLayer3Match());
        assertNull(match.getLayer4Match());
        assertNull(match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class));
    }

    private void checkMatchVxgpeNsh(Match match) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxTunGpeNp() != null) {
                assertEquals(nxAugMatch.getNxmNxTunGpeNp().getValue().shortValue(), OpenFlow13Utils.TUN_GPE_NP_NSH);
            }
        }
    }

    private void checkMatchTunId(Match match, long value) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxTunId() != null) {
                assertEquals(nxAugMatch.getNxmNxTunId().getValue().longValue(), value);
            }
        }
    }

    private void checkMatchEthNsh(Match match) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxEncapEthType() != null) {
                assertEquals(nxAugMatch.getNxmNxEncapEthType().getValue().intValue(), OpenFlow13Utils.ETHERTYPE_NSH);
            }
        }
    }

    private void checkMatchNshMdType1(Match match) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNshMdtype() != null) {
                assertEquals(nxAugMatch.getNxmNxNshMdtype().getValue().shortValue(),
                        OpenFlow13Provider.NSH_MDTYPE_ONE);
            }
        }
    }

    private void checkMatchInport(Match match, String inportStr) {
        assertEquals(inportStr, match.getInPort().getValue());
    }

    private void checkMatchC1(Match match, long c1) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNshc1() != null) {
                assertEquals(nxAugMatch.getNxmNxNshc1().getValue().longValue(), c1);
            }
        }
    }

    private void checkMatchC2(Match match, long c2) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNshc2() != null) {
                assertEquals(nxAugMatch.getNxmNxNshc2().getValue().longValue(), c2);
            }
        }
    }

    private void checkMatchNsp(Match match, long nsp) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNsp() != null) {
                assertEquals(nxAugMatch.getNxmNxNsp().getValue().longValue(), nsp);
            }
        }
    }

    private void checkMatchNsi(Match match, short nsi) {
        GeneralAugMatchNodesNodeTableFlow genAug =
                match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);

        assertNotNull(genAug);

        List<ExtensionList> extensions = genAug.getExtensionList();
        for (ExtensionList extensionList : extensions) {
            Extension extension = extensionList.getExtension();
            NxAugMatchNodesNodeTableFlow nxAugMatch = extension.getAugmentation(NxAugMatchNodesNodeTableFlow.class);

            if (nxAugMatch.getNxmNxNsi() != null) {
                assertEquals(nxAugMatch.getNxmNxNsi().getNsi().shortValue(), nsi);
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

    private void checkActionPushNsh(Action action) {
        NxActionPushNshNodesNodeTableFlowApplyActionsCase pushNshCase =
                (NxActionPushNshNodesNodeTableFlowApplyActionsCase) action.getAction();
        assertNotNull(pushNshCase.getNxPushNsh());
    }

    private void checkActionPopNsh(Action action) {
        NxActionPopNshNodesNodeTableFlowApplyActionsCase popNshCase =
                (NxActionPopNshNodesNodeTableFlowApplyActionsCase) action.getAction();
        assertNotNull(popNshCase.getNxPopNsh());
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

    private void checkActionLoadNshMdtype(Action action) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNshMdtypeCase mdTypeCase = (DstNxNshMdtypeCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(mdTypeCase.isNxNshMdtype());
    }

    private void checkActionLoadNshNp(Action action) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNshNpCase npCase = (DstNxNshNpCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(npCase.isNxNshNp());
    }

    private void checkActionLoadNsp(Action action) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNspCase nspCase = (DstNxNspCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(nspCase.isNxNspDst());
    }

    private void checkActionLoadNsi(Action action) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNsiCase nsiCase = (DstNxNsiCase) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(nsiCase.isNxNsiDst());
    }

    private void checkActionLoadNshc1(Action action, long c1) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNshc1Case c1Case = (DstNxNshc1Case) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(c1Case.isNxNshc1Dst());
        assertEquals(regLoad.getNxRegLoad().getValue().longValue(), c1);
    }

    private void checkActionLoadNshc2(Action action, long c2) {
        NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoad =
                (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) action.getAction();
        DstNxNshc2Case c2Case = (DstNxNshc2Case) regLoad.getNxRegLoad().getDst().getDstChoice();
        assertTrue(c2Case.isNxNshc2Dst());
        assertEquals(regLoad.getNxRegLoad().getValue().longValue(), c2);
    }

    private void checkActionOutport(Action action, String outport) {
        OutputActionCase output = (OutputActionCase) action.getAction();
        assertEquals(output.getOutputAction().getOutputNodeConnector().getValue(), outport);
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

    private void checkActionMoveTunReg(Action action, Class<? extends NxmNxReg> reg, boolean checkSrc) {
        NxActionRegMoveNodesNodeTableFlowApplyActionsCase regMove =
                (NxActionRegMoveNodesNodeTableFlowApplyActionsCase) action.getAction();
        if (checkSrc) {
            SrcNxRegCase src = (SrcNxRegCase) regMove.getNxRegMove().getSrc().getSrcChoice();
            assertTrue(src.getNxReg() == reg);
        } else {
            DstNxRegCase dst = (DstNxRegCase) regMove.getNxRegMove().getDst().getDstChoice();
            assertTrue(dst.getNxReg() == reg);
        }
    }

}
