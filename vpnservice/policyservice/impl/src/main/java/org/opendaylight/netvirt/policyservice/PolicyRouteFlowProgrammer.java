/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo.InterfaceType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyRouteFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRouteFlowProgrammer.class);

    private static final int INVALID_LPORT_TAG = 0;

    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final IInterfaceManager interfaceManager;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceFlowUtil policyFlowUtil;
    private final DataStoreJobCoordinator coordinator;

    @Inject
    public PolicyRouteFlowProgrammer(final DataBroker dataBroker, final ItmRpcService itmRpcService,
            final IInterfaceManager interfaceManager, final PolicyIdManager policyIdManager,
            final PolicyServiceFlowUtil policyFlowUtil) {
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.policyIdManager = policyIdManager;
        this.policyFlowUtil = policyFlowUtil;
        this.coordinator = DataStoreJobCoordinator.getInstance();
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
        int lportTag = getLogicalTunnelLportTag(localDpId, remoteDpId);
        if (lportTag == IfmConstants.INVALID_PORT_NO) {
            LOG.debug("Missing lport-tag for policy classifier {} logical tunnel for source DPN {} dst DPN {}",
                    policyClassifierName, localDpId, remoteDpId);
            return;
        }

        List<MatchInfoBase> matches = policyFlowUtil.getPolicyRouteMatches(policyClassifierId, lportTag);
        policyFlowUtil.updateFlowToTx(localDpId, NwConstants.EGRESS_POLICY_ROUTING_TABLE,
                PolicyIdManager.getPolicyClassifierGroupKey(policyClassifierName, remoteDpId),
                PolicyServiceConstants.POLICY_FLOW_PRIOPITY, NwConstants.EGRESS_POLICY_ROUTING_COOKIE, matches,
                instructions, addOrRemove, tx);
    }

    private int getLogicalTunnelLportTag(BigInteger srcDpId, BigInteger dstDpId) {
        String tunnelInterfaceName = getTunnelInterfaceName(srcDpId, dstDpId);
        if (tunnelInterfaceName == null) {
            LOG.debug("Failed to get tunnel for source DPN {} dst DPN {}", srcDpId, dstDpId);
            return INVALID_LPORT_TAG;
        }

        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(tunnelInterfaceName);
        if (interfaceInfo == null) {
            LOG.debug("Failed to get interface info for tunnel {}", tunnelInterfaceName);
            return INVALID_LPORT_TAG;
        }

        if (InterfaceType.LOGICAL_GROUP_INTERFACE.equals(interfaceInfo.getInterfaceType())) {
            return interfaceInfo.getInterfaceTag();
        } else {
            LOG.debug("No logical tunnel found between source DPN {} dst DPN {}", srcDpId, dstDpId);
            return INVALID_LPORT_TAG;
        }
    }

    private String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        Future<RpcResult<GetTunnelInterfaceNameOutput>> tunnelInterfaceOutput = itmRpcService.getTunnelInterfaceName(
                new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId).setDestinationDpid(dstDpId).build());
        try {
            if (tunnelInterfaceOutput.get().isSuccessful()) {
                return tunnelInterfaceOutput.get().getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {} for source DPN {} dst DPN {}", srcDpId, dstDpId);
        }

        return null;
    }

}
