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
import java.util.List;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
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
    private IMdsalApiManager mdsalManager;

    /**
     * Instantiates a new acl node listener.
     *
     * @param mdsalManager the mdsal manager
     */
    public AclNodeListener(final IMdsalApiManager mdsalManager) {
        super(FlowCapableNode.class, AclNodeListener.class);

        this.mdsalManager = mdsalManager;
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
        createTableMissEntries(dpnId);
    }

    /**
     * Creates the table miss entries.
     *
     * @param dpnId the dpn id
     */
    private void createTableMissEntries(BigInteger dpnId) {
        if (AclConstants.isStatelessAcl()) {
            addStatelessIngressAclTableMissFlow(dpnId);
            addStatelessEgressAclTableMissFlow(dpnId);
        } else {
            addIngressAclTableMissFlow(dpnId);
            addEgressAclTableMissFlow(dpnId);
        }
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
