/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.policyservice.PolicyServiceConstants;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyServiceFlowUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyServiceFlowUtil.class);

    private final IMdsalApiManager mdsalManager;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final IVpnManager vpnManager;

    @Inject
    public PolicyServiceFlowUtil(final IMdsalApiManager mdsalManager, final IInterfaceManager interfaceManager,
            final IElanService elanService, final IVpnManager vpnManager) {
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.vpnManager = vpnManager;
    }

    public List<InstructionInfo> getTableMissInstructions() {
        List<ActionInfo> actions = Collections
                .singletonList(new ActionNxResubmit(NwConstants.EGRESS_LPORT_DISPATCHER_TABLE));
        return Collections.singletonList(new InstructionApplyActions(actions));
    }

    public List<InstructionInfo> getPolicyClassifierInstructions(long policyClassifierId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getPolicyClassifierMetaData(policyClassifierId),
                MetaDataUtil.METADATA_MASK_POLICY_CLASSIFER_ID));
        instructions.add(new InstructionGotoTable(NwConstants.EGRESS_POLICY_ROUTING_TABLE));
        return instructions;
    }

    public List<MatchInfoBase> getPolicyRouteMatches(long policyClassifierId, int lportTag) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new NxMatchRegister(NxmNxReg6.class, lportTag, MetaDataUtil.getLportTagMaskForReg6()));
        matches.add(new MatchMetadata(MetaDataUtil.getPolicyClassifierMetaData(policyClassifierId),
                MetaDataUtil.METADATA_MASK_POLICY_CLASSIFER_ID));
        return matches;
    }

    public List<InstructionInfo> getPolicyRouteInstructions(long groupId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(Collections.singletonList(new ActionGroup(groupId))));
        return instructions;
    }

    public List<MatchInfoBase> getIngressInterfaceMatches(String ingressInterface) {
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(ingressInterface);
        if (interfaceInfo == null) {
            LOG.warn("No interface info found for {}", ingressInterface);
            return Collections.emptyList();
        }

        int lportTag = interfaceInfo.getInterfaceTag();
        return Collections.singletonList(
                new MatchMetadata(MetaDataUtil.getMetadataLPort(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG));
    }

    public List<MatchInfoBase> getElanInstanceMatches(String elanInstanceName) {
        return elanService.getEgressMatchesForElanInstance(elanInstanceName);
    }

    public List<MatchInfoBase> getVpnInstanceMatches(String vpnInstanceName) {
        return vpnManager.getEgressMatchesForVpn(vpnInstanceName);
    }

    public void updateFlowToTx(BigInteger dpId, short tableId, String flowName, int priority, BigInteger cookie,
            List<? extends MatchInfoBase> matches, List<InstructionInfo> instructions, int addOrRemove,
            WriteTransaction tx) {
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowName, priority, flowName, 0, 0, cookie,
                matches, instructions);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlowToTx(flowEntity, tx);
        } else {
            mdsalManager.removeFlowToTx(flowEntity, tx);
        }
    }

    public void updateGroupToTx(BigInteger dpId, long groupId, String groupName, GroupTypes groupType, int addOrRemove,
            WriteTransaction tx) {
        if (addOrRemove == NwConstants.ADD_FLOW && mdsalManager.groupExists(dpId, groupId)) {
            LOG.trace("Group {} id {} already exists", groupName, groupId);
            return;
        }

        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpId, groupId, groupName, groupType,
                Collections.emptyList() /*listBucketInfo*/);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Add group {} to DPN {}", groupId, dpId);
            mdsalManager.addGroupToTx(groupEntity, tx);
        } else {
            LOG.debug("Remove group {} from DPN {}", groupId, dpId);
            mdsalManager.removeGroupToTx(groupEntity, tx);
        }
    }

    public void updateInterfaceBucketToTx(BigInteger dpId, long groupId, int bucketId, String interfaceName,
            int addOrRemove, WriteTransaction tx) {
        if (groupId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("No valid group id found for interface {} DPN {}", interfaceName, dpId);
            return;
        }

        if (addOrRemove == NwConstants.DEL_FLOW) {
            LOG.debug("Remove bucket for interface {} from group {} DPN {}", interfaceName, groupId, dpId);
            mdsalManager.removeBucketToTx(dpId, groupId, bucketId, tx);
            return;
        }

        List<ActionInfo> egressActions = interfaceManager.getInterfaceEgressActions(interfaceName);
        if (egressActions == null || egressActions.isEmpty()) {
            LOG.error("Failed to get egress actions for interface {} DPN {}", interfaceName, dpId);
            return;
        }

        Long port = interfaceManager.getPortForInterface(interfaceName);
        if (port == null) {
            LOG.error("Failed to get port for interface {}", interfaceName);
            return;
        }

        Bucket bucket = MDSALUtil.buildBucket(MDSALUtil.buildActions(egressActions), MDSALUtil.GROUP_WEIGHT, bucketId,
                port, MDSALUtil.WATCH_GROUP);
        LOG.debug("Add bucket id {} for interface {} to group {} DPN {}", bucketId, interfaceName, groupId, dpId);
        mdsalManager.addBucketToTx(dpId, groupId, bucket, tx);
    }
}
