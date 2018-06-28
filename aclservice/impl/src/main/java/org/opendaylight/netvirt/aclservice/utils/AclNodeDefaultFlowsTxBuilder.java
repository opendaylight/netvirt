/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.TransactionAdapter;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxCtClear;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtMark;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.DefaultBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The transaction builder class for ACL node default flows.
 *
 * @author Somashekar Byrappa
 */
public class AclNodeDefaultFlowsTxBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(AclNodeDefaultFlowsTxBuilder.class);

    private final BigInteger dpId;
    private final IMdsalApiManager mdsalManager;
    private final AclserviceConfig config;
    private final TypedWriteTransaction<Datastore.Configuration> tx;

    public AclNodeDefaultFlowsTxBuilder(BigInteger dpId, IMdsalApiManager mdsalManager, AclserviceConfig config,
            TypedWriteTransaction<Datastore.Configuration> tx) {
        this.dpId = dpId;
        this.mdsalManager = mdsalManager;
        this.config = config;
        this.tx = tx;
    }

    public void build() {
        createTableDefaultEntries();
    }


    /**
     * Creates the table default entries.
     */
    private void createTableDefaultEntries() {
        addStatefulIngressDefaultFlows();
        addStatefulEgressDefaultFlows();
    }

    private void addStatefulIngressDefaultFlows() {
        addIngressAclTableMissFlows();
        addIngressDropFlows();
        addIngressAntiSpoofingTableGotoFlows();
        addIngressConntrackClassifierFlows();
        addIngressConntrackStateRules();
    }

    private void addStatefulEgressDefaultFlows() {
        addEgressAclTableMissFlows();
        addEgressDropFlows();
        addEgressConntrackClassifierFlows();
        addEgressConntrackStateRules();
        addEgressAllowBroadcastFlow();
        addEgressCtClearRule();
    }

    /**
     * Adds the ingress acl table miss flows.
     */
    private void addIngressAclTableMissFlows() {
        InstructionInfo writeMetatdata = AclServiceUtils.getWriteMetadataForDropFlag();
        List<InstructionInfo> instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE,
                NwConstants.INGRESS_ACL_COMMITTER_TABLE, instructions);

        writeMetatdata = AclServiceUtils
                .getWriteMetadataForAclClassifierType(AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED);
        instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE, instructions);

        addDropOrAllowTableMissFlow(NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE,
                NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.INGRESS_REMOTE_ACL_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(NwConstants.INGRESS_ACL_COMMITTER_TABLE, NwConstants.LPORT_DISPATCHER_TABLE);

        LOG.debug("Added Stateful Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     */
    private void addEgressAclTableMissFlows() {
        // EGRESS_ACL_DUMMY_TABLE exists on egress side only.
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_ACL_DUMMY_TABLE, NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);

        InstructionInfo writeMetatdata = AclServiceUtils.getWriteMetadataForDropFlag();
        List<InstructionInfo> instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE,
                NwConstants.EGRESS_ACL_COMMITTER_TABLE, instructions);

        writeMetatdata = AclServiceUtils
                .getWriteMetadataForAclClassifierType(AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED);
        instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE, instructions);

        addDropOrAllowTableMissFlow(NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE,
                NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addGotoOrResubmitTableMissFlow(NwConstants.EGRESS_REMOTE_ACL_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(NwConstants.EGRESS_ACL_COMMITTER_TABLE, NwConstants.LPORT_DISPATCHER_TABLE);

        LOG.debug("Added Stateful Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    private void addIngressDropFlows() {
        List<InstructionInfo> dropInstructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        addFlowToTx(NwConstants.INGRESS_ACL_COMMITTER_TABLE, "Ingress_Committer_Drop_Flow",
                AclConstants.COMMITTER_TABLE_DROP_PRIORITY, getMetadataForCommitterDropFlag(), dropInstructions);
    }

    private void addEgressDropFlows() {
        List<InstructionInfo> dropInstructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        addFlowToTx(NwConstants.EGRESS_ACL_COMMITTER_TABLE, "Egress_Committer_Drop_Flow",
                AclConstants.COMMITTER_TABLE_DROP_PRIORITY, getMetadataForCommitterDropFlag(), dropInstructions);
    }

    private void addIngressAntiSpoofingTableGotoFlows() {
        InstructionInfo writeMetatdata = AclServiceUtils.getWriteMetadataForDropFlag();
        List<InstructionInfo> gotoInstructions = AclServiceOFFlowBuilder
                .getGotoInstructionInfo(NwConstants.INGRESS_ACL_COMMITTER_TABLE);
        gotoInstructions.add(writeMetatdata);

        List<MatchInfoBase> arpGotoMatches = new ArrayList<>();
        arpGotoMatches.add(MatchEthernetType.ARP);
        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress_ACL_Table_ARP_GOTO_Flow",
                AclConstants.PROTO_ARP_TRAFFIC_DROP_PRIORITY, arpGotoMatches, gotoInstructions);

        List<MatchInfoBase> ipGotoMatches = new ArrayList<>();
        ipGotoMatches.add(MatchEthernetType.IPV4);
        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress_ACL_Table_IP_GOTO_Flow",
                AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY, ipGotoMatches, gotoInstructions);

        List<MatchInfoBase> ipv6GotoMatches = new ArrayList<>();
        ipv6GotoMatches.add(MatchEthernetType.IPV6);
        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress_ACL_Table_IPv6_GOTO_Flow",
                AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY, ipv6GotoMatches, gotoInstructions);

        addIngressAclDhcpServerTrafficFlow(gotoInstructions);
        addIngressAclDhcpv6ServerTrafficFlow(gotoInstructions);
        addIngressAclIcmpv6RouterAdvtsFlow(gotoInstructions);
    }

    private void addIngressAclDhcpServerTrafficFlow(List<InstructionInfo> gotoInstructions) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.UDP);
        matches.add(new MatchUdpDestinationPort(AclConstants.DHCP_CLIENT_PORT_IPV4));
        matches.add(new MatchUdpSourcePort(AclConstants.DHCP_SERVER_PORT_IPV4));

        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Egress_DHCP_Server_v4_GOTO_FLOW",
                AclConstants.PROTO_MATCH_PRIORITY, matches, gotoInstructions);
    }

    private void addIngressAclDhcpv6ServerTrafficFlow(List<InstructionInfo> gotoInstructions) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.UDP);
        matches.add(new MatchUdpDestinationPort(AclConstants.DHCP_CLIENT_PORT_IPV6));
        matches.add(new MatchUdpSourcePort(AclConstants.DHCP_SERVER_PORT_IPV6));

        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Egress_DHCP_Server_v6_GOTO_FLOW",
                AclConstants.PROTO_MATCH_PRIORITY, matches, gotoInstructions);
    }

    private void addIngressAclIcmpv6RouterAdvtsFlow(List<InstructionInfo> gotoInstructions) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6((short) AclConstants.ICMPV6_TYPE_RA, (short) 0));

        addFlowToTx(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE,
                "Egress_ICMPv6_" + AclConstants.ICMPV6_TYPE_RA + "_GOTO_FLOW",
                AclConstants.PROTO_IPV6_DROP_PRIORITY, matches, gotoInstructions);
    }

    private List<MatchInfoBase> getMetadataForCommitterDropFlag() {
        List<MatchInfoBase> matches = new ArrayList<>();
        BigInteger metaData = MetaDataUtil.METADATA_MASK_ACL_DROP
                .and(AclConstants.METADATA_DROP_FLAG.shiftLeft(2));
        BigInteger metaDataMask = MetaDataUtil.METADATA_MASK_ACL_DROP
                .and(AclConstants.METADATA_DROP_FLAG.shiftLeft(2));
        matches.add(new MatchMetadata(metaData, metaDataMask));

        return matches;
    }

    private void addDropOrAllowTableMissFlow(short tableId, short nextTableId) {
        List<MatchInfo> matches = Collections.emptyList();
        List<InstructionInfo> instructions;
        if (config.getDefaultBehavior() == DefaultBehavior.Deny) {
            instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        } else {
            instructions = getGotoOrResubmitInstructions(tableId, nextTableId);
        }
        addFlowToTx(tableId, getTableMissFlowId(tableId), AclConstants.ACL_TABLE_MISS_PRIORITY, matches, instructions);
    }

    private void addGotoOrResubmitTableMissFlow(short tableId, short nextTableId) {
        addGotoOrResubmitTableMissFlow(tableId, nextTableId, null);
    }

    private void addGotoOrResubmitTableMissFlow(short tableId, short nextTableId, List<InstructionInfo> instructions) {
        List<MatchInfoBase> matches = Collections.emptyList();
        List<InstructionInfo> ins = getGotoOrResubmitInstructions(tableId, nextTableId);
        if (instructions != null && !instructions.isEmpty()) {
            ins.addAll(instructions);
        }
        addFlowToTx(tableId, getTableMissFlowId(tableId), AclConstants.ACL_TABLE_MISS_PRIORITY, matches, ins);
    }

    private List<InstructionInfo> getGotoOrResubmitInstructions(short tableId, short nextTableId) {
        List<InstructionInfo> instructions;
        if (tableId < nextTableId) {
            instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(nextTableId);
        } else {
            instructions = AclServiceOFFlowBuilder.getResubmitInstructionInfo(nextTableId);
        }
        return instructions;
    }

    private void addIngressConntrackStateRules() {
        addConntrackStateRules(NwConstants.LPORT_DISPATCHER_TABLE, NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    }

    private void addEgressConntrackStateRules() {
        addConntrackStateRules(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    }

    private void addIngressConntrackClassifierFlows() {
        addConntrackClassifierFlows(NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
    }

    private void addEgressConntrackClassifierFlows() {
        addConntrackClassifierFlows(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
    }

    private void addConntrackClassifierFlows(short tableId, short gotoTableId) {
        for (IPProtocols protocol : AclConstants.PROTOCOLS_SUPPORTED_BY_CONNTRACK) {
            switch (protocol) {
                case TCP:
                case UDP:
                    // For tcp and udp, create one flow each for IPv4 and IPv6
                    programConntrackClassifierFlow(tableId, gotoTableId, MatchEthernetType.IPV4, protocol);
                    programConntrackClassifierFlow(tableId, gotoTableId, MatchEthernetType.IPV6, protocol);
                    break;
                case ICMP:
                    programConntrackClassifierFlow(tableId, gotoTableId, MatchEthernetType.IPV4, protocol);
                    break;
                case IPV6ICMP:
                    programConntrackClassifierFlow(tableId, gotoTableId, MatchEthernetType.IPV6, protocol);
                    break;
                default:
                    LOG.error("Invalid protocol [{}] for conntrack", protocol);
            }
        }
    }

    private void programConntrackClassifierFlow(short tableId, short gotoTableId, MatchEthernetType etherType,
            IPProtocols protocol) {
        String flowId = "Fixed_Conntrk_Classifier_" + dpId + "_" + tableId + "_" + etherType + "_" + protocol.name();

        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.buildIpProtocolMatches(etherType, protocol));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(gotoTableId);
        InstructionInfo writeMetatdata =
                AclServiceUtils.getWriteMetadataForAclClassifierType(AclConntrackClassifierType.CONNTRACK_SUPPORTED);
        instructions.add(writeMetatdata);

        addFlowToTx(tableId, flowId, AclConstants.ACL_DEFAULT_PRIORITY, matches, instructions);
    }

    private void addEgressAllowBroadcastFlow() {
        final List<MatchInfoBase> ipBroadcastMatches =
                AclServiceUtils.buildBroadcastIpV4Matches(AclConstants.IPV4_ALL_SUBNET_BROADCAST_ADDR);
        List<InstructionInfo> ipBroadcastInstructions =
                AclServiceOFFlowBuilder.getGotoInstructionInfo(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE);
        String ipBroadcastflowName = "Ingress_v4_Broadcast_" + dpId + "_Permit";
        addFlowToTx(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE, ipBroadcastflowName, AclConstants.PROTO_MATCH_PRIORITY,
                ipBroadcastMatches, ipBroadcastInstructions);

        final List<MatchInfoBase> l2BroadcastMatch = AclServiceUtils.buildL2BroadcastMatches();
        List<InstructionInfo> l2BroadcastInstructions =
                AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
        String l2BroadcastflowName = "Ingress_L2_Broadcast_" + dpId + "_Permit";
        addFlowToTx(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE, l2BroadcastflowName,
                AclConstants.PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY, l2BroadcastMatch, l2BroadcastInstructions);
    }

    private void addConntrackStateRules(short dispatcherTableId, short tableId) {
        programConntrackForwardRule(AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Tracked_Established",
                AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_EST_CT_STATE_MASK,
                dispatcherTableId, tableId, true);
        programConntrackForwardRule(AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Tracked_Related",
                AclConstants.TRACKED_REL_CT_STATE, AclConstants.TRACKED_REL_CT_STATE_MASK,
                dispatcherTableId, tableId, true);
        programConntrackForwardRule(AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Untracked_Related",
                AclConstants.UNTRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK,
                NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE, tableId, false);
    }

    /**
     * Adds the rule to forward the known packets.
     *
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param dispatcherTableId the dispatcher table id
     * @param tableId the table id
     */
    private void programConntrackForwardRule(Integer priority, String flowId, int conntrackState, int conntrackMask,
            short dispatcherTableId, short tableId, boolean shouldMatchMark) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchCtState(conntrackState, conntrackMask));
        if (shouldMatchMark) {
            matches.add(new NxMatchCtMark(AclConstants.CT_MARK_EST_STATE, AclConstants.CT_MARK_EST_STATE_MASK));
        }
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxCtClear());
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        flowId = "Fixed_Conntrk_Trk_" + dpId + "_" + flowId + dispatcherTableId;
        addFlowToTx(tableId, flowId, priority, matches, instructions);
    }

    private void addEgressCtClearRule() {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxCtClear());
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE));
        String flowName = "Egress_Fixed_Ct_Clear_Table_Ipv4_" + this.dpId;
        addFlowToTx(NwConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.ACL_DEFAULT_PRIORITY, matches,
                instructions);
        matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        flowName = "Egress_Fixed_Ct_Clear_Table_Ipv6_" + this.dpId;
        addFlowToTx(NwConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.ACL_DEFAULT_PRIORITY, matches,
                instructions);
    }

    private void addFlowToTx(short tableId, String flowId, int priority, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions) {
        int idleTimeOut = 0;
        int hardTimeOut = 0;
        BigInteger cookie = AclConstants.COOKIE_ACL_BASE;
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(this.dpId, tableId, flowId, priority, flowId, idleTimeOut,
                                                          hardTimeOut, cookie, matches, instructions);
        LOG.trace("Installing Acl default Flow:: DpnId: {}, flowId: {}, flowName: {}, tableId: {}", dpId, flowId,
                  flowId, tableId);
        mdsalManager.addFlowToTx(flowEntity, TransactionAdapter.toWriteTransaction(tx));
    }

    /**
     * Gets the table miss flow id.
     *
     * @param tableId the table id
     * @return the table miss flow id
     */
    private String getTableMissFlowId(short tableId) {
        return String.valueOf(tableId);
    }
}
