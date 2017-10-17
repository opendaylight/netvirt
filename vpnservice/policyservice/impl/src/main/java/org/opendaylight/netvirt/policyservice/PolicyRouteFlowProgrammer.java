/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program POLICY_ROUTING_TABLE flows. Each flow is composed from<br>
 * * OF matches - logical tunnel lport tag and policy classifier metadata
 * bits<br>
 * * OF actions - set policy classifier group id based on the remote DPN
 * associated with the logical tunnel.
 *
 */
@SuppressWarnings("deprecation")
@Singleton
public class PolicyRouteFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRouteFlowProgrammer.class);

    private final DataBroker dataBroker;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyFlowUtil;
    private final JobCoordinator coordinator;

    @Inject
    public PolicyRouteFlowProgrammer(final DataBroker dataBroker, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyFlowUtil,
            final JobCoordinator coordinator) {
        this.dataBroker = dataBroker;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyFlowUtil = policyFlowUtil;
        this.coordinator = coordinator;
    }

    public void programPolicyClassifierFlows(String policyClassifierName, List<BigInteger> localDpIds,
            List<BigInteger> remoteDpIds, int addOrRemove) {
        long policyClassifierId = policyIdManager.getPolicyClassifierId(policyClassifierName);
        if (policyClassifierId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get policy classifier id for classifier {}", policyClassifierName);
            return;
        }

        coordinator.enqueueJob(policyClassifierName, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            remoteDpIds.forEach(remoteDpId -> {
                long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifierName, remoteDpId);
                if (groupId != PolicyServiceConstants.INVALID_ID) {
                    List<InstructionInfo> instructions = policyFlowUtil.getPolicyRouteInstructions(groupId);
                    localDpIds.forEach(localDpId -> {
                        if (!remoteDpId.equals(localDpId)) {
                            programPolicyClassifierFlow(policyClassifierName, policyClassifierId, instructions,
                                    localDpId, remoteDpId, tx, addOrRemove);
                        }
                    });
                } else {
                    LOG.error("Failed to get group id for policy classifier {} DPN {}", policyClassifierName,
                            remoteDpId);
                }
            });
            return Collections.singletonList(tx.submit());
        });

    }

    public void programPolicyClassifierFlow(String policyClassifierName, BigInteger localDpId, BigInteger remoteDpId,
            int addOrRemove) {
        programPolicyClassifierFlow(policyClassifierName, localDpId, remoteDpId, addOrRemove, false);
    }

    public void programPolicyClassifierFlow(String policyClassifierName, BigInteger localDpId, BigInteger remoteDpId,
            int addOrRemove, boolean createIfMissing) {
        long policyClassifierId = policyIdManager.getPolicyClassifierId(policyClassifierName);
        if (policyClassifierId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get policy classifier id for classifier {}", policyClassifierName);
            return;
        }

        long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifierName, remoteDpId);
        if (groupId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get group id for policy classifier {} DPN {}", policyClassifierName, remoteDpId);
            return;
        }

        if (addOrRemove == NwConstants.ADD_FLOW && createIfMissing) {
            addPolicyClassifierGroup(policyClassifierName, localDpId, remoteDpId, addOrRemove, groupId);
        }

        List<InstructionInfo> instructions = policyFlowUtil.getPolicyRouteInstructions(groupId);
        coordinator.enqueueJob(policyClassifierName, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            programPolicyClassifierFlow(policyClassifierName, policyClassifierId, instructions, localDpId, remoteDpId,
                    tx, addOrRemove);
            return Collections.singletonList(tx.submit());
        });

    }

    private void programPolicyClassifierFlow(String policyClassifierName, long policyClassifierId,
            List<InstructionInfo> instructions, BigInteger localDpId, BigInteger remoteDpId, WriteTransaction tx,
            int addOrRemove) {
        Optional<Integer> lportTagOpt = policyServiceUtil.getLogicalTunnelLportTag(localDpId, remoteDpId);
        if (!lportTagOpt.isPresent()) {
            LOG.debug("Missing lport-tag for policy classifier {} logical tunnel for source DPN {} dst DPN {}",
                    policyClassifierName, localDpId, remoteDpId);
            return;
        }

        List<MatchInfoBase> matches = policyFlowUtil.getPolicyRouteMatches(policyClassifierId, lportTagOpt.get());
        LOG.debug("{} policy classifier {} route on source DPN {} dest DPN {}",
                addOrRemove == NwConstants.ADD_FLOW ? "Installing" : "Removing", policyClassifierName, localDpId,
                remoteDpId);
        policyFlowUtil.updateFlowToTx(localDpId, NwConstants.EGRESS_POLICY_ROUTING_TABLE,
                PolicyIdManager.getPolicyClassifierGroupKey(policyClassifierName, remoteDpId),
                PolicyServiceConstants.POLICY_FLOW_PRIOPITY, NwConstants.EGRESS_POLICY_ROUTING_COOKIE, matches,
                instructions, addOrRemove, tx);
    }

    private void addPolicyClassifierGroup(String policyClassifierName, BigInteger localDpId, BigInteger remoteDpId,
            int addOrRemove, long groupId) {
        coordinator.enqueueJob(policyClassifierName, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            policyFlowUtil.updateGroupToTx(localDpId, groupId,
                    PolicyIdManager.getPolicyClassifierGroupKey(policyClassifierName, remoteDpId), GroupTypes.GroupFf,
                    addOrRemove, tx);
            return Collections.singletonList(tx.submit());
        });
    }

}
