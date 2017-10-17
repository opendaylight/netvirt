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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
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
@SuppressWarnings("deprecation")
@Singleton
public class PolicyAceFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAceFlowProgrammer.class);

    private final DataBroker dataBroker;
    private final IAclServiceUtil aclServiceUtil;
    private final IInterfaceManager interfaceManager;
    private final PolicyIdManager policyIdManager;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyServiceFlowUtil policyFlowUtil;
    private final JobCoordinator coordinator;

    @Inject
    public PolicyAceFlowProgrammer(final DataBroker dataBroker, final IAclServiceUtil aclServiceUtil,
            final IInterfaceManager interfaceManager, final PolicyIdManager policyIdManager,
            final PolicyServiceUtil policyServiceUtil, final PolicyServiceFlowUtil policyFlowUtil,
            final JobCoordinator coordinator) {
        this.dataBroker = dataBroker;
        this.aclServiceUtil = aclServiceUtil;
        this.interfaceManager = interfaceManager;
        this.policyIdManager = policyIdManager;
        this.policyServiceUtil = policyServiceUtil;
        this.policyFlowUtil = policyFlowUtil;
        this.coordinator = coordinator;
    }

    public void programAceFlows(Ace ace, BigInteger dpId, int addOrRemove) {
        Optional<String> policyClassifierOpt = policyServiceUtil.getAcePolicyClassifier(ace);
        if (!policyClassifierOpt.isPresent()) {
            LOG.debug("No egress policy classifier found for ACE rule {}", ace.getRuleName());
            return;
        }

        List<InstructionInfo> instructions = addOrRemove == NwConstants.ADD_FLOW
                ? getPolicyClassifierInstructions(policyClassifierOpt.get()) : null;
        programAceFlows(ace, instructions, dpId, addOrRemove);
    }

    public void programAceFlows(Ace ace, String policyClassifierName, List<BigInteger> dpIds, int addOrRemove) {
        List<InstructionInfo> instructions = addOrRemove == NwConstants.ADD_FLOW
                ? getPolicyClassifierInstructions(policyClassifierName) : null;
        dpIds.forEach(dpId -> programAceFlows(ace, instructions, dpId, addOrRemove));
    }

    public void programAceFlows(Ace ace, List<InstructionInfo> instructions, BigInteger dpId, int addOrRemove) {
        Optional<PolicyAceFlowWrapper> policyFlowWrapperOpt = getPolicyAceFlowWrapper(ace.getMatches());
        if (policyFlowWrapperOpt.isPresent()) {
            PolicyAceFlowWrapper policyFlowWrapper = policyFlowWrapperOpt.get();
            if (policyFlowWrapper.isPartial()) {
                LOG.debug("Delaying policy ACE rule {} installation due to partial information", ace.getRuleName());
                return;
            }

            BigInteger policyFlowDpId = policyFlowWrapper.getDpId();
            if (!BigInteger.ZERO.equals(policyFlowDpId) && !Objects.equals(dpId, policyFlowDpId)) {
                LOG.trace("Ignoring policy ACE rule {} flow {} on DPN {}", ace.getRuleName(),
                        policyFlowWrapper.getFlowName(), dpId);
                return;
            }
        }

        Map<String, List<MatchInfoBase>> aclFlowMap = aclServiceUtil.programIpFlow(ace.getMatches());
        if (aclFlowMap == null || aclFlowMap.isEmpty()) {
            LOG.warn("Failed to create flow matches for ACE rule {}", ace.getRuleName());
            return;
        }

        coordinator.enqueueJob(ace.getRuleName(), () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

            aclFlowMap.forEach((flowName, matches) -> {
                String policyFlowName = "Policy_" + flowName;
                List<MatchInfoBase> policyMatches = matches;
                int policyPriority = PolicyServiceConstants.POLICY_FLOW_PRIOPITY;

                if (policyFlowWrapperOpt.isPresent()) {
                    PolicyAceFlowWrapper policyFlowWrapper = policyFlowWrapperOpt.get();
                    policyFlowName += '_' + policyFlowWrapper.getFlowName();
                    policyPriority = policyFlowWrapper.getPriority();
                    policyMatches = Stream.concat(matches.stream(), policyFlowWrapper.getMatches().stream())
                            .collect(Collectors.toList());
                }

                LOG.debug("{} policy ACE rule {} on DPN {} flow {}",
                        addOrRemove == NwConstants.ADD_FLOW ? "Installing" : "Removing", ace.getRuleName(), dpId,
                        policyFlowName);
                policyFlowUtil.updateFlowToTx(dpId, NwConstants.EGRESS_POLICY_CLASSIFIER_TABLE, policyFlowName,
                        policyPriority, NwConstants.EGRESS_POLICY_CLASSIFIER_COOKIE, policyMatches, instructions,
                        addOrRemove, tx);
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

    private Optional<PolicyAceFlowWrapper> getPolicyAceFlowWrapper(Matches matches) {
        IngressInterface ingressInterface = matches.getAugmentation(IngressInterface.class);
        if (ingressInterface != null) {
            Optional<PolicyAceFlowWrapper> interfaceFlowOpt = getIngressInterfaceFlow(ingressInterface);
            if (interfaceFlowOpt.isPresent()) {
                return interfaceFlowOpt;
            }
        }

        Service service = matches.getAugmentation(Service.class);
        if (service != null) {
            Optional<PolicyAceFlowWrapper> serviceFlowOpt = getPolicyServiceFlow(service);
            if (serviceFlowOpt.isPresent()) {
                return serviceFlowOpt;
            }
        }

        return Optional.absent();
    }

    private Optional<PolicyAceFlowWrapper> getIngressInterfaceFlow(IngressInterface ingressInterface) {
        String interfaceName = ingressInterface.getName();
        if (interfaceName == null) {
            LOG.error("Invalid ingress interface augmentation. missing interface name");
            return Optional.absent();
        }

        String flowName = "INGRESS_INTERFACE_" + interfaceName;
        int flowPriority = PolicyServiceConstants.POLICY_ACL_TRUNK_INTERFACE_FLOW_PRIOPITY;
        VlanId vlanId = ingressInterface.getVlanId();
        if (vlanId != null) {
            Optional<String> vlanMemberInterfaceOpt = policyServiceUtil.getVlanMemberInterface(interfaceName, vlanId);
            if (!vlanMemberInterfaceOpt.isPresent()) {
                LOG.debug("Vlan member {} missing for trunk {}", vlanId.getValue(), interfaceName);
                return Optional.of(new PolicyAceFlowWrapper(flowName, PolicyAceFlowWrapper.PARTIAL));
            }

            interfaceName = vlanMemberInterfaceOpt.get();
            flowPriority = PolicyServiceConstants.POLICY_ACL_VLAN_INTERFACE_FLOW_PRIOPITY;
        }

        List<MatchInfoBase> matches = policyFlowUtil.getIngressInterfaceMatches(interfaceName);
        if (matches == null || matches.isEmpty()) {
            LOG.debug("Failed to get ingress interface {} matches", interfaceName);
            return Optional.of(new PolicyAceFlowWrapper(flowName, PolicyAceFlowWrapper.PARTIAL));
        }

        BigInteger dpId = interfaceManager.getDpnForInterface(interfaceName);
        if (dpId == null) {
            dpId = BigInteger.ZERO;
        }
        return Optional.of(new PolicyAceFlowWrapper(flowName, matches, flowPriority, dpId));
    }

    private Optional<PolicyAceFlowWrapper> getPolicyServiceFlow(Service service) {
        String serviceName = service.getServiceName();
        Class<? extends ServiceTypeBase> serviceType = service.getServiceType();
        if (serviceName == null || serviceType == null) {
            LOG.error("Invalid policy service augmentation {}", service);
            return Optional.absent();
        }

        if (serviceType.isAssignableFrom(L2vpnServiceType.class)) {
            String flowName = "L2VPN_" + serviceName;
            List<MatchInfoBase> elanMatches = policyFlowUtil.getElanInstanceMatches(serviceName);
            if (elanMatches == null || elanMatches.isEmpty()) {
                return Optional.of(new PolicyAceFlowWrapper(flowName, PolicyAceFlowWrapper.PARTIAL));
            }

            return Optional.of(new PolicyAceFlowWrapper(flowName, elanMatches,
                    PolicyServiceConstants.POLICY_ACL_L2VPN_FLOW_PRIOPITY));
        }

        if (serviceType.isAssignableFrom(L3vpnServiceType.class)) {
            String flowName = "L3VPN_" + serviceName;
            List<MatchInfoBase> vpnMatches = policyFlowUtil.getVpnInstanceMatches(serviceName);
            if (vpnMatches == null || vpnMatches.isEmpty()) {
                return Optional.of(new PolicyAceFlowWrapper(flowName, PolicyAceFlowWrapper.PARTIAL));
            }

            return Optional.of(new PolicyAceFlowWrapper(flowName, vpnMatches,
                    PolicyServiceConstants.POLICY_ACL_L3VPN_FLOW_PRIOPITY));
        }

        return Optional.absent();
    }
}
