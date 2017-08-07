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
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
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

import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg5;
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
        LOG.info("Adding default ACL entries for mode: "
                + (securityGroupMode == null ? SecurityGroupMode.Stateful : securityGroupMode));

        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            addStatefulIngressAclTableMissFlow(dpnId);
            addStatefulEgressAclTableMissFlow(dpnId);
            addConntrackRules(dpnId, NwConstants.LPORT_DISPATCHER_TABLE, NwConstants.INGRESS_ACL_FILTER_TABLE,
                    NwConstants.ADD_FLOW);
            addConntrackRules(dpnId, NwConstants.EGRESS_LPORT_DISPATCHER_TABLE, NwConstants.EGRESS_ACL_FILTER_TABLE,
                    NwConstants.ADD_FLOW);
        } else if (securityGroupMode == SecurityGroupMode.Transparent) {
            addTransparentIngressAclTableMissFlow(dpnId);
            addTransparentEgressAclTableMissFlow(dpnId);
        } else if (securityGroupMode == SecurityGroupMode.Learn) {
            addLearnIngressAclTableMissFlow(dpnId);
            addLearnEgressAclTableMissFlow(dpnId);
        } else {
            LOG.error("Invalid security group mode (" + securityGroupMode + ") obtained from AclserviceConfig.");
        }
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

    private void addEgressAclTableAllowFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> allowAllInstructions = new ArrayList<>();
        allowAllInstructions.add(new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE));

        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE), 0, "Egress ACL Table allow all Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, allowAllInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Egress ACL Table Allow all Flows for dpn {}", dpId);
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

    private void addEgressAclFilterTableAllowFlow(BigInteger dpId) {
        short dispatcherTableId =  NwConstants.EGRESS_LPORT_DISPATCHER_TABLE;

        List<MatchInfo> mkMatches = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        instructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_FILTER_TABLE), 0, "Egress ACL Filter Table allow all Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, instructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Egress ACL Filter Table allow all Flows for dpn {}", dpId);
    }

    private void addLearnEgressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionNxResubmit(NwConstants.EGRESS_LEARN_TABLE));
        actionsInfos.add(new ActionNxResubmit(NwConstants.EGRESS_LEARN_ACL_REMOTE_ACL_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity doubleResubmitTable = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                "RESUB-" + getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress resubmit ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, Collections.emptyList(), instructions);
        mdsalManager.installFlow(doubleResubmitTable);

        addLearnEgressAclRemoteAclTableMissFlow(dpId);

        instructions = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE,
                "LEARN-" + getTableMissFlowId(NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE), 0,
                "Egress Learn2 ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, Collections.emptyList(), instructions);
        mdsalManager.installFlow(flowEntity);

        List<NxMatchInfo> nxMkMatches = new ArrayList<>();
        nxMkMatches.add(new NxMatchRegister(NxmNxReg5.class, AclConstants.LEARN_MATCH_REG_VALUE));

        actionsInfos = new ArrayList<>();
        instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE,
                "LEARN2-REG-" + getTableMissFlowId(NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress Learn2 ACL Table match reg Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, nxMkMatches, instructions);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added Learn Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addLearnEgressAclRemoteAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_LEARN_ACL_REMOTE_ACL_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_LEARN_ACL_REMOTE_ACL_TABLE), 0,
                "Egress ACL Remote Table Miss Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, Collections.emptyList(),
                mkInstructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Learn Egress ACL Remote Table Miss Flows for dpn {}", dpId);
    }

    private void addLearnIngressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionNxResubmit(NwConstants.INGRESS_LEARN_TABLE));
        actionsInfos.add(new ActionNxResubmit(NwConstants.INGRESS_LEARN_ACL_REMOTE_ACL_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity doubleResubmitTable = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
                "RESUB-" + getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Ingress resubmit ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, Collections.emptyList(), instructions);
        mdsalManager.installFlow(doubleResubmitTable);

        addLearnIngressAclRemoteAclTableMissFlow(dpId);

        instructions = config.getDefaultBehavior() == DefaultBehavior.Deny
                ? AclServiceOFFlowBuilder.getDropInstructionInfo()
                : AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.LPORT_DISPATCHER_TABLE);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE,
                "LEARN-" + getTableMissFlowId(NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE), 0,
                "Ingress Learn2 ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, Collections.emptyList(), instructions);
        mdsalManager.installFlow(flowEntity);

        List<NxMatchInfo> nxMkMatches = new ArrayList<>();
        nxMkMatches.add(new NxMatchRegister(NxmNxReg5.class, AclConstants.LEARN_MATCH_REG_VALUE));

        actionsInfos = new ArrayList<>();
        instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE,
                "LEARN2-REG-" + getTableMissFlowId(NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress Learn2 ACL Table match reg Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, nxMkMatches, instructions);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added Learn ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the ingress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addLearnIngressAclRemoteAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_LEARN_ACL_REMOTE_ACL_TABLE,
                getTableMissFlowId(NwConstants.INGRESS_LEARN_ACL_REMOTE_ACL_TABLE), 0,
                "Ingress ACL Remote Table Miss Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Learn Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the ingress acl table transparent flow.
     *
     * @param dpId the dp id
     */
    private void addTransparentIngressAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> instructionsAcl =
                AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.LPORT_DISPATCHER_TABLE);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE,
                getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE), 0, "Ingress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAcl);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added Transparent Ingress ACL Table allow all Flows for dpn {}", dpId);
    }

    private void addIngressAclFilterTableAllowFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        short dispatcherTableId = NwConstants.LPORT_DISPATCHER_TABLE;
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> dispatcherInstructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        dispatcherInstructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE,
                getTableMissFlowId(NwConstants.INGRESS_ACL_FILTER_TABLE), 0, "Ingress ACL Filter Table allow all Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, dispatcherInstructions);
        mdsalManager.installFlow(flowEntity);

        LOG.debug("Added Ingress ACL Filter Table allow all Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table transparent flow.
     *
     * @param dpId the dp id
     */
    private void addTransparentEgressAclTableMissFlow(BigInteger dpId) {

        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> instructionsAcl =
                AclServiceOFFlowBuilder.getResubmitInstructionInfo(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE,
                getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE), 0, "Ingress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, instructionsAcl);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added Transparent Egress ACL Table allow all Flows for dpn {}", dpId);
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

    private void addConntrackRules(BigInteger dpnId, short dispatcherTableId,short tableId, int write) {
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Established", AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_EST_CT_STATE_MASK,
            dispatcherTableId, tableId, write);
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,"Tracked_Related", AclConstants
            .TRACKED_REL_CT_STATE, AclConstants.TRACKED_REL_CT_STATE_MASK, dispatcherTableId, tableId, write);
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
