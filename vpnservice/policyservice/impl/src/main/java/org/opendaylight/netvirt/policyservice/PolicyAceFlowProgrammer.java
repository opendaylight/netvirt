/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.IngressInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.L2vpnServiceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.L3vpnServiceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.ServiceTypeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program POLICY_CLASSIFER_TABLE flows. Each flow is composed from<br>
 * * OF matches - translated from set of {@link Ace} matches<br>
 * * OF actions - set policy classifier bits in the metadata and goto
 * POLICY_ROUTING_TABLE
 *
 */
@Singleton
public class PolicyAceFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAceFlowProgrammer.class);

    private final DataBroker dataBroker;
    private final IAclServiceUtil aclServiceUtil;
    private final IInterfaceManager interfaceManager;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyFlowUtil;
    private final DataStoreJobCoordinator coordinator;

    @Inject
    public PolicyAceFlowProgrammer(final DataBroker dataBroker, final IAclServiceUtil aclServiceUtil,
            final IInterfaceManager interfaceManager, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyFlowUtil) {
        this.dataBroker = dataBroker;
        this.aclServiceUtil = aclServiceUtil;
        this.interfaceManager = interfaceManager;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyFlowUtil = policyFlowUtil;
        this.coordinator = DataStoreJobCoordinator.getInstance();
    }

    public void programAceFlows(Ace ace, BigInteger dpId, int addOrRemove) {
        Optional<String> policyClassifierOpt = policyServiceUtil.getAcePolicyClassifier(ace);
        if (!policyClassifierOpt.isPresent()) {
            LOG.debug("No egress policy classifier found for ACE rule {}", ace.getRuleName());
            return;
        }

        List<InstructionInfo> instructions = (addOrRemove == NwConstants.ADD_FLOW)
                ? getPolicyClassifierInstructions(policyClassifierOpt.get()) : null;
        programAceFlows(ace, instructions, dpId, addOrRemove);
    }

    public void programAceFlows(Ace ace, String policyClassifierName, List<BigInteger> dpIds, int addOrRemove) {
        List<InstructionInfo> instructions = (addOrRemove == NwConstants.ADD_FLOW)
                ? getPolicyClassifierInstructions(policyClassifierName) : null;
        dpIds.forEach(dpId -> {
            programAceFlows(ace, instructions, dpId, addOrRemove);
        });
    }

    public void programAceFlows(Ace ace, List<InstructionInfo> instructions, BigInteger dpId, int addOrRemove) {
        Map<String, List<MatchInfoBase>> aclflowMap = aclServiceUtil.programIpFlow(ace.getMatches());
        Map<String, List<MatchInfoBase>> policyFlowMap = programPolicyAclFlows(ace.getMatches(), dpId);
        Map<String, List<MatchInfoBase>> flowMap = new HashMap<>();
        flowMap.putAll(Optional.fromNullable(aclflowMap).or(Collections.emptyMap()));
        flowMap.putAll(policyFlowMap);

        coordinator.enqueueJob(ace.getRuleName(), () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

            flowMap.forEach((flowName, matches) -> {
                String policyFlowName = "Policy_" + flowName;
                LOG.debug("{} ACE rule {} on DPN {} flow {}",
                        addOrRemove == NwConstants.ADD_FLOW ? "Installing" : "Removing", ace.getRuleName(), dpId,
                        policyFlowName);
                policyFlowUtil.updateFlowToTx(dpId, NwConstants.EGRESS_POLICY_CLASSIFIER_TABLE, policyFlowName,
                        PolicyServiceConstants.POLICY_FLOW_PRIOPITY, NwConstants.EGRESS_POLICY_CLASSIFIER_COOKIE,
                        matches, instructions, addOrRemove, tx);
            });
            return Collections.singletonList(tx.submit());
        });
    }

    private List<InstructionInfo> getPolicyClassifierInstructions(String policyClassifierName) {
        long policyClassifierId = policyIdManager.getPolicyClassifierId(policyClassifierName);
        if (policyClassifierId == PolicyServiceConstants.INVALID_ID) {
            LOG.error("Failed to get policy classifier id for classifier {}", policyClassifierName);
            return Collections.emptyList();
        }

        return policyFlowUtil.getPolicyClassifierInstructions(policyClassifierId);
    }

    private Map<String, List<MatchInfoBase>> programPolicyAclFlows(Matches matches, BigInteger dpId) {
        Map<String, List<MatchInfoBase>> flowsMap = new HashMap<>();
        IngressInterface ingressInterface = matches.getAugmentation(IngressInterface.class);
        if (ingressInterface != null) {
            Pair<String,
                    List<MatchInfoBase>> ingressInterfaceFlow = programIngressInterfaceFlow(ingressInterface, dpId);
            if (ingressInterfaceFlow != null) {
                flowsMap.put(ingressInterfaceFlow.getKey(), ingressInterfaceFlow.getValue());
            }
        }

        Service service = matches.getAugmentation(Service.class);
        if (service != null) {
            Pair<String, List<MatchInfoBase>> serviceFlow = programServiceFlow(service);
            if (serviceFlow != null) {
                flowsMap.put(serviceFlow.getKey(), serviceFlow.getValue());
            }
        }

        return flowsMap;
    }

    private Pair<String, List<MatchInfoBase>> programIngressInterfaceFlow(IngressInterface ingressInterface,
            BigInteger dpId) {
        String interfaceName = ingressInterface.getName();
        if (interfaceName == null) {
            LOG.error("Invalid ingress interface augmentation. missing interface name");
            return null;
        }

        BigInteger interfaceDpId = interfaceManager.getDpnForInterface(interfaceName);
        if (!Objects.equal(dpId, interfaceDpId)) {
            LOG.trace("Ingress interface match will be installed only on DPN {}. Ignoring DPN {}", ingressInterface,
                    dpId);
            return null;
        }

        List<MatchInfoBase> matches = policyFlowUtil.getIngressInterfaceMatches(interfaceName);
        if (matches == null) {
            LOG.error("Failed to get ingress interface {} matches", interfaceName);
            return null;
        }

        String flowName = "INGRESS_INTERFACE_" + interfaceName;
        return Pair.of(flowName, matches);
        // TODO vlan
    }

    private Pair<String, List<MatchInfoBase>> programServiceFlow(Service service) {
        // TODO Auto-generated method stub
        String serviceName = service.getServiceName();
        Class<? extends ServiceTypeBase> serviceType = service.getServiceType();
        if (serviceName == null || serviceType == null) {
            LOG.error("Invalid policy service augmentation {}", service);
            return null;
        }

        String flowName = null;
        List<MatchInfoBase> matches = null;
        if (serviceType.isAssignableFrom(L2vpnServiceType.class)) {
            flowName = "L2VPN_" + serviceName;
            matches = policyFlowUtil.getElanInstanceMatches(serviceName);
        } else if (serviceType.isAssignableFrom(L3vpnServiceType.class)) {
            flowName = "L3VPN_" + serviceName;
            matches = policyFlowUtil.getVpnInstanceMatches(serviceName);
        }

        if (flowName != null && matches != null) {
            return Pair.of(flowName, matches);
        }

        LOG.debug("Failed to get service {} matches", serviceName);
        return null;
    }

    // TODO table miss flow
}
