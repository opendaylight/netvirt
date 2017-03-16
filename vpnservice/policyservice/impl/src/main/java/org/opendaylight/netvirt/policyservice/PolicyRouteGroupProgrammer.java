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
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyRouteGroupProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyRouteGroupProgrammer.class);

    private final DataBroker dataBroker;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyServiceFlowUtil;
    private final DataStoreJobCoordinator coordinator;

    @Inject
    public PolicyRouteGroupProgrammer(final DataBroker dataBroker, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyServiceFlowUtil) {
        this.dataBroker = dataBroker;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyServiceFlowUtil = policyServiceFlowUtil;
        this.coordinator = DataStoreJobCoordinator.getInstance();
    }

    public void programPolicyClassifierGroups(String policyClassifier, List<BigInteger> dpnIds, int addOrRemove) {
        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            dpnIds.forEach(dstDpId -> {
                long groupId = policyIdManager.getPolicyClassifierGroupId(policyClassifier, dstDpId);
                String groupName = policyClassifier + '-' + dstDpId;
                dpnIds.forEach(srcDpId -> {
                    if (!dstDpId.equals(srcDpId)) {
                        policyServiceFlowUtil.updateGroupToTx(srcDpId, groupId, groupName, GroupTypes.GroupFf,
                                addOrRemove, tx);
                    }

                });
            });
            return Collections.singletonList(tx.submit());
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
                List<DpnToInterface> dpnToInterfaceList = policyServiceUtil
                        .getUnderlayNetworkDpnToInterfaces(underlayNetwork);
                if (dpnToInterfaceList != null) {
                    dpnToInterfaceList.forEach(dpnToInterfaces -> {
                        BigInteger dpId = dpnToInterfaces.getDpId();
                        List<TunnelInterface> tunnelInterfaces = dpnToInterfaces.getTunnelInterface();
                        programPolicyClassifierBuckets(policyClassifier, tunnelInterfaces, dpId, bucketId, addOrRemove,
                                tx);
                    });
                } else {
                    LOG.debug("No DpnToInterface found for underlay network {}", underlayNetwork);
                }
            }
            return Collections.singletonList(tx.submit());
        });
    }

    public void programPolicyClassifierBuckets(String policyClassifier, List<TunnelInterface> tunnelInterfaces,
            BigInteger dpId, int bucketId, int addOrRemove) {
        coordinator.enqueueJob(policyClassifier, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            programPolicyClassifierBuckets(policyClassifier, tunnelInterfaces, dpId, bucketId, addOrRemove, tx);
            return Collections.singletonList(tx.submit());
        });
    }

    private void programPolicyClassifierBuckets(String policyClassifier, List<TunnelInterface> tunnelInterfaces,
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
