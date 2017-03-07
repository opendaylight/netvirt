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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyAceFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAceFlowProgrammer.class);

    private final IAclServiceUtil iAclServiceUtil;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyFlowUtil;

    @Inject
    public PolicyAceFlowProgrammer(final IAclServiceUtil iAclServiceUtil, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyFlowUtil) {
        this.iAclServiceUtil = iAclServiceUtil;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyFlowUtil = policyFlowUtil;
    }

    public void programAceFlows(Ace ace, String underlayNetwork, BigInteger dpId, int addOrRemove) {
        String policyClassifierName = policyServiceUtil.getAcePolicyClassifier(ace);
        if (policyClassifierName == null) {
            LOG.debug("No egress policy classifier found for ACE rule {}", ace.getRuleName());
            return;
        }

        programAceFlows(ace, policyClassifierName, Collections.singletonList(underlayNetwork), addOrRemove);

    }

    public void programAceFlows(Ace ace, String policyClassifierName, List<String> underlayNetworks, int addOrRemove) {
        List<BigInteger> dpIds = policyServiceUtil.getUnderlayNetworksDpns(underlayNetworks);
        if (dpIds == null || dpIds.isEmpty()) {
            LOG.debug("No DPNs found for installation of ACE rule {} networks {}", ace.getRuleName(), underlayNetworks);
            return;
        }

        long policyClassifierId = policyIdManager.getPolicyClassifierId(policyClassifierName);
        if (policyClassifierId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get policy classifier id for ACE rule {} classifier {}", ace.getRuleName(),
                    policyClassifierName);
            return;
        }

        List<InstructionInfo> instructions = (addOrRemove == NwConstants.ADD_FLOW)
                ? policyFlowUtil.getPolicyClassifierInstructions(policyClassifierId) : null;
        dpIds.forEach(dpId -> {
            programAceFlows(ace, underlayNetworks, instructions, dpId, addOrRemove);
        });
    }

    public void programAceFlows(Ace ace, List<String> underlayNetworks, List<InstructionInfo> instructions,
            BigInteger dpId, int addOrRemove) {
        Map<String, List<MatchInfoBase>> flowMap = iAclServiceUtil.programIpFlow(ace.getMatches());
        if (flowMap == null) {
            LOG.error("Failed to create flows for ACE rule {}", ace.getRuleName());
            return;
        }

        flowMap.forEach((flowName, matches) -> {
            policyFlowUtil.syncFlow(dpId, NwConstants.EGRESS_POLICY_CLASSIFIER_TABLE, "Policy_" + flowName,
                    PolicyServiceConstants.POLICY_FLOW_PRIOPITY, NwConstants.EGRESS_POLICY_CLASSIFIER_COOKIE, matches,
                    instructions, addOrRemove);
        });
    }

    // TODO table miss flow
}
