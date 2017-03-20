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
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyRouteFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRouteFlowProgrammer.class);

    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final IInterfaceManager interfaceManager;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceFlowUtil policyFlowUtil;
    private final PolicyServiceUtil policyServiceUtil;
    private final DataStoreJobCoordinator coordinator;

    @Inject
    public PolicyRouteFlowProgrammer(final DataBroker dataBroker, final ItmRpcService itmRpcService,
            final IInterfaceManager interfaceManager, final PolicyIdManager policyIdManager,
            final PolicyServiceFlowUtil policyFlowUtil, final PolicyServiceUtil policyServiceUtil) {
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.policyIdManager = policyIdManager;
        this.policyFlowUtil = policyFlowUtil;
        this.policyServiceUtil = policyServiceUtil;
        this.coordinator = DataStoreJobCoordinator.getInstance();
    }

    public void programPolicyClassifierFlows(String policyClassifier, List<String> underlayNetworks, int addOrRemove) {
        List<BigInteger> dpnIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        if (dpnIds == null || dpnIds.isEmpty()) {
            LOG.debug("No DPNs found for installation of policy classifier flows in networks {}", underlayNetworks);
            return;
        }

        long policyClassifierId = policyIdManager.getPolicyClassifierId(policyClassifier);
        if (policyClassifierId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get policy classifier id for classifier {}", policyClassifier);
            return;
        }

        coordinator.enqueueJob(policyClassifier, () -> {
            List<InstructionInfo> instructions = policyFlowUtil.getPolicyClassifierInstructions(policyClassifierId);
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            dpnIds.forEach(dstDpId -> {
                long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifier, dstDpId);
                if (groupId != PolicyServiceConstants.INVALID_ID) {
                    dpnIds.forEach(srcDpId -> {
                        int lportTag = getLogicalTunnelLportTag(srcDpId, dstDpId);
                        if (lportTag != IfmConstants.INVALID_PORT_NO) {
                            List<MatchInfoBase> matches = policyFlowUtil.getPolicyRouteMatches(policyClassifierId,
                                    lportTag);
                            if (!dstDpId.equals(srcDpId)) {
                                policyFlowUtil.updateFlowToTx(srcDpId, NwConstants.EGRESS_POLICY_ROUTING_TABLE,
                                        policyClassifier + '-' + dstDpId, PolicyServiceConstants.POLICY_FLOW_PRIOPITY,
                                        NwConstants.EGRESS_POLICY_ROUTING_COOKIE, matches, instructions, addOrRemove,
                                        tx);
                            }
                        } else {
                            LOG.debug("Missing lport-tag for policy classifier {} source DPN {} dst DPN {}",
                                    policyClassifier, srcDpId, dstDpId);
                        }

                    });
                } else {
                    LOG.error("Failed to get group id for policy classifier {} DPN {}", policyClassifier, dstDpId);
                }
            });
            return Collections.singletonList(tx.submit());
        });

    }

    private int getLogicalTunnelLportTag(BigInteger srcDpId, BigInteger dstDpId) {
        String tunnelInterfaceName = getTunnelInterfaceName(srcDpId, dstDpId);
        if (tunnelInterfaceName == null) {
            LOG.debug("Failed to get tunnel for source DPN {} dst DPN {}", srcDpId, dstDpId);
            return IfmConstants.INVALID_PORT_NO;
        }

        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(tunnelInterfaceName);
        if (interfaceInfo == null) {
            LOG.debug("Failed to get interface info for tunnel {}", tunnelInterfaceName);
            return IfmConstants.INVALID_PORT_NO;
        }

        return interfaceInfo.getInterfaceTag();
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
