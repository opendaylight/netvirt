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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program policy classifier fast-failover groups per remote DPN.<br>
 * Group id is allocated for combination of policy classifier and DPN id.<br>
 * The group buckets are built based on the underlay networks defined for the
 * policy classifier.<br>
 * Each bucket in the group contains the tunnel egress actions for the actual
 * tunnel interface associated with the underlay network.
 *
 */
@SuppressWarnings("deprecation")
@Singleton
public class PolicyRouteGroupProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRouteGroupProgrammer.class);

    private final DataBroker dataBroker;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyServiceFlowUtil;
    private final JobCoordinator coordinator;

    @Inject
    public PolicyRouteGroupProgrammer(final DataBroker dataBroker, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyServiceFlowUtil,
            final JobCoordinator coordinator) {
        this.dataBroker = dataBroker;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyServiceFlowUtil = policyServiceFlowUtil;
        this.coordinator = coordinator;
    }

    public void programPolicyClassifierGroups(String policyClassifier, List<BigInteger> localDpIds,
            List<BigInteger> remoteDpIds, int addOrRemove) {
        if (remoteDpIds == null || remoteDpIds.isEmpty()) {
            LOG.debug("No remote DPNs found for policy classifier {}", policyClassifier);
            return;
        }

        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            remoteDpIds.forEach(
                remoteDpId -> programPolicyClassifierGroups(policyClassifier, localDpIds, remoteDpId, tx,
                            addOrRemove));
            return Collections.singletonList(tx.submit());
        });
    }

    public void programPolicyClassifierGroups(String policyClassifier, BigInteger dpId,
            List<TunnelInterface> tunnelInterfaces, int addOrRemove) {
        if (tunnelInterfaces == null) {
            LOG.debug("No tunnel interfaces found for policy classifier {} DPN {}", policyClassifier, dpId);
            return;

        }
        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tunnelInterfaces.forEach(tunnelInterface -> {
                BigInteger remoteDpId = tunnelInterface.getRemoteDpId();
                programPolicyClassifierGroups(policyClassifier, Collections.singletonList(dpId), remoteDpId, tx,
                        addOrRemove);
            });
            return Collections.singletonList(tx.submit());
        });
    }

    private void programPolicyClassifierGroups(String policyClassifier, List<BigInteger> localDpIds,
            BigInteger remoteDpId, WriteTransaction tx, int addOrRemove) {
        long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifier, remoteDpId);
        if (groupId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get group id for policy classifier {}", policyClassifier);
            return;
        }

        if (localDpIds == null || localDpIds.isEmpty()) {
            LOG.debug("No DPNs found for policy classifier {}", policyClassifier);
            return;
        }

        String groupName = PolicyIdManager.getPolicyClassifierGroupKey(policyClassifier, remoteDpId);
        localDpIds.forEach(srcDpId -> {
            if (!remoteDpId.equals(srcDpId)) {
                policyServiceFlowUtil.updateGroupToTx(srcDpId, groupId, groupName, GroupTypes.GroupFf, addOrRemove, tx);
            }
        });
    }

    public void programPolicyClassifierGroupBuckets(String policyClassifier, List<String> underlayNetworks,
            int addOrRemove) {
        if (underlayNetworks == null) {
            return;
        }

        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

            for (int idx = 0; idx < underlayNetworks.size(); idx++) {
                final int bucketId = idx;
                String underlayNetwork = underlayNetworks.get(idx);
                List<DpnToInterface> dpnToInterfaceList =
                        policyServiceUtil.getUnderlayNetworkDpnToInterfaces(underlayNetwork);
                dpnToInterfaceList.forEach(dpnToInterface -> {
                    BigInteger dpId = dpnToInterface.getDpId();
                    List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
                    programPolicyClassifierGroupBuckets(policyClassifier, tunnelInterfaces, dpId, bucketId,
                            addOrRemove, tx);
                });
            }
            return Collections.singletonList(tx.submit());
        });
    }

    public void programPolicyClassifierGroupBuckets(String policyClassifier, List<TunnelInterface> tunnelInterfaces,
            BigInteger dpId, int bucketId, int addOrRemove) {
        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            programPolicyClassifierGroupBuckets(policyClassifier, tunnelInterfaces, dpId, bucketId, addOrRemove, tx);
            return Collections.singletonList(tx.submit());
        });
    }

    private void programPolicyClassifierGroupBuckets(String policyClassifier, List<TunnelInterface> tunnelInterfaces,
            BigInteger dpId, int bucketId, int addOrRemove, WriteTransaction tx) {
        if (tunnelInterfaces == null) {
            LOG.debug("No tunnel interfaces found for policy classifier {} DPN {}", policyClassifier, dpId);
            return;
        }

        tunnelInterfaces.forEach(tunnelInterface -> {
            String interfaceName = tunnelInterface.getInterfaceName();
            BigInteger remoteDpId = tunnelInterface.getRemoteDpId();
            long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifier, remoteDpId);
            policyServiceFlowUtil.updateInterfaceBucketToTx(dpId, groupId, bucketId, interfaceName, addOrRemove, tx);
        });
    }
}
