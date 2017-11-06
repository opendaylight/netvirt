/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.listeners;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.netvirt.aclservice.utils.AclConntrackClassifierType;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.DefaultBehavior;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener to handle flow capable node updates.
 */
@Singleton
@SuppressWarnings("deprecation")
public class AclNodeListener extends AsyncDataTreeChangeListenerBase<FlowCapableNode, AclNodeListener>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclNodeListener.class);

    private final IMdsalApiManager mdsalManager;
    private final AclserviceConfig config;
    private final DataBroker dataBroker;
    private final AclServiceUtils aclServiceUtils;
    private final AclDataUtil aclDataUtil;
    private final int dummyTag = 0;

    private SecurityGroupMode securityGroupMode = null;

    @Inject
    public AclNodeListener(final IMdsalApiManager mdsalManager, DataBroker dataBroker, AclserviceConfig config,
            AclServiceUtils aclServiceUtils, AclDataUtil aclDataUtil) {
        super(FlowCapableNode.class, AclNodeListener.class);

        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
        this.config = config;
        this.aclServiceUtils = aclServiceUtils;
        this.aclDataUtil = aclDataUtil;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        if (config != null) {
            this.securityGroupMode = config.getSecurityGroupMode();
        }
        this.aclServiceUtils.createRemoteAclIdPool();
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        LOG.info("AclserviceConfig: {}", this.config);
    }

    @Override
    public void close() {
        super.close();
        this.aclServiceUtils.deleteRemoteAclIdPool();
    }

    @Override
    protected InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        if (!aclDataUtil.doesDpnHaveAclInterface(dpnId)) {
            // serialize ACL pool deletion per switch
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(String.valueOf(dpnId), () -> {
                this.aclServiceUtils.deleteAclIdPools(dpnId);
                return Collections.emptyList();
            });
            LOG.debug("On FlowCapableNode remove event, ACL pools for dpid: {} are deleted.", dpnId);
        } else {
            LOG.info("On FlowCapableNode remove event, ACL pools for dpid: {} are not deleted "
                    + "because ACL ports are associated.", dpnId);
        }
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModificationBefore,
            FlowCapableNode dataObjectModificationAfter) {
        // do nothing
    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        LOG.trace("FlowCapableNode Added: key: {}", key);
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        createTableDefaultEntries(dpnId);
        // serialize ACL pool creation per switch
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(String.valueOf(dpnId), () -> {
            this.aclServiceUtils.createAclIdPools(dpnId);
            return Collections.emptyList();
        });
        LOG.trace("FlowCapableNode (dpid: {}) add event is processed.", dpnId);
    }

    /**
     * Creates the table miss entries.
     *
     * @param dpnId the dpn id
     */
    private void createTableDefaultEntries(BigInteger dpnId) {
        LOG.info("Adding default ACL entries for mode {}",
                securityGroupMode == null ? SecurityGroupMode.Stateful : securityGroupMode);

        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            addStatefulIngressDefaultFlows(dpnId);
            addStatefulEgressDefaultFlows(dpnId);
        } else {
            LOG.error("Invalid security group mode ({}) obtained from AclserviceConfig.", securityGroupMode);
        }
    }

    private void addStatefulIngressDefaultFlows(BigInteger dpId) {
        addIngressAclTableMissFlows(dpId);
        addIngressDropFlows(dpId);
        addIngressConntrackClassifierFlows(dpId);
        addIngressConntrackStateRules(dpId);
    }

    private void addStatefulEgressDefaultFlows(BigInteger dpId) {
        addEgressAclTableMissFlows(dpId);
        addEgressConntrackClassifierFlows(dpId);
        addEgressConntrackStateRules(dpId);
        addEgressAllowBroadcastFlow(dpId);
        addEgressConntrackDummyLookup(dpId);
    }

    /**
     * Adds the ingress acl table miss flows.
     *
     * @param dpId the dp id
     */
    private void addIngressAclTableMissFlows(BigInteger dpId) {
        addDropOrAllowTableMissFlow(dpId, NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE,
                NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE);

        InstructionInfo writeMetatdata = AclServiceUtils
                .getWriteMetadataForAclClassifierType(AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED);
        List<InstructionInfo> instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE, instructions);

        addDropOrAllowTableMissFlow(dpId, NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE,
                NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(dpId, NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.INGRESS_REMOTE_ACL_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(dpId, NwConstants.INGRESS_ACL_COMMITTER_TABLE, NwConstants.LPORT_DISPATCHER_TABLE);

        LOG.debug("Added Stateful Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addEgressAclTableMissFlows(BigInteger dpId) {
        // EGRESS_ACL_DUMMY_TABLE exists on egress side only.
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.EGRESS_ACL_DUMMY_TABLE,
                NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
        addDropOrAllowTableMissFlow(dpId, NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE,
                NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE);

        InstructionInfo writeMetatdata = AclServiceUtils
                .getWriteMetadataForAclClassifierType(AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED);
        List<InstructionInfo> instructions = Lists.newArrayList(writeMetatdata);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE, instructions);

        addDropOrAllowTableMissFlow(dpId, NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE,
                NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(dpId, NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE,
                NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addGotoOrResubmitTableMissFlow(dpId, NwConstants.EGRESS_REMOTE_ACL_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
        addDropOrAllowTableMissFlow(dpId, NwConstants.EGRESS_ACL_COMMITTER_TABLE, NwConstants.LPORT_DISPATCHER_TABLE);

        LOG.debug("Added Stateful Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    private void addIngressDropFlows(BigInteger dpId) {
        List<InstructionInfo> dropInstructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        List<MatchInfoBase> arpDropMatches = new ArrayList<>();
        arpDropMatches.add(MatchEthernetType.ARP);
        FlowEntity antiSpoofingArpDropFlowEntity = MDSALUtil.buildFlowEntity(dpId,
                NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress ACL Table ARP Drop Flow",
                AclConstants.PROTO_ARP_TRAFFIC_DROP_PRIORITY, "Ingress ACL Table ARP Drop Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, arpDropMatches, dropInstructions);
        mdsalManager.installFlow(antiSpoofingArpDropFlowEntity);

        List<MatchInfoBase> ipDropMatches = new ArrayList<>();
        ipDropMatches.add(MatchEthernetType.IPV4);
        FlowEntity antiSpoofingIpDropFlowEntity = MDSALUtil.buildFlowEntity(dpId,
                NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress ACL Table IP Drop Flow",
                AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY, "Ingress ACL Table IP Drop Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, ipDropMatches, dropInstructions);
        mdsalManager.installFlow(antiSpoofingIpDropFlowEntity);

        List<MatchInfoBase> ipv6DropMatches = new ArrayList<>();
        ipv6DropMatches.add(MatchEthernetType.IPV6);
        FlowEntity antiSpoofingIpv6DropFlowEntity = MDSALUtil.buildFlowEntity(dpId,
                NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE, "Ingress ACL Table IPv6 Drop Flow",
                AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY, "Ingress ACL Table IPv6 Drop Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, ipv6DropMatches, dropInstructions);
        mdsalManager.installFlow(antiSpoofingIpv6DropFlowEntity);
    }

    private void addDropOrAllowTableMissFlow(BigInteger dpId, short tableId, short nextTableId) {
        List<MatchInfo> matches = Collections.emptyList();
        List<InstructionInfo> instructions;
        if (config.getDefaultBehavior() == DefaultBehavior.Deny) {
            instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        } else {
            instructions = getGotoOrResubmitInstructions(tableId, nextTableId);
        }
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, getTableMissFlowId(tableId), 0, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions);
        mdsalManager.installFlow(flowEntity);
    }

    private void addGotoOrResubmitTableMissFlow(BigInteger dpId, short tableId, short nextTableId) {
        addGotoOrResubmitTableMissFlow(dpId, tableId, nextTableId, null);
    }

    private void addGotoOrResubmitTableMissFlow(BigInteger dpId, short tableId, short nextTableId,
            List<InstructionInfo> instructions) {
        List<MatchInfoBase> matches = Collections.emptyList();
        List<InstructionInfo> ins = getGotoOrResubmitInstructions(tableId, nextTableId);
        if (instructions != null && !instructions.isEmpty()) {
            ins.addAll(instructions);
        }
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, getTableMissFlowId(tableId), 0, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, ins);
        mdsalManager.installFlow(flowEntity);
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

    private void addIngressConntrackStateRules(BigInteger dpId) {
        addConntrackStateRules(dpId, NwConstants.LPORT_DISPATCHER_TABLE,
                NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    }

    private void addEgressConntrackStateRules(BigInteger dpId) {
        addConntrackStateRules(dpId, NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
                NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    }

    private void addIngressConntrackClassifierFlows(BigInteger dpnId) {
        addConntrackClassifierFlows(dpnId, NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
    }

    private void addEgressConntrackClassifierFlows(BigInteger dpnId) {
        addConntrackClassifierFlows(dpnId, NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE,
                NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
    }

    private void addConntrackClassifierFlows(BigInteger dpnId, short tableId, short gotoTableId) {
        for (IPProtocols protocol : AclConstants.PROTOCOLS_SUPPORTED_BY_CONNTRACK) {
            switch (protocol) {
                case TCP:
                case UDP:
                    // For tcp and udp, create one flow each for IPv4 and IPv6
                    programConntrackClassifierFlow(dpnId, tableId, gotoTableId, MatchEthernetType.IPV4, protocol);
                    programConntrackClassifierFlow(dpnId, tableId, gotoTableId, MatchEthernetType.IPV6, protocol);
                    break;
                case ICMP:
                    programConntrackClassifierFlow(dpnId, tableId, gotoTableId, MatchEthernetType.IPV4, protocol);
                    break;
                case IPV6ICMP:
                    programConntrackClassifierFlow(dpnId, tableId, gotoTableId, MatchEthernetType.IPV6, protocol);
                    break;
                default:
                    LOG.error("Invalid protocol [{}] for conntrack", protocol);
            }
        }
    }

    private void programConntrackClassifierFlow(BigInteger dpId, short tableId, short gotoTableId,
            MatchEthernetType etherType, IPProtocols protocol) {
        String flowId = "Fixed_Conntrk_Classifier_" + dpId + "_" + tableId + "_" + etherType + "_" + protocol.name();

        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.buildIpProtocolMatches(etherType, protocol));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(gotoTableId);
        InstructionInfo writeMetatdata =
                AclServiceUtils.getWriteMetadataForAclClassifierType(AclConntrackClassifierType.CONNTRACK_SUPPORTED);
        instructions.add(writeMetatdata);

        syncFlow(dpId, tableId, flowId, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE,
                matches, instructions, NwConstants.ADD_FLOW);
    }

    private void addEgressAllowBroadcastFlow(BigInteger dpId) {
        final List<MatchInfoBase> ipBroadcastMatches =
                AclServiceUtils.buildBroadcastIpV4Matches(AclConstants.IPV4_ALL_SUBNET_BROADCAST_ADDR);
        List<InstructionInfo> ipBroadcastInstructions =
                AclServiceOFFlowBuilder.getGotoInstructionInfo(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE);
        String ipBroadcastflowName = "Ingress_v4_Broadcast_" + dpId + "_Permit";
        FlowEntity ipBroadcastFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE,
                ipBroadcastflowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE,
                ipBroadcastMatches, ipBroadcastInstructions);
        mdsalManager.installFlow(ipBroadcastFlowEntity);

        final List<MatchInfoBase> l2BroadcastMatch = AclServiceUtils.buildL2BroadcastMatches();
        List<InstructionInfo> l2BroadcastInstructions =
                AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
        String l2BroadcastflowName = "Ingress_L2_Broadcast_" + dpId + "_Permit";
        FlowEntity l2BroadcastFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE,
                l2BroadcastflowName, AclConstants.PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, l2BroadcastMatch, l2BroadcastInstructions);
        mdsalManager.installFlow(l2BroadcastFlowEntity);
    }

    private void addConntrackStateRules(BigInteger dpnId, short dispatcherTableId, short tableId) {
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Tracked_Established",
                AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_EST_CT_STATE_MASK, dispatcherTableId, tableId);
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Tracked_Related",
                AclConstants.TRACKED_REL_CT_STATE, AclConstants.TRACKED_REL_CT_STATE_MASK, dispatcherTableId, tableId);
    }

    /**
     * Adds the rule to forward the known packets.
     *
     * @param dpId the dpId
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param dispatcherTableId the dispatcher table id
     * @param tableId the table id
     */
    private void programConntrackForwardRule(BigInteger dpId, Integer priority, String flowId, int conntrackState,
            int conntrackMask, short dispatcherTableId, short tableId) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchCtState(conntrackState, conntrackMask));
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getResubmitInstructionInfo(dispatcherTableId);

        flowId = "Fixed_Conntrk_Trk_" + dpId + "_" + flowId + dispatcherTableId;
        syncFlow(dpId, tableId, flowId, priority, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions,
                NwConstants.ADD_FLOW);
    }

    private void addEgressConntrackDummyLookup(BigInteger dpnId) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(AclConstants.TRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK));
        int elanTag = dummyTag;
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxConntrack(2, 0, 0, elanTag, NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowName = "Egress_Fixed_Dummy_Table_Ipv4_" + dpnId;
        syncFlow(dpnId, NwConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, NwConstants.ADD_FLOW);
        matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new NxMatchCtState(AclConstants.TRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK));
        flowName = "Egress_Fixed_Dummy_Table_Ipv6_" + dpnId;
        syncFlow(dpnId, NwConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, NwConstants.ADD_FLOW);
    }

    /**
     * Writes/remove the flow to/from the datastore.
     *
     * @param dpId the dpId
     * @param tableId the tableId
     * @param flowId the flowId
     * @param priority the priority
     * @param flowName the flow name
     * @param idleTimeOut the idle timeout
     * @param hardTimeOut the hard timeout
     * @param cookie the cookie
     * @param matches the list of matches to be written
     * @param instructions the list of instruction to be written.
     * @param addOrRemove add or remove the entries.
     */
    protected void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
            int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, null);
            LOG.trace("Removing Acl Flow:: DpnId: {}, flowId: {}, flowName: {}, tableId: {}", dpId, flowId, flowName,
                    tableId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, instructions);
            LOG.trace("Installing Acl Flow:: DpnId: {}, flowId: {}, flowName: {}, tableId: {}", dpId, flowId, flowName,
                    tableId);
            mdsalManager.installFlow(flowEntity);
        }
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

    @Override
    protected AclNodeListener getDataTreeChangeListener() {
        return AclNodeListener.this;
    }
}
