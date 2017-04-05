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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final int INGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 510;
    public static final int INGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 500;
    public static final int INGRESS_CLASSIFIER_ACL_PRIORITY = 500;
    public static final int EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_FILTER_NONSH_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_NEXTHOP_C1C2_PRIORITY = 250;
    public static final int EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_EGRESS_LOCAL_PRIORITY = 260;
    public static final int EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY = 250;

    // Flow names for each table
    public static final String INGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME = "nvsfc_ingr_class_filter_vxgpe";
    public static final String INGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME = "nvsfc_ingr_class_filter_eth";
    public static final String INGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_ingr_class_filter_nonsh";
    public static final String INGRESS_CLASSIFIER_ACL_FLOW_NAME = "nvsfc_ingr_class_acl";
    public static final String EGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME = "nvsfc_egr_class_filter_vxgpe";
    public static final String EGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME = "nvsfc_egr_class_filter_eth";
    public static final String EGRESS_CLASSIFIER_FILTER_NONSH_FLOW_NAME = "nvsfc_egr_class_filter_nonsh";
    public static final String EGRESS_CLASSIFIER_NEXTHOP_C1C2_FLOW_NAME = "nvsfc_egr_class_nexthop_c1c2";
    public static final String EGRESS_CLASSIFIER_NEXTHOP_NOC1C2_FLOW_NAME = "nvsfc_egr_class_nexthop_noc1c2";
    public static final String EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME = "nvsfc_egr_class_ tport egress";

    public static final short NSH_MDTYPE_ONE = 0x01;
    public static final long DEFAULT_NSH_CONTEXT_VALUE = 0L;
    private static final int DEFAULT_NETMASK = 32;
    private static final Logger LOG = LoggerFactory.getLogger(OpenFlow13Provider.class);
    private final DataBroker dataBroker;

    @Inject
    public OpenFlow13Provider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

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

        tx.put(LogicalDatastoreType.CONFIGURATION, iidFlow, flow, true);
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

    public WriteTransaction newWriteOnlyTransaction() {
        return this.dataBroker.newWriteOnlyTransaction();
    }

    /*
     * Ingress Classifier Filter Vxgpe NSH flow:
     *     Only allows Non-NSH packets to proceed in the classifier
     *     Match on NSP and resubmit to Ingress Dispatch on match
     */
    public Flow createIngressClassifierFilterVxgpeNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchVxgpeNsh(match);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
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
     *     set SFFIp in Reg0, and resubmit to Ingress Dispatcher to be sent down the rest of
     *     the pipeline
     */
    public Flow createIngressClassifierAclFlow(NodeId nodeId, MatchBuilder match, Long port, String sffIpStr,
            long nsp, short nsi) {
        OpenFlow13Utils.addMatchInPort(match, nodeId, port);

        Long ipl = InetAddresses.coerceToInteger(InetAddresses.forString(sffIpStr)) & 0xffffffffL;
        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionNxPushNsh(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsp((int) nsp, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNsi(nsi, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc1(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadNshc2(DEFAULT_NSH_CONTEXT_VALUE, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxLoadReg0(ipl, actionList.size()));
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        LOG.info("createIngressClassifierAclFlow match.toString() [{}]", match.build().toString());

        // The flowIdStr needs to be unique, so the best way to make it unique is to use the match
        String flowIdStr = INGRESS_CLASSIFIER_ACL_FLOW_NAME + "_" + nodeId.getValue() + match.build().toString();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.INGRESS_SFC_CLASSIFIER_ACL_TABLE,
            INGRESS_CLASSIFIER_ACL_PRIORITY, INGRESS_CLASSIFIER_ACL_COOKIE, INGRESS_CLASSIFIER_ACL_FLOW_NAME,
            flowIdStr, match, isb).build();

    }

    //
    // Internal EgressFlow util methods
    //

    /*
     * Egress Classifier Filter Vxgpe NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     Match on NSP, Goto table Egress Classifier NextHop on match
     */
    public Flow createEgressClassifierFilterVxgpeNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchVxgpeNsh(match);

        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_VXGPENSH_FLOW_NAME, flowIdStr, match, isb).build();
    }

    /*
     * Egress Classifier Filter Eth NSH flow:
     *     Only allows NSH packets to proceed in the egress classifier
     *     Match on NSP, Goto table Egress Classifier NextHop on match
     */
    public Flow createEgressClassifierFilterEthNshFlow(NodeId nodeId) {
        MatchBuilder match = new MatchBuilder();
        OpenFlow13Utils.addMatchEthNsh(match);

        InstructionsBuilder isb = OpenFlow13Utils.appendGotoTableInstruction(new InstructionsBuilder(),
            NwConstants.EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE);
        String flowIdStr = EGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME + nodeId.getValue();

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
            EGRESS_CLASSIFIER_FILTER_NSH_PRIORITY, EGRESS_CLASSIFIER_FILTER_COOKIE,
            EGRESS_CLASSIFIER_FILTER_ETHNSH_FLOW_NAME, flowIdStr, match, isb).build();
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
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIpv4DstToNsc1Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveTunIdToNsc2Register(actionList.size()));
        actionList.add(OpenFlow13Utils.createActionNxMoveReg0ToTunIpv4Dst(actionList.size()));

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
     *     Final egress processing and egress packets. Determines if the
     *     packet should go to a local or remote SFF.
     *     Match on [Nsp, TunIpv4Dst==thisNodeIp], Resubmit to Ingress
     *     Dispatcher to be processed by SFC SFF on match
     */
    public Flow createEgressClassifierTransportEgressLocalFlow(NodeId nodeId, long nsp, String localIpStr) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);
        OpenFlow13Utils.addMatchTunIpv4Dst(match, localIpStr, DEFAULT_NETMASK);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionResubmitTable(NwConstants.LPORT_DISPATCHER_TABLE,
            actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp + "_" + localIpStr;

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
    public Flow createEgressClassifierTransportEgressRemoteFlow(NodeId nodeId, long nsp, long outport) {
        MatchBuilder match = OpenFlow13Utils.getNspMatch(nsp);

        List<Action> actionList = new ArrayList<>();
        actionList.add(OpenFlow13Utils.createActionOutPort("output:" + outport, actionList.size()));

        InstructionsBuilder isb = OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(actionList);
        String flowIdStr = EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME + nodeId.getValue() + "_" + nsp;

        return OpenFlow13Utils.createFlowBuilder(NwConstants.EGRESS_SFC_CLASSIFIER_EGRESS_TABLE,
            EGRESS_CLASSIFIER_EGRESS_REMOTE_PRIORITY, EGRESS_CLASSIFIER_TPORTEGRESS_COOKIE,
            EGRESS_CLASSIFIER_TPORTEGRESS_FLOW_NAME, flowIdStr, match, isb).build();
    }
}
