/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.listeners;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listener to handle flow capable node updates.
 */
@SuppressWarnings("deprecation")
public class AclNodeListener extends AsyncDataTreeChangeListenerBase<FlowCapableNode, AclNodeListener>
        implements AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(AclNodeListener.class);

    /** The mdsal manager. */
    private final IMdsalApiManager mdsalManager;

    /** The data broker. */
    private final DataBroker dataBroker;

    private SecurityGroupMode securityGroupMode = null;

    /**
     * Instantiates a new acl node listener.
     *
     * @param mdsalManager the mdsal manager
     */
    public AclNodeListener(final IMdsalApiManager mdsalManager, DataBroker dataBroker) {
        super(FlowCapableNode.class, AclNodeListener.class);

        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        Optional<AclserviceConfig> aclConfig = MDSALDataStoreUtils.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier
                .create(AclserviceConfig.class));
        if (aclConfig.isPresent()) {
            this.securityGroupMode = aclConfig.get().getSecurityGroupMode();
        }
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#
     * getWildCardPath()
     */
    @Override
    protected InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#
     * remove(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        // do nothing

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#
     * update(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModificationBefore,
            FlowCapableNode dataObjectModificationAfter) {
        // do nothing

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#
     * add(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        LOG.trace("FlowCapableNode Added: key: {}", key);

        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        createTableDefaultEntries(dpnId);
    }

    /**
     * Creates the table miss entries.
     *
     * @param dpnId the dpn id
     */
    private void createTableDefaultEntries(BigInteger dpnId) {
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            addIngressAclTableMissFlow(dpnId);
            addEgressAclTableMissFlow(dpnId);
            addConntrackRules(dpnId, NwConstants.LPORT_DISPATCHER_TABLE,
                NwConstants.EGRESS_ACL_NEXT_TABLE_ID, NwConstants.ADD_FLOW);
            addConntrackRules(dpnId,NwConstants.EGRESS_LPORT_DISPATCHER_TABLE,
                NwConstants.INGRESS_ACL_NEXT_TABLE_ID,
                 NwConstants.ADD_FLOW);
        } else if (securityGroupMode == SecurityGroupMode.Stateless) {
            addStatelessIngressAclTableMissFlow(dpnId);
            addStatelessEgressAclTableMissFlow(dpnId);
        } else if (securityGroupMode == SecurityGroupMode.Learn) {
            addLearnIngressAclTableMissFlow(dpnId);
            addLearnEgressAclTableMissFlow(dpnId);
            removeMe(dpnId);
        }
    }

    private void removeMe(BigInteger dpnId) {
        // TODO Auto-generated method stub
        List<org.opendaylight.genius.mdsalutil.ActionInfo> listActionInfo = new ArrayList<>();
        String[] header = new String[] { "0", "0", "0", "0", "0", "0", "0", "0" };

//                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_IDLE_TO_KEY, "60"),
//                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_HARD_TO_KEY, "60"),
//                AclConstants.PROTO_MATCH_PRIORITY.toString(),
//                AclConstants.COOKIE_ACL_BASE.toString(), "0",
//                Short.toString(AclConstants.INGRESS_LEARN_TABLE), "0", "0" };
        String[][] valuesMat = new String[][] {
                { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                        NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
                        NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(), "48" }};
//                { NwConstants.LearnFlowModsType.ADD_FROM_VALUE.name(), "3",
//                        NwConstants.NxmOfFieldType.NXM_OF_IN_PORT.getHexType(),  "16" }};
//                { NwConstants.LearnFlowModsType.COPY_FROM_FIELD.name(),
//                        NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
//                        NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(), "16" }};
//                { NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), "3",
//                        NwConstants.NxmOfFieldType.NXM_OF_IN_PORT.getHexType(),  "16" },
//                { NwConstants.LearnFlowModsType.OUTPUT_YO_PORT.name(), "2", "16" }
        //new String[]{"0", "0x0206", "0x0106", "48"}
        listActionInfo.add(new org.opendaylight.genius.mdsalutil.ActionInfo(ActionType.learn, header, valuesMat, 0));

        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkMatches.add(new MatchInfo(MatchFieldType.tcp_dst, new long[] {80}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions,
                listActionInfo));
        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.INGRESS_ACL_NEXT_TABLE_ID,
                getTableMissFlowId(NwConstants.INGRESS_ACL_NEXT_TABLE_ID), 0, "Ingress ACL Filter Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("remove me {}", dpnId);
    }

    /**
     * Adds the ingress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addIngressAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE_ID,
                getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE_ID), 0, "Ingress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_NEXT_TABLE_ID,
                getTableMissFlowId(NwConstants.INGRESS_ACL_NEXT_TABLE_ID), 0, "Ingress ACL Filter Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    private void addLearnEgressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[] {Short.toString(AclConstants.EGRESS_LEARN_TABLE) }));
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[] {Short.toString(AclConstants.EGRESS_LEARN2_TABLE) }));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity doubleResubmitTable = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE_ID,
                "RESUB-" + getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress resubmit ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(doubleResubmitTable);

        mkMatches = new ArrayList<>();
        mkInstructions = new ArrayList<>();
        actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.EGRESS_LEARN_TABLE,
                "LEARN-" + getTableMissFlowId(AclConstants.EGRESS_LEARN_TABLE), 0,
                "Egress Learn ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.EGRESS_LEARN2_TABLE,
                "LEARN-" + getTableMissFlowId(AclConstants.EGRESS_LEARN2_TABLE), 0,
                "Egress Learn2 ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        List<NxMatchInfo> nxMkMatches = new ArrayList<>();
        nxMkMatches.add(new NxMatchInfo(NxMatchFieldType.nxm_reg_6,
                new long[] {Long.valueOf(AclConstants.LEARN_MATCH_REG_VALUE)}));

        short dispatcherTableId = AclConstants.EGRESS_LPORT_DISPATCHER_TABLE;
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] {Short.toString(dispatcherTableId)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.EGRESS_LEARN2_TABLE,
                "LEARN2-REG-" + getTableMissFlowId(AclConstants.EGRESS_LEARN2_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress Learn2 ACL Table match reg Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, nxMkMatches, instructions);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added learn ACL Table Miss Flows for dpn {}", dpId);
    }

    private void addLearnIngressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[] {Short.toString(AclConstants.INGRESS_LEARN_TABLE) }));
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[] {Short.toString(AclConstants.INGRESS_LEARN2_TABLE) }));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity doubleResubmitTable = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE_ID,
                "RESUB-" + getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_PRIORITY, "Ingress resubmit ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(doubleResubmitTable);

        mkMatches = new ArrayList<>();
        mkInstructions = new ArrayList<>();
        actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.INGRESS_LEARN_TABLE,
                "LEARN-" + getTableMissFlowId(AclConstants.INGRESS_LEARN_TABLE), 0,
                "Ingress Learn ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.INGRESS_LEARN2_TABLE,
                "LEARN-" + getTableMissFlowId(AclConstants.INGRESS_LEARN2_TABLE), 0,
                "Ingress Learn2 ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        List<NxMatchInfo> nxMkMatches = new ArrayList<>();
        nxMkMatches.add(new NxMatchInfo(NxMatchFieldType.nxm_reg_6,
                new long[] {Long.valueOf(AclConstants.LEARN_MATCH_REG_VALUE)}));

        short dispatcherTableId = NwConstants.LPORT_DISPATCHER_TABLE;
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] {Short.toString(dispatcherTableId)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        flowEntity = MDSALUtil.buildFlowEntity(dpId, AclConstants.INGRESS_LEARN2_TABLE,
                "LEARN2-REG-" + getTableMissFlowId(AclConstants.INGRESS_LEARN2_TABLE),
                AclConstants.PROTO_MATCH_PRIORITY, "Egress Learn2 ACL Table match reg Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, nxMkMatches, instructions);
        mdsalManager.installFlow(flowEntity);
        LOG.debug("Added learn ACL Table Miss Flows for dpn {}", dpId);

    }

    /**
     * Adds the ingress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addStatelessIngressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> synInstructions = new ArrayList<>();
        List<MatchInfo> synMatches = new ArrayList<>();
        synMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { AclConstants.TCP_FLAG_SYN }));

        List<ActionInfo> dropActionsInfos = new ArrayList<>();
        dropActionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        synInstructions.add(new InstructionInfo(InstructionType.apply_actions, dropActionsInfos));

        FlowEntity synFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE_ID,
                "SYN-" + getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_SYN_DROP_PRIORITY, "Ingress Syn ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, synMatches, synInstructions);
        mdsalManager.installFlow(synFlowEntity);

        synMatches = new ArrayList<>();
        synMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { AclConstants.TCP_FLAG_SYN_ACK }));

        List<InstructionInfo> allowAllInstructions = new ArrayList<>();
        allowAllInstructions.add(
            new InstructionInfo(InstructionType.goto_table,
                    new long[] { NwConstants.INGRESS_ACL_NEXT_TABLE_ID }));

        FlowEntity synAckFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE_ID,
                "SYN-ACK-ALLOW-" + getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_SYN_ACK_ALLOW_PRIORITY, "Ingress Syn Ack ACL Table Allow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, synMatches, allowAllInstructions);
        mdsalManager.installFlow(synAckFlowEntity);


        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_TABLE_ID,
                getTableMissFlowId(NwConstants.INGRESS_ACL_TABLE_ID), 0, "Ingress Stateless ACL Table Miss Flow",
                0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches, allowAllInstructions);
        mdsalManager.installFlow(flowEntity);

        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INGRESS_ACL_NEXT_TABLE_ID,
                getTableMissFlowId(NwConstants.INGRESS_ACL_NEXT_TABLE_ID), 0,
                "Ingress Stateless Next ACL Table Miss Flow", 0, 0, AclConstants.COOKIE_ACL_BASE,
                mkMatches, allowAllInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Stateless Ingress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the stateless egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addStatelessEgressAclTableMissFlow(BigInteger dpId) {
        List<InstructionInfo> allowAllInstructions = new ArrayList<>();
        allowAllInstructions.add(
                new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.EGRESS_ACL_NEXT_TABLE_ID }));

        List<InstructionInfo> synInstructions = new ArrayList<>();
        List<MatchInfo> synMatches = new ArrayList<>();
        synMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { AclConstants.TCP_FLAG_SYN }));

        List<ActionInfo> synActionsInfos = new ArrayList<>();
        synActionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        synInstructions.add(new InstructionInfo(InstructionType.apply_actions, synActionsInfos));

        FlowEntity synFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE_ID,
                "SYN-" + getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_SYN_DROP_PRIORITY, "Egress Syn ACL Table Block", 0, 0,
                AclConstants.COOKIE_ACL_BASE, synMatches, synInstructions);
        mdsalManager.installFlow(synFlowEntity);

        synMatches = new ArrayList<>();
        synMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { AclConstants.TCP_FLAG_SYN_ACK }));

        FlowEntity synAckFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE_ID,
                "SYN-ACK-ALLOW-" + getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE_ID),
                AclConstants.PROTO_MATCH_SYN_ACK_ALLOW_PRIORITY, "Egress Syn Ack ACL Table Allow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, synMatches, allowAllInstructions);
        mdsalManager.installFlow(synAckFlowEntity);

        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE_ID,
                getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE_ID), 0, "Egress Stateless ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, allowAllInstructions);
        mdsalManager.installFlow(flowEntity);

        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_NEXT_TABLE_ID,
                getTableMissFlowId(NwConstants.EGRESS_ACL_NEXT_TABLE_ID), 0,
                "Egress Stateless Next ACL Table Miss Flow", 0, 0, AclConstants.COOKIE_ACL_BASE, mkMatches,
                allowAllInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Stateless Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    /**
     * Adds the egress acl table miss flow.
     *
     * @param dpId the dp id
     */
    private void addEgressAclTableMissFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_TABLE_ID,
                getTableMissFlowId(NwConstants.EGRESS_ACL_TABLE_ID), 0, "Egress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        FlowEntity nextTblFlowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.EGRESS_ACL_NEXT_TABLE_ID,
                getTableMissFlowId(NwConstants.EGRESS_ACL_NEXT_TABLE_ID), 0, "Egress ACL Table Miss Flow", 0, 0,
                AclConstants.COOKIE_ACL_BASE, mkMatches, mkInstructions);
        mdsalManager.installFlow(nextTblFlowEntity);

        LOG.debug("Added Egress ACL Table Miss Flows for dpn {}", dpId);
    }

    private void addConntrackRules(BigInteger dpnId, short dispatcherTableId,short tableId, int write) {
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Established", AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_EST_CT_STATE_MASK,
            dispatcherTableId, tableId, write );
        programConntrackForwardRule(dpnId, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,"Tracked_Related", AclConstants
            .TRACKED_REL_CT_STATE, AclConstants.TRACKED_REL_CT_STATE_MASK, dispatcherTableId, tableId, write );
        programConntrackDropRule(dpnId, AclConstants.CT_STATE_NEW_PRIORITY_DROP,"Tracked_New",
            AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK, tableId, write );
        programConntrackDropRule(dpnId, AclConstants.CT_STATE_NEW_PRIORITY_DROP, "Tracked_Invalid",
            AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK, tableId, write );

    }

    /**
     * Adds the rule to forward the packets known packets.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackForwardRule(BigInteger dpId, Integer priority, String flowId,
            int conntrackState, int conntrackMask, short dispatcherTableId, short tableId, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(
            new ArrayList<>(),dispatcherTableId);

        flowId = "Fixed_Conntrk_Trk_" + dpId + "_" + flowId + dispatcherTableId;
        syncFlow(dpId, tableId, flowId, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rule to drop the unknown/invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackDropRule(BigInteger dpId, Integer priority, String flowId,
            int conntrackState, int conntrackMask, short tableId, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
        flowId = "Fixed_Conntrk_NewDrop_" + dpId + "_" + flowId + tableId;
        syncFlow(dpId, tableId, flowId, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Gets the dispatcher table resubmit instructions.
     *
     * @param actionsInfos the actions infos
     * @return the instructions for dispatcher table resubmit
     */
    private List<InstructionInfo> getDispatcherTableResubmitInstructions(List<ActionInfo> actionsInfos,
                                                                         short dispatcherTableId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] {Short.toString(dispatcherTableId)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
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
            LOG.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId,
                priority, flowName, idleTimeOut, hardTimeOut, cookie, matches, instructions);
            LOG.trace("Installing DpnId {}, flowId {}", dpId, flowId);
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

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#
     * getDataTreeChangeListener()
     */
    @Override
    protected AclNodeListener getDataTreeChangeListener() {
        return AclNodeListener.this;
    }
}
