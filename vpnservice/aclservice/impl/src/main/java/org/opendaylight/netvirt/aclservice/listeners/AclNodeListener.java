/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.listeners;

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
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
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
public class AclNodeListener extends AsyncDataTreeChangeListenerBase<FlowCapableNode, AclNodeListener>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclNodeListener.class);

    private final IMdsalApiManager mdsalManager;
    private final AclserviceConfig config;
    private final DataBroker dataBroker;
    private final AclServiceUtils aclServiceUtils;
    private final AclDataUtil aclDataUtil;
    private final int dummyTag = 0;
    private final JobCoordinator jobCoordinator;

    private SecurityGroupMode securityGroupMode = null;

    @Inject
    public AclNodeListener(final IMdsalApiManager mdsalManager, DataBroker dataBroker, AclserviceConfig config,
            AclServiceUtils aclServiceUtils, AclDataUtil aclDataUtil, JobCoordinator jobCoordinator) {
        super(FlowCapableNode.class, AclNodeListener.class);

        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
        this.config = config;
        this.aclServiceUtils = aclServiceUtils;
        this.aclDataUtil = aclDataUtil;
        this.jobCoordinator = jobCoordinator;
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
            jobCoordinator.enqueueJob(String.valueOf(dpnId), () -> {
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
        jobCoordinator.enqueueJob(String.valueOf(dpnId), () -> {
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
            addConntrackDummyLookup(dpnId, NwConstants.ADD_FLOW);
        } else {
            LOG.error("Invalid security group mode ({}) obtained from AclserviceConfig.", securityGroupMode);
        }
    }

    private void addStatefulEgressDefaultFlows(BigInteger dpId) {
        addStatefulEgressAclTableMissFlow(dpId);
        addConntrackRules(dpId, NwConstants.EGRESS_LPORT_DISPATCHER_TABLE, NwConstants.EGRESS_ACL_FILTER_TABLE,
                NwConstants.ADD_FLOW);
        addStatefulEgressDropFlows(dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addStatefulEgressAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> instructionsAcl = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE), 0, "Egress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAcl);
        mdsalManager.installFlow(flowEntity);

        addEgressAclRemoteAclTableMissFlow(dpId);

        List<InstructionInfo> instructionsAclFilter = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_FILTER_TABLE), 0, "Egress ACL Filter Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAclFilter);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addEgressAclRemoteAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_FILTER_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE), 0, "Egress ACL Remote Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Egress ACL Remote Table Miss Flows for dpn {}", dpId);
    }

    private void addStatefulEgressDropFlows(BigInteger dpId) {
        List<InstructionInfo> dropInstructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        List<MatchInfoBase> arpDropMatches = new ArrayList<>();
        arpDropMatches.add(MatchEthernetType.ARP);
        FlowEntity antiSpoofingArpDropFlowEntity =
                MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
                        "Egress ACL Table ARP Drop Flow", AclConstants.PROTO_ARP_TRAFFIC_DROP_PRIORITY,
                        "Egress ACL Table ARP Drop Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, arpDropMatches,
                        dropInstructions);
        mdsalManager.installFlow(antiSpoofingArpDropFlowEntity);

        List<MatchInfoBase> ipDropMatches = new ArrayList<>();
        ipDropMatches.add(MatchEthernetType.IPV4);
        FlowEntity antiSpoofingIpDropFlowEntity =
                MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
                        "Egress ACL Table IP Drop Flow", AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY,
                        "Egress ACL Table IP Drop Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, ipDropMatches,
                        dropInstructions);
        mdsalManager.installFlow(antiSpoofingIpDropFlowEntity);

        List<MatchInfoBase> ipv6DropMatches = new ArrayList<>();
        ipv6DropMatches.add(MatchEthernetType.IPV6);
        FlowEntity antiSpoofingIpv6DropFlowEntity =
                MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
                        "Egress ACL Table IPv6 Drop Flow", AclConstants.PROTO_IP_TRAFFIC_DROP_PRIORITY,
                        "Egress ACL Table IPv6 Drop Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, ipv6DropMatches,
                        dropInstructions);
        mdsalManager.installFlow(antiSpoofingIpv6DropFlowEntity);
    }

    private void addStatefulIngressDefaultFlows(BigInteger dpId) {
        addStatefulIngressAclTableMissFlow(dpId);
        addConntrackRules(dpId, NwConstants.LPORT_DISPATCHER_TABLE, NwConstants.INGRESS_ACL_FILTER_TABLE,
                NwConstants.ADD_FLOW);
        addStatefulIngressAllowBroadcastFlow(dpId);
    }

    /**
     * Adds the ingress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addStatefulIngressAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> instructionsAcl = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
            getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE), 0, "Ingress ACL Table Miss Flow", 0, 0,
            AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAcl);
        mdsalManager.installFlow(flowEntity);

        addIngressAclRemoteAclTableMissFlow(dpId);

        List<InstructionInfo> instructionsAclFilter = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.LPORT_DISPATCHER_TABLE);
        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE,
            getTableMissFlowId(NwConstants.INGRESS_ACL_FILTER_TABLE), 0, "Ingress ACL Table Miss Flow", 0, 0,
            AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAclFilter);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Stateful Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the ingress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addIngressAclRemoteAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.INGRESS_ACL_FILTER_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE,
                getTableMissFlowId(NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE), 0, "Ingress ACL Remote Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Ingress ACL Remote Table Miss Flows for dpn {}", dpId);
    }

    private void addStatefulIngressAllowBroadcastFlow(BigInteger dpId) {
        final List<MatchInfoBase> ipBroadcastMatches =
                AclServiceUtils.buildBroadcastIpV4Matches(AclConstants.IPV4_ALL_SUBNET_BROADCAST_ADDR);
        List<InstructionInfo> ipBroadcastInstructions = new ArrayList<>();
        ipBroadcastInstructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE));
        String ipBroadcastflowName = "Ingress_v4_Broadcast_" + dpId + "_Permit";
        FlowEntity ipBroadcastFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                ipBroadcastflowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE,
                ipBroadcastMatches, ipBroadcastInstructions);
        mdsalManager.installFlow(ipBroadcastFlowEntity);

        final List<MatchInfoBase> l2BroadcastMatch = AclServiceUtils.buildL2BroadcastMatches();
        List<ActionInfo> l2BroadcastActionsInfos = new ArrayList<>();
        List<InstructionInfo> l2BroadcastInstructions = getDispatcherTableResubmitInstructions(l2BroadcastActionsInfos,
                NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);
        String l2BroadcastflowName = "Ingress_L2_Broadcast_" + dpId + "_Permit";
        FlowEntity l2BroadcastFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                l2BroadcastflowName, AclConstants.PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, l2BroadcastMatch, l2BroadcastInstructions);
        mdsalManager.installFlow(l2BroadcastFlowEntity);
    }

    private void addConntrackRules(BigInteger dpnId, short dispatcherTableId,short tableId, int write) {
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Established", AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_EST_CT_STATE_MASK,
            dispatcherTableId, tableId, write);
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,"Tracked_Related", AclConstants
            .TRACKED_REL_CT_STATE, AclConstants.TRACKED_REL_CT_STATE_MASK, dispatcherTableId, tableId, write);
    }

    private void addConntrackDummyLookup(BigInteger dpnId, int write) {
        addConntrackIngressDummyLookup(dpnId, write);
    }

    private void addConntrackIngressDummyLookup(BigInteger dpnId, int write) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(AclConstants.TRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK));
        int elanTag = dummyTag;
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxConntrack(2, 0, 0, elanTag, NwConstants.EGRESS_ACL_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowName = "Egress_Fixed_Dummy_Table_Ipv4_" + dpnId;
        syncFlow(dpnId, AclConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, write);
        matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new NxMatchCtState(AclConstants.TRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK));
        flowName = "Egress_Fixed_Dummy_Table_Ipv6_" + dpnId;
        syncFlow(dpnId, AclConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, write);

        //Adding dummy lookup miss entry
        matches = new ArrayList<>();
        instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_TABLE));
        flowName = "Egress_Fixed_Dummy_Table_Miss_" + dpnId;
        syncFlow(dpnId, AclConstants.EGRESS_ACL_DUMMY_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, write);
    }

    /**
     * Adds the rule to forward the packets known packets.
     *
     * @param dpId the dpId
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param dispatcherTableId the dispatcher table id
     * @param tableId the table id
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackForwardRule(BigInteger dpId, Integer priority, String flowId,
            int conntrackState, int conntrackMask, short dispatcherTableId, short tableId, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchCtState(conntrackState, conntrackMask));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(
            new ArrayList<>(),dispatcherTableId);

        flowId = "Fixed_Conntrk_Trk_" + dpId + "_" + flowId + dispatcherTableId;
        syncFlow(dpId, tableId, flowId, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Gets the dispatcher table resubmit instructions.
     *
     * @param actionsInfos the actions infos
     * @param dispatcherTableId the dispatcher table id
     * @return the instructions for dispatcher table resubmit
     */
    private List<InstructionInfo> getDispatcherTableResubmitInstructions(List<ActionInfo> actionsInfos,
                                                                         short dispatcherTableId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        instructions.add(new InstructionApplyActions(actionsInfos));
        return instructions;
    }

    /**
     * Writes/remove the flow to/from the datastore.
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
                          int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase>  matches,
                          List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,flowId,
                priority, flowName , idleTimeOut, hardTimeOut, cookie, matches, null);
            LOG.trace("Removing Acl Flow:: DpnId: {}, flowId: {}, flowName: {}, tableId: {}", dpId,
                flowId, flowName, tableId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId,
                priority, flowName, idleTimeOut, hardTimeOut, cookie, matches, instructions);
            LOG.trace("Installing Acl Flow:: DpnId: {}, flowId: {}, flowName: {}, tableId: {}", dpId,
                flowId, flowName, tableId);
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
